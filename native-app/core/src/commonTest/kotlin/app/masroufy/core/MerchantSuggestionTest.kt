package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** تصنيف التاجر في بلدك التانية = **اقتراح** (رد المالك §64-٢): بعد التاجر المؤكد وقبل القواعد. أسماء مخترعة. */
class MerchantSuggestionTest {
    private val rule = ClassificationRule("rule-1", 1, "WAHMI", RuleMatchMode.CONTAINS, "cat-rule", true)

    private fun run(m: Merchant) =
        categorize(CategorizationInput(false, merchantName = "SUPER WAHMI"), CategorizeDeps(merchantIndex(listOf(m)), listOf(rule), emptyMap()))

    @Test
    fun suggestedMerchantCategoryIsASuggestionBeforeRules() {
        val r = run(Merchant("m-1", "سوبر وهمي", "super wahmi", suggestedCategoryId = "cat-sa"))
        assertEquals("cat-sa", r.categoryId)
        assertEquals(CategorizationSource.OTHER_COUNTRY_MERCHANT, r.source)
        assertEquals(ReviewState.SUGGESTED, r.reviewState)
    }

    @Test
    fun confirmedMerchantCategoryWinsOverTheSuggestion() {
        val r = run(Merchant("m-1", "سوبر وهمي", "super wahmi", verifiedCategoryId = "cat-eg", suggestedCategoryId = "cat-sa"))
        assertEquals(Triple("cat-eg", CategorizationSource.VERIFIED_MERCHANT, ReviewState.CONFIRMED), Triple(r.categoryId, r.source, r.reviewState))
    }

    @Test
    fun withoutASuggestionTheRuleApplies() {
        val r = run(Merchant("m-1", "سوبر وهمي", "super wahmi"))
        assertEquals("cat-rule" to CategorizationSource.RULE, r.categoryId to r.source)
    }
}
