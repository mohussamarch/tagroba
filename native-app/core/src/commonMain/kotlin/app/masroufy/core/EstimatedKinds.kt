package app.masroufy.core

/**
 * «تقريبي» — نقل `src/domain/estimatedKinds.ts` (OVERRIDES §18: الأرقام دايمًا ظاهرة).
 * العملية «غير المحددة» بتتحسب **في المجاميع بس** بنوع مفترض — مفيش حاجة بتتكتب، ولا نوع أكده المستخدم بيتلمس:
 *   ١. اقتراح عالي الثقة ⇒ نوعه · ٢. اقتراح مرجّح ⇒ المقترح (محتاج تأكيد) · ٣. مفيش ⇒ الصادر مصروف والوارد دخل (محتاج تأكيد)
 * **إلا** العملية اللي عليها سؤال مستني ([Transaction.suggestedKind] — «ده استرداد؟» · «نلغي الاتنين؟» §77-D): بتفضل «غير محددة» (مش في
 * الدخل ولا المصروف) وبتتعد في «محتاجة تأكيد» — قبل كده الفلوس اللي رجعت كانت بتبان «دخل» في الرئيسية والميزانية والملخص.
 *
 * **قرار المالك §75-1 ([EstimatePolicy.OWNER_2026_10]) بيغيّر (٢) و(٣) للوارد بس:** الداخل اللي مش متأكد منه وما اتعرفش إنه راتب
 * بيفضل «غير محدد» في العرض المعدود ⇒ **لا دخل ولا بينقّص المصروف** — بيتعد لوحده «مستني» ([EstimatedView.pendingIncomingIds]).
 * «الراتب المعروف» هنا: نوع متخزن (المالك أكده أو قاعدة الجهة §44 أو جواب «ده راتبك؟» §75-2 — دول مش «غير محدد» أصلًا) ·
 * اقتراح عالي الثقة (مفيش للوارد النهارده) · أو تصنيف اسمه راتب من الكشف أو من التطبيق ([SALARY_CATEGORY_NAMES]).
 * الصادر ما اتغيرش. العدّين ([EstimatedView.estimatedCount] · [EstimatedView.needsReviewCount]) نفسهم في السياستين.
 */
data class EstimatedView(
    val transactions: List<Transaction>,
    /** كام عملية اتحسبت بنوع مش متأكد منه المستخدم (والمستنية منهم). */
    val estimatedCount: Int,
    /** منهم: كام عملية التطبيق نفسه مش متأكد منها — دي اللي محتاجة تأكيد (والمستنية منهم). */
    val needsReviewCount: Int,
    /** §75-1: الداخل المستني برّه الدخل، بترتيب المدخل — جزء من [needsReviewCount]. فاضي في [EstimatePolicy.LEGACY]. */
    val pendingIncomingIds: List<Id> = emptyList(),
    /** مبلغ المستني لكل عملة — ما بيتجمعش بين عملتين. */
    val pendingIncomingByCurrency: Map<Currency, Halalas> = emptyMap(),
) {
    val pendingIncomingCount: Int get() = pendingIncomingIds.size

    /** مبلغ الداخل المستني بعملة الشاشة [currency] (صفر لو مفيش). */
    fun pendingIncomingMinor(currency: Currency): Halalas = pendingIncomingByCurrency[currency] ?: 0L

    /**
     * العرض المعدود **من غير** الداخل المستني — لفحص «الصرف معروف؟» (`assessCoverage`): الداخل عمره ما بيبقى صرف، فاستناه
     * ما يسكّتش أرقام الصرف ولا متوسطاته ولا المساعد. الدخل والمتبقي بيتحكم فيهم [pendingIncomingCount] لوحده.
     */
    val withoutPendingIncoming: List<Transaction>
        get() = if (pendingIncomingIds.isEmpty()) transactions else pendingIncomingIds.toSet().let { ids -> transactions.filter { it.id !in ids } }
}

/** أسماء تصنيف «الراتب» (من عمود الكشف أو تصنيف التطبيق) — بيانات بيتطابق بيها، مش نص واجهة. */
val SALARY_CATEGORY_NAMES: List<String> = listOf("رواتب", "راتب", "الراتب", "مرتب", "المرتب")

private val SALARY_CATEGORY_KEYS: Set<String> = SALARY_CATEGORY_NAMES.map(::normalizeText).toSet()

/** الاسم ده تصنيف راتب؟ */
fun isSalaryCategory(name: String?): Boolean = !name.isNullOrBlank() && normalizeText(name) in SALARY_CATEGORY_KEYS

fun withEstimatedKinds(
    transactions: List<Transaction>,
    categoryNameById: Map<String, String>,
    policy: EstimatePolicy = EstimatePolicy.current,
): EstimatedView {
    var estimatedCount = 0
    var needsReviewCount = 0
    val pendingIds = mutableListOf<Id>()
    val pendingMinor = LinkedHashMap<Currency, Halalas>()
    val view = transactions.map { t ->
        if (t.economicKind != EconomicKind.UNCLASSIFIED || t.economicKindConfirmed) return@map t
        // §75-6 · §77-D: عليها سؤال لسه ما اتجاوبش («ده استرداد؟» · «نلغي الاتنين؟») ⇒ مستنية برّه أي مجموع، مش «دخل» بالتخمين
        if (awaitsKindAnswer(t)) {
            estimatedCount += 1
            needsReviewCount += 1
            return@map t
        }
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
        if (policy == EstimatePolicy.OWNER_2026_10 && t.observedDirection == Direction.IN) {
            // §75-1: الراتب المعروف بالتصنيف بس بيتحسب راتب؛ أي داخل تاني (حتى اللي ليه اقتراح مرجّح) مستني برّه الدخل
            if (isSalaryCategory(t.sourceCategory) || isSalaryCategory(categoryName)) return@map t.copy(economicKind = EconomicKind.SALARY)
            pendingIds += t.id
            pendingMinor[t.currency] = addMoney(pendingMinor[t.currency] ?: 0L, t.amountMinor)
            return@map t
        }
        val fallback = if (t.observedDirection == Direction.OUT) EconomicKind.PURCHASE else EconomicKind.SALARY
        t.copy(economicKind = suggestion.kind ?: fallback)
    }
    return EstimatedView(view, estimatedCount, needsReviewCount, pendingIds, pendingMinor)
}
