package app.masroufy.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import app.masroufy.usecase.AppLock
import app.masroufy.usecase.SignIn
import kotlinx.coroutines.flow.StateFlow

/**
 * الجلسة كلها (التطبيق بيرسم حسبها — `MasroufyApp`): بيبدأ ⇒ مفيش حد داخل (شاشة الدخول) ⇒ «بيفتح» (التنزيل الأول بشريط تقدم — §54) ⇒ جاهز
 * (الهيكل بحالات استخدام البلد الشغالة). التنفيذ في `:androidApp` (`AppContainer` فوق `AccountSession`).
 */
sealed interface AppSession {
    data object Starting : AppSession

    data object SignedOut : AppSession

    /** [synced] من [total] مجموعة نزلت (لشريط التقدم). */
    data class Opening(val synced: Int, val total: Int) : AppSession

    /** [deps] بتتبني من جديد مع كل تبديل بلد — الشاشات بتاخدها من `LocalSpace`. */
    data class Ready(val uid: String, val deps: SpaceDeps) : AppSession
}

/** أذونات الجهاز — نافذة النظام نفسها بتطلع من الجوال. الشرح قبلها شغل الشاشة (`SmsPermission` · أول تشغيل). */
interface Permissions {
    /** رسايل البنك (أندرويد بس): `true` = الإذن موجود بعد الطلب. الآيفون ⇒ `false` من غير نافذة. */
    suspend fun requestSms(): Boolean

    fun hasSms(): Boolean

    /** إشعارات الجوال (أندرويد 13+ بيسأل؛ أقدم = موجود). */
    suspend fun requestNotifications(): Boolean
}

/** اللي على مستوى التطبيق كله (مش بلد بعينها). */
interface AppDeps {
    val session: StateFlow<AppSession>

    /** الدخول والخروج ونسيت كلمة المرور — الشاشات بتنادي ده مش `AuthPort` (CLAUDE.md #4). */
    val signIn: SignIn

    /** قفل التطبيق بالبصمة (§21) — null = الجهاز ما يدعموش. */
    val lock: AppLock?

    val permissions: Permissions

    /** نسخة اختبار متصلة بمحاكي فايربيز على الكمبيوتر — بتتكتب على شاشة الدخول عشان ما تتلخبطش مع الحقيقية. */
    val emulator: Boolean
}

val LocalApp = staticCompositionLocalOf<AppDeps> { error("AppDeps مش متقدّم") }

val LocalPermissions = staticCompositionLocalOf<Permissions> { error("Permissions مش متقدّمة") }
