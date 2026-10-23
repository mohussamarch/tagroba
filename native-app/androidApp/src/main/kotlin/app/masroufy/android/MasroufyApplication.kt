package app.masroufy.android

import android.app.Application
import android.os.Process
import android.os.SystemClock
import app.masroufy.perf.PerfTrace
import app.masroufy.device.BackgroundGraph
import app.masroufy.device.MasroufyBackground

/**
 * بداية العملية (الشاشة أو عامل الخلفية): التجميع مرة واحدة + الجلسة + **تسجيل دورة الخلفية** (`MasroufyBackground.install` — ARCHITECTURE §31.29):
 * رسايل البنك الجديدة بتتسجل لوحدها بعد وصولها وكل ٤ ساعات، للحساب اللي داخل.
 */
class MasroufyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // تقارير الأعطال + «سجل المشاكل» (OVERRIDES §90) قبل أي حاجة: الوقعة بعد كده بتتسجل على الجوال ثم Crashlytics ثم معالج النظام
        val crash = AndroidCrashReports(this).also { it.apply() }
        val problems = ProblemLogSetup(this, crash).also { it.install() }
        crash.installCrashHandler()
        // بيانات الاستخدام (OVERRIDES §114): نسخ المالك بس — المحاكي والاختبارات من غير (`Usage.port` = null)
        app.masroufy.problems.Usage.port = FirebaseUsage.create(this)
        // قياس السرعة في نسخ المحاكي بس (سطور MPERF في logcat — HANDOVER §7)؛ نسخة المالك ما بتكتبش حاجة
        if (BuildConfig.FIREBASE_EMULATOR_PROJECT.isNotEmpty()) {
            PerfTrace.thread = { Thread.currentThread().name }
            PerfTrace.sink = { android.util.Log.i("MPERF", it) }
            PerfTrace.log("app create sinceProcessStart=${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()}")
        }
        container = AppContainer(this, problems)
        container.start()
        MasroufyBackground.install(this, BackgroundGraph { container.backgroundCycle() })
    }
}
