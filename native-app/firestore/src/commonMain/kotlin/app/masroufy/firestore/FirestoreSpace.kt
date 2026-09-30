package app.masroufy.firestore

import app.masroufy.data.Doc
import app.masroufy.data.DocCodec
import app.masroufy.data.omittedFields
import app.masroufy.data.toStore
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot

/**
 * مكان بيانات الحساب في فايربيز: `users/{uid}` النهارده، و`users/{uid}/spaces/{spaceId}` لما البلاد تتبني (OVERRIDES §41).
 * المستودعات بتاخد المكان ده، فحالات الاستخدام ما تعرفش عنه حاجة.
 */
class FirestoreSpace(val db: FirebaseFirestore, val root: String) {
    fun collection(group: String): CollectionReference = db.collection("$root/$group")

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
