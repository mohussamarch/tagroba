package app.masroufy.usecase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * `JSON.parse` لنص ملف — لـ`Map`/`List`/نص/رقم/منطقي/null بنفس معاني جافاسكربت: العدد الصحيح `Long`
 * (لو في المدى) وغير كده `Double`. ده الشكل اللي `core` بيفهمه، فـ`core` ما بيعرفش حاجة عن المكتبة.
 */
internal object JsonText {
    class InvalidJson : IllegalArgumentException("invalid JSON")

    fun parse(raw: String): Any? {
        val element = try {
            Json.parseToJsonElement(raw)
        } catch (_: Exception) {
            throw InvalidJson()
        }
        return plain(element)
    }

    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonArray -> e.map(::plain)
        is JsonObject -> LinkedHashMap<String, Any?>().apply { for ((k, v) in e) put(k, plain(v)) }
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            else -> e.doubleOrNull ?: throw InvalidJson()
        }
    }
}
