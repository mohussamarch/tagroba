package app.masroufy.data

import app.masroufy.core.AutoMeaning
import app.masroufy.core.BackupError
import app.masroufy.core.Currency
import app.masroufy.core.DebtTerms
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Person
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.checkFullBackupData
import app.masroufy.core.emptyBackupData
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
import app.masroufy.usecase.DeleteDebt
import app.masroufy.usecase.DeleteDebtDeps
import app.masroufy.usecase.ManagePersonLinks
import app.masroufy.usecase.PersonLinksDeps
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * مراجعة r05 F1 · F2: مسح الدين من «الديون» أو «فك الربط» بعد ميعاد ⇒ النسخة الشاملة **بتتعمل** (كانت بترفض `BACKUP_RELATION_MISSING`) ·
 * A2: الممنوع على قواعد الحساب بيتحفظ · A4: «دخل تاني» في النسخة. بيانات مخترعة.
 */
class PersonLinkBackupTest {
    private val out = Transaction(
        id = "t-out", occurredAt = "2026-02-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = Direction.OUT, amountMinor = 50_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "c", updatedAt = "c",
        rawDescription = "تجريبيW-/TOACCT/99990000005678TO:ملاحظة", sourceOperationType = "عملية تحويل داخلية",
    )
    private val person = Person("p-1", "شخص تجريبي")
    private val txns = MemoryTransactionRepository(listOf(out))
    private val links = MemoryPersonLinkRepository()
    private val obligations = MemoryObligationRepository()
    private val settlements = MemorySettlementRepository()
    private val allocations = MemoryAllocationRepository()
    private val terms = MemoryDebtTermsRepository()
    private val parties = MemoryTransferPartyRepository()
    private val clock = FixedClock("2026-03-01T10:00:00.000Z")
    private val uow = MemoryUnitOfWork(listOf(txns, links, parties))
    private val manage = ManagePersonLinks(
        PersonLinksDeps(
            links, MemoryPersonRepository(listOf(person)), obligations, settlements, allocations, txns, MemoryCategoryRepository(emptyList()), parties, uow,
            SequentialIdGenerator(), clock, terms = terms,
        ),
    )

    private suspend fun backup() = emptyBackupData().also { d ->
        txns.findByIds(listOf("t-out")).forEach { d.getValue("transactions") += LedgerCodecs.transactions.toStore(it) }
        d.getValue("people") += ReferenceCodecs.people.toStore(person)
        val obs = obligations.listByPerson("p-1")
        obs.forEach { d.getValue("obligations") += LedgerCodecs.obligations.toStore(it) }
        settlements.listByObligations(obs.map { it.id }).forEach { d.getValue("settlements") += LedgerCodecs.settlements.toStore(it) }
        allocations.listByTransactionIds(listOf("t-out")).forEach { d.getValue("allocations") += LedgerCodecs.allocations.toStore(it) }
        terms.listAll().forEach { d.getValue(DuesCodecs.debtTerms.group) += DuesCodecs.debtTerms.toStore(it) }
        links.all().forEach { d.getValue(TransferCodecs.personLinks.group) += TransferCodecs.personLinks.toStore(it) }
    }

    @Test fun f1DeletingALinkedDebtLeavesABackupThatPasses() = runBlocking<Unit> {
        val link = manage.link("t-out", "p-1", PersonMeaning.LENT).link
        val debt = obligations.listByPerson("p-1").single()
        terms.save(DebtTerms(debt.id, "p-1", "2026-04-01"))
        checkFullBackupData(backup())
        DeleteDebt(DeleteDebtDeps(obligations, settlements, allocations, uow, terms, links, txns, clock)).delete("p-1", debt.id)
        checkFullBackupData(backup())
        // الشاهد: الربط المقطوع اللي كان بيفضل قبل التصليح النسخة بترفضه
        links.saveMany(listOf(link))
        assertFailsWith<BackupError> { checkFullBackupData(backup()) }
    }

    @Test fun f2UnlinkAfterSettingADueDateLeavesABackupThatPasses() = runBlocking<Unit> {
        manage.link("t-out", "p-1", PersonMeaning.LENT)
        terms.save(DebtTerms(obligations.listByPerson("p-1").single().id, "p-1", "2026-04-01"))
        manage.unlink("t-out")
        checkFullBackupData(backup())
    }

    @Test fun a2TheSkipListAndA4OtherIncomeRoundTrip() {
        val codec = TransferCodecs.transferParties
        val party = TransferParty("#5678", "••••5678", "5678", TransferVerdict.PERSON, "p-1", "2026-03-01", AutoMeaning.GIFT, "2026-03-01", autoSkip = listOf("t-9"))
        assertEquals(party, codec.decode(codec.toStore(party)))
        assertFalse("autoSkip" in codec.toStore(party.copy(autoSkip = emptyList())), "المستند القديم زي ما هو")
        val income = out.copy(economicKind = EconomicKind.OTHER_INCOME, observedDirection = Direction.IN, economicKindConfirmed = true)
        // الكودك بيخبّي رقم الحساب الكامل في الوصف (القاعدة 11) ⇒ المقارنة على النوع والاتجاه والتأكيد
        val stored = LedgerCodecs.transactions.toStore(income)
        assertEquals("other_income", stored["economicKind"])
        val back = LedgerCodecs.transactions.decode(stored)
        assertEquals(Triple(EconomicKind.OTHER_INCOME, Direction.IN, true), Triple(back.economicKind, back.observedDirection, back.economicKindConfirmed))
    }
}
