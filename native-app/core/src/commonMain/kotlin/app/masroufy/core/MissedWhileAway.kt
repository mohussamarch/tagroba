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
