package app.masroufy.perf

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * CLAUDE.md «الأمان» البند 5 · OVERRIDES §80: **ولا مفتاح ولا بيانات دخول بتتكتب في أي سجل.** بيمسح كود التشغيل كله (مش الاختبارات):
 * أي كتابة في السجل (`PerfTrace.log` · `Log.x` · `println` · `printStackTrace` …) **اللي بتتكتب نفسها** فيها كلمة توكن/باسورد/مفتاح،
 * أو رسالة استثناء كاملة (`.message` — ممكن تشيل التوكن نفسه)، أو استثناء متبعت للسجل ⇒ الاختبار بيقع ويقول السطر.
 * اسم نوع الاستثناء بس (`it::class.simpleName`) مسموح.
 */
class NoSecretsInLogsTest {
    private val logCall = Regex("""PerfTrace\.log\(|\bLog\.(v|d|i|w|e|wtf)\(|\bprintln\(|\bprint\(|System\.(out|err)\.\w+\(""")
    private val secret = Regex("""(?i)token|password|passwd|credential|secret|api[_-]?key|private[_-]?key|\.message\b|localizedMessage|stackTrace""")

    /** اللي جوه قوسين النداء (بعد [from] على طول) — بعدّ الأقواس عشان `${…}` و`f(x)` جوه الرسالة. */
    private fun argsAt(line: String, from: Int): String {
        var depth = 1
        var i = from
        while (i < line.length && depth > 0) {
            if (line[i] == '(') depth++ else if (line[i] == ')') depth--
            i++
        }
        return line.substring(from, (i - 1).coerceAtLeast(from))
    }

    /** عدد المعاملات على المستوى الأول — `Log.e(TAG, msg, e)` = 3 ⇒ الاستثناء نفسه (برسالته) رايح السجل. */
    private fun topLevelArgs(args: String): Int {
        var depth = 0
        var count = if (args.isBlank()) 0 else 1
        for (c in args) when (c) {
            '(', '{', '[' -> depth++
            ')', '}', ']' -> depth--
            ',' -> if (depth == 0) count++
        }
        return count
    }

    internal fun leaks(line: String): Boolean {
        if (line.contains("printStackTrace(")) return true
        return logCall.findAll(line).any { m ->
            val args = argsAt(line, m.range.last + 1)
            secret.containsMatchIn(args) || (m.value.startsWith("Log.") && topLevelArgs(args) > 2)
        }
    }

    private fun root(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return dir ?: fail("ما لقيناش جذر المشروع (settings.gradle.kts)")
    }

    private fun mainSources(root: File): List<File> = root.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".gradle" && it.name != "node_modules" && !it.name.contains("test", ignoreCase = true) }
        .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
        .toList()

    @Test fun theCheckCatchesWhatItShould() {
        assertTrue(leaks("""PerfTrace.log("sign-in failed: ${'$'}{it.message}")"""))
        assertTrue(leaks("""Log.i(TAG, "token=${'$'}idToken")"""))
        assertTrue(leaks("""Log.e(TAG, "failed", e)"""))
        assertTrue(leaks("""e.printStackTrace()"""))
        assertTrue(leaks("""println("password " + pw)"""))
        assertEquals(false, leaks("""runCatching { auth.signInWithCustomToken(t) }.onFailure { PerfTrace.log("failed: ${'$'}{it::class.simpleName}") }"""))
        assertEquals(false, leaks("""PerfTrace.log("read src=fs docs=${'$'}{docs.size} ms=${'$'}{start.elapsedNow().inWholeMilliseconds}")"""))
    }

    @Test fun noLogLineCarriesATokenOrAFullExceptionMessage() {
        val root = root()
        val files = mainSources(root)
        assertTrue(files.size > 100, "المسح لازم يلاقي كود التشغيل فعلًا — لقى ${files.size} ملف بس")
        val bad = files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (leaks(line)) "${f.relativeTo(root).invariantSeparatorsPath}:${i + 1}: ${line.trim()}" else null }
        }
        // تطبيق التجربة القديم (firebase-trial: قياس قراية فايربيز) مالوش دخول ولا توكن — بيكتب الخطأ لتشخيص القياس. أي حاجة تانية ممنوعة.
        val allowed = bad.filterNot { it.startsWith("firebase-trial/") }
        assertTrue(allowed.isEmpty(), "سطور سجل ممكن تكشف توكن أو بيانات دخول:\n" + allowed.joinToString("\n"))
    }
}
