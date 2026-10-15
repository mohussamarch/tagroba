package app.masroufy.usecase

import app.masroufy.core.MatchingState
import app.masroufy.core.smsConfirmCandidate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **§83 على تاريخ طويل** (المالك: «ده لازم يتختبر على تاريخ طويل قبل ما يشتغل على حساب المالك، عشان الكشف متستورد من 2025-01-01»):
 * 18 شهر من الرسايل المخترعة + كشف مخترع لنفس العمليات متستورد **قبلها** (زي حساب المالك). «اقرأ اللي فات؟» ⇒ «من رصيد البداية» ⇒ «أكّد
 * الأشكال» ⇒ **ولا عملية اتكررت والرصيد زي ما هو بالظبط** — كل رسالة اتدمجت في سطر الكشف بتاعها (عملية واحدة بمصدرين — §75-10)، و«التوأم»
 * (نفس المبلغ يومين ورا بعض) بيستنى «شبه عملية» ومش بيتسجل. وتاني قراية لنفس الرسايل ما بتسجلش حاجة.
 */
class SmsHistoryLongTest {
    @Test fun eighteenMonthsOfSmsOverAnImportedStatementMergeAndNothingDoubles() = runBlocking<Unit> {
        // OVERRIDES §91-1: الحوالات الواردة «إلى:1111» بتتسجل لوحدها بس لما محفظة البنك عليها الأرقام دي
        val w = HistoryWorld(space = digitsSpace())
        val txns = eighteenMonths()
        val twins = txns.filter { it.id.endsWith("twin") }.flatMap { twin -> listOf(twin.id, twin.id.removeSuffix("twin")) }.toSet()
        assertTrue(txns.size > 550 && twins.size >= 22, "${txns.size} transactions, ${twins.size} twins")
        // الكشف الأول (زي حساب المالك) ⇒ رصيد ونهاية مرجعية
        w.importStatement(w.statement(txns))
        val before = w.space.all()
        assertEquals(txns.size, before.size)
        val closing = w.closing("2026-07-02")
        assertEquals(emptyList(), w.mismatches("2026-07-02"))

        // الجوال عليه رسايل الـ18 شهر + 5 رسايل من ديسمبر 2024 (قبل رصيد البداية)
        val december = (1..5).map { HistoryTxn("old$it", "2024-12-2$it", 4_000L + it, false, "TEST CAFE", 0) }
        w.memory.deliver(*(december + txns).map { it.sms }.toTypedArray())
        w.memory.enable(listOf("TESTBANK"))
        val history = w.history()

        val q = assertNotNull(history.question("sa"), "first link ⇒ «اقرأ اللي فات؟»")
        assertEquals(listOf("TESTBANK") to "2024-12-21", q.senders to q.oldestDay)
        assertEquals(SmsOpening(HISTORY_BANK.name, "2025-01-01"), q.opening)
        assertTrue(q.askStart, "older than the opening balance ⇒ the owner picks the start (§83 «يسألني أنا من إمتى»)")

        // «من رصيد البداية» ⇒ كل رسايل البنك من 2025-01-01: ولا واحدة بتتسجل لوحدها قبل ما شكلها يتأكد (§77-A)
        val first = history.start("sa", q.senders, "2025-01-01")
        assertEquals(0, first.recorded)
        assertEquals(txns.size, first.queued, "the whole history is in the inbox — December is not")
        assertEquals(txns.size, w.space.all().size, "nothing recorded before the owner confirms a shape")
        // قايمة واحدة: رسالة واحدة من كل شكل (الشراء · الحوالة الواردة) — مش 600 سؤال
        assertEquals(2, first.shapes.size, first.shapes.toString())
        assertTrue(first.shapes.sumOf { it.count } > 150, "each line stands for many messages of its shape")
        assertNotNull(smsConfirmCandidate(w.deps().let { AutoRecordSms(it).waiting().messageIds }), "one alert candidate for all of them")
        assertNull(history.question("sa"), "the question does not come back")

        // «أكّد الأشكال» ⇒ الباقي بنفس الأشكال بيتسجل لوحده — ويتدمج في سطور الكشف
        var report = history.confirmShapes(first.shapeMessageIds)
        var rounds = 0
        while (report.shapes.isNotEmpty() && rounds++ < 5) report = history.confirmShapes(report.shapeMessageIds)
        val after = w.space.all()
        assertEquals(before.size, after.size, "not one transaction doubled")
        assertEquals(before.map { it.id }.toSet(), after.map { it.id }.toSet())
        assertEquals(closing, w.closing("2026-07-02"), "the balance is exactly the same")
        assertEquals(emptyList(), w.mismatches("2026-07-02"), "and the statement balance chain still matches line by line")
        // كل عملية مش «توأم» = مصدرين (الكشف والرسالة)، والتوأم بيستنى «شبه عملية» في الصندوق
        val merged = w.space.sources.listByTransactionIds(after.map { it.id }).groupBy { it.transactionId }
        val single = after.filter { (merged[it.id]?.size ?: 0) < 2 }
        assertEquals(twins.size, single.size, "only the twins stay with one source")
        val waiting = w.memory.sync().messages.map { it.id }.toSet()
        assertEquals(twins, waiting, "the twins wait as «similar» — never recorded twice")
        val screen = w.space.screen(w.memory)
        val review = screen.load(SmsReviewTarget(HISTORY_BANK.id, HISTORY_BANK.name))
        assertEquals(twins.size, review.similar.count { it.state == MatchingState.SIMILAR })

        // نفس الرسايل تاني (البرنامج اتمسح واتثبت والمالك قال «أيوه» تاني) ⇒ ولا حاجة جديدة
        val again = HistoryWorld(memory = w.memory.reinstall(), settings = app.masroufy.memory.MemoryUserSettingsStore(), space = w.space)
        again.memory.enable(listOf("TESTBANK"))
        val h2 = again.history()
        val q2 = assertNotNull(h2.question("sa"))
        var r2 = h2.start("sa", q2.senders, "2025-01-01")
        rounds = 0
        while (r2.shapes.isNotEmpty() && rounds++ < 5) r2 = h2.confirmShapes(r2.shapeMessageIds)
        assertEquals(before.size, w.space.all().size, "reading the same messages twice records nothing twice")
        assertEquals(closing, w.closing("2026-07-02"))
        assertTrue(r2.duplicates >= txns.size - twins.size, "${r2.duplicates} duplicates removed")
    }
}
