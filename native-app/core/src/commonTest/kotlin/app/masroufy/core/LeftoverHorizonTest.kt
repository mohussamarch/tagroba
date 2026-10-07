package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * لحد إمتى «فاضلك تقريبًا» بيتحسب (رد المالك §65): أقرب قبض جاي من الوظيفة (يوم المرتب في الملف) أو البارت تايم (شهري أو أسبوعي)
 * أو المعاش أو الإيجار الشهري. بيانات مخترعة — النهارده الحد 2026-10-04.
 */
class LeftoverHorizonTest {
    private val today = "2026-10-04"

    private fun src(id: String, kind: IncomeSourceKind, day: Int? = null, weekday: Int? = null, ended: String? = null, currency: Currency = Currency.SAR) =
        IncomeSource(
            id, "مصدر وهمي", "n-$id", kind, currency, "2025-01-01", ended, expectedDayOfMonth = day,
            payFrequency = if (weekday != null) PayFrequency.WEEKLY else PayFrequency.MONTHLY, payWeekday = weekday,
        )

    @Test fun isoWeekdayFromTheCalendar() {
        assertEquals(4, isoWeekday("1970-01-01"), "خميس")
        assertEquals(7, isoWeekday(today), "حد")
        assertEquals(1, isoWeekday("2026-10-05"), "اتنين")
    }

    @Test fun jobUsesTheAccountPaydayAndOthersTheirOwnDay() {
        assertEquals("2026-10-28", nextPayDate(src("j", IncomeSourceKind.JOB, day = 10), today, 28), "الوظيفة بيوم الملف مش يومها")
        assertNull(nextPayDate(src("j", IncomeSourceKind.JOB), today, null), "مفيش يوم مرتب في الملف ⇒ مش معروف")
        assertEquals("2026-10-10", nextPayDate(src("p", IncomeSourceKind.PENSION, day = 10), today, 28))
        assertEquals("2026-10-31", nextPayDate(src("r", IncomeSourceKind.RENT, day = 31), today, 28))
        assertNull(nextPayDate(src("p", IncomeSourceKind.PART_TIME), today, 28), "البارت تايم لسه ما قالش بيقبض إمتى")
    }

    @Test fun weeklyPayIsTheNextMatchingWeekdayStrictlyAfterToday() {
        assertEquals("2026-10-08", nextPayDate(src("w", IncomeSourceKind.PART_TIME, weekday = 4), today, 28))
        assertEquals("2026-10-11", nextPayDate(src("w", IncomeSourceKind.PART_TIME, weekday = 7), today, 28), "النهارده يوم القبض ⇒ الأسبوع الجاي")
        assertEquals("2026-10-05", nextPayDate(src("w", IncomeSourceKind.PART_TIME, weekday = 1), today, 28))
    }

    @Test fun aSourceEndingBeforeItsNextPayHasNoNextPay() {
        assertNull(nextPayDate(src("p", IncomeSourceKind.PENSION, day = 20, ended = "2026-10-15"), today, 28))
    }

    @Test fun horizonIsTheNearestPayAndMonthEndOnlyWhenItIsTheAccountPayday() {
        assertEquals(LeftoverHorizon("2026-10-28", true), leftoverHorizon(listOf(src("j", IncomeSourceKind.JOB)), today, 28, Currency.SAR))
        val withWeekly = listOf(src("j", IncomeSourceKind.JOB), src("w", IncomeSourceKind.PART_TIME, weekday = 4))
        assertEquals(LeftoverHorizon("2026-10-08", false), leftoverHorizon(withWeekly, today, 28, Currency.SAR))
        // معاش يومه نفس يوم المرتب ⇒ «آخر الشهر»
        assertEquals(LeftoverHorizon("2026-10-28", true), leftoverHorizon(listOf(src("p", IncomeSourceKind.PENSION, day = 28)), today, 28, Currency.SAR))
        assertEquals(LeftoverHorizon("2026-10-10", false), leftoverHorizon(listOf(src("p", IncomeSourceKind.PENSION, day = 10)), today, 28, Currency.SAR))
    }

    @Test fun aWeeklyJobUsesItsOwnWeekdayAndIsNeverTheMonthEnd() {
        // رد المالك §64-٤: وظيفة أسبوعي ⇒ «لحد القبض الجاي» بيومها، مش يوم مرتب الحساب
        val weeklyJob = src("jw", IncomeSourceKind.JOB, weekday = 4)
        assertEquals("2026-10-08", nextPayDate(weeklyJob, today, 28), "الخميس الجاي — مش يوم 28")
        assertEquals(LeftoverHorizon("2026-10-08", false), leftoverHorizon(listOf(weeklyJob), today, 28, Currency.SAR))
        assertNull(nextPayDate(src("jw", IncomeSourceKind.JOB).copy(payFrequency = PayFrequency.WEEKLY), today, 28), "يومها في الأسبوع لسه ما اتقالش")
        // يوم مرتب الحساب 8 = الخميس الجاي صدفة: القبض الأسبوعي لوحده مش «آخر الشهر»، ومعاه وظيفة شهري ⇒ «آخر الشهر»
        assertEquals(LeftoverHorizon("2026-10-08", false), leftoverHorizon(listOf(weeklyJob), today, 8, Currency.SAR))
        assertEquals(LeftoverHorizon("2026-10-08", true), leftoverHorizon(listOf(weeklyJob, src("j", IncomeSourceKind.JOB)), today, 8, Currency.SAR))
    }

    @Test fun noHorizonWithoutASalariedSourceOrAKnownDayOrInAnotherCurrency() {
        assertNull(leftoverHorizon(listOf(src("c", IncomeSourceKind.CLIENT, day = 5)), today, 28, Currency.SAR))
        assertNull(leftoverHorizon(listOf(src("r", IncomeSourceKind.RENT, weekday = 2)), today, 28, Currency.SAR), "الإيجار الأسبوعي مش «بمرتب»")
        assertNull(leftoverHorizon(listOf(src("j", IncomeSourceKind.JOB, currency = Currency.EGP)), today, 28, Currency.SAR))
        assertNull(leftoverHorizon(listOf(src("pt", IncomeSourceKind.PART_TIME)), today, 28, Currency.SAR))
        assertTrue(isSalaried(listOf(src("pt", IncomeSourceKind.PART_TIME)), today), "بمرتب بس ميعاده مش معروف ⇒ «من اللي معاك»")
    }
}
