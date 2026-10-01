package app.masroufy.firestore

import androidx.test.platform.app.InstrumentationRegistry
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize

/**
 * GitLive على محاكي أندرويد قدام Firestore Emulator على الكمبيوتر — مشروع `demo-…` (فايربيز ما بيقبلش اسم زي ده على سيرفر حقيقي).
 * المحاكي بيشتغل من `native-app/firestore/emulator/` بقواعد مفتوحة للمشروع التجريبي ده بس.
 */
object Emulator {
    const val PROJECT = "demo-masroufy-kt"

    /** 10.0.2.2 = الكمبيوتر نفسه من جوه محاكي أندرويد. */
    private const val HOST = "10.0.2.2"
    private const val PORT = 8088
    private val apps = mutableMapOf<String, FirebaseApp>()

    /** [device] = اسم «جهاز» — كل اسم ليه تطبيق فايربيز ونسخة محلية لوحده، فاختبار الجهازين بيشتغل على محاكي واحد. */
    fun firestore(device: String = "masroufy-kt-test"): FirebaseFirestore {
        apps[device]?.let { return Firebase.firestore(it) }
        val created = Firebase.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FirebaseOptions(applicationId = "1:000000000000:android:0000000000000000", apiKey = "emulator-only", projectId = PROJECT),
            device,
        )
        apps[device] = created
        return Firebase.firestore(created).also { it.useEmulator(HOST, PORT) }
    }
}

/**
 * دخول + Firestore **بقواعد المشروع الحقيقية** (`emulator-auth/` — منافذ 9099 و8089، مشروع `demo-masroufy-auth`).
 * كل [device] تطبيق فايربيز لوحده بدخوله ونسخته المحلية.
 */
object AuthEmulator {
    const val PROJECT = "demo-masroufy-auth"
    private val apps = mutableMapOf<String, FirebaseApp>()

    private fun app(device: String): FirebaseApp = apps.getOrPut(device) {
        Firebase.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FirebaseOptions(applicationId = "1:000000000000:android:0000000000000001", apiKey = "emulator-only", projectId = PROJECT),
            "auth-$device",
        ).also { created ->
            Firebase.auth(created).useEmulator("10.0.2.2", 9099)
            Firebase.firestore(created).useEmulator("10.0.2.2", 8089)
        }
    }

    fun auth(device: String): FirebaseAuth = Firebase.auth(app(device))

    fun firestore(device: String): FirebaseFirestore = Firebase.firestore(app(device))
}
