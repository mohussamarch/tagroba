package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** r041 A4 — اسم العملية للعرض (`displayTitleOf`): مفيش نص رسالة كامل بسطوره كعنوان. كل البيانات مخترعة. */
class DisplayTitleTest {
    private val smsOut = "حوالة داخلية صادرة\nمن:9988\nإلى:TEST FRIEND\nبـSR 500\n26/10/07 09:35"

    private fun tx(description: String?, merchant: String? = null, note: String? = null, kind: EconomicKind = EconomicKind.UNCLASSIFIED) = Transaction(
        id = "t-1", occurredAt = "2026-10-07", datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = false,
        observedDirection = Direction.OUT, amountMinor = 50_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "2026-10-07T08:00:00.000Z", updatedAt = "2026-10-07T08:00:00.000Z",
        walletId = "w-bank", rawMerchantName = merchant, rawDescription = description, note = note,
    )

    @Test fun anSmsTransferWithNoMerchantIsTitledByItsParty() {
        val t = tx(smsOut, merchant = "")
        assertEquals("TEST FRIEND", displayTitleOf(t))
        assertTrue(titleIsPartyOf(t))
        assertFalse('\n' in displayTitleOf(t))
    }

    @Test fun merchantAndNoteStillWin() {
        assertEquals("مقهى تجربة", displayTitleOf(tx(smsOut, merchant = "مقهى تجربة")))
        assertEquals("هدية", displayTitleOf(tx(smsOut, note = "هدية")))
        assertEquals("محل معروف", displayTitleOf(tx(smsOut), merchant = "محل معروف"))
        assertFalse(titleIsPartyOf(tx(smsOut, note = "هدية")))
    }

    @Test fun aMultiLineDescriptionWithNoPartyGivesItsFirstLine() {
        val body = "  \nشراء عبر نقاط البيع\nCard 1234\nAMOUNT SAR 12"
        assertEquals("شراء عبر نقاط البيع", displayTitleOf(tx(body)))
        assertFalse(titleIsPartyOf(tx(body)))
    }

    @Test fun nothingAtAllFallsBackToTheKindName() {
        assertEquals(ruleFor(EconomicKind.PURCHASE).label, displayTitleOf(tx(null, kind = EconomicKind.PURCHASE)))
        assertEquals(null, displayTitleOrNull(tx("   ")))
        assertEquals(null, firstLineOf(null))
    }
}
