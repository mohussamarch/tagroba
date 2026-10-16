package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Person
import app.masroufy.core.PersonLink
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.ZakatFact
import app.masroufy.core.ZakatSubject
import app.masroufy.core.checkFullBackupData
import app.masroufy.core.emptyBackupData
import app.masroufy.core.mergeFullBackupDetailed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** مراجعة r05 الجولة التانية (R1/N1 · R3 · R6) — النسخة الشاملة. بيانات مخترعة. */
class PersonLinkBackupRound2Test {
    private fun txn(id: String) = Transaction(
        id = id, occurredAt = "2026-02-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.OTHER_INCOME,
        economicKindConfirmed = true, observedDirection = Direction.IN, amountMinor = 50_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c",
        rawDescription = "تجريبي وارد",
    )

    private val person = Person("p-1", "شخص تجريبي")

    private fun backup(transactionId: String, link: PersonLink?, parties: List<TransferParty> = emptyList(), facts: List<ZakatFact> = emptyList()) =
        emptyBackupData().also { d ->
            d.getValue("transactions") += LedgerCodecs.transactions.toStore(txn(transactionId))
            d.getValue("people") += ReferenceCodecs.people.toStore(person)
            link?.let { d.getValue(TransferCodecs.personLinks.group) += TransferCodecs.personLinks.toStore(it) }
            parties.forEach { d.getValue(TransferCodecs.transferParties.group) += TransferCodecs.transferParties.toStore(it) }
            facts.forEach { d.getValue(ZakatCodecs.zakatFacts.group) += ZakatCodecs.zakatFacts.toStore(it) }
        }

    @Test fun r1CompanyAndLabelDecisionsDoNotBlockTheBackup() {
        val company = TransferParty("#1111", "••••1111", "1111", TransferVerdict.COMPANY, null, "2026-03-01", kind = EconomicKind.FREELANCE)
        val label = TransferParty("#2222", "••••2222", "2222", TransferVerdict.LABEL, null, "2026-03-01", kind = EconomicKind.OTHER_INCOME, note = "إيجار تجريبي")
        checkFullBackupData(backup("t-1", null, listOf(company, label)))
    }

    @Test fun r3AZakatFactOnADeletedDebtIsWhatTheBackupRefused() {
        val fact = ZakatFact("obl-gone", ZakatSubject.OBLIGATION, updatedAt = "x")
        // الشاهد: الحقيقة اللي بتفضل بعد مسح الدين (قبل التصليح) النسخة بترفضها — «فك الربط» و«امسح» بيشيلوها دلوقتي (PersonLinksRound2Test)
        kotlin.test.assertFailsWith<app.masroufy.core.BackupError> { checkFullBackupData(backup("t-1", null, facts = listOf(fact))) }
    }

    @Test fun r6AMergedLinkMovesToTheMatchedTransactionId() {
        val incoming = backup("t-old", PersonLink("t-old", "p-1", PersonMeaning.WITH, 50_000, "c"))
        val existing = backup("t-new", null)
        val merge = mergeFullBackupDetailed(incoming, existing)
        assertFalse(merge.additions.getValue("transactions").any { it["id"] == "t-old" }, "نفس العملية بمعرّف تاني ⇒ ما بتتضافش")
        val added = merge.additions.getValue(TransferCodecs.personLinks.group).single()
        assertEquals("t-new" to "t-new", added["id"] to added["transactionId"], "المستند بيتكتب تحت معرّف العملية الجديد ⇒ الفك والمسح بيوصلوله")
    }

    @Test fun a2TheRulesOffMarkRoundTripsAndIsAbsentWhenFalse() {
        val marked = txn("t-1").copy(personRulesOff = true)
        assertEquals(true, LedgerCodecs.transactions.decode(LedgerCodecs.transactions.toStore(marked)).personRulesOff)
        assertFalse("personRulesOff" in LedgerCodecs.transactions.toStore(txn("t-1")), "المستند القديم زي ما هو")
    }
}
