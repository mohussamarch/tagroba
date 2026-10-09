package app.masroufy.memory

import app.masroufy.port.CachedFeed
import app.masroufy.port.FeedCachePort
import app.masroufy.port.HttpFailure
import app.masroufy.port.HttpTextPort

/** نسخة الملفات في الذاكرة — للاختبارات (على الجهاز `AndroidFeedCache` في `:device`). */
class MemoryFeedCache : FeedCachePort {
    private val files = mutableMapOf<String, CachedFeed>()

    override fun read(name: String): CachedFeed? = files[name]

    override fun write(name: String, feed: CachedFeed) {
        files[name] = feed
    }
}

/** «النت» في الاختبارات: رد ثابت لكل رابط، ورابط مش موجود ⇒ زي مفيش نت. [calls] = الروابط اللي اتطلبت بالترتيب. */
class MemoryHttpText(private val replies: MutableMap<String, String> = mutableMapOf()) : HttpTextPort {
    val calls = mutableListOf<String>()

    /** null = مفيش نت (مفيش رد خالص). */
    var offline = false

    operator fun set(url: String, body: String) {
        replies[url] = body
    }

    override suspend fun getText(url: String): String {
        calls += url
        if (offline) throw HttpFailure(null, "offline")
        return replies[url] ?: throw HttpFailure(404, "not found")
    }
}
