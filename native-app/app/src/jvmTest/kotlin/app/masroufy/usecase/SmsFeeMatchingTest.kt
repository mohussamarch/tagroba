package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.memory.MemoryCategoryRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * دمج S2 × S4 (الدامج): رسالة فيها الرسوم **جوه** المبلغ (§77-B — الأصلية بتتسجل المبلغ − الرسوم، و`originalAmountMinor` = إجمالي الرسالة)
 * وبعدها كشف فيه سطر التحويل بالمبلغ الأساسي (§75-10). كل الرسايل والأسامي والمبالغ مخترعة.
 */
class SmsFeeMatchingTest {
    private val transfer = "حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nضريبة القيمة المضافة: SAR 0.86\n" +
        "إجمالي المبلغ المستحق: SAR 1,006.61\nإلى: TEST PERSON\nفي: 2026-10-01 09:10"
    private val principalLine = "2026-10-01,1000.00,0.00,4000.00,TEST PERSON,,,حوالة صادرة"

    private fun world() = MatchingWorld(effects = listOf(SmsFeeEffect(MemoryCategoryRepository()), RefundConfirmEffect()))

    @Test fun aStatementPrincipalLineMergesIntoTheSmsTransferWhoseFeeWasSplit() = runBlocking<Unit> {
        val w = world()
        w.receive(transfer, at = "2026-10-01T10:00:00Z")
        assertEquals(1, w.recordSms())
        val main = w.all().single { it.economicKind != EconomicKind.FEE }
        assertEquals(100_000L to 100_661L, main.amountMinor to main.originalAmountMinor)
        assertEquals(661L, w.all().single { it.economicKind == EconomicKind.FEE }.amountMinor)

        val preview = w.importer.preview(w.statement(principalLine))
        val line = preview.lines.single()
        assertEquals(main.id, line.mergeInto, "سطر الكشف بالمبلغ الأساسي هو نفس تحويل الرسالة — مش تحويل تاني")
        w.import(w.statement(principalLine))
        assertEquals(1, w.all().count { it.economicKind != EconomicKind.FEE }, "تحويل واحد")
        assertEquals(2, w.sources.listByTransactionIds(listOf(main.id)).size, "مصدرين: الرسالة وسطر الكشف")
    }

    @Test fun aStatementTotalLineStillMergesIntoTheSmsTransfer() = runBlocking<Unit> {
        val w = world()
        w.receive(transfer, at = "2026-10-01T10:00:00Z")
        w.recordSms()
        val main = w.all().single { it.economicKind != EconomicKind.FEE }
        val total = "2026-10-01,1006.61,0.00,3993.39,TEST PERSON,,,حوالة صادرة"
        assertEquals(main.id, w.importer.preview(w.statement(total)).lines.single().mergeInto, "كشف بيطلّع الإجمالي — زي قبل الدمج")
    }

    @Test fun anSmsWithTheFeeInsideMergesIntoTheStatementPrincipalLine() = runBlocking<Unit> {
        val w = world()
        w.import(w.statement(principalLine))
        val statement = w.all().single()
        w.receive(transfer, at = "2026-10-01T10:00:00Z")
        w.recordSms()
        assertEquals(1, w.all().count { it.economicKind != EconomicKind.FEE }, "الرسالة اتدمجت في سطر الكشف — مش تحويل تاني")
        assertEquals(2, w.sources.listByTransactionIds(listOf(statement.id)).size)
        // الرسالة اتدمجت ⇒ ما اتكتبش ليها «رسوم بنكية» — الكشف هو دفتر البنك، ورسومه بتيجي من سطره هو (اختيار الدامج — بيتغيّر)
        assertEquals(emptyList(), w.all().filter { it.economicKind == EconomicKind.FEE }.map { it.amountMinor })
    }
}
