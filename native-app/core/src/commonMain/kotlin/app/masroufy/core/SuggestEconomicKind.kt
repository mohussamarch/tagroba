package app.masroufy.core

import app.masroufy.core.EconomicKind.*

/**
 * اقتراح النوع الاقتصادي — نقل `src/domain/suggestEconomicKind.ts`. **بيقترح ومش بيقرر.**
 * spec/02: «لا تصنف تحويلًا غامضًا كراتب افتراضيًا» — الغامض بيفضل غامض ومحتاج قرار.
 */
enum class SuggestionConfidence(val wire: String) { HIGH("high"), MEDIUM("medium"), AMBIGUOUS("ambiguous") }

data class KindSuggestion(
    /** null عند الغموض — مفيش اقتراح بيتخترع عشان يسد فراغ. */
    val kind: EconomicKind?,
    val confidence: SuggestionConfidence,
    val reason: String,
    val alternatives: List<EconomicKind>,
)

data class SuggestionInput(
    val direction: Direction,
    /** تصنيف الإنفاق من الملف — دليل قوي بس مش نوع اقتصادي. */
    val sourceCategory: String? = null,
    /** اسم التصنيف اللي حدده التطبيق أو المستخدم. */
    val categoryName: String? = null,
    val sourceOperationType: String? = null,
    val merchantName: String? = null,
    val description: String? = null,
)

private const val CASH_WITHDRAWAL = "سحب نقدي"
private val INVESTMENT = listOf("استثمار", "ذهب")
private const val INSTALLMENT = "تقسيط"
private const val TRANSFERS = "تحويلات"
private const val DIGITAL_WALLETS = "محافظ رقمية"
private const val DEBT_PAYMENT = "سداد"
private const val BANK_FEES = "رسوم بنكية"
private const val DEPOSIT = "إيداع"
private const val REFUND = "استرداد"

private fun eq(a: String?, b: String) = a != null && normalizeText(a) == normalizeText(b)
private fun oneOf(a: String?, list: List<String>) = list.any { eq(a, it) }
private fun ambiguous(reason: String, vararg alternatives: EconomicKind) =
    KindSuggestion(null, SuggestionConfidence.AMBIGUOUS, reason, alternatives.toList())

fun suggestEconomicKind(input: SuggestionInput): KindSuggestion {
    // عمود الملف الأول (أقرب للمصدر)، وبعده تصنيف التطبيق
    val cat = JsText.trim(input.sourceCategory ?: "").ifEmpty { null } ?: JsText.trim(input.categoryName ?: "").ifEmpty { null }
    val haystack = "${input.merchantName ?: ""} ${input.description ?: ""}"

    if (input.direction == Direction.OUT) {
        if (eq(cat, CASH_WITHDRAWAL)) {
            return KindSuggestion(INTERNAL_TRANSFER, SuggestionConfidence.HIGH, "سحب نقدي: الفلوس اتنقلت من البنك لمحفظة الكاش، مش اتصرفت", emptyList())
        }
        if (oneOf(cat, INVESTMENT)) {
            return KindSuggestion(ASSET_BUY, SuggestionConfidence.HIGH, "«$cat»: شراء أصل استثماري، مش استهلاك", listOf(PURCHASE))
        }
        if (eq(cat, BANK_FEES)) return KindSuggestion(FEE, SuggestionConfidence.HIGH, "رسوم بنكية: مصروف مستقل", emptyList())
        if (eq(cat, INSTALLMENT)) {
            return KindSuggestion(
                DEBT_REPAID, SuggestionConfidence.MEDIUM,
                "تقسيط: سداد جزء من التزام سابق. " + "لو الشراء الأصلي مسجَّل كمصروف، احتسابه تاني هيبقى عدّ مزدوج.",
                listOf(PURCHASE),
            )
        }
        if (eq(cat, DEBT_PAYMENT)) {
            // التعارض الموثق في ARCHITECTURE §9.6 — ما بيتحسمش آليًا
            return ambiguous(
                "سداد: ممكن يكون سداد دين عليك (مش مصروف) وممكن يكون دفع فاتورة " + "(مصروف). الكشف مش بيفرّق، فمحتاج قرارك.",
                DEBT_REPAID, PURCHASE, INTERNAL_TRANSFER,
            )
        }
        if (eq(cat, DIGITAL_WALLETS)) {
            return ambiguous(
                "محفظة رقمية (زي برق): الفلوس خرجت من الراجحي، بس الكشف مش بيقول " + "راحت فين. ممكن تكون محفظتك انت، وممكن تكون دعم أو قرض لحد.",
                INTERNAL_TRANSFER, SUPPORT_GIFT, LOAN_GRANTED,
            )
        }
        if (eq(cat, TRANSFERS)) {
            return ambiguous(
                "تحويل صادر: الكشف بيقول الفلوس خرجت بس مش بيقول ليه. " + "ممكن تحويل لحسابك، دعم، قرض، أو سداد.",
                INTERNAL_TRANSFER, SUPPORT_GIFT, LOAN_GRANTED, DEBT_REPAID,
            )
        }
        // برق وسيلة تحويل مش نوع مصروف (spec/02)
        if (normalizedContains(haystack, "BARQ")) {
            return ambiguous(
                "برق وسيلة تحويل مش نوع مصروف. لازم تحدد الغرض: حسابك، دعم، قرض، ولا سداد.",
                INTERNAL_TRANSFER, SUPPORT_GIFT, LOAN_GRANTED, DEBT_REPAID,
            )
        }
        if (cat != null && !eq(cat, "غير مصنّف")) {
            return KindSuggestion(PURCHASE, SuggestionConfidence.HIGH, "«$cat»: شراء أو فاتورة", listOf(SUPPORT_GIFT, LOAN_GRANTED))
        }
        return ambiguous("صادر بلا تصنيف واضح. محتاج تحدد ده شراء ولا نقل فلوس.", PURCHASE, INTERNAL_TRANSFER, LOAN_GRANTED, DEBT_REPAID)
    }

    // الوارد — **ما بيتقترحش كدخل أبدًا** من الكشف لوحده (الفرق بين راتب وقرض وتحصيل مش في البيانات)
    if (oneOf(cat, INVESTMENT)) {
        return KindSuggestion(ASSET_SELL, SuggestionConfidence.MEDIUM, "وارد من الاستثمار: حصيلة بيع أصل، والربح المحقق فقط هو المكسب", listOf(SALARY))
    }
    if (eq(cat, REFUND)) {
        return ambiguous(
            "استرداد: ده رجوع فلوس دفعتها، مش دخل جديد. " + "الأنسب تربطه بالعملية الأصلية بدل ما تعدّه دخلًا.",
            PERSONAL_SALE,
        )
    }
    if (eq(cat, DEPOSIT) || eq(cat, TRANSFERS) || eq(cat, DIGITAL_WALLETS) || cat == null) {
        return ambiguous(
            "وارد: الكشف بيقول الفلوس دخلت بس مش بيقول منين. " + "راتب؟ قرض استلمته؟ تحصيل دين لك؟ أمانة؟ الفرق بينهم كبير في الحساب.",
            SALARY, FREELANCE, LOAN_RECEIVED, DEBT_COLLECTED, CUSTODY_RECEIVED,
        )
    }
    return ambiguous("وارد بتصنيف «$cat»: محتاج تحدد نوعه الاقتصادي.", SALARY, FREELANCE, PERSONAL_SALE, LOAN_RECEIVED, DEBT_COLLECTED)
}

/** قوي كفاية للتأكيد الجماعي؟ */
fun isBulkConfirmable(suggestion: KindSuggestion): Boolean =
    suggestion.kind != null && suggestion.confidence == SuggestionConfidence.HIGH
