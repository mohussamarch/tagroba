package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** منع التكرار والبصمات وتعديل المبلغ (native-app/golden/dedupe.json). */
class DedupeGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("dedupe", fn) { json(run(it)) }

    private fun candidate(e: JsonElement): DedupeCandidate {
        val o = e.jsonObject
        return DedupeCandidate(
            accountIdentity = e.field("accountIdentity").str,
            sourceReference = o["sourceReference"]?.takeIf { it !is JsonNull }?.str,
            date = e.field("date").str,
            amountMinor = e.field("amountMinor").num,
            direction = Direction.fromWire(e.field("direction").str),
            merchantName = e.field("merchantName").str,
            rowIndex = e.field("rowIndex").jsonPrimitive.int,
            statedBalanceMinor = o["statedBalanceMinor"]?.num,
            smsSource = o["smsSource"]?.jsonPrimitive?.boolean ?: false,
        )
    }

    private fun DedupeVerdict.toJson() = obj(
        "state" to state.wire, "reason" to reason, "matchedTransactionId" to matchedTransactionId,
        "conflictFields" to conflictFields, "matchedBalanceKey" to matchedBalanceKey,
    )

    private fun nullable(value: String?): JsonElement = value?.let { json(it) } ?: JsonNull

    @Test fun keys() {
        check("keys") {
            val c = candidate(it)
            JsonObject(mapOf("detail" to json(detailKey(c)), "reference" to nullable(referenceKey(c)), "balance" to nullable(balanceKey(c))))
        }
    }

    @Test fun classifyCandidate() {
        check("classifyCandidate") { s ->
            val existing = s.field("existing").jsonArray.map { ExistingRecord(candidate(it), it.field("transactionId").str) }
            val consumed = s.field("consumed").jsonObject.mapValues { (_, v) -> v.jsonPrimitive.int }
            val index = buildDedupeIndex(existing)
            s.field("incoming").jsonArray.map { classifyCandidate(candidate(it), index, consumed).toJson() }
        }
    }

    @Test fun hashContent() { check("hashContent") { hashContent(it.str) } }
    @Test fun importFingerprint() { check("importFingerprint") { importFingerprint(it.field("content").str, it.field("account").str) } }

    @Test fun sourceAmountMinor() {
        check("sourceAmountMinor") { sourceAmountMinor(it.field("amountMinor").num, it.jsonObject["originalAmountMinor"]?.num) }
    }

    @Test fun planAmountEdit() {
        check("planAmountEdit") {
            val txn = it.field("txn")
            val edit = planAmountEdit(
                txn.field("amountMinor").num, txn.jsonObject["originalAmountMinor"]?.num, it.field("amount").num,
                it.field("allocations").jsonArray.map { a -> a.field("amountMinor").num },
                it.field("settlements").jsonArray.map { s -> s.field("amountMinor").num },
            )
            obj("amountMinor" to edit.amountMinor, "originalAmountMinor" to edit.originalAmountMinor)
        }
    }
}
