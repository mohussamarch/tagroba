package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.AllocationKind
import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Currency
import app.masroufy.core.Merchant
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonAllocation
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.Settlement
import app.masroufy.core.Wallet
import app.masroufy.core.normalizeText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** التصنيفات والقواعد والتجار والمحافظ والأشخاص والديون على محاكي أندرويد قدام Firestore Emulator. بيانات وهمية. */
@RunWith(AndroidJUnit4::class)
class ReferenceAndPeopleRepositoriesTest {
    private fun space() = FirestoreSpace.forUser(Emulator.firestore(), "kt-" + java.util.UUID.randomUUID())

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(60_000) { block() } }

    @Test fun categoriesAndRulesComeBackInTheCurrentAppOrder() = run {
        val s = space()
        val categories = FirestoreCategoryRepository(s)
        categories.save(Category("c-b", null, "ب", "i", "#111111", "#222222", true, 2))
        categories.save(Category("c-a", "c-b", "أ", "i", "#111111", "#222222", true, 1))
        assertEquals(listOf("c-a", "c-b"), categories.listAll().map { it.id })
        val rules = FirestoreRuleRepository(s)
        rules.saveMany(listOf(ClassificationRule("r-2", 5, "b", RuleMatchMode.EXACT, "c-b", true), ClassificationRule("r-1", 1, "a", RuleMatchMode.CONTAINS, "c-a", false)))
        assertEquals(listOf("r-1", "r-2"), rules.listAll().map { it.id })
        rules.deleteMany(listOf("r-1"))
        assertEquals(listOf("r-2"), rules.listAll().map { it.id })
    }

    @Test fun merchantIsFoundByNameThenByAlias() = run {
        val merchants = FirestoreMerchantRepository(space())
        // المخزن دايمًا مطبّع (`normalizeText` بيكبّر الحروف اللاتيني) — زي ما التطبيق بيحفظ التاجر
        merchants.saveMany(listOf(Merchant("m-1", "Test Mart", normalizeText("Test Mart"), aliases = listOf(normalizeText("تست مارت"))), Merchant("m-2", "Cafe", normalizeText("Cafe"))))
        assertEquals("m-1", merchants.findByNormalizedName("TEST  MART")?.id, "بالاسم الأساسي")
        assertEquals("m-1", merchants.findByNormalizedName("تست مارت")?.id, "بالاسم البديل")
        assertNull(merchants.findByNormalizedName("مش موجود"))
    }

    @Test fun walletKeepsOnlyTheLastFourDigits() = run {
        val s = space()
        val wallets = FirestoreWalletRepository(s)
        wallets.save(Wallet("w-1", "بنك وهمي", Currency.SAR, "bank", 100, "2025-01-01", accountLast4 = "SA12 3456 7890 1234"))
        wallets.save(Wallet("w-2", "كاش", Currency.SAR, "cash", 0, "2026-09-06", accountLast4 = "بدون أرقام"))
        assertEquals("1234", wallets.findById("w-1")?.accountLast4)
        assertFalse("accountLast4" in s.collection("wallets").document("w-2").get().rawData()!!, "من غير أرقام الحقل ما بيتكتبش")
        assertNull(wallets.findById("مش-موجودة"))
        assertEquals(2, wallets.listAll().size)
    }

    @Test fun peopleDebtsAndSettlementsRoundTrip() = run {
        val s = space()
        FirestorePersonRepository(s).save(Person("p-1", "شخص وهمي"))
        assertEquals(listOf("p-1"), FirestorePersonRepository(s).listAll().map { it.id })
        val obligations = FirestoreObligationRepository(s)
        obligations.saveMany(listOf(Obligation("o-1", "p-1", "t-1", ObligationKind.RECEIVABLE, 5_000, Currency.SAR), Obligation("o-2", "p-1", null, ObligationKind.LOAN_PAYABLE, 1, Currency.SAR)))
        assertEquals(setOf("o-1", "o-2"), obligations.listByPerson("p-1").map { it.id }.toSet())
        assertEquals(listOf("o-1"), obligations.listByTransactionIds(listOf("t-1")).map { it.id })
        val settlements = FirestoreSettlementRepository(s)
        settlements.saveMany(listOf(Settlement("s-1", "t-2", "o-1", 2_000)))
        assertEquals(listOf("s-1"), settlements.listByObligations(listOf("o-1", "o-2")).map { it.id })
        val allocations = FirestoreAllocationRepository(s)
        allocations.saveMany(listOf(PersonAllocation("al-1", "t-1", "p-1", AllocationKind.RECEIVABLE, 5_000, Currency.SAR)))
        assertEquals(5_000, allocations.listByTransactionIds(listOf("t-1")).single().amountMinor)
    }
}
