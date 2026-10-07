package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.port.TransactionPatch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * مستودع العمليات الحقيقي على محاكي أندرويد قدام Firestore Emulator. كل اختبار في حساب جديد (`uid` عشوائي)
 * عشان ما يشوفش بيانات اختبار تاني. بيانات وهمية بس.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreTransactionRepositoryTest {
    private fun space() = FirestoreSpace.forUser(Emulator.firestore(), "kt-" + java.util.UUID.randomUUID())

    private fun txn(id: String, date: String, order: Int = 0, note: String? = null) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = order, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = 1_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "2026-09-0${(order % 9) + 1}T00:00:00.000Z",
        updatedAt = "2026-09-30T00:00:00.000Z", note = note,
    )

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(60_000) { block() } }

    @Test fun periodReadIsSortedLikeTheCurrentApp() = run {
        val repo = FirestoreTransactionRepository(space())
        repo.saveMany(listOf(txn("t-3", "2026-09-10", 2), txn("t-1", "2026-09-10", 1), txn("t-out", "2026-10-01"), txn("t-0", "2026-09-01")))
        assertEquals(listOf("t-0", "t-1", "t-3"), repo.listByDateRange("2026-09-01", "2026-09-30").map { it.id })
        // `createdAt` بعد اللحظة دي بالظبط (مش يساويها): t-1 (يوم 2) و t-3 (يوم 3) بس
        assertEquals(setOf("t-3", "t-1"), repo.listCreatedAfter("2026-09-01T00:00:00.000Z").map { it.id }.toSet())
    }

    @Test fun findByIdsWorksPastThe30Limit() = run {
        val repo = FirestoreTransactionRepository(space())
        val all = (1..35).map { txn("t-$it", "2026-09-15", it) }
        repo.saveMany(all)
        assertEquals(all.map { it.id }.toSet(), repo.findByIds(all.map { it.id }).map { it.id }.toSet())
        assertEquals(emptyList(), repo.findByIds(emptyList()))
    }

    @Test fun updateSetsAndClearsFields() = run {
        val repo = FirestoreTransactionRepository(space())
        repo.saveMany(listOf(txn("t-1", "2026-09-15", note = "قديمة").copy(categoryId = "c-1")))
        repo.update("t-1", TransactionPatch(note = "رقم 1234567890", categoryConfirmed = true))
        var t = repo.findByIds(listOf("t-1")).single()
        assertEquals("رقم ****7890", t.note, "النص الحر بيتقص في التعديل كمان")
        assertTrue(t.categoryConfirmed)
        repo.update("t-1", TransactionPatch(clearNote = true, clearCategoryId = true))
        t = repo.findByIds(listOf("t-1")).single()
        assertEquals(null, t.note)
        assertEquals(null, t.categoryId)
    }

    @Test fun savingKeepsFieldsItDoesNotKnowAndClearsEmptiedOnes() = run {
        val s = space()
        val repo = FirestoreTransactionRepository(s)
        repo.saveMany(listOf(txn("t-1", "2026-09-15", note = "ملاحظة")))
        // حقل كتبه التطبيق الحالي (أو إصدار أحدث) ومش في كيان كوتلن
        s.collection("transactions").document("t-1").update("legacyField" to "من التطبيق الحالي")
        repo.saveMany(listOf(txn("t-1", "2026-09-15", note = null)))
        val raw = s.collection("transactions").document("t-1").get().rawData()!!
        assertEquals("من التطبيق الحالي", raw["legacyField"], "merge ما يمسحش حقل مش معروف")
        assertFalse("note" in raw, "الحقل اللي اتفضى لازم يتمسح")
    }

    @Test fun deleteManyRemoves() = run {
        val repo = FirestoreTransactionRepository(space())
        repo.saveMany(listOf(txn("t-1", "2026-09-15"), txn("t-2", "2026-09-16")))
        repo.deleteMany(listOf("t-1"))
        assertEquals(listOf("t-2"), repo.listByDateRange("2026-09-01", "2026-09-30").map { it.id })
    }
}
