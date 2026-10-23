package app.masroufy.core

/**
 * جهة الشغل في البلدين = **جهة واحدة بمبلغين** (قرار المالك — OVERRIDES §111-2 · ARCHITECTURE «جهة الشغل في البلدين»).
 *
 * الاختيار الأقل تدخلًا: مصادر الدخل بتفضل **لكل بلد** (حساباتها وقواعدها ويوم قبضها وعملتها جوه البلد — زي ما هي)، والربط
 * «هوية» بس: مصدرين في بلدين بنفس [employerIdentity] = جهة واحدة. الهوية = [IncomeSource.employerKey] لو اتربطوا بإيد المالك،
 * وإلا الاسم المطبّع (`normalizedName`) — فنفس الاسم في البلدين بيترابط لوحده من غير ما يكتب حاجة.
 * المبلغ لكل عملة = «المرتب المتوقع» بتاع مصدر البلد دي (`expectedMinor`) — **مفيش جمع بين عملتين**.
 */
fun employerIdentity(source: IncomeSource): String = source.employerKey ?: source.normalizedName

/** مبلغ الجهة في بلد بعملتها. [expectedMinor] null = ما اتكتبش (القاعدة 10 — مش صفر). */
data class EmployerCountryPay(val spaceId: String, val sourceId: Id, val currency: Currency, val expectedMinor: Halalas?)

data class LinkedEmployer(val key: String, val name: String, val pays: List<EmployerCountryPay>) {
    /** شغال في أكتر من بلد. */
    val inBothCountries: Boolean get() = pays.map { it.spaceId }.distinct().size > 1

    fun payIn(currency: Currency): Halalas? = pays.filter { it.currency == currency }.mapNotNull { it.expectedMinor }.takeIf { it.isNotEmpty() }?.let { addMoney(*it.toLongArray()) }
}

/** جهات الشغل الشغالة النهارده في كل البلاد، متجمعة بالهوية — سطر لكل بلد بعملته. ترتيب ثابت: البلد الأصلية الأول ثم الاسم. */
fun linkedEmployers(sourcesBySpace: Map<String, List<IncomeSource>>, today: IsoDate): List<LinkedEmployer> {
    val rows = sourcesBySpace.flatMap { (spaceId, list) -> sourcesActiveOn(list, today).filter(::isEmployer).map { spaceId to it } }
    return rows.groupBy { employerIdentity(it.second) }.map { (key, group) ->
        val ordered = group.sortedWith(compareBy<Pair<String, IncomeSource>> { it.first != DEFAULT_SPACE_ID }.thenBy { it.first }.thenBy { it.second.id })
        LinkedEmployer(key, ordered.first().second.name, ordered.map { (sp, s) -> EmployerCountryPay(sp, s.id, s.currency, s.expectedMinor) })
    }.sortedWith(compareBy<LinkedEmployer> { e -> e.pays.none { it.spaceId == DEFAULT_SPACE_ID } }.thenBy { it.key })
}
