package app.masroufy.port

/**
 * آخر مرة العامل بتاع الخلفية قرا رسايل البنك **من غير خطأ** (وقت الجهاز بالملّي ثانية). على الجهاز ده بس — مش بيانات حساب ومش بيتزامن.
 * التنفيذ: `SharedPreferences` على أندرويد (`AndroidBackgroundRead` في `:device`) · الذاكرة للاختبار (`MemoryBackgroundRead`).
 * [last] = null ⇒ لسه ما قراش (الشاشة بتقول كده — مفيش وقت بيتخترع).
 */
interface BackgroundReadPort {
    fun last(): Long?

    fun mark(atMillis: Long)

    /** 0.5.2: آخر قراية للصندوق من أي نوع (الخلفية أو الشاشة) — الرسايل اللي وصلت قبلها اتعاينت. null = ولا مرة. */
    fun lastAnyRead(): Long? = last()

    fun markAnyRead(atMillis: Long) {}

    /** فرق منطقة الجهاز عن العالمي (بالملّي ثانية) في الوقت ده — لتنسيق التاريخ من غير لغة الجوال. */
    fun zoneOffsetMillis(atMillis: Long): Long
}
