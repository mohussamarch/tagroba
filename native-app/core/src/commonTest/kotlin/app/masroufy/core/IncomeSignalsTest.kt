package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * مين بيحوّل المرتب، والمرتب المتأخر (OVERRIDES §48 · §64) — كل الأسامي والمبالغ مخترعة، بنفس شكل «سريع وارد» في كشف الراجحي.
 */
class IncomeSignalsTest {
    private var seq = 0

    private fun deposit(company: String, date: String, amount: Long = 1_000_000, kind: EconomicKind = EconomicKind.UNCLASSIFIED, confirmed: Boolean = false, currency: Currency = Currency.SAR, dir: Direction = Direction.IN) =
        Transaction(
            id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = confirmed,
            observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
            rawDescription = "PAYROLL-PA1234:ملاحظة | سامي-INMAINM1234567RJ-$company | x", sourceOperationType = "حواالت سريع الواردة",
        )

    private val star = "شركة النجمة الوهمية"
    private val moon = "شركة القمر الوهمية"
    private val starKey = transferPartyOf(deposit(star, "2026-01-01"))!!.key

    private fun src(id: String, from: String = "2026-01-01", to: String? = null, kind: IncomeSourceKind = IncomeSourceKind.JOB, payers: List<String> = emptyList(), declined: List<String> = emptyList(), day: Int? = null, amount: Long? = null) =
        IncomeSource(id, "مصدر $id", "مصدر $id", kind, Currency.SAR, from, to, day, amount, payerKeys = payers, declinedPayerKeys = declined)

    @Test fun salaryLikeIsAnIncomingDepositNobodyCalledSomethingElse() {
        assertTrue(isSalaryLike(deposit(star, "2026-02-27")))
        assertTrue(isSalaryLike(deposit(star, "2026-02-27", kind = EconomicKind.SALARY, confirmed = true)))
        assertFalse(isSalaryLike(deposit(star, "2026-02-27", kind = EconomicKind.LOAN_RECEIVED, confirmed = true)))
        assertFalse(isSalaryLike(deposit(star, "2026-02-27", dir = Direction.OUT)))
    }

    @Test fun oneQuestionPerUnknownPartyOnItsFirstDepositWhileAJobIsRunning() {
        val first = deposit(star, "2026-02-27")
        val txns = listOf(deposit(star, "2025-12-27"), first, deposit(star, "2026-03-27", 1_100_000), deposit(moon, "2026-03-05", kind = EconomicKind.GIFT_RECEIVED, confirmed = true))
        val q = payerQuestions(txns, listOf(src("a"))).single()
        assertEquals(PayerQuestion(TransferPartyRef(starKey, star, null), "a", "مصدر a", first.id, "2026-02-27"), q, "قبل الشغل ما يبدأ مفيش سؤال، وبعده أول إيداع بس")
        assertEquals("ده مرتب من «مصدر a»؟", payerQuestionText(q))
        // الطرف المعروف ما بيتسألش، والطرف اللي اتقرر في زون التحويلات كمان
        assertTrue(payerQuestions(txns, listOf(src("a", payers = listOf(starKey)))).isEmpty())
        assertTrue(payerQuestions(txns, listOf(src("a")), skipParties = setOf(starKey)).isEmpty())
        // عميل أو إيجار مش «مرتب»
        assertTrue(payerQuestions(txns, listOf(src("c", kind = IncomeSourceKind.CLIENT))).isEmpty())
    }

    @Test fun noOnOneSourceMovesTheQuestionToAnotherRunningSourceOrStopsIt() {
        val txns = listOf(deposit(star, "2026-03-27"))
        val a = src("a", declined = listOf(starKey))
        assertTrue(payerQuestions(txns, listOf(a)).isEmpty(), "«لأ» ⇒ ما يتسألش تاني")
        assertEquals("b", payerQuestions(txns, listOf(a, src("b", from = "2026-02-01", kind = IncomeSourceKind.PART_TIME))).single().sourceId)
        // مصدرين شغالين: اللي لسه ما اتعرفش مين بيحوّله الأول
        val known = src("k", from = "2026-02-15", payers = listOf("حد تاني"))
        assertEquals("a", payerQuestions(txns, listOf(known, src("a"))).single().sourceId)
    }

    @Test fun answeringRecordsOnlyWhoPaysAndNeverTouchesTheAmount() {
        val s = src("a", amount = 900_000)
        val yes = answerPayerQuestion(s, starKey, yes = true)
        assertEquals(listOf(starKey), yes.payerKeys)
        assertEquals(900_000, yes.expectedMinor, "المبلغ المتوقع زي ما هو — ما بنستنتجش زيادة")
        assertEquals(listOf(starKey), answerPayerQuestion(s, starKey, yes = false).declinedPayerKeys)
        assertEquals(s.copy(payerKeys = listOf(starKey)), answerPayerQuestion(yes, starKey, yes = true), "نفس الرد تاني ما بيكررش")
    }

    @Test fun laterDepositsFromTheKnownPayerAreAttributedWithoutAQuestion() {
        val sources = listOf(src("a", to = "2026-06-30", payers = listOf(starKey)))
        assertEquals("a", attributedSourceId(deposit(star, "2026-03-27", 1_350_000), sources), "أي مبلغ")
        assertNull(attributedSourceId(deposit(moon, "2026-03-27"), sources))
        assertNull(attributedSourceId(deposit(star, "2026-07-27"), sources), "بعد ما المصدر اتقفل")
        assertNull(attributedSourceId(deposit(star, "2026-03-27", kind = EconomicKind.LOAN_RECEIVED, confirmed = true), sources), "قال إنها سلفة")
        assertNull(attributedSourceId(deposit(star, "2026-03-27", kind = EconomicKind.EVENT_GIFT, confirmed = true), sources))
        assertNull(attributedSourceId(deposit(star, "2026-03-27", kind = EconomicKind.END_OF_SERVICE, confirmed = true), sources), "المكافأة مش مرتب الشهر")
        assertNull(attributedSourceId(deposit(star, "2026-03-27", currency = Currency.EGP), sources))
    }

    @Test fun everyDepositFromAConfirmedPayerBecomesSalaryUnlessChangedByHand() {
        // رد المالك §64 (اختياره): بعد ما يأكد مرة ⇒ أي تحويل من الشركة «مرتب» — حتى بعد ما المصدر يتقفل (المكافأة)
        val sources = listOf(src("a", to = "2026-06-30", payers = listOf(starKey)))
        val auto = applyKnownPayerSalary(deposit(star, "2026-07-27", 2_000_000), sources, "now")
        assertEquals(Triple(EconomicKind.SALARY, true, ReviewState.CONFIRMED), Triple(auto.economicKind, auto.economicKindConfirmed, auto.reviewState))
        assertEquals("now", auto.updatedAt)
        val byHand = deposit(star, "2026-03-27", kind = EconomicKind.REFUND_RECEIVED, confirmed = true)
        assertEquals(byHand, applyKnownPayerSalary(byHand, sources, "now"), "اللي اتغيّر بإيده ما بيتلمسش")
        val benefit = deposit(star, "2026-07-27", 9_000_000, kind = EconomicKind.END_OF_SERVICE, confirmed = true)
        assertEquals(benefit, applyKnownPayerSalary(benefit, sources, "now"), "مكافأة نهاية الخدمة المتعلّمة بإيده ما بتبقاش «مرتب» تاني (§64-٧)")
        val unknown = deposit(moon, "2026-03-27")
        assertEquals(unknown, applyKnownPayerSalary(unknown, sources, "now"))
        val out = deposit(star, "2026-03-27", dir = Direction.OUT)
        assertEquals(out, applyKnownPayerSalary(out, sources, "now"), "الطالع مش مرتب")
        val declinedOnly = listOf(src("a", declined = listOf(starKey)))
        assertEquals(unknown.copy(id = "x"), applyKnownPayerSalary(unknown.copy(id = "x"), declinedOnly, "now"))
        val fromStar = deposit(star, "2026-03-27")
        assertEquals(fromStar, applyKnownPayerSalary(fromStar, declinedOnly, "now"), "«لأ» مش تأكيد")
    }

    private val paid = src("a", from = "2026-01-01", payers = listOf(starKey), day = 25, amount = 900_000)

    @Test fun lateSalaryWaitsThreeDaysAfterTheUsualDayThenGoesAwayWhenAnyAmountArrives() {
        val september = deposit(star, "2026-09-25")
        assertTrue(lateIncomeCandidates(listOf(paid), listOf(september), "2026-10-27").isEmpty(), "يومين بعد الميعاد ⇒ لسه في المهلة")
        val late = lateIncomeCandidates(listOf(paid), listOf(september), "2026-10-28").single()
        assertEquals(AlertKind.INCOME_LATE, late.kind)
        assertEquals("income_late|a|2026-10-25", late.threadKey)
        assertEquals("لسه ما وصلش من «مصدر a»", late.title)
        // وصل أقل من المعتاد — برضه وصل (المبلغ مش علامة على حاجة)
        val small = deposit(star, "2026-10-29", 10_000)
        assertTrue(lateIncomeCandidates(listOf(paid), listOf(september, small), "2026-10-29").isEmpty())
        // نزل بدري قبل إجازة ⇒ اتحسب لنفس الشهر
        assertTrue(lateIncomeCandidates(listOf(paid), listOf(deposit(star, "2026-10-15")), "2026-10-28").isEmpty())
    }

    @Test fun noLateAlertWithoutAKnownPayerAnUsualDayOrARunningSource() {
        assertTrue(lateIncomeCandidates(listOf(paid.copy(payerKeys = emptyList())), emptyList(), "2026-10-28").isEmpty(), "مش عارفين مين بيحوّل ⇒ ما نقولش «ما وصلش»")
        assertTrue(lateIncomeCandidates(listOf(paid.copy(expectedDayOfMonth = null)), emptyList(), "2026-10-28").isEmpty())
        assertTrue(lateIncomeCandidates(listOf(paid.copy(endedAt = "2026-10-01")), emptyList(), "2026-10-28").isEmpty())
        assertTrue(lateIncomeCandidates(listOf(paid.copy(startedAt = "2026-10-26")), emptyList(), "2026-10-28").isEmpty(), "بدأ بعد الميعاد")
        // الإيداع من طرف تاني ما بيحلّش
        assertEquals(1, lateIncomeCandidates(listOf(paid), listOf(deposit(moon, "2026-10-25")), "2026-10-28").size)
    }

    @Test fun usualDayIsClampedToShortMonths() {
        assertEquals("2026-02-28", lastDueWithGracePassed(31, "2026-03-03"))
        assertEquals("2026-01-31", lastDueWithGracePassed(31, "2026-03-02"))
        assertEquals("2026-10-25", lastDueWithGracePassed(25, "2026-10-28"))
        assertEquals("2026-09-25", lastDueWithGracePassed(25, "2026-10-27"))
    }

    @Test fun lockScreenSaysNothingAboutTheSourceOrTheAmount() {
        try {
            for (lang in Language.entries) {
                Texts.language = lang
                val c = lateIncomeCandidates(listOf(paid), emptyList(), "2026-10-28").single()
                assertTrue(c.title.contains("مصدر a"), "التفاصيل جوه التطبيق فيها الاسم")
                val notice = systemNoticeFor(c.kind)
                for (text in listOf(notice.title, notice.body)) {
                    assertTrue(isLockSafe(text), "[$lang] $text")
                    assertFalse(text.contains("مصدر a") || text.contains("2026"), "[$lang] $text")
                }
                assertEquals(AlertGroup.INCOME, c.kind.group)
                assertTrue(alertGroupLabel(AlertGroup.INCOME).isNotBlank())
            }
        } finally {
            Texts.language = Language.AR
        }
    }
}
