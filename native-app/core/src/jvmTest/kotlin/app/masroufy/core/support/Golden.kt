package app.masroufy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * قراية ملفات المرجع (فولدر `native-app/golden`) اللي بيولدها `npm run golden` من التطبيق الحالي.
 * كل حالة لازم تطلع نفس الناتج أو نفس رسالة الخطأ بالحرف.
 */
object Golden {
    private val dir = File(System.getProperty("golden.dir") ?: "golden")
    private val cache = mutableMapOf<String, JsonObject>()

    fun cases(module: String, function: String): List<JsonObject> {
        val root = cache.getOrPut(module) { Json.parseToJsonElement(File(dir, "$module.json").readText()).jsonObject }
        val list = root[function] ?: fail("مفيش «$function» في $module.json")
        return list.jsonArray.map { it.jsonObject }
    }

    /** بيشغّل كل حالات الدالة، وبيرجّع عددها عشان الاختبار يتأكد إنه ما عداش فاضي. */
    fun check(module: String, function: String, run: (input: JsonElement) -> JsonElement): Int {
        val list = cases(module, function)
        assertTrue(list.isNotEmpty(), "$module.$function فاضية")
        for (case in list) {
            val input = case["in"] ?: JsonNull
            val expectedError = case["error"]?.jsonPrimitive?.content
            val result = runCatching { run(input) }
            if (expectedError != null) {
                val error = result.exceptionOrNull() ?: fail("$function($input): كان المفروض خطأ «$expectedError» وطلع ${result.getOrNull()}")
                assertEquals(expectedError, error.message, "$function($input)")
            } else {
                val actual = result.getOrElse { fail("$function(${short(input)}): خطأ مش متوقع «${it.message}»") }
                val diff = firstDiff(case["out"] ?: JsonNull, actual, "")
                if (diff != null) fail("$function(${short(input)}): أول فرق عند $diff")
            }
        }
        return list.size
    }

    private fun short(e: JsonElement) = e.toString().let { if (it.length > 300) it.take(300) + "…" else it }

    /** مكان أول فرق والقيمتين هناك — عشان الفشل يبان على طول بدل ما يطبع الناتج كله. */
    fun firstDiff(a: JsonElement, b: JsonElement, path: String): String? {
        if (a is JsonArray && b is JsonArray) {
            for (i in 0 until minOf(a.size, b.size)) firstDiff(a[i], b[i], "$path[$i]")?.let { return it }
            return if (a.size != b.size) "$path: الطول المتوقع ${a.size} والناتج ${b.size}" else null
        }
        if (a is JsonObject && b is JsonObject) {
            if (a.keys != b.keys) return "$path: المفاتيح المتوقعة ${a.keys} والناتجة ${b.keys}"
            for (k in a.keys) firstDiff(a[k]!!, b[k]!!, "$path.$k")?.let { return it }
            return null
        }
        return if (same(a, b)) null else "$path: المتوقع ${short(a)} والناتج ${short(b)}"
    }

    /** الأرقام بتتقارن بقيمتها (0 من جافاسكربت = 0.0 هنا)، والباقي بالحرف. */
    fun same(a: JsonElement, b: JsonElement): Boolean = when {
        a is JsonNull || b is JsonNull -> a is JsonNull && b is JsonNull
        a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { same(a[it], b[it]) }
        a is JsonObject && b is JsonObject -> a.keys == b.keys && a.keys.all { same(a[it]!!, b[it]!!) }
        a is JsonPrimitive && b is JsonPrimitive -> when {
            a.isString || b.isString -> a.isString == b.isString && a.content == b.content
            a.booleanOrNull != null || b.booleanOrNull != null -> a.booleanOrNull == b.booleanOrNull
            else -> a.content.toBigDecimal().compareTo(b.content.toBigDecimal()) == 0
        }
        else -> false
    }
}

val JsonElement.str: String get() = jsonPrimitive.content
val JsonElement.num: Long get() = jsonPrimitive.long
fun JsonElement.field(name: String): JsonElement = jsonObject[name] ?: JsonNull
fun json(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map(::json))
    else -> error("نوع مش مدعوم في المقارنة: ${value::class}")
}
