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

    /**
     * تقارير الأعطال (OVERRIDES §90 · §80): أي نداء لـCrashlytics — `log` ممنوع خالص (ممكن نص من البيانات) · `recordException` بخطأ متنضف
     * بس (`CrashSanitizer.sanitize`) · `setCustomKey` بالمفاتيح التلاتة بس (الشاشة · النسخة · البلد) · `setUserId` برقم فايربيز (`uid`) بس.
     */
    private val crashCall = Regex("""\.(log|recordException|setCustomKey|setUserId|setCustomKeys)\(""")
    private val allowedKeys = setOf("KEY_SCREEN", "KEY_VERSION", "KEY_COUNTRY")

    internal fun crashLeaks(line: String): Boolean = crashCall.findAll(line).any { m ->
        val args = argsAt(line, m.range.last + 1).trim()
        when (m.groupValues[1]) {
            "recordException" -> !args.startsWith("CrashSanitizer.sanitize(")
            "setCustomKey" -> args.substringBefore(',').trim() !in allowedKeys || secret.containsMatchIn(args)
            // LOGS-2: `setUserId("")` = مسح رقم الحساب (مش بيبعت حاجة)
            "setUserId" -> !(args == "uid.orEmpty()" || args == "uid" || args == "\"\"")
            else -> true
        }
    }

    @Test fun theCrashReportCheckCatchesWhatItShould() {
        assertTrue(crashLeaks("""crashlytics?.log("sms: ${'$'}body")"""))
        assertTrue(crashLeaks("""c.recordException(error)"""))
        assertTrue(crashLeaks("""c.setCustomKey("merchant", name)"""))
        assertTrue(crashLeaks("""c.setCustomKey(KEY_SCREEN, e.message)"""))
        assertTrue(crashLeaks("""c.setUserId(user.email)"""))
        assertEquals(false, crashLeaks("""crashlytics?.recordException(CrashSanitizer.sanitize(error, code.name))"""))
        assertEquals(false, crashLeaks("""c.setCustomKey(KEY_COUNTRY, country.orEmpty().take(2))"""))
        assertEquals(false, crashLeaks("""c.setUserId(uid.orEmpty())"""))
        assertEquals(false, crashLeaks("""c.setUserId("")"""))
        assertTrue(crashLeaks("""c.setUserId("someone")"""))
    }

    @Test fun crashReportsCarryNoMessagesNamesOrAmounts() {
        val root = root()
        val users = mainSources(root).filter { f -> f.extension == "kt" && f.readText().contains("FirebaseCrashlytics") }
        assertEquals(listOf("CrashReports.kt"), users.map { it.name }, "Crashlytics من مكان واحد بس (`CrashReports.kt`)")
        val bad = users.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (crashLeaks(line)) "${f.relativeTo(root).invariantSeparatorsPath}:${i + 1}: ${line.trim()}" else null }
        }
        assertTrue(bad.isEmpty(), "نداء لتقارير الأعطال ممكن يبعت بيانات:\n" + bad.joinToString("\n"))
    }

    /**
     * بيانات الاستخدام (OVERRIDES §114 · PLAN-0.6 §16): كل نداء `Usage.log` بياخد حدث من `UsageEvents` بس، و`Usage.set` خاصية من `UsageProperty`
     * وقيمة من `UsageValue` — أنواع `core` اللي مالهاش نص حر (`UsageEventsTest`). ومكتبة فايربيز نفسها من ملف واحد (`FirebaseUsage.kt`):
     * `logEvent` باسم الحدث من القايمة بس · `setUserProperty` باسم الخاصية بس · `setUserId` و`setDefaultEventParameters` ممنوعين خالص.
     */
    private val usageCall = Regex("""(?<![A-Za-z])Usage\.(log|set)\(""")
    private val analyticsCall = Regex("""\.(logEvent|setUserProperty|setUserId|setDefaultEventParameters|setSessionTimeoutDuration)\(""")
    private val core = "(app\\.masroufy\\.core\\.)?"

    internal fun usageLeaks(line: String): Boolean = usageCall.findAll(line).any { m ->
        val args = argsAt(line, m.range.last + 1).trim()
        when (m.groupValues[1]) {
            "log" -> !Regex("^${core}UsageEvents\\.\\w+\\(").containsMatchIn(args)
            else -> !Regex("^${core}UsageProperty\\.[A-Z_]+,\\s*${core}UsageValue\\.\\w+\\(").containsMatchIn(args)
        }
    }

    internal fun analyticsLeaks(line: String): Boolean = analyticsCall.findAll(line).any { m ->
        val args = argsAt(line, m.range.last + 1).trim()
        when (m.groupValues[1]) {
            "logEvent" -> !args.startsWith("event.name.wire,")
            "setUserProperty" -> !args.startsWith("property.wire,")
            else -> true
        }
    }

    @Test fun theUsageCheckCatchesWhatItShould() {
        assertTrue(usageLeaks("""Usage.log(UsageEvent(name, mapOf()))"""))
        assertTrue(usageLeaks("""app.masroufy.problems.Usage.log(event)"""))
        assertTrue(usageLeaks("""Usage.set(UsageProperty.ACCOUNTS, value)"""))
        assertTrue(analyticsLeaks("""analytics.logEvent("sms", bundleOf("body" to body))"""))
        assertTrue(analyticsLeaks("""analytics.setUserId(uid)"""))
        assertTrue(analyticsLeaks("""analytics.setUserProperty("merchant", name)"""))
        assertEquals(false, usageLeaks("""app.masroufy.problems.Usage.log(app.masroufy.core.UsageEvents.plusDismissed())"""))
        assertEquals(false, usageLeaks("""Usage.set(UsageProperty.DARK_MODE, UsageValue.flag(it))"""))
        assertEquals(false, analyticsLeaks("""analytics.logEvent(event.name.wire, bundle)"""))
    }

    @Test fun usageCallsCarryOnlyCatalogEvents() {
        val root = root()
        val files = mainSources(root).filter { it.extension == "kt" }
        val firebase = files.filter { it.readText().contains("FirebaseAnalytics") }
        assertEquals(listOf("FirebaseUsage.kt"), firebase.map { it.name }, "مكتبة التحليلات من مكان واحد بس (`FirebaseUsage.kt`)")
        val bad = files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val leak = usageLeaks(line) || (f.name == "FirebaseUsage.kt" && analyticsLeaks(line))
                if (leak) "${f.relativeTo(root).invariantSeparatorsPath}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertTrue(bad.isEmpty(), "نداء بيانات استخدام ممكن يبعت بيانات:\n" + bad.joinToString("\n"))
        val calls = files.sumOf { f -> f.readLines().count { usageCall.containsMatchIn(it) } }
        assertTrue(calls >= 10, "المسح لازم يلاقي نداءات بيانات الاستخدام فعلًا — لقى $calls")
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
