package app.masroufy.core

/**
 * كارت الرئيسية «استثماراتك النهارده» (مواصفة النقل §1.14 · §2.7 — Q9): بنحفظ قيمة كل حاجة (نفس قيم «حاجاتي»: غير الشخصي والقيمة
 * معروفة) آخر يوم اتشاف، وأول ما يوم جديد ييجي القديم بيبقى «امبارح». التغيّر = الحاجات الموجودة في اليومين بس (الجديدة والمتشالة
 * عمرها ما تتحسب مكسب أو خسارة). مفيش يوم قبل كده، أو قيمته ≤ 0 ⇒ «غير متاح».
 */
data class InvestTodayChange(
    /** نقاط أساس (120 = 1.2%) بالتقريب النص لبعيد عن الصفر. */
    val pctBp: Int,
    val gainMinor: Halalas,
    val prevDate: IsoDate,
)

/** اللقطة الجديدة بعد قيم [now] النهارده: يوم جديد ⇒ المحفوظ بيتنقل لـ«قبل»؛ نفس اليوم ⇒ قيم النهارده بتتحدّث و«قبل» زي ما هو. */
fun shiftSnapshot(stored: UserSetting.InvestDaily?, spaceId: String, today: IsoDate, now: Map<Id, Halalas>, updatedAt: String): UserSetting.InvestDaily {
    val ids = now.keys.sorted()
    val values = ids.map { now.getValue(it) }
    return when {
        stored == null || stored.date > today -> UserSetting.InvestDaily(spaceId, today, ids, values, null, emptyList(), emptyList(), updatedAt)
        stored.date == today -> stored.copy(ids = ids, values = values, updatedAt = updatedAt)
        else -> UserSetting.InvestDaily(spaceId, today, ids, values, stored.date, stored.ids, stored.values, updatedAt)
    }
}

fun todayChange(snapshot: UserSetting.InvestDaily): InvestTodayChange? {
    val prevDate = snapshot.prevDate ?: return null
    if (snapshot.prevIds.size != snapshot.prevValues.size || snapshot.ids.size != snapshot.values.size) return null
    val prev = snapshot.prevIds.zip(snapshot.prevValues).toMap()
    val now = snapshot.ids.zip(snapshot.values).toMap()
    val common = now.keys.filter { it in prev }
    if (common.isEmpty()) return null
    val base = sumMoney(common.map { prev.getValue(it) })
    if (base <= 0) return null
    val gain = sumMoney(common.map { now.getValue(it) - prev.getValue(it) })
    return InvestTodayChange(ratioBp(gain, base), gain, prevDate)
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
