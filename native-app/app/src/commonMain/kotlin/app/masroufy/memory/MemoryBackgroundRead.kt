package app.masroufy.memory

import app.masroufy.port.BackgroundReadPort

/** «آخر قراية في الخلفية» في الذاكرة — للاختبار (CLAUDE.md #6). [offsetMillis] = فرق المنطقة اللي الاختبار عايزه. */
class MemoryBackgroundRead(private var value: Long? = null, private val offsetMillis: Long = 0) : BackgroundReadPort {
    override fun last(): Long? = value

    override fun mark(atMillis: Long) {
        value = atMillis
        anyRead = maxOf(anyRead ?: atMillis, atMillis)
    }

    private var anyRead: Long? = value

    override fun lastAnyRead(): Long? = anyRead

    override fun markAnyRead(atMillis: Long) {
        anyRead = maxOf(anyRead ?: atMillis, atMillis)
    }

    override fun zoneOffsetMillis(atMillis: Long): Long = offsetMillis
}
