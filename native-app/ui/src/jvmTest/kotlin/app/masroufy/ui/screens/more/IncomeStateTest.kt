package app.masroufy.ui.screens.more

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.IncomeFollowUp
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.Language
import app.masroufy.core.PayFrequency
import app.masroufy.core.SourceStartComparison
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.periodForDate
import app.masroufy.core.sentenceDigits
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** مصادر الدخل: الصياغة بس — المتوقع الفاضي «لم تكتبه» (null) مش صفر، والنسبة المجهولة «—». */
class IncomeStateTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun source(id: String, kind: IncomeSourceKind = IncomeSourceKind.JOB, start: String = "2026-04-28", end: String? = null) =
        IncomeSource(id, "شركة $id", "شركة $id", kind, Currency.SAR, start, end)

    @Test
    fun payTextCoversMonthlyWeeklyAndUnknown() {
        assertEquals(t(TextKey.INCSRC_PAY_MONTHLY, sentenceNumber(28)), payText(source("a").copy(expectedDayOfMonth = 28)))
        assertEquals(t(TextKey.INCSRC_PAY_WEEKLY, t(TextKey.WEEKDAY_THU)), payText(source("a").copy(payFrequency = PayFrequency.WEEKLY, payWeekday = 4)))
        assertEquals(t(TextKey.INCSRC_PAY_WEEKLY_UNSET), payText(source("a").copy(payFrequency = PayFrequency.WEEKLY)))
        assertEquals(t(TextKey.INCSRC_PAY_UNSET), payText(source("a")), "وظيفة من غير يوم ⇒ «غير محدد» (مش يوم صفر)")
        assertEquals(t(TextKey.INCSRC_PAY_UNSET), payText(source("p", IncomeSourceKind.PENSION)))
        assertEquals(t(TextKey.INCSRC_PAY_NONE), payText(source("c", IncomeSourceKind.CLIENT)))
    }

    @Test
    fun weekdaysAreIsoMondayFirst() {
        assertEquals(TextKey.WEEKDAY_MON, weekdayLabel(1))
        assertEquals(TextKey.WEEKDAY_SUN, weekdayLabel(7))
        assertEquals(TextKey.WEEKDAY_MON, weekdayLabel(0))
    }

    @Test
    fun periodTextSinceOrRange() {
        assertEquals(t(TextKey.INCSRC_PERIOD_SINCE, fullDate("2026-04-28")!!), periodText(source("a")))
        assertEquals(
            t(TextKey.INCSRC_PERIOD_RANGE, fullDate("2025-03-01")!!, fullDate("2026-04-27")!!),
            periodText(source("b", start = "2025-03-01", end = "2026-04-27")),
        )
    }

    @Test
    fun sectionsSplitCurrentAndPastAndKeepTheExpectedAmountUnknown() {
        val s = incomeSections(listOf(source("a").copy(expectedMinor = 1_250_000), source("b", end = "2026-04-27"), source("c", IncomeSourceKind.RENT)))
        assertEquals(listOf("a", "c"), s.current.map { it.id })
        assertEquals(listOf("b"), s.past.map { it.id })
        assertTrue(s.past.single().ended)
        assertEquals(amountLabel(1_250_000, Currency.SAR), s.current[0].expected)
        assertNull(s.current[1].expected, "ما كتبش المتوقع ⇒ null مش «٠»")
        assertEquals(t(TextKey.INCSRC_META, t(TextKey.INCSRC_KIND_RENT), t(TextKey.INCSRC_PAY_NONE)), s.current[1].meta)
        assertFalse(s.current[1].meta.contains("·"), "مفيش «·» جنب الأرقام العربي")
    }

    @Test
    fun tenthPercentIsFormattedNotComputed() {
        assertEquals("+" + sentenceDigits("26") + t(TextKey.INCSRC_DECIMAL_SEP) + sentenceDigits("3") + "٪", tenthPercentText(263))
        assertEquals("−" + sentenceDigits("0") + t(TextKey.INCSRC_DECIMAL_SEP) + sentenceDigits("5") + "٪", tenthPercentText(-5))
        assertEquals(sentenceDigits("0") + t(TextKey.INCSRC_DECIMAL_SEP) + sentenceDigits("0") + "٪", tenthPercentText(0))
        assertEquals("—", tenthPercentText(null))
    }

    @Test
    fun trendIsGoodWhenIncomeRisesOrSpendingFalls() {
        assertEquals(Trend.GOOD, trend(10, isIncome = true))
        assertEquals(Trend.BAD, trend(-10, isIncome = true))
        assertEquals(Trend.GOOD, trend(-10, isIncome = false))
        assertEquals(Trend.BAD, trend(10, isIncome = false))
        assertEquals(Trend.FLAT, trend(0, true))
        assertEquals(Trend.FLAT, trend(null, false))
    }

    @Test
    fun compareRowsCarryTheUseCaseNumbersAsIs() {
        val c = SourceStartComparison("a", Currency.SAR, emptyList(), emptyList(), 900_000, 1_100_000, 700_000, 650_000, 222, null, true)
        val (income, expense) = compareRows(c)
        assertEquals(900_000L, income.before)
        assertEquals(1_100_000L, income.after)
        assertEquals(tenthPercentText(222), income.change)
        assertEquals(Trend.GOOD, income.trend)
        assertEquals(650_000L, expense.after)
        assertEquals("—", expense.change)
        assertEquals(Trend.FLAT, expense.trend)
    }

    @Test
    fun followUpQueuePutsNewQuestionsFirst() {
        val queue = listOf(IncomeFollowUp.AskPayday("a"), IncomeFollowUp.AskExpectedSalary("a"))
        val next = nextQueue(queue, listOf(IncomeFollowUp.ChangeMonthStart("a", 1)))
        assertEquals(listOf(IncomeFollowUp.ChangeMonthStart("a", 1), IncomeFollowUp.AskExpectedSalary("a")), next)
        assertEquals(listOf(IncomeFollowUp.AskExpectedSalary("a")), nextQueue(queue, emptyList()))
        assertTrue(nextQueue(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun dateChoicesAreDistinctAndRealDays() {
        assertEquals("2026-10-10", plusDays("2026-10-09", 1))
        assertEquals("2026-09-30", plusDays("2026-10-01", -1))
        assertEquals("2027-01-01", firstOfMonth("2026-12-15", 1))
        assertEquals("2026-12-01", firstOfMonth("2026-12-15"))

        val end = endDateChoices("2026-10-09", 28)
        assertEquals(listOf("2026-10-09", "2026-09-30", plusDays(periodForDate("2026-10-09", 28).start, -1)), end)
        // أول الشهر المالي = أول الشهر العادي ⇒ مفيش تكرار
        assertEquals(2, endDateChoices("2026-10-09", 1).size)

        assertEquals(listOf("2026-10-10", "2026-11-01", "2026-10-09"), startDateChoices("2026-10-09", 28, change = true))
        assertEquals(listOf("2026-10-09", periodForDate("2026-10-09", 28).start, "2026-01-01"), startDateChoices("2026-10-09", 28, change = false))
        assertEquals(2, startDateChoices("2026-01-05", 1, change = false).size)
    }

    @Test
    fun dateChipSaysTodayInEachVariant() {
        assertEquals("اليوم", dateChipLabel("2026-10-09", "2026-10-09"))
        assertEquals(fullDate("2026-10-08"), dateChipLabel("2026-10-08", "2026-10-09"))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("النهارده", dateChipLabel("2026-10-09", "2026-10-09"))
        assertEquals(t(TextKey.INCSRC_PAY_MONTHLY, sentenceNumber(28)), payText(source("a").copy(expectedDayOfMonth = 28)))
        assertTrue(payText(source("a").copy(expectedDayOfMonth = 28)).endsWith("كل شهر"))
        Texts.arabicVariant = ArabicVariant.MSA
        assertTrue(payText(source("a").copy(expectedDayOfMonth = 28)).endsWith("شهريًا"))
    }
}
