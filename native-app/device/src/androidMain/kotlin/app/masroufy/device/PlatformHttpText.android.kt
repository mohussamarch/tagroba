package app.masroufy.device

import app.masroufy.port.HttpFailure
import app.masroufy.port.HttpTextPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * أندرويد: `HttpURLConnection` من النظام على خيط الإدخال والإخراج. محتاج إذن `INTERNET` (في `:androidApp`). https بس (الرابط ثابت في
 * `LoadOnlineFeeds`) — أندرويد بيمنع http العادي لوحده.
 */
actual class PlatformHttpText actual constructor() : HttpTextPort {
    actual override suspend fun getText(url: String): String = withContext(Dispatchers.IO) { read(url) }

    private fun read(url: String): String {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw HttpFailure(null, e.message ?: "bad url")
        }
        try {
            connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
            connection.readTimeout = HTTP_READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            val status = try {
                connection.responseCode
            } catch (e: IOException) {
                throw HttpFailure(null, e.message ?: "no response")
            }
            if (status != HttpURLConnection.HTTP_OK) throw HttpFailure(status, "HTTP $status")
            return try {
                connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: IOException) {
                throw HttpFailure(null, e.message ?: "read failed")
            }
        } finally {
            connection.disconnect()
        }
    }
}
