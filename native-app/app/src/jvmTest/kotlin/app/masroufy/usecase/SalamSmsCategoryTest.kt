package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.SAUDI_PACK
import app.masroufy.core.SmsParseResult
import app.masroufy.core.parseBankSms
import app.masroufy.core.toParsedRow
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryUserSettingsStore
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.seed.BundledSeeds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * fix055 (فحص 0.5.4 ملاحظة 3): رسالة شراء «لـسلام» على حساب **قديم** (عنده قواعد دراهم الأولى بس — دورة الخلفية لسه ما اشتغلتش) ⇒ من
 * المحلل الحقيقي لحد التصنيف في نفس طريق تسجيل الرسايل (`buildCategorizeDeps` + `categorizeRow`). رسايل مخترعة.
 */
class SalamSmsCategoryTest {
    private val clock = FixedClock("2026-10-24T10:00:00.000Z")

    private fun row(body: String) = assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", "2026-10-24T00:21:00Z", body), 1)).row.toParsedRow()

    private suspend fun oldAccount(withEnsure: Boolean): Pair<ImportStatementDeps, String> {
        val seed = BundledSeeds.load(SAUDI_PACK)
        val categories = MemoryCategoryRepository(seed.categories)
        val rules = MemoryRuleRepository(seed.rules.filter { it.priority < DARAHIM_RULES2_FROM_PRIORITY })
        val settings = MemoryUserSettingsStore()
        val repairs = listOf(
            RepairDarahimRules("sa", categories, rules, BundledSeeds, SAUDI_PACK, settings, clock),
            RepairDarahimRules("sa", categories, rules, BundledSeeds, SAUDI_PACK, settings, clock, DARAHIM_RULES2_REPAIR, DARAHIM_RULES2_FROM_PRIORITY, Int.MAX_VALUE),
        )
        val txns = MemoryTransactionRepository()
        val sources = MemorySourceRecordRepository()
        val batches = MemoryImportBatchRepository()
        val deps = ImportStatementDeps(
            txns, sources, batches, MemoryMerchantRepository(), categories, rules, MemoryUnitOfWork(listOf(txns, sources, batches)),
            SequentialIdGenerator(), clock,
            ensureRules = if (withEnsure) EnsureDarahimRules(RepairMissingDefaultSubs(categories, BundledSeeds, SAUDI_PACK), repairs) else null,
        )
        val pharmacy = seed.rules.single { it.matchText == "سلام" }.categoryId
        return deps to pharmacy
    }

    @Test fun salamSmsLandsInPharmacyEvenBeforeTheBackgroundCycle() = runBlocking<Unit> {
        val (deps, pharmacy) = oldAccount(withEnsure = true)
        val salam = row(SALAM)
        assertEquals("سلام", salam.merchantName, "«لـ» مش جزء من اسم المحل")
        assertEquals(4130L, salam.amountMinor)
        assertEquals(pharmacy, categorizeRow(salam, buildCategorizeDeps(deps)).categoryId)
        val bakery = row("شراء PoS\nعبر1234;مدى\nبـSR 19.65\nلـمخبز السلام\n26/10/24 00:22")
        assertNotEquals(pharmacy, categorizeRow(bakery, buildCategorizeDeps(deps)).categoryId, "مطابقة تامة: «مخبز السلام» مش صيدلية")
    }

    @Test fun withoutTheEnsureStepTheOldAccountMissesTheRule() = runBlocking<Unit> {
        val (deps, _) = oldAccount(withEnsure = false)
        assertNull(categorizeRow(row(SALAM), buildCategorizeDeps(deps)).categoryId, "ده اللي اتشاف على المحاكي")
    }

    private companion object {
        /** شكل الراجحي 2026 («شراء PoS» — `SaudiSmsRajhi2026ShapesTest`) بمحل اسمه «سلام». */
        const val SALAM = "شراء PoS\nعبر1234;مدى\nبـSR 41.30\nلـسلام\n26/10/24 00:21"
    }
}
