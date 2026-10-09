package app.masroufy.usecase

import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.MergeRestore
import app.masroufy.core.ParsedRow
import app.masroufy.core.ReviewState
import app.masroufy.core.SchemaId
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.uiText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * مراجعة الشريحة S4 (§75-10): اللي المراجعة لقته واتصلّح — بيانات وهمية كلها.
 * - «متسجلة خلاص» بالنص لوحده كانت بتشيل عمليات مختلفة فعلًا في صمت (سطر PDF جديد · قهوة تانية بنفس النص).
 * - التراجع عن الكشف كان بيمسح تعديل المالك بعد الدمج.
 * - سؤال «أكتر من احتمال» ما كانش ليه رد «هي دي».
 * - التسجيل التلقائي في الخلفية مع الدمج (ما كانش متجرب).
 */
class StatementSmsMergeReviewTest {
    private val cafe = "2026-10-02,100.00,0.00,4900.00,TEST CAFE RIYADH,,,شراء نقاط بيع"

    private fun pdfRow(line: Int, date: String, minor: Long, balance: Long, shop: String, page: Int = 1) = ParsedRow(
        lineNumber = line, date = date, amountMinor = minor, direction = Direction.OUT, merchantName = shop, reference = null,
        sourceName = "كشف وهمي", description = shop, raw = "صفحة $page · $date", statedBalanceMinor = balance,
    )

    private fun pdf(name: String, rows: List<ParsedRow>) = ImportRequest(
        name, rows.joinToString("\n") { "${it.raw}|${it.amountMinor}|${it.statedBalanceMinor}" }, MW_BANK_NAME, ImportSourceType.PDF_ALRAJHI,
        walletId = MW_BANK, schema = SchemaId.ALRAJHI_PDF, parsedRows = rows,
    )

    /** نص سطر الـPDF «صفحة N · التاريخ» بس ⇒ سطر جديد في نفس الصفحة واليوم كان بيطلع «متسجل خلاص». */
    @Test fun aNewPdfRowOnTheSamePageAndDayIsNotDroppedAsAlreadyMerged() = runBlocking<Unit> {
        for (window in listOf<Int?>(null, CROSS_SOURCE_WINDOW_DAYS)) {
            val w = MatchingWorld(window = window)
            w.receive(matchingPurchaseSms("100", "26/10/02"), at = "2026-10-02T09:00:00Z")
            assertEquals(1, w.recordSms())
            // تصدير ١ (نص اليوم): سطر الـ100 — بيتدمج في عملية الرسالة لما النافذة شغالة
            w.import(pdf("export-1.pdf", listOf(pdfRow(1, "2026-10-02", 10_000, 490_000, "TEST CAFE"))))
            // تصدير ٢ (بعدين): نفس السطر + شراء جديد 75.00 في نفس اليوم ونفس الصفحة
            val preview = w.importer.preview(
                pdf("export-2.pdf", listOf(pdfRow(1, "2026-10-02", 10_000, 490_000, "TEST CAFE"), pdfRow(2, "2026-10-02", 7_500, 482_500, "TEST BAKERY"))),
            )
            val bakery = preview.lines.single { it.row.amountMinor == 7_500L }
            assertEquals(MatchingState.NEW, bakery.state, "window=$window: ${bakery.reason}")
            assertTrue(bakery.selectedByDefault)
            if (window != null) {
                // والسطر القديم بيتعرف بالرصيد (سطر الكشف نفسه) مش بالنص
                val old = preview.lines.single { it.row.amountMinor == 10_000L }
                assertEquals(MatchingState.DUPLICATE, old.state)
                assertEquals(uiText(TextKey.DEDUPE_SAME_STATEMENT_LINE), old.reason)
            }
        }
    }

    /** قهوتين بنفس المبلغ في نفس اليوم: نص الرسالتين واحد — التانية **بتسأل** (زي قبل S4)، مش «متسجلة خلاص» وتتشال. */
    @Test fun aSecondIdenticalSmsLaterTheSameDayAsksInsteadOfBeingDropped() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(matchingPurchaseSms("12", "26/10/01"), at = "2026-10-01T09:00:00Z")
        assertEquals(1, w.recordSms())
        w.import(w.statement("2026-10-01,12.00,0.00,4988.00,TEST CAFE RIYADH,,,شراء نقاط بيع"))
        assertEquals(1, w.all().size, "اتدمجوا")
        w.receive(matchingPurchaseSms("12", "26/10/01"), at = "2026-10-01T18:00:00Z")
        val review = w.review.load(w.target)
        assertEquals(emptyList(), review.duplicates, "مش «متسجلة خلاص»")
        assertEquals(MatchingState.SIMILAR, review.similar.single().state)
        assertEquals(0, w.review.recordAll(emptyMap(), emptyList()))
        assertEquals(1, w.inbox.sync().messages.size, "فضلت في الصندوق مستنية قرار المالك")
        assertEquals(1, w.all().size)
        // المالك قال «دي عملية تانية» ⇒ اتسجلت
        val again = w.review.load(w.target).similar.single()
        assertEquals(1, w.review.recordAll(emptyMap(), listOf(again.lineNumber)))
        assertEquals(2, w.all().size)
    }

    /** المالك صلّح التاريخ بعد الدمج ⇒ التراجع عن الكشف بيسيب تصليحه، وبيرجّع الرصيد (مصدره اتمسح) والترتيب. */
    @Test fun revertKeepsAnOwnerDateEditMadeAfterTheMerge() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(matchingPurchaseSms("100", "26/10/01"))
        w.recordSms()
        val sms = w.all().single()
        val batch = w.import(w.statement(cafe))
        val merged = w.txns.findByIds(listOf(sms.id)).single()
        assertEquals("2026-10-02", merged.occurredAt)
        w.txns.saveMany(listOf(merged.copy(occurredAt = "2026-10-03", updatedAt = "2026-10-06T00:00:00.000Z")))
        w.revert().execute(batch.id)
        val back = w.txns.findByIds(listOf(sms.id)).single()
        assertEquals("2026-10-03", back.occurredAt, "تصليح المالك بيفضل")
        assertEquals(sms.statedBalanceMinor, back.statedBalanceMinor, "رصيد الكشف اتشال مع الكشف")
        assertEquals(sms.sourceOrder, back.sourceOrder)
    }

    @Test fun eachMergedFieldComesBackOnlyWhileItStillHoldsWhatTheStatementWrote() {
        val t = Transaction(
            id = "t-1", occurredAt = "2026-10-02", datePrecision = "day", sourceOrder = 5, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
            observedDirection = Direction.OUT, amountMinor = 10_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", statedBalanceMinor = 490_000,
        )
        val wrote = MergeRestore("2026-10-01", 1, 300_000, "2026-10-02", 490_000)
        assertEquals(t.copy(occurredAt = "2026-10-01", sourceOrder = 1, statedBalanceMinor = 300_000), undoMergedFields(t, wrote, lineNumber = 5))
        // كل حقل لوحده: اللي اتغير بعد الدمج بيفضل
        assertEquals(t.copy(sourceOrder = 1, statedBalanceMinor = 300_000), undoMergedFields(t, wrote.copy(mergedOccurredAt = "2026-10-03"), 5))
        assertEquals(t.copy(occurredAt = "2026-10-01", statedBalanceMinor = 300_000), undoMergedFields(t, wrote, lineNumber = 7))
        assertEquals(t.copy(occurredAt = "2026-10-01", sourceOrder = 1), undoMergedFields(t, wrote.copy(mergedStatedBalanceMinor = 480_000), 5))
        // السطر ما كانش فيه رصيد ⇒ الرصيد ما اتغيرش بالدمج ⇒ ما بيتلمسش
        assertEquals(t.copy(occurredAt = "2026-10-01", sourceOrder = 1), undoMergedFields(t, wrote.copy(mergedStatedBalanceMinor = null), 5))
        // سجل أقدم من غير المكتوب ⇒ زي الأول (التاريخ والرصيد من غير شرط)
        assertEquals(t.copy(occurredAt = "2026-10-01", sourceOrder = 1, statedBalanceMinor = 300_000), undoMergedFields(t, MergeRestore("2026-10-01", 1, 300_000), 5))
    }

    /** «أكتر من احتمال»: المالك بيختار أنهي رسالة هي سطر الكشف ⇒ دمج (مش إضافة مرة تانية ولا تجاهل). */
    @Test fun theOwnerPicksWhichSmsTheStatementLineIs() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(matchingPurchaseSms("100", "26/10/01"))
        w.receive(matchingPurchaseSms("100", "26/10/02", shop = "TEST BAKERY"), at = "2026-10-02T09:00:00Z")
        assertEquals(2, w.recordSms())
        val cafeSms = w.all().single { it.occurredAt == "2026-10-01" }
        val bakerySms = w.all().single { it.occurredAt == "2026-10-02" }
        val request = w.statement(cafe)
        val preview = w.importer.preview(request)
        val line = preview.lines.single()
        assertEquals(MatchingState.SIMILAR, line.state)
        assertEquals(listOf(bakerySms.id, cafeSms.id), line.mergeCandidates, "الأقرب في التاريخ الأول")
        val e = assertFailsWith<IllegalArgumentException> { w.importer.commit(request, preview, mergeChoices = mapOf(line.row.lineNumber to "txn-x")) }
        assertEquals(uiText(TextKey.MATCH_CHOICE_INVALID), e.message)

        val batch = w.importer.commit(request, preview, mergeChoices = mapOf(line.row.lineNumber to cafeSms.id))
        assertEquals(0, batch.counts.imported)
        assertEquals(2, w.all().size, "ولا عملية جديدة")
        val after = w.txns.findByIds(listOf(cafeSms.id)).single()
        assertEquals("2026-10-02", after.occurredAt, "تاريخ الكشف")
        assertEquals(4_900_00L, after.statedBalanceMinor, "رصيد الكشف")
        assertEquals(bakerySms, w.txns.findByIds(listOf(bakerySms.id)).single(), "التانية ما اتلمستش")
        val record = w.sources.listByBatch(batch.id).single { it.transactionId == cafeSms.id }
        assertEquals(MatchingState.DUPLICATE, record.matchingState)
        assertEquals(uiText(TextKey.MATCH_MERGE_CHOSEN, uiText(TextKey.MATCH_SOURCE_SMS)), record.reason)
        // نفس الكشف تاني ⇒ مكرر (بالرصيد)
        assertEquals(MatchingState.DUPLICATE, w.importer.preview(w.statement(cafe, file = "again.csv")).lines.single().state)
        // والتراجع بيرجّع الرسالة زي ما كانت
        w.revert().execute(batch.id)
        val restored = w.txns.findByIds(listOf(cafeSms.id)).single()
        assertEquals(cafeSms.copy(updatedAt = restored.updatedAt), restored)
    }

    @Test fun eachTransactionCanBeChosenForOneLineOnly() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.receive(matchingPurchaseSms("100", "26/10/01"))
        w.recordSms()
        val sms = w.all().single()
        // سطرين بيتنافسوا على رسالة واحدة ⇒ الاتنين بيسألوا
        val request = w.statement("2026-10-01,100.00,0.00,4900.00,TEST CAFE,,,شراء", cafe.replace("4900.00", "4800.00"))
        val preview = w.importer.preview(request)
        assertTrue(preview.lines.all { it.mergeCandidates == listOf(sms.id) })
        val both = preview.lines.associate { it.row.lineNumber to sms.id }
        assertFailsWith<IllegalArgumentException> { w.importer.commit(request, preview, mergeChoices = both) }
        val first = preview.lines.first().row.lineNumber
        w.importer.commit(request, preview, mergeChoices = mapOf(first to sms.id))
        assertEquals(1, w.all().size, "السطر التاني ما اتضافش من غير قرار")
        assertEquals(2, w.sources.listByTransactionIds(listOf(sms.id)).size)
    }

    /** نفس السؤال من ناحية الرسالة: الكشف سبق وفيه سطرين ممكن يكونوا هي ⇒ «هي دي» بيدمجها ويشيلها من الصندوق. */
    @Test fun theOwnerPicksWhichStatementLineAnSmsIs() = runBlocking<Unit> {
        val w = MatchingWorld()
        w.import(w.statement("2026-10-01,100.00,0.00,4900.00,TEST CAFE,,,شراء", "2026-10-02,100.00,0.00,4800.00,TEST MART,,,شراء"))
        val before = w.all().associateBy { it.id }
        val first = before.values.single { it.occurredAt == "2026-10-01" }
        w.receive(matchingPurchaseSms("100", "26/10/01"))
        val line = w.review.load(w.target).similar.single()
        assertEquals(2, line.mergeCandidates.size)
        assertEquals(first.id, line.mergeCandidates.first())
        assertEquals(1, w.review.recordAll(emptyMap(), emptyList(), mapOf(line.lineNumber to first.id)))
        assertTrue(w.inbox.sync().messages.isEmpty(), "اتدمجت واتشالت")
        assertEquals(before, w.all().associateBy { it.id }, "ولا عملية جديدة والكشف كسب")
        assertEquals(2, w.sources.listByTransactionIds(listOf(first.id)).size)
    }

    /** التسجيل التلقائي في الخلفية (§72) بيدمج برضه: الكشف سبق ⇒ الرسالة بتتدمج وتتشال من غير ما تتسجل مرتين. */
    @Test fun theBackgroundRecordingMergesAndLeavesTheInbox() = runBlocking<Unit> {
        val space = SmsSpace(window = CROSS_SOURCE_WINDOW_DAYS)
        val world = SmsWorld(listOf(space)).enable()
        val importer = ImportStatement(space.importDeps())
        val content = listOf(MW_HEADER, "2026-10-07,25.00,0.00,975.00,TEST CAFE RIYADH,,,شراء").joinToString("\n")
        val request = ImportRequest("statement.csv", content, BANK.name, ImportSourceType.CSV_LEGACY, walletId = BANK.id)
        importer.commit(request, importer.preview(request))
        val statementTxn = space.all().single()

        world.receive(sms("m1", CAFE))
        val result = world.auto().run()
        assertEquals(1, result.recorded)
        assertEquals(emptyList(), result.waiting)
        assertTrue(world.queued().isEmpty(), "اتدمجت واتشالت من الصندوق")
        assertEquals(listOf(statementTxn), space.all(), "ولا عملية جديدة، والكشف كسب")
        assertEquals(2, space.sources.listByTransactionIds(listOf(statementTxn.id)).size)

        // قهوة تانية بنفس النص بعدها بساعات ⇒ مستنية تأكيد (مش مكررة ولا اتسجلت لوحدها)
        world.receive(sms("m2", CAFE, at = "2026-10-07T18:00:00Z"))
        val second = world.auto().run()
        assertEquals(0, second.recorded)
        assertEquals(0, second.duplicates)
        assertEquals(listOf("m2"), second.waiting)
        assertEquals(1, space.all().size)
    }
}
