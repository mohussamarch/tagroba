package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * مراجعة r06 money-2 — قاعدة المالك: «اي تحويلات المفروض متتحسبش مشتريات غير لما صاحب الحساب يقول كدة». الخارج المجهول اللي اقتراحه
 * «أكتر من احتمال» (تحويلات · محافظ رقمية · برق · سداد) بياخد أول احتمال من الاقتراح — مش «شراء» بالتخمين — فما بيتحسبش مصروف شخصي.
 * السياسة القديمة (`LEGACY` — ملف مرجع التطبيق القديم) زي ما هي. كل الأرقام مخترعة.
 */
class AmbiguousOutgoingTest {
    private fun out(id: String, category: String?, merchant: String? = null) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = Direction.OUT, amountMinor = 10_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "c", updatedAt = "c", categoryId = category, walletId = "w-1",
        rawMerchantName = merchant,
    )

    private val names = mapOf("c-tr" to "تحويلات", "c-dw" to "محافظ رقمية", "c-food" to "مطاعم")

    @Test fun ambiguousTransfersAreNotPurchasesUntilTheOwnerSaysSo() {
        val txns = listOf(out("tr", "c-tr"), out("dw", "c-dw"), out("barq", null, merchant = "BARQ test"), out("food", "c-food"))
        val kinds = withEstimatedKinds(txns, names, EstimatePolicy.OWNER_2026_10).transactions.associate { it.id to it.economicKind }
        assertEquals(EconomicKind.INTERNAL_TRANSFER, kinds["tr"])
        assertEquals(EconomicKind.INTERNAL_TRANSFER, kinds["dw"])
        assertEquals(EconomicKind.INTERNAL_TRANSFER, kinds["barq"])
        assertEquals(EconomicKind.PURCHASE, kinds["food"], "تصنيف مشتريات واضح بيفضل شراء")
    }

    @Test fun ambiguousTransfersDoNotCountAsPersonalExpense() {
        val view = withEstimatedKinds(listOf(out("tr", "c-tr"), out("food", "c-food")), names, EstimatePolicy.OWNER_2026_10)
        assertEquals(10_000L, computePeriodTotals(view.transactions, emptyList()).personalExpenseMinor)
    }

    @Test fun theLegacyPolicyIsUnchanged() {
        val kinds = withEstimatedKinds(listOf(out("tr", "c-tr")), names, EstimatePolicy.LEGACY).transactions.map { it.economicKind }
        assertEquals(listOf(EconomicKind.PURCHASE), kinds)
    }
}
