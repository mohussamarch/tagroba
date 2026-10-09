package app.masroufy.ui.screens.operations

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.TransferVerdict
import app.masroufy.core.dayMonth
import app.masroufy.core.monthName
import app.masroufy.core.sentenceDigits
import app.masroufy.ui.text.t
import app.masroufy.usecase.TransferZone

/** أقسام الزون: بانتظار ردك (٥ تحويلات أو أكتر في شهر) · بلا قرار · تقرّر. */
enum class PartySection { WAITING, UNDECIDED, DECIDED }

data class PartyRowView(
    val ref: TransferPartyRef,
    val initial: String,
    /** آخر ٤ أرقام بس (CLAUDE.md #11) — بأرقام الجملة. */
    val last4: String?,
    val meta: String,
    val chip: FieldChip,
    val incomingMinor: Halalas?,
    val outgoingMinor: Halalas?,
    val currency: Currency,
    val section: PartySection,
    val verdict: TransferVerdict?,
    /** سطر الشرح تحت الأفعال لما الكارت يتفتح. */
    val hint: String,
)

data class TransfersView(val sections: List<Pair<PartySection, List<PartyRowView>>>, val unidentified: Int) {
    val empty: Boolean get() = sections.isEmpty()
}

/** «تحويل واحد» · «تحويلان» · «٥ تحويلات» · «١٤ تحويلًا». */
fun transfersCount(n: Int): String =
    countText(n, TextKey.TRANSFERS_COUNT_ONE, TextKey.TRANSFERS_COUNT_TWO, TextKey.TRANSFERS_COUNT_FEW, TextKey.TRANSFERS_COUNT_MANY)

/**
 * الزون (`ManageTransfers.zone`) ⇒ الأقسام. [personNames] أسماء الأشخاص (للقرار «شخص: …»). المبالغ زي ما هي من حالة الاستخدام (كل عملة لوحدها).
 */
fun transfersView(zone: TransferZone, personNames: Map<Id, String>): TransfersView {
    val asked = zone.questions.associateBy { it.party.key }
    val rows = zone.rows.map { r ->
        val d = r.decision
        val q = asked[r.party.key]
        val section = when {
            d != null -> PartySection.DECIDED
            q != null -> PartySection.WAITING
            else -> PartySection.UNDECIDED
        }
        val why = if (section == PartySection.WAITING && q != null) {
            val month = q.month.split('-').getOrNull(1)?.toIntOrNull()?.let(::monthName).orEmpty()
            t(TextKey.TRANSFERS_IN_MONTH, transfersCount(q.count), month)
        } else transfersCount(r.count)
        val chip = when (d?.verdict) {
            null -> if (section == PartySection.WAITING) FieldChip(t(TextKey.TRANSFER_PARTY_ASK), ChipInk.amber, ChipInk.amberBg) else FieldChip(t(TextKey.TRANSFERS_UNDECIDED), ChipInk.grey, ChipInk.greyBg)
            TransferVerdict.OWN_ACCOUNT -> FieldChip(t(TextKey.TRANSFER_PARTY_OWN), ChipInk.blue, ChipInk.blueBg)
            TransferVerdict.PERSON -> FieldChip(t(TextKey.TRANSFERS_PERSON_CHIP, d.personId?.let(personNames::get) ?: t(TextKey.NOT_AVAILABLE)), ChipInk.green, ChipInk.greenBg)
            TransferVerdict.DISMISSED -> FieldChip(t(TextKey.TRANSFER_PARTY_NOT), ChipInk.grey, ChipInk.greyBg)
        }
        val hint = when (d?.verdict) {
            null -> t(TextKey.TRANSFERS_HINT_ASK)
            TransferVerdict.OWN_ACCOUNT -> t(TextKey.TRANSFERS_HINT_OWN)
            else -> t(TextKey.TRANSFERS_HINT_UNDO)
        }
        PartyRowView(
            ref = r.party,
            initial = r.party.label.trim().take(1),
            last4 = r.party.last4?.let(::sentenceDigits),
            meta = t(TextKey.TRANSFERS_META, why, dayMonth(r.lastAt)),
            chip = chip,
            incomingMinor = r.incomingMinor.takeIf { it > 0 },
            outgoingMinor = r.outgoingMinor.takeIf { it > 0 },
            currency = r.currency,
            section = section,
            verdict = d?.verdict,
            hint = hint,
        )
    }
    val sections = PartySection.entries.mapNotNull { s -> rows.filter { it.section == s }.takeIf { it.isNotEmpty() }?.let { s to it } }
    return TransfersView(sections, zone.unidentified)
}

fun sectionTitle(s: PartySection): String = t(when (s) {
    PartySection.WAITING -> TextKey.TRANSFERS_WAITING
    PartySection.UNDECIDED -> TextKey.TRANSFERS_UNDECIDED
    PartySection.DECIDED -> TextKey.TRANSFERS_DECIDED
})
