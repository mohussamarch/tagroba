package app.masroufy.memory

import app.masroufy.port.AuthPort
import app.masroufy.port.AuthUser
import app.masroufy.port.authError
import app.masroufy.port.checkedCredentials

/**
 * تسجيل دخول في الذاكرة للاختبارات — بنفس أكواد فايربيز ونفس سلوك المشروع الجديد في فايربيز (حماية تعداد الإيميلات مفعّلة):
 * إيميل مش موجود أو كلمة سر غلط ⇒ نفس الخطأ `auth/invalid-credential`، وطلب إعادة التعيين لإيميل مش موجود بيعدّي من غير خطأ.
 */
class MemoryAuth(private val uidPrefix: String = "u-") : AuthPort {
    private class Account(val uid: String, val password: String)

    private val accounts = LinkedHashMap<String, Account>()
    private val listeners = mutableListOf<(AuthUser?) -> Unit>()
    private var current: AuthUser? = null
    val resetRequests = mutableListOf<String>()

    override val googleUnavailableReason: String? = null

    override fun currentUser(): AuthUser? = current

    override fun observe(callback: (AuthUser?) -> Unit): () -> Unit {
        listeners += callback
        callback(current)
        return { listeners -= callback }
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthUser {
        val key = checkedCredentials(email, password)
        val account = accounts[key.lowercase()]
        if (account == null || account.password != password) throw authError("auth/invalid-credential")
        return enter(AuthUser(account.uid, key, null))
    }

    override suspend fun registerWithEmail(email: String, password: String): AuthUser {
        val key = checkedCredentials(email, password)
        if (!key.contains('@')) throw authError("auth/invalid-email")
        if (key.lowercase() in accounts) throw authError("auth/email-already-in-use")
        if (password.length < 6) throw authError("auth/weak-password")
        val account = Account("$uidPrefix${accounts.size + 1}", password)
        accounts[key.lowercase()] = account
        return enter(AuthUser(account.uid, key, null))
    }

    override suspend fun sendPasswordReset(email: String) {
        val key = email.trim()
        if (!key.contains('@')) throw authError("auth/invalid-email")
        resetRequests += key
    }

    override suspend fun signOut() {
        current = null
        listeners.toList().forEach { it(null) }
    }

    private fun enter(user: AuthUser): AuthUser {
        current = user
        listeners.toList().forEach { it(user) }
        return user
    }
}
