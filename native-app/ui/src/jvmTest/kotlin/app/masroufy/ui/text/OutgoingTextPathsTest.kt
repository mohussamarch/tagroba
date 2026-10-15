package app.masroufy.ui.text

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * إجابة المالك 2026-10-15 (OVERRIDES §91-8): «ر.س» **برا التطبيق بس** (الإشعارات · المشاركة · النسخ). كل سطر في الكود بيطلّع نص برا
 * التطبيق (عنوان/نص إشعار · نص مشاركة · نسخ للحافظة) لازم يعدّي على `outgoingText` — الاختبار ده بيقرا الكود نفسه عشان أي طريق جديد
 * يتنسي يتمسك هنا.
 */
class OutgoingTextPathsTest {
    private val outgoing = Regex("""\.setContentTitle\(|\.setContentText\(|\.setBigText\(|\.bigText\(|\.setTicker\(|EXTRA_TEXT,|clipboard\.setText\(|setPrimaryClip\(""")

    @Test fun everyOutgoingTextPathGoesThroughOutgoingText() {
        val root = File("..").canonicalFile
        val files = root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" && "${File.separator}src${File.separator}" in it.path && !it.path.contains("Test") }
            .toList()
        assertTrue(files.size > 100, "scanned ${files.size} files from $root")
        val paths = files.flatMap { f -> f.readLines().withIndex().filter { outgoing.containsMatchIn(it.value) }.map { f to it } }
        assertTrue(paths.size >= 4, "found the known paths: ${paths.map { it.first.name }}")
        val bare = paths.filter { (_, line) -> "outgoingText(" !in line.value }.map { (f, line) -> "${f.name}:${line.index + 1}" }
        assertEquals(emptyList(), bare, "outgoing text without outgoingText")
    }
}
