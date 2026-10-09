package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.Currency
import app.masroufy.core.GrowthClass
import app.masroufy.core.Halalas
import app.masroufy.core.MAX_ANNUAL_RATE_BP
import app.masroufy.core.MIN_ANNUAL_RATE_BP
import app.masroufy.core.TextKey
import app.masroufy.core.describeEntry
import app.masroufy.core.uiText
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t
import app.masroufy.usecase.GrowthChoice
import app.masroufy.usecase.GrowthCompareOutcome

/**
 * «لو وضعتها في…» (`SavingsGrowth`) — من `CompareSavingsGrowth` لحالة القطعة. كل رقم (الناتج · الزيادة · بقيمة المال اليوم) جاي من حالة
 * الاستخدام؛ هنا الكلام وطول الشريط (نسبة عرض بس).
 */
data class GrowthRowUi(
    val growthClass: GrowthClass,
    val name: String,
    val rateText: String,
    /** المعدل مش معروف ⇒ السطر كهرماني ويطلب يكتبه. */
    val rateMissing: Boolean,
    /** null ⇒ «غير متاح» (مش صفر). */
    val amountMinor: Halalas?,
    /** الزيادة فوق اللي حطيته — null لو صفر أو «بقيمة المال اليوم» شغال (حالة الاستخدام مش بتدّيها مخصومة). */
    val gainMinor: Halalas?,
    /** طول الشريط من 100 (0 = مفيش شريط). */
    val barPercent: Int,
    val source: String,
    val note: String?,
    val stale: Boolean,
    val editLabel: String,
    val editEnabled: Boolean,
    /** «اكتب النسبة» (مفيش معدل) ⇒ زرار أخضر مليان. */
    val editStrong: Boolean,
    val canReset: Boolean,
    /** المعدل الحالي (للوحة التعديل). */
    val rateBp: Int?,
)

data class GrowthUi(
    val paidLine: String,
    val todayOn: Boolean,
    val todaySub: String,
    val todaySubWarn: Boolean,
    val rows: List<GrowthRowUi>,
)

fun growthUi(outcome: GrowthCompareOutcome, currency: Currency): GrowthUi {
    val c = outcome.comparison
    // «بقيمة المال اليوم» شغال والتضخم مش معروف ⇒ الأرقام زي ما هي والسطر تحت الزرار بيقول «غير متاح» (زي النموذج)
    val deflated = c.todayMoney && c.inflationBp != null
    val shown = c.lines.associate { it.growthClass to if (deflated) it.todayMoneyMinor else it.reachedMinor }
    val top = shown.values.filterNotNull().maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val rows = outcome.choices.map { choice -> row(choice, shown[choice.line.growthClass], top, deflated) }
    val inflation = outcome.inflation
    return GrowthUi(
        paidLine = t(TextKey.SAVGROW_PAID, money(c.paidInMinor, currency), durationPhrase(c.months)),
        todayOn = c.todayMoney,
        todaySub = if (inflation == null) t(TextKey.GROWTH_NA_INFLATION) else t(TextKey.SAVGROW_TODAY_SUB, percentText(inflation.valueBp)),
        todaySubWarn = inflation == null,
        rows = rows,
    )
}

private fun row(choice: GrowthChoice, shownMinor: Halalas?, top: Long, todayMoney: Boolean): GrowthRowUi {
    val cls = choice.line.growthClass
    val d = choice.default
    val rate = choice.line.rateBp
    val cash = cls == GrowthClass.CASH
    val why = when {
        cls == GrowthClass.DEPOSIT && d.userMustType -> uiText(TextKey.GROWTH_TYPE_BANK_RATE)
        d.userMustType -> uiText(TextKey.GROWTH_TYPE_YOUR_RATE)
        else -> d.reason ?: uiText(TextKey.GROWTH_NA_MISSING)
    }
    val info = d.info
    val source = when {
        rate == null -> ""
        !choice.typedByUser && cls == GrowthClass.DEPOSIT && info != null -> t(TextKey.SAVGROW_INFO, describeEntry(info), percentText(info.valueBp))
        else -> choice.sourceText
    }
    val gain = if (todayMoney) null else choice.line.gainMinor?.takeIf { it != 0L }
    return GrowthRowUi(
        growthClass = cls,
        name = cls.label,
        rateText = rate?.let { t(TextKey.SAVGROW_RATE_YEARLY, percentText(it)) } ?: why,
        rateMissing = rate == null,
        amountMinor = shownMinor,
        gainMinor = gain,
        barPercent = shownMinor?.let { maxOf(2L, it * 100 / top).toInt() } ?: 0,
        source = source,
        note = choice.note?.text,
        stale = d.stale && !choice.typedByUser && !cash,
        editLabel = t(
            when {
                cash -> TextKey.SAVGROW_CASH_FIXED
                rate == null -> TextKey.SAVGROW_TYPE_RATE
                else -> TextKey.SAVGROW_EDIT_RATE
            },
        ),
        editEnabled = !cash,
        editStrong = !cash && rate == null,
        canReset = choice.typedByUser && d.rateBp != null,
        rateBp = rate,
    )
}

/** النسبة اللي المستخدم كتبها («14.88» · «٤٫٥» · «-2») ⇒ نقاط أساس — نفس قراية المبلغ (خانتين عشريتين = نقاط الأساس بالظبط). */
fun parseRateBp(text: String): Int? {
    val cleaned = text.filterNot { it == '%' || it == '٪' || it.isWhitespace() }
    val bp = parseAmountField(cleaned, Currency.SAR).orNull ?: return null
    return bp.takeIf { it in MIN_ANNUAL_RATE_BP.toLong()..MAX_ANNUAL_RATE_BP.toLong() }?.toInt()
}

/** المعدل في الخانة وقت فتح لوحة التعديل («14.88»). */
fun rateInputText(bp: Int?): String = bp?.let { percentText(it).removeSuffix("%") } ?: ""

/** عنوان لوحة النسبة وشرحها. */
fun rateSheetTitle(cls: GrowthClass): String = t(TextKey.SAVGROW_SHEET_TITLE, cls.label)

fun rateSheetBody(cls: GrowthClass): String = t(if (cls == GrowthClass.DEPOSIT) TextKey.SAVGROW_SHEET_BODY_DEPOSIT else TextKey.SAVGROW_SHEET_BODY)
