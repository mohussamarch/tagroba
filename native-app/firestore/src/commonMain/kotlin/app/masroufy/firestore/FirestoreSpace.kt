package app.masroufy.firestore

import app.masroufy.data.Doc
import app.masroufy.data.DocCodec
import app.masroufy.data.omittedFields
import app.masroufy.data.toStore
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.QuerySnapshot
import dev.gitlive.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * مكان بيانات الحساب في فايربيز: `users/{uid}` النهارده، و`users/{uid}/spaces/{spaceId}` لما البلاد تتبني (OVERRIDES §41).
 * المستودعات بتاخد المكان ده، فحالات الاستخدام ما تعرفش عنه حاجة.
 */
class FirestoreSpace(val db: FirebaseFirestore, val root: String) {
    fun collection(group: String): CollectionReference = db.collection("$root/$group")

    /**
     * منين المستودعات تقرا المجموعة: النسخة المحلية (`CACHE`) لما [FirestoreSync] يقول إنها كاملة، وإلا العادي
     * (`DEFAULT` = السيرفر لو فيه نت، والنسخة المحلية لو مفيش). من غير مزامنة شغالة: العادي دايمًا.
     */
    var sourceFor: (group: String) -> Source = { Source.DEFAULT }

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
        for (item in chunk) batch.set(collection(codec.group).document(codec.id(item)), codec.mergeForm(item), merge = true)
        write { batch.commit() }
    }
}

internal suspend fun FirestoreSpace.deleteAll(group: String, ids: List<String>) {
    for (chunk in ids.chunked(BATCH_LIMIT)) {
        val batch = db.batch()
        for (id in chunk) batch.delete(collection(group).document(id))
        write { batch.commit() }
    }
}

/**
 * مستند واحد بمعرّفه. ⚠️ من النسخة المحلية، المستند **مش موجود** بيرمي «UNAVAILABLE» بدل ما يرجع فاضي — ولما المجموعة
 * متزامنة كاملة ([FirestoreSync]) ده معناه إنه مش موجود فعلًا ⇒ `null`. أي خطأ تاني بيطلع زي ما هو.
 */
internal suspend fun FirestoreSpace.readDoc(group: String, id: String): Doc? {
    val source = sourceFor(group)
    return try {
        collection(group).document(id).get(source).rawData()
    } catch (e: FirebaseFirestoreException) {
        if (source == Source.CACHE && e.code == FirestoreExceptionCode.UNAVAILABLE) null else throw e
    }
}

/** قراية بـ`in` على حقل بحد 30 قيمة في الاستعلام — والقايمة الفاضية ما بتعملش استعلام. */
internal suspend fun <T> FirestoreSpace.findIn(codec: DocCodec<T>, field: String, values: List<String>): List<T> {
    val out = mutableListOf<T>()
    for (chunk in values.chunked(IN_LIMIT)) out += codec.decodeAll(collection(codec.group).where { field inArray chunk }.get(sourceFor(codec.group)))
    return out
}
