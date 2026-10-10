package app.masroufy.core

/**
 * قواعد كروت التأكيد اللي المساعد بيطلّعها قبل أي كتابة: **عملية** (إضافة بالكتابة §78-٣) و**تقسيم فاتورة**. ولا حاجة بتتكتب قبل «احفظ» —
 * والحفظ نفسه بحالة الاستخدام العادية للإضافة (`AddTransaction`) والربط بالأشخاص (`ManagePeople`).
 */

/**
 * التقسيم بأعداد صحيحة (التصميم `AssistSplit`): كل واحد [total] ÷ [heads]، و**الباقي (الهللات) على نصيبك إنت**. أول نصيب = نصيبك.
 * مجموع الأنصبة = الإجمالي بالظبط. (التقسيم من غيرك — «بين أحمد وسارة» بس — مش في أول نسخة: المساعد بيحسبك واحد منهم دايمًا.)
 */
fun splitShares(total: Halalas, heads: Int): List<Halalas> {
    require(heads >= 2) { "split needs two people or more" }
    require(total > 0) { "split amount must be positive" }
    val each = total / heads
    val rest = total - each * heads
    return listOf(each + rest) + List(heads - 1) { each }
}

/** من أين المحفظة اتختارت — للعرض والاختبار. */
enum class WalletSource { NAMED_IN_TEXT, RECURRING_BILL, MAIN_WALLET, ONLY_WALLET, NONE }

/**
 * محفظة المصروف المكتوب (رد المالك ٢ — 2026-10-09 + النافذة التانية: «الفاتورة الدورية على محفظتها المعتادة»):
 * المذكورة في الكلام ⇒ محفظة الفاتورة الدورية (محفظة آخر دفعة ليها) ⇒ المحفظة الأساسية للبلد ⇒ محفظة واحدة بس في البلد ⇒ مفيش
 * (⇒ «بتصرف عادةً منين؟» مرة واحدة).
 */
fun resolveSpendWallet(named: Id?, recurringWallet: Id?, mainWallet: Id?, onlyWallet: Id? = null): Pair<Id?, WalletSource> = when {
    named != null -> named to WalletSource.NAMED_IN_TEXT
    recurringWallet != null -> recurringWallet to WalletSource.RECURRING_BILL
    mainWallet != null -> mainWallet to WalletSource.MAIN_WALLET
    onlyWallet != null -> onlyWallet to WalletSource.ONLY_WALLET
    else -> null to WalletSource.NONE
}

/**
 * عملية شبه المكتوبة اتسجلت **نفس اليوم بنفس المبلغ** (خارجة) — تحذير على الكارت بس، ما بيمنعش (سؤال المالك ٨ — المقترح: ممكن يكون
 * اشترى قهوتين، أو العملية دي نفسها اتسجلت لوحدها من رسالة البنك §72).
 */
fun similarSameDay(existing: List<Transaction>, date: IsoDate, amountMinor: Halalas, currency: Currency): Transaction? =
    existing.filter { it.occurredAt.take(10) == date && it.amountMinor == amountMinor && it.currency == currency && it.observedDirection == Direction.OUT }
        .maxWithOrNull(compareBy<Transaction> { it.occurredAt }.thenBy { it.sourceOrder })
