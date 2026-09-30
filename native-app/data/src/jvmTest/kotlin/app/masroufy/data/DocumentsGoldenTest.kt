package app.masroufy.data

import app.masroufy.core.Golden
import app.masroufy.core.field
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * المحوّلات قدام **اللي اتخزن فعلًا** في فايربيز (`documents.json`): كل كيان اتحفظ بمستودع التطبيق الحالي
 * على Firestore Emulator واتقرا خام بنوع كل قيمة. كوتلن لازم:
 * 1. تقرا الكيان وتكتبه **نفس الحقول بنفس الأنواع** (عدد صحيح مش عشري، `null` مش غياب) بعد قص أرقام الحسابات.
 * 2. تطلّع **نفس معرّف المستند** (إيصالات التنبيه متشفّرة، والميزانية بمفتاح الفترة).
 * 3. تقرا المستند المتخزن وتكتبه تاني **من غير ما يتغير حرف** — عشان التطبيقين يشتغلوا على نفس البيانات.
 */
class DocumentsGoldenTest {
    /** JSON الكيان (زي ما التطبيق الحالي بيبعته) ⇒ شكل محايد: العدد الصحيح `Long` والكسر `Double`. */
    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonObject -> e.mapValues { plain(it.value) }
        is JsonArray -> e.map(::plain)
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            else -> e.content.toDouble()
        }
    }

    /** قيمة Firestore REST بنوعها ⇒ نفس الشكل المحايد. نوع مش متوقع = الاختبار يفشل ويقول النوع. */
    private fun stored(v: JsonElement): Any? {
        val o = v.jsonObject
        return when {
            "nullValue" in o -> null
            "stringValue" in o -> o.getValue("stringValue").jsonPrimitive.content
            "integerValue" in o -> o.getValue("integerValue").jsonPrimitive.content.toLong()
            "doubleValue" in o -> o.getValue("doubleValue").jsonPrimitive.content.toDouble()
            "booleanValue" in o -> o.getValue("booleanValue").jsonPrimitive.content.toBooleanStrict()
            "arrayValue" in o -> (o.getValue("arrayValue").jsonObject["values"] as? JsonArray ?: JsonArray(emptyList())).map(::stored)
            "mapValue" in o -> fields(o.getValue("mapValue").jsonObject["fields"] ?: JsonObject(emptyMap()))
            else -> fail("نوع قيمة مش متوقع في المستند: ${o.keys}")
        }
    }

    private fun fields(e: JsonElement): Doc = e.jsonObject.mapValues { stored(it.value) }

    /** مقارنة بالنوع: `Long(5)` مش زي `Double(5.0)` — وده بالظبط اللي بنختبره. */
    private fun diff(a: Any?, b: Any?, path: String): String? = when {
        a is Map<*, *> && b is Map<*, *> ->
            (a.keys + b.keys).firstNotNullOfOrNull { k -> if (k !in a || k !in b) "$path.$k: موجود في ناحية بس" else diff(a[k], b[k], "$path.$k") }
        a is List<*> && b is List<*> ->
            if (a.size != b.size) "$path: ${a.size} عنصر قدام ${b.size}" else a.indices.firstNotNullOfOrNull { diff(a[it], b[it], "$path[$it]") }
        a == null && b == null -> null
        a != null && b != null && a::class == b::class && a == b -> null
        else -> "$path: كوتلن=${a}(${a?.let { it::class.simpleName }}) المتخزن=${b}(${b?.let { it::class.simpleName }})"
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> check(codec: DocCodec<T>): Int {
        val cases = Golden.cases("documents", codec.group)
        assertTrue(cases.isNotEmpty(), "مفيش حالات لـ${codec.group}")
        for ((i, c) in cases.withIndex()) {
            val where = "${codec.group}[$i]"
            val entity = codec.decode(plain(c.field("in")) as Doc)
            val want = fields(c.field("out").field("fields"))
            diff(codec.toStore(entity), want, where)?.let { fail("الكتابة: $it") }
            assertEquals(c.field("out").field("id").jsonPrimitive.content, codec.id(entity), "$where: معرّف المستند")
            // المتخزن ⇒ كيان ⇒ نفس المتخزن (القراية والكتابة تاني ما بتغيّرش حاجة)
            val again = codec.decode(want)
            diff(codec.toStore(again), want, "$where (قراية ثم كتابة)")?.let { fail(it) }
        }
        return cases.size
    }

    @Test
    fun `كل مجموعات التطبيق الحالي بنفس الشكل المتخزن`() {
        var total = 0
        for (codec in DocumentCodecs.current) total += check(codec)
        assertEquals(24, DocumentCodecs.current.size)
        assertEquals(46, total, "عدد المستندات في documents.json")
    }
}
