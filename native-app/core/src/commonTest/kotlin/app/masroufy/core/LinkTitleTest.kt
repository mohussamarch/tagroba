package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** fix055 ملاحظة 2: اسم التحويل في قايمة «اربطه بالتحويل اللي وصل» = الطرف أو التاجر — عمره ما يبقى نص الرسالة كله (بيانات مخترعة). */
class LinkTitleTest {
    private fun tx(description: String?, direction: Direction = Direction.IN, merchant: String? = null, note: String? = null) = Transaction(
        id = "t-1", occurredAt = "2026-10-24", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = direction, amountMinor = 60_000, currency = Currency.SAR,
        categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x",
        updatedAt = "x", rawDescription = description, rawMerchantName = merchant, note = note,
    )

    @Test fun oneLineIncomingSmsShowsTheSenderNotTheWholeText() {
        val text = "حوالة محلية واردة من:SAMPLE SENDER Y بـSR 600 إلى:0000 26/10/24"
        assertEquals("SAMPLE SENDER Y", linkTitleOf(tx(text)))
        assertEquals("SAMPLE SENDER Y", linkTitleOf(tx(text, merchant = text)), "اسم التاجر = نص الرسالة ⇒ ما يتعرضش")
    }

    @Test fun multiLineSmsUsesTheParsedParty() {
        assertEquals("TEST PERSON", linkTitleOf(tx("حوالة محلية واردة\nمن:TEST PERSON\nبـSR 1000\nإلى:1111\n26/9/18")))
    }

    @Test fun merchantAndNoteWin() {
        assertEquals("QA Shop", linkTitleOf(tx("بيع أصل", merchant = "QA Shop")))
        assertEquals("ملاحظتي", linkTitleOf(tx("x y z", note = "ملاحظتي")))
    }

    @Test fun noNameFallsBackToTheKindLabelNeverTheRawText() {
        val text = "إيداع 600 في حسابك 26/10/24"
        val title = linkTitleOf(tx(text))
        assertFalse(title.contains("26/10/24"), title)
        assertEquals(ruleFor(EconomicKind.UNCLASSIFIED).label, title)
    }
}
