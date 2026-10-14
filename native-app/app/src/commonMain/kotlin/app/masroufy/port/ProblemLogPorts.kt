package app.masroufy.port

import app.masroufy.core.ProblemEntry

/**
 * «سجل المشاكل» على الجهاز (OVERRIDES §90): حلقة بآخر [keepLast] سطر. **مش بيانات حساب ومش بيتزامن.**
 * التنفيذ: ملف خاص بالتطبيق على أندرويد (`FileProblemLog` في `:device`) · الذاكرة للاختبار (`MemoryProblemLog`).
 * ⚠️ دوال عادية (مش `suspend`): الوقعة نفسها (`UncaughtExceptionHandler`) بتكتب قبل ما العملية تموت — ملف صغير (300 سطر).
 */
interface ProblemLogPort {
    fun append(entry: ProblemEntry, keepLast: Int)

    /** من الأقدم للأحدث. */
    fun entries(): List<ProblemEntry>

    fun clear()
}

/**
 * تقارير الأعطال (Firebase Crashlytics على أندرويد — `:androidApp`). [available] = false ⇒ النسخة دي مالهاش (المحاكي · الاختبارات · الآيفون).
 * الإعداد على الجهاز ده بس (مش بيتزامن). [screenShown] = اسم الشاشة الظاهرة (`Route.name` — اسم ثابت، مش بيانات).
 */
interface CrashReportsPort {
    val available: Boolean

    fun isOn(): Boolean

    fun setOn(on: Boolean)

    fun screenShown(route: String)
}
