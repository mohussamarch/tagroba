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

    /**
     * الشريحة S3 (§75-6 · §75-12 · §77-D): الحقول الجديدة بتتحفظ وترجع، والتعديل بيكتبها ويمسحها. معرّف فيه 6 أرقام ورا بعض في
     * `reversalOfId`/`reversedById` **ما بيتقصش** (آخر الاسم `Id` — قص أرقام الحسابات بيعدّي عليه).
     */
    @Test fun newFieldsRoundTripAndPatch() = run {
        val s = space()
        val repo = FirestoreTransactionRepository(s)
        val ret = txn("txn-000001", "2026-10-05").copy(
            observedDirection = Direction.IN, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
            suggestedKind = EconomicKind.REFUND_RECEIVED, foreignCurrency = "USD", foreignAmountMinor = 2_340,
        )
        val original = txn("txn-000002", "2026-10-02")
        repo.saveMany(listOf(ret, original))
        assertEquals(ret, repo.findByIds(listOf(ret.id)).single())
        repo.update(ret.id, TransactionPatch(economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, clearSuggestedKind = true, reversalOfId = original.id))
        repo.update(original.id, TransactionPatch(economicKind = EconomicKind.INTERNAL_TRANSFER, reversedById = ret.id))
        val r = repo.findByIds(listOf(ret.id)).single()
        assertEquals(original.id, r.reversalOfId)
        assertEquals(null, r.suggestedKind)
        assertEquals(ret.id, repo.findByIds(listOf(original.id)).single().reversedById)
        val raw = s.collection("transactions").document(ret.id).get().rawData()!!
        assertEquals("txn-000002", raw["reversalOfId"])
        assertFalse("suggestedKind" in raw, "الاقتراح اتمسح")
        assertEquals(2_340L, raw["foreignAmountMinor"])
        repo.update(ret.id, TransactionPatch(clearReversalOfId = true, suggestedKind = EconomicKind.REFUND_RECEIVED))
        repo.update(original.id, TransactionPatch(clearReversedById = true))
        assertEquals(null to EconomicKind.REFUND_RECEIVED, repo.findByIds(listOf(ret.id)).single().let { it.reversalOfId to it.suggestedKind })
        assertFalse("reversedById" in s.collection("transactions").document(original.id).get().rawData()!!)
        // مستند من غير الحقول (زي التطبيق القديم) بيتقري زي ما هو
        val plain = txn("t-plain", "2026-10-01")
        repo.saveMany(listOf(plain))
        assertEquals(plain, repo.findByIds(listOf("t-plain")).single())
    }

    @Test fun deleteManyRemoves() = run {
        val repo = FirestoreTransactionRepository(space())
        repo.saveMany(listOf(txn("t-1", "2026-09-15"), txn("t-2", "2026-09-16")))
        repo.deleteMany(listOf("t-1"))
        assertEquals(listOf("t-2"), repo.listByDateRange("2026-09-01", "2026-09-30").map { it.id })
    }
}
