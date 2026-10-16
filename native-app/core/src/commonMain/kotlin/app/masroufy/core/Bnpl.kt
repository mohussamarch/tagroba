package app.masroufy.core

/**
 * r05 — «اشتري دلوقتي وادفع بعدين» (BNPL) من رسايل البنك. كلام المالك (2026-10-16): «تابي وتمارا وكل المؤسسات اللي بتقدم حلول مالية زيهم
 * المفروض يعرف علطول العملية ويسأله عنها: اشتريت إيه وقسمتها على كام، يحسب أوتوماتيك إن عليه كذا في الأقساط ويفكّره». §82-20: **أول قسط
 * بيتدفع وقت الشراء** والباقي أقساط جاية.
 *
 * الحساب نفسه هو قواعد الأقساط الموجودة (`Installments.kt` — تقسيط مشتريات): كل قسط **مصروف** يوم ما يتدفع، والمشترى نفسه ما بيتسجلش
 * عملية لوحده ⇒ مفيش عدّ مرتين. الملف ده **التعرّف والمطابقة بس** (من غير مستودع ولا شاشة).
 *
 * [counts] = اختيارات «قسمتها على كام؟» (الأول المعتاد) · [defaultCount] = المختار أول ما اللوحة تفتح. الاسم بالحروف اللاتينية أو العربي
 * المميّز بس — «مدفوع» و«سهولة» كلمات عادية، فـ«Madfu» و«Souhoola» بالإنجليزي بس.
 */
enum class BnplProvider(val brand: String, val counts: List<Int>, val defaultCount: Int, pattern: String) {
    TABBY("Tabby", listOf(4, 6, 12), 4, "tabby|تابي"),
    TAMARA("Tamara", listOf(3, 4, 6), 4, "tamara|تمارا"),
    MADFU("Madfu", listOf(4), 4, "madfu"),
    MISPAY("MISpay", listOf(3, 4), 4, "mis ?pay|مس ?باي"),
    POSTPAY("Postpay", listOf(3, 4), 3, "postpay"),
    SPOTII("Spotii", listOf(4), 4, "spotii"),
    VALU("valU", listOf(3, 6, 9, 12), 6, "valu|فاليو"),
    SYMPL("Sympl", listOf(3, 6), 3, "sympl|سيمبل"),
    SOUHOOLA("Souhoola", listOf(3, 6, 9, 12), 6, "souhoola"),
    ;

    internal val regex = Regex("(?<![a-z\\u0600-\\u06FF])(?:$pattern)(?![a-z\\u0600-\\u06FF])")
}

/** مزوّد «ادفع بعدين» من اسم التاجر في الرسالة أو اسم الجهة في خطة الأقساط — null = مش واحد منهم. */
fun bnplProviderOf(name: String?): BnplProvider? {
    val key = shapeKey(name ?: return null)
    if (key.isEmpty()) return null
    return BnplProvider.entries.firstOrNull { it.regex.containsMatchIn(key) }
}

/** «كل قد إيه؟» — كل شهر (الافتراضي) أو كل أسبوعين. */
enum class BnplCycle(val weeks: Int?) { MONTHLY(null), TWO_WEEKS(2) }

/** خطة مفتوحة لنفس المزوّد: [remainingMinor] = الباقي عليها · [nextDueAt] = القسط الجاي (null = خلصت). */
data class BnplOpenPlan(val plan: InstallmentPlan, val remainingMinor: Halalas, val nextDueAt: IsoDate?)

/** قسط واحد ممكن يختلف عن قسط الخطة بهللة واحدة (تقريب الإجمالي على العدد). */
const val BNPL_AMOUNT_TOLERANCE: Halalas = 1

/** رسالة خصم من مزوّد «ادفع بعدين» تبع إيه؟ */
sealed interface BnplMatch {
    /** مفيش خطة مفتوحة للمزوّد ده ⇒ مشترى جديد: الخصم ده **أول قسط** ⇒ «اشتريت إيه؟ قسمتها على كام؟». */
    data object NewPurchase : BnplMatch

    /** القسط الجاي في الخطة دي (نفس المبلغ ±هللة) ⇒ يتربط لوحده من غير سؤال. */
    data class NextInstallment(val planId: Id) : BnplMatch

    /** فيه خطط مفتوحة بس المبلغ مش قسط أي واحدة ⇒ «ده قسط من أنهي مشترى؟» أو «مشترى جديد». */
    data class AskWhich(val planIds: List<Id>) : BnplMatch
}

/**
 * المطابقة: خطط [provider] المفتوحة (فيها باقي) ⇒ اللي قسطها = [amountMinor] ±[BNPL_AMOUNT_TOLERANCE] ⇒ أقرب قسط جاي (لو أكتر من خطة بنفس
 * القسط). مفيش خطط ⇒ [BnplMatch.NewPurchase]. فيه خطط والمبلغ مختلف ⇒ [BnplMatch.AskWhich] (ما بنخمّنش).
 */
fun bnplMatch(provider: BnplProvider, amountMinor: Halalas, currency: Currency, open: List<BnplOpenPlan>): BnplMatch {
    assertHalalas(amountMinor)
    val mine = open.filter { bnplProviderOf(it.plan.provider) == provider && it.remainingMinor > 0 && it.plan.currency == currency }
    if (mine.isEmpty()) return BnplMatch.NewPurchase
    val same = mine.filter { kotlin.math.abs(it.plan.installmentMinor - amountMinor) <= BNPL_AMOUNT_TOLERANCE && amountMinor <= it.remainingMinor + BNPL_AMOUNT_TOLERANCE }
    val best = same.minWithOrNull(compareBy({ it.nextDueAt ?: "9999-12-31" }, { it.plan.firstDueAt }, { it.plan.id }))
    return if (best != null) BnplMatch.NextInstallment(best.plan.id) else BnplMatch.AskWhich(mine.map { it.plan.id })
}

/** الجزء من الخصم اللي بيتحسب على الخطة: المبلغ كله، إلا لو أكبر من الباقي بهللة التقريب ⇒ الباقي بالظبط. */
fun bnplLinkAmount(amountMinor: Halalas, remainingMinor: Halalas): Halalas = minOf(amountMinor, remainingMinor)
