package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * أيام قبض المصادر في التقويم (رد المالك §64-٣): بارت تايم · معاش · إيجار · وظيفة أسبوعي — كل مصدر بيومه ومن غير مبلغ،
 * والوظيفة الشهري ما بتتكررش على سطر مرتب الحساب. أكتوبر 2026: الخميس 1 · 8 · 15 · 22 · 29 — الاتنين 5 · 12 · 19 · 26.
 * كل الأسامي مخترعة.
 */
class IncomePayDaysTest {
    private val today = "2026-10-04"

    private fun src(
        id: String,
        kind: IncomeSourceKind,
        day: Int? = null,
        weekly: Int? = null,
        start: String = "2025-01-01",
        end: String? = null,
        currency: Currency = Currency.SAR,
        expected: Long? = null,
    ) = IncomeSource(
        id, "مصدر وهمي $id", "n-$id", kind, currency, start, end, expectedDayOfMonth = day, expectedMinor = expected,
        payFrequency = if (weekly != null) PayFrequency.WEEKLY else PayFrequency.MONTHLY, payWeekday = weekly,
    )

    private val sources = listOf(
        src("job", IncomeSourceKind.JOB, day = 28),
        src("job-w", IncomeSourceKind.JOB, weekly = 4, start = "2026-10-10"),
        src("pt", IncomeSourceKind.PART_TIME, day = 31, expected = 150_000),
        src("pt-eg", IncomeSourceKind.PART_TIME, day = 2, currency = Currency.EGP),
        src("pension", IncomeSourceKind.PENSION, day = 5, end = "2026-10-04"),
        src("rent", IncomeSourceKind.RENT, weekly = 1),
        src("client", IncomeSourceKind.CLIENT, day = 3),
        src("pt-unknown", IncomeSourceKind.PART_TIME),
    )

    @Test
    fun everyIncomeSourceOnItsOwnDayWithoutAmountAndNoSecondLineForTheMonthlyJob() {
        val items = buildCalendar("2026-10-01", "2026-10-31", today, Currency.SAR, CalendarSources(payday = 28, incomeSources = sources))
        val pay = items.filter { it.type == CalendarItemType.INCOME_PAY }
        assertEquals(
            listOf(
                "2026-10-02 pt-eg", "2026-10-05 rent", "2026-10-12 rent", "2026-10-15 job-w", "2026-10-19 rent", "2026-10-22 job-w",
                "2026-10-26 rent", "2026-10-29 job-w", "2026-10-31 pt",
            ),
            pay.map { "${it.date} ${it.sourceId}" },
        )
        assertTrue(pay.all { it.amountMinor == null && it.flow == DueFlow.RECEIVE }, "من غير مبلغ — زي سطر المرتب، حتى لو المرتب المتوقع مكتوب")
        assertEquals(Currency.EGP, pay.first().currency, "بعملة المصدر")
        assertEquals(uiText(TextKey.CAL_TITLE_SOURCE_PAY, "مصدر وهمي rent"), pay[1].title)
        assertEquals(listOf("2026-10-28"), items.filter { it.type == CalendarItemType.PAYDAY }.map { it.date }, "الوظيفة الشهري = سطر مرتب الحساب بس")
        assertTrue(items.none { it.sourceId == "job" || it.sourceId == "client" || it.sourceId == "pt-unknown" })
    }

    @Test
    fun payDatesStayInsideTheSourcePeriodAndClampToMonthEnd() {
        val pt = sources.single { it.id == "pt" }
        assertEquals(listOf("2026-11-30"), sourcePayDatesIn(pt, "2026-11-01", "2026-11-30"), "يوم 31 في نوفمبر ⇒ آخر الشهر")
        val pension = sources.single { it.id == "pension" }
        assertEquals(listOf("2026-09-05"), sourcePayDatesIn(pension, "2026-09-01", "2026-09-30"), "قبل القفل بيظهر")
        assertTrue(sourcePayDatesIn(pension, "2026-10-01", "2026-12-31").isEmpty(), "بعد القفل ما بيظهرش")
        val weekly = sources.single { it.id == "job-w" }
        assertTrue(sourcePayDatesIn(weekly, "2026-10-01", "2026-10-09").isEmpty(), "قبل البداية ما بيظهرش")
        assertEquals(listOf("2026-10-15"), sourcePayDatesIn(weekly, "2026-10-15", "2026-10-15"), "اليوم نفسه شامل")
    }

    @Test
    fun incomePayDayCannotBeCountedFromYourMoney() {
        val line = buildCalendar("2026-10-05", "2026-10-05", today, Currency.SAR, CalendarSources(incomeSources = sources)).single()
        assertEquals(CalendarItemType.INCOME_PAY, line.type)
        assertFailsWith<ReservationError>("قبض جاي ليك — مش بيتحسب من اللي معاك") { countedAmount(line, 1_000, today) }
    }
}
