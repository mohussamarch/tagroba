package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Person
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.remainingOfObligation
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonLinkRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** مراجعة دمج 0.5.1 (r051-int) — الأشخاص: ترتيب «مستحقات دايمًا» (A-F3) · سداد دين اتعمل بعدها (B-F3) · الدمج (B-F5). **كل الأسامي والأرقام مخترعة.** */
class R051PersonFixesTest {
    private val fromHim = "تجريبيW-/FRACCT/99990000007777FR:ملاحظة"

    private fun t(id: String, dir: Direction, date: String, amount: Long, desc: String = fromHim) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "2026-01-01T00:00:00.000Z", updatedAt = "x", rawDescription = desc,
        sourceOperationType = "عملية تحويل داخلية", walletId = "w-bank",
    )

    private val lent = t("t-lent", Direction.OUT, "2026-01-10", 100_000, "تجريبيW-/TOACCT/99990000007777TO:ملاحظة")
    private val feb = t("t-feb", Direction.IN, "2026-02-10", 80_000)
    private val mar = t("t-mar", Direction.IN, "2026-03-10", 80_000)
    private val earlyIn = t("t-early", Direction.IN, "2026-01-05", 30_000)

    private val txns = MemoryTransactionRepository(listOf(lent, feb, mar, earlyIn))
    private val links = MemoryPersonLinkRepository()
    private val obligations = MemoryObligationRepository()
    private val settlements = MemorySettlementRepository()
    private val parties = MemoryTransferPartyRepository()
    private val uow = MemoryUnitOfWork(listOf(txns, links, parties))
    private val deps = PersonLinksDeps(
        links, MemoryPersonRepository(listOf(Person("p-1", "شخص تجريبي"))), obligations, settlements, MemoryAllocationRepository(), txns,
        MemoryCategoryRepository(emptyList()), parties, uow, SequentialIdGenerator(), FixedClock("2026-03-20T10:00:00.000Z"), terms = MemoryDebtTermsRepository(),
    )
    private val manage = ManagePersonLinks(deps)

    private suspend fun duesParty() = transferPartyOf(feb)!!.let { ref ->
        parties.save(TransferParty(ref.key, ref.label, ref.last4, TransferVerdict.PERSON, "p-1", "2026-01-01", dues = true))
    }

    private suspend fun linkOf(id: String) = links.listByTransactionIds(listOf(id)).singleOrNull()

    @Test fun aF3DuesSettleOldestFirstEvenWhenTheNewerTransferIsRecordedFirst() = runBlocking {
        manage.link("t-lent", "p-1", PersonMeaning.LENT)
        duesParty()
        // الصفحة الأحدث في الصندوق بتتسجل الأول: مارس لوحده
        assertEquals(1, manage.rules.onRecorded(listOf("t-mar")))
        assertEquals(PersonMeaning.COLLECTED, linkOf("t-mar")?.meaning)
        // وبعدها فبراير (الصفحة الأقدم) ⇒ فبراير هو السداد، ومارس (أكبر من اللي فاضل) بيرجع سؤال
        assertEquals(1, manage.rules.onRecorded(listOf("t-feb")))
        assertEquals(PersonMeaning.COLLECTED, linkOf("t-feb")?.meaning, "الأقدم الأول — زي رد «مستحقات دايمًا»")
        assertEquals(null, linkOf("t-mar"), "مارس اتفك ورجع سؤال «ده سداد؟»")
        val marNow = txns.findByIds(listOf("t-mar")).single()
        assertFalse(marNow.economicKindConfirmed, "نوعه رجع زي ما كان")
        assertTrue(marNow.personRulesOff != true && parties.listAll().single().autoSkip.isEmpty(), "الفك مش من المالك ⇒ ما بيتمنعش")
        val debt = obligations.listByPerson("p-1").single()
        assertEquals(20_000L, remainingOfObligation(debt, settlements.listByObligations(listOf(debt.id))))
    }

    @Test fun bF3MoneyThatCameBeforeTheLoanDoesNotRepayIt() = runBlocking {
        manage.link("t-lent", "p-1", PersonMeaning.LENT)
        val out = manage.link("t-early", "p-1", PersonMeaning.COLLECTED)
        assertEquals(30_000L, out.unmatchedMinor, "فلوس ٥ يناير ما بتسددش سلفة ١٠ يناير")
        assertTrue(out.link.settlementIds.isEmpty())
        val debt = obligations.listByPerson("p-1").single()
        assertEquals(100_000L, remainingOfObligation(debt, settlements.listByObligations(listOf(debt.id))))
        // اللي بعد الدين بيسدده زي الأول
        assertEquals(0L, manage.link("t-feb", "p-1", PersonMeaning.COLLECTED).unmatchedMinor)
    }

    @Test fun bF5TheUnlinkedMarkSurvivesAMergeAndTheRulesStayOff() = runBlocking {
        manage.link("t-lent", "p-1", PersonMeaning.LENT)
        duesParty()
        val statementLine = feb.copy(id = "t-stmt")
        val unlinkedSms = feb.copy(id = "t-sms", personRulesOff = true)
        val kept = keptAfter(statementLine, unlinkedSms, statementWins = false, now = "2026-03-20T10:00:00.000Z")
        assertEquals(true, kept.personRulesOff, "«فكّيت ربطها» بيتنقل على اللي فاضلة")
        txns.saveMany(listOf(kept))
        assertEquals(0, manage.rules.onRecorded(listOf("t-stmt")), "القاعدة ما بترجعش تربط العملية اللي المالك فك ربطها")
        assertFalse(manage.rules.dues(kept))
        assertEquals(0, manage.accounts.applyAuto(listOf("t-stmt")))
        assertEquals(null, linkOf("t-stmt"))
    }
}
