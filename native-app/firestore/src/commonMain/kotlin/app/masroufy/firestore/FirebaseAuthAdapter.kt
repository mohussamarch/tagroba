package app.masroufy.firestore

import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.AccountPort
import app.masroufy.port.AuthError
import app.masroufy.port.AuthPort
import app.masroufy.port.AuthUser
import app.masroufy.port.authError
import app.masroufy.port.checkedCredentials
import app.masroufy.port.hidesAccountOnReset
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.FirebaseTooManyRequestsException
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * تسجيل الدخول بفايربيز (GitLive) — نقل `FirebaseAuthAdapter.ts`. كل خطأ بيتحول لنفس كود مكتبة الويب (`auth/…`)
 * ⇒ نفس الرسالة العربي ونفس الحقل اللي في التطبيق الحالي ([authError]).
 * **دخول جوجل:** رمز الهوية بييجي من نافذة الجهاز (أندرويد: Credential Manager في `:androidApp`) ⇒ [signInWithGoogle].
 * [googleReady] = التطبيق عنده معرّف عميل الويب (من `google-services.json`) — من غيره السبب بيتقال للمستخدم.
 */
class FirebaseAuthAdapter(
    private val auth: FirebaseAuth,
    private val scope: CoroutineScope,
    private val googleReady: Boolean = false,
) : AuthPort, AccountPort {
    override val googleUnavailableReason: String? get() = if (googleReady) null else uiText(TextKey.AUTH_GOOGLE_PENDING)

    override fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    override fun observe(callback: (AuthUser?) -> Unit): () -> Unit {
        val job = scope.launch { auth.authStateChanged.collect { callback(it?.toAuthUser()) } }
        return { job.cancel() }
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthUser {
        val trimmed = checkedCredentials(email, password)
        return translated { auth.signInWithEmailAndPassword(trimmed, password).user!!.toAuthUser() }
    }

    override suspend fun registerWithEmail(email: String, password: String): AuthUser {
        val trimmed = checkedCredentials(email, password)
        return translated { auth.createUserWithEmailAndPassword(trimmed, password).user!!.toAuthUser() }
    }

    override suspend fun signInWithGoogle(idToken: String): AuthUser {
        if (idToken.isBlank()) throw authError("auth/invalid-credential")
        return translated { auth.signInWithCredential(GoogleAuthProvider.credential(idToken, null)).user!!.toAuthUser() }
    }

    /** تصليح أمان (OVERRIDES §76): «مفيش حساب بالإيميل ده» ⇒ نجاح ظاهريًا ([hidesAccountOnReset]) — ما بنكشفش مين مسجّل. */
    override suspend fun sendPasswordReset(email: String) {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) throw authError("auth/invalid-email")
        try {
            translated { auth.sendPasswordResetEmail(trimmed) }
        } catch (e: AuthError) {
            if (!hidesAccountOnReset(e.code)) throw e
        }
    }

    override suspend fun signOut() = translated { auth.signOut() }

    // AccountPort — صفحة الحساب: التطبيق ما بيستلمش كلمة سر، بيبعت إيميل إعادة تعيين بس
    override fun email(): String? = auth.currentUser?.email

    override suspend fun sendPasswordReset() = sendPasswordReset(email() ?: throw authError("auth/user-not-found"))

    private suspend fun <T> translated(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthError) {
        throw e
    } catch (e: Throwable) {
        throw authError(authCodeOf(e))
    }
}

private fun FirebaseUser.toAuthUser() = AuthUser(uid, email, displayName)

/**
 * كود الخطأ بصيغة الويب. الأدق من كود مكتبة الجهاز نفسه ([platformAuthCode] — على أندرويد `ERROR_WEAK_PASSWORD` ⇒ `auth/weak-password`)،
 * وإلا من نوع الخطأ (الآيفون — ⚠️ اتبنى بس، والإيميل الغلط هناك ممكن يطلع «الإيميل أو كلمة السر غلط»).
 */
internal fun authCodeOf(e: Throwable): String = platformAuthCode(e) ?: when (e) {
    is FirebaseNetworkException -> "auth/network-request-failed"
    is FirebaseTooManyRequestsException -> "auth/too-many-requests"
    is FirebaseAuthUserCollisionException -> "auth/email-already-in-use"
    is FirebaseAuthWeakPasswordException -> "auth/weak-password"
    is FirebaseAuthInvalidUserException -> "auth/user-not-found"
    is FirebaseAuthInvalidCredentialsException -> "auth/invalid-credential"
    else -> "app/${e::class.simpleName ?: "unknown"}"
}

/** كود مكتبة الجهاز بصيغة الويب، أو `null` لو المكتبة ما بتديش كود. */
internal expect fun platformAuthCode(e: Throwable): String?

/** `ERROR_EMAIL_ALREADY_IN_USE` ⇒ `auth/email-already-in-use` — نفس أسماء مكتبة الويب. */
internal fun webAuthCode(nativeCode: String): String = "auth/" + nativeCode.removePrefix("ERROR_").lowercase().replace('_', '-')
