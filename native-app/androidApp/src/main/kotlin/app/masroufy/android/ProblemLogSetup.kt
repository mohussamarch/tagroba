package app.masroufy.android

import android.content.Context
import app.masroufy.core.errorKindOf
import app.masroufy.device.FileProblemLog
import app.masroufy.problems.Problems
import app.masroufy.ui.app.ProblemLogDeps
import app.masroufy.usecase.ClearProblems
import app.masroufy.usecase.ExportProblems
import app.masroufy.usecase.ListProblems
import app.masroufy.usecase.ManageCrashReports
import app.masroufy.usecase.RecordProblem
import java.io.File
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * «سجل المشاكل» على الجوال (OVERRIDES §90) — **أول حاجة في بداية العملية** (قبل التجميع) عشان أي وقعة بعدها تتسجل:
 * ملف خاص بالتطبيق (`filesDir/problems/problems.log` — `FileProblemLog`) · الوقت بوقت الجوال وفرقه · النسخة من البناء ·
 * البلد الشغالة من الجلسة ([session]). [install] بيوصّل `Problems` بالسجل وبتقارير الأعطال (الأخطاء الكبيرة بس، متنضفة).
 */
class ProblemLogSetup(context: Context, private val crash: AndroidCrashReports) {
    @Volatile private var country: String? = null
    private val log = FileProblemLog(File(context.filesDir, "problems/problems.log"))
    private val nowIso: () -> String = { OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString() }
    private val record = RecordProblem(log, nowIso, BuildConfig.VERSION_NAME, { country })

    /** للشاشات (`AppDeps.problems`): القايمة · النص اللي بيتبعت · المسح · مفتاح تقارير الأعطال. */
    val deps: ProblemLogDeps = object : ProblemLogDeps {
        override val list = ListProblems(log)
        override val export = ExportProblems(log, nowIso, BuildConfig.VERSION_NAME)
        override val clear = ClearProblems(log)
        override val crashReports = ManageCrashReports(crash)
    }

    fun install() {
        Problems.sink = { code, error, details ->
            record.record(code, errorKindOf(error), details)
            crash.problem(code, error)
        }
    }

    /** الحساب الداخل ([uid] — رقم فايربيز بس) وبلده الشغالة. null ⇒ خرج. */
    fun session(uid: String?, countryCode: String?) {
        country = countryCode
        crash.session(uid, countryCode)
    }
}
