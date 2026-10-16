package app.masroufy.core

/**
 * كارت الرئيسية «استثماراتك النهارده» (مواصفة النقل §1.14 · §2.7 — Q9): بنحفظ قيمة كل حاجة (نفس قيم «حاجاتي»: غير الشخصي والقيمة
 * معروفة) وكميتها آخر يوم اتشاف، وأول ما يوم جديد ييجي القديم بيبقى «امبارح». التغيّر = حركة **السعر** بس على الحاجات الموجودة في اليومين
 * (الجديدة والمتشالة عمرها ما تتحسب مكسب أو خسارة):
 * - حاجة بالكمية — معادن وأسهم وغيرها ([DailyValue.units] ≥ 0 = الكمية): نفس الكمية ⇒ الفرق كله سعر. الكمية اتغيرت (شراء زيادة أو بيع جزء) ⇒ الأساس =
 *   كمية النهارده بسعر امبارح (قيمة امبارح × كمية النهارده ÷ كمية امبارح، تقريب واحد) — فلوس الشراء أو البيع مش مكسب.
 * - العقار (قيمته مكتوبة باليد — [MANUAL_UNITS]): مفيش نشرة بتحركه ⇒ نفس القيمة تدخل الأساس بمكسب صفر، واتعدلت ⇒ أساس جديد
 *   (تطلع من اليوم ده).
 * مفيش يوم قبل كده، أو لقطة قديمة من غير كميات، أو الأساس ≤ 0 ⇒ «غير متاح».
 */
data class InvestTodayChange(
    /** نقاط أساس (120 = 1.2%) بالتقريب النص لبعيد عن الصفر. */
    val pctBp: Int,
    val gainMinor: Halalas,
    val prevDate: IsoDate,
)

/** قيمة حاجة النهارده و«وحداتها»: الكمية (× 10^8)، أو [MANUAL_UNITS] للعقار (قيمته مكتوبة باليد). */
data class DailyValue(val valueMinor: Halalas, val units: Long)

/** «وحدات» حاجة قيمتها يدوي — أي تعديل في القيمة = أساس جديد، مش مكسب. */
const val MANUAL_UNITS: Long = -1L

/** اللقطة الجديدة بعد قيم [now] النهارده: يوم جديد ⇒ المحفوظ بيتنقل لـ«قبل»؛ نفس اليوم ⇒ قيم النهارده بتتحدّث و«قبل» زي ما هو. */
fun shiftSnapshot(stored: UserSetting.InvestDaily?, spaceId: String, today: IsoDate, now: Map<Id, DailyValue>, updatedAt: String): UserSetting.InvestDaily {
    val ids = now.keys.sorted()
    val values = ids.map { now.getValue(it).valueMinor }
    val units = ids.map { now.getValue(it).units }
    return when {
        stored == null || stored.date > today ->
            UserSetting.InvestDaily(spaceId, today, ids, values, null, emptyList(), emptyList(), updatedAt, units, emptyList())
        stored.date == today -> stored.copy(ids = ids, values = values, units = units, updatedAt = updatedAt)
        else -> UserSetting.InvestDaily(spaceId, today, ids, values, stored.date, stored.ids, stored.values, updatedAt, units, stored.units)
    }
}

fun todayChange(snapshot: UserSetting.InvestDaily): InvestTodayChange? {
    val s = snapshot
    val prevDate = s.prevDate ?: return null
    if (s.prevIds.size != s.prevValues.size || s.ids.size != s.values.size) return null
    // لقطة قديمة من غير كميات ⇒ مش عارفين نفرّق السعر عن الشراء ⇒ «غير متاح» يوم واحد (مش مكسب متألّف)
    if (s.prevUnits.size != s.prevIds.size || s.units.size != s.ids.size) return null
    val prev = s.prevIds.indices.associate { s.prevIds[it] to DailyValue(s.prevValues[it], s.prevUnits[it]) }
    val now = s.ids.indices.associate { s.ids[it] to DailyValue(s.values[it], s.units[it]) }
    val moves = now.keys.filter { it in prev }.mapNotNull { id -> priceMove(prev.getValue(id), now.getValue(id)) }
    if (moves.isEmpty()) return null
    val base = sumMoney(moves.map { it.first })
    if (base <= 0) return null
    val gain = sumMoney(moves.map { it.second })
    return InvestTodayChange(ratioBp(gain, base), gain, prevDate)
}

/** (الأساس، المكسب) لحاجة في اليومين — null = تطلع من الحساب (قيمة يدوي اتعدلت، أو كمية امبارح مش معروفة). */
private fun priceMove(prev: DailyValue, now: DailyValue): Pair<Halalas, Halalas>? = when {
    prev.units == MANUAL_UNITS || now.units == MANUAL_UNITS ->
        if (prev.units == now.units && prev.valueMinor == now.valueMinor) prev.valueMinor to 0L else null
    prev.units <= 0L || now.units < 0L || prev.valueMinor < 0L -> null
    prev.units == now.units -> prev.valueMinor to now.valueMinor - prev.valueMinor
    else -> {
        val base = scaleByQuantity(prev.valueMinor, now.units, prev.units)
        base to now.valueMinor - base
    }
}

/** [value] × [num] ÷ [den] بالظبط (حاصل ضرب 128 بت) وتقريب واحد النص لبعيد عن الصفر — [num] ممكن يكون أكبر من [den] (شراء زيادة). */
internal fun scaleByQuantity(value: Halalas, num: Long, den: Long): Halalas {
    require(value >= 0 && num >= 0 && den > 0)
    val (q, r) = mulDivRem(value, num % den, den)
    return addMoney(multiplyMoneyByInt(value, num / den), q + if (r >= den - r) 1 else 0)
}

/** `gain × 10000 / base` بالتقريب النص لبعيد عن الصفر — على مرحلتين (خارج القسمة + الباقي) عشان الضرب ما يعدّيش حد `Long`. */
internal fun ratioBp(gain: Halalas, base: Halalas): Int {
    require(base > 0)
    val sign = if (gain < 0) -1 else 1
    val a = if (gain < 0) -gain else gain
    val whole = (a / base) * 10_000
    val rest = (a % base) * 10_000
    val q = rest / base
    val r = rest % base
    val rounded = whole + q + if (r * 2 >= base) 1 else 0
    return (sign * rounded).toInt()
}
