package app.masroufy.trial

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/** مكتبة جوجل الرسمية مباشرة. على الآيفون ده معناه كود تاني بـSwift لنفس الشغل. */
fun createReader(context: Context): TrialReader = DirectReader(context)

private class DirectReader(context: Context) : TrialReader {
    override val name = "direct"
    private val db: FirebaseFirestore

    init {
        val options = FirebaseOptions.Builder().setProjectId(TRIAL_PROJECT).setApplicationId(TRIAL_APP_ID).setApiKey(TRIAL_API_KEY).build()
        val app = FirebaseApp.initializeApp(context, options)
        db = FirebaseFirestore.getInstance(app)
        db.useEmulator(EMULATOR_HOST, EMULATOR_PORT)
    }

    override suspend fun clearCache() {
        db.clearPersistence().await()
    }

    override suspend fun read(from: String?, to: String?, source: TrialSource): List<TxnDoc> {
        var q: Query = db.collection(TRIAL_COLLECTION)
        if (from != null) q = q.whereGreaterThanOrEqualTo("occurredAt", from)
        if (to != null) q = q.whereLessThanOrEqualTo("occurredAt", to)
        val src = when (source) {
            TrialSource.SERVER -> Source.SERVER
            TrialSource.CACHE -> Source.CACHE
            TrialSource.DEFAULT -> Source.DEFAULT
        }
        // التحويل بالإيد حقل حقل — ده الشغل اللي GitLive بتعمله لوحدها بـkotlinx.serialization
        return q.get(src).await().documents.map { it.toTxn() }
    }

    override suspend fun setOnline(online: Boolean) {
        if (online) db.enableNetwork().await() else db.disableNetwork().await()
    }
}

private fun DocumentSnapshot.toTxn() = TxnDoc(
    id = id,
    occurredAt = getString("occurredAt") ?: "",
    sourceOrder = getLong("sourceOrder") ?: 0,
    economicKind = getString("economicKind") ?: "",
    observedDirection = getString("observedDirection") ?: "",
    amountMinor = getLong("amountMinor") ?: 0,
    currency = getString("currency") ?: "SAR",
    rawMerchantName = getString("rawMerchantName"),
    categoryId = getString("categoryId"),
    walletId = getString("walletId"),
    reviewState = getString("reviewState") ?: "",
    createdAt = getString("createdAt") ?: "",
    updatedAt = getString("updatedAt") ?: "",
)
