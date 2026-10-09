package app.masroufy.device

import android.content.Context
import app.masroufy.port.CachedFeed
import app.masroufy.port.FeedCachePort
import java.io.File

/**
 * نسخة ملفات الأسعار والمتوسطات على الجوال (`files/feeds/`) — عشان «آخر تحديث» يفضل يتعرض من غير نت. مش بيانات حساب ومش بتتزامن
 * (نفس الملف لكل الحسابات — بيانات عامة). الكتابة ذرية (ملف مؤقت ⇒ إعادة تسمية) عشان وقوع في النص ما يسيبش نسخة بايظة.
 */
class AndroidFeedCache(context: Context) : FeedCachePort {
    private val dir = File(context.filesDir, "feeds")

    override fun read(name: String): CachedFeed? = try {
        val body = File(dir, safe(name))
        val meta = File(dir, safe(name) + ".meta")
        if (!body.isFile || !meta.isFile) null else {
            val (iso, millis) = meta.readText(Charsets.UTF_8).split('\n', limit = 2)
            CachedFeed(body.readText(Charsets.UTF_8), iso, millis.trim().toLong())
        }
    } catch (_: Exception) {
        null
    }

    override fun write(name: String, feed: CachedFeed) {
        try {
            dir.mkdirs()
            atomic(File(dir, safe(name)), feed.text)
            atomic(File(dir, safe(name) + ".meta"), feed.fetchedAtIso + "\n" + feed.fetchedAtMillis)
        } catch (_: Exception) {
            // النسخة مش ضرورية — المرة الجاية بتتنزل تاني
        }
    }

    private fun atomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
