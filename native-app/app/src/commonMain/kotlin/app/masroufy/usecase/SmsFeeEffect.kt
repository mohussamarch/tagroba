package app.masroufy.usecase

import app.masroufy.core.BankFeeCategory
import app.masroufy.core.CategorizationSource
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsFee
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsRow
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.hashContent
import app.masroufy.core.subtractMoney
import app.masroufy.core.uiText
import app.masroufy.port.CategoryRepository

/**
 * الرسوم = **عملية لوحدها «رسوم بنكية»** جنب العملية الأصلية (§77-B — قرار المالك 2026-10-09 ✓): الرسوم المكتوبة في رسالة البنك
 * ([SmsRow.fee] — `SmsFees.kt`) بتتسجل عملية صرف تانية **في نفس الدفعة** (نفس اليوم والمحفظة والعملة)، فرصيد المحفظة بيفضل مظبوط.
 * عمرها ما بتتكتب من غير عمليتها الأصلية (نفس وحدة العمل)، والتراجع عن الدفعة بيمسح الاتنين. اللي بيتكتب = [smsFeeToRecord] بالظبط
 * (الشاشة بتعرضه قبل «سجّل الكل» — `SmsReviewLine.fee`).
 *
 * - الرسوم **جوه** مبلغ الرسالة («إجمالي المبلغ المستحق») ⇒ العملية الأصلية = المبلغ − الرسوم، و`originalAmountMinor` = مبلغ الرسالة —
 *   ده الحقل اللي التطبيقين بيقارنوا بيه التكرار (§32)، فقراية نفس الرسالة تاني بتطلع **«مكررة»** مش «تعارض» ومن غير رسوم تانية.
 *   الرسوم **برّه** المبلغ ⇒ الأصلية زي ما هي.
 * - **عقد الترتيب:** الأثر ده بعد الآثار اللي بتغيّر المبلغ (`CashWithdrawalEffect` · المبلغ المحلي للشراء الأجنبي …): لو أثر قبله غيّر
 *   مبلغ العملية، الرسوم اللي **جوه** المبلغ ما بتتقسمش (مش عارفين المبلغ الجديد فيه الرسوم ولا لأ — قاعدة 10)، واللي **برّه** بتتكتب
 *   والأصلية ما بتتلمسش.
 * - عملية الرسوم: نوعها «رسوم» مؤكد · تصنيفها «رسوم بنكية» ([BankFeeCategory] — بيتعمل لو مش موجود) · مراجَعة · نوع العملية في المصدر
 *   «رسوم بنكية» (فزون التحويلات ما بيعتبرهاش تحويل) · سجل مصدر لوحده مرجعه = مرجع الرسالة + `#fee`.
 * - رسالة الرسوم نفسها ([SmsKind.FEE] — «خصم رسوم» · «Debit fees») ⇒ عمليتها نفسها بتبقى «رسوم» مؤكد. التصنيف: اللي المالك اختاره
 *   (دلوقتي، أو **للمحل ده قبل كده** — §75-16 · §36) بيفضل زي ما الشاشة عرضته؛ «رسوم بنكية» بس لو مفيش تصنيف من المالك.
 */
class SmsFeeEffect(private val categories: CategoryRepository) : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        val feeKind = ctx.lines.filter { it.sms?.kind == SmsKind.FEE && it.transaction.observedDirection == Direction.OUT }
        val withFee = ctx.lines.mapNotNull { line -> line.sms?.let(::smsFeeToRecord)?.let { line to it } }
        if (feeKind.isEmpty() && withFee.isEmpty()) return
        // «رسوم بنكية» بيتعمل بس لو فيه عملية هتتسجل تحته
        var bankFees: Id? = null
        val bankFeeCategory: suspend () -> Id = { bankFees ?: bankFeeCategoryId().also { bankFees = it } }
        for (line in feeKind) {
            val t = line.transaction
            val owners = ctx.chosenCategories[line.line.row.lineNumber] ?: t.categoryId?.takeIf { line.line.categorySource in OWNER_SOURCES }
            line.transaction = if (owners != null) {
                // تصنيف المالك: زي ما `ImportStatement` بناه (المختار مؤكد · المتفتكر للمحل زي أي محل متفتكر)
                t.copy(economicKind = EconomicKind.FEE, economicKindConfirmed = true, sourceOperationType = BankFeeCategory.NAME, updatedAt = ctx.nowIso)
            } else {
                t.copy(
                    economicKind = EconomicKind.FEE, economicKindConfirmed = true, categoryId = bankFeeCategory(), categoryConfirmed = true,
                    reviewState = ReviewState.CONFIRMED, sourceOperationType = BankFeeCategory.NAME, updatedAt = ctx.nowIso,
                )
            }
        }
        for ((line, fee) in withFee) split(ctx, line, fee, bankFeeCategory)
    }

    /** «رسوم بنكية» (بيتعمل لو مش موجود — اللي المالك غيّره ما يتكتبش فوقه). */
    private suspend fun bankFeeCategoryId(): Id {
        BankFeeCategory.existingId(categories.listAll())?.let { return it }
        val created = BankFeeCategory.default()
        categories.save(created)
        return created.id
    }

    private suspend fun split(ctx: RecordContext, line: RecordedLine, fee: SmsFee, category: suspend () -> Id) {
        val main = line.transaction
        val reference = line.line.row.reference ?: return
        if (fee.includedInAmount) {
            // الرسوم اتقرت على مبلغ الرسالة — أثر قبلنا غيّره ⇒ ما بنقسمش (عقد الترتيب فوق)
            if (main.amountMinor != line.line.row.amountMinor) return
            line.transaction = main.copy(amountMinor = subtractMoney(main.amountMinor, fee.amountMinor), originalAmountMinor = main.amountMinor, updatedAt = ctx.nowIso)
        }
        val txn = Transaction(
            id = ctx.ids.next("txn"),
            occurredAt = main.occurredAt,
            datePrecision = main.datePrecision,
            sourceOrder = main.sourceOrder,
            economicKind = EconomicKind.FEE,
            economicKindConfirmed = true,
            observedDirection = Direction.OUT,
            amountMinor = fee.amountMinor,
            currency = main.currency,
            categoryConfirmed = true,
            excludedFromBudget = false,
            reviewState = ReviewState.CONFIRMED,
            isCashTagged = false,
            createdAt = ctx.nowIso,
            updatedAt = ctx.nowIso,
            categoryId = category(),
            walletId = main.walletId,
            rawDescription = main.rawDescription,
            rawMerchantName = BankFeeCategory.NAME,
            sourceOperationType = BankFeeCategory.NAME,
        )
        val record = SourceRecord(
            id = ctx.ids.next("src"),
            batchId = ctx.batchId,
            accountIdentity = ctx.request.accountIdentity,
            sourceReference = reference + FEE_REFERENCE_SUFFIX,
            sourceHash = hashContent(line.line.row.raw),
            originalRowIndex = line.line.row.lineNumber,
            rawLine = line.line.row.raw,
            transactionId = txn.id,
            matchingState = MatchingState.NEW,
            reason = uiText(TextKey.SMS_FEE_SOURCE_REASON),
        )
        ctx.extra += txn to record
    }

    companion object {
        /** مرجع سجل مصدر الرسوم = مرجع الرسالة + ده (نص عادي — التطبيق القديم بيقراه زي أي مرجع). */
        const val FEE_REFERENCE_SUFFIX = "#fee"

        /** التصنيف جه من المالك: أكّده بنفسه · أو افتكره للمحل ده (§36 · §75-16). */
        private val OWNER_SOURCES = setOf(CategorizationSource.USER_CONFIRMED, CategorizationSource.VERIFIED_MERCHANT)
    }
}

/**
 * الرسوم اللي هتتسجل عملية «رسوم بنكية» لوحدها من صف الرسالة ده، أو null — [SmsFeeEffect] بيكتب ده بالظبط، والشاشة بتعرضه
 * (`SmsReviewLine.fee`) قبل «سجّل الكل». رسالة الرسوم نفسها مالهاش رسوم زيادة · من غير مرجع مفيش سجل للرسوم (ومنع التكرار) · الرسوم
 * جوه المبلغ: في الصادر بس ولازم يفضل للأصلية مبلغ.
 */
internal fun smsFeeToRecord(row: SmsRow): SmsFee? {
    val fee = row.fee ?: return null
    if (row.kind == SmsKind.FEE || row.reference == null || fee.amountMinor <= 0) return null
    if (fee.includedInAmount && (row.direction != Direction.OUT || fee.amountMinor >= row.amountMinor)) return null
    return fee
}
