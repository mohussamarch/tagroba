package app.masroufy.port

/**
 * البيانات اللي بتيجي من النت من غير سيرفر لينا (CLAUDE.md #12 — باقة Spark): ملفات الأسعار والمتوسطات اللي GitHub Actions
 * بيولّدها في مستودع الأسعار. **قراية بس** — مفيش ولا حرف من بيانات المستخدم بيطلع.
 */

/** بيجيب نص ملف من رابط https. التنفيذ على الجهاز (`PlatformHttpText` في `:device` — من غير مكتبة). بيرمي [HttpFailure] لو فشل. */
fun interface HttpTextPort {
    suspend fun getText(url: String): String
}

/** [status] = كود الرد لو السيرفر رد (404 مثلًا)، null = مفيش رد أصلًا (مفيش نت · انتهى الوقت). */
class HttpFailure(val status: Int?, message: String) : Exception(message)

/** آخر نسخة نزلت من ملف: النص زي ما هو + إمتى نزل. */
data class CachedFeed(val text: String, val fetchedAtIso: String, val fetchedAtMillis: Long)

/** نسخة الملفات على الجهاز (مش بيانات حساب، ومش بتتزامن) — عشان «آخر تحديث» يفضل يتعرض من غير نت. */
interface FeedCachePort {
    fun read(name: String): CachedFeed?

    fun write(name: String, feed: CachedFeed)
}
