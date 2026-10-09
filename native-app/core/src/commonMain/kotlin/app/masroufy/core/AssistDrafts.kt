package app.masroufy.core

/**
 * كروت التأكيد اللي المساعد بيطلّعها قبل أي كتابة: **عملية** (إضافة بالكتابة §78-٣) و**تقسيم فاتورة**. ولا حاجة بتتكتب قبل «احفظ» —
 * والحفظ نفسه بحالة الاستخدام العادية للإضافة (`AddTransaction`) والربط بالأشخاص (`ManagePeople`).
 */
data class TxnDraft(
    /** موجب دايمًا، بالوحدة الصغرى. */
    val amountMinor: Halalas,
    val currency: Currency,
    val occurredAt: IsoDate,
    /** null = لسه مفيش محفظة أساسية ⇒ المساعد بيسأل «بتصرف عادةً منين؟» الأول. */
    val walletId: Id?,
    val categoryId: Id?,
    /** اسم العملية (المحل · التصنيف · الكلمة اللي كتبها). */
    val title: String,
    val merchantId: Id? = null,
    /** شراء (الافتراضي) أو رسوم («رسوم التحويل ١٥»). */
    val economicKind: EconomicKind = EconomicKind.PURCHASE,
    /** الفاتورة الدورية اللي الكلام بيتكلم عنها (محفظتها المعتادة). */
    val recurringId: Id? = null,
    /** عملية شبهها اتسجلت النهارده (نفس المبلغ) ⇒ تحذير على الكارت، ما بيمنعش. */
    val similarTransactionId: Id? = null,
    /** المستخدم غيّر التصنيف على الكارت ⇒ بيتحفظ للمحل لما يأكد (§75-16). */
    val categoryChanged: Boolean = false,
)

data class SplitShare(val personId: Id?, val name: String, val amountMinor: Halalas, val me: Boolean = false)

data class SplitDraft(
    val totalMinor: Halalas,
    val currency: Currency,
    val occurredAt: IsoDate,
    val walletId: Id?,
    val categoryId: Id?,
    val title: String,
    val shares: List<SplitShare>,
    /** عملية موجودة هتتقسم (بدل ما تتسجل جديدة). */
    val existingTransactionId: Id? = null,
)

/**
 * التقسيم بأعداد صحيحة: كل واحد [total] ÷ [heads]، و**الباقي (الهللات) على نصيبك إنت** (التصميم: «remainder halalas on your own share»).
 * أول نصيب = نصيبك. مجموع الأنصبة = الإجمالي بالظبط.
 */
fun splitShares(total: Halalas, heads: Int): List<Halalas> {
    require(heads >= 2) { "split needs two people or more" }
    require(total > 0) { "split amount must be positive" }
    val each = total / heads
    val rest = total - each * heads
    return listOf(each + rest) + List(heads - 1) { each }
}

/** من أين المحفظة اتختارت — للعرض والاختبار. */
enum class WalletSource { NAMED_IN_TEXT, RECURRING_BILL, MAIN_WALLET, NONE }

/**
 * محفظة المصروف المكتوب (رد المالك ٢ — 2026-10-09 + الرد التاني «الفاتورة الدورية على محفظتها المعتادة»):
 * المذكورة في الكلام ⇒ محفظة الفاتورة الدورية (محفظة آخر دفعة ليها) ⇒ المحفظة الأساسية للبلد ⇒ مفيش (⇒ «بتصرف عادةً منين؟» مرة واحدة).
 */
fun resolveSpendWallet(named: Id?, recurringWallet: Id?, mainWallet: Id?): Pair<Id?, WalletSource> = when {
    named != null -> named to WalletSource.NAMED_IN_TEXT
    recurringWallet != null -> recurringWallet to WalletSource.RECURRING_BILL
    mainWallet != null -> mainWallet to WalletSource.MAIN_WALLET
    else -> null to WalletSource.NONE
}

/**
 * عملية شبه المكتوبة اتسجلت **نفس اليوم بنفس المبلغ** (خارجة) — تحذير على الكارت بس (اختيار Claude: نفس فكرة «شبه عملية موجودة» في
 * منع التكرار §36، من غير ما نمنع — ممكن يكون اشترى قهوتين).
 */
fun similarSameDay(existing: List<Transaction>, date: IsoDate, amountMinor: Halalas, currency: Currency): Transaction? =
    existing.firstOrNull { it.occurredAt.take(10) == date && it.amountMinor == amountMinor && it.currency == currency && it.observedDirection == Direction.OUT }
