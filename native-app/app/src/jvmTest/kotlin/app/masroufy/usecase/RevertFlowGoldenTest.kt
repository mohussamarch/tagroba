package app.masroufy.usecase

import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.SourceRecord
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** التراجع عن دفعة + تنظيف المعلّق على `revertFlow.json`. */
class RevertFlowGoldenTest {
    private fun JsonElement.optStr(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

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

    private fun outcomeJson(o: RevertLineOutcome) = JsonObject(
        mapOf("transactionId" to json(o.transactionId), "decision" to json(o.decision.wire), "reason" to json(o.reason)),
    )

    private fun planFields(p: RevertPlan) = mapOf(
        "batchId" to json(p.batchId),
        "toDelete" to json(p.toDelete),
        "toKeep" to JsonArray(p.toKeep.map(::outcomeJson)),
        "outcomes" to JsonArray(p.outcomes.map(::outcomeJson)),
        "isClean" to json(p.isClean),
        "expectedCount" to json(p.expectedCount),
        "recordsFound" to json(p.recordsFound),
        "blocked" to json(p.blocked),
    )

    private data class Repos(
        val txns: MemoryTransactionRepository,
        val sources: MemorySourceRecordRepository,
        val batches: MemoryImportBatchRepository,
    )

    private fun repos(seed: JsonElement) = Repos(
        txns = MemoryTransactionRepository(EntityJson.transactions(seed.field("transactions"))),
        sources = MemorySourceRecordRepository(seed.field("sourceRecords").jsonArray.map(::sourceRecord)),
        batches = MemoryImportBatchRepository(seed.field("batches").jsonArray.map(::batch)),
    )

    private fun storedJson(r: Repos) = mapOf(
        "storedBatches" to JsonArray(r.batches.all().map(::batchJson)),
        "storedTransactionIds" to JsonArray(r.txns.all().map { json(it.id) }),
        "storedRecordIds" to JsonArray(r.sources.all().map { json(it.id) }),
    )

    @Test
    fun revertImportBatch() {
        Golden.check("revertFlow", "revertImportBatch") { input ->
            val seed = input.field("seed")
            val action = input.field("action")
            val r = repos(seed)
            val revert = RevertImportBatch(
                RevertDeps(
                    txns = r.txns, sources = r.sources, batches = r.batches,
                    settlements = MemorySettlementRepository(EntityJson.settlements(seed.field("settlements"))),
                    allocations = MemoryAllocationRepository(EntityJson.allocations(seed.field("allocations"))),
                    obligations = MemoryObligationRepository(EntityJson.obligations(seed.field("obligations"))),
                    uow = MemoryUnitOfWork(listOf(r.txns, r.sources, r.batches)),
                ),
            )
            runBlocking {
                when (action.field("kind").str) {
                    "history" -> JsonObject(
                        mapOf("history" to JsonArray(revert.history(action.jsonObject["limit"]?.jsonPrimitive?.int ?: 50).map(::batchJson))),
                    )
                    "plan" -> JsonObject(mapOf("plan" to JsonObject(planFields(revert.plan(action.field("batchId").str)))))
                    else -> {
                        val outcome = revert.execute(action.field("batchId").str)
                        // مسار النسخة قبل الحذف مؤجل لطبقة التخزين — التطبيق الحالي من غير المنفذ بيرجّع null برضه
                        JsonObject(mapOf("outcome" to JsonObject(planFields(outcome) + ("backup" to JsonNull))) + storedJson(r))
                    }
                }
            }
        }
    }

    @Test
    fun resumeStagedBatch() {
        Golden.check("revertFlow", "resumeStagedBatch") { input ->
            val seed = input.field("seed")
            val action = input.field("action")
            val r = repos(seed)
            val resume = ResumeStagedBatch(
                ResumeStagedBatchDeps(
                    txns = r.txns, sources = r.sources, batches = r.batches,
                    scanLimit = action.jsonObject["scanLimit"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.int,
                ),
            )

            fun cleanupJson(o: StagedCleanupOutcome) = EntityJson.obj(
                "batchId" to o.batchId, "fileName" to o.fileName,
                "deletedTransactions" to o.deletedTransactions, "deletedRecords" to o.deletedRecords,
                "error" to o.error,
            )

            runBlocking {
                when (action.field("kind").str) {
                    "findStaged" -> JsonObject(mapOf("staged" to JsonArray(resume.findStaged().map(::batchJson))))
                    "cleanup" -> JsonObject(mapOf("outcome" to cleanupJson(resume.cleanup(action.field("batchId").str))) + storedJson(r))
                    else -> JsonObject(mapOf("outcomes" to JsonArray(resume.cleanupAll().map(::cleanupJson))) + storedJson(r))
                }
            }
        }
    }
}
