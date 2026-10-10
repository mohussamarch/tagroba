package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.UiKey
import app.masroufy.core.Bequest
import app.masroufy.core.Currency
import app.masroufy.core.EstateItem
import app.masroufy.core.EstateOwner
import app.masroufy.core.HeirKind
import app.masroufy.core.INHERIT_MAX_COUNT
import app.masroufy.core.InheritanceCase
import app.masroufy.core.InheritanceLaw
import app.masroufy.core.InheritanceScenario
import app.masroufy.core.PredeceasedChild
import app.masroufy.core.SpecialCircumstance
import app.masroufy.core.TextKey
import app.masroufy.core.countryPack
import app.masroufy.core.dayMonth
import app.masroufy.core.specialCircumstanceFromWire
import app.masroufy.core.wire
import app.masroufy.ui.text.t
import app.masroufy.usecase.InheritanceScenarioDraft

/**
 * مسودة «حاسبة الورث» (لوحة `InheritanceCalculator` بخطواتها): اللي المستخدم كتبه كنصوص زي ما هو، وبيتحوّل لـ`InheritanceCase` وقت الحساب
 * والحفظ (`CalculateInheritance` · `ManageInheritanceScenarios`). مفيش حساب هنا — قراية خانات بس.
 */
data class EstateRow(val name: String, val value: String)

data class BeforeDraft(
    val funeral: String = "",
    val debts: String = "",
    val bequest: String = "",
    val toHeir: Boolean = false,
    val consent: Boolean = false,
    /** مصر: «فيه ابن أو بنت توفّي قبله وله أولاد؟». */
    val preOn: Boolean = false,
    val preSon: Boolean = true,
    val preSons: Int = 0,
    val preDaughters: Int = 0,
    val preGift: String = "",
    val special: Set<SpecialCircumstance> = emptySet(),
)

data class InheritanceDraft(
    /** بلد الحسبة ⇒ قانونها (الجديدة = البلد الشغالة · المحفوظة = بلدها — §69.4). */
    val countryCode: String,
    val scenarioId: String? = null,
    val scenarioName: String? = null,
    val estateOf: EstateOwner = EstateOwner.MINE,
    val personId: String? = null,
    /** اسم الشخص (من أشخاصك أو مكتوب). */
    val personName: String = "",
    val items: List<EstateRow> = emptyList(),
    val heirs: Map<HeirKind, Int> = emptyMap(),
    /** الأسامي الاختيارية لكل نوع كنص واحد («يوسف، عمر»). */
    val names: Map<HeirKind, String> = emptyMap(),
    val before: BeforeDraft = BeforeDraft(),
    /** إجابة «هل للمتوفى أقارب آخرون؟» (بتتسأل في النتيجة لو لزم). */
    val distantRelatives: Boolean? = null,
    /** 1 تركة مَن · 2 الأملاك · 3 الورثة · 4 قبل القسمة · 5 النتيجة. */
    val step: Int = 1,
) {
    val law: InheritanceLaw? get() = InheritanceLaw.of(countryCode)
    val currency: Currency get() = countryPack(countryCode).currency
}

/** الحد الأقصى لكل نوع وارث (النموذج): زوج واحد · 4 زوجات · أب وأم وجد وجدة واحدة من كل جهة · الباقي لحد 100. */
fun maxCount(kind: HeirKind): Int = when (kind) {
    HeirKind.HUSBAND, HeirKind.FATHER, HeirKind.MOTHER, HeirKind.GRANDFATHER, HeirKind.PATERNAL_GRANDMOTHER, HeirKind.MATERNAL_GRANDMOTHER -> 1
    HeirKind.WIFE -> 4
    else -> INHERIT_MAX_COUNT
}

private fun amountOrZero(text: String, currency: Currency): Long? = when (val p = parseAmountField(text, currency)) {
    Parsed.Empty -> 0L
    Parsed.Bad -> null
    is Parsed.Ok -> p.value
}

/** الأسامي من النص («يوسف، عمر» أو «يوسف, عمر»). */
fun splitNames(text: String): List<String> = text.split('،', ',').map { it.trim() }.filter { it.isNotEmpty() }

/** المسألة للحساب والحفظ. [null] لو مبلغ مش مقروء (الخطوة بتمنع ده قبلها). */
fun InheritanceDraft.toCase(): InheritanceCase? {
    val cur = currency
    val items = items.map { row -> EstateItem(row.name.trim(), parseAmountField(row.value, cur).orNull ?: return null) }
    val b = before
    val funeral = amountOrZero(b.funeral, cur) ?: return null
    val debts = amountOrZero(b.debts, cur) ?: return null
    val bequest = amountOrZero(b.bequest, cur) ?: return null
    val gift = amountOrZero(b.preGift, cur) ?: return null
    val pre = if (law == InheritanceLaw.EG && b.preOn) listOf(PredeceasedChild(b.preSon, b.preSons, b.preDaughters, gift)) else emptyList()
    return InheritanceCase(
        countryCode = countryCode,
        heirs = heirs.filterValues { it > 0 },
        items = items,
        funeralMinor = funeral,
        debtsMinor = debts,
        bequest = if (bequest > 0) Bequest(bequest, b.toHeir, b.consent) else null,
        predeceasedChildren = pre,
        distantRelatives = distantRelatives,
        special = b.special,
        names = names.mapValues { splitNames(it.value) }.filterValues { it.isNotEmpty() }.filterKeys { (heirs[it] ?: 0) > 0 },
    )
}

/** فحص الخطوة قبل «التالي» — null = تمام. */
fun checkStep(d: InheritanceDraft): String? = when (d.step) {
    1 -> if (d.estateOf == EstateOwner.OTHER && d.personId == null && d.personName.isBlank()) t(UiKey.INHCALC_PICK_PERSON) else null
    2 -> when {
        d.items.isEmpty() -> t(TextKey.INHERIT_INVALID_NO_ITEMS)
        d.items.any { it.name.isBlank() } -> t(TextKey.INHERIT_INVALID_ITEM_NAME)
        d.items.any { (parseAmountField(it.value, d.currency).orNull ?: 0L) <= 0L } -> t(UiKey.INHCALC_ITEM_VALUE)
        else -> null
    }
    4 -> {
        val b = d.before
        if (listOf(b.funeral, b.debts, b.bequest, b.preGift).any { amountOrZero(it, d.currency).let { v -> v == null || v < 0 } }) t(TextKey.MONEY_BAD_FORMAT) else null
    }
    else -> null
}

/** للحفظ: الاسم + تركة مين + الشخص + المسألة. */
fun InheritanceDraft.toScenarioDraft(name: String): InheritanceScenarioDraft? {
    val case = toCase() ?: return null
    val person = if (estateOf == EstateOwner.OTHER) personId else null
    return InheritanceScenarioDraft(name, estateOf, person, case)
}

/** الاسم الافتراضي للحفظ: «تركتي — 7 أكتوبر» · «تركة سالم — 7 أكتوبر». */
fun defaultScenarioName(d: InheritanceDraft, today: String): String =
    d.scenarioName ?: if (d.estateOf == EstateOwner.MINE) t(UiKey.INHCALC_NAME_MINE, dayMonth(today))
    else t(UiKey.INHCALC_NAME_OTHER, d.personName.trim(), dayMonth(today))

/** حسبة محفوظة ⇒ مسودة على النتيجة. [people] لاسم الشخص لو التركة لشخص من أشخاصك. */
fun draftFrom(s: InheritanceScenario, people: List<PersonChoice>): InheritanceDraft {
    val c = s.input
    val cur = countryPack(c.countryCode).currency
    fun text(v: Long): String = if (v == 0L) "" else plainAmount(v, cur)
    val pre = c.predeceasedChildren.firstOrNull()
    return InheritanceDraft(
        countryCode = c.countryCode,
        scenarioId = s.id,
        scenarioName = s.name,
        estateOf = s.estateOf,
        personId = s.personId,
        personName = s.personId?.let { id -> people.firstOrNull { it.id == id }?.name }.orEmpty(),
        items = c.items.map { EstateRow(it.name, plainAmount(it.valueMinor, cur)) },
        heirs = c.heirs,
        names = c.names.mapValues { it.value.joinToString("، ") },
        before = BeforeDraft(
            funeral = text(c.funeralMinor),
            debts = text(c.debtsMinor),
            bequest = c.bequest?.let { text(it.amountMinor) }.orEmpty(),
            toHeir = c.bequest?.toHeir ?: false,
            consent = c.bequest?.heirsConsent ?: false,
            preOn = pre != null,
            preSon = pre?.isSon ?: true,
            preSons = pre?.sons ?: 0,
            preDaughters = pre?.daughters ?: 0,
            preGift = pre?.let { text(it.givenInLifeMinor) }.orEmpty(),
            special = c.special,
        ),
        distantRelatives = c.distantRelatives,
        step = 5,
    )
}

// ── الحفظ مع الشاشة (`rememberSaveable`): نص واحد بفواصل تحكم ما بتتكتبش (US · RS · GS)

private const val US = '\u001F'
private const val RS = '\u001E'
private const val GS = '\u001D'

fun encodeDraft(d: InheritanceDraft): String {
    val b = d.before
    return listOf(
        d.countryCode, d.scenarioId.orEmpty(), d.scenarioName.orEmpty(), d.estateOf.wire, d.personId.orEmpty(), d.personName,
        d.items.joinToString(RS.toString()) { "${it.name}$GS${it.value}" },
        d.heirs.entries.joinToString(RS.toString()) { "${it.key.wire}$GS${it.value}" },
        d.names.entries.joinToString(RS.toString()) { "${it.key.wire}$GS${it.value}" },
        b.funeral, b.debts, b.bequest, b.toHeir.toString(), b.consent.toString(), b.preOn.toString(), b.preSon.toString(),
        b.preSons.toString(), b.preDaughters.toString(), b.preGift, b.special.joinToString(RS.toString()) { it.wire },
        d.distantRelatives?.toString().orEmpty(), d.step.toString(),
    ).joinToString(US.toString())
}

fun decodeDraft(text: String): InheritanceDraft? {
    val p = text.split(US)
    if (p.size != 22) return null
    fun pairs(s: String): List<Pair<String, String>> = if (s.isEmpty()) emptyList() else s.split(RS).mapNotNull { e -> e.split(GS).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
    return InheritanceDraft(
        countryCode = p[0],
        scenarioId = p[1].ifEmpty { null },
        scenarioName = p[2].ifEmpty { null },
        estateOf = EstateOwner.fromWire(p[3]) ?: EstateOwner.MINE,
        personId = p[4].ifEmpty { null },
        personName = p[5],
        items = pairs(p[6]).map { EstateRow(it.first, it.second) },
        heirs = pairs(p[7]).mapNotNull { (k, v) -> HeirKind.fromWire(k)?.let { kind -> v.toIntOrNull()?.let { kind to it } } }.toMap(),
        names = pairs(p[8]).mapNotNull { (k, v) -> HeirKind.fromWire(k)?.let { it to v } }.toMap(),
        before = BeforeDraft(
            p[9], p[10], p[11], p[12].toBoolean(), p[13].toBoolean(), p[14].toBoolean(), p[15].toBoolean(), p[16].toIntOrNull() ?: 0,
            p[17].toIntOrNull() ?: 0, p[18], if (p[19].isEmpty()) emptySet() else p[19].split(RS).mapNotNull(::specialCircumstanceFromWire).toSet(),
        ),
        distantRelatives = p[20].ifEmpty { null }?.toBoolean(),
        step = p[21].toIntOrNull()?.coerceIn(1, 5) ?: 1,
    )
}
