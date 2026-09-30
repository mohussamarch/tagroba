package app.masroufy.firestore

import androidx.test.platform.app.InstrumentationRegistry
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.FirebaseOptions
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
    private var app: FirebaseApp? = null

    fun firestore(): FirebaseFirestore {
        app?.let { return Firebase.firestore(it) }
        val created = Firebase.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FirebaseOptions(applicationId = "1:000000000000:android:0000000000000000", apiKey = "emulator-only", projectId = PROJECT),
            "masroufy-kt-test",
        )
        app = created
        return Firebase.firestore(created).also { it.useEmulator(HOST, PORT) }
    }
}
