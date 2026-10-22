package app.masroufy.memory

import app.masroufy.port.NoticeMemoryPort

/** آخر حالة اتبعت عنها إشعار — في الذاكرة للاختبار (CLAUDE.md #6). */
class MemoryNoticeMemory : NoticeMemoryPort {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun set(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
