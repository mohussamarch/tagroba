package app.masroufy.android

import android.content.Context
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize

/**
 * فايربيز للتطبيق: **الحقيقي** (مشروع المالك — من `google-services.json` اللي برا المستودع) أو **محاكي الكمبيوتر** (نوع البناء `emulator`،
 * مشروع `demo-…` اللي فايربيز ما بيقبلوش على سيرفر حقيقي ⇒ مستحيل يوصل لبيانات المالك). المحاكي: الدخول 9099 وFirestore 8089 على
 * `10.0.2.2` (الكمبيوتر من جوه محاكي أندرويد) — `native-app/firestore/emulator-auth/` بقواعد المشروع الحقيقية.
 * [webClientId] = معرّف عميل الويب لدخول جوجل (من الإعدادات) — null ⇒ دخول جوجل مش متاح والسبب بيتقال.
 */
class FirebaseHandles(val auth: FirebaseAuth, val firestore: FirebaseFirestore, val emulator: Boolean, val webClientId: String?)

const val EMULATOR_HOST = "10.0.2.2"
const val EMULATOR_AUTH_PORT = 9099
const val EMULATOR_FIRESTORE_PORT = 8089

fun openFirebase(context: Context): FirebaseHandles? {
    val emulatorProject = BuildConfig.FIREBASE_EMULATOR_PROJECT
    if (emulatorProject.isNotEmpty()) {
        val app = Firebase.initialize(
            context,
            FirebaseOptions(applicationId = "1:000000000000:android:0000000000000002", apiKey = "emulator-only", projectId = emulatorProject),
            "emulator",
        )
        val auth = Firebase.auth(app).also { it.useEmulator(EMULATOR_HOST, EMULATOR_AUTH_PORT) }
        val db = Firebase.firestore(app).also { it.useEmulator(EMULATOR_HOST, EMULATOR_FIRESTORE_PORT) }
        return FirebaseHandles(auth, db, emulator = true, webClientId = null)
    }
    if (!BuildConfig.HAS_FIREBASE_SETTINGS) return null
    // الإعدادات بتتقري من الموارد اللي إضافة google-services ولّدتها وقت البناء (من غير ما المفاتيح تبقى في الكود)
    val app = Firebase.initialize(context) ?: return null
    val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    val webClientId = if (id != 0) context.getString(id).takeIf { it.isNotBlank() } else null
    return FirebaseHandles(Firebase.auth(app), Firebase.firestore(app), emulator = false, webClientId = webClientId)
}
