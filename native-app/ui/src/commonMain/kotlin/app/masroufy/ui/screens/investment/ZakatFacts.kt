package app.masroufy.ui.screens.investment

import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.ZakatAssessment
import app.masroufy.core.ZakatCollectability
import app.masroufy.core.ZakatHolding
import app.masroufy.core.ZakatMetal
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.ZakatShareHolding
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText

/**
 * لوحة «الوقائع — ليست آراء» (`ZakatFactsSheet` جوه `Zakat`): الأسئلة من سطور الحساب نفسه (`ZakatAssessment.items`) — **الواقعة اللي
 * البلد ما بتفرّقش فيها ما بتتسألش** (حالة الاستخدام ما بتطلبهاش: مصر مثلًا ما بتسألش عن الدين ولا جنسية الشركة).
 * الإجابة بتروح لـ`ManageZakat.setAssetFacts` / `setReceivableFact` والحساب بيتعاد.
 */
sealed interface FactAnswer {
    data class Purpose(val value: ZakatPurpose) : FactAnswer
    data class Holding(val value: ZakatShareHolding) : FactAnswer
    data class Saudi(val value: Boolean) : FactAnswer
    data class Collect(val value: ZakatCollectability) : FactAnswer
    data class Karat(val value: Int) : FactAnswer
    data class Fineness(val value: Int) : FactAnswer
}

data class FactOption(val label: String, val selected: Boolean, val answer: FactAnswer)

/** سؤال على حاجة بعينها ([subjectId] = الأصل أو الدين). [missing] = لسه ما اتجاوبش والحساب مستنيه. */
data class FactQuestion(val subjectId: Id, val title: String, val ask: String, val options: List<FactOption>, val missing: Boolean)

/** ملخص الكارت: «ينقص ١» (كهرماني) · «٥ مُجابة» · «لا أسئلة». */
data class FactsSummary(val chip: String, val missing: Boolean)

private val KARATS = listOf(24, 22, 21, 18)
private val FINENESS = listOf(999, 925, 900)

fun factQuestions(a: ZakatAssessment?, currency: Currency): List<FactQuestion> {
    if (a == null) return emptyList()
    val out = mutableListOf<FactQuestion>()
    for (item in a.items) {
        when (val h = item.holding) {
            is ZakatHolding.Metal -> {
                val title = uiText(TextKey.ZAKAT_FACTS_METAL_TITLE, h.name, qtyText(h.grams))
                out += FactQuestion(
                    h.id, title, uiText(TextKey.ZAKAT_FACTS_ASK_PURPOSE),
                    listOf(
                        FactOption(uiText(TextKey.ZAKAT_FACTS_WEAR), h.purpose == ZakatPurpose.WEAR, FactAnswer.Purpose(ZakatPurpose.WEAR)),
                        FactOption(uiText(TextKey.ZAKAT_FACTS_SAVING), h.purpose == ZakatPurpose.SAVING, FactAnswer.Purpose(ZakatPurpose.SAVING)),
                    ),
                    missing = item.missingFact == "purpose",
                )
                if (item.missingFact == "karat" || (h.metal == ZakatMetal.GOLD && h.karat != null)) {
                    out += FactQuestion(
                        h.id, title, uiText(TextKey.ZAKAT_FACTS_ASK_KARAT),
                        KARATS.map { k -> FactOption(uiText(TextKey.ZAKAT_FACTS_KARAT, sentenceNumber(k)), h.karat == k, FactAnswer.Karat(k)) },
                        missing = item.missingFact == "karat",
                    )
                }
                if (item.missingFact == "fineness" || (h.metal == ZakatMetal.SILVER && h.fineness != null)) {
                    out += FactQuestion(
                        h.id, title, uiText(TextKey.ZAKAT_FACTS_ASK_FINENESS),
                        FINENESS.map { f -> FactOption(uiText(TextKey.ZAKAT_FACTS_FINENESS, sentenceNumber(f)), h.fineness == f, FactAnswer.Fineness(f)) },
                        missing = item.missingFact == "fineness",
                    )
                }
            }
            is ZakatHolding.Security -> {
                out += FactQuestion(
                    h.id, h.name, uiText(TextKey.ZAKAT_FACTS_ASK_HOLDING),
                    listOf(
                        FactOption(uiText(TextKey.ZAKAT_FACTS_TRADING), h.holding == ZakatShareHolding.TRADING, FactAnswer.Holding(ZakatShareHolding.TRADING)),
                        FactOption(uiText(TextKey.ZAKAT_FACTS_LONG), h.holding == ZakatShareHolding.LONG_TERM, FactAnswer.Holding(ZakatShareHolding.LONG_TERM)),
                    ),
                    missing = item.missingFact == "holding",
                )
                if (item.missingFact == "saudiCompany" || h.saudiCompany != null) {
                    out += FactQuestion(
                        h.id, h.name, uiText(TextKey.ZAKAT_FACTS_ASK_SAUDI),
                        listOf(
                            FactOption(uiText(TextKey.ZAKAT_FACTS_YES), h.saudiCompany == true, FactAnswer.Saudi(true)),
                            FactOption(uiText(TextKey.ZAKAT_FACTS_NO), h.saudiCompany == false, FactAnswer.Saudi(false)),
                        ),
                        missing = item.missingFact == "saudiCompany",
                    )
                }
            }
            is ZakatHolding.Receivable -> if (item.missingFact == "collectability" || h.collectability != null) {
                out += collectQuestion(h.id, h.name, moneyText(h.remainingMinor, currency), h.collectability, item.missingFact == "collectability")
            }
            is ZakatHolding.CollectedReceivable -> if (item.missingFact == "collectability") {
                out += collectQuestion(h.obligationId, h.name, moneyText(h.amountMinor, currency), h.collectability, true)
            }
            else -> Unit
        }
    }
    return out.distinctBy { it.subjectId + "|" + it.ask }
}

private fun collectQuestion(id: Id, name: String?, amount: String, current: ZakatCollectability?, missing: Boolean) = FactQuestion(
    id, uiText(TextKey.ZAKAT_FACTS_DEBT_ON, name ?: uiText(TextKey.NOT_AVAILABLE), amount), uiText(TextKey.ZAKAT_FACTS_ASK_COLLECT),
    listOf(
        FactOption(uiText(TextKey.ZAKAT_FACTS_STRONG), current == ZakatCollectability.STRONG, FactAnswer.Collect(ZakatCollectability.STRONG)),
        FactOption(uiText(TextKey.ZAKAT_FACTS_DOUBT), current == ZakatCollectability.DOUBTFUL, FactAnswer.Collect(ZakatCollectability.DOUBTFUL)),
    ),
    missing,
)

fun factsSummary(questions: List<FactQuestion>): FactsSummary {
    val missing = questions.count { it.missing }
    return when {
        missing > 0 -> FactsSummary(uiText(TextKey.ZAKAT_SCREEN_FACTS_MISSING, sentenceNumber(missing)), true)
        questions.isEmpty() -> FactsSummary(uiText(TextKey.ZAKAT_SCREEN_FACTS_NONE), false)
        else -> FactsSummary(uiText(TextKey.ZAKAT_SCREEN_FACTS_DONE, sentenceNumber(questions.size)), false)
    }
}
