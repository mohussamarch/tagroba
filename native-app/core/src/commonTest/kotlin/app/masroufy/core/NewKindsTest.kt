package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * الأنواع الواردة الجديدة — قرارات المالك 2026-09-20 (OVERRIDES §42).
 * **مفيش ملف مرجع ليها**: التطبيق الحالي مفيهوش الأنواع دي، فمفيش حاجة يتطابق معاها.
 * الضمان هنا اختبارات مكتوبة بالإيد على المعنى المحاسبي.
 */
class NewKindsTest {
    private fun txn(kind: EconomicKind, amount: Halalas, excluded: Boolean = false) = Transaction(
        id = "t-${kind.wire}-$amount",
        occurredAt = "2026-09-30",
        datePrecision = "day",
        sourceOrder = 0,
        economicKind = kind,
        economicKindConfirmed = true,
        observedDirection = if (ruleFor(kind).liquidity == Liquidity.OUT) Direction.OUT else Direction.IN,
        amountMinor = amount,
        currency = Currency.SAR,
        categoryConfirmed = false,
        excludedFromBudget = excluded,
        reviewState = ReviewState.CONFIRMED,
        isCashTagged = false,
        createdAt = "x",
        updatedAt = "x",
    )

    @Test
    fun `الهدية والمساعدة والدعم وأرباح الاستثمار بتزود الدخل`() {
        for (kind in listOf(EconomicKind.GIFT_RECEIVED, EconomicKind.SUPPORT_RECEIVED, EconomicKind.BENEFIT_RECEIVED, EconomicKind.INVESTMENT_INCOME)) {
            assertTrue(countsAsIncome(kind), "${kind.wire} المفروض يزود الدخل")
            val totals = computePeriodTotals(listOf(txn(kind, 50_000)), emptyList())
            assertEquals(50_000, totals.incomeMinor, kind.wire)
            assertEquals(0, totals.personalExpenseMinor, kind.wire)
        }
    }

    @Test
    fun `سلفة الشغل بتدخل الحساب من غير ما تزود الدخل`() {
        val totals = computePeriodTotals(listOf(txn(EconomicKind.ADVANCE_RECEIVED, 300_000)), emptyList())
        assertEquals(0, totals.incomeMinor)
        assertEquals(0, totals.personalExpenseMinor)
        // السلفة دين على المستخدم زي القرض
        assertEquals(PersonEffect.PAYABLE_LOAN_UP, ruleFor(EconomicKind.ADVANCE_RECEIVED).personEffect)
        assertEquals(PersonEffect.NONE, ruleFor(EconomicKind.ROSCA_PAYOUT).personEffect)
    }

    @Test
    fun `الاسترداد بينقّص المصروف مش بيزود الدخل`() {
        val totals = computePeriodTotals(
            listOf(txn(EconomicKind.PURCHASE, 30_000), txn(EconomicKind.REFUND_RECEIVED, 12_000)),
            emptyList(),
        )
        assertEquals(0, totals.incomeMinor)
        assertEquals(18_000, totals.personalExpenseMinor)
        assertEquals(-18_000, totals.remainingMinor)
    }

    @Test
    fun `استرداد أكبر من المصروف بيطلع سالب مش صفر`() {
        // الرقم الحقيقي أولى من رقم مريح — CLAUDE.md #10
        val totals = computePeriodTotals(
            listOf(txn(EconomicKind.PURCHASE, 10_000), txn(EconomicKind.REFUND_RECEIVED, 25_000)),
            emptyList(),
        )
        assertEquals(-15_000, totals.personalExpenseMinor)
    }

    @Test
    fun `استرداد عملية مستبعدة من الميزانية بينقّص المستبعد`() {
        val totals = computePeriodTotals(
            listOf(
                txn(EconomicKind.PURCHASE, 40_000, excluded = true),
                txn(EconomicKind.REFUND_RECEIVED, 15_000, excluded = true),
                txn(EconomicKind.PURCHASE, 5_000),
            ),
            emptyList(),
        )
        assertEquals(25_000, totals.excludedExpenseMinor)
        assertEquals(5_000, totals.personalExpenseMinor)
    }

    @Test
    fun `النقوط دخل بس نوعه لوحده عشان يتميز`() {
        // قرار المالك §44: يتحسب دخل، بس يفضل نوع مستقل عشان ما يخربطش متوسط الدخل الشهري
        assertTrue(countsAsIncome(EconomicKind.EVENT_GIFT))
        assertTrue(EconomicKind.EVENT_GIFT != EconomicKind.GIFT_RECEIVED)
        val totals = computePeriodTotals(listOf(txn(EconomicKind.EVENT_GIFT, 1_500_000)), emptyList())
        assertEquals(1_500_000, totals.incomeMinor)
    }

    @Test
    fun `مكافأة نهاية الخدمة دخل وارد بس ومرة واحدة`() {
        // رد المالك §64-٧ — اختيار Claude: نوع لوحده، دخل، وبرا متوسطات الدخل زي النقوط
        assertTrue(countsAsIncome(EconomicKind.END_OF_SERVICE))
        assertEquals(Liquidity.IN, ruleFor(EconomicKind.END_OF_SERVICE).liquidity)
        assertTrue(isConsistentWithObservedDirection(EconomicKind.END_OF_SERVICE, Direction.IN))
        assertTrue(!isConsistentWithObservedDirection(EconomicKind.END_OF_SERVICE, Direction.OUT), "صادر ما ينفعش يبقى مكافأة")
        assertEquals(setOf(EconomicKind.EVENT_GIFT, EconomicKind.END_OF_SERVICE), NOT_IN_INCOME_AVERAGES)
        val totals = computePeriodTotals(listOf(txn(EconomicKind.END_OF_SERVICE, 4_000_000)), emptyList())
        assertEquals(4_000_000, totals.incomeMinor, "في شهره دخل")
        assertEquals(0, totals.personalExpenseMinor)
        assertEquals(PersonEffect.NONE, ruleFor(EconomicKind.END_OF_SERVICE).personEffect)
        try {
            Texts.language = Language.EN
            assertEquals("End-of-service benefit", ruleFor(EconomicKind.END_OF_SERVICE).label)
            Texts.language = Language.AR
            assertEquals("مكافأة نهاية خدمة", ruleFor(EconomicKind.END_OF_SERVICE).label)
        } finally {
            Texts.language = Language.AR
        }
    }

    @Test
    fun `الأنواع الجديدة كلها ليها اسم معروض بالعربي وبالإنجليزي`() {
        val added = listOf(
            EconomicKind.GIFT_RECEIVED, EconomicKind.SUPPORT_RECEIVED, EconomicKind.BENEFIT_RECEIVED,
            EconomicKind.INVESTMENT_INCOME, EconomicKind.ROSCA_PAYOUT, EconomicKind.REFUND_RECEIVED,
            EconomicKind.ADVANCE_RECEIVED, EconomicKind.EVENT_GIFT, EconomicKind.END_OF_SERVICE,
        )
        for (kind in added) {
            assertTrue(ruleFor(kind).label.isNotBlank(), kind.wire)
            assertEquals(kind, EconomicKind.fromWire(kind.wire))
        }
        // الاسترداد هو الوحيد اللي بينقّص المصروف
        assertEquals(listOf(EconomicKind.REFUND_RECEIVED), ALL_ECONOMIC_KINDS.filter { reducesExpense(it) })
    }
}
