package app.masroufy.usecase

import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.AuthError
import app.masroufy.port.AuthPort
import app.masroufy.port.AuthUser
import app.masroufy.port.GoogleIdToken
import app.masroufy.port.GoogleIdTokenSource
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

class SignIn(private val auth: AuthPort, private val google: GoogleIdTokenSource?) {
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

    suspend fun signOut() = auth.signOut()
}
