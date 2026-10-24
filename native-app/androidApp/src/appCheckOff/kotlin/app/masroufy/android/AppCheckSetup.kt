package app.masroufy.android

import android.content.Context

/**
 * AUTH-1 — App Check **مقفول** (الافتراضي): مفيش مكتبة ولا مزوّد. بيتفتح بـ`-Pmasroufy.appCheck=true` (النسخة التانية في `src/appCheckOn`)
 * بعد ما المالك يسجّل البرنامج في Play Integrity (`tools/security/APP-CHECK-STEPS.md`). الفرض على Firestore من اللوحة بعد التجربة بس.
 */
object AppCheckSetup {
    const val ENABLED = false

    fun install(context: Context, emulator: Boolean) = Unit
}
