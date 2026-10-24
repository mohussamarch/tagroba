package app.masroufy.port

/**
 * اللي بيتمسح من الجهاز مع تسجيل الخروج (AUTH-4): [beforeSignOut] والحساب لسه داخل (قراية رسايل البنك بتقف ورسايله المحفوظة بتتمسح ·
 * نسخ «قبل الإصلاح» · المسودات لكل حساب)، و[afterSignOut] بعد الخروج (نسخة فايربيز على الجهاز: terminate + clearPersistence).
 * أي فشل هنا ما بيمنعش الخروج نفسه.
 */
interface SignOutCleanup {
    suspend fun beforeSignOut(uid: String)

    suspend fun afterSignOut()

    companion object {
        val NONE: SignOutCleanup = object : SignOutCleanup {
            override suspend fun beforeSignOut(uid: String) = Unit

            override suspend fun afterSignOut() = Unit
        }
    }
}
