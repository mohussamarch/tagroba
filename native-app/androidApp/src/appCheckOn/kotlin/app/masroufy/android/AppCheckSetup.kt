package app.masroufy.android

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * AUTH-1 — App Check **مفتوح** (`-Pmasroufy.appCheck=true`): فايربيز بيطلب من جوجل إثبات إن الطلب جاي من نسختنا الأصلية على جهاز حقيقي.
 * - نسخ المالك (debug · fast) والإصدار: **Play Integrity**.
 * - نسخ debug والمحاكي: **مزوّد التجربة** (debug provider) — بيطبع «رمز تجربة» في logcat مرة، والمالك بيضيفه في اللوحة
 *   (`tools/security/APP-CHECK-STEPS.md`). بيتلاقى بالاسم عشان نسخة الإصدار ما يبقاش فيها مزوّد تجربة خالص.
 * محاكي فايربيز المحلي (`emulator`) مش بيفحص App Check ⇒ مفيش تركيب هناك.
 * ⚠️ ⚠️ ما اتجربش على جهاز: محتاج تنزيل المكتبات (موافقة المالك) وتسجيل البرنامج في اللوحة. الفرض على Firestore من اللوحة بعد التجربة بس.
 */
object AppCheckSetup {
    const val ENABLED = true

    fun install(context: Context, emulator: Boolean) {
        if (emulator) return
        val app = runCatching { FirebaseApp.getInstance() }.getOrNull() ?: FirebaseApp.initializeApp(context) ?: return
        val factory = debugFactory() ?: PlayIntegrityAppCheckProviderFactory.getInstance()
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(factory)
    }

    private fun debugFactory(): AppCheckProviderFactory? = runCatching {
        Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory").getMethod("getInstance").invoke(null) as AppCheckProviderFactory
    }.getOrNull()
}
