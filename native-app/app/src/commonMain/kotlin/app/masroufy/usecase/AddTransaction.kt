package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Liquidity
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.assertHalalas
import app.masroufy.core.isConsistentWithObservedDirection
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.jsTrim
import app.masroufy.core.ruleFor
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * AddTransaction — نقل `addTransaction.ts`: إضافة عملية يدويًا (القهوة بالكاش ما بتوصلش من أي كشف).
 * الفرق عن الاستيراد: هنا المستخدم **عارف** عمل إيه، فالنوع بيتحدد وبيتأكد فورًا،
 * والاتجاه الملاحظ **بيشتق من النوع** — عكس الاستيراد بالظبط.
 * («المبلغ عدد صحيح» مش متفحصة هنا — نظام أنواع كوتلن بيمنعها وقت الترجمة.)
 */

data class NewTransactionInput(
    /** موجب دايمًا. الاتجاه من النوع الاقتصادي. */
    val amountMinor: Halalas,
    val currency: Currency? = null,
    val occurredAt: String,
    val walletId: Id,
    /** المحفظة المستقبِلة — إلزامية للتحويل الداخلي وحده. */
    val transferToWalletId: Id? = null,
    val economicKind: EconomicKind,
    val merchantName: String,
    val categoryId: Id? = null,
    val note: String? = null,
    val isCashTagged: Boolean? = null,
    val excludedFromBudget: Boolean? = null,
)

data class AddTransactionDeps(
    val txns: TransactionRepository,
    val wallets: WalletRepository,
    val ids: IdGenerator,
    val clock: Clock,
)

class AddTransaction(private val deps: AddTransactionDeps) {
    suspend fun add(input: NewTransactionInput): Transaction {
        if (input.amountMinor <= 0) {
            throw IllegalArgumentException("المبلغ لازم يكون أكبر من صفر. الاتجاه بيتحدد من نوع العملية.")
        }
        assertHalalas(input.amountMinor, "مبلغ العملية")

        if (!isValidIsoDate(input.occurredAt)) throw IllegalArgumentException("التاريخ مش صالح")

        val wallet = deps.wallets.findById(input.walletId) ?: throw IllegalArgumentException("اختار محفظة موجودة")

        if (input.economicKind == EconomicKind.UNCLASSIFIED) {
            throw IllegalArgumentException("لازم تحدد نوع العملية — انت عارف اشتريت ولا حوّلت")
        }

        // الاتجاه الملاحظ بيشتق من النوع هنا — في الاستيراد الكشف بيدي الاتجاه والنوع مجهول
        val observedDirection = if (ruleFor(input.economicKind).liquidity == Liquidity.IN) Direction.IN else Direction.OUT

        if (!isConsistentWithObservedDirection(input.economicKind, observedDirection)) {
            throw IllegalArgumentException("النوع ده ما يتوافقش مع اتجاه الحركة")
        }

        /*
         * التحويل الداخلي **لازم له طرفين** (spec/02) — من غير المحفظة المستقبِلة
         * الفلوس بتختفي من الحساب بلا أثر. بيترفض بدل ما يتحفظ ناقص.
         */
        var transferTo: Id? = null
        if (input.economicKind == EconomicKind.INTERNAL_TRANSFER) {
            val targetId = input.transferToWalletId
                ?: throw IllegalArgumentException("التحويل الداخلي لازم تحدد راح لأنهي محفظة")
            if (targetId == input.walletId) throw IllegalArgumentException("مينفعش تحوّل من محفظة لنفسها")
            val target = deps.wallets.findById(targetId) ?: throw IllegalArgumentException("المحفظة المستقبِلة مش موجودة")
            if (target.currency != wallet.currency) {
                throw IllegalArgumentException("التحويل بين عملتين مختلفتين محتاج سعر صرف موثّق — لسه مش مدعوم (spec/02)")
            }
            transferTo = target.id
        } else if (input.transferToWalletId != null) {
            throw IllegalArgumentException("المحفظة المستقبِلة تتحدد للتحويل الداخلي بس")
        }

        val name = jsTrim(input.merchantName)
        if (name.length > 120) throw IllegalArgumentException("اسم المتجر أطول من 120 حرف")
        if ((input.note ?: "").length > 1000) throw IllegalArgumentException("الملاحظة أطول من 1000 حرف")

        val now = deps.clock.nowIso()
        val note = input.note?.let { jsTrim(it) }?.takeIf { it.isNotEmpty() }
        val transaction = Transaction(
            id = deps.ids.next("txn"),
            occurredAt = input.occurredAt,
            datePrecision = "day",
            // العمليات اليدوية بعد أي عملية مستوردة في نفس اليوم
            sourceOrder = 9_000_000,
            economicKind = input.economicKind,
            // ✅ مؤكد فورًا: المستخدم اختاره بنفسه، فما يتكتبش فوقه آليًا (spec/05)
            economicKindConfirmed = true,
            observedDirection = observedDirection,
            amountMinor = input.amountMinor,
            currency = input.currency ?: wallet.currency,
            walletId = wallet.id,
            categoryConfirmed = input.categoryId != null,
            excludedFromBudget = input.excludedFromBudget ?: false,
            reviewState = if (input.categoryId != null) ReviewState.CONFIRMED else ReviewState.NEEDS_REVIEW,
            // الكاش بيتوسم تلقائيًا لما المحفظة كاش — الوسم شارة عرض مش مبلغ
            isCashTagged = input.isCashTagged ?: (wallet.kind == "cash"),
            rawMerchantName = name.ifEmpty { "بلا اسم" },
            createdAt = now,
            updatedAt = now,
            transferToWalletId = transferTo,
            categoryId = input.categoryId,
            note = note,
        )

        deps.txns.saveMany(listOf(transaction))
        return transaction
    }
}
