package app.masroufy.core

/**
 * «فيه رسايل فاتته» (PLAN-0.6 §13 ب): رسايل البنك اللي وصلت **بعد** آخر قراية في الخلفية ولسه ما اتقرتش. الدالة صافية:
 * - [lastBackgroundMillis] = null (الخلفية عمرها ما قرت) ⇒ كل رسالة مستنية بتتعدّ.
 * - غير كده ⇒ بس اللي وقت وصولها أحدث من آخر قراية. رسالة وقتها مش معروف ([arrivals] فيها null) ما بتتعدّش (قاعدة 10: مفيش عدّ بالتخمين).
 * [arrivals] = أوقات وصول الرسايل المستنية (بالملّي ثانية) من نفس مسح الصندوق اللي الشاشة عملته — مفيش مسح تاني.
 * 0 ⇒ «تمام».
 */
fun missedWhileAway(lastBackgroundMillis: Long?, arrivals: List<Long?>): Int =
    if (lastBackgroundMillis == null) arrivals.size else arrivals.count { it != null && it > lastBackgroundMillis }

/** العدد + [more] = «أكتر من [n]»: الصفحة فيها أحدث الرسايل بس، وكلها اتعدّت، وفي الصندوق أقدم منها ما اتشافتش (مفيش سقف 200 بيتقال كأنه العدد). */
data class MissedSms(val n: Int, val more: Boolean = false)

/**
 * 0.5.2 — [lastReadMillis] = آخر قراية **من أي نوع** (الخلفية أو الشاشة: «اسحب لتحت» · «اقرأ اللي فات» · «أكّد الأشكال» · التأكيد) — اللي وصل
 * قبلها اتعاين، ولو لسه مستني تأكيد المالك فهو مش «فاتته». [queued] = كل اللي في الصندوق ([arrivals] = أحدث صفحة منه بس).
 */
fun missedSms(lastReadMillis: Long?, arrivals: List<Long?>, queued: Int): MissedSms {
    val n = missedWhileAway(lastReadMillis, arrivals)
    return MissedSms(n, more = n > 0 && n == arrivals.size && queued > arrivals.size)
}
