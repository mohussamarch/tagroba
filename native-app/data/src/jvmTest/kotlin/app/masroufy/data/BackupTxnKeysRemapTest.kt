package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.emptyBackupData
import app.masroufy.core.mergeFullBackupDetailed
import app.masroufy.core.salaryTxnKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * مراجعة دمج 0.5.1 (B-F6): «أضف الناقص» من نسخة جهاز تاني — العمليات اللي اتطابقت بالمحتوى بمعرّف تاني لازم تتغير كمان جوه «ما ترجعش تربطها»
 * (`autoSkip`) واختيار جهة الراتب و«مش دي» (`txn:<معرّف>`). **كل البيانات مخترعة.**
 */
class BackupTxnKeysRemapTest {
    private fun txn(id: String, amount: Long, desc: String) = Transaction(
        id = id, occurredAt = "2026-02-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = Direction.IN, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "c", updatedAt = "c", rawDescription = desc,
    )

    private val party = TransferParty("#7777", "••••7777", "7777", TransferVerdict.PERSON, null, "2026-03-01", autoSkip = listOf("a-1", "a-gone"))
    private val employer = IncomeSource(
        "e-1", "جهة تجريبية", "جهة تجريبية", IncomeSourceKind.JOB, Currency.SAR, "2026-01-01", null,
        payerKeys = listOf("#7777", salaryTxnKey("a-9")), declinedPayerKeys = listOf(salaryTxnKey("a-8")),
    )

    @Test fun matchedTransactionIdsMoveInsideAutoSkipAndSalaryPicks() {
        val incoming = emptyBackupData().also { d ->
            listOf(txn("a-1", 10_000, "تحويل تجريبي"), txn("a-9", 900_000, "إيداع الراتب"), txn("a-8", 50_000, "إيداع راتب قريب"))
                .forEach { d.getValue("transactions") += LedgerCodecs.transactions.toStore(it) }
            d.getValue(TransferCodecs.transferParties.group) += TransferCodecs.transferParties.toStore(party)
            d.getValue(IncomeCodecs.incomeSources.group) += IncomeCodecs.incomeSources.toStore(employer)
        }
        val existing = emptyBackupData().also { d ->
            listOf(txn("b-1", 10_000, "تحويل تجريبي"), txn("b-9", 900_000, "إيداع الراتب"), txn("b-8", 50_000, "إيداع راتب قريب"))
                .forEach { d.getValue("transactions") += LedgerCodecs.transactions.toStore(it) }
        }
        val merge = mergeFullBackupDetailed(incoming, existing)
        assertEquals(emptyList(), merge.additions.getValue("transactions"), "نفس العمليات بمعرّفات تانية ⇒ ما بتتضافش")
        val addedParty = TransferCodecs.transferParties.decode(merge.additions.getValue(TransferCodecs.transferParties.group).single())
        assertEquals(listOf("b-1", "a-gone"), addedParty.autoSkip, "المعرّف اللي مش في الملف بيفضل زي ما هو")
        val addedEmployer = IncomeCodecs.incomeSources.decode(merge.additions.getValue(IncomeCodecs.incomeSources.group).single())
        assertEquals(listOf("#7777", salaryTxnKey("b-9")), addedEmployer.payerKeys, "الحساب زي ما هو · اختيار الراتب على العملية الموجودة")
        assertEquals(listOf(salaryTxnKey("b-8")), addedEmployer.declinedPayerKeys)
    }
}
