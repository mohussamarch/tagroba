package app.masroufy.ui.screens.investment.future

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Texts
import app.masroufy.ui.screens.investment.common.ChartFlag
import app.masroufy.ui.screens.investment.common.ChartSeries
import app.masroufy.ui.screens.investment.common.chartScale
import app.masroufy.ui.screens.investment.common.flagFraction
import app.masroufy.ui.screens.investment.common.niceStep
import app.masroufy.ui.screens.investment.common.xLabelIndices
import androidx.compose.ui.graphics.Color
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** عرض «هتبقى معاك كام» (دوال نقية): الأرقام بالوحدات الكاملة · المحاور · اسم المدة · سعر الصرف · خطوة الشبكة والسنين. */
class FutureTextTest {
    @AfterTest fun reset() { Texts.arabicVariant = ArabicVariant.MSA }

    @Test fun wholeUnitsDropTheFractionAndUseTrueMinus() {
        assertEquals("1,134,480", whole(113_448_099))
        assertEquals("−447,820", whole(-44_782_000))
        assertEquals("0", whole(99))
    }

    @Test fun axisLabelsAreShort() {
        assertEquals("1.5 مليون", shortAxis(150_000_000))
        assertEquals("1 مليون", shortAxis(100_000_000))
        assertEquals("500 ألف", shortAxis(50_000_000))
        assertEquals("0", shortAxis(0))
    }

    @Test fun yearsNameFollowsTheVariant() {
        assertEquals(listOf("سنة", "سنتان", "5 سنوات", "10 سنوات", "15 سنة"), listOf(1, 2, 5, 10, 15).map(::yearsName))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals(listOf("سنة", "سنتين", "5 سنين", "40 سنة"), listOf(1, 2, 5, 40).map(::yearsName))
    }

    @Test fun fxShowsTwoDecimals() {
        assertEquals("13.93", fxText(139_330))
        assertEquals("13.94", fxText(139_350))
    }

    @Test fun gridStepIsNiceAndYearsLabelsKeepTheLast() {
        assertEquals(listOf(1L, 2L, 25L, 50L, 100L, 250L), listOf(1L, 2L, 21L, 41L, 99L, 201L).map(::niceStep))
        val s = chartScale(listOf(ChartSeries("k", "k", Color.Black, listOf(68_666_000, 122_000_000))))
        assertEquals(0L, s.lo)
        assertEquals(150_000_000L, s.hi, "خطوة 50 مليون هللة (500 ألف)")
        assertEquals(listOf(0, 2, 4, 6, 8, 10), xLabelIndices(10))
        assertEquals(listOf(0, 5, 9), xLabelIndices(9))
        assertEquals(listOf(0, 10, 20, 30, 40), xLabelIndices(40))
        assertEquals(0.5f, flagFraction(ChartFlag(60, "هدف"), 10))
        assertNull(flagFraction(ChartFlag(130, "هدف"), 10), "بعد المدة ما بيترسمش")
    }
}
