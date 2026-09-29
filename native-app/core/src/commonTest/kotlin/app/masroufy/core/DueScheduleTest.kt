package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * جدول الأقساط وقايمة «المستحقات» — OVERRIDES §50. **مفيش ملف مرجع**: الميزة جديدة في كوتلن بس
 * (التطبيق الحالي مفيهوش جمعيات ولا أقساط)، فالضمان اختبارات مكتوبة بالإيد. الأرقام وهمية.
 */
class DueScheduleTest {
    // 4 أقساط: 1000 + 1000 + 1000 + 500
    private val s = DueSchedule("2026-01-15", 1, 100_000, 350_000)

    @Test
    fun `آخر قسط هو الباقي`() {
        assertEquals(4, installmentCount(s))
        assertEquals(100_000, installmentAmountOf(s, 1))
        assertEquals(50_000, installmentAmountOf(s, 4))
    }

    @Test
    fun `يوم 31 بيبقى آخر الشهر ويرجع 31 في الشهر اللي بعده`() {
        val end = DueSchedule("2026-01-31", 1, 100_000, 300_000)
        assertEquals("2026-02-28", dueDateOf(end, 2))
        assertEquals("2026-03-31", dueDateOf(end, 3))
    }

    @Test
    fun `لسه ما دفعش حاجة`() {
        val p = dueProgress(s, 0, "2026-01-10")
        assertEquals(1, p.nextNumber)
        assertEquals("2026-01-15", p.nextDueAt)
        assertEquals(100_000, p.nextAmountMinor)
        assertEquals(0, p.overdueCount)
        assertEquals(350_000, p.remainingMinor)
    }

    @Test
    fun `الدفعة الناقصة بتتحسب من القسط اللي بعدها والمتأخر بيتعد`() {
        // دفع 1500: القسط الأول كامل ونص التاني. النهارده 20 مارس ⇒ قسط فبراير ومارس متأخرين
        val p = dueProgress(s, 150_000, "2026-03-20")
        assertEquals(1, p.paidCount)
        assertEquals(2, p.nextNumber)
        assertEquals("2026-02-15", p.nextDueAt)
        assertEquals(50_000, p.nextAmountMinor)
        assertEquals(2, p.overdueCount)
        assertEquals(150_000, p.overdueMinor)
    }

    @Test
    fun `خلص`() {
        val p = dueProgress(s, 350_000, "2026-09-30")
        assertTrue(p.done)
        assertNull(p.nextNumber)
        assertEquals(4, p.paidCount)
    }

    @Test
    fun `المدفوع أكبر من الإجمالي خطأ مش صفر`() {
        assertFailsWith<DueScheduleError> { dueProgress(s, 360_000, "2026-09-30") }
        assertFailsWith<DueScheduleError> { dueProgress(s, -1, "2026-09-30") }
    }

    @Test
    fun `الجدول الغلط بيترفض`() {
        assertFailsWith<DueScheduleError> { checkDueSchedule(s.copy(cycleMonths = 0)) }
        assertFailsWith<DueScheduleError> { checkDueSchedule(s.copy(installmentMinor = 0)) }
        assertFailsWith<DueScheduleError> { checkDueSchedule(s.copy(firstDueAt = "2026-02-30")) }
        // 361 قسط
        assertFailsWith<DueScheduleError> { checkDueSchedule(s.copy(installmentMinor = 1, totalMinor = 361)) }
    }

    @Test
    fun `اللي عليك لحد تاريخ معين — ولو مفيش يرجع الجاي بس`() {
        assertEquals(listOf("2026-02-15" to 50_000L, "2026-03-15" to 100_000L), unpaidInstallments(s, 150_000, "2026-03-31"))
        assertEquals(listOf("2026-01-15" to 100_000L), unpaidInstallments(s, 0, "2026-01-01"))
        assertEquals(emptyList(), unpaidInstallments(s, 350_000, "2026-12-31"))
    }

    @Test
    fun `الحالة من الميعاد`() {
        assertEquals(DueStatus.OVERDUE, dueStatusOf("2026-09-29", "2026-09-30"))
        assertEquals(DueStatus.SOON, dueStatusOf("2026-09-30", "2026-09-30"))
        assertEquals(DueStatus.SOON, dueStatusOf("2026-10-03", "2026-09-30"))
        assertEquals(DueStatus.UPCOMING, dueStatusOf("2026-10-04", "2026-09-30"))
    }

    private fun recurring(active: Boolean = true) = RecurringItem(
        "r-1", "اشتراك وهمي", "manual:x", "subscription", 1, 4_500, Currency.SAR, "2026-01-05", active, true,
    )

    @Test
    fun `الاشتراك بيتكرر لحد التاريخ والموقوف ما بيظهرش`() {
        assertEquals(listOf("2026-01-05", "2026-02-05", "2026-03-05"), recurringDueItems(recurring(), "2026-01-01", "2026-03-10").map { it.dueAt })
        assertEquals(1, recurringDueItems(recurring(), "2026-01-01", "2026-01-01").size)
        assertEquals(emptyList(), recurringDueItems(recurring(active = false), "2026-01-01", "2026-03-10"))
    }

    @Test
    fun `الدين اللي ليك بتستلمه واللي عليك بتدفعه`() {
        val lent = Obligation("o-1", "p-1", null, ObligationKind.RECEIVABLE, 100_000, Currency.SAR)
        val terms = DebtTerms("o-1", "p-1", "2026-02-01", installmentMinor = 50_000)
        val paid = listOf(Settlement("s-1", "t-9", "o-1", 50_000))
        val items = debtDueItems(terms, lent, paid, "فلان", "2026-01-20", "2026-01-31")
        assertEquals(listOf(DueFlow.RECEIVE), items.map { it.flow })
        assertEquals("2026-03-01", items.single().dueAt)
        assertEquals(50_000, items.single().amountMinor)

        val borrowed = lent.copy(kind = ObligationKind.LOAN_PAYABLE)
        // من غير قسط = دفعة واحدة بالمبلغ كله
        val single = debtDueItems(terms.copy(installmentMinor = null), borrowed, emptyList(), "فلان", "2026-01-20", "2026-01-31")
        assertEquals(listOf(DueFlow.PAY to 100_000L), single.map { it.flow to it.amountMinor })
    }

    private fun due(date: String, amount: Halalas, flow: DueFlow = DueFlow.PAY) =
        DueItem(DueSource.RECURRING, "x-$date", "وهمي", date, amount, Currency.SAR, flow, DueStatus.UPCOMING)

    @Test
    fun `سطر الشهر — المتأخر بيتحسب واللي بعد الشهر لأ واللي هتستلمه لوحده`() {
        val period = Period("2026-01", "2026-01-01", "2026-01-31", 31)
        val items = listOf(
            due("2025-12-20", 10_000), due("2026-01-10", 20_000), due("2026-02-01", 99_999),
            due("2026-01-15", 70_000, DueFlow.RECEIVE), due("2026-01-12", 5_000).copy(currency = Currency.EGP),
        )
        val line = duesMonthLine(items, period, Currency.SAR, 500_000)
        assertEquals(30_000, line.toPayMinor)
        assertEquals(70_000, line.toReceiveMinor)
        assertEquals(470_000, line.remainingAfterMinor)
        assertEquals(2, line.payCount)
        // «المتبقي» مجهول ⇒ اللي بعده مجهول، مش رقم مخترع
        assertNull(duesMonthLine(items, period, Currency.SAR, null).remainingAfterMinor)
    }

    @Test
    fun `في نفس اليوم اللي هتدفعه قبل اللي هتستلمه`() {
        val sorted = sortDues(listOf(due("2026-01-10", 1, DueFlow.RECEIVE), due("2026-01-10", 2), due("2026-01-01", 3)))
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.amountMinor })
    }
}
