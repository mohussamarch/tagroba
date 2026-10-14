package app.masroufy.memory

import app.masroufy.core.ProblemEntry
import app.masroufy.port.ProblemLogPort

/** «سجل المشاكل» في الذاكرة — للاختبار (CLAUDE.md #6) وللنسخ اللي مالهاش ملف (الآيفون لسه). نفس الحلقة: آخر `keepLast` سطر. */
class MemoryProblemLog : ProblemLogPort {
    private val lines = ArrayDeque<ProblemEntry>()

    override fun append(entry: ProblemEntry, keepLast: Int) {
        lines.addLast(entry)
        while (lines.size > keepLast.coerceAtLeast(1)) lines.removeFirst()
    }

    override fun entries(): List<ProblemEntry> = lines.toList()

    override fun clear() = lines.clear()
}
