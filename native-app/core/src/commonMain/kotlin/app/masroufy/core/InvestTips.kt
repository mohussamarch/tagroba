package app.masroufy.core

/**
 * بنك نصايح «الاستثمار» (§87-12 · مواصفة النقل §1.7 · §2.7): كل نصيحة بمجموعتها وحالتها (لو ليها) ومصدرها الرسمي — اليوتيوبرز أفكار
 * بس وما بيظهروش هنا. كارت واحد في المرة: نصايح حالتك الأول بالترتيب، وبعدها العامة، من غير المقفولة (عمرها ما ترجع).
 */
enum class TipGroup(val title: InvestInsightKey) {
    START(InvestInsightKey.TIPS_GROUP_START),
    SPREAD(InvestInsightKey.TIPS_GROUP_SPREAD),
    PROTECT(InvestInsightKey.TIPS_GROUP_PROTECT),
    HALAL(InvestInsightKey.TIPS_GROUP_HALAL),
}

/** حالتك اللي بتختار النصيحة (Claude choice ❓ للترتيب والحدود — Q10). الترتيب هنا = ترتيب الأولوية. */
enum class TipSituation { EMERGENCY, CONCENTRATED, SAVES_NOT_INVESTING, HAS_RETURNS }

/** نصيحة واحدة. [why] = سطر «ليه دي» فوق النص (العامة: «نصيحة»). المصدر حسب البلد ([sourceFor]) — التحذيرات من هيئة البلد نفسها. */
data class InvestTip(
    val id: String,
    val group: TipGroup,
    val situation: TipSituation?,
    val why: InvestInsightKey,
    val text: InvestInsightKey,
    private val source: InvestInsightKey,
    private val sourceEgypt: InvestInsightKey = source,
) {
    fun sourceFor(countryCode: String?): InvestInsightKey = if (countryCode.equals(EGYPT_CODE, ignoreCase = true)) sourceEgypt else source
}

private const val EGYPT_CODE = "EG"

/** البنك بترتيب صفحة «النصايح» (النموذج `InvestTipCard.dc.html` — TIPS). */
val INVEST_TIPS: List<InvestTip> = listOf(
    InvestTip("emergency", TipGroup.START, TipSituation.EMERGENCY, InvestInsightKey.TIP_WHY_EMERGENCY, InvestInsightKey.TIP_EMERGENCY, InvestInsightKey.SRC_EMERGENCY),
    InvestTip("monthly", TipGroup.START, TipSituation.SAVES_NOT_INVESTING, InvestInsightKey.TIP_WHY_MONTHLY, InvestInsightKey.TIP_MONTHLY, InvestInsightKey.SRC_MONTHLY),
    InvestTip("compound", TipGroup.START, TipSituation.HAS_RETURNS, InvestInsightKey.TIP_WHY_COMPOUND, InvestInsightKey.TIP_COMPOUND, InvestInsightKey.SRC_COMPOUND),
    InvestTip("spread", TipGroup.SPREAD, TipSituation.CONCENTRATED, InvestInsightKey.TIP_WHY_SPREAD, InvestInsightKey.TIP_SPREAD, InvestInsightKey.SRC_SPREAD),
    InvestTip("daytrade", TipGroup.PROTECT, null, InvestInsightKey.TIP_WHY_GENERAL, InvestInsightKey.TIP_DAYTRADE, InvestInsightKey.SRC_DAYTRADE),
    InvestTip("influencer", TipGroup.PROTECT, null, InvestInsightKey.TIP_WHY_GENERAL, InvestInsightKey.TIP_INFLUENCER, InvestInsightKey.SRC_INFLUENCER),
    InvestTip("fraud", TipGroup.PROTECT, null, InvestInsightKey.TIP_WHY_GENERAL, InvestInsightKey.TIP_FRAUD, InvestInsightKey.SRC_FRAUD_SA, InvestInsightKey.SRC_FRAUD_EG),
    InvestTip("halal", TipGroup.HALAL, null, InvestInsightKey.TIP_WHY_GENERAL, InvestInsightKey.TIP_HALAL, InvestInsightKey.SRC_HALAL),
)

/** الطابور: نصايح كل حالة متحققة بالترتيب ⇒ العامة ⇒ من غير المقفولة. أول واحدة = الكارت. فاضي ⇒ الكارت ما بيترسمش. */
fun tipQueue(situations: List<TipSituation>, closed: Set<String>): List<InvestTip> {
    val matched = situations.distinct().flatMap { s -> INVEST_TIPS.filter { it.situation == s } }
    val general = INVEST_TIPS.filter { it.situation == null }
    return (matched + general).filter { it.id !in closed }
}

/** المقفولة بعد «×»: القديمة + الجديدة من غير تكرار (بالترتيب). */
fun closeTip(closed: List<String>, tipId: String): List<String> = if (tipId in closed) closed else closed + tipId
