package app.masroufy.ui.screens.dues

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.RecurringCandidate
import app.masroufy.core.RecurringItem
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.usecase.RecurringItemView
import app.masroufy.usecase.RecurringView
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «الاشتراكات والفواتير» و«تفاصيل الاشتراك»: `ManageRecurring.load` ⇒ الصفوف والاقتراح والتفاصيل، وتعديل المبلغ قبل `save`.
 * المدفوع والسنوي من حالة الاستخدام؛ الشاشة بتختار الكلام وبتقرا نص المبلغ بس.
 */
class SubscriptionsModelTest {
    private fun item(id: String, name: String, next: String, kind: String = "subscription", cycle: Int = 1, active: Boolean = true) =
        RecurringItem(id, name, "m-$id", kind, cycle, 9_900, Currency.SAR, next, active, confirmed = true)

    private fun itemView(i: RecurringItem, paid: Long? = 29_700, count: Int = 3, overdue: Boolean = false) =
        RecurringItemView(i, paid, count, 118_800, overdue)

    private val gym = item("s1", "اشتراك النادي", "2026-10-11")
    private val power = item("s2", "فاتورة الكهرباء", "2026-10-03", kind = "bill")
    private val cloud = item("s3", "تخزين سحابي", "2027-01-05", cycle = 12, active = false)
    private val candidate = RecurringCandidate("خدمة بث", "stream", Currency.SAR, 4_500, "2026-10-24", 1, listOf("t1", "t2", "t3"), "reason text")

    private fun view(vararg items: RecurringItemView, candidates: List<RecurringCandidate> = listOf(candidate)) =
        RecurringView("2025-10-09", TODAY, emptyList(), candidates, items.toList())

    @BeforeTest fun arabic() = useArabic()

    @AfterTest fun reset() = resetTexts()

    @Test fun rowsAreByDateWithTheirStatus() {
        val ui = subscriptionsUi(view(itemView(gym), itemView(power, overdue = true), itemView(cloud)), TODAY, emptySet())
        assertEquals(listOf("s2", "s1", "s3"), ui.rows.map { it.itemId })
        val (late, soon, stopped) = ui.rows
        assertEquals(Chip.OVERDUE to "متأخرة", late.chip to late.chipText)
        assertEquals("فاتورة، " + dayMonth("2026-10-03"), late.sub)
        assertEquals(Chip.SOON to "قريبة", soon.chip to soon.chipText)
        assertEquals(Chip.MUTED to "متوقف", stopped.chip to stopped.chipText)
        assertEquals("سنوي", stopped.cycle)
        assertEquals("خدمة بث", ui.candidate?.name)
    }

    @Test fun aDismissedSuggestionStaysHiddenAndNothingIsTheEmptyState() {
        val hidden = subscriptionsUi(view(itemView(gym)), TODAY, setOf(candidateKey(candidate)))
        assertNull(hidden.candidate, "«لا» بيخفي الاقتراح (في الجلسة دي بس — الحفظ مش في حالة الاستخدام)")
        val empty = subscriptionsUi(view(candidates = emptyList()), TODAY, emptySet())
        assertTrue(empty.empty)
        val confirm = candidate.confirmInput()
        assertEquals(listOf("خدمة بث", "stream", "subscription"), listOf(confirm.name, confirm.merchantKey, confirm.kind))
        assertEquals(4_500L, confirm.expectedMinor)
        assertTrue(confirm.active)
    }

    @Test fun detailOfAnActiveSubscription() {
        val d = subDetailUi(itemView(gym), TODAY)
        assertEquals("اشتراك، شهري", d.kindLine)
        assertEquals("الموعد القادم", d.nextLabel)
        assertEquals(dayMonth("2026-10-11"), d.nextText)
        assertEquals("بعد يومين، المتوقع " + amountLabel(9_900, Currency.SAR), d.nextSub)
        assertEquals(sentenceNumber(3) + " دفعات", d.paidCount)
        assertEquals(amountLabel(9_900, Currency.SAR, showCurrency = false) + " × " + sentenceNumber(12), d.annualNote)
        val late = subDetailUi(itemView(power, overdue = true), TODAY)
        assertEquals("متأخرة منذ " + sentenceNumber(6) + " أيام، المتوقع " + amountLabel(9_900, Currency.SAR), late.nextSub)
        assertEquals("لا دفعات بعد", subDetailUi(itemView(gym, paid = null, count = 0), TODAY).paidCount, "صفر ⇒ جملة مش «٠ دفعات»")
    }

    @Test fun detailOfAStoppedSubscription() {
        val d = subDetailUi(itemView(cloud), TODAY)
        assertEquals("المتابعة متوقفة", d.nextLabel)
        assertEquals("—", d.nextText)
        assertEquals("لن يظهر في المستحقات ولن ننبّهك بموعده.", d.nextSub)
        assertEquals("خارج الحساب", d.annualNote)
    }

    @Test fun editReadsTheAmountAndKeepsTheRest() {
        val (input, error) = subEditInput(gym, "120", 3, "2026-11-11")
        assertNull(error)
        val ok = assertNotNull(input)
        assertEquals(listOf("s1", "m-s1", "subscription"), listOf(ok.id, ok.merchantKey, ok.kind))
        assertEquals(12_000L, ok.expectedMinor)
        assertEquals(3, ok.cycleMonths)
        assertEquals("2026-11-11", ok.nextDueAt)
        assertEquals(null to "اكتب المبلغ بالأرقام، مثل 1200.50", subEditInput(gym, "x", 1, TODAY))
        assertEquals(null to "اكتب مبلغًا أكبر من صفر.", subEditInput(gym, "0", 1, TODAY))
        val stop = gym.withActive(false)
        assertEquals(false, stop.active)
        assertEquals(gym.expectedMinor, stop.expectedMinor, "الإيقاف بيغيّر المتابعة بس")
    }

    @Test fun egyptWording() {
        useArabic(ArabicVariant.EGYPTIAN)
        val d = subDetailUi(itemView(gym), TODAY)
        assertEquals("اشتراك، كل شهر", d.kindLine)
        assertEquals("الميعاد الجاي", d.nextLabel)
        assertEquals("قرّبت", d.chipText)
        assertEquals("لسه مفيش دفعات", subDetailUi(itemView(gym, paid = null, count = 0), TODAY).paidCount)
    }
}
