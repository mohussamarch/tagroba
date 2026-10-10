package app.masroufy.ui.screens.dues

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.CycleUnit
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.daysBetween
import app.masroufy.core.monthName
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t

/**
 * عرض التواريخ والعدّ في شاشات المستحقات — **كلام بس، من غير أي حساب فلوس** (CLAUDE.md #4). الأرقام جوه الجمل بالعربي الشرقي
 * (`sentenceNumber`)، و**ممنوع «·» جنب رقم عربي** (KOTLIN-MAP §٣) ⇒ «،».
 */

/** لون شريحة الحالة: فات موعده · قرّب · جاي · هادي (بلا موعد أو بعيد) · متوقف/خلص. */
enum class Chip { OVERDUE, SOON, UPCOMING, CALM, MUTED }

/** «٢٠ سبتمبر» — والسنة لو مش سنة النهارده («٢٧ سبتمبر ٢٠٢٨»). */
internal fun dateText(date: IsoDate, today: IsoDate): String =
    if (date.take(4) == today.take(4)) dayMonth(date) else t(UiKey.DUES_DATE_WITH_YEAR, dayMonth(date), sentenceNumber(parseIsoDate(date).year))

/** «سبتمبر ٢٠٢٦» (عنوان سطر في جدول الأقساط). */
internal fun monthYearText(date: IsoDate): String {
    val p = parseIsoDate(date)
    return t(UiKey.DATE_MONTH_YEAR, monthName(p.month), sentenceNumber(p.year))
}

/** «اليوم» · «غدًا» · «بعد يومين» · «بعد ٥ أيام» · «بعد ٢٥ يومًا» (نفس كلام «القادم» في الرئيسية). */
internal fun afterText(days: Int): String = when {
    days <= 0 -> t(UiKey.UPCOMING_TODAY)
    days == 1 -> t(UiKey.UPCOMING_TOMORROW)
    days == 2 -> t(UiKey.UPCOMING_TWO_DAYS)
    days <= 10 -> t(UiKey.UPCOMING_FEW_DAYS, sentenceNumber(days))
    else -> t(UiKey.UPCOMING_MANY_DAYS, sentenceNumber(days))
}

/** «منذ يوم واحد» · «منذ يومين» · «منذ ٥ أيام» · «منذ ١٧ يومًا». */
internal fun agoText(days: Int): String = when {
    days <= 1 -> t(UiKey.DUES_AGO_ONE)
    days == 2 -> t(UiKey.DUES_AGO_TWO)
    days <= 10 -> t(UiKey.DUES_AGO_FEW, sentenceNumber(days))
    else -> t(UiKey.DUES_AGO_MANY, sentenceNumber(days))
}

/** موعد بالنسبة للنهارده: اللي فات ⇒ «منذ …»، والجاي ⇒ «بعد …». */
internal fun relativeText(date: IsoDate, today: IsoDate): String {
    val d = daysBetween(today, date)
    return if (d < 0) agoText(-d) else afterText(d)
}

/** عدّ بصيغ العربي الأربعة: واحد · اتنين · ٣–١٠ · ١١ وأكتر ([few]/[many] فيهم `{0}`) — و[zero] لو الصفر ليه جملة («لا أقساط» مش «٠ قسطًا»). */
internal fun countText(n: Int, one: TextRef, two: TextRef, few: TextRef, many: TextRef, zero: TextRef? = null): String = when {
    n == 0 && zero != null -> t(zero)
    n == 1 -> t(one)
    n == 2 -> t(two)
    n in 3..10 -> t(few, sentenceNumber(n))
    else -> t(many, sentenceNumber(n))
}

/** «قسط واحد» · «قسطان» · «٥ أقساط» · «١٢ قسطًا». */
internal fun installmentsCount(n: Int): String =
    countText(n, UiKey.DUES_INST_ONE, UiKey.DUES_INST_TWO, UiKey.DUES_INST_FEW, UiKey.DUES_INST_MANY, zero = UiKey.DUES_INST_NONE)

/** الدورة بالكلام: «كل شهر» · «كل شهرين» · «كل ٣ أشهر» · «كل سنة» · «كل أسبوع» · «كل أسبوعين». */
internal fun cycleText(every: Int, unit: CycleUnit = CycleUnit.MONTH): String = when (unit) {
    CycleUnit.WEEK -> when (every) {
        1 -> t(TextKey.ROSCA_FREQ_WEEKLY)
        2 -> t(TextKey.ROSCA_FREQ_BIWEEKLY)
        else -> t(UiKey.DUES_CYCLE_WEEKS, sentenceNumber(every))
    }
    CycleUnit.MONTH -> when (every) {
        1 -> t(TextKey.ROSCA_FREQ_MONTHLY)
        2 -> t(TextKey.ROSCA_FREQ_BIMONTHLY)
        12 -> t(UiKey.DUES_CYCLE_YEAR)
        else -> t(UiKey.DUES_CYCLE_MONTHS, sentenceNumber(every))
    }
}

/** أول حرف من الاسم (دايرة الشخص). */
internal fun initialOf(name: String): String = name.trim().firstOrNull()?.toString().orEmpty()
