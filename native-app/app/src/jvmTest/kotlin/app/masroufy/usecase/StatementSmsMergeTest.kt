package app.masroufy.usecase

import app.masroufy.core.ImportBatchState
import app.masroufy.core.MatchingState
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §75-10 (قرار المالك 2026-10-08): **الكشف والرسالة نفس العملية لو الفرق يومين بالكتير، ويتدمجوا** (عملية واحدة بمصدرين). الكشف بيكسب في
 * التاريخ والرصيد المعلن (سلسلة الرصيد سطر بسطر — CLAUDE.md معيار ٣)، والباقي بتاع اللي اتسجل الأول. بيانات وهمية كلها.
 */
class StatementSmsMergeTest {
    private val cafe = "2026-10-02,100.00,0.00,4900.00,TEST CAFE RIYADH,,,شراء نقاط بيع"
    private val mart = "2026-10-03,40.00,0.00,4860.00,TEST MART,,,شراء نقاط بيع"
    private val salary = "2026-10-04,0.00,1000.00,5860.00,TEST EMPLOYER,,,حوالة واردة"

    @Test fun smsThenStatementIsOneTransactionWithTwoSources() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(purchaseSms("100", "26/10/01"))
        assertEquals(1, w.recordSms())
        val sms = w.all().single()
        assertEquals("2026-10-01", sms.occurredAt)
        assertNull(sms.statedBalanceMinor)

        val request = w.statement(cafe, mart, salary)
        val preview = w.importer.preview(request)
        val merged = preview.lines.single { it.row.amountMinor == 10_000L }
        assertEquals(MatchingState.NEW, merged.state)
        assertEquals(sms.id, merged.mergeInto)
        assertTrue(merged.selectedByDefault)
        assertEquals(uiText(TextKey.MATCH_MERGE, uiText(TextKey.MATCH_SOURCE_SMS), "2026-10-01"), merged.reason)
        // سطر الدمج أثره صفر: الـ100 اتحسبت من الرسالة خلاص
        assertEquals(1_000_00L - 40_00L, preview.impact.walletDeltaMinor)
        assertEquals(40_00L, preview.impact.expenseMinor)

        val batch = w.importer.commit(request, preview)
        assertEquals(ImportBatchState.COMMITTED, batch.state)
        assertEquals(2, batch.counts.imported, "سطرين جديد بس — الدمج ما بيعملش عملية")
        assertEquals(3, w.all().size)
        val after = w.txns.findByIds(listOf(sms.id)).single()
        assertEquals(2, w.sources.listByTransactionIds(listOf(sms.id)).size, "مصدرين: الرسالة وسطر الكشف")
        assertEquals("2026-10-02", after.occurredAt, "تاريخ الكشف")
        assertEquals(4_900_00L, after.statedBalanceMinor, "رصيد الكشف")
        assertEquals(merged.row.lineNumber, after.sourceOrder)
        assertEquals("TEST CAFE", after.rawMerchantName, "المحل بتاع اللي اتسجل الأول")
        val record = w.sources.listByBatch(batch.id).single { it.transactionId == sms.id }
        assertEquals(MatchingState.DUPLICATE, record.matchingState)
        assertEquals("2026-10-01", record.mergeUndo?.occurredAt)

        // سلسلة الرصيد بتطابق الكشف سطر بسطر
        val outcome = ReconcileBalance(ReconcileDeps(w.txns, w.wallets)).run(MW_BANK, "2026-10-04", 28)
        assertEquals(emptyList(), outcome.result.mismatches)
        assertEquals(3, outcome.result.checkedCount)
        assertEquals(5_860_00L, outcome.result.closingMinor)

        // نفس الكشف تاني ⇒ ولا حاجة جديدة
        val again = w.importer.preview(request)
        assertTrue(again.lines.all { it.state == MatchingState.DUPLICATE }, again.lines.map { it.state }.toString())
        assertEquals(batch.id, w.importer.commit(request, again).id)
        assertEquals(3, w.all().size)
        // ونفس الرسالة لو وصلت تاني ⇒ مكررة (مرجعها على العملية المدموجة)
        w.receive(purchaseSms("100", "26/10/01"))
        assertEquals(MatchingState.DUPLICATE, w.review.load(w.target).duplicates.single().state)
    }

    @Test fun theSameStatementInAnotherExportIsStillRecognisedByItsBalance() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(purchaseSms("100", "26/10/01"))
        w.recordSms()
        w.import(w.statement(cafe))
        // نفس السطر بنص تاني (تصدير تاني للكشف): التاريخ والمبلغ والرصيد بتوع الكشف ⇒ نفس سطر الكشف
        val line = w.importer.preview(w.statement("2026-10-02,100.00,0.00,4900.00,TEST CAFE RIYADH SA,,,POS", file = "export-2.csv")).lines.single()
        assertEquals(MatchingState.DUPLICATE, line.state)
        assertEquals(uiText(TextKey.DEDUPE_SAME_STATEMENT_LINE), line.reason)
    }

    @Test fun aStatementOfOnlyTheMergedLineHasZeroImpactAndStillCommits() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(purchaseSms("100", "26/10/01"))
        w.recordSms()
        val request = w.statement(cafe)
        val preview = w.importer.preview(request)
        assertEquals(ImportImpact(0, 0, 0), preview.impact)
        val batch = w.importer.commit(request, preview)
        assertEquals(ImportBatchState.COMMITTED, w.batchStore.findById(batch.id)?.state)
        assertEquals(1, w.all().size)
    }

    @Test fun statementThenSmsMergesAndLeavesTheInbox() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.import(w.statement(cafe, mart))
        val before = w.all().associateBy { it.id }
        val cafeTxn = before.values.single { it.amountMinor == 10_000L }
        w.receive(purchaseSms("100", "26/10/01"))
        val review = w.review.load(w.target)
        val line = review.ready.single()
        assertEquals(uiText(TextKey.MATCH_MERGE, uiText(TextKey.MATCH_SOURCE_STATEMENT), "2026-10-02"), line.reason)

        assertEquals(1, w.review.recordAll(emptyMap(), emptyList()))
        assertEquals(before.keys, w.all().map { it.id }.toSet(), "ولا عملية جديدة")
        assertTrue(w.inbox.sync().messages.isEmpty(), "الرسالة اتشالت من الصندوق")
        assertEquals(2, w.sources.listByTransactionIds(listOf(cafeTxn.id)).size)
        // الرسالة اللي جت بعد الكشف ما بتغيّرش حاجة في العملية (الكشف كسب خلاص)
        assertEquals(cafeTxn, w.txns.findByIds(listOf(cafeTxn.id)).single())
        assertTrue(w.sources.listByTransactionIds(listOf(cafeTxn.id)).all { it.mergeUndo == null }, "مفيش حاجة تترجع")

        // نفس الرسالة لو رجعت (انقطاع قبل الشيل) ⇒ مكررة، مش عملية تانية
        w.receive(purchaseSms("100", "26/10/01"), at = "2026-10-01T09:00:00Z")
        assertEquals(MatchingState.DUPLICATE, w.review.load(w.target).duplicates.single().state)
    }

    @Test fun aRenamedWalletStillMergesByWallet() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(purchaseSms("100", "26/10/01"))
        // الرسايل اتسجلت باسم المحفظة القديم (هوية حساب تانية) — نفس المحفظة
        w.review.load(w.target.copy(accountIdentity = "بنك وهمي القديم"))
        w.review.recordAll(emptyMap(), emptyList())
        val sms = w.all().single()
        val preview = w.importer.preview(w.statement(cafe))
        assertEquals(sms.id, preview.lines.single().mergeInto)
    }

    @Test fun threeDaysApartIsTodaysBehaviour() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(purchaseSms("100", "26/10/01"))
        w.recordSms()
        val preview = w.importer.preview(w.statement("2026-10-04,100.00,0.00,4900.00,TEST CAFE RIYADH,,,شراء"))
        assertEquals(MatchingState.NEW, preview.lines.single().state)
        assertNull(preview.lines.single().mergeInto)
    }

    @Test fun twoCandidatesAskInsteadOfGuessing() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(purchaseSms("100", "26/10/01"))
        w.receive(purchaseSms("100", "26/10/02", shop = "TEST BAKERY"), at = "2026-10-02T09:00:00Z")
        assertEquals(2, w.recordSms())
        val request = w.statement(cafe)
        val line = w.importer.preview(request).lines.single()
        assertEquals(MatchingState.SIMILAR, line.state)
        assertNull(line.mergeInto)
        assertTrue(!line.selectedByDefault)
        w.import(request)
        assertEquals(2, w.all().size, "ما اتضافش ولا اتدمج من غير قرار")
    }

    @Test fun oneSmsAbsorbsOneStatementLineOnly() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(purchaseSms("100", "26/10/01"))
        w.recordSms()
        val lines = w.importer.preview(w.statement("2026-10-01,100.00,0.00,4900.00,TEST CAFE,,,شراء", cafe.replace("4900.00", "4800.00"))).lines
        assertEquals(listOf(MatchingState.SIMILAR, MatchingState.SIMILAR), lines.map { it.state })
        assertTrue(lines.all { it.mergeInto == null })
        // وبعد الدمج العملية ما بتبلعش سطر تاني
        val w2 = MatchingWorld()
        w2.receive(purchaseSms("100", "26/10/01"))
        w2.recordSms()
        w2.import(w2.statement(cafe))
        val next = w2.importer.preview(w2.statement("2026-10-02,100.00,0.00,4800.00,TEST CAFE RIYADH,,,شراء تاني", file = "later.csv")).lines.single()
        assertNull(next.mergeInto)
    }

    @Test fun withoutTheWindowNothingIsMerged() = runBlocking<Unit> {
        val w = MatchingWorld(window = null)
        w.receive(purchaseSms("100", "26/10/01"))
        w.recordSms()
        val line = w.importer.preview(w.statement(cafe)).lines.single()
        assertEquals(MatchingState.NEW, line.state)
        assertNull(line.mergeInto)
        assertEquals(uiText(TextKey.DEDUPE_NEW), line.reason)
    }

    @Test fun aCrashBeforeCommitNeverPointsAtTheSmsTransaction() = runBlocking<Unit> {
        val w = MatchingWorld(rollback = false)
        w.receive(purchaseSms("100", "26/10/01"))
        w.recordSms()
        val sms = w.all().single()
        w.batches.crash = true
        assertFailsWith<IllegalStateException> { w.import(w.statement(cafe, mart)) }
        val staged = w.batchStore.all().single { it.state == ImportBatchState.STAGED }
        assertTrue(w.sources.listByBatch(staged.id).none { it.transactionId == sms.id }, "الدفعة المعلّقة ما بتشاورش على عملية الرسالة")
        assertEquals(1, w.sources.listByTransactionIds(listOf(sms.id)).size)

        val cleaned = ResumeStagedBatch(ResumeStagedBatchDeps(w.txns, w.sources, w.batchStore)).cleanupAll()
        assertNull(cleaned.single().error)
        assertEquals(listOf(sms), w.all(), "التنظيف ما مسحش عملية الرسالة ولا غيّرها")

        // والمحاولة التانية بتدمج عادي
        w.import(w.statement(cafe, mart))
        assertEquals(2, w.all().size)
        assertEquals("2026-10-02", w.txns.findByIds(listOf(sms.id)).single().occurredAt)
    }

    @Test fun aStagedTransactionIsNeverAMergeTarget() = runBlocking<Unit> {
        val w = MatchingWorld(rollback = false)
        w.batches.crash = true
        assertFailsWith<IllegalStateException> { w.import(w.statement(cafe)) }
        assertEquals(1, w.all().size, "عملية الكشف المعلّقة لسه موجودة لحد التنظيف")
        w.receive(purchaseSms("100", "26/10/01"))
        val line = w.review.load(w.target).ready.single()
        assertEquals(uiText(TextKey.DEDUPE_NEW_REFERENCE), line.reason, "ما اتدمجتش في عملية هتتمسح")
    }

    @Test fun revertingTheStatementKeepsTheSmsAndRestoresItsDateAndBalance() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(purchaseSms("100", "26/10/01"))
        w.recordSms()
        val sms = w.all().single()
        val batch = w.import(w.statement(cafe, mart))
        val revert = w.revert()
        val plan = revert.plan(batch.id)
        assertEquals(RevertDecision.KEPT_OTHER_SOURCE, plan.outcomes.single { it.transactionId == sms.id }.decision)
        revert.execute(batch.id)
        val back = w.txns.findByIds(listOf(sms.id)).single()
        assertEquals(sms.copy(updatedAt = back.updatedAt), back, "التاريخ والترتيب والرصيد رجعوا زي ما كانوا")
        assertEquals(1, w.all().size, "سطر الـ40 اتمسح")
        assertEquals(1, w.sources.listByTransactionIds(listOf(sms.id)).size)
        // ونفس الكشف تاني بيتدمج من جديد
        assertNotNull(w.importer.preview(w.statement(cafe, mart, file = "again.csv")).lines.single { it.row.amountMinor == 10_000L }.mergeInto)
    }

    @Test fun revertingTheSmsBatchKeepsTheStatementVersion() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(purchaseSms("100", "26/10/01"))
        w.recordSms()
        val sms = w.all().single()
        val smsBatch = w.sources.listByTransactionIds(listOf(sms.id)).single().batchId
        w.import(w.statement(cafe))
        val merged = w.txns.findByIds(listOf(sms.id)).single()
        w.revert().execute(smsBatch)
        // العملية فضلت بسطر الكشف بس — والتراجع عن الرسالة ما بيرجّعش حاجة (هي ما غيّرتش حاجة)
        assertEquals(merged, w.txns.findByIds(listOf(sms.id)).single())
        assertEquals(1, w.sources.listByTransactionIds(listOf(sms.id)).size)
    }
}
