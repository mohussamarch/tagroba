package app.masroufy.ui.screens.investment

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.ZakatAssessment
import app.masroufy.core.ZakatItem
import app.masroufy.core.ZakatItemStatus
import app.masroufy.core.ZakatOutcome
import app.masroufy.core.ZakatPrices
import app.masroufy.core.ZakatRule
import app.masroufy.core.ZakatTopic
import app.masroufy.core.ZakatYear
import app.masroufy.core.daysBetween
import app.masroufy.core.hijriOf
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.usecase.ZakatDateSuggestion

/**
 * «الزكاة» — من نتايج `ManageZakat` (`visible` · `scopeNote` · `rules` · `pricesFrom` · `openYear` · `suggestDate` · `assess`) لشكل الشاشة.
 * **مفيش حساب هنا:** المطلوب والنصاب واللي عليه زكاة لكل سطر من `ZakatAssessment` زي ما هم، والمجهول `null` ⇒ «غير متاح».
 * من غير سنة مؤكدة مفيش حساب (`assess` محتاج السنة) ⇒ الشاشة بتطلب «أكّده» الأول. الوقائع في `ZakatFacts.kt`.
 */
data class ZakatData(
    val currency: Currency,
    val scopeNote: String?,
    val rules: List<ZakatRule>,
    val prices: ZakatPrices,
    val openYear: ZakatYear?,
    val suggestion: ZakatDateSuggestion?,
    val assessment: ZakatAssessment?,
)

enum class OutcomeChip(val key: TextKey) {
    DUE(TextKey.ZAKAT_SCREEN_DUE),
    BELOW(TextKey.ZAKAT_SCREEN_BELOW),
    PARTIAL(TextKey.ZAKAT_SCREEN_PARTIAL),
    RESTARTED(TextKey.ZAKAT_SCREEN_RESTARTED),
    NA(TextKey.NOT_AVAILABLE),
}

data class HawlCard(
    val confirmed: Boolean,
    /** «١ جمادى الأولى ١٤٤٨ — بعد ٥ أيام» أو null لو مفيش يوم (لا مؤكد ولا مقترح). */
    val dateLine: String?,
    val gregorian: String?,
    val why: String,
    val rule: String,
    val canConfirm: Boolean,
)

data class ZakatLineUi(val label: String, val dueMinor: Halalas?, val detail: String, val source: String)

data class ZakatOutUi(val name: String, val valueMinor: Halalas?, val rule: String, val source: String)

data class ZakatUi(
    val currency: Currency,
    val reference: String,
    val scopeNote: String?,
    val hasYear: Boolean,
    val chip: OutcomeChip?,
    val heroMinor: Halalas?,
    val heroSub: String,
    val nisabMinor: Halalas?,
    val nisabRule: String,
    val pricesLine: String?,
    val hawl: HawlCard,
    val lines: List<ZakatLineUi>,
    val outs: List<ZakatOutUi>,
    val canClose: Boolean,
)

fun zakatUi(d: ZakatData, today: IsoDate): ZakatUi {
    val a = d.assessment
    val ruleOf = { topic: ZakatTopic -> d.rules.firstOrNull { it.topic == topic } }
    val authority = d.rules.firstOrNull()?.source?.authority?.label ?: uiText(TextKey.NOT_AVAILABLE)
    val (chip, hero, sub) = hero(a, d.currency)
    val nisab = ruleOf(ZakatTopic.NISAB)
    return ZakatUi(
        currency = d.currency,
        reference = uiText(TextKey.ZAKAT_SCREEN_REF, authority),
        scopeNote = d.scopeNote,
        hasYear = d.openYear != null,
        chip = chip,
        heroMinor = hero,
        heroSub = sub,
        nisabMinor = a?.nisabMinor,
        nisabRule = nisab?.let { uiText(TextKey.ZAKAT_SCREEN_RULE_SRC, it.ruling, it.source.document) }.orEmpty(),
        pricesLine = pricesLine(d.prices, d.currency),
        hawl = hawlCard(d, today, ruleOf(ZakatTopic.HAWL)),
        lines = a?.let { lines(it, d) }.orEmpty(),
        outs = a?.let { outs(it, d) }.orEmpty(),
        canClose = a != null && (a.outcome == ZakatOutcome.DUE || a.outcome == ZakatOutcome.BELOW_NISAB),
    )
}

private fun hero(a: ZakatAssessment?, currency: Currency): Triple<OutcomeChip?, Halalas?, String> {
    if (a == null) return Triple(null, null, uiText(TextKey.ZAKAT_SCREEN_NO_YEAR))
    return when (a.outcome) {
        ZakatOutcome.DUE -> Triple(OutcomeChip.DUE, a.dueMinor, uiText(TextKey.ZAKAT_SCREEN_SUB_DUE, moneyText(a.totalZakatableMinor, currency)))
        ZakatOutcome.BELOW_NISAB -> Triple(OutcomeChip.BELOW, a.dueMinor, uiText(TextKey.ZAKAT_SCREEN_SUB_BELOW, moneyText(a.knownZakatableMinor, currency)))
        ZakatOutcome.PARTIAL -> Triple(OutcomeChip.PARTIAL, null, uiText(TextKey.ZAKAT_SCREEN_SUB_PARTIAL, moneyText(a.knownZakatableMinor, currency)))
        ZakatOutcome.HAWL_RESTARTED -> Triple(OutcomeChip.RESTARTED, a.dueMinor, uiText(TextKey.ZAKAT_SCREEN_SUB_RESTARTED))
        ZakatOutcome.UNAVAILABLE ->
            Triple(OutcomeChip.NA, null, uiText(if (a.nisabMinor == null) TextKey.ZAKAT_SCREEN_SUB_NO_NISAB else TextKey.ZAKAT_SCREEN_SUB_NA))
    }
}

/** «الذهب الخالص X للجرام، الفضة Y للجرام، بتاريخ …» — اللي في ملف الأسعار بعملة البلد بس. */
private fun pricesLine(p: ZakatPrices, currency: Currency): String? {
    val parts = listOfNotNull(
        p.goldPureGramMinor?.let { uiText(TextKey.ZAKAT_SCREEN_PRICE_GOLD, moneyText(it, currency)) },
        p.silverPureGramMinor?.let { uiText(TextKey.ZAKAT_SCREEN_PRICE_SILVER, moneyText(it, currency)) },
        p.asOf?.let { uiText(TextKey.ZAKAT_SCREEN_PRICE_DATE, dateText(it)) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString("، ")
}

private fun hawlCard(d: ZakatData, today: IsoDate, rule: ZakatRule?): HawlCard {
    val due = d.openYear?.dueAt ?: d.suggestion?.dueAt
    val confirmed = d.openYear != null
    return HawlCard(
        confirmed = confirmed,
        dateLine = due?.let { uiText(TextKey.ZAKAT_SCREEN_HAWL_DATE, hijriText(hijriOf(it)), relativeDays(daysBetween(today, it))) },
        gregorian = due?.let(::dateText),
        why = uiText(
            when {
                confirmed -> TextKey.ZAKAT_SCREEN_HAWL_WHY_CONFIRMED
                d.suggestion != null -> TextKey.ZAKAT_SCREEN_HAWL_WHY_SUGGESTED
                else -> TextKey.ZAKAT_SCREEN_HAWL_NA
            },
        ),
        rule = rule?.let { uiText(TextKey.ZAKAT_SCREEN_RULE_SRC, it.ruling, it.source.document) }.orEmpty(),
        canConfirm = !confirmed && d.suggestion != null,
    )
}

/** «اليوم» · «غدًا» · «بعد يومين» · «بعد ٥ أيام» · «بعد ٢٤ يومًا» · «مضى موعدها». */
fun relativeDays(days: Int): String = when {
    days < 0 -> uiText(TextKey.ZAKAT_SCREEN_REL_PAST)
    days == 0 -> uiText(TextKey.ZAKAT_SCREEN_REL_TODAY)
    days == 1 -> uiText(TextKey.ZAKAT_SCREEN_REL_TOMORROW)
    days == 2 -> uiText(TextKey.ZAKAT_SCREEN_REL_TWO)
    days <= 10 -> uiText(TextKey.ZAKAT_SCREEN_REL_FEW, sentenceNumber(days))
    else -> uiText(TextKey.ZAKAT_SCREEN_REL_MANY, sentenceNumber(days))
}

/** «تجب فيه الزكاة»: سطر لكل نوع بمطلوبه (من الحساب)، وجنبه اللي عليه زكاة وقاعدته — أو الواقعة/السعر الناقص. */
private fun lines(a: ZakatAssessment, d: ZakatData): List<ZakatLineUi> = a.lines.map { line ->
    val items = a.items.filter { it.line == line.kind }
    val blocker = items.firstOrNull { it.status == ZakatItemStatus.NEEDS_FACT || it.status == ZakatItemStatus.NO_VALUE }
    val rule = items.firstNotNullOfOrNull { it.topic }?.let { t -> d.rules.firstOrNull { it.topic == t } }
    val detail = when {
        blocker != null -> missingText(blocker)
        line.zakatableMinor != null -> uiText(TextKey.ZAKAT_SCREEN_AMOUNT_RULE, moneyText(line.zakatableMinor, d.currency), rule?.ruling.orEmpty())
        else -> rule?.ruling.orEmpty()
    }
    ZakatLineUi(line.kind.label, line.dueMinor, detail, rule?.source?.document.orEmpty())
}

/** «خارج الحساب»: المعفي · الدين اللي عليك (ما بيتخصمش) · اللي المرجع ما حددش فيه · النوع اللي مش داخل — كل واحد بقاعدته. */
private fun outs(a: ZakatAssessment, d: ZakatData): List<ZakatOutUi> = a.items
    .filter { it.status in OUT_STATUSES }
    .map { item ->
        val rule = item.topic?.let { t -> d.rules.firstOrNull { it.topic == t } }
        ZakatOutUi(
            name = item.holding.name ?: item.line?.label ?: uiText(TextKey.NOT_AVAILABLE),
            valueMinor = item.valueMinor,
            rule = rule?.ruling ?: uiText(TextKey.ZAKAT_NOT_COVERED),
            source = rule?.source?.document.orEmpty(),
        )
    }

private val OUT_STATUSES = setOf(ZakatItemStatus.EXEMPT, ZakatItemStatus.NOT_DEDUCTED, ZakatItemStatus.NO_RULING, ZakatItemStatus.NOT_COVERED)

/** نص الواقعة الناقصة (أو السعر الناقص) للسطر. */
fun missingText(item: ZakatItem): String = uiText(
    when (item.missingFact) {
        "purpose" -> TextKey.ZAKAT_SCREEN_MISSING_PURPOSE
        "holding" -> TextKey.ZAKAT_SCREEN_MISSING_HOLDING
        "saudiCompany" -> TextKey.ZAKAT_SCREEN_MISSING_SAUDI
        "collectability" -> TextKey.ZAKAT_SCREEN_MISSING_COLLECT
        "karat" -> TextKey.ZAKAT_SCREEN_MISSING_KARAT
        "fineness" -> TextKey.ZAKAT_SCREEN_MISSING_FINENESS
        else -> TextKey.ZAKAT_SCREEN_MISSING_PRICE
    },
)
