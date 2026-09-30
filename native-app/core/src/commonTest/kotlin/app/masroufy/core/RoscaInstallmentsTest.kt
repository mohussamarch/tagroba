package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الجمعية والأقساط — قرارات المالك 2026-09-30 (OVERRIDES §50). مكتوبة بالإيد؛ الأرقام وهمية.
 */
class RoscaInstallmentsTest {
    // 10 أدوار، قسطي 1000 شهري، دوري الرابع ⇒ هقبض 10,000
    private val rosca = Rosca(
        id = "rc-1", name = "جمعية وهمية", currency = Currency.SAR, contributionMinor = 100_000, every = 1,
        firstDueAt = "2026-01-01", cycleCount = 10, myTurns = listOf(4), payoutMinor = 1_000_000,
    )

    private fun pay(n: Int) = (1..n).map { RoscaEntry("e-c$it", "rc-1", "t-c$it", RoscaEntryKind.CONTRIBUTION, 100_000) }
    private val payout = RoscaEntry("e-p1", "rc-1", "t-p1", RoscaEntryKind.PAYOUT, 1_000_000)

    @Test
    fun `قيمة الدور الافتراضية — ولو ما بتتقسمش المستخدم يكتبها`() {
        assertEquals(1_000_000, defaultRoscaPayout(100_000, 10, 1))
        // نص سهم
        assertEquals(500_000, defaultRoscaPayout(50_000, 10, 1))
        // سهمين = دورين
        assertEquals(1_000_000, defaultRoscaPayout(200_000, 10, 2))
        assertNull(defaultRoscaPayout(100_000, 10, 3))
    }

    @Test
    fun `قبل دورك القسط ادخار — الجمعية شايلالك`() {
        val st = roscaStatus(rosca, pay(3), "2026-03-15")
        assertEquals(RoscaPhase.SAVING, st.phase)
        assertEquals(300_000, st.positionMinor)
        assertEquals(PayoutState.EXPECTED, st.payouts.single().state)
        assertEquals("2026-04-01", st.payouts.single().dueAt)
        assertEquals("2026-04-01", st.contributions.nextDueAt)
    }

    @Test
    fun `بعد ما تقبض عليك للجمعية الباقي`() {
        val st = roscaStatus(rosca, pay(4) + payout, "2026-04-02")
        assertEquals(RoscaPhase.REPAYING, st.phase)
        assertEquals(-600_000, st.positionMinor)
        assertEquals(PayoutState.RECEIVED, st.payouts.single().state)
    }

    @Test
    fun `في الآخر الموقف صفر`() {
        val st = roscaStatus(rosca, pay(10) + payout, "2026-10-02")
        assertEquals(RoscaPhase.DONE, st.phase)
        assertEquals(0, st.positionMinor)
        assertEquals(0, st.gainMinor)
    }

    @Test
    fun `الدور اللي ميعاده فات ولسه ما اتقبضش متأخر`() {
        assertEquals(PayoutState.LATE, roscaStatus(rosca, pay(4), "2026-04-05").payouts.single().state)
    }

    @Test
    fun `جمعية فيها رسوم بتبان كخسارة مش بتختفي`() {
        assertEquals(-50_000, roscaStatus(rosca.copy(payoutMinor = 950_000), emptyList(), "2026-01-01").gainMinor)
    }

    @Test
    fun `الزيادة بترفض بسبب واضح`() {
        val msg = checkRoscaEntry(rosca, pay(9), RoscaEntryKind.CONTRIBUTION, 200_000)
        assertEquals(uiText(TextKey.ROSCA_CONTRIBUTION_OVER, formatMoney(100_000), formatMoney(100_000)), msg)
        assertNull(checkRoscaEntry(rosca, pay(9), RoscaEntryKind.CONTRIBUTION, 100_000))
        assertTrue(checkRoscaEntry(rosca, listOf(payout), RoscaEntryKind.PAYOUT, 1) != null)
        assertEquals(uiText(TextKey.DUE_AMOUNT_POSITIVE), checkRoscaEntry(rosca, emptyList(), RoscaEntryKind.PAYOUT, 0))
    }

    @Test
    fun `الجمعية الغلط بتترفض`() {
        assertFailsWith<RoscaError> { checkRosca(rosca.copy(myTurns = listOf(4, 4))) }
        assertFailsWith<RoscaError> { checkRosca(rosca.copy(myTurns = listOf(11))) }
        // دور لسه ما اتحددش مسموح (قيمة الدور صفر)، بس قيمة دور بالسالب لأ
        checkRosca(rosca.copy(myTurns = emptyList(), payoutMinor = 0))
        assertFailsWith<RoscaError> { checkRosca(rosca.copy(myTurns = emptyList(), payoutMinor = -1)) }
        assertFailsWith<RoscaError> { checkRosca(rosca.copy(cycleCount = 1)) }
        assertFailsWith<RoscaError> { checkRosca(rosca.copy(name = "  ")) }
        assertFailsWith<RoscaError> { checkRosca(rosca.copy(members = listOf(RoscaMember(12, "عضو وهمي")))) }
        assertEquals("جمعية وهمية", checkRosca(rosca.copy(name = "  جمعية   وهمية ")).name)
    }

    @Test
    fun `بنود الجمعية في المستحقات — أقساط هتدفعها ودور هتستلمه`() {
        val items = roscaDueItems(rosca, pay(2), "2026-03-02", "2026-04-30")
        assertEquals(
            listOf(
                Triple(DueSource.ROSCA_CONTRIBUTION, "2026-03-01", DueStatus.OVERDUE),
                Triple(DueSource.ROSCA_CONTRIBUTION, "2026-04-01", DueStatus.UPCOMING),
                Triple(DueSource.ROSCA_PAYOUT, "2026-04-01", DueStatus.UPCOMING),
            ),
            items.map { Triple(it.source, it.dueAt, it.status) },
        )
        assertEquals(DueFlow.RECEIVE, items.last().flow)
    }

    // تمويل: استلمت 10,000 وهترجع 12,000 على 12 شهر ⇒ تكلفة 2,000
    private val loan = InstallmentPlan(
        id = "ip-1", name = "تمويل وهمي", provider = "بنك وهمي", kind = InstallmentKind.FINANCING, currency = Currency.SAR,
        principalMinor = 1_000_000, totalMinor = 1_200_000, installmentMinor = 100_000, cycleMonths = 1, firstDueAt = "2026-01-10",
    )

    @Test
    fun `تكلفة التمويل بتتوزع على الأقساط ومجموعها بالظبط بالهللة`() {
        assertEquals(200_000, financingCostOf(loan))
        assertEquals(16_667, financingCostPaidThrough(loan, 100_000))
        val parts = (1..12).map { financingCostPaidThrough(loan, it * 100_000L) - financingCostPaidThrough(loan, (it - 1) * 100_000L) }
        assertEquals(200_000, parts.sum())
    }

    @Test
    fun `مصروف التمويل في الشهر = جزء التكلفة من اللي اتدفع فيه بس`() {
        val payments = (1..3).map { InstallmentPayment("ip-p$it", "ip-1", "t-i$it", 100_000) }
        val dates = mapOf("ip-p1" to "2026-01-10", "ip-p2" to "2026-02-10", "ip-p3" to "2026-03-10")
        val feb = Period("2026-02", "2026-02-01", "2026-02-28", 28)
        assertEquals(financingCostPaidThrough(loan, 200_000) - financingCostPaidThrough(loan, 100_000), financingCostInPeriod(loan, payments, dates, feb))
        // تقسيط المشتريات: القسط كله مصروف بنوعه، فمفيش تكلفة زيادة تتحسب مرتين
        assertEquals(0, financingCostInPeriod(loan.copy(kind = InstallmentKind.PURCHASE_PLAN), payments, dates, feb))
    }

    @Test
    fun `نوع العملية المربوطة`() {
        assertEquals(EconomicKind.INSTALLMENT_PAID, installmentPaymentKind(loan))
        assertEquals(EconomicKind.PURCHASE, installmentPaymentKind(loan.copy(kind = InstallmentKind.PURCHASE_PLAN)))
    }

    @Test
    fun `قسط الجمعية وقسط التمويل مش مصروف ومش دخل`() {
        for (kind in listOf(EconomicKind.ROSCA_CONTRIBUTION, EconomicKind.INSTALLMENT_PAID)) {
            assertEquals(Liquidity.OUT, ruleFor(kind).liquidity)
            assertTrue(!countsAsIncome(kind) && !countsAsPersonalExpense(kind), kind.wire)
        }
    }

    @Test
    fun `الإجمالي أقل من الأصل بيترفض`() {
        assertFailsWith<InstallmentError> { checkInstallmentPlan(loan.copy(totalMinor = 900_000)) }
        assertFailsWith<InstallmentError> { checkInstallmentPlan(loan.copy(name = "")) }
    }

    @Test
    fun `الأرصدة — موقف الجمعية الموجب ليك والسالب عليك ومفيش تقاص`() {
        val saving = roscaStatus(rosca, pay(3), "2026-03-15")
        val repaying = roscaStatus(rosca, pay(4) + payout, "2026-04-02")
        val totals = duesTotals(listOf(PersonBalance("p-1", 20_000, 30_000, 5_000)), listOf(saving, repaying), listOf(700_000))
        assertEquals(DuesTotals(20_000, 300_000, 30_000, 5_000, 700_000, 600_000), totals)
    }
}
