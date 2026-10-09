package app.masroufy.device

import app.masroufy.port.HttpFailure
import app.masroufy.port.HttpTextPort

/** الكمبيوتر (الاختبارات): نفس كود أندرويد ([readUrlText]) من غير خيوط — الاختبار بيناديه من `runBlocking`. */
actual class PlatformHttpText actual constructor() : HttpTextPort {
    actual override suspend fun getText(url: String): String = readUrlText(url)
}

/** `HttpURLConnection` — نفس الدالة في أندرويد (`PlatformHttpText.android.kt`). */
internal fun readUrlText(url: String): String {
    val connection = try {
        java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
    } catch (e: Exception) {
        throw HttpFailure(null, e.message ?: "bad url")
    }
    try {
        connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
        connection.readTimeout = HTTP_READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        val status = try {
            connection.responseCode
        } catch (e: java.io.IOException) {
            throw HttpFailure(null, e.message ?: "no response")
        }
        if (status != 200) throw HttpFailure(status, "HTTP $status")
        return try {
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: java.io.IOException) {
            throw HttpFailure(null, e.message ?: "read failed")
        }
    } finally {
        connection.disconnect()
    }
}
