package app.masroufy.ui.screens.dues

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.dayMonth
import app.masroufy.core.roscaForecast
import app.masroufy.core.roscaStatus
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.usecase.RoscaView
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «الجمعيات» و«تفاصيل الجمعية»: `ManageRoscas.list` (`RoscaStatus`) و`forecast` ⇒ البطاقة والأدوار والتحليل.
 * الموقف والمكسب والتحليل كلهم من حالة الاستخدام؛ الشاشة بتشيل الإشارة للعرض بس، ودورك المجهول ⇒ «غير متاح» مش صفر.
 */
class RoscasModelTest {
    private val family = Rosca(
        "j1", "جمعية العائلة", Currency.SAR, contributionMinor = 50_000, every = 1, firstDueAt = "2026-06-01",
        cycleCount = 10, myTurns = listOf(6), payoutMinor = 500_000,
    )
    private val work = family.copy(id = "j2", name = "جمعية العمل", myTurns = listOf(2))
    private val draw = family.copy(id = "j3", name = "جمعية القرعة", myTurns = emptyList(), payoutMinor = 0)

    private fun paid(id: String, n: Int) = (1..n).map { RoscaEntry("e-$id-$it", id, "t-$id-$it", RoscaEntryKind.CONTRIBUTION, 50_000) }

    private fun view(r: Rosca, entries: List<RoscaEntry>) = RoscaView(r, roscaStatus(r, entries, TODAY))

    @BeforeTest fun arabic() = useArabic()

    @AfterTest fun reset() = resetTexts()

    @Test fun savingCardShowsWhatTheUseCaseSays() {
        val c = roscaCard(view(family, paid("j1", 4)))
        assertEquals(sentenceNumber(10) + " أدوار · " + amountLabel(50_000, Currency.SAR) + " كل شهر", c.terms)
        assertEquals("ادخار", c.stage)
        assertEquals(Chip.UPCOMING, c.stageChip)
        assertEquals("دفعت " + sentenceNumber(4) + " من " + sentenceNumber(10), c.progressText)
        assertEquals("الدور رقم " + sentenceNumber(6), c.turnText)
        assertEquals(200_000L, c.paidMinor)
        assertNull(c.gotMinor, "ما قبضتش ⇒ «—» مش صفر")
        assertEquals(0L, c.gainMinor)
        assertEquals("الجمعية تحفظ لك " + amountLabel(200_000, Currency.SAR), c.position)
        assertTrue(c.positionPositive)
    }

    @Test fun repayingCardDropsTheSignForDisplay() {
        val v = view(work, paid("j2", 4) + RoscaEntry("e-p", "j2", "t-p", RoscaEntryKind.PAYOUT, 500_000))
        val c = roscaCard(v)
        assertEquals("سداد", c.stage)
        assertEquals(500_000L, c.gotMinor)
        assertEquals("عليك للجمعية " + amountLabel(300_000, Currency.SAR), c.position, "−3000 بيتعرض «عليك للجمعية 3000»")
        assertFalse(c.positionPositive)
        val d = roscaDetailUi(v, roscaForecast(work), TODAY)
        assertEquals("عليك للجمعية", d.heroLabel)
        assertEquals(300_000L, d.heroMinor)
    }

    @Test fun detailRowsAndAnalysisWhenTheTurnIsKnown() {
        val d = roscaDetailUi(view(family, paid("j1", 4)), roscaForecast(family), TODAY, organizer = "نورة")
        assertTrue(d.card.terms.endsWith(" · تدفع إلى نورة"), "اسم اللي بيلم الفلوس في سطر الشروط")
        assertEquals("الجمعية تحفظ لك", d.heroLabel)
        assertEquals(200_000L, d.heroMinor)
        assertEquals(10, d.rows.size)
        assertEquals("دُفع", d.rows[0].status)
        assertTrue(d.rows[4].late, "الدور الخامس (١ أكتوبر) فات ولسه ما اتدفعش")
        assertEquals("متأخر", d.rows[4].status)
        assertEquals("دورك · ستقبض " + amountLabel(500_000, Currency.SAR, showCurrency = false), d.rows[5].status)
        assertEquals("تحفظ لك " + amountLabel(250_000, Currency.SAR, showCurrency = false), d.rows[4].after)
        assertEquals("عليك " + amountLabel(200_000, Currency.SAR, showCurrency = false), d.rows[5].after)
        assertTrue(d.focusRows.any { it.mine })
        val payout = d.stats[0]
        assertEquals(StatValue.Text(dayMonth("2026-11-01")), payout.value)
        assertEquals(StatValue.Text("لا شيء"), d.stats[1].value, "مكسب صفر ⇒ «لا شيء»")
        assertEquals("تقبض بقدر ما تدفع", d.stats[1].note)
        assertEquals(StatValue.Money(500_000), d.stats[2].value)
        assertEquals(StatValue.Text(sentenceNumber(5) + " أقساط"), d.stats[4].value, "أقساط قبل القبض من التحليل")
        assertEquals(StatValue.Money(250_000, tint = StatTint.INCOME), d.stats[6].value, "أكبر مبلغ تحفظه لك — أخضر")
        assertEquals(StatValue.Money(200_000, tint = StatTint.EXPENSE), d.stats[7].value, "أكبر دين عليك — أحمر")
        assertEquals("محسوب من بيانات الجمعية: قسطك وعدد الأدوار ودورك.", d.note)
    }

    @Test fun unknownTurnIsUnavailableNotZero() {
        val v = view(draw, paid("j3", 2))
        val c = roscaCard(v)
        assertEquals("دورك غير معروف بعد", c.turnText)
        assertNull(c.gainMinor)
        val d = roscaDetailUi(v, roscaForecast(draw), TODAY)
        assertEquals(StatValue.NA, d.stats[0].value)
        assertEquals(StatValue.NA, d.stats[1].value)
        assertEquals("يحتاج دورك", d.stats[1].note)
        assertEquals(StatValue.NA, d.stats[3].value, "إجمالي ما تقبضه غير متاح")
        assertEquals(listOf(StatValue.NA, StatValue.NA), d.stats.takeLast(2).map { it.value })
        assertTrue(d.rows.all { it.after == null }, "الموقف بعد كل دور محتاج دورك")
        assertTrue(d.note.startsWith("حين يُعرف دورك"))
    }

    @Test fun egyptWording() {
        useArabic(ArabicVariant.EGYPTIAN)
        val c = roscaCard(view(family, paid("j1", 4)))
        assertEquals("الجمعية شايلالك " + amountLabel(200_000, Currency.SAR), c.position)
        assertEquals("دور رقم " + sentenceNumber(6), c.turnText)
        val d = roscaDetailUi(view(draw, emptyList()), roscaForecast(draw), TODAY)
        assertEquals("محتاج دورك", d.stats[1].note)
    }
}
