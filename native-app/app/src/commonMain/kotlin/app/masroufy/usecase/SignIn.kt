package app.masroufy.usecase

import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.AuthError
import app.masroufy.port.AuthPort
import app.masroufy.port.AuthUser
import app.masroufy.port.GoogleIdToken
import app.masroufy.port.GoogleIdTokenSource
import app.masroufy.port.SignOutCleanup
import app.masroufy.port.hidesAccountOnReset

/**
 * الدخول (spec/04 · OVERRIDES §26 · §63): شاشات `SignInEmail` و`ResetPasswordSheet` و`Onboarding` بتنادي ده — مش `AuthPort` مباشرة
 * (CLAUDE.md #4). الأخطاء [AuthError] بنفس رسايل التطبيق الحالي وحقلها (الشاشة بتعرضها جنب الخانة).
 */
sealed interface GoogleSignIn {
    data class SignedIn(val user: AuthUser) : GoogleSignIn

    /** المستخدم قفل النافذة — الشاشة ترجع لحالها من غير رسالة. */
    data object Cancelled : GoogleSignIn

    data class Unavailable(val reason: String) : GoogleSignIn
}

/** نتيجة «امسح حسابي» (r06-links2). */
sealed interface DeleteAccountResult {
    data object Done : DeleteAccountResult

    /** البيانات اتمسحت، بس حساب الدخول محتاج دخول قريب ⇒ ادخل تاني وجرّب تاني (المحاولة التانية بتكمّل). */
    data object NeedsRecentLogin : DeleteAccountResult

    data class Failed(val reason: String) : DeleteAccountResult
}

class SignIn(
    private val auth: AuthPort,
    private val google: GoogleIdTokenSource?,
    private val cleanup: SignOutCleanup,
    /** r06-links2: بيمسح كل اللي تحت `users/{uid}` على السيرفر. null ⇒ «امسح حسابي» مش متاح. */
    private val eraser: app.masroufy.port.AccountDataEraser? = null,
) {
    constructor(auth: AuthPort, google: GoogleIdTokenSource?) : this(auth, google, SignOutCleanup.NONE)

    val canDeleteAccount: Boolean get() = eraser != null

    /**
     * r06-links2 (§118-ج «امسح حسابي» — مطلوب للمتجر كمان): **مالوش رجوع**. بالترتيب: بيانات الحساب على السيرفر (لازم قبل حساب الدخول —
     * القواعد بتطلب إنك داخل) ⇒ اللي على الجوال (نفس مسح الخروج) ⇒ حساب الدخول ⇒ نسخة فايربيز على الجوال. فايربيز عايز دخول قريب ⇒
     * خروج و[DeleteAccountResult.NeedsRecentLogin]: يدخل تاني ويضغط تاني (البيانات اتمسحت خلاص، فالمرة التانية بتمسح حساب الدخول بس).
     */
    suspend fun deleteAccount(): DeleteAccountResult {
        val erase = eraser ?: return DeleteAccountResult.Failed(uiText(TextKey.NOT_AVAILABLE))
        val uid = auth.currentUser()?.uid ?: return DeleteAccountResult.Failed(uiText(TextKey.NOT_AVAILABLE))
        try {
            erase.eraseAll(uid)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return DeleteAccountResult.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
        runCatching { cleanup.beforeSignOut(uid) }
        try {
            auth.deleteCurrentUser()
        } catch (e: AuthError) {
            if (e.code != "auth/requires-recent-login") return DeleteAccountResult.Failed(e.message ?: e.code)
            runCatching { auth.signOut() }
            runCatching { cleanup.afterSignOut() }
            return DeleteAccountResult.NeedsRecentLogin
        }
        runCatching { cleanup.afterSignOut() }
        return DeleteAccountResult.Done
    }

    /** null = دخول جوجل متاح. غير كده الجملة اللي بتتعرض بدل الزرار. */
    val googleUnavailableReason: String?
        get() = if (google == null) uiText(TextKey.AUTH_GOOGLE_PENDING) else auth.googleUnavailableReason

    fun currentUser(): AuthUser? = auth.currentUser()

    suspend fun withEmail(email: String, password: String): AuthUser = auth.signInWithEmail(email, password)

    suspend fun register(email: String, password: String): AuthUser = auth.registerWithEmail(email, password)

    /** نافذة جوجل ⇒ فايربيز. الأخطاء من فايربيز [AuthError] زي الإيميل. */
    suspend fun withGoogle(): GoogleSignIn {
        googleUnavailableReason?.let { return GoogleSignIn.Unavailable(it) }
        return when (val token = google!!.request()) {
            is GoogleIdToken.Token -> GoogleSignIn.SignedIn(auth.signInWithGoogle(token.idToken))
            GoogleIdToken.Cancelled -> GoogleSignIn.Cancelled
            is GoogleIdToken.Unavailable -> GoogleSignIn.Unavailable(token.reason)
        }
    }

    /**
     * رابط إعادة التعيين — **نفس النتيجة سواء الإيميل ليه حساب أو لأ** (تصليح أمان OVERRIDES §76؛ المستودع نفسه بيعمل كده كمان —
     * هنا تاني عشان أي تنفيذ قديم أو جديد للمنفذ ما يكشفش مين مسجّل).
     */
    suspend fun sendPasswordReset(email: String) {
        try {
            auth.sendPasswordReset(email)
        } catch (e: AuthError) {
            if (!hidesAccountOnReset(e.code)) throw e
        }
    }

    /**
     * تسجيل الخروج (AUTH-4): مسح اللي على الجهاز للحساب ده ⇒ الخروج ⇒ مسح نسخة فايربيز على الجهاز. التنضيف لو فشل ما بيوقفش الخروج،
     * والخروج لو فشل بيرمي زي الأول (والحساب بيفضل داخل).
     */
    suspend fun signOut() {
        auth.currentUser()?.uid?.let { uid -> runCatching { cleanup.beforeSignOut(uid) } }
        auth.signOut()
        runCatching { cleanup.afterSignOut() }
    }
}
