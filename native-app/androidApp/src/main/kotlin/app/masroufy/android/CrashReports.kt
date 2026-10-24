package app.masroufy.android

import android.content.Context
import android.os.Looper
import android.os.Process
import app.masroufy.core.ProblemCode
import app.masroufy.device.CrashReportPolicy
import app.masroufy.device.ProblemCodeReport
import app.masroufy.device.CrashSanitizer
import app.masroufy.port.CrashReportsPort
import app.masroufy.problems.Problems
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlin.system.exitProcess

/**
 * تقارير الأعطال (Firebase Crashlytics — OVERRIDES §90 · حدود §80):
 * - **شغالة** في نسخ المالك الحقيقية (`debug` · `fast` — `BuildConfig.CRASH_REPORTS`) لو مفتاح «ابعت تقارير الأعطال» مفتوح (الافتراضي مفتوح —
 *   سؤال للمالك). **مقفولة** في نسخ المحاكي (`emulator` · `fastEmulator`) ومن غير إعدادات فايربيز — ومن غير ما حاجة تقع.
 * - الجمع مقفول في `AndroidManifest.xml` لحد ما [apply] يقرر. المقفول ⇒ `deleteUnsentReports` (اللي اتخزن وهو مقفول ما يتبعتش بعدين).
 * - **مفيش رسالة خطأ أبدًا:** كل خطأ بيعدّي على [CrashSanitizer] (اسم النوع والمكان بس). الهوية = رقم فايربيز (`uid`) بس.
 *   المفاتيح التلاتة بس: [KEY_SCREEN] · [KEY_VERSION] · [KEY_COUNTRY]. مفيش `log` خالص (ممكن نص من البيانات).
 */
class AndroidCrashReports(context: Context) : CrashReportsPort {
    private val prefs = context.getSharedPreferences("crash-reports", Context.MODE_PRIVATE)
    private val crashlytics: FirebaseCrashlytics? = crashlyticsOrNull(context)

    override val available: Boolean = BuildConfig.CRASH_REPORTS && crashlytics != null

    override fun isOn(): Boolean = available && runCatching { prefs.getBoolean(KEY_ON, true) }.getOrDefault(true)

    override fun setOn(on: Boolean) {
        runCatching { prefs.edit().putBoolean(KEY_ON, on).apply() }
        apply()
    }

    override fun screenShown(route: String) {
        if (isOn()) crashlytics?.setCustomKey(KEY_SCREEN, route.take(64))
    }

    /** نوع البناء + المفتاح ⇒ الجمع مفتوح ولا مقفول. نسخ المحاكي (اللي فيها مكتبة فايربيز برضه) بتتقفل صراحةً. */
    fun apply() {
        val c = crashlytics ?: return
        val on = isOn()
        c.setCrashlyticsCollectionEnabled(on)
        if (!on) {
            // LOGS-2: التقارير اتقفلت ⇒ رقم الحساب بيتشال كمان (ما يفضلش متعلّق لو اتفتحت تاني بعد ما الحساب خرج)
            c.setUserId("")
            c.deleteUnsentReports()
            return
        }
        c.setCustomKey(KEY_VERSION, BuildConfig.VERSION_NAME)
    }

    /** الحساب الداخل ([uid] — رقم فايربيز المجهول بس) والبلد الشغالة. null ⇒ خرج. */
    fun session(uid: String?, country: String?) {
        val c = crashlytics ?: return
        // LOGS-2: الخروج بيمسح رقم الحساب **دايمًا** — حتى لو التقارير مقفولة (التقرير الجاي ما يتبعتش باسم حساب خرج)
        if (uid == null) {
            c.setUserId("")
            c.setCustomKey(KEY_COUNTRY, "")
        }
        if (!isOn()) return
        c.setUserId(uid.orEmpty())
        c.setCustomKey(KEY_COUNTRY, country.orEmpty().take(2))
    }

    /** مشكلة «خطأ» (بخطأ أو من غيره) أو «تحذير» من «سجل المشاكل» ⇒ تقرير من غير وقعة (non-fatal) — متنضف، والرسالة اسم الكود بس لو مفيش خطأ. */
    fun problem(code: ProblemCode, error: Throwable?) {
        // الوقعة نفسها بيبعتها معالج Crashlytics ([installCrashHandler]) — مش مرتين
        if (!CrashReportPolicy.shouldSend(code) || !isOn()) return
        crashlytics?.recordException(CrashSanitizer.sanitize(error ?: ProblemCodeReport(code.name), code.name))
    }

    /**
     * الوقعة (`UncaughtExceptionHandler`) بالترتيب: **السجل على الجوال** (`Problems` ⇒ `RecordProblem`) ⇒ **تقارير الأعطال** (معالج Crashlytics
     * اللي اتركّب قبلنا) ⇒ **معالج النظام الأصلي** (اللي بيقفل البرنامج). اللي بيتبعت للاتنين خطأ متنضف — الأصلي برسالته ما بيخرجش.
     */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val main = thread === Looper.getMainLooper().thread
            if (main) Problems.report(ProblemCode.CRASH_MAIN, error) else Problems.report(ProblemCode.CRASH_BACKGROUND, error)
            val clean: Throwable = runCatching { CrashSanitizer.sanitize(error) }.getOrElse { CrashSanitizer.sanitize(IllegalStateException()) }
            if (previous != null) previous.uncaughtException(thread, clean)
            else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    private companion object {
        const val KEY_ON = "enabled"
        const val KEY_SCREEN = "screen"
        const val KEY_VERSION = "app_version"
        const val KEY_COUNTRY = "country"

        /** المكتبة بتشتغل مع تطبيق فايربيز الافتراضي بس — مش موجود (من غير إعدادات) ⇒ null من غير ما حاجة تقع. */
        fun crashlyticsOrNull(context: Context): FirebaseCrashlytics? {
            val ready = runCatching { FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME } }.getOrDefault(false)
            return if (ready) runCatching { FirebaseCrashlytics.getInstance() }.getOrNull() else null
        }
    }
}
