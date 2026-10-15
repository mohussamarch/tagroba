package app.masroufy.problems

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * OVERRIDES §90: «سجل المشاكل» **أكواد من القايمة الثابتة بس**. بيمسح كود التشغيل كله: كل `Problems.report(` و`.record(ProblemCode`
 * أول معامل فيه لازم يبدأ بـ`ProblemCode.` — ولا نص (`"`) ولا قالب (`$`) جوه النداء. والنوع نفسه (`ProblemEntry`) مالوش خانة نص من
 * اللي بينادي (الوقت والنسخة والبلد من `RecordProblem`) — `ProblemLogFormatTest` بيتأكد إن المكتوب بيتنضف.
 */
class ProblemCallSitesTest {
    private val call = Regex("""\bProblems\.report\(""")

    private fun root(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return dir ?: fail("ما لقيناش جذر المشروع (settings.gradle.kts)")
    }

    /** اللي جوه قوسين النداء (لحد القوس اللي بيقفله). */
    private fun argsAt(line: String, from: Int): String {
        var depth = 1
        var i = from
        while (i < line.length && depth > 0) {
            if (line[i] == '(') depth++ else if (line[i] == ')') depth--
            i++
        }
        return line.substring(from, (i - 1).coerceAtLeast(from))
    }

    internal fun badCall(line: String): Boolean = call.findAll(line).any { m ->
        val args = argsAt(line, m.range.last + 1).trim()
        !args.startsWith("ProblemCode.") || '"' in args || '$' in args
    }

    @Test
    fun theCheckCatchesWhatItShould() {
        assertTrue(badCall("""Problems.report("sync failed")"""))
        assertTrue(badCall("""Problems.report(code)"""))
        assertTrue(badCall("""Problems.report(ProblemCode.SYNC_WRITE_FAILED, IllegalStateException("${'$'}{sms.body}"))"""))
        assertTrue(!badCall("""Problems.report(ProblemCode.SYNC_WRITE_FAILED, e)"""))
        assertTrue(!badCall("""Problems.report(ProblemCode.BACKGROUND_FAILED, null, mapOf(ProblemDetail.ATTEMPT to runAttemptCount.toLong()))"""))
    }

    @Test
    fun everyReportUsesACatalogueCodeAndNoText() {
        val root = root()
        val files = root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && !it.name.contains("test", ignoreCase = true) }
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val sites = files.flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if (call.containsMatchIn(line)) Triple(f, i, line) else null } }
        assertTrue(sites.size >= 10, "لازم يلاقي نقط التسجيل فعلًا — لقى ${sites.size}")
        val bad = sites.filter { (_, _, line) -> badCall(line) }.map { (f, i, line) -> "${f.relativeTo(root).invariantSeparatorsPath}:${i + 1}: ${line.trim()}" }
        assertTrue(bad.isEmpty(), "تسجيل مشكلة من غير كود ثابت أو بنص:\n" + bad.joinToString("\n"))
    }
}
