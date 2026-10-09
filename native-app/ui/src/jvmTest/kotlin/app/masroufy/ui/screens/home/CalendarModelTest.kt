package app.masroufy.ui.screens.home

import app.masroufy.core.ArabicVariant
import app.masroufy.core.CalendarItemType
import app.masroufy.core.DueFlow
import app.masroufy.core.Texts
import app.masroufy.ui.screens.home.HomeTestData.item
import app.masroufy.ui.shell.MarkKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * التقويم: السطر الصغير جنب كل ميعاد (فات · ستستلمه · محجوز ✓ · غير محجوز · بلا مبلغ — من `reservationState`) · «بعد كام يوم» ·
 * الجاي (من النهارده + اللي فات وعليك) · أقرب ٦ في ٣٠ يوم · الجملة الذكية · علامات الشبكة.
 */
class CalendarModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val late = item(CalendarItemType.RECURRING, "2026-10-03", -4, amount = 38_000, title = "فاتورة الكهرباء")
    private val gym = item(CalendarItemType.RECURRING, "2026-10-09", 2, amount = 19_900, reserved = 19_900, title = "اشتراك النادي")
    private val bday = item(CalendarItemType.OCCASION, "2026-10-14", 7, flow = null, title = "عيد ميلاد ليلى")
    private val owed = item(CalendarItemType.DEBT, "2026-10-15", 8, amount = 30_000, flow = DueFlow.RECEIVE, title = "مستحق من نورة")
    private val car = item(CalendarItemType.INSTALLMENT, "2026-10-27", 20, amount = 145_000, title = "قسط السيارة")
    private val pay = item(CalendarItemType.PAYDAY, "2026-10-28", 21, flow = null, title = "يوم الراتب")
    private val far = item(CalendarItemType.DEBT, "2026-11-20", 44, amount = 200_000, title = "سلفة عمر")

    @Test fun statusLineComesFromTheReservationNotFromTheScreen() {
        assertEquals("فات موعده", calRowOf(late).status)
        assertEquals(CalTone.LATE, calRowOf(late).statusTone)
        assertEquals("محجوز ✓، 199.00 ر.س", calRowOf(gym).status)
        assertEquals("بلا مبلغ", calRowOf(bday).status)
        assertEquals("ستستلمه، 300.00 ر.س", calRowOf(owed).status)
        assertEquals("غير محجوز، 1,450.00 ر.س", calRowOf(car).status)
        assertEquals(CalTone.OPEN, calRowOf(car).statusTone)
        assertEquals("ستستلمه", calRowOf(pay).status, "يوم الراتب من غير مبلغ")
        assertEquals("بعد يومين", calRowOf(gym).whenText)
        assertEquals(CalTone.OPEN, calRowOf(gym).whenTone, "قريب (≤٣) ⇒ كهرماني")
        assertEquals("منذ ٤ أيام", calRowOf(late).whenText)
        assertEquals("٣ أكتوبر، اشتراك", calRowOf(late).sub)
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("مش محجوز، 1,450.00 ر.س", calRowOf(car).status)
    }

    @Test fun upcomingKeepsLateDuesAndTheSummaryShowsSixInThirtyDays() {
        val passedOccasion = item(CalendarItemType.OCCASION, "2026-10-01", -6, flow = null)
        val ups = upcomingOf(listOf(far, car, passedOccasion, late, gym))
        assertEquals(listOf(late, gym, car, far), ups, "اللي فات من غير فلوس عليك بيختفي، والمتأخر بيفضل")
        assertEquals(listOf(late, gym, car), shownOf(ups, all = false), "الـ٣٠ يوم بس")
        assertEquals(ups, shownOf(ups, all = true))
        val many = (1..9).map { item(CalendarItemType.EVENT, "2026-10-1$it", it, flow = null) }
        assertEquals(6, shownOf(many, all = false).size)
    }

    @Test fun smartSentenceCountsDatesAndUnreservedPayments() {
        val ups = upcomingOf(listOf(late, gym, bday, owed, car, pay, far))
        val s = smartSentence(ups, beforePayday = listOf(car), nextPayday = "2026-10-28")!!
        assertTrue(s.startsWith("الأقرب: اشتراك النادي بعد يومين."), s)
        assertTrue("في الـ٣٠ يومًا القادمة ٥ مواعيد، منها ١ غير محجوزة" in s, s)
        assertTrue(s.endsWith("قسط السيارة قبل الراتب بيوم."), s)
        assertNull(smartSentence(emptyList(), emptyList(), null))
        assertEquals("موعدان", datesCount(2))
        assertEquals("١٢ موعدًا", datesCount(12))
    }

    @Test fun gridMarksOnlyThisMonthAndLateIsRed() {
        val marks = marksOf(listOf(late, gym, far, pay), 2026, 10)
        assertEquals(listOf(3, 9, 28), marks.map { it.day })
        assertEquals(MarkKind.LATE, marks[0].kind)
        assertTrue(marks[0].late)
        assertEquals(MarkKind.SUBSCRIPTION, marks[1].kind)
        assertEquals(MarkKind.PAYDAY, marks[2].kind)
        assertEquals("اشتراك", marks[1].label, "اسم قصير = أول كلمة")
    }
}
