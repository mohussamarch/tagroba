package app.masroufy.firestore

import app.masroufy.data.Doc
import app.masroufy.data.DocCodec
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.Query

/**
 * استعلام بيتوصف **مرة واحدة** ويتنفّذ بطريقتين: على [LocalMirror] لو المجموعة اتزامنت، أو على فايربيز.
 * كده الطريقتين ما يختلفوش — والمطابقة متقاسة باختبار بيشغّل نفس المستودعات بالطريقتين.
 *
 * قواعد فايربيز اللي بنقلّدها: الشرط على حقل مش موجود (أو من نوع تاني) ما بيطابقش · من غير ترتيب النتيجة بمعرّف المستند ·
 * مع شرط مدى الترتيب بحقل المدى ثم المعرّف · `orderBy` بيستبعد المستند اللي مفيهوش الحقل.
 * ⚠️ مقارنة النصوص هنا بـUTF-16 وفايربيز بـUTF-8 — نفس النتيجة للحروف اللاتيني والأرقام (التواريخ والمعرّفات)، ودي بس اللي بنعمل عليها مدى.
 */
internal sealed interface Cond {
    val field: String

    data class Eq(override val field: String, val value: Any) : Cond
    data class Contains(override val field: String, val value: Any) : Cond
    data class In(override val field: String, val values: List<Any>) : Cond
    data class AtLeast(override val field: String, val value: String) : Cond
    data class AtMost(override val field: String, val value: String) : Cond
    data class After(override val field: String, val value: String) : Cond
}

internal data class DocQuery(val conds: List<Cond> = emptyList(), val descendingBy: String? = null, val limit: Int? = null) {
    private val rangeField: String? = conds.firstOrNull { it is Cond.AtLeast || it is Cond.AtMost || it is Cond.After }?.field

    fun matches(doc: Doc): Boolean = conds.all { c ->
        val v = doc[c.field]
        when (c) {
            is Cond.Eq -> v == c.value
            is Cond.Contains -> v is List<*> && c.value in v
            is Cond.In -> v != null && v in c.values
            is Cond.AtLeast -> v is String && v >= c.value
            is Cond.AtMost -> v is String && v <= c.value
            is Cond.After -> v is String && v > c.value
        }
    } && (descendingBy == null || doc[descendingBy] != null)

    /** التنفيذ في الذاكرة — نفس ترتيب فايربيز ونفس الحد. */
    fun runOn(docs: Map<String, LocalMirror.Entry>): List<LocalMirror.Entry> {
        val hits = docs.entries.filter { matches(it.value.doc) }
        val ordered = when {
            descendingBy != null -> hits.sortedWith(compareByDescending<Map.Entry<String, LocalMirror.Entry>> { it.value.doc[descendingBy] as? Comparable<Any> }.thenByDescending { it.key })
            rangeField != null -> hits.sortedWith(compareBy<Map.Entry<String, LocalMirror.Entry>> { it.value.doc[rangeField] as String }.thenBy { it.key })
            else -> hits.sortedBy { it.key }
        }
        return ordered.let { if (limit != null) it.take(limit) else it }.map { it.value }
    }

    /** نفس الاستعلام على فايربيز. */
    fun applyTo(base: Query): Query {
        var q = base
        for (c in conds) q = when (c) {
            is Cond.Eq -> q.where { c.field equalTo c.value }
            is Cond.Contains -> q.where { c.field contains c.value }
            is Cond.In -> q.where { c.field inArray c.values }
            is Cond.AtLeast -> q.where { c.field greaterThanOrEqualTo c.value }
            is Cond.AtMost -> q.where { c.field lessThanOrEqualTo c.value }
            is Cond.After -> q.where { c.field greaterThan c.value }
        }
        if (descendingBy != null) q = q.orderBy(descendingBy, Direction.DESCENDING)
        if (limit != null) q = q.limit(limit.toLong())
        return q
    }
}

/** القراية الوحيدة للمستودعات: من الذاكرة لو المجموعة اتزامنت، وإلا من فايربيز (السيرفر لو فيه نت، والنسخة المحلية لو مفيش). */
internal suspend fun <T> FirestoreSpace.select(codec: DocCodec<T>, query: DocQuery = DocQuery()): List<T> {
    val local = mirror?.docsOf(codec.group)
    if (local != null) return query.runOn(local).map { it.entity(codec::decode) }
    return codec.decodeAll(query.applyTo(collection(codec.group)).get())
}
