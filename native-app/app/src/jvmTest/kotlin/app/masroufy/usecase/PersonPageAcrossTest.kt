package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DuesCategories
import app.masroufy.core.EconomicKind
import app.masroufy.core.LABEL_DEFAULT_KIND
import app.masroufy.core.Person
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.countsAsIncome
import app.masroufy.core.personCategoryIds
import app.masroufy.core.seedCategoryId
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonLinkRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** ردود المالك A1 · A3 · A4 + F4 · F6 + السرعة (3,000+ عملية و50 شخص). **كل الأسماء والأرقام مخترعة.** */
class PersonPageAcrossTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
        Texts.language = app.masroufy.core.Language.AR
    }

    private val people = listOf(Person("p-1", "شخص تجريبي")) + (2..50).map { Person("p-$it", "تجريبي رقم $it") }

    private fun t(id: String, dir: Direction, amount: Long, currency: Currency = Currency.SAR, desc: String? = null, category: String? = null, created: String = "2026-03-01T00:00:00.000Z") =
        Transaction(
            id = id, occurredAt = "2026-02-${(id.hashCode().mod(27) + 1).toString().padStart(2, '0')}", datePrecision = "day", sourceOrder = 1,
            economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = currency,
            categoryConfirmed = category != null, excludedFromBudget = false, reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = created,
            updatedAt = created, rawDescription = desc, sourceOperationType = if (desc != null) "عملية تحويل داخلية" else null, categoryId = category, walletId = "w",
        )

    /** بلد واحدة بمستودعاتها في الذاكرة. [txns] ممكن يتلف بعدّاد. */
    private class Space(seed: List<Transaction>, people: List<Person>, categories: List<Category> = emptyList()) {
        val inner = MemoryTransactionRepository(seed)
        var fullReads = 0
        val txns: TransactionRepository = object : TransactionRepository by inner {
            override suspend fun listByDateRange(fromIso: String, toIso: String): List<Transaction> {
                fullReads++
                return inner.listByDateRange(fromIso, toIso)
            }
        }
        val links = MemoryPersonLinkRepository()
        val parties = MemoryTransferPartyRepository()
        val manage = ManagePersonLinks(
            PersonLinksDeps(
                links, MemoryPersonRepository(people), MemoryObligationRepository(), MemorySettlementRepository(), MemoryAllocationRepository(), txns,
                MemoryCategoryRepository(categories), parties, MemoryUnitOfWork(listOf(inner, links, parties)), SequentialIdGenerator(), FixedClock("2026-03-01T10:00:00.000Z"),
            ),
        )
    }

    @Test fun a1AndF4OnlyLoansDebtsDuesAndTransfersToPeopleAskWithWhom() {
        fun c(id: String, parent: String?, name: String) = Category(id, parent, name, "x", "#000000", "#ffffff", true, 1)
        val care = seedCategoryId("العناية الشخصية")
        val tree = listOf(
            c(care, null, "العناية الشخصية"), c(seedCategoryId("العناية الشخصية", "عطور"), care, "عطور"),
            c(seedCategoryId("تحويلات"), null, "تحويلات"), c(seedCategoryId("تحويلات", "تحويل لأشخاص"), seedCategoryId("تحويلات"), "تحويل لأشخاص"),
            c(DuesCategories.ROOT, null, "المستحقات"), c(DuesCategories.FINANCING, DuesCategories.ROOT, "تمويل"),
            c("category-1", null, "سلف الأصحاب"), c("category-2", "category-1", "أخويا"), c("category-3", null, "منتجات شخصية"), c("category-4", null, "قروض"),
            c("category-5", null, "هدايا لأشخاص"), c("category-6", null, "تحويلات للأشخاص"), c("category-7", null, "مستحقات الشغل"), c("category-8", null, "شخصي"),
        )
        assertEquals(
            setOf(seedCategoryId("تحويلات", "تحويل لأشخاص"), DuesCategories.ROOT, "category-1", "category-2", "category-4", "category-6", "category-7"),
            personCategoryIds(tree),
            "«العناية الشخصية» وفروعها و«شخصية» و«هدايا لأشخاص» مش فلوس مع شخص · تمويل البنك تحت المستحقات برّه",
        )
    }

    @Test fun f6TheOwnersCaseNoDebtsButLabelledOperationsStillNeedAPerson() = runBlocking {
        val cat = Category("category-9", null, "سلفة", "x", "#000000", "#ffffff", true, 1)
        val s = Space(listOf(t("t-1", Direction.OUT, 10_000, category = cat.id)), people, listOf(cat))
        assertEquals(listOf("t-1"), s.manage.needsPerson().map { it.transaction.id }, "مفيش ولا دين ⇒ القايمة لسه فيها العملية")
    }

    @Test fun a3EveryCountryWithItsOwnCurrencyNeverSummed() = runBlocking {
        val sa = Space(listOf(t("t-sa", Direction.OUT, 50_000)), people)
        val eg = Space(listOf(t("t-eg", Direction.OUT, 300_000, Currency.EGP)), people)
        sa.manage.link("t-sa", "p-1", PersonMeaning.LENT)
        eg.manage.link("t-eg", "p-1", PersonMeaning.LENT)
        val page = sa.manage.page.load("p-1", listOf(eg.manage))!!
        assertEquals(mapOf(Currency.SAR to 50_000L, Currency.EGP to 300_000L), page.ledger.totals.associate { it.currency to it.owedToYouMinor })
        assertEquals(2, page.ledger.entries.size)
        assertEquals(setOf(50_000L, 300_000L), page.ledger.entries.map { it.netAfterMinor }.toSet(), "الصافي بيتراكم لكل عملة لوحدها")
        assertTrue(page.ledger.open.single { it.obligation.currency == Currency.EGP }.obligation.id.startsWith(ABROAD_ID_PREFIX), "دين البلد التانية ما بيتفتحش من هنا")
        assertEquals(null, page.ledger.entries.single { it.currency == Currency.EGP }.transactionId)
    }

    @Test fun a4OtherIncomeIsIncomeAndHasItsLabelInTheThreeTables() {
        assertEquals(EconomicKind.OTHER_INCOME, LABEL_DEFAULT_KIND)
        assertTrue(countsAsIncome(EconomicKind.OTHER_INCOME))
        assertEquals(EconomicKind.OTHER_INCOME, EconomicKind.fromWire("other_income"))
        val labels = ArabicVariant.entries.map { v -> Texts.arabicVariant = v; uiText(TextKey.KIND_OTHER_INCOME) }
        assertTrue(labels.all { it.isNotBlank() && it.none { c -> c in 'a'..'z' || c in 'A'..'Z' } }, labels.toString())
        assertTrue("دخل تاني" in labels)
        Texts.language = app.masroufy.core.Language.EN
        assertEquals("Other income", uiText(TextKey.KIND_OTHER_INCOME))
    }

    @Test fun speedThePersonPageWith3000OperationsAnd50PeopleIsFastAndDoesNotRereadEverything() = runBlocking {
        val seed = (1..3_200).map { i ->
            val who = i % 50 + 1
            val dir = if (i % 3 == 0) Direction.OUT else Direction.IN
            val tag = if (dir == Direction.IN) "FRACCT" else "TOACCT"
            val last4 = (1000 + who).toString()
            t("t-$i", dir, 1_000L + i, desc = "تجريبيW-/$tag/9999000000${last4}${if (dir == Direction.IN) "FR" else "TO"}:ملاحظة")
        }
        val s = Space(seed, people)
        for (who in 1..50) {
            val ref = transferPartyOf(seed[who - 1])!!
            val owner = "p-${seed[who - 1].id.removePrefix("t-").toInt() % 50 + 1}"
            s.parties.save(TransferParty(ref.key, ref.label, ref.last4, TransferVerdict.PERSON, owner))
        }
        for (i in 1..150) s.manage.link("t-$i", "p-${i % 50 + 1}", if (i % 3 == 0) PersonMeaning.LENT else PersonMeaning.BORROWED)
        s.fullReads = 0
        val first = measureTime { s.manage.page.load("p-1")!! }
        val page = s.manage.page.load("p-1")!!
        val again = measureTime { repeat(5) { s.manage.page.load("p-${it + 2}")!! } }
        assertTrue(first.inWholeMilliseconds < 700, "أول فتحة $first")
        assertTrue(again.inWholeMilliseconds < 700, "خمس فتحات $again")
        assertEquals(1, s.fullReads, "قراية الكل مرة واحدة بس (الفهرس) — مش في كل فتحة")
        assertTrue(page.ledger.entries.size >= 40, "سجل الشخص فيه تحويلات حسابه وروابطه: ${page.ledger.entries.size}")
        assertFalse(page.ledger.isEmpty)
    }
}
