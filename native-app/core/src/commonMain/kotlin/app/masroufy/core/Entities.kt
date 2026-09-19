package app.masroufy.core

/**
 * الكيانات — نقل `src/domain/entities/types.ts` بنفس الحقول ونفس أسماء التخزين في فايربيز.
 * أنواع بس من غير سلوك. المبالغ `Long` بالهللة و**موجبة دايمًا** (الاتجاه في `observedDirection`).
 */
typealias Id = String

enum class ReviewState(val wire: String) {
    CONFIRMED("confirmed"), SUGGESTED("suggested"), NEEDS_REVIEW("needs_review");

    companion object {
        fun fromWire(wire: String): ReviewState = entries.first { it.wire == wire }
    }
}

data class Transaction(
    val id: Id,
    val occurredAt: IsoDate,
    /** "day" أو "minute" — بعض المصادر بتدي اليوم من غير ساعة. */
    val datePrecision: String,
    val sourceOrder: Int,
    val economicKind: EconomicKind,
    /** المستخدم أكد النوع بنفسه ⇒ ما يتكتبش فوقه آليًا. */
    val economicKindConfirmed: Boolean,
    /** اتجاه السيولة في المصدر — حقيقة بنكية، ثابت مهما النوع اتغير. */
    val observedDirection: Direction,
    val amountMinor: Halalas,
    val currency: Currency,
    val categoryConfirmed: Boolean,
    val excludedFromBudget: Boolean,
    val reviewState: ReviewState,
    val isCashTagged: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val sourceTime: String? = null,
    /** المبلغ الأصلي من المصدر لو المستخدم عدّل المبلغ (OVERRIDES §32). */
    val originalAmountMinor: Halalas? = null,
    val merchantId: Id? = null,
    val categoryId: Id? = null,
    val note: String? = null,
    val walletId: Id? = null,
    /** الطرف التاني في التحويل الداخلي (ARCHITECTURE §17). */
    val transferToWalletId: Id? = null,
    /** الرصيد اللي أعلنه المصدر بعد الحركة (OVERRIDES §7-ب). */
    val statedBalanceMinor: Halalas? = null,
    val rawDescription: String? = null,
    val rawMerchantName: String? = null,
    val sourceCategory: String? = null,
    val sourceOperationType: String? = null,
)

enum class AllocationKind(val wire: String) {
    RECEIVABLE("receivable"), GIFT("gift");

    companion object {
        fun fromWire(wire: String): AllocationKind = entries.first { it.wire == wire }
    }
}

data class PersonAllocation(
    val id: Id,
    val transactionId: Id,
    val personId: Id,
    val allocationKind: AllocationKind,
    val amountMinor: Halalas,
    val currency: Currency,
)

enum class ObligationKind(val wire: String) {
    RECEIVABLE("receivable"), LOAN_PAYABLE("loan_payable"), CUSTODY_PAYABLE("custody_payable");

    companion object {
        fun fromWire(wire: String): ObligationKind = entries.first { it.wire == wire }
    }
}

data class Obligation(
    val id: Id,
    val personId: Id,
    /** null = دين قديم من كاش قبل التطبيق (OVERRIDES §27). */
    val originTransactionId: Id?,
    val kind: ObligationKind,
    val originalMinor: Halalas,
    val currency: Currency,
)

data class Settlement(val id: Id, val transactionId: Id, val obligationId: Id, val amountMinor: Halalas)
