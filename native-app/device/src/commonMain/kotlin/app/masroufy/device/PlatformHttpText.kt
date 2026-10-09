package app.masroufy.device

import app.masroufy.port.HttpTextPort

/**
 * جلب نص من رابط https **من غير مكتبة** (ARCHITECTURE §31.31): أندرويد والكمبيوتر `HttpURLConnection`، والآيفون `NSData` (Foundation).
 * لملفات الأسعار والمتوسطات بس (`LoadOnlineFeeds`) — قراية، ومفيش ولا حرف من بيانات المستخدم بيطلع. أي رد غير 200 أو انقطاع ⇒
 * `HttpFailure` (`status` = كود الرد، أو null لو مفيش رد خالص).
 */
expect class PlatformHttpText() : HttpTextPort {
    override suspend fun getText(url: String): String
}

/** حدود الانتظار — الملفات صغيرة (عشرات الكيلوبايت). */
internal const val HTTP_CONNECT_TIMEOUT_MS = 10_000
internal const val HTTP_READ_TIMEOUT_MS = 15_000
