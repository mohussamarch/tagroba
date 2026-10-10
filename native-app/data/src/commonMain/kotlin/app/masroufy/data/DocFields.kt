package app.masroufy.data

import app.masroufy.core.TextKey
import app.masroufy.core.uiText

/**
 * المستند = `Map` قيمه من الأنواع اللي فايربيز بيخزنها بس: نص · `Long` (عدد صحيح) · `Double` · `Boolean` · قايمة · `Map` · `null`.
 * ده الشكل المحايد اللي المحوّلات بتشتغل عليه، ومكتبة فايربيز (GitLive) بتتوصل بيه في طبقة لوحدها.
 *
 * ⚠️ **قاعدتين من اللي اتخزن فعلًا** (ملف المرجع `documents.json` — اتقرا من Firestore Emulator بعد الحفظ بكود التطبيق الحالي):
 * 1. الأعداد كلها **صحيحة** (`integerValue`) — مفيش ولا مبلغ عشري.
 * 2. فيه حقول **بتتخزن `null`** (زي `parentId`) وحقول **ما بتتكتبش خالص** لو فاضية (زي `merchantId`) — والفرق لازم يفضل.
 */
typealias Doc = Map<String, Any?>

class DocumentError(message: String) : IllegalStateException(message)

/** قراية حقول مستند مع رسالة واضحة لو حاجة ناقصة أو نوعها غلط — بدل ما التطبيق يقع من غير سبب. */
class DocReader(private val group: String, private val doc: Doc) {
    private val where: String get() = "$group/${doc["id"] ?: doc["eventKey"] ?: doc["assetId"] ?: "?"}"

    private fun missing(name: String): Nothing = throw DocumentError(uiText(TextKey.DOC_FIELD_MISSING, where, name))

    private fun wrongType(name: String): Nothing = throw DocumentError(uiText(TextKey.DOC_FIELD_TYPE, where, name))

    fun has(name: String): Boolean = doc[name] != null

    /** أسامي الحقول (لخريطة مفاتيحها مش ثابتة — معرّفات رابط الشاشة في رسالة المساعد). */
    fun keys(): Set<String> = doc.keys

    fun str(name: String): String = strOrNull(name) ?: missing(name)

    fun strOrNull(name: String): String? = when (val v = doc[name]) {
        null -> null
        is String -> v
        else -> wrongType(name)
    }

    fun long(name: String): Long = longOrNull(name) ?: missing(name)

    /** العدد الصحيح. `Double` بقيمة صحيحة مقبول (مكتبة قديمة ممكن تكون كتبته كده)، والكسر مرفوض — فلوس بالهللة بس. */
    fun longOrNull(name: String): Long? = when (val v = doc[name]) {
        null -> null
        is Long -> v
        is Int -> v.toLong()
        is Double -> if (v % 1.0 == 0.0 && kotlin.math.abs(v) <= 9_007_199_254_740_991.0) v.toLong() else wrongType(name)
        else -> wrongType(name)
    }

    fun int(name: String): Int = long(name).toInt()

    fun intOrNull(name: String): Int? = longOrNull(name)?.toInt()

    fun bool(name: String): Boolean = when (val v = doc[name]) {
        null -> missing(name)
        is Boolean -> v
        else -> wrongType(name)
    }

    fun boolOrNull(name: String): Boolean? = if (doc[name] == null) null else bool(name)

    fun ints(name: String): List<Int> = when (val v = doc[name]) {
        null -> missing(name)
        is List<*> -> v.map { (it as? Long)?.toInt() ?: (it as? Int) ?: wrongType(name) }
        else -> wrongType(name)
    }

    fun strings(name: String): List<String>? = when (val v = doc[name]) {
        null -> null
        is List<*> -> v.map { it as? String ?: wrongType(name) }
        else -> wrongType(name)
    }

    fun map(name: String): DocReader = when (val v = doc[name]) {
        null -> missing(name)
        is Map<*, *> -> @Suppress("UNCHECKED_CAST") DocReader("$group.$name", v as Doc)
        else -> wrongType(name)
    }

    fun maps(name: String): List<DocReader> = when (val v = doc[name]) {
        null -> emptyList()
        is List<*> -> v.map { @Suppress("UNCHECKED_CAST") DocReader("$group.$name", it as? Doc ?: wrongType(name)) }
        else -> wrongType(name)
    }

    /** قيمة مخزنة باسمها (`wire`) — القيمة المجهولة خطأ واضح مش استثناء من غير رسالة. */
    fun <E> wire(name: String, parse: (String) -> E): E {
        val raw = str(name)
        return runCatching { parse(raw) }.getOrElse { throw DocumentError(uiText(TextKey.DOC_FIELD_VALUE, where, name, raw)) }
    }
}

/**
 * المستند المكتوب + أسماء الحقول الاختيارية اللي **ما اتكتبتش** لأنها فاضية — المستودع بيمسحها صريح
 * لما بيكتب بـ«merge» (ARCHITECTURE §31.4)، عشان الحقل اللي المستخدم فضّاه ما يفضلش بقيمته القديمة.
 */
class WrittenDoc(private val fields: Map<String, Any?>, val omitted: Set<String>) : Map<String, Any?> by fields {
    // ⚠️ مش `LinkedHashMap` بالوراثة: على الآيفون (Kotlin/Native) الكلاس ده final. والمساواة بالمحتوى زي أي `Map`
    override fun equals(other: Any?): Boolean = fields == other

    override fun hashCode(): Int = fields.hashCode()

    override fun toString(): String = fields.toString()
}

/** الحقول الاختيارية الفاضية في مستند اتبنى بـ[doc] (فاضية لو المستند جاي من مكان تاني). */
val Doc.omittedFields: Set<String> get() = (this as? WrittenDoc)?.omitted ?: emptySet()

/** بناء مستند بترتيب الحقول. [opt] = ما يتكتبش لو فاضي، [nul] = يتكتب `null` صريح لو فاضي. */
class DocWriter {
    private val out = LinkedHashMap<String, Any?>()
    private val omitted = LinkedHashSet<String>()

    fun req(name: String, value: Any): DocWriter = apply { out[name] = norm(value) }

    fun opt(name: String, value: Any?): DocWriter = apply { if (value != null) out[name] = norm(value) else omitted += name }

    fun nul(name: String, value: Any?): DocWriter = apply { out[name] = value?.let(::norm) }

    fun build(): Doc = WrittenDoc(out, omitted)

    private fun norm(value: Any): Any = when (value) {
        is Int -> value.toLong()
        else -> value
    }
}

inline fun doc(block: DocWriter.() -> Unit): Doc = DocWriter().apply(block).build()
