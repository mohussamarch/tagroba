package app.masroufy.usecase

import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.MemoryTransactionRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** تصدير العمليات CSV على `csvExport.json` — الناتج نص الملف كامل بالحرف (ومعاه علامة BOM). */
class CsvExportGoldenTest {
    @Test
    fun exportCsv() {
        Golden.check("csvExport", "exportCsv") { input ->
            val exporter = ExportCsv(MemoryTransactionRepository(EntityJson.transactions(input.field("transactions"))))
            json(runBlocking { exporter.export(input.field("from").str, input.field("to").str, input.field("payday").jsonPrimitive.int) })
        }
    }
}
