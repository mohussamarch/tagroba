package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * أسئلة التحويل مع شخص مربوط (قرارات المالك §75-5 و§75-9) — منطق صافي. كل الأسامي والمبالغ مخترعة.
 */
class TransferAsksTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val person = TransferParty("testperson#1111", "TEST PERSON", "1111", TransferVerdict.PERSON, "p-1")

    private fun t(dir: Direction, amount: Halalas, confirmed: Boolean = false, currency: Currency = Currency.SAR, id: String = "t-new") = Transaction(
        id = id, occurredAt = "2026-10-07", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = confirmed, observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private fun debt(id: String, kind: ObligationKind, amount: Halalas, origin: String? = null, personId: String = "p-1", currency: Currency = Currency.SAR) =
        Obligation(id, personId, origin, kind, amount, currency)

    @Test fun outgoingToAPersonAsksLoanOrSupportEveryTime() {
        assertEquals(AskKind.LOAN_OR_SUPPORT, transferAskOf(t(Direction.OUT, 50_000), person, emptyList(), emptyList()))
        assertEquals(AskKind.LOAN_OR_SUPPORT, transferAskOf(t(Direction.OUT, 50_000, id = "t-2"), person, emptyList(), emptyList()), "كل مرة")
        assertNull(transferAskOf(t(Direction.OUT, 50_000, confirmed = true), person, emptyList(), emptyList()), "النوع المؤكد ⇒ مفيش سؤال")
        assertNull(transferAskOf(t(Direction.IN, 50_000), person, emptyList(), emptyList()), "الوارد من غير دين ⇒ يتسأل عن نوعه (§39.1) مش هنا")
    }

    @Test fun onlyAPersonVerdictAsks() {
        for (verdict in listOf(TransferVerdict.OWN_ACCOUNT, TransferVerdict.DISMISSED)) {
            assertNull(transferAskOf(t(Direction.OUT, 50_000), person.copy(verdict = verdict), emptyList(), emptyList()), verdict.wire)
        }
        assertNull(transferAskOf(t(Direction.OUT, 50_000), person.copy(personId = null), emptyList(), emptyList()), "شخص من غير معرّف ⇒ ولا سؤال")
        assertNull(transferAskOf(t(Direction.OUT, 50_000), null, emptyList(), emptyList()))
    }

    @Test fun anOpenDebtTurnsTheQuestionIntoRepayment() {
        val lent = debt("o-1", ObligationKind.RECEIVABLE, 50_000)
        val owed = debt("o-2", ObligationKind.LOAN_PAYABLE, 100_000)
        assertEquals(AskKind.DEBT_REPAYMENT, transferAskOf(t(Direction.IN, 30_000), person, listOf(lent), emptyList()), "ليك عنده ⇒ «ده سداد السلفة؟»")
        assertEquals(AskKind.DEBT_REPAYMENT, transferAskOf(t(Direction.OUT, 30_000), person, listOf(owed), emptyList()), "ليه عندك ⇒ «ده سداد دين عليك؟»")
        // الاتجاه لازم يطابق نوع الدين: ليك عنده وإنت بتحوّل ⇒ «سلفة ولا دعم؟»
        assertEquals(AskKind.LOAN_OR_SUPPORT, transferAskOf(t(Direction.OUT, 30_000), person, listOf(lent), emptyList()))
        assertNull(transferAskOf(t(Direction.IN, 30_000), person, listOf(owed), emptyList()))
        // «لأ، مش سداد» ⇒ الصادر يرجع «سلفة ولا دعم؟» والوارد مالوش سؤال هنا
        assertEquals(AskKind.LOAN_OR_SUPPORT, transferAskOf(t(Direction.OUT, 30_000), person, listOf(owed), emptyList(), debtRuledOut = true))
        assertNull(transferAskOf(t(Direction.IN, 30_000), person, listOf(lent), emptyList(), debtRuledOut = true))
    }

    @Test fun closedOtherCurrencyOtherPersonOrCustodyDebtsDoNotCount() {
        val paid = debt("o-1", ObligationKind.RECEIVABLE, 50_000)
        val settled = listOf(Settlement("s-1", "t-old", "o-1", 50_000))
        assertNull(transferAskOf(t(Direction.IN, 30_000), person, listOf(paid), settled), "اتسدد بالكامل")
        assertNull(transferAskOf(t(Direction.IN, 30_000), person, listOf(debt("o-3", ObligationKind.RECEIVABLE, 50_000, currency = Currency.EGP)), emptyList()), "عملة تانية")
        assertNull(transferAskOf(t(Direction.IN, 30_000), person, listOf(debt("o-4", ObligationKind.RECEIVABLE, 50_000, personId = "p-2")), emptyList()), "شخص تاني")
        assertEquals(AskKind.LOAN_OR_SUPPORT, transferAskOf(t(Direction.OUT, 30_000), person, listOf(debt("o-5", ObligationKind.CUSTODY_PAYABLE, 50_000)), emptyList()), "الأمانة ليها نوعها")
        // تسوية العملية نفسها ما بتقفلش سؤالها (الإجابة اللي اتقطعت تكمل)
        val own = listOf(Settlement("s-2", "t-new", "o-1", 50_000))
        assertEquals(AskKind.DEBT_REPAYMENT, transferAskOf(t(Direction.IN, 50_000), person, listOf(paid), own))
    }

    @Test fun repaymentGoesToTheOldestDebtFirst() {
        val debts = listOf(
            debt("o-b", ObligationKind.RECEIVABLE, 30_000, origin = "t-feb"),
            debt("o-a", ObligationKind.RECEIVABLE, 20_000, origin = "t-jan"),
            debt("o-z", ObligationKind.RECEIVABLE, 10_000, origin = null),
        )
        val dates = mapOf("t-jan" to "2026-01-05", "t-feb" to "2026-02-05")
        val open = openDebtsFor(t(Direction.IN, 25_000), "p-1", debts, emptyList(), dates)
        assertEquals(listOf("o-z", "o-a", "o-b"), open.map { it.obligation.id }, "الدين القديم من غير عملية (§27) الأول، وبعده بالتاريخ")
        val plan = assertIs<RepaymentPlan.Settle>(planRepayment(25_000, open))
        assertEquals(listOf("o-z" to 10_000L, "o-a" to 15_000L), plan.parts.map { it.first.id to it.second })
        val all = assertIs<RepaymentPlan.Settle>(planRepayment(60_000, open))
        assertEquals(listOf(10_000L, 20_000L, 30_000L), all.parts.map { it.second }, "بالظبط قد المفتوح ⇒ كله يتقفل")
    }

    @Test fun moreThanTheOpenDebtIsRefusedWithTheExistingMessage() {
        val lent = debt("o-1", ObligationKind.RECEIVABLE, 50_000)
        val open = openDebtsFor(t(Direction.IN, 60_000), "p-1", listOf(lent), emptyList())
        val refused = assertIs<RepaymentPlan.Refused>(planRepayment(60_000, open))
        assertEquals(checkSettlement(lent, emptyList(), 60_000).reason, refused.reason, "نفس رسالة الزيادة حرفيًا")
        assertEquals(uiText(TextKey.SETTLEMENT_OVER_REMAINING, uiText(TextKey.OBLIGATION_RECEIVABLE), formatMoney(50_000), formatMoney(60_000), formatMoney(10_000)), refused.reason)
        assertIs<RepaymentPlan.Refused>(planRepayment(1, emptyList()))
    }

    @Test fun questionTextsExistInEveryVariant() {
        forEachTextVariant { label ->
            for (key in listOf(TextKey.ASK_LOAN_OR_SUPPORT, TextKey.ASK_DEBT_COLLECTED, TextKey.ASK_DEBT_REPAID, TextKey.ASK_TRANSFER_NOT_PENDING)) {
                assertEquals(false, uiText(key).isBlank() || uiText(key) == key.name, "$label $key")
            }
            val body = uiText(TextKey.ASK_DEBT_COLLECTED_BODY, "300", "TEST PERSON", "500")
            assertEquals(true, "300" in body && "TEST PERSON" in body && "500" in body, "$label: $body")
        }
    }
}
