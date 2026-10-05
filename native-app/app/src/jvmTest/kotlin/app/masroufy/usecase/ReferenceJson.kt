package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.EntityJson
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.Merchant
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.SourceRecord
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** قراية المراجع (تصنيفات · تجار · قواعد · سجلات مصدر · دفعات) من ملفات المرجع — مشتركة بين اختبارات الاستخدامات. */
internal object ReferenceJson {
    private fun JsonElement.optStr(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    fun category(e: JsonElement) = Category(
        id = e.field("id").str, parentId = e.optStr("parentId"), name = e.field("name").str,
        iconKey = e.field("iconKey").str, lightColor = e.field("lightColor").str, darkColor = e.field("darkColor").str,
        active = e.field("active").jsonPrimitive.boolean, order = e.field("order").jsonPrimitive.int,
        groupKey = e.optStr("groupKey"), requires = e.optStr("requires"),
        noCarName = e.optStr("noCarName"), noCarIconKey = e.optStr("noCarIconKey"),
    )

    fun merchant(e: JsonElement) = Merchant(
        id = e.field("id").str, displayName = e.field("displayName").str, normalizedName = e.field("normalizedName").str,
        aliases = e.jsonObject["aliases"]?.takeIf { it !is JsonNull }?.jsonArray?.map { it.str },
        verifiedCategoryId = e.optStr("verifiedCategoryId"),
    )

    fun merchantJson(m: Merchant) = EntityJson.obj(
        "id" to m.id, "displayName" to m.displayName, "normalizedName" to m.normalizedName,
        "aliases" to m.aliases?.let { list -> JsonArray(list.map(::json)) },
        "logoAsset" to m.logoAsset, "logoSource" to m.logoSource, "verifiedCategoryId" to m.verifiedCategoryId,
    )

    fun rule(e: JsonElement) = ClassificationRule(
        id = e.field("id").str, priority = e.field("priority").jsonPrimitive.int, matchText = e.field("matchText").str,
        matchMode = RuleMatchMode.fromWire(e.field("matchMode").str), categoryId = e.field("categoryId").str,
        enabled = e.field("enabled").jsonPrimitive.boolean,
    )

    fun sourceRecord(e: JsonElement) = SourceRecord(
        id = e.field("id").str, batchId = e.field("batchId").str, accountIdentity = e.field("accountIdentity").str,
        sourceReference = e.optStr("sourceReference"), sourceHash = e.field("sourceHash").str,
        originalRowIndex = e.field("originalRowIndex").jsonPrimitive.int, rawLine = e.field("rawLine").str,
        transactionId = e.optStr("transactionId"), matchingState = MatchingState.fromWire(e.field("matchingState").str),
        reason = e.field("reason").str,
    )

    fun batch(e: JsonElement): ImportBatch {
        val c = e.field("counts")
        return ImportBatch(
            id = e.field("id").str, sourceType = ImportSourceType.fromWire(e.field("sourceType").str),
            fileHash = e.field("fileHash").str, fileName = e.field("fileName").str, importedAt = e.field("importedAt").str,
            state = ImportBatchState.fromWire(e.field("state").str),
            counts = ImportCounts(
                total = c.field("total").jsonPrimitive.int, imported = c.field("imported").jsonPrimitive.int,
                duplicates = c.field("duplicates").jsonPrimitive.int, similar = c.field("similar").jsonPrimitive.int,
                conflicts = c.field("conflicts").jsonPrimitive.int, invalid = c.field("invalid").jsonPrimitive.int,
            ),
        )
    }
}
