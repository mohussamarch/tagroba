package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.ImportSourceType
import app.masroufy.core.ParsedRow
import app.masroufy.core.SchemaId
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
import kotlin.test.Test
import kotlin.test.assertEquals

/** الاستيراد بيسجّل العمليات **بعملة الكشف** — كانت مثبتة ريال، فكشف QNB مصر كان هيتسجل بالريال من غير رسالة. */
class ImportCurrencyTest {
    @Test fun egyptianStatementIsStoredInPounds() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val sources = MemorySourceRecordRepository()
        val batches = MemoryImportBatchRepository()
        val importer = ImportStatement(
            ImportStatementDeps(
                txns, sources, batches, MemoryMerchantRepository(), MemoryCategoryRepository(), MemoryRuleRepository(),
                MemoryUnitOfWork(listOf(txns, sources, batches)), SequentialIdGenerator(), FixedClock("2026-10-01T00:00:00.000Z"),
            ),
        )
        val rows = listOf(ParsedRow(1, "2026-01-03", 4_125, Direction.OUT, "SAMPLE STORE", null, "كشف QNB", "CARD PURCHASE", "صفحة 1 · 2026-01-03", statedBalanceMinor = 120_875))
        val request = ImportRequest("qnb.pdf", "صفحة 1 · 2026-01-03", "qnb", ImportSourceType.PDF_QNB, null, SchemaId.QNB_PDF, parsedRows = rows, currency = Currency.EGP)
        importer.commit(request, importer.preview(request))
        assertEquals(listOf(Currency.EGP), txns.all().map { it.currency })
    }
}
