package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Liquidity
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.assertHalalas
import app.masroufy.core.isConsistentWithObservedDirection
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.jsTrim
import app.masroufy.core.ruleFor
import app.masroufy.core.uiText
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
            throw IllegalArgumentException(uiText(TextKey.TXN_AMOUNT_POSITIVE))
        }
        assertHalalas(input.amountMinor, uiText(TextKey.TXN_AMOUNT_LABEL))

        if (!isValidIsoDate(input.occurredAt)) throw IllegalArgumentException(uiText(TextKey.TXN_DATE_INVALID))

        val wallet = deps.wallets.findById(input.walletId) ?: throw IllegalArgumentException(uiText(TextKey.TXN_WALLET_REQUIRED))

        if (input.economicKind == EconomicKind.UNCLASSIFIED) {
            throw IllegalArgumentException(uiText(TextKey.TXN_KIND_REQUIRED))
        }

        // الاتجاه الملاحظ بيشتق من النوع هنا — في الاستيراد الكشف بيدي الاتجاه والنوع مجهول
        val observedDirection = if (ruleFor(input.economicKind).liquidity == Liquidity.IN) Direction.IN else Direction.OUT

        if (!isConsistentWithObservedDirection(input.economicKind, observedDirection)) {
            throw IllegalArgumentException(uiText(TextKey.TXN_KIND_DIRECTION))
        }

        /*
         * التحويل الداخلي **لازم له طرفين** (spec/02) — من غير المحفظة المستقبِلة
         * الفلوس بتختفي من الحساب بلا أثر. بيترفض بدل ما يتحفظ ناقص.
         */
        var transferTo: Id? = null
        if (input.economicKind == EconomicKind.INTERNAL_TRANSFER) {
            val targetId = input.transferToWalletId
                ?: throw IllegalArgumentException(uiText(TextKey.TXN_TRANSFER_TARGET_REQUIRED))
            if (targetId == input.walletId) throw IllegalArgumentException(uiText(TextKey.TXN_TRANSFER_SAME_WALLET))
            val target = deps.wallets.findById(targetId) ?: throw IllegalArgumentException(uiText(TextKey.TXN_TRANSFER_TARGET_MISSING))
            if (target.currency != wallet.currency) {
                throw IllegalArgumentException(uiText(TextKey.TXN_TRANSFER_CURRENCY))
            }
            transferTo = target.id
        } else if (input.transferToWalletId != null) {
            throw IllegalArgumentException(uiText(TextKey.TXN_TRANSFER_TARGET_ONLY_INTERNAL))
        }

        val name = jsTrim(input.merchantName)
        if (name.length > 120) throw IllegalArgumentException(uiText(TextKey.TXN_MERCHANT_TOO_LONG))
        if ((input.note ?: "").length > 1000) throw IllegalArgumentException(uiText(TextKey.NOTE_TOO_LONG, "1000"))

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
