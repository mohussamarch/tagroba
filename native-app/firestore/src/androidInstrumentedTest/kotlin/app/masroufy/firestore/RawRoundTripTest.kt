package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.data.DocumentCodecs
import app.masroufy.data.LedgerCodecs
import app.masroufy.data.ReferenceCodecs
import app.masroufy.data.toStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * GitLive على أندرويد بيكتب `Map` المحوّل ويرجّعه **بنفس الأنواع** (Long فضل Long، و`null` الصريح فضل موجود)،
 * والمحوّل بيقرا نفس الكيان. ده الأساس اللي المستودعات الحقيقية هتتبني عليه.
 */
@RunWith(AndroidJUnit4::class)
class RawRoundTripTest {
    private val txn = Transaction(
        id = "t-raw-1", occurredAt = "2026-09-15", datePrecision = "day", sourceOrder = 3, economicKind = EconomicKind.PURCHASE,
        economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = 9_007_199_254_740_991, currency = Currency.SAR,
        categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.SUGGESTED, isCashTagged = false,
        createdAt = "2026-09-30T00:00:00.000Z", updatedAt = "2026-09-30T00:00:00.000Z", rawDescription = "شراء من حساب 9988776655443322",
    )

    @Test fun transactionComesBackWithTheSameTypes() = runBlocking<Unit> {
        val db = Emulator.firestore()
        val stored = LedgerCodecs.transactions.toStore(txn)
        val ref = db.collection("users/kt-user/transactions").document(txn.id)
        withTimeout(30_000) { ref.set(stored) }
        val back = withTimeout(30_000) { ref.get() }.rawData()
        assertEquals(stored, back)
        assertEquals(txn.copy(rawDescription = "شراء من حساب ****3322"), LedgerCodecs.transactions.decode(back!!))
    }

    @Test fun explicitNullsAndNestedMapsSurvive() = runBlocking<Unit> {
        val db = Emulator.firestore()
        val category = app.masroufy.core.Category("c-1", null, "أكل", "utensils", "#AA3344", "#FF8899", true, 1)
        val stored = ReferenceCodecs.categories.toStore(category)
        val ref = db.collection("users/kt-user/categories").document(category.id)
        withTimeout(30_000) { ref.set(stored) }
        val back = withTimeout(30_000) { ref.get() }.rawData()!!
        assertEquals(true, back.containsKey("parentId"), "parentId لازم يتخزن null صريح")
        assertEquals(category, ReferenceCodecs.categories.decode(back))
        assertEquals(36, DocumentCodecs.byGroup.size)
    }
}
