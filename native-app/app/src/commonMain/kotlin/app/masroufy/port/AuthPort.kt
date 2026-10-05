package app.masroufy.port

import app.masroufy.core.TextKey
import app.masroufy.core.uiText

/** تسجيل الدخول — نقل `AuthPort.ts`. التنفيذ في `:firestore` (فايربيز) و`memory` (الاختبارات). */

data class AuthUser(val uid: String, val email: String?, val displayName: String?)

/** الحقل المسؤول عن الخطأ — الشاشة بتعرض الرسالة جنبه (spec/04). */
enum class AuthField { EMAIL, PASSWORD, FORM }

/** خطأ بلغة المستخدم: الشاشة بتعرض `message` زي ما هو. `code` بصيغة فايربيز الموحدة (`auth/…`) للسجل والاختبارات. */
class AuthError(val code: String, val field: AuthField, message: String) : Exception(message)

interface AuthPort {
    /** الحساب الداخل دلوقتي؛ `null` = مفيش حد داخل. */
    fun currentUser(): AuthUser?

    /** بيبلّغ بالحساب الحالي فورًا وبعدين مع كل دخول أو خروج (أو انتهاء الجلسة). بيرجّع دالة الإلغاء — زي `observe` في التطبيق الحالي. */
    fun observe(callback: (AuthUser?) -> Unit): () -> Unit

    /** سبب إن دخول جوجل مش متاح، أو `null` لو متاح. */
    val googleUnavailableReason: String?

    suspend fun signInWithEmail(email: String, password: String): AuthUser

    suspend fun registerWithEmail(email: String, password: String): AuthUser

    suspend fun sendPasswordReset(email: String)

    suspend fun signOut()
}

/**
 * من كود فايربيز (`auth/…`) لرسالة المستخدم والحقل — **نفس جدول التطبيق الحالي** (`FirebaseAuthAdapter.ts`).
 * كود مش معروف بيتعرض بصراحة مع رمزه، مش ورا رسالة عامة كدابة.
 */
fun authError(code: String): AuthError {
    val (key, field) = when (code) {
        "auth/invalid-email" -> TextKey.AUTH_INVALID_EMAIL to AuthField.EMAIL
        "auth/user-disabled" -> TextKey.AUTH_USER_DISABLED to AuthField.FORM
        "auth/user-not-found" -> TextKey.AUTH_USER_NOT_FOUND to AuthField.EMAIL
        "auth/wrong-password" -> TextKey.AUTH_WRONG_PASSWORD to AuthField.PASSWORD
        "auth/invalid-credential" -> TextKey.AUTH_INVALID_CREDENTIAL to AuthField.FORM
        "auth/email-already-in-use" -> TextKey.AUTH_EMAIL_IN_USE to AuthField.EMAIL
        "auth/weak-password" -> TextKey.AUTH_WEAK_PASSWORD to AuthField.PASSWORD
        "auth/missing-password" -> TextKey.AUTH_MISSING_PASSWORD to AuthField.PASSWORD
        "auth/too-many-requests" -> TextKey.AUTH_TOO_MANY_REQUESTS to AuthField.FORM
        "auth/network-request-failed" -> TextKey.AUTH_NETWORK to AuthField.FORM
        "auth/operation-not-allowed" -> TextKey.AUTH_NOT_ALLOWED to AuthField.FORM
        else -> return AuthError(code, AuthField.FORM, uiText(TextKey.AUTH_UNEXPECTED, code))
    }
    return AuthError(code, field, uiText(key))
}

/**
 * فحص قبل ما نكلّم فايربيز: مكتبة أندرويد بترمي `IllegalArgumentException` (مش خطأ فايربيز) على النص الفاضي،
 * فبنرجّع نفس كود مكتبة الويب بدل وقوع من غير رسالة. الإيميل بيتقص من المسافات زي التطبيق الحالي.
 */
fun checkedCredentials(email: String, password: String): String {
    val trimmed = email.trim()
    if (trimmed.isEmpty()) throw authError("auth/invalid-email")
    if (password.isEmpty()) throw authError("auth/missing-password")
    return trimmed
}
