package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «جهات الشغل» (قايمة المالك 2026-10-15، البندين 6 و7) — كل الأسامي والأرقام مخترعة: جهة «الفجر» لحد آخر أغسطس بحسابين بيحوّلوا بالتبادل،
 * وبعدها «النور» من أول سبتمبر. ممنوع أي اسم جهة حقيقي (المستودع عام).
 */
class EmployersTest {
    private var seq = 0

    private fun deposit(sender: String, last4: String, date: String, amount: Long = 1_200_000, kind: EconomicKind = EconomicKind.UNCLASSIFIED, confirmed: Boolean = false, dir: Direction = Direction.IN) =
        Transaction(
            id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = confirmed,
            observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
            rawDescription = "W-/FRACCT/0000000000000$last4" + "FR$sender:ملاحظة", sourceOperationType = "حوالة واردة داخل البنك",
        )

    private fun keyOf(sender: String, last4: String) = transferPartyOf(deposit(sender, last4, "2026-01-01"))!!.key

    private val fajr1 = keyOf("FAJRCO", "1111")
    private val fajr2 = keyOf("FAJRCO", "2222")
    private val noor = keyOf("NOORCO", "3333")

    private fun job(id: String, name: String, from: String, to: String?, payers: List<String>) =
        IncomeSource(id, name, normalizeText(name), IncomeSourceKind.JOB, Currency.SAR, from, to, payerKeys = payers)

    private val fajr = job("e-fajr", "جهة الفجر", "2025-01-01", "2026-08-31", listOf(fajr1, fajr2))
    private val noorJob = job("e-noor", "جهة النور", "2026-09-01", null, listOf(noor))
    private val both = listOf(fajr, noorJob)

    @Test fun theTwoAccountsOfOneEmployerAreDifferentKeysButTheSameEmployer() {
        assertTrue(fajr1 != fajr2, "حسابين مختلفين بآخر 4")
        assertEquals("e-fajr", employerOf(deposit("FAJRCO", "1111", "2026-06-27"), both)?.id)
        assertEquals("e-fajr", employerOf(deposit("FAJRCO", "2222", "2026-07-27"), both)?.id, "الحساب التاني لنفس الجهة")
        assertEquals("e-fajr", employerOf(deposit("FAJRCO", "1111", "2026-08-31"), both)?.id, "آخر يوم في الفترة جوّاها")
    }

    @Test fun theSwitchAtTheEndOfAugustMovesTheSalaryToTheNewEmployer() {
        assertEquals("e-noor", employerOf(deposit("NOORCO", "3333", "2026-09-27"), both)?.id)
        assertNull(employerOf(deposit("FAJRCO", "1111", "2026-09-27"), both), "حساب الجهة القديمة بعد الخروج ⇒ مش راتب لوحده")
        assertNull(employerOf(deposit("NOORCO", "3333", "2026-08-27"), both), "قبل بداية الجهة الجديدة")
        assertNull(employerOf(deposit("OTHERCO", "4444", "2026-09-27"), both), "حساب مش معروف")
        assertNull(employerOf(deposit("NOORCO", "3333", "2026-09-27", dir = Direction.OUT), both), "الطالع مش راتب")
    }

    @Test fun knownPayerSalaryOnlyInsideThePeriod() {
        // PLAN-0.6 §7: «راتب» لوحده محتاج كلمة راتب أو مبلغ وميعاد قريبين
        val inside = applyKnownPayerSalary(deposit("FAJRCO", "2222", "2026-05-27").copy(sourceOperationType = "حوالة رواتب"), both, "now")
        assertEquals(EconomicKind.SALARY to true, inside.economicKind to inside.economicKindConfirmed)
        val outside = deposit("FAJRCO", "2222", "2026-10-03")
        assertEquals(outside, applyKnownPayerSalary(outside, both, "now"), "برّه الفترة ما بيتلمسش")
    }

    @Test fun retroPlanSplitsUnconfirmedFromHandSetAndNeverTouchesSpecialKinds() {
        val a = deposit("FAJRCO", "1111", "2026-06-27")
        val b = deposit("FAJRCO", "2222", "2026-07-27", kind = EconomicKind.GIFT_RECEIVED, confirmed = true)
        val c = deposit("FAJRCO", "1111", "2026-08-27", kind = EconomicKind.SALARY, confirmed = true)
        val d = deposit("FAJRCO", "2222", "2026-09-27")
        val e = deposit("NOORCO", "3333", "2026-09-28")
        val f = deposit("FAJRCO", "1111", "2026-08-30", kind = EconomicKind.END_OF_SERVICE, confirmed = true)
        val waiting = deposit("FAJRCO", "1111", "2026-08-15").copy(suggestedKind = EconomicKind.REFUND_RECEIVED)
        val txns = listOf(e, d, c, b, a, f, waiting)
        val plan = salaryRetroPlan(txns, both, employerId = "e-fajr")
        assertEquals(listOf(a.id), plan.toSalary.map { it.id }, "برّه الفترة (d) والجهة التانية (e) والراتب المؤكد (c) برّه")
        assertEquals(listOf(b.id), plan.handSet.map { it.id }, "اللي اتحدد بإيده لوحده — والمكافأة عمرها ما بتتغير")
        val all = salaryRetroPlan(txns, both, skip = setOf(a.id))
        assertEquals(listOf(e.id), all.toSalary.map { it.id }, "كل الجهات من غير اللي اتجاوب")
        assertTrue(salaryRetroPlan(txns, emptyList()).isEmpty)
    }

    @Test fun anAccountCannotBelongToTwoEmployersAtTheSameTime() {
        val overlapping = job("e-x", "جهة تانية", "2026-08-01", null, emptyList())
        assertEquals("e-fajr", accountClash(overlapping, fajr1, both + overlapping)?.id)
        val later = job("e-y", "جهة بعدين", "2026-09-01", null, emptyList())
        assertNull(accountClash(later, fajr1, both + later), "فترتين منفصلتين ⇒ مسموح")
    }

    @Test fun accountsShowTheNameAsReceivedAndOnlyTheLastFourDigits() {
        val senders = incomingSenders(listOf(deposit("FAJRCO", "1111", "2026-06-27"), deposit("FAJRCO", "1111", "2026-07-27"), deposit("NOORCO", "3333", "2026-09-27")))
        assertEquals(listOf(noor, fajr1), senders.map { it.party.key }, "الأحدث الأول")
        assertEquals(2, senders[1].count)
        val accounts = payerAccountsOf(fajr, senders.associate { it.party.key to it.party })
        assertEquals(listOf("FAJRCO" to "1111", "••••2222" to "2222"), accounts.map { it.label to it.last4 }, "من غير عملية ⇒ آخر 4 بس")
        assertTrue(accounts.none { it.label.contains("000000") }, "عمرنا ما بنعرض رقم الحساب كامل")
    }

    @Test fun sideJobAndSaleAreIncomeAndTheirCategoriesExist() {
        val side = deposit("PERSONX", "5555", "2026-09-10", 300_000, kind = EconomicKind.FREELANCE, confirmed = true)
        val sale = deposit("PERSONY", "6666", "2026-09-12", 150_000, kind = EconomicKind.PERSONAL_SALE, confirmed = true)
        assertEquals(450_000L, computePeriodTotals(listOf(side, sale), emptyList()).incomeMinor)
        val cats = IncomeCategories.defaults()
        assertEquals(listOf(null, IncomeCategories.ROOT, IncomeCategories.ROOT, IncomeCategories.ROOT), cats.map { it.parentId })
        assertEquals(IncomeCategories.SIDE_JOB, IncomeCategories.forKind(EconomicKind.FREELANCE))
        assertEquals(IncomeCategories.SALE, IncomeCategories.forKind(EconomicKind.PERSONAL_SALE))
        assertTrue(cats.all { it.name.isNotBlank() && it.iconKey.isNotBlank() && it.active })
    }

    @Test fun salaryTaggedFromAnEmployerKeepsThePayday28MonthLogic() {
        val salary = asConfirmedSalary(deposit("NOORCO", "3333", "2026-09-27"), "now")
        assertEquals("2026-09-28", countingDate(salary, payday = 28, policy = EstimatePolicy.OWNER_2026_10), "راتب يوم 27 يتحسب للشهر اللي بيبدأ 28")
    }
}
