package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.GrowthClass
import app.masroufy.core.Language
import app.masroufy.core.Texts
import app.masroufy.usecase.CompareSavingsGrowth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «لو وضعتها في…» (`SavingsGrowth`): من `CompareSavingsGrowth` لحالة القطعة. ملف المتوسطات لسه ما وصلش ⇒ كل نوع من غير معدل «غير متاح»
 * (مش صفر) ويطلب النسبة، والكاش بس ثابت. النسبة اللي المستخدم يكتبها بتغلب.
 */
class SavingsGrowthPresenterTest {
    private val today = "2026-10-07"
    private val growth = CompareSavingsGrowth()

    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test
    fun withoutTheAveragesFileEveryRateIsUnavailableExceptCash() {
        val ui = growthUi(growth.compare(100_000, 120, 0, today, feed = null, countryCode = "SA"), Currency.SAR)
        assertEquals("من جيبك: 120,000.00 ر.س خلال 10 سنوات", ui.paidLine)
        assertEquals(GrowthClass.entries.toList(), ui.rows.map { it.growthClass }, "الخمسة بالترتيب")
        for (r in ui.rows.filter { it.growthClass != GrowthClass.CASH }) {
            assertNull(r.amountMinor, "${r.growthClass}: «غير متاح» مش صفر")
            assertTrue(r.rateMissing)
            assertTrue(r.editStrong, "«اكتب النسبة» زرار مليان")
            assertEquals(0, r.barPercent, "من غير شريط")
        }
        assertEquals("اكتب فائدة بنكك على الوديعة", ui.rows.first { it.growthClass == GrowthClass.DEPOSIT }.rateText)
        val cash = ui.rows.first { it.growthClass == GrowthClass.CASH }
        assertEquals(12_000_000, cash.amountMinor, "الكاش = اللي حطيته بالظبط")
        assertNull(cash.gainMinor, "زيادة صفر ما بتتكتبش")
        assertFalse(cash.editEnabled)
        assertEquals("ثابت: صفر", cash.editLabel)
        assertEquals(100, cash.barPercent)
    }

    @Test
    fun aTypedRateWinsAndShowsItsSource() {
        val ui = growthUi(growth.compare(100_000, 120, 0, today, null, "SA", userRates = mapOf(GrowthClass.GOLD to 1488)), Currency.SAR)
        val gold = ui.rows.first { it.growthClass == GrowthClass.GOLD }
        assertNotNull(gold.amountMinor)
        assertTrue(gold.amountMinor!! > 12_000_000, "بزيادة 14.88% في السنة")
        assertEquals("14.88% سنويًا", gold.rateText)
        assertEquals("النسبة التي كتبتها أنت", gold.source)
        assertEquals(1488, gold.rateBp)
        assertFalse(gold.canReset, "مفيش متوسط يرجعله لحد ما الملف يوصل")
        assertEquals("عدّل النسبة", gold.editLabel)
        assertEquals(100, gold.barPercent, "الأعلى = الشريط كله")
        assertTrue(ui.rows.first { it.growthClass == GrowthClass.CASH }.barPercent < 100)
    }

    @Test
    fun todaysMoneyWithoutInflationKeepsTheNumbersAndSaysWhy() {
        val ui = growthUi(growth.compare(100_000, 120, 0, today, null, "SA", mapOf(GrowthClass.GOLD to 1488), todayMoney = true), Currency.SAR)
        assertTrue(ui.todayOn)
        assertTrue(ui.todaySubWarn)
        assertEquals("«بقيمة المال اليوم» غير متاح: معدل التضخم الرسمي غير معروف", ui.todaySub)
        assertEquals(12_000_000, ui.rows.first { it.growthClass == GrowthClass.CASH }.amountMinor, "الأرقام زي ما هي (زي النموذج)")
        assertNotNull(ui.rows.first { it.growthClass == GrowthClass.GOLD }.amountMinor)
    }

    @Test
    fun egyptWording() {
        Texts.followCountry("EG")
        val ui = growthUi(growth.compare(300_000, 24, 0, today, null, "EG"), Currency.EGP)
        assertEquals("من جيبك: 72,000.00 ج.م على سنتين", ui.paidLine)
        assertEquals("«بقيمة فلوس النهارده» غير متاح: التضخم الرسمي مش معروف", ui.todaySub)
        assertEquals("اكتب فايدة بنكك على الوديعة", ui.rows.first { it.growthClass == GrowthClass.DEPOSIT }.rateText)
    }

    @Test
    fun theTypedRateIsReadInBasisPointsWithinTheLimits() {
        assertEquals(1488, parseRateBp("14.88"))
        assertEquals(450, parseRateBp("4.5"))
        assertEquals(450, parseRateBp("4.5 %"))
        assertEquals(-200, parseRateBp("-2"))
        assertNull(parseRateBp("1001"), "أكبر من 1000% سنويًا")
        assertNull(parseRateBp("abc"))
        assertEquals("14.88", rateInputText(1488))
        assertEquals("", rateInputText(null))
    }

    @Test
    fun typedRatesSurviveTheScreenStateAsText() {
        val rates = mapOf(GrowthClass.GOLD to 1488, GrowthClass.DEPOSIT to -200)
        assertEquals(rates, decodeRates(encodeRates(rates)))
        assertTrue(decodeRates("").isEmpty())
        assertEquals(mapOf(GrowthClass.GOLD to 5), decodeRates("gold:5,nonsense,x:y"))
    }
}
