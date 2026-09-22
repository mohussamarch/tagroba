package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Direction
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.Halalas
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.Merchant
import app.masroufy.core.ParsedRow
import app.masroufy.core.RowError
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.SchemaId
import app.masroufy.core.SourceRecord
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** تدفق الاستيراد (معاينة → التزام) على `importFlow.json` — الحقول الفاضية بتتكتب null صراحةً زي جافاسكربت. */
class ImportFlowGoldenTest {
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.optStr(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun JsonElement.optLong(name: String): Halalas? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.toLong()

    private fun JsonElement.optInt(name: String): Int? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.int

    private fun merchant(e: JsonElement) = Merchant(
        id = e.field("id").str, displayName = e.field("displayName").str, normalizedName = e.field("normalizedName").str,
        aliases = e.jsonObject["aliases"]?.takeIf { it !is JsonNull }?.jsonArray?.map { it.str },
        verifiedCategoryId = e.optStr("verifiedCategoryId"),
    )

    private fun category(e: JsonElement) = Category(
        id = e.field("id").str, parentId = e.optStr("parentId"), name = e.field("name").str,
        iconKey = e.field("iconKey").str, lightColor = e.field("lightColor").str, darkColor = e.field("darkColor").str,
        active = e.field("active").jsonPrimitive.boolean, order = e.field("order").jsonPrimitive.int,
        groupKey = e.optStr("groupKey"), requires = e.optStr("requires"),
    )

    private fun rule(e: JsonElement) = ClassificationRule(
        id = e.field("id").str, priority = e.field("priority").jsonPrimitive.int, matchText = e.field("matchText").str,
        matchMode = RuleMatchMode.fromWire(e.field("matchMode").str), categoryId = e.field("categoryId").str,
        enabled = e.field("enabled").jsonPrimitive.boolean,
    )

    private fun sourceRecord(e: JsonElement) = SourceRecord(
        id = e.field("id").str, batchId = e.field("batchId").str, accountIdentity = e.field("accountIdentity").str,
        sourceReference = e.optStr("sourceReference"), sourceHash = e.field("sourceHash").str,
        originalRowIndex = e.field("originalRowIndex").jsonPrimitive.int, rawLine = e.field("rawLine").str,
        transactionId = e.optStr("transactionId"), matchingState = MatchingState.fromWire(e.field("matchingState").str),
        reason = e.field("reason").str,
    )

    private fun batch(e: JsonElement): ImportBatch {
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

    private fun parsedRow(e: JsonElement) = ParsedRow(
        lineNumber = e.field("lineNumber").jsonPrimitive.int, date = e.field("date").str,
        amountMinor = e.field("amountMinor").jsonPrimitive.content.toLong(), direction = Direction.fromWire(e.field("direction").str),
        merchantName = e.field("merchantName").str, reference = e.optStr("reference"), sourceName = e.field("sourceName").str,
        description = e.field("description").str, raw = e.field("raw").str,
        sourceCategory = e.optStr("sourceCategory"), sourceOperationType = e.optStr("sourceOperationType"),
        statedBalanceMinor = e.optLong("statedBalanceMinor"),
    )

    private fun batchJson(b: ImportBatch) = JsonObject(
        mapOf(
            "id" to json(b.id), "sourceType" to json(b.sourceType.wire), "fileHash" to json(b.fileHash),
            "fileName" to json(b.fileName), "importedAt" to json(b.importedAt), "state" to json(b.state.wire),
            "counts" to JsonObject(
                mapOf(
                    "total" to json(b.counts.total), "imported" to json(b.counts.imported), "duplicates" to json(b.counts.duplicates),
                    "similar" to json(b.counts.similar), "conflicts" to json(b.counts.conflicts), "invalid" to json(b.counts.invalid),
                ),
            ),
        ),
    )

    private fun recordJson(r: SourceRecord) = JsonObject(
        mapOf(
            "id" to json(r.id), "batchId" to json(r.batchId), "accountIdentity" to json(r.accountIdentity),
            "sourceReference" to nullable(r.sourceReference), "sourceHash" to json(r.sourceHash),
            "originalRowIndex" to json(r.originalRowIndex), "rawLine" to json(r.rawLine),
            "transactionId" to nullable(r.transactionId), "matchingState" to json(r.matchingState.wire), "reason" to json(r.reason),
        ),
    )

    private fun errorJson(e: RowError) = JsonObject(
        mapOf("lineNumber" to json(e.lineNumber), "field" to json(e.field), "message" to json(e.message), "raw" to json(e.raw)),
    )

    private fun previewJson(p: ImportPreview) = JsonObject(
        mapOf(
            "fileName" to json(p.fileName), "fileHash" to json(p.fileHash), "schema" to json(p.schema.wire),
            "accountIdentity" to json(p.accountIdentity),
            "previousBatch" to (p.previousBatch?.let(::batchJson) ?: JsonNull),
            "lines" to JsonArray(
                p.lines.map { l ->
                    JsonObject(
                        mapOf(
                            "lineNumber" to json(l.row.lineNumber), "date" to json(l.row.date), "amountMinor" to json(l.row.amountMinor),
                            "direction" to json(l.row.direction.wire), "merchantName" to json(l.row.merchantName),
                            "reference" to nullable(l.row.reference), "statedBalanceMinor" to nullable(l.row.statedBalanceMinor),
                            "state" to json(l.state.wire), "reason" to json(l.reason),
                            "matchedTransactionId" to nullable(l.matchedTransactionId),
                            "categoryId" to nullable(l.categoryId), "categoryReason" to json(l.categoryReason),
                            "categorySource" to nullable(l.categorySource?.wire),
                            "selectedByDefault" to json(l.selectedByDefault),
                        ),
                    )
                },
            ),
            "errors" to JsonArray(p.errors.map(::errorJson)),
            "counts" to JsonObject(
                mapOf(
                    "total" to json(p.counts.total), "newCount" to json(p.counts.newCount), "duplicates" to json(p.counts.duplicates),
                    "similar" to json(p.counts.similar), "conflicts" to json(p.counts.conflicts), "invalid" to json(p.counts.invalid),
                ),
            ),
            "impact" to JsonObject(
                mapOf(
                    "walletDeltaMinor" to json(p.impact.walletDeltaMinor), "expenseMinor" to json(p.impact.expenseMinor),
                    "incomeMinor" to json(p.impact.incomeMinor),
                ),
            ),
        ),
    )

    @Test
    fun importFlow() {
        Golden.check("importFlow", "importFlow") { input ->
            val seed = input.field("seed")
            val references = input.field("references")
            val requestJson = input.field("request")
            val action = input.field("action")

            val txns = MemoryTransactionRepository(EntityJson.transactions(seed.field("transactions")))
            val sources = MemorySourceRecordRepository(seed.field("sourceRecords").jsonArray.map(::sourceRecord))
            val batches = MemoryImportBatchRepository(seed.field("batches").jsonArray.map(::batch))
            val deps = ImportStatementDeps(
                txns = txns, sources = sources, batches = batches,
                merchants = MemoryMerchantRepository(references.field("merchants").jsonArray.map(::merchant)),
                categories = MemoryCategoryRepository(references.field("categories").jsonArray.map(::category)),
                rules = MemoryRuleRepository(references.field("rules").jsonArray.map(::rule)),
                uow = MemoryUnitOfWork(listOf(txns, sources, batches)),
                ids = SequentialIdGenerator(),
                clock = FixedClock("2026-09-22T10:00:00.000Z"),
            )
            val request = ImportRequest(
                fileName = requestJson.field("fileName").str,
                content = requestJson.field("content").str,
                accountIdentity = requestJson.field("accountIdentity").str,
                sourceType = ImportSourceType.fromWire(requestJson.field("sourceType").str),
                walletId = requestJson.optStr("walletId"),
                schema = requestJson.optStr("schema")?.let(SchemaId::fromWire),
                parsedRows = requestJson.jsonObject["parsedRows"]?.takeIf { it !is JsonNull }?.jsonArray?.map(::parsedRow),
            )

            val importer = ImportStatement(deps)
            runBlocking {
                val preview = importer.preview(request)
                if (action.field("kind").str == "preview") {
                    JsonObject(mapOf("preview" to previewJson(preview)))
                } else {
                    val selected = action.jsonObject["selected"]?.takeIf { it !is JsonNull }?.jsonArray?.map { it.jsonPrimitive.int }
                    val chosen = action.jsonObject["chosenCategories"]?.takeIf { it !is JsonNull }?.jsonObject
                        ?.entries?.associate { (k, v) -> k.toInt() to v.jsonPrimitive.content }
                    val commitRequest = action.optStr("commitContent")?.let { request.copy(content = it) } ?: request
                    val batch = importer.commit(commitRequest, preview, selected, chosen)
                    JsonObject(
                        mapOf(
                            "batch" to batchJson(batch),
                            "storedBatches" to JsonArray(batches.all().map(::batchJson)),
                            "storedTransactions" to JsonArray(txns.all().map { EntityJson.transactionJson(it) }),
                            "storedSourceRecords" to JsonArray(sources.all().map(::recordJson)),
                        ),
                    )
                }
            }
        }
    }
}
