package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.Claimant
import app.masroufy.core.Currency
import app.masroufy.core.Frac
import app.masroufy.core.Halalas
import app.masroufy.core.HeirKind
import app.masroufy.core.HeirShare
import app.masroufy.core.InheritanceLaw
import app.masroufy.core.InheritanceNoteKind
import app.masroufy.core.InheritanceResult
import app.masroufy.core.ShareBasis
import app.masroufy.core.TextKey
import app.masroufy.core.UnsupportedReason
import app.masroufy.core.label
import app.masroufy.core.sentenceDigits
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t

/**
 * نتيجة «حاسبة الورث» (لوحات `InheritanceResult` + `InheritanceSplit`) من `InheritanceResult` — كل مبلغ وكسر وقسمة جاي من المحرك
 * (`CalculateInheritance`)، وهنا الكلام والألوان بس.
 */
data class ClaimUi(val label: String, val amountMinor: Halalas)

/** [color] ترتيب اللون في اللوحة (null = رمادي — محجوب أو مالوش حاجة). */
data class HeirRowUi(val name: String, val basis: String, val sub: String, val amountMinor: Halalas, val fraction: String, val color: Int?)

data class SplitPartUi(val label: String, val amountMinor: Halalas, val color: Int?)

data class SplitItemUi(val name: String, val valueMinor: Halalas, val parts: List<SplitPartUi>)

data class NoteUi(val text: String, val cite: String?, val warn: Boolean)

enum class StopKind { INVALID, UNSUPPORTED, COURT }

sealed interface InheritanceView {
    data class Computed(
        val poolMinor: Halalas,
        val heroSub: String,
        /** أجزاء الشريط تحت الرقم: (اللون، المبلغ) للورثة اللي ليهم نصيب. */
        val segments: List<Pair<Int, Halalas>>,
        val claims: List<ClaimUi>,
        val heirs: List<HeirRowUi>,
        val items: List<SplitItemUi>,
        val itemsNote: String,
        val notes: List<NoteUi>,
    ) : InheritanceView

    /** «هل للمتوفى أقارب آخرون؟» — الإجابة بتغيّر القسمة. */
    data object AskDistant : InheritanceView

    data class Stop(val kind: StopKind, val title: String?, val text: String, val cite: String?) : InheritanceView
}

data class InheritanceResultUi(val view: InheritanceView, val disclaimer: String, val currency: Currency)

/** عدد ألوان الورثة في اللوحة (النموذج: ٨). */
const val HEIR_COLORS = 8

fun inheritanceResultUi(result: InheritanceResult, currency: Currency): InheritanceResultUi {
    val view = when (result) {
        is InheritanceResult.Computed -> computed(result, currency)
        is InheritanceResult.NoText -> InheritanceView.Stop(StopKind.COURT, t(TextKey.INHRES_COURT_TITLE), uiText(result.reason.textKey), result.citation.text)
        is InheritanceResult.Unsupported ->
            if (result.reason == UnsupportedReason.ASK_DISTANT_RELATIVES) InheritanceView.AskDistant
            else InheritanceView.Stop(StopKind.UNSUPPORTED, null, result.text, null)
        is InheritanceResult.Invalid -> InheritanceView.Stop(StopKind.INVALID, null, result.text, null)
    }
    return InheritanceResultUi(view, result.disclaimer, currency)
}

fun stopKindLabel(kind: StopKind): String = t(
    when (kind) {
        StopKind.INVALID -> TextKey.INHRES_KIND_INVALID
        StopKind.UNSUPPORTED -> TextKey.INHRES_KIND_UNSUPPORTED
        StopKind.COURT -> TextKey.INHRES_KIND_COURT
    },
)

private fun computed(r: InheritanceResult.Computed, currency: Currency): InheritanceView.Computed {
    // لون لكل وارث ليه نصيب، بالترتيب (زي النموذج)
    var next = 0
    val colors = r.heirs.associate { h -> h.kind to if (h.totalMinor > 0) next++ % HEIR_COLORS else null }
    val awl = r.notes.any { it.kind == InheritanceNoteKind.AWL }
    val heirs = r.heirs.map { h -> heirRow(h, r, colors[h.kind], awl, currency) }
    val claims = buildList {
        if (r.funeralMinor > 0) add(ClaimUi(uiText(TextKey.INHERIT_CLAIMANT_FUNERAL), r.funeralMinor))
        if (r.debtsMinor > 0) add(ClaimUi(uiText(TextKey.INHERIT_CLAIMANT_DEBTS), r.debtsMinor))
        if (r.wajibaMinor > 0) {
            val son = r.wajiba.firstOrNull()?.child?.isSon ?: true
            add(ClaimUi(t(TextKey.INHRES_WAJIBA_FOR, (if (son) HeirKind.SON else HeirKind.DAUGHTER).label), r.wajibaMinor))
        }
        if (r.bequestMinor > 0) add(ClaimUi(uiText(TextKey.INHERIT_CLAIMANT_BEQUEST), r.bequestMinor))
    }
    val things = thingsPhrase(r.items.size)
    val heroSub = if (claims.isEmpty()) t(TextKey.INHRES_HERO_SUB_PLAIN, money(r.grossMinor, currency), things)
    else t(TextKey.INHRES_HERO_SUB_CLAIMS, money(r.grossMinor, currency), things)
    val items = r.items.map { item ->
        SplitItemUi(item.name, item.valueMinor, item.parts.map { p -> SplitPartUi(claimantLabel(p.claimant, r), p.amountMinor, (p.claimant as? Claimant.Heir)?.let { colors[it.kind] }) })
    }
    return InheritanceView.Computed(
        poolMinor = r.heirsMinor,
        heroSub = heroSub,
        segments = r.heirs.mapNotNull { h -> colors[h.kind]?.let { it to h.totalMinor } },
        claims = claims,
        heirs = heirs,
        items = items,
        itemsNote = t(if (r.law == InheritanceLaw.EG) TextKey.INHSPLIT_NOTE_EG else TextKey.INHSPLIT_NOTE),
        notes = r.notes.map { NoteUi(it.text, it.citation?.text, warn = it.kind == InheritanceNoteKind.UNCLEAR_TEXT) },
    )
}

/** الأزواج اللي بيورثوا الباقي سوا «للذكر مثل حظ الأنثيين». */
private val PAIRS = listOf(
    HeirKind.SON to HeirKind.DAUGHTER, HeirKind.SON_SON to HeirKind.SON_DAUGHTER,
    HeirKind.FULL_BROTHER to HeirKind.FULL_SISTER, HeirKind.PATERNAL_BROTHER to HeirKind.PATERNAL_SISTER,
)

private fun heirRow(h: HeirShare, r: InheritanceResult.Computed, color: Int?, awl: Boolean, currency: Currency): HeirRowUi {
    val name = if (h.count > 1) t(TextKey.INHRES_HEIR_COUNT, h.kind.label, sentenceNumber(h.count)) else h.kind.label
    val names = h.names.filterNotNull().filter { it.isNotBlank() }
    val each = if (h.count > 1 && h.totalMinor > 0) {
        val same = h.amountsMinor.distinct().size == 1
        t(TextKey.INHRES_EACH, money(h.amountsMinor.first(), currency)) + if (same) "" else t(if (r.law == InheritanceLaw.EG) TextKey.INHRES_EXTRA_PIASTRE else TextKey.INHRES_EXTRA_HALALA)
    } else null
    return HeirRowUi(
        name = name,
        basis = basisText(h, r, awl),
        sub = listOfNotNull(names.takeIf { it.isNotEmpty() }?.joinToString("، "), each).joinToString(" — "),
        amountMinor = h.totalMinor,
        fraction = fractionText(h.share),
        color = color,
    )
}

private fun basisText(h: HeirShare, r: InheritanceResult.Computed, awl: Boolean): String = when (h.basis) {
    ShareBasis.FARD -> (if (!awl) fardName(h.share) else null) ?: (t(TextKey.INHRES_BASIS_FARD) + if (awl) t(TextKey.INHRES_AFTER_AWL) else "")
    ShareBasis.RESIDUARY -> {
        val pair = PAIRS.firstOrNull { (m, f) -> h.kind == m || h.kind == f }
        val mixed = pair != null && r.heir(pair.first)?.basis == ShareBasis.RESIDUARY && r.heir(pair.second)?.basis == ShareBasis.RESIDUARY
        t(if (mixed) TextKey.INHRES_BASIS_REST_MF else TextKey.INHRES_BASIS_REST)
    }
    ShareBasis.FARD_AND_RESIDUARY -> t(TextKey.INHRES_BASIS_FARD_REST)
    ShareBasis.RADD -> t(TextKey.INHRES_BASIS_RADD)
    ShareBasis.BLOCKED -> t(TextKey.INHRES_BASIS_BLOCKED)
    ShareBasis.NOTHING_LEFT -> t(TextKey.INHRES_BASIS_NOTHING)
    ShareBasis.DISTANT -> t(TextKey.INHRES_BASIS_DISTANT)
}

/** اسم الفرض لو النصيب كسر من الفروض المعروفة (من غير عول): النصف · الربع · الثمن · الثلث · الثلثان · السدس. */
private fun fardName(f: Frac): String? = when (f.num to f.den) {
    1L to 2L -> t(TextKey.INHRES_FRAC_HALF)
    1L to 4L -> t(TextKey.INHRES_FRAC_QUARTER)
    1L to 8L -> t(TextKey.INHRES_FRAC_EIGHTH)
    1L to 3L -> t(TextKey.INHRES_FRAC_THIRD)
    2L to 3L -> t(TextKey.INHRES_FRAC_TWO_THIRDS)
    1L to 6L -> t(TextKey.INHRES_FRAC_SIXTH)
    else -> null
}

/** «١/٨» أو «٠». */
fun fractionText(f: Frac): String = if (f.isZero) sentenceDigits("0") else sentenceDigits("${f.num}/${f.den}")

/** «شيء واحد · شيئان · ٣ أشياء · ١١ شيئًا». */
fun thingsPhrase(n: Int): String = when (n) {
    1 -> t(TextKey.INHCALC_THINGS_ONE)
    2 -> t(TextKey.INHCALC_THINGS_TWO)
    in 3..10 -> t(TextKey.INHCALC_THINGS_FEW, sentenceNumber(n))
    else -> t(TextKey.INHCALC_THINGS_MANY, sentenceNumber(n))
}

private fun claimantLabel(c: Claimant, r: InheritanceResult.Computed): String = when (c) {
    is Claimant.Heir -> {
        val share = r.heir(c.kind)
        share?.names?.getOrNull(c.index)?.takeIf { it.isNotBlank() }
            ?: if ((share?.count ?: 1) > 1) t(TextKey.INHRES_HEIR_NUMBER, c.kind.label, sentenceNumber(c.index + 1)) else c.kind.label
    }
    is Claimant.Wajiba -> t(TextKey.INHRES_WAJIBA_PERSON, t(if (c.son) TextKey.INHRES_GRANDSON else TextKey.INHRES_GRANDDAUGHTER), sentenceNumber(c.index + 1))
    else -> c.label
}
