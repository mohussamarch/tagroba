package app.masroufy.usecase

import app.masroufy.core.BankFeeCategory
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsFee
import app.masroufy.core.SmsKind
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.hashContent
import app.masroufy.core.subtractMoney
import app.masroufy.core.uiText
import app.masroufy.port.CategoryRepository

/**
 * الرسوم = **عملية لوحدها «رسوم بنكية»** جنب العملية الأصلية (§77-B — قرار المالك 2026-10-09 ✓): الرسوم المكتوبة في رسالة البنك
 * ([app.masroufy.core.SmsRow.fee] — `SmsFees.kt`) بتتسجل عملية صرف تانية **في نفس الدفعة** (نفس اليوم والمحفظة والعملة)، فرصيد المحفظة
 * بيفضل مظبوط. عمرها ما بتتكتب من غير عمليتها الأصلية (نفس وحدة العمل)، والتراجع عن الدفعة بيمسح الاتنين.
 *
 * - الرسوم **جوه** مبلغ الرسالة («إجمالي المبلغ المستحق») ⇒ العملية الأصلية = المبلغ − الرسوم، و`originalAmountMinor` = مبلغ الرسالة —
 *   ده الحقل اللي التطبيقين بيقارنوا بيه التكرار (§32)، فقراية نفس الرسالة تاني بتطلع **«مكررة»** مش «تعارض» ومن غير رسوم تانية.
 *   الرسوم **برّه** المبلغ ⇒ الأصلية زي ما هي.
 * - عملية الرسوم: نوعها «رسوم» مؤكد · تصنيفها «رسوم بنكية» ([BankFeeCategory] — بيتعمل لو مش موجود) · مراجَعة · نوع العملية في المصدر
 *   «رسوم بنكية» (فزون التحويلات ما بيعتبرهاش تحويل) · سجل مصدر لوحده مرجعه = مرجع الرسالة + `#fee`.
 * - رسالة الرسوم نفسها ([SmsKind.FEE] — «خصم رسوم» · «Debit fees») ⇒ عمليتها نفسها بتبقى «رسوم» مؤكد تحت «رسوم بنكية» (لو المالك ما
 *   اختارش تصنيف تاني).
 */
class SmsFeeEffect(private val categories: CategoryRepository) : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        val feeKind = ctx.lines.filter { it.sms?.kind == SmsKind.FEE && it.transaction.observedDirection == Direction.OUT }
        val withFee = ctx.lines.filter { it.sms?.kind != SmsKind.FEE && it.sms?.fee != null }
        if (feeKind.isEmpty() && withFee.isEmpty()) return
        val categoryId = bankFeeCategoryId()
        for (line in feeKind) {
            val chosen = ctx.chosenCategories[line.line.row.lineNumber]
            line.transaction = line.transaction.copy(
                economicKind = EconomicKind.FEE, economicKindConfirmed = true, categoryId = chosen ?: categoryId, categoryConfirmed = true,
                reviewState = ReviewState.CONFIRMED, sourceOperationType = BankFeeCategory.NAME, updatedAt = ctx.nowIso,
            )
        }
        for (line in withFee) split(ctx, line, line.sms!!.fee!!, categoryId)
    }

    /** «رسوم بنكية» (بيتعمل لو مش موجود — اللي المالك غيّره ما يتكتبش فوقه). */
    private suspend fun bankFeeCategoryId(): Id {
        BankFeeCategory.existingId(categories.listAll())?.let { return it }
        val created = BankFeeCategory.default()
        categories.save(created)
        return created.id
    }

    private fun split(ctx: RecordContext, line: RecordedLine, fee: SmsFee, categoryId: Id) {
        val main = line.transaction
        val source = line.line.row.amountMinor
        // من غير مرجع الرسالة مفيش مرجع لسجل الرسوم (ومنع التكرار) ⇒ ما بنقسمش. الرسوم جوه المبلغ: لازم يفضل للأصلية مبلغ، وفي الصادر بس
        val reference = line.line.row.reference ?: return
        if (fee.amountMinor <= 0) return
        if (fee.includedInAmount) {
            if (main.observedDirection != Direction.OUT || fee.amountMinor >= source) return
            line.transaction = main.copy(amountMinor = subtractMoney(source, fee.amountMinor), originalAmountMinor = source, updatedAt = ctx.nowIso)
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
            categoryId = categoryId,
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
    }
}
