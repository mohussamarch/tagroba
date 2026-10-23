package app.masroufy.android

import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.masroufy.ui.shell.ask.LocalSpeech
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.masroufy.ui.app.AppSession
import app.masroufy.ui.app.LockGate
import app.masroufy.ui.app.MasroufyApp
import app.masroufy.ui.app.ShellState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * الشاشة الوحيدة (كل التنقل جوه Compose — من غير مكتبة تنقل). `FragmentActivity` عشان نافذة البصمة (`AndroidDeviceLock`).
 * - زرار الرجوع ⇒ `ShellState.handleBack` (لوحة ⇒ شاشة ⇒ الرئيسية)، ولو مفيش حاجة ⇒ أندرويد يقفل التطبيق.
 * - القفل: `onStop` = راح الخلفية · `onStart` = رجع (`AppLock` بيقرر بعد ٥ دقايق).
 * - أول مرة الحساب يجهز ⇒ طلب إذن الإشعارات (أندرويد 13+) مرة واحدة. إذن الرسايل بيتطلب من شاشة رسايل البنك بعد الشرح.
 */
class MainActivity : FragmentActivity() {
    private val container get() = (application as MasroufyApplication).container
    private lateinit var shell: ShellState
    private lateinit var lock: LockGate
    private lateinit var resume: app.masroufy.ui.app.ResumeRefresh

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        container.activity = this
        val permissions = AndroidPermissions(this)
        shell = ShellState()
        lock = LockGate(container.appLock)
        resume = app.masroufy.ui.app.ResumeRefresh(shell.changes)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (shell.handleBack()) return
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            },
        )
        // نسخ المحاكي بس: دخول حساب القياس الوهمي من سطر الأوامر (adb … --es perf_uid <المعرّف>) — من غير كلمة سر
        intent?.getStringExtra("perf_uid")?.let(container::perfSignIn)
        openFrom(intent)
        val deps = container.appDeps(permissions)
        // ميكروفون المساعد: نافذة التعرّف على الكلام بتاعة الجوال (§79.2-8) — لازم تتسجل هنا قبل ما الشاشة تبدأ
        val speech = AndroidSpeech(this)
        // صورة الشخص (r06-person): نافذة اختيار الصور — على الجهاز بس، من غير رفع
        val photos = AndroidPersonPhotos(this)
        // المظهر (فاتح · غامق · زي الجوال) وإخفاء الأرقام: المحفوظ على الجهاز بيتقرا قبل أول رسم عشان ما يبقاش فيه وميض
        val nightNow = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        app.masroufy.ui.theme.Appearance.attach(AndroidPrefStore(this), nightNow)
        // «تخطّي» / «بعدين» على لوحة تابي وتمارا: بيتفتكروا لكل بلد بعد قفل البرنامج (r06-polish)
        app.masroufy.ui.screens.dues.BnplLater.attach(AndroidPrefStore(this))
        setContent {
            // 0.5.2: أيقونات شريط الحالة والتنقل بتتبع مظهر البرنامج (فاتحة على الغامق) وقت الاختيار — مش مظهر الجوال
            val dark = app.masroufy.ui.theme.Appearance.isDark
            androidx.compose.runtime.LaunchedEffect(dark) { systemBars(dark) }
            CompositionLocalProvider(LocalSpeech provides speech, app.masroufy.ui.screens.people.LocalPersonPhotos provides photos) { MasroufyApp(deps, shell, lock, reduceMotion = animationsOff()) }
        }
        askNotificationsOnce(permissions)
    }

    /** شفاف من غير طبقة تباين (كانت بتعمل ضباب فاتح تحت في الداكن) — والأيقونات فاتحة على الغامق. */
    private fun systemBars(dark: Boolean) {
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (dark) androidx.activity.SystemBarStyle.dark(clear) else androidx.activity.SystemBarStyle.light(clear, clear)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    /** PLAN-0.6 §13 (ج): الضغطة على إشعار حالة القراية (والتطبيق مفتوح أو في الخلفية) ⇒ «رسائل البنك». */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    private fun openFrom(intent: android.content.Intent?) {
        if (intent?.getStringExtra(app.masroufy.device.AndroidDeviceNotifier.EXTRA_OPEN) != app.masroufy.device.AndroidDeviceNotifier.OPEN_BANK_SMS) return
        // مرة واحدة: إعادة إنشاء الشاشة (تدوير الجوال) ما تفتحهاش تاني
        intent.removeExtra(app.masroufy.device.AndroidDeviceNotifier.EXTRA_OPEN)
        shell.openBankSms = true
    }

    override fun onStart() {
        super.onStart()
        container.activity = this
        lock.onVisible()
        resume.onVisible()
    }

    override fun onStop() {
        lock.onHidden()
        super.onStop()
    }

    override fun onDestroy() {
        if (container.activity === this) container.activity = null
        super.onDestroy()
    }

    /** «تقليل الحركة» من الجهاز (مدة الحركات صفر) ⇒ ظهور بالشفافية بس (DESIGN-SYSTEM v0.4). */
    private fun animationsOff(): Boolean =
        runCatching { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

    private fun askNotificationsOnce(permissions: AndroidPermissions) {
        val prefs = getSharedPreferences("first-run", MODE_PRIVATE)
        if (prefs.getBoolean(ASKED_NOTIFICATIONS, false)) return
        lifecycleScope.launch {
            container.appSession.first { it is AppSession.Ready }
            prefs.edit().putBoolean(ASKED_NOTIFICATIONS, true).apply()
            permissions.requestNotifications()
        }
    }

    private companion object {
        const val ASKED_NOTIFICATIONS = "asked-notifications"
    }
}
