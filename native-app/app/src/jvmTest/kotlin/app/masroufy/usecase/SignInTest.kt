package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.uiText
import app.masroufy.memory.MemoryAuth
import app.masroufy.port.AuthError
import app.masroufy.port.AuthField
import app.masroufy.port.AuthPort
import app.masroufy.port.AuthUser
import app.masroufy.port.GoogleIdToken
import app.masroufy.port.authError
import app.masroufy.port.hidesAccountOnReset
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** حالة استخدام الدخول (الشاشات بتناديها مش المنفذ) + تصليح الأمان: إعادة التعيين ما بتكشفش مين عنده حساب (OVERRIDES §76). */
class SignInTest {
    @AfterTest
    fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    /** منفذ قديم بيرمي «مفيش حساب» (زي `FirebaseAuthAdapter` قبل التصليح) — حالة الاستخدام لازم تخبّيه برضه. */
    private class LeakyAuth(private val inner: MemoryAuth = MemoryAuth()) : AuthPort by inner {
        var resets = 0
        override suspend fun sendPasswordReset(email: String) {
            resets++
            if (email.trim().isEmpty()) throw authError("auth/invalid-email")
            if (email.startsWith("nobody")) throw authError("auth/user-not-found")
        }
    }

    @Test fun onlyUserNotFoundIsHiddenOnReset() {
        assertTrue(hidesAccountOnReset("auth/user-not-found"))
        for (code in listOf("auth/invalid-email", "auth/network-request-failed", "auth/too-many-requests", "auth/invalid-credential")) {
            assertFalse(hidesAccountOnReset(code), code)
        }
    }

    @Test fun resetLooksTheSameWhetherOrNotTheEmailHasAnAccount() = runBlocking<Unit> {
        val auth = LeakyAuth()
        val signIn = SignIn(auth, null)
        signIn.sendPasswordReset("nobody@example.com") // ما بيرميش — نفس نتيجة الإيميل المسجّل
        signIn.sendPasswordReset("me@example.com")
        assertEquals(2, auth.resets)
        // الإيميل الفاضي لسه خطأ جنب الخانة (ما بيكشفش حاجة)
        val e = assertFailsWith<AuthError> { signIn.sendPasswordReset("  ") }
        assertEquals(AuthField.EMAIL, e.field)
    }

    @Test fun emailSignInAndRegisterGoThroughThePort() = runBlocking<Unit> {
        val auth = MemoryAuth()
        val signIn = SignIn(auth, null)
        val user = signIn.register("me@example.com", "secret1")
        assertEquals(user, signIn.currentUser())
        signIn.signOut()
        assertNull(signIn.currentUser())
        assertFailsWith<AuthError> { signIn.withEmail("me@example.com", "wrong-1") }
        assertEquals(user.uid, signIn.withEmail("me@example.com", "secret1").uid)
    }

    @Test fun googleWithoutADeviceSourceSaysWhy() = runBlocking<Unit> {
        val signIn = SignIn(MemoryAuth(), null)
        assertEquals(uiText(TextKey.AUTH_GOOGLE_PENDING), signIn.googleUnavailableReason)
        assertIs<GoogleSignIn.Unavailable>(signIn.withGoogle())
    }

    @Test fun googleTokenSignsInAndCancelIsSilent() = runBlocking<Unit> {
        val auth = MemoryAuth()
        var answer: GoogleIdToken = GoogleIdToken.Token("token-a")
        val signIn = SignIn(auth) { answer }
        assertNull(signIn.googleUnavailableReason)
        val first = assertIs<GoogleSignIn.SignedIn>(signIn.withGoogle())
        signIn.signOut()
        val again = assertIs<GoogleSignIn.SignedIn>(signIn.withGoogle())
        assertEquals(first.user.uid, again.user.uid, "نفس حساب جوجل ⇒ نفس الحساب")
        answer = GoogleIdToken.Cancelled
        assertIs<GoogleSignIn.Cancelled>(signIn.withGoogle())
        answer = GoogleIdToken.Unavailable("no account")
        assertEquals(GoogleSignIn.Unavailable("no account"), signIn.withGoogle())
        val user: AuthUser? = auth.currentUser()
        assertEquals(first.user.uid, user?.uid, "الإلغاء ما بيخرّجش")
    }
}
