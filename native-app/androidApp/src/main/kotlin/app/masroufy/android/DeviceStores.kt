package app.masroufy.android

import android.content.Context
import app.masroufy.core.TextKey
import app.masroufy.firestore.FirstSyncMarks
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
 * «الجهاز ده خلّص تنزيل أول كامل للحساب» (`AccountSession` — الفتح الجاي من نسخة الجهاز لو مفيش نت، §54). علامة لكل حساب، مش بيانات.
 * بتتمسح مع بيانات التطبيق (إلغاء التثبيت) ⇒ الجهاز بيرجع يستنى التنزيل كله.
 */
class AndroidFirstSyncMarks(context: Context) : FirstSyncMarks {
    private val prefs = context.getSharedPreferences("first-sync", Context.MODE_PRIVATE)

    override fun completed(uid: String): Boolean = runCatching { prefs.getBoolean("done|$uid", false) }.getOrDefault(false)

    override fun markCompleted(uid: String) {
        runCatching { prefs.edit().putBoolean("done|$uid", true).apply() }
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
