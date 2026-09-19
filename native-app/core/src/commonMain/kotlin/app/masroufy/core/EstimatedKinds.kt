package app.masroufy.core

/**
 * «تقريبي» — نقل `src/domain/estimatedKinds.ts` (OVERRIDES §18: الأرقام دايمًا ظاهرة).
 * العملية «غير المحددة» بتتحسب **في المجاميع بس** بنوع مفترض — مفيش حاجة بتتكتب، ولا نوع أكده المستخدم بيتلمس:
 *   ١. اقتراح عالي الثقة ⇒ نوعه · ٢. اقتراح مرجّح ⇒ المقترح (محتاج تأكيد) · ٣. مفيش ⇒ الصادر مصروف والوارد دخل (محتاج تأكيد)
 */
data class EstimatedView(
    val transactions: List<Transaction>,
    /** كام عملية اتحسبت بنوع مش متأكد منه المستخدم. */
    val estimatedCount: Int,
    /** منهم: كام عملية التطبيق نفسه مش متأكد منها — دي اللي محتاجة تأكيد. */
    val needsReviewCount: Int,
)

fun withEstimatedKinds(transactions: List<Transaction>, categoryNameById: Map<String, String>): EstimatedView {
    var estimatedCount = 0
    var needsReviewCount = 0
    val view = transactions.map { t ->
        if (t.economicKind != EconomicKind.UNCLASSIFIED || t.economicKindConfirmed) return@map t
        val categoryName = t.categoryId?.let { categoryNameById[it] }?.ifEmpty { null }
        val suggestion = suggestEconomicKind(
            SuggestionInput(
                direction = t.observedDirection,
                categoryName = categoryName,
                merchantName = t.rawMerchantName ?: "",
                description = t.rawDescription ?: "",
            ),
        )
        estimatedCount += 1
        if (isBulkConfirmable(suggestion) && suggestion.kind != null) return@map t.copy(economicKind = suggestion.kind)
        needsReviewCount += 1
        val fallback = if (t.observedDirection == Direction.OUT) EconomicKind.PURCHASE else EconomicKind.SALARY
        t.copy(economicKind = suggestion.kind ?: fallback)
    }
    return EstimatedView(view, estimatedCount, needsReviewCount)
}
