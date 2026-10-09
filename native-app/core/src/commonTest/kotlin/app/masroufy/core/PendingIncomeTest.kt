package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * قرار المالك §75-1: الداخل اللي نوعه مش معروف **«مستني» برّه الدخل** لحد ما يتأكد — بيلغي تخمين «الداخل = راتب» (§18) لغير الراتب
 * المعروف. السياسة القديمة ([EstimatePolicy.LEGACY]) بتفضل زي التطبيق القديم بالظبط (ملفات المرجع). كل المبالغ مخترعة.
 */
class PendingIncomeTest {
    @AfterTest
    fun reset() {
        EstimatePolicy.current = EstimatePolicy.OWNER_2026_10
    }

    private var seq = 0
    private val owner = EstimatePolicy.OWNER_2026_10
    private val legacy = EstimatePolicy.LEGACY

    private fun t(
        dir: Direction,
        minor: Long,
        categoryId: String? = null,
        sourceCategory: String? = null,
        kind: EconomicKind = EconomicKind.UNCLASSIFIED,
        confirmed: Boolean = false,
        currency: Currency = Currency.SAR,
    ) = Transaction(
        id = "t-${seq++}", occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = dir, amountMinor = minor, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = categoryId,
        sourceCategory = sourceCategory, rawMerchantName = "TEST PAYER",
    )

    private val names = mapOf(
        "c-salary" to "راتب", "c-refund" to "استرداد", "c-invest" to "استثمار", "c-food" to "مطاعم وقهوة", "c-gold" to "ذهب",
        "c-transfers" to "تحويلات", "c-fees" to "رسوم بنكية", "c-cash" to "سحب نقدي",
    )

    @Test fun unknownIncomingWaitsOutsideIncome() {
        val incoming = t(IN, 150_000)
        val food = t(OUT, 20_000, "c-food")
        val view = withEstimatedKinds(listOf(incoming, food), names, owner)
        assertEquals(EconomicKind.UNCLASSIFIED, view.transactions[0].economicKind, "بيفضل «غير محدد» في العرض المعدود")
        val totals = computePeriodTotals(view.transactions, emptyList())
        assertEquals(0, totals.incomeMinor, "مش دخل")
        assertEquals(20_000, totals.personalExpenseMinor, "ولا بينقّص المصروف")
        assertEquals(1, view.pendingIncomingCount)
        assertEquals(150_000, view.pendingIncomingMinor(Currency.SAR))
        assertEquals(0, view.pendingIncomingMinor(Currency.EGP), "مفيش مستني بالجنيه")
        assertEquals(listOf(incoming.id), view.pendingIncomingIds)
        assertEquals(listOf(food.id), view.withoutPendingIncoming.map { it.id }, "فحص «الصرف معروف؟» من غير المستني")
    }

    @Test fun legacyKeepsTheOldGuess() {
        val view = withEstimatedKinds(listOf(t(IN, 150_000), t(OUT, 20_000, "c-food")), names, legacy)
        assertEquals(EconomicKind.SALARY, view.transactions[0].economicKind, "التطبيق القديم: الوارد دخل")
        assertEquals(150_000, computePeriodTotals(view.transactions, emptyList()).incomeMinor)
        assertEquals(0, view.pendingIncomingCount)
        assertEquals(emptyMap(), view.pendingIncomingByCurrency)
        assertEquals(view.transactions, view.withoutPendingIncoming)
    }

    @Test fun aRecognisedSalaryIsStillIncome() {
        // عمود «رواتب» من الكشف · تصنيف «راتب» في التطبيق · نوع متخزن (المالك أو قاعدة الجهة أو جواب «ده راتبك؟»)
        val fromStatement = t(IN, 900_000, sourceCategory = "رواتب")
        val fromCategory = t(IN, 800_000, "c-salary")
        val stored = t(IN, 700_000, kind = EconomicKind.SALARY, confirmed = true)
        val view = withEstimatedKinds(listOf(fromStatement, fromCategory, stored), names, owner)
        assertEquals(List(3) { EconomicKind.SALARY }, view.transactions.map { it.economicKind })
        assertEquals(2_400_000, computePeriodTotals(view.transactions, emptyList()).incomeMinor)
        assertEquals(0, view.pendingIncomingCount)
        assertEquals(2, view.needsReviewCount, "المقدَّر لسه محتاج تأكيد — بس بيتحسب")
    }

    @Test fun refundAndMediumGuessesWaitToo() {
        // «استرداد» مقترح (§75-6 — بيستنى التأكيد) · «استثمار» = بيع أصل بثقة متوسطة · «تحويلات» = مش واضح
        val refund = t(IN, 5_000, "c-refund")
        val sale = t(IN, 300_000, "c-invest")
        val transfer = t(IN, 50_000, "c-transfers")
        val view = withEstimatedKinds(listOf(refund, sale, transfer), names, owner)
        assertEquals(listOf(refund.id, sale.id, transfer.id), view.pendingIncomingIds)
        assertEquals(355_000, view.pendingIncomingMinor(Currency.SAR))
        assertTrue(view.transactions.all { it.economicKind == EconomicKind.UNCLASSIFIED })
        assertEquals(EconomicKind.ASSET_SELL, withEstimatedKinds(listOf(sale), names, legacy).transactions.single().economicKind, "القديم: المقترح")
    }

    @Test fun outgoingEstimationIsUnchanged() {
        val outs = listOf(
            t(OUT, 1_000), t(OUT, 2_000, "c-food"), t(OUT, 3_000, "c-gold"), t(OUT, 4_000, "c-transfers"), t(OUT, 5_000, "c-fees"),
            t(OUT, 6_000, "c-cash"), t(OUT, 7_000, sourceCategory = "ذهب"), t(OUT, 8_000, kind = EconomicKind.SUPPORT_GIFT, confirmed = true),
        )
        val now = withEstimatedKinds(outs, names, owner)
        val before = withEstimatedKinds(outs, names, legacy)
        assertEquals(before, now, "الصادر ما اتغيرش في أي حاجة")
        assertEquals(
            listOf(
                EconomicKind.PURCHASE, EconomicKind.PURCHASE, EconomicKind.ASSET_BUY, EconomicKind.PURCHASE, EconomicKind.FEE,
                EconomicKind.INTERNAL_TRANSFER, EconomicKind.PURCHASE, EconomicKind.SUPPORT_GIFT,
            ),
            now.transactions.map { it.economicKind },
        )
    }

    @Test fun confirmedOrClassifiedIncomingIsNeverPending() {
        val confirmedUnknown = t(IN, 10_000, confirmed = true)
        val loan = t(IN, 20_000, kind = EconomicKind.LOAN_RECEIVED)
        val view = withEstimatedKinds(listOf(confirmedUnknown, loan), names, owner)
        assertEquals(0, view.pendingIncomingCount, "النوع المتخزن أو اللي المالك قفله ما بيتلمسش")
        assertEquals(listOf(EconomicKind.UNCLASSIFIED, EconomicKind.LOAN_RECEIVED), view.transactions.map { it.economicKind })
    }

    @Test fun pendingAmountsStaySeparatePerCurrency() {
        val view = withEstimatedKinds(listOf(t(IN, 150_000), t(IN, 500_000, currency = Currency.EGP), t(IN, 10_000)), names, owner)
        assertEquals(mapOf(Currency.SAR to 160_000L, Currency.EGP to 500_000L), view.pendingIncomingByCurrency, "ما بيتجمعش بين عملتين")
        assertEquals(3, view.pendingIncomingCount)
    }

    @Test fun reviewCountsAreTheSameUnderBothPolicies() {
        val mixed = listOf(t(IN, 1_000), t(IN, 2_000, "c-salary"), t(OUT, 3_000), t(OUT, 4_000, "c-food"), t(IN, 5_000, kind = EconomicKind.SALARY, confirmed = true))
        val now = withEstimatedKinds(mixed, names, owner)
        val before = withEstimatedKinds(mixed, names, legacy)
        assertEquals(before.estimatedCount to before.needsReviewCount, now.estimatedCount to now.needsReviewCount)
        assertEquals(4 to 3, now.estimatedCount to now.needsReviewCount, "المستني جوه «محتاجة تأكيد»")
    }

    /**
     * مراجعة S6: مشروع شغل جاله 5,000 داخل لسه ما اتأكدش + صرف 1,000. «اللي جالك» من غير المستني (زي الدخل في الرئيسية)، بس الملخص
     * بيقول إن فيه داخل مستني ومبلغه — عشان الشاشة ما تعرضش «خسران 1,000» من غير سبب.
     */
    @Test fun projectSummaryShowsItsPendingIncoming() {
        val paid = t(IN, 500_000)
        val cost = t(OUT, 100_000, kind = EconomicKind.PURCHASE, confirmed = true)
        EstimatePolicy.current = owner
        val now = summarizeProject(listOf(paid, cost), emptyList(), names)
        assertEquals(0L to -100_000L, now.receivedMinor to projectNetMinor(now), "الصافي «لحد دلوقتي»")
        assertEquals(1, now.pendingIncomingCount)
        assertEquals(mapOf(Currency.SAR to 500_000L), now.pendingIncomingMinor)
        assertEquals(listOf(paid.id), now.pendingIncomingIds)
        EstimatePolicy.current = legacy
        val old = summarizeProject(listOf(paid, cost), emptyList(), names)
        assertEquals(400_000L to 0, projectNetMinor(old) to old.pendingIncomingCount, "القديم: الداخل راتب تقديري")
    }

    @Test fun theDefaultFollowsTheAppWidePolicy() {
        val rows = listOf(t(IN, 1_000))
        assertEquals(EstimatePolicy.OWNER_2026_10, EstimatePolicy.current, "التطبيق بيشتغل بقرار المالك")
        assertEquals(1, withEstimatedKinds(rows, names).pendingIncomingCount)
        EstimatePolicy.current = EstimatePolicy.LEGACY
        assertEquals(0, withEstimatedKinds(rows, names).pendingIncomingCount)
    }
}
