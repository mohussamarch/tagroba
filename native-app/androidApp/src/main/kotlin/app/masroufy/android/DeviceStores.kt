package app.masroufy.android

import android.content.Context
import app.masroufy.core.TextKey
import app.masroufy.port.AuthPort
import app.masroufy.port.AuthUser
import app.masroufy.port.authError
import app.masroufy.wiring.SeenAlerts

/** «تعليم الكل كمقروء» على الجهاز ده (لكل حساب مفتاح لوحده) — مش بيانات حساب (OVERRIDES §74 ❓ المزامنة مستنية المالك). */
class AndroidSeenAlerts(context: Context, uid: String) : SeenAlerts {
    private val prefs = context.getSharedPreferences("alert-seen", Context.MODE_PRIVATE)
    private val key = "seen|$uid"

    override fun read(): Set<String> = runCatching { prefs.getStringSet(key, emptySet()).orEmpty().toSet() }.getOrDefault(emptySet())

    override fun addAll(threadKeys: Collection<String>) {
        runCatching { prefs.edit().putStringSet(key, read() + threadKeys).apply() }
    }
}

/**
 * من غير إعدادات فايربيز (البناء من غير `secrets/google-services.json`): مفيش حد داخل، وأي دخول بيرجع «طريقة الدخول دي مش مفعّلة»
 * بدل ما التطبيق يقع. للتجربة: نوع البناء `emulator`.
 */
object NoFirebaseAuth : AuthPort {
    override fun currentUser(): AuthUser? = null

    override fun observe(callback: (AuthUser?) -> Unit): () -> Unit {
        callback(null)
        return {}
    }

    override val googleUnavailableReason: String get() = app.masroufy.core.uiText(TextKey.AUTH_GOOGLE_PENDING)

    override suspend fun signInWithEmail(email: String, password: String): AuthUser = throw authError("auth/operation-not-allowed")

    override suspend fun registerWithEmail(email: String, password: String): AuthUser = throw authError("auth/operation-not-allowed")

    override suspend fun signInWithGoogle(idToken: String): AuthUser = throw authError("auth/operation-not-allowed")

    override suspend fun sendPasswordReset(email: String) = throw authError("auth/operation-not-allowed")

    override suspend fun signOut() = Unit
}
