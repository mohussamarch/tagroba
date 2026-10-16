package app.masroufy.ui.screens.operations

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.isConsistentWithObservedDirection
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** «النوع» في التفاصيل (2026-10-16): نفس أنواع الاتجاه بس، والتحويل بين محافظك ونص «رجعت» ورجل «لنفسك» ما بيتغيروش من هنا. */
class KindPickerTest {
    private fun tx(dir: Direction) = Transaction(
        id = "t", occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = 1_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    @Test fun choicesFollowTheDirection() {
        val out = kindChoicesFor(Direction.OUT)
        val inn = kindChoicesFor(Direction.IN)
        assertTrue(EconomicKind.PURCHASE in out && EconomicKind.PURCHASE !in inn)
        assertTrue(EconomicKind.SALARY in inn && EconomicKind.SALARY !in out)
        assertTrue(out.all { isConsistentWithObservedDirection(it, Direction.OUT) } && inn.all { isConsistentWithObservedDirection(it, Direction.IN) })
        assertFalse(EconomicKind.UNCLASSIFIED in out, "«لسه ما اتحددش» مش اختيار — هو اللي بيفتح اللوحة")
    }

    @Test fun lockedAndOwnMovesAreNotEditableHere() {
        assertTrue(kindEditable(tx(Direction.OUT), locked = false))
        assertFalse(kindEditable(tx(Direction.OUT), locked = true))
        assertFalse(kindEditable(tx(Direction.OUT).copy(transferToWalletId = "w-2"), locked = false))
        assertFalse(kindEditable(tx(Direction.IN).copy(reversalOfId = "t0"), locked = false))
    }
}
