package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** قراية CSV والمخططات ومطابقة الرصيد (native-app/golden/import.json). */
class ImportGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("import", fn) { json(run(it)) }
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun CsvDocument.toJson() = obj(
        "header" to header,
        "rows" to rows.map { obj("lineNumber" to it.lineNumber, "cells" to it.cells, "raw" to it.raw) },
    )

    private fun ParsedRow.toJson() = JsonObject(
        buildMap {
            put("lineNumber", json(lineNumber)); put("date", json(date)); put("amountMinor", json(amountMinor))
            put("direction", json(direction.wire)); put("merchantName", json(merchantName)); put("reference", nullable(reference))
            put("sourceName", json(sourceName)); put("description", json(description)); put("raw", json(raw))
            sourceCategory?.let { put("sourceCategory", json(it)) }
            sourceOperationType?.let { put("sourceOperationType", json(it)) }
            statedBalanceMinor?.let { put("statedBalanceMinor", json(it)) }
        },
    )

    @Test fun parseCsv() { check("parseCsv") { parseCsv(it.str).toJson() } }
    @Test fun detectSchema() { check("detectSchema") { detectSchema(parseCsv(it.str)).wire } }

    @Test fun parseRows() {
        check("parseRows") {
            val schema = it.field("schema").let { s -> if (s is JsonNull) null else SchemaId.fromWire(s.str) }
            val outcome = parseRows(parseCsv(it.field("text").str), schema)
            obj(
                "schema" to outcome.schema.wire,
                "rows" to outcome.rows.map { r -> r.toJson() },
                "errors" to outcome.errors.map { e -> obj("lineNumber" to e.lineNumber, "field" to e.field, "message" to e.message, "raw" to e.raw) },
            )
        }
    }

    @Test fun legacyDateToIso() { check("legacyDateToIso") { legacyDateToIso(it.str) } }

    @Test fun reconcileBalance() {
        check("reconcileBalance") {
            val movements = it.field("movements").jsonArray.map { m ->
                val o = m.jsonObject
                LedgerMovement(
                    m.field("date").str, m.field("sourceOrder").jsonPrimitive.int, m.field("debitMinor").num, m.field("creditMinor").num,
                    o["statedBalanceMinor"]?.num, o["reference"]?.str, o["label"]?.str,
                )
            }
            val r = reconcileBalance(it.field("opening").num, it.field("openingAt").str, movements)
            JsonObject(
                mapOf(
                    "openingMinor" to json(r.openingMinor), "openingAt" to json(r.openingAt), "closingMinor" to json(r.closingMinor),
                    "closingAt" to nullable(r.closingAt), "totalDebitMinor" to json(r.totalDebitMinor), "totalCreditMinor" to json(r.totalCreditMinor),
                    "movementCount" to json(r.movementCount), "checkedCount" to json(r.checkedCount), "ambiguityNote" to nullable(r.ambiguityNote),
                    "mismatches" to json(
                        r.mismatches.map { x ->
                            obj(
                                "index" to x.index, "date" to x.date, "computedMinor" to x.computedMinor, "statedMinor" to x.statedMinor,
                                "differenceMinor" to x.differenceMinor, "reference" to x.reference, "label" to x.label, "sameDayCount" to x.sameDayCount,
                            )
                        },
                    ),
                ),
            )
        }
    }
}
