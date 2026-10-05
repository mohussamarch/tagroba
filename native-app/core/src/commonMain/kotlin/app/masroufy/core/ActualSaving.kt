package app.masroufy.core

/**
 * «اللي بتحوّشه فعلًا» — المقارنة في حاسبة الادخار (قرار المالك §69: «يقارن باللي بتحوّشه فعلًا — آخر 3 شهور»).
 *
 * **تعريف «التحويش» في شهر مالي واحد (من يوم الراتب لليوم اللي قبل الراتب الجاي):**
 * > التحويش = **الدخل الحقيقي** − **المصروف الحقيقي**
 * - **الدخل الحقيقي** = اللي نوعه دخل (`countsAsIncome`: راتب · مكافأة · عمولة · عمل حر · هدية · دعم · عائد استثمار · …)
 *   **من غير** النقوط ومكافأة نهاية الخدمة (`NOT_IN_INCOME_AVERAGES` — دخل مرة واحدة، فما يدخلش متوسط شهري — نفس قاعدة مقارنة الدخل §47).
 * - **المصروف الحقيقي** = نصيبك من المصروف (`personalShareOf` — اللي على غيرك كدين برا) **+ المستبعد من الميزانية** (اتصرف فعلًا)،
 *   ناقص الاسترداد — نفس `computePeriodTotals` ونفس تعريف «المصروف الحقيقي» في مقارنة الدخل (`IncomeComparison.kt`).
 * - **برا الحسبة تمامًا** (لا دخل ولا مصروف): التحويل بين محافظك (`INTERNAL_TRANSFER`) · **التحويل لنفسك بين بلدين** (رجليه «تحويل داخلي»
 *   مؤكد §64) · السلف والديون والأمانات (اللي خدته أو ادّيته أو سدّدته) · شراء وبيع الأصول (شراء دهب = فلوس اتحوّشت في شكل تاني، مش
 *   مصروف) · الاسترداد بينقّص المصروف بس.
 * - **بعملة البلد بس** (عملية بعملة تانية ما بتتحوّلش بسعر صرف — القاعدة 1 و10).
 *
 * **الشهر «مش معروف» (القاعدة 10 ⇒ المتوسط كله «غير متاح»):** مفيش ولا عملية فيه، **أو** فيه عملية لسه نوعها ما اتحددش
 * (ممكن تطلع دخل أو مصروف). نفس شرط «المعتاد» في المساعد المالي (§68).
 */
const val ACTUAL_SAVING_MONTHS = 3

/** تحويش شهر واحد من عملياته (بعد النوع التقديري للواضح). null = الشهر مش معروف. ممكن يطلع **سالب** (صرفت أكتر من دخلك) — وده رقم معروف. */
fun monthSavingMinor(transactions: List<Transaction>, allocations: List<PersonAllocation>): Halalas? {
    if (transactions.isEmpty() || transactions.any { it.economicKind == EconomicKind.UNCLASSIFIED }) return null
    val incomeTotals = computePeriodTotals(transactions.filter { it.economicKind !in NOT_IN_INCOME_AVERAGES }, allocations)
    val all = computePeriodTotals(transactions, allocations)
    return subtractMoney(incomeTotals.incomeMinor, addMoney(all.personalExpenseMinor, all.excludedExpenseMinor))
}

/**
 * متوسط التحويش في الشهر على [ACTUAL_SAVING_MONTHS] شهور: أي شهر null ⇒ null (مش متوسط الشهور المعروفة بس — القاعدة 10).
 * القسمة بالتقريب لأقرب هللة (النص لبعيد عن الصفر)، والسالب بيفضل سالب.
 */
fun averageSavingMinor(months: List<Halalas?>): Halalas? {
    if (months.size < ACTUAL_SAVING_MONTHS || months.any { it == null }) return null
    val sum = sumMoney(months.map { it!! })
    return mulDivHalfUp(sum, 1, months.size.toLong())
}

enum class SavingVerdict {
    /** اللي بتحوّشه فعلًا ≥ المطلوب. */
    ENOUGH,
    /** أقل من المطلوب بـ[SavingComparison.gapMinor]. */
    SHORT,
    /** اللي بتحوّشه فعلًا مش معروف ⇒ مفيش حكم. */
    UNKNOWN,
}

/** المطلوب في الشهر قدام المتوسط الفعلي. [gapMinor] = الفعلي − المطلوب (سالب = ناقصك) — null لو الفعلي مش معروف. */
data class SavingComparison(val requiredMinor: Halalas, val actualMinor: Halalas?, val gapMinor: Halalas?, val verdict: SavingVerdict)

fun compareWithActual(requiredMinor: Halalas, actualMinor: Halalas?): SavingComparison {
    if (actualMinor == null) return SavingComparison(requiredMinor, null, null, SavingVerdict.UNKNOWN)
    val gap = subtractMoney(actualMinor, requiredMinor)
    return SavingComparison(requiredMinor, actualMinor, gap, if (gap >= 0) SavingVerdict.ENOUGH else SavingVerdict.SHORT)
}
