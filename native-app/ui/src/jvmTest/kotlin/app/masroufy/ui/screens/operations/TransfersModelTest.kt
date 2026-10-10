package app.masroufy.ui.screens.operations

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.SuspiciousParty
import app.masroufy.core.Texts
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.TransferVerdict
import app.masroufy.usecase.TransferZone
import app.masroufy.usecase.TransferZoneRow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * زون التحويلات (`Transfers`) و«من هذا؟» (`TransferParty`): من `ManageTransfers.zone()` للأقسام (بانتظار ردك · بلا قرار · تقرّر)، الداخل والخارج
 * زي ما هما من حالة الاستخدام (كل عملة لوحدها، والصفر ما بيتعرضش)، وآخر ٤ أرقام بس.
 */
class TransfersModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val salem = TransferPartyRef("سالم#4821", "سالم", "4821")
    private val laila = TransferPartyRef("ليلى#6602", "ليلى", "6602")
    private val mohamed = TransferPartyRef("محمد#1140", "محمد", "1140")
    private val fahd = TransferPartyRef("فهد", "فهد", null)

    private fun zoneRow(p: TransferPartyRef, decision: TransferParty? = null, count: Int = 2, inMinor: Long = 0, outMinor: Long = 0) =
        TransferZoneRow(p, decision, Currency.SAR, count, inMinor, outMinor, "2026-10-07")

    private val zone = TransferZone(
        rows = listOf(
            zoneRow(salem, count = 6, inMinor = 900_000),
            zoneRow(laila, TransferParty(laila.key, laila.label, laila.last4, TransferVerdict.PERSON, "p-l"), count = 9, outMinor = 450_000),
            zoneRow(mohamed, TransferParty(mohamed.key, mohamed.label, mohamed.last4, TransferVerdict.OWN_ACCOUNT), count = 14, inMinor = 600_000, outMinor = 1_820_000),
            zoneRow(fahd, count = 1, inMinor = 120_000),
        ),
        questions = listOf(SuspiciousParty(salem, "2026-10", 6, 6, 0)),
        unidentified = 3,
    )

    @Test fun partiesAreGroupedWaitingThenUndecidedThenDecided() {
        val v = transfersView(zone, mapOf("p-l" to "ليلى"))
        assertEquals(listOf(PartySection.WAITING, PartySection.UNDECIDED, PartySection.DECIDED), v.sections.map { it.first })
        val waiting = v.sections.first().second.single()
        assertEquals("٦ تحويلات في أكتوبر، آخرها ٧ أكتوبر", waiting.meta)
        assertEquals("من هذا؟", waiting.chip.text)
        assertEquals("٤٨٢١", waiting.last4)
        assertEquals(900_000L, waiting.incomingMinor)
        assertNull(waiting.outgoingMinor, "الصفر ما بيتعرضش (مش «غير متاح» — مفيش تحويلات خارجة)")
        val decided = v.sections.last().second
        assertEquals(listOf("شخص: ليلى", "حسابي الآخر"), decided.map { it.chip.text })
        assertEquals("كل تحويلاته أصبحت بين محافظك، لا دخلًا ولا مصروفًا.", decided.last().hint)
        assertEquals("بلا قرار", v.sections[1].second.single().chip.text)
        assertNull(v.sections[1].second.single().last4, "مفيش أرقام ⇒ الاسم بس")
        assertEquals(3, v.unidentified)
        assertTrue(transfersView(TransferZone(emptyList(), emptyList(), 0), emptyMap()).empty)
    }

    @Test fun countsAndEgyptianWording() {
        assertEquals(listOf("تحويل واحد", "تحويلان", "٥ تحويلات", "١٤ تحويلًا"), listOf(1, 2, 5, 14).map(::transfersCount))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val v = transfersView(zone, mapOf("p-l" to "ليلى"))
        assertEquals("مين ده؟", v.sections.first().second.single().chip.text)
        assertEquals("٦ تحويلات في أكتوبر، آخر واحد ٧ أكتوبر", v.sections.first().second.single().meta)
        assertEquals("١٤ تحويل", transfersCount(14))
    }

    @Test fun decisionCardSaysWhatHappened() {
        assertEquals("حساب ينتهي بـ ٤٨٢١", partyAccount(salem))
        assertEquals("فهد", partyAccount(fahd), "من غير أرقام ⇒ اسم الطرف")
        val person = decisionTexts(PartyDecision.AsPerson("سالم", EconomicKind.GIFT_RECEIVED, added = true), salem)
        assertTrue(person.first.startsWith("رُبط بـسالم، "))
        assertTrue(person.second.startsWith("أُضيف سالم إلى أشخاصك. أي تحويل من حساب ينتهي بـ ٤٨٢١"))
        assertEquals("تحويل بين محافظك" to "ليس دخلًا ولا مصروفًا. وصُحّحت عمليتان من الطرف نفسه.", decisionTexts(PartyDecision.OwnAccount(2), salem))
        assertEquals("تم" to "لن نسألك عن هذا الحساب مجددًا.", decisionTexts(PartyDecision.NotThis, salem))
        assertEquals("سالم", partyNewName(salem, "  "), "الاسم الفاضي ⇒ الاسم زي ما جه")
        assertEquals("سالم العتيبي", partyNewName(salem, " سالم العتيبي "))
    }
}
