package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * مراجعة دمج 0.5.1 (r051-int) — الدوال النقية: راتب من غير حساب (A-F1 · B-F1) و«السجل بينا» (B-F2).
 * **كل الأسامي والأرقام والرسايل مخترعة** (المستودع عام).
 */
class R051ReviewFixesTest {
    private fun txn(id: String, date: String, desc: String?, amount: Long = 900_000, dir: Direction = Direction.IN, opType: String? = null) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", rawDescription = desc, sourceOperationType = opType,
    )

    private fun fromAccount(id: String, date: String) =
        txn(id, date, "W-/FRACCT/00000000000004444FRTESTCO:ملاحظة", opType = "حوالة واردة داخل البنك")

    private val companyKey = transferPartyOf(fromAccount("k", "2026-01-01"))!!.key

    private fun job(id: String, payers: List<String>, day: Int? = null) =
        IncomeSource(id, "جهة تجريبية", normalizeText("جهة تجريبية"), IncomeSourceKind.JOB, Currency.SAR, "2026-01-01", null, expectedDayOfMonth = day, payerKeys = payers)

    @Test fun aF1TheSoleEmployerGuessNeverConfirmsSalaryOnItsOwn() {
        val salary = txn("t-new", "2026-09-27", "إيداع الراتب الشهري")
        assertNull(transferPartyOf(salary), "الشرط: رسالة راتب من غير حساب مُحوِّل")
        val sole = listOf(job("e-1", listOf(companyKey)))
        assertEquals("e-1", employerOf(salary, sole)?.id, "العرض والسؤال بيفضلوا يشوفوا الجهة الوحيدة")
        assertEquals(salary, applyKnownPayerSalary(salary, sole, "now"), "بس النوع ما بيتأكدش لوحده — رد «ده راتبك؟» هو اللي بيقرر")
        val fromCompany = fromAccount("t-acct", "2026-09-27").copy(sourceOperationType = "حوالة رواتب") // PLAN-0.6 §7: بكلمة راتب
        assertTrue(applyKnownPayerSalary(fromCompany, sole, "now").economicKindConfirmed, "الحساب المعروف لسه بيأكد «راتب» زي الأول")
        val picked = listOf(job("e-1", listOf(companyKey, salaryTxnKey("t-new"))))
        assertEquals(EconomicKind.SALARY, applyKnownPayerSalary(salary, picked, "now").economicKind, "اختيار المالك للعملية دي بإيده بيأكد")
    }

    @Test fun aF1LinkedAccountWordingIsNotSalaryText() {
        val linked = txn("t-dep", "2026-09-27", "إيداع نقدي في حسابك المرتبط بالبطاقة")
        assertNull(transferPartyOf(linked))
        assertFalse(isPartylessSalary(linked), "«المرتبط» مش «مرتب»")
        assertFalse(isPartylessSalary(txn("t-dep2", "2026-09-27", "تحويل لحساباتك المرتبطة")))
        assertFalse(isPartylessSalary(txn("t-dep3", "2026-09-27", "عرض المراتب التجريبي")))
        assertTrue(isPartylessSalary(txn("t-s1", "2026-09-27", "إيداع مرتب شهر سبتمبر")))
        assertTrue(isPartylessSalary(txn("t-s2", "2026-09-27", "وصل راتبك")))
        assertNull(employerOf(linked, listOf(job("e-1", listOf(companyKey)))), "ولا جهة بتتخمّن ليه")
    }

    @Test fun bF1APickedTransactionIsNotAKnownPayerAccount() {
        val onlyPick = job("e-1", listOf(salaryTxnKey("t-old")), day = 27)
        assertEquals(emptyList(), partyPayerKeys(onlyPick))
        assertEquals(emptyList(), payerAccountsOf(onlyPick, emptyMap()), "مفيش سطر حساب بمعرّف عملية")
        val today = "2026-10-05"
        assertEquals(emptyList(), lateIncomeCandidates(listOf(onlyPick), emptyList(), today), "مش معروف مين بيحوّل ⇒ ما نقولش «ما وصلش» (قاعدة 10)")
        val withAccount = job("e-1", listOf(companyKey, salaryTxnKey("t-sep")), day = 27)
        assertEquals(listOf(companyKey), payerAccountsOf(withAccount, emptyMap()).map { it.key })
        val arrived = txn("t-sep", "2026-09-27", "إيداع الراتب الشهري")
        assertEquals("e-1", attributedSourceId(arrived, listOf(withAccount)), "الراتب اللي المالك اختار جهته منسوب لها")
        assertEquals(emptyList(), lateIncomeCandidates(listOf(withAccount), listOf(arrived), today), "وصل ⇒ مفيش «اتأخر»")
        assertEquals(1, lateIncomeCandidates(listOf(withAccount), emptyList(), today).size, "ما وصلش ⇒ التنبيه زي الأول")
    }

    @Test fun bF2ADebtFromALinkedTransactionThatTheLinkDidNotMakeStaysInTheHistory() {
        val t1 = txn("t-1", "2026-02-01", "شراء تجريبي", amount = 30_000, dir = Direction.OUT)
        val t2 = txn("t-2", "2026-03-01", "تحويل تجريبي", amount = 10_000, dir = Direction.OUT)
        val links = listOf(
            PersonLink("t-1", "p-1", PersonMeaning.WITH, 30_000, "c"),
            PersonLink("t-2", "p-1", PersonMeaning.LENT, 10_000, "c", obligationId = "obl-2"),
        )
        val obligations = listOf(
            Obligation("obl-1", "p-1", "t-1", ObligationKind.RECEIVABLE, 30_000, Currency.SAR),
            Obligation("obl-2", "p-1", "t-2", ObligationKind.RECEIVABLE, 10_000, Currency.SAR),
        )
        val ledger = personLedger(links, obligations, emptyList(), mapOf("t-1" to t1, "t-2" to t2))
        val debt = ledger.entries.single { it.obligationId == "obl-1" && it.type == PersonEntryType.DEBT }
        assertEquals(30_000L, debt.effectMinor, "دين «حد تاني» على نفس العملية ليه سطره")
        assertTrue(ledger.entries.none { it.type == PersonEntryType.DEBT && it.obligationId == "obl-2" }, "دين الرابط نفسه جوه سطر الرابط بس")
        assertEquals(40_000L, ledger.entries.first().netAfterMinor, "الصافي في السجل = «لك عنده»")
        assertEquals(40_000L, ledger.totals.single().netMinor)
    }
}
