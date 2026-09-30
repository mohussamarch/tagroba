package app.masroufy.trial

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize

/**
 * GitLive — **ملف واحد في الكود المشترك** بيتبني لأندرويد والآيفون. [context] = `Context` على أندرويد و`null` على الآيفون.
 * التحويل من المستند لـ`TxnDoc` بـkotlinx.serialization لوحده (قارن بـ`DirectReader.toTxn` اللي بالإيد).
 */
class GitLiveReader(context: Any?) : TrialReader {
    override val name = "gitlive"
    private val db: FirebaseFirestore

    init {
        val app = Firebase.initialize(context, FirebaseOptions(applicationId = TRIAL_APP_ID, apiKey = TRIAL_API_KEY, projectId = TRIAL_PROJECT))
        db = Firebase.firestore(app)
        db.useEmulator(EMULATOR_HOST, EMULATOR_PORT)
    }

    override suspend fun clearCache() = db.clearPersistence()

    override suspend fun read(from: String?, to: String?, source: TrialSource): List<TxnDoc> {
        var q: Query = db.collection(TRIAL_COLLECTION)
        if (from != null) q = q.where { "occurredAt" greaterThanOrEqualTo from }
        if (to != null) q = q.where { "occurredAt" lessThanOrEqualTo to }
        val src = when (source) {
            TrialSource.SERVER -> Source.SERVER
            TrialSource.CACHE -> Source.CACHE
            TrialSource.DEFAULT -> Source.DEFAULT
        }
        return q.get(src).documents.map { it.data<TxnDoc>().copy(id = it.id) }
    }

    override suspend fun setOnline(online: Boolean) {
        if (online) db.enableNetwork() else db.disableNetwork()
    }
}
