package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.assertHalalas
import app.masroufy.core.formatMoney
import app.masroufy.core.uiText
import app.masroufy.core.Category
import app.masroufy.core.DuesCategories
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.ZakatPaymentRepository

/**
 * ربط عملية من الكشف بجمعية أو بخطة أقساط — الفحص المشترك قبل الكتابة.
 * العملية الواحدة **ما تتربطش بحاجتين**: لو اتربطت بجمعية وبقسط (أو بمبلغ تمويل مستلم، أو بدفعة زكاة — §62)، نفس الفلوس هتتعد مرتين.
 */
class DueLinkError(message: String) : IllegalArgumentException(message)

internal class DueLinks(
    private val txns: TransactionRepository,
    private val roscaEntries: RoscaEntryRepository,
    private val payments: InstallmentPaymentRepository,
    private val plans: InstallmentPlanRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
    /** دفعات الزكاة (§62) — اختياري عشان اللي ما بيستعملش الزكاة يفضل زي ما هو؛ التشغيل الحقيقي بيدّيه دايمًا. */
    private val zakatPayments: ZakatPaymentRepository? = null,
    /** روابط الأحداث (§64) — العملية اللي بقت نقطة ما تتربطش بالمستحقات أو الزكاة (نوعها كان هيتكتب فوقه). */
    private val eventLinks: EventLinkRepository? = null,
    /** رجول التحويل لنفسك في البلد دي (§64) — الرجل ما تبقاش قسط ولا جمعية ولا زكاة. التشغيل الحقيقي بيدّيه. */
    private val spaceLegs: app.masroufy.port.SpaceTransferLegs? = null,
) {
    /** العملية بعد الفحص + المبلغ اللي هيتربط (المبلغ كله لو ما اتحددش). */
    suspend fun check(
        transactionId: Id,
        direction: Direction,
        currency: Currency,
        ownerName: String,
        amountMinor: Halalas?,
        needsIn: TextKey = TextKey.DUE_TXN_NEEDS_IN,
    ): Pair<Transaction, Halalas> {
        val txn = txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw DueLinkError(uiText(TextKey.DUE_TXN_NOT_FOUND))
        if (txn.observedDirection != direction) {
            throw DueLinkError(uiText(if (direction == Direction.OUT) TextKey.DUE_TXN_NEEDS_OUT else needsIn))
        }
        if (txn.currency != currency) throw DueLinkError(uiText(TextKey.DUE_TXN_CURRENCY, ownerName))
        val ids = listOf(transactionId)
        if (spaceLegs?.isLeg(transactionId) == true) throw DueLinkError(uiText(TextKey.SPACE_TRANSFER_LEG_LOCKED))
        if (roscaEntries.listByTransactionIds(ids).isNotEmpty() || payments.listByTransactionIds(ids).isNotEmpty() ||
            plans.listAll().any { it.receivedTransactionId == transactionId } ||
            zakatPayments?.listByTransactionIds(ids).orEmpty().isNotEmpty() ||
            eventLinks?.listByTransactionIds(ids).orEmpty().any { it.role.isGift }
        ) {
            throw DueLinkError(uiText(TextKey.DUE_TXN_ALREADY_LINKED))
        }
        val amount = amountMinor ?: txn.amountMinor
        assertHalalas(amount)
        // جزء من العملية مسموح (تحويل فيه القسط وحاجة تانية)، أكتر منها لأ
        if (amount <= 0) throw DueLinkError(uiText(TextKey.DUE_AMOUNT_POSITIVE))
        if (amount > txn.amountMinor) throw DueLinkError(uiText(TextKey.DUE_AMOUNT_OVER_TXN, formatMoney(txn.amountMinor, txn.currency)))
        return txn to amount
    }

    /**
     * النوع والتصنيف بيتأكدوا عشان محدش يرجع يسأل عليهم، والعملية بتخرج من «محتاجة مراجعة».
     * التصنيف = فرع «المستحقات» بتاعها (قرار المالك §56) — والتصنيفات دي بتتعمل لو مش موجودة بس (اللي المستخدم غيّره ما يتكتبش فوقه).
     */
    suspend fun markKind(transactionId: Id, kind: EconomicKind, categoryId: Id, ensure: List<Category> = DuesCategories.defaults()) {
        categories.ensureAll(ensure)
        txns.update(
            transactionId,
            TransactionPatch(
                economicKind = kind, economicKindConfirmed = true, categoryId = categoryId, categoryConfirmed = true,
                reviewState = ReviewState.CONFIRMED, clearSuggestedKind = true, updatedAt = clock.nowIso(),
            ),
        )
    }

    /** فك الربط: النوع والتصنيف بيرجعوا «لسه ما اتحددش» ويتسألوا تاني — ما بنخمّنش القديم. */
    suspend fun clearKind(transactionId: Id) {
        txns.update(
            transactionId,
            TransactionPatch(
                economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, clearCategoryId = true, categoryConfirmed = false,
                reviewState = ReviewState.NEEDS_REVIEW, updatedAt = clock.nowIso(),
            ),
        )
    }
}

/**
 * التصنيفات الثابتة (المستحقات · الزكاة · النقوط) بتتعمل **لو مش موجودة بس** — اللي المستخدم غيّره ما يتكتبش فوقه.
 * [wanted] بالترتيب: الأب قبل الفرع.
 */
internal suspend fun CategoryRepository.ensureAll(wanted: List<Category>) {
    val existing = listAll().map { it.id }.toSet()
    for (c in wanted) if (c.id !in existing) save(c)
}
