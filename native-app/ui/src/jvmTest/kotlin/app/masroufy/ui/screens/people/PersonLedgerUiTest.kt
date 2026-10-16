package app.masroufy.ui.screens.people

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.PersonLink
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.Settlement
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.personLedger
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.screens.operations.meaningError
import app.masroufy.ui.screens.operations.meaningLabel
import app.masroufy.ui.text.t
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «السجل بينا» في صفحة الشخص (جولة r05) — كلام وترتيب بس. كل الأرقام مخترعة. */
class PersonLedgerUiTest {
    private fun tx(id: String, date: String, dir: Direction, amount: Long) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "x", updatedAt = "x", rawMerchantName = "طرف تجريبي",
    )

    @Test
    fun rowsAreNewestFirstWithMeaningAndRunningNet() {
        val out = tx("t-1", "2026-02-01", Direction.OUT, 50_000)
        val back = tx("t-2", "2026-02-10", Direction.IN, 20_000)
        val debt = Obligation("o-1", "p-1", "t-1", ObligationKind.RECEIVABLE, 50_000, Currency.SAR)
        val paid = Settlement("s-1", "t-2", "o-1", 20_000)
        val ledger = personLedger(
            listOf(
                PersonLink("t-1", "p-1", PersonMeaning.LENT, 50_000, "x", obligationId = "o-1"),
                PersonLink("t-2", "p-1", PersonMeaning.COLLECTED, 20_000, "x", settlementIds = listOf("s-1")),
            ),
            listOf(debt), listOf(paid), mapOf("t-1" to out, "t-2" to back),
        )
        val ui = ledgerUi(ledger, accounts = 0, today = "2026-03-01")
        assertEquals(listOf("t-2", "t-1"), ui.rows.map { it.transactionId })
        assertEquals(meaningLabel(PersonMeaning.COLLECTED), ui.rows.first().title)
        assertEquals(AmountTone.INCOME, ui.rows.first().tone)
        assertTrue("طرف تجريبي" in ui.rows.last().sub)
        assertEquals(30_000L, ui.totals.single().netMinor)
        assertEquals(1, ui.open.size)
        assertFalse(ui.empty)
    }

    @Test
    fun emptyOnlyWhenNothingAtAll() {
        val nothing = personLedger(emptyList(), emptyList(), emptyList(), emptyMap())
        assertTrue(ledgerUi(nothing, accounts = 0, today = "2026-03-01").empty)
        assertFalse(ledgerUi(nothing, accounts = 1, today = "2026-03-01").empty)
        // دين قديم من غير عملية بيبان في السجل «من غير تاريخ»
        val opening = personLedger(emptyList(), listOf(Obligation("o-9", "p-1", null, ObligationKind.LOAN_PAYABLE, 9_000, Currency.SAR)), emptyList(), emptyMap())
        val ui = ledgerUi(opening, accounts = 0, today = "2026-03-01")
        assertTrue(t(UiKey.PLINK_E_NO_DATE) in ui.rows.single().sub)
        assertEquals("o-9", ui.rows.single().obligationId)
        assertEquals(-9_000L, ui.totals.single().netMinor)
    }

    @Test
    fun formErrorsBeforeTheUseCase() {
        assertEquals(t(UiKey.PLINK_ERR_PERSON), meaningError(false, PersonMeaning.LENT))
        assertEquals(t(UiKey.PLINK_ERR_MEANING), meaningError(true, null))
        assertNull(meaningError(true, PersonMeaning.WITH))
        assertEquals(t(UiKey.PLINK_ERR_DATE), addOperationError(PersonMeaning.LENT, 100, "16/10/2026", "w"))
        assertEquals(t(UiKey.PLINK_ERR_WALLET), addOperationError(PersonMeaning.LENT, 100, "2026-10-16", null))
        assertNull(addOperationError(PersonMeaning.LENT, 100, "2026-10-16", "w"))
        assertFalse(PersonMeaning.WITH in ADD_MEANINGS)
    }
}
