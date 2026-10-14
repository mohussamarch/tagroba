package app.masroufy.usecase

import app.masroufy.core.ErrorKind
import app.masroufy.core.PROBLEM_LOG_LIMIT
import app.masroufy.core.ProblemCode
import app.masroufy.core.ProblemDetail
import app.masroufy.core.ProblemEntry
import app.masroufy.core.ProblemLogFormat
import app.masroufy.port.CrashReportsPort
import app.masroufy.port.ProblemLogPort

/**
 * تسجيل مشكلة في «سجل المشاكل» (OVERRIDES §90). اللي بينادي بيدّي **كود من القايمة + نوع + أرقام بس** — الوقت والنسخة والبلد من هنا
 * (مش من اللي بينادي)، فمفيش طريق لنص من بيانات المستخدم يدخل السطر. [country] = بلد الحساب الشغال دلوقتي (null قبل الدخول).
 */
class RecordProblem(
    private val log: ProblemLogPort,
    private val nowIso: () -> String,
    private val appVersion: String,
    private val country: () -> String?,
    private val keepLast: Int = PROBLEM_LOG_LIMIT,
) {
    fun record(code: ProblemCode, kind: ErrorKind? = null, details: Map<ProblemDetail, Long> = emptyMap()): ProblemEntry {
        val entry = ProblemEntry(nowIso(), code, kind, details, appVersion, country())
        log.append(entry, keepLast)
        return entry
    }
}

/** صفحة «سجل المشاكل»: الأحدث فوق. */
class ListProblems(private val log: ProblemLogPort) {
    fun newestFirst(): List<ProblemEntry> = log.entries().asReversed()
}

/** «ابعته»: السجل كله نص (`ProblemLogFormat.export`) — الأقدم الأول، وكل حقل متنضف. */
class ExportProblems(private val log: ProblemLogPort, private val nowIso: () -> String, private val appVersion: String) {
    fun text(): String = ProblemLogFormat.export(log.entries(), nowIso(), appVersion)
}

/** «امسح السجل» (بعد التأكيد في الصفحة نفسها). */
class ClearProblems(private val log: ProblemLogPort) {
    fun clear() = log.clear()
}

/** مفتاح «ابعت تقارير الأعطال» + اسم الشاشة الظاهرة للتقرير. */
class ManageCrashReports(private val port: CrashReportsPort) {
    val available: Boolean get() = port.available

    fun isOn(): Boolean = port.available && port.isOn()

    fun setOn(on: Boolean) {
        if (port.available) port.setOn(on)
    }

    fun screenShown(route: String) = port.screenShown(route)
}
