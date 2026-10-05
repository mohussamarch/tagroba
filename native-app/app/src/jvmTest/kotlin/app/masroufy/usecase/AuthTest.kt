package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Language
import app.masroufy.core.Texts
import app.masroufy.memory.MemoryAuth
import app.masroufy.port.AuthError
import app.masroufy.port.AuthField
import app.masroufy.port.AuthUser
import app.masroufy.port.authError
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** جدول أخطاء الدخول = جدول التطبيق الحالي (`FirebaseAuthAdapter.ts`)، وتسجيل الدخول في الذاكرة. */
class AuthTest {
    // النص المتوقع هنا = نص التطبيق الحالي = النسخة المصرية (OVERRIDES §66)
    @BeforeTest
    fun egyptianText() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
    }

    @AfterTest
    fun defaultText() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun errorTableMatchesTheCurrentApp() {
        Texts.language = Language.AR
        // نفس النص ونفس الحقل اللي في `MESSAGES` بتاع التطبيق الحالي
        val expected = mapOf(
            "auth/invalid-email" to ("الإيميل مش مكتوب صح" to AuthField.EMAIL),
            "auth/user-disabled" to ("الحساب ده متوقف" to AuthField.FORM),
            "auth/user-not-found" to ("مفيش حساب بالإيميل ده" to AuthField.EMAIL),
            "auth/wrong-password" to ("كلمة السر غلط" to AuthField.PASSWORD),
            "auth/invalid-credential" to ("الإيميل أو كلمة السر غلط" to AuthField.FORM),
            "auth/email-already-in-use" to ("الإيميل ده مسجّل قبل كده — جرّب تسجيل الدخول" to AuthField.EMAIL),
            "auth/weak-password" to ("كلمة السر قصيرة — لازم ٦ حروف على الأقل" to AuthField.PASSWORD),
            "auth/missing-password" to ("اكتب كلمة السر" to AuthField.PASSWORD),
            "auth/too-many-requests" to ("محاولات كتير. استنى شوية وجرّب تاني" to AuthField.FORM),
            "auth/network-request-failed" to ("مفيش إنترنت. اتأكد من الاتصال وجرّب تاني" to AuthField.FORM),
            "auth/operation-not-allowed" to ("طريقة الدخول دي مش مفعّلة في المشروع" to AuthField.FORM),
        )
        for ((code, want) in expected) {
            val e = authError(code)
            assertEquals(want.first, e.message, code)
            assertEquals(want.second, e.field, code)
        }
        // كود مش معروف: بيتعرض برمزه، مش رسالة عامة
        assertEquals("حصل خطأ مش متوقع (auth/strange)", authError("auth/strange").message)
    }

    @Test fun memorySignInFlow() = runBlocking<Unit> {
        val auth = MemoryAuth()
        val seen = mutableListOf<AuthUser?>()
        val cancel = auth.observe { seen += it }
        val user = auth.registerWithEmail("  me@example.com ", "secret1")
        assertEquals("me@example.com", user.email, "الإيميل بيتقص من المسافات")
        assertEquals("auth/email-already-in-use", assertFailsWith<AuthError> { auth.registerWithEmail("ME@example.com", "secret2") }.code)
        assertEquals("auth/weak-password", assertFailsWith<AuthError> { auth.registerWithEmail("two@example.com", "123") }.code)
        assertEquals("auth/missing-password", assertFailsWith<AuthError> { auth.signInWithEmail("me@example.com", "") }.code)
        assertEquals("auth/invalid-email", assertFailsWith<AuthError> { auth.signInWithEmail("   ", "x") }.code)
        auth.signOut()
        assertEquals("auth/invalid-credential", assertFailsWith<AuthError> { auth.signInWithEmail("me@example.com", "wrong!") }.code)
        assertEquals(user.uid, auth.signInWithEmail("me@example.com", "secret1").uid)
        cancel()
        auth.signOut()
        assertEquals(listOf(null, user, null, user), seen, "بعد الإلغاء ما بيوصلش حاجة")
        assertNull(auth.currentUser())
    }
}
