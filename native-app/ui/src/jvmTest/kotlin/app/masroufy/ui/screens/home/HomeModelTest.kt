package app.masroufy.ui.screens.home

import app.masroufy.core.AlertKind
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Texts
import app.masroufy.ui.screens.home.HomeTestData.inbox
import app.masroufy.ui.screens.home.HomeTestData.now
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الرئيسية: من نتايج حالات الاستخدام لحالة الشاشة — «صرفت هذا الشهر» (و«غير متاح» مش صفر) · «الراتب بعد N» بقواعد العدّ · كارت المساعد
 * من آخر تنبيه للمساعد · الحالات (حساب جديد · شهر من غير عمليات) · «الكاش وحده».
 */
class HomeModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun month(spent: Long?, count: Int = 3, next: String? = "2026-10-28") = HomeMonth(spent, partial = false, transactionCount = count, nextPayday = next)

    @Test fun spentLineShowsTheAmountOrNotAvailableNeverZero() {
        assertEquals("صرفت هذا الشهر 6,650.00", spentLine(month(665_000), Currency.SAR))
        assertEquals("صرفت هذا الشهر غير متاح", spentLine(month(null), Currency.SAR), "مجهول ⇒ «غير متاح»")
        assertEquals("صرفت هذا الشهر غير متاح", spentLine(null, Currency.SAR), "الشهر فشل يتقري ⇒ «غير متاح» مش صفر")
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("صرفت الشهر ده 6,650.00", spentLine(month(665_000), Currency.SAR))
    }

    @Test fun salaryLineCountsDaysWithArabicRules() {
        assertEquals("الراتب اليوم", salaryLine("2026-10-07", "2026-10-07"))
        assertEquals("الراتب غدًا", salaryLine("2026-10-08", "2026-10-07"))
        assertEquals("الراتب بعد يومين", salaryLine("2026-10-09", "2026-10-07"))
        assertEquals("الراتب بعد ٥ أيام", salaryLine("2026-10-12", "2026-10-07"))
        assertEquals("الراتب بعد ٢١ يومًا", salaryLine("2026-10-28", "2026-10-07"))
        assertNull(salaryLine(null, "2026-10-07"), "مفيش يوم راتب ⇒ مفيش سطر")
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("المرتب بعد ٢١ يوم", salaryLine("2026-10-28", "2026-10-07"))
    }

    @Test fun advisorCardIsTheNewestAdvisorAlertThatIsNotMutedOrDeleted() {
        val list = listOf(
            inbox("budget", AlertKind.BUDGET_EXCEEDED),
            inbox("habit-muted", AlertKind.HABIT_VS_GOAL, muted = true),
            inbox("habit", AlertKind.HABIT_VS_GOAL),
            inbox("big", AlertKind.BIG_ONE),
        )
        assertEquals("habit", advisorCardOf(list)?.threadKey)
        assertEquals("عنوان habit", advisorCardOf(list)?.title)
        assertEquals("big", advisorCardOf(list, gone = setOf("habit"))?.threadKey, "اللي اتمسح بـ«×» ما يبانش")
        assertNull(advisorCardOf(list.take(2)), "مفيش تنبيه مساعد ⇒ مفيش كارت (مش كلام مخترع)")
    }

    @Test fun statesForANewAccountAndAQuietMonth() {
        assertTrue(isBrandNew(now(total = null, cashMinor = null, wallets = emptyList()), month(null, count = 0)))
        assertFalse(isBrandNew(now(), month(null, count = 0)), "فيه محافظ ⇒ البطاقة البطلة بتظهر")
        assertTrue(isQuietMonth(now(), month(0, count = 0)))
        assertFalse(isQuietMonth(now(), month(500, count = 2)))
        assertFalse(isQuietMonth(now(), null), "الشهر ما اتقراش ⇒ مش بنقول «مفيش عمليات»")
    }

    @Test fun cashOnlyLineNeedsAKnownCashBalance() {
        assertEquals("الكاش وحده 200.00 ر.س.", cashOnlyLine(now(total = null, cashMinor = 20_000)))
        assertNull(cashOnlyLine(now(total = null, cashMinor = null)))
    }
}
