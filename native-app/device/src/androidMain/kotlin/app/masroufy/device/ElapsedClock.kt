package app.masroufy.device

/**
 * وقت منقضي من تشغيل الجوال بالمللي ثانية (`SystemClock.elapsedRealtime`) — **مش ساعة الحيطة**: تغيير ساعة الجوال لورا أو لقدام
 * ما بيأثرش فيه، وبيعد وقت النوم العميق (عكس `System.nanoTime`). لمهلة قفل التطبيق (`AppLock` — مراجعة: الرجوع بالساعة كان بيفتح القفل).
 */
object ElapsedClock {
    fun nowMs(): Long = android.os.SystemClock.elapsedRealtime()
}
