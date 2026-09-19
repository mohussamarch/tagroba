package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** كشف الراجحي PDF بعد قراية النص — صفحات مصنوعة (native-app/golden/pdf.json). */
class PdfGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("pdf", fn) { json(run(it)) }
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)
    private fun words(e: JsonElement) = e.jsonArray.map { PositionedWord(it.field("x").jsonPrimitive.double, it.field("y").jsonPrimitive.double, it.field("text").str) }

    @Test fun parse() {
        check("parseAlrajhiPdf") {
            val out = parseAlrajhiPdf(it.jsonArray.map { p -> PdfPage(p.field("pageNumber").jsonPrimitive.int, words(p.field("words"))) })
            obj(
                "rows" to out.rows.map { r ->
                    JsonObject(
                        buildMap {
                            put("lineNumber", json(r.lineNumber)); put("date", json(r.date)); put("amountMinor", json(r.amountMinor))
                            put("direction", json(r.direction.wire)); put("merchantName", json(r.merchantName)); put("reference", nullable(r.reference))
                            put("sourceName", json(r.sourceName)); r.sourceOperationType?.let { v -> put("sourceOperationType", json(v)) }
                            r.statedBalanceMinor?.let { v -> put("statedBalanceMinor", json(v)) }; put("description", json(r.description)); put("raw", json(r.raw))
                        },
                    )
                },
                "errors" to out.errors.map { e -> obj("lineNumber" to e.lineNumber, "field" to e.field, "message" to e.message, "raw" to e.raw) },
                "pagesRead" to out.pagesRead,
            )
        }
    }

    @Test fun text() {
        check("joinLine") { joinLine(words(it)) }
        check("tidy") { tidy(it.field("text").str, it.field("max").jsonPrimitive.int) }
        check("hasArabic") { hasArabic(it.str) }
        check("extractMerchantName") { nullable(extractMerchantName(it.str)) }
    }
}
