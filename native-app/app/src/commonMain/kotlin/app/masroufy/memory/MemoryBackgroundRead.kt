package app.masroufy.memory

import app.masroufy.port.BackgroundReadPort

/** «آخر قراية في الخلفية» في الذاكرة — للاختبار (CLAUDE.md #6). [offsetMillis] = فرق المنطقة اللي الاختبار عايزه. */
class MemoryBackgroundRead(private var value: Long? = null, private val offsetMillis: Long = 0) : BackgroundReadPort {
    override fun last(): Long? = value

    override fun mark(atMillis: Long) {
        value = atMillis
    }

    override fun zoneOffsetMillis(atMillis: Long): Long = offsetMillis
}
