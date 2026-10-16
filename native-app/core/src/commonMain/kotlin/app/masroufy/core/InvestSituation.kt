package app.masroufy.core

/**
 * حالتك للنصيحة (مواصفة النقل §2.7 — Claude choice ❓ للترتيب والحدود، Q10). كل رقم من بياناتك؛ الناقص (null) ⇒ الحالة دي ما بتتحسبش
 * (مش بتتحقق بصفر مفترض — القاعدة 10).
 * - طوارئ: متوسط صرفك الشهري معروف وفلوسك المعروفة أقل من [EMERGENCY_MONTHS] أضعافه.
 * - كله في نوع واحد: اللي بيدخل التوقع أكبر من صفر، وأكبر نوع (عقار · دهب · أسهم · مش بيكبر) أكتر من [CONCENTRATION_PERCENT]%.
 * - بيحوّش ومش بيستثمر: متوسط التحويش أكبر من صفر ومفيش «شراء أصل» في آخر 3 شهور.
 * - فيه أرباح بترجع: مجموع «عائد استثمار» من أول السنة أكبر من صفر.
 */
const val EMERGENCY_MONTHS = 3
const val CONCENTRATION_PERCENT = 70

data class TipFacts(
    /** مجموع أرصدة المحافظ المعروفة (البنوك + الكاش) — null = ولا محفظة رصيدها معروف. */
    val cashMinor: Halalas?,
    /** متوسط الصرف الشهري لآخر 3 شهور كاملة — null = شهر منهم مش معروف. */
    val avgSpendMinor: Halalas?,
    /** null = «حاجاتي» ما اتحمّلتش. */
    val split: InvestableSplit?,
    /** متوسط التحويش لنفس الشهور — null = مش معروف. */
    val saveMinor: Halalas?,
    val boughtAssetLately: Boolean,
    val investIncomeThisYearMinor: Halalas,
)

fun situationsOf(f: TipFacts): List<TipSituation> = buildList {
    val cash = f.cashMinor
    val spend = f.avgSpendMinor
    if (cash != null && spend != null && spend > 0 && cash < spend * EMERGENCY_MONTHS) add(TipSituation.EMERGENCY)
    val split = f.split
    if (split != null && split.totalMinor > 0) {
        val largest = maxOf(split.propertyMinor, split.goldMinor, split.sharesMinor, split.noGrowthMinor)
        if (largest * 100 > split.totalMinor * CONCENTRATION_PERCENT) add(TipSituation.CONCENTRATED)
    }
    val save = f.saveMinor
    if (save != null && save > 0 && !f.boughtAssetLately) add(TipSituation.SAVES_NOT_INVESTING)
    if (f.investIncomeThisYearMinor > 0) add(TipSituation.HAS_RETURNS)
}

/**
 * صرف شهر واحد (نفس قواعد [monthSavingMinor]: الشهر الفاضي أو اللي فيه عملية نوعها مش محدد ⇒ null): المصروف الشخصي + المستبعد من
 * الميزانية. المتوسط على 3 شهور بـ[averageSavingMinor] (نفس القاعدة: أي شهر null ⇒ null).
 */
fun monthSpendMinor(transactions: List<Transaction>, allocations: List<PersonAllocation>): Halalas? {
    if (transactions.isEmpty() || transactions.any { it.economicKind == EconomicKind.UNCLASSIFIED }) return null
    val all = computePeriodTotals(transactions, allocations)
    return addMoney(all.personalExpenseMinor, all.excludedExpenseMinor)
}
