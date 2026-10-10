package app.masroufy.core

/**
 * §82-20 (المالك 2026-10-10): **عدد الأقساط × القسط ⇒ الإجمالي** — والتقسيط بخدمات «اشتري دلوقتي وادفع بعدين» (تابي · تمارا) بكلام المالك:
 * «لو حد اشتري مرتبة مثلا ب1000 ريال و اختار تابي علي4 معناها انه بالفعل دفع 250 و فاضل 750 علي 3 مرات».
 * - الإجمالي = القسط × العدد بالوحدة الصغرى (من غير كسور ولا تقريب).
 * - «اشتري دلوقتي وادفع بعدين»: **أول قسط يوم الشراء** (= أول ميعاد في الجدول) والباقي أقساط جاية. القسط **مصروف وقت ما يتدفع** (§56) —
 *   يعني بعمليته اللي اتربطت بالخطة (`ManageInstallments.saveCounted` بيربط عملية أول قسط لو اتحددت). من غير عملية مربوطة القسط الأول
 *   بيفضل «مستحق يوم الشراء» (القاعدة 10: مدفوع من غير مصدر = رقم من غير مصدر)، واقتراح الربط (§75-8) بيلاقي عمليته.
 */
data class InstallmentCountPreview(
    val count: Int,
    val installmentMinor: Halalas,
    val totalMinor: Halalas,
    /** «اشتري دلوقتي وادفع بعدين» بس: أول قسط (وقت الشراء) · الباقي · عدد الأقساط الجاية. null للخطة العادية. */
    val paidAtPurchaseMinor: Halalas? = null,
    val leftMinor: Halalas? = null,
    val upcomingCount: Int? = null,
)

/** الإجمالي من العدد والقسط — العدد من 1 لـ[DUE_MAX_INSTALLMENTS] والقسط أكبر من صفر، وإلا خطأ برسالته (مش صفر). */
fun totalFromCount(installmentMinor: Halalas, count: Int): Halalas {
    assertHalalas(installmentMinor)
    if (installmentMinor <= 0) throw DueScheduleError(uiText(TextKey.DUE_INSTALLMENT_POSITIVE))
    if (count !in 1..DUE_MAX_INSTALLMENTS) throw DueScheduleError(uiText(UiKey.INST_ERR_COUNT, DUE_MAX_INSTALLMENTS.toString()))
    return multiplyMoneyByInt(installmentMinor, count.toLong())
}

/** المعاينة اللي النموذج بيعرضها وإنت بتكتب: «الإجمالي 1,000 = 4 × 250» و(لـ«ادفع بعدين») «دفعت 250 وقت الشراء والباقي 750 على 3». */
fun installmentCountPreview(installmentMinor: Halalas, count: Int, payLater: Boolean): InstallmentCountPreview {
    val total = totalFromCount(installmentMinor, count)
    if (!payLater) return InstallmentCountPreview(count, installmentMinor, total)
    return InstallmentCountPreview(count, installmentMinor, total, installmentMinor, subtractMoney(total, installmentMinor), count - 1)
}
