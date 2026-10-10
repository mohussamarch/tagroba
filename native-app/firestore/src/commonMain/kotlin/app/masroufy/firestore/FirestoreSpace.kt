package app.masroufy.firestore

import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.SPACES_GROUP
import app.masroufy.data.Doc
import app.masroufy.data.DocCodec
import app.masroufy.data.omittedFields
import app.masroufy.data.toStore
import app.masroufy.perf.PerfTrace
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * مكان بيانات في فايربيز: الحساب `users/{uid}`، والبلد السعودية `users/{uid}` نفسه، والبلاد التانية `users/{uid}/spaces/{spaceId}`
 * (OVERRIDES §41 · §64). المستودعات بتاخد المكان ده، فحالات الاستخدام ما تعرفش عنه حاجة.
 */
class FirestoreSpace(val db: FirebaseFirestore, val root: String) {
    fun collection(group: String): CollectionReference = db.collection("$root/$group")

    /**
     * النسخة اللي في الذاكرة ([LocalMirror]) لما [FirestoreSync] شغال — المستودعات بتقرا منها للمجموعات اللي اتزامنت،
     * وكتابة التطبيق بتتطبّق عليها لحظتها. `null` = كل قراية من فايربيز.
     */
    var mirror: LocalMirror? = null

    /**
     * الكتابة من غير ما نستنى السيرفر (وضع «بيشتغل من غير نت» — §54). `null` = استنى تأكيد السيرفر (الاختبارات والوضع العادي).
     * مكتبة فايربيز بتحط الكتابة في النسخة المحلية **لحظة إرسالها** وبتحتفظ بيها لحد ما النت يرجع — دي الـ«outbox» بتاعتها؛
     * فمن غير نت `commit()` ما بيخلصش أبدًا، والشاشة كانت هتعلّق لو استنته.
     */
    var writeScope: CoroutineScope? = null

    private val failures = MutableSharedFlow<Throwable>(extraBufferCapacity = 16)

    /** كتابة رفضها السيرفر بعد ما رجعنا للشاشة (صلاحيات مثلًا) — الشاشة لازم تعرضها، ما تتبلعش. */
    val writeFailures: SharedFlow<Throwable> = failures.asSharedFlow()

    /**
     * تنفيذ كتابة: لو [writeScope] متحدد، الكتابة **بتبدأ فورًا** (`UNDISPATCHED` — النداء الأصلي بيحصل قبل ما نرجع، فأي قراية بعدها
     * من النسخة المحلية بتشوفها) ومن غير ما نستنى السيرفر.
     */
    internal suspend fun write(block: suspend () -> Unit) {
        val scope = writeScope ?: return block()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failures.tryEmit(e)
            }
        }
    }

    companion object {
        fun forUser(db: FirebaseFirestore, uid: String) = FirestoreSpace(db, "users/$uid")

        /** مكان بيانات الحساب المشتركة (الملف · الأشخاص · التجار · الوسوم · المناسبات · سجل البلاد · التحويل لنفسك) — §64. */
        fun forAccount(db: FirebaseFirestore, uid: String) = FirestoreSpace(db, "users/$uid")

        /**
         * مكان بيانات بلد: السعودية = `users/{uid}` نفسه (بيانات التطبيق الحالي في مكانها، من غير نقل)، والباقي `users/{uid}/spaces/{id}`.
         * ⚠️ حتى لو المكان هو هو، ده **كائن تاني** غير [forAccount] — لكل واحد نسخته في الذاكرة ومستمعينه.
         */
        fun forSpace(db: FirebaseFirestore, uid: String, spaceId: String) =
            FirestoreSpace(db, if (spaceId == DEFAULT_SPACE_ID) "users/$uid" else "users/$uid/$SPACES_GROUP/$spaceId")
    }
}

/** حد فايربيز: 500 عملية في الدفعة الواحدة. */
internal const val BATCH_LIMIT = 500

/** حد فايربيز لاستعلام `in`: 30 قيمة. */
internal const val IN_LIMIT = 30

/**
 * اللي بيتكتب لما نحفظ كيان كامل بـ**merge** (ARCHITECTURE §31.4): الحقول المعروفة بقيمها + **مسح صريح**
 * للحقول الاختيارية الفاضية. كده حقل التطبيق الحالي بيضيفه بعدين (ومش عندنا) ما يتمسحش، والحقل اللي المستخدم فضّاه ما يفضلش بقيمته القديمة.
 */
internal fun <T> DocCodec<T>.mergeForm(value: T): Doc {
    val stored = toStore(value)
    val deletes = omittedFields(value).associateWith { FieldValue.delete }
    return stored + deletes
}

internal fun <T> DocCodec<T>.decodeAll(snapshot: QuerySnapshot): List<T> = snapshot.documents.mapNotNull { it.rawData() }.map(::decode)

/** حفظ كيانات بدفعات 500 بـmerge — نفس `writeBatch` في التطبيق الحالي. */
internal suspend fun <T> FirestoreSpace.saveAll(codec: DocCodec<T>, items: List<T>) {
    for (chunk in items.chunked(BATCH_LIMIT)) {
        val batch = db.batch()
        val forms = chunk.map { codec.id(it) to codec.mergeForm(it) }
        for ((id, form) in forms) batch.set(collection(codec.group).document(id), form, merge = true)
        write { batch.commit() }
        mirror?.let { m -> forms.forEach { (id, form) -> m.applySet(codec.group, id, form) } }
    }
}

internal suspend fun FirestoreSpace.deleteAll(group: String, ids: List<String>) {
    for (chunk in ids.chunked(BATCH_LIMIT)) {
        val batch = db.batch()
        for (id in chunk) batch.delete(collection(group).document(id))
        write { batch.commit() }
        mirror?.applyDelete(group, chunk)
    }
}

/** مستند واحد بمعرّفه — من الذاكرة لو المجموعة اتزامنت، وإلا من فايربيز. */
internal suspend fun FirestoreSpace.readDoc(group: String, id: String): Doc? {
    mirror?.awaitDocs(group, MIRROR_WAIT_MS)?.let { return it[id]?.doc }
    val start = PerfTrace.mark()
    val snap = collection(group).document(id).get()
    return snap.rawData().also { if (PerfTrace.enabled) ReadMeter.server("$group/doc", listOf(it), snap.metadata.isFromCache, start) }
}

/** تعديل حقول بعينها في مستند موجود (`update()`) — ويتطبّق على الذاكرة لحظتها. */
internal suspend fun FirestoreSpace.updateDoc(group: String, id: String, fields: Map<String, Any?>) {
    write { collection(group).document(id).update(*fields.map { (k, v) -> k to v }.toTypedArray()) }
    mirror?.applyUpdate(group, id, fields)
}

/**
 * قراية بـ`in` على حقل بحد 30 قيمة في الاستعلام — والقايمة الفاضية ما بتعملش استعلام.
 * من الذاكرة كمان بدفعات 30: النتيجة **بنفس ترتيب فايربيز** (كل دفعة مترتبة لوحدها) — `MirrorParityTest` مسك الفرق ده.
 */
internal suspend fun <T> FirestoreSpace.findIn(codec: DocCodec<T>, field: String, values: List<String>): List<T> {
    val out = mutableListOf<T>()
    for (chunk in values.chunked(IN_LIMIT)) out += select(codec, DocQuery(listOf(Cond.In(field, chunk))))
    return out
}
