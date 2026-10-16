package app.masroufy.ui.screens.operations

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.usecase.LinkedInflowsView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r041 A5 — شباك «تراجع» 8 ثواني بساعة مزيفة (بيخلص عند 8000 مش 7999، وبيقف والإيد على الشريط) · A11 — شريط «مربوطة بـ» (الاسم من اسم
 * العرض، والمصروف الممسوح مش بيتفتح). بيانات مخترعة.
 */
class UndoWindowLinkedTest {
    private var now = 1_000L
    private val clock = { now }

    @Test fun theWindowExpiresAtEightSecondsNotBefore() {
        val w = UndoWindow(clock)
        now += 7_999
        assertFalse(w.expired())
        assertEquals(1L, w.leftMs())
        now += 1
        assertTrue(w.expired())
        assertEquals(0L, w.leftMs())
    }

    @Test fun holdingTheStripPausesTheWindow() {
        val w = UndoWindow(clock)
        now += 5_000
        w.pause()
        now += 10_000
        assertFalse(w.expired(), "the hand is on the strip")
        assertEquals(3_000L, w.leftMs())
        w.resume()
        now += 2_999
        assertFalse(w.expired())
        now += 1
        assertTrue(w.expired())
    }

    private fun txn(id: String, amount: Long, dir: Direction, merchant: String?, raw: String?) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c", walletId = "w-1",
        rawMerchantName = merchant, rawDescription = raw,
    )

    @Test fun theBannerNamesTheExpenseAndOpensIt() {
        val expense = txn("exp", 12_000, Direction.OUT, "Test Diner", "TEST DINER\nREF 0001")
        val share = txn("in", 6_000, Direction.IN, null, "transfer")
        val b = linkedBanner(LinkedInflowsView.Inflow(share, expense))
        assertEquals("Test Diner", b.title)
        assertEquals("exp", b.expenseId)
        assertEquals(12_000L, b.amountMinor)
        assertEquals("2026-09-10", b.day)
        assertFalse(b.gone)
        assertEquals(UiKey.LINKED_BANNER, b.textKey)
    }

    @Test fun aDeletedExpenseShowsGoneAndCannotBeOpened() {
        val share = txn("in", 6_000, Direction.IN, null, "transfer")
        val b = linkedBanner(LinkedInflowsView.Inflow(share, null))
        assertTrue(b.gone)
        assertNull(b.expenseId)
        assertNull(b.amountMinor)
        assertEquals(UiKey.LINKED_BANNER_GONE, b.textKey)
    }
}
