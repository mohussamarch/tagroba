package app.masroufy.core

/**
 * نصايح «الاستثمار» (§87-12 · مواصفة النقل §1.7 · §1.8 · §2.7 · إجابة المالك 2026-10-16): **الموثّقة بس** من [VERIFIED_TIPS] — نصايح
 * النموذج التمانية اتشالت لأن مصادرها لسه 🔎. كارت واحد في المرة: نصايح حالتك الأول بالترتيب، وبعدها الباقي بترتيب البنك، من غير
 * المقفولة (عمرها ما ترجع) ومن غير نصايح البلد التانية.
 */
enum class TipGroup(val title: InvestInsightKey) {
    START(InvestInsightKey.TIPS_GROUP_START),
    SPREAD(InvestInsightKey.TIPS_GROUP_SPREAD),
    DEBT(InvestInsightKey.TIPS_GROUP_DEBT),
    PROTECT(InvestInsightKey.TIPS_GROUP_PROTECT),
    HALAL(InvestInsightKey.TIPS_GROUP_HALAL),
}

/** حالتك اللي بتختار النصيحة (Claude choice ❓ للترتيب والحدود — Q10). الترتيب هنا = ترتيب الأولوية. */
enum class TipSituation(val why: InvestInsightKey) {
    EMERGENCY(InvestInsightKey.TIP_WHY_EMERGENCY),
    CONCENTRATED(InvestInsightKey.TIP_WHY_SPREAD),
    SAVES_NOT_INVESTING(InvestInsightKey.TIP_WHY_MONTHLY),
    HAS_RETURNS(InvestInsightKey.TIP_WHY_COMPOUND),
}

/** عنوان ونص و«ليه مهمة» بنسخة واحدة (فصحى أو مصري). */
data class TipWords(val title: String, val text: String, val why: String)

/** المصدر الرسمي: الاسم · الرابط · يوم التأكد منه. */
data class TipSource(val name: String, val url: String, val checkedAt: IsoDate)

/** نصيحة من البنك الموثّق. [country] = "both" · "sa" · "eg". */
data class BankTip(val id: String, val country: String, val risk: String, val group: TipGroup, val sa: TipWords, val eg: TipWords, val source: TipSource) {
    fun fitsCountry(countryCode: String?): Boolean = country == "both" || country.equals(countryCode, ignoreCase = true)

    /** المصري لمساحة مصر بالمصري، وإلا الفصحى (الإنجليزي كمان — مفيش نسخة إنجليزي في البنك، سؤال مفتوح). */
    fun words(variant: ArabicVariant, language: Language): TipWords = if (language == Language.AR && variant == ArabicVariant.EGYPTIAN) eg else sa
}

/**
 * نصايح كل حالة (Claude choice ❓ — من البنك الموثّق بس؛ حالة مالهاش نصيحة موثّقة ⇒ ما بتغيّرش الترتيب):
 * طوارئ ⇒ صندوق الطوارئ (كوّنه · ابدأه صغير) · كله في نوع ⇒ «لا تضع كل مدخراتك في سلة واحدة» · بيحوّش ومش بيستثمر ⇒
 * «المبالغ الصغيرة المنتظمة» و«وائم مكان المال مع موعد الهدف» و«النقد المعطّل» ·
 * فيه أرباح بترجع ⇒ مفيش نصيحة موثّقة عن إعادة الاستثمار لسه.
 */
val SITUATION_TIPS: Map<TipSituation, List<String>> = mapOf(
    TipSituation.EMERGENCY to listOf("emergency-fund-first", "emergency-fund-habit"),
    TipSituation.CONCENTRATED to listOf("diversify-savings"),
    TipSituation.SAVES_NOT_INVESTING to listOf("small-regular-amounts-over-time", "match-money-to-horizon", "idle-cash-loses-value"),
    TipSituation.HAS_RETURNS to emptyList(),
)

/** نصيحة في الطابور + الحالة اللي جابتها (null = عامة ⇒ «نصيحة»). */
data class QueuedTip(val tip: BankTip, val situation: TipSituation?)

/** الطابور: نصايح كل حالة متحققة بالترتيب ⇒ باقي البنك بترتيبه ⇒ من غير المقفولة ونصايح البلد التانية. فاضي ⇒ الكارت ما بيترسمش. */
fun tipQueue(situations: List<TipSituation>, closed: Set<String>, countryCode: String?, bank: List<BankTip> = VERIFIED_TIPS): List<QueuedTip> {
    val byId = bank.associateBy { it.id }
    val matched = situations.distinct().flatMap { s -> SITUATION_TIPS[s].orEmpty().mapNotNull { id -> byId[id]?.let { QueuedTip(it, s) } } }
    val rest = bank.filter { t -> matched.none { it.tip.id == t.id } }.map { QueuedTip(it, null) }
    return (matched.distinctBy { it.tip.id } + rest).filter { it.tip.id !in closed && it.tip.fitsCountry(countryCode) }
}

/** المقفولة بعد «×»: القديمة + الجديدة من غير تكرار (بالترتيب). */
fun closeTip(closed: List<String>, tipId: String): List<String> = if (tipId in closed) closed else closed + tipId
