package app.masroufy.ui.text

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import app.masroufy.core.Currency
import app.masroufy.core.RIYAL_SIGN
import app.masroufy.core.withCurrency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** العلامة والرقم عمرهم ما يتفرقوا على سطرين (المالك 2026-10-15 — العلامة كانت بتنزل لوحدها تحت «معك الآن»). */
class RiyalTogetherTest {
    @Test
    fun theSpaceAfterTheSignNeverBreaks() {
        val shown = keepRiyalTogether(withCurrency("1,250.00", Currency.SAR))
        assertTrue("$RIYAL_SIGN$NO_BREAK_SPACE" + "1,250.00" in shown)
        assertFalse("$RIYAL_SIGN " in shown)
        assertEquals(withCurrency("1,250.00", Currency.SAR).length, shown.length)
    }

    @Test
    fun sentencesWithTwoAmountsKeepBoth() {
        val line = "دفعت ${withCurrency("10.00", Currency.SAR)} من ${withCurrency("90.00", Currency.SAR)}"
        assertEquals(2, keepRiyalTogether(line).split("$RIYAL_SIGN$NO_BREAK_SPACE").size - 1)
    }

    @Test
    fun otherCurrenciesAndPlainTextAreUntouched() {
        val egp = withCurrency("1,000.00", Currency.EGP)
        assertEquals(egp, keepRiyalTogether(egp))
        assertEquals("مرحبا بك", keepRiyalTogether("مرحبا بك"))
    }

    @Test
    fun styledTextKeepsItsStylesInPlace() {
        val bold = SpanStyle(fontWeight = FontWeight.Bold)
        val text = buildAnnotatedString {
            append("الباقي ")
            withStyle(bold) { append(withCurrency("42.00", Currency.SAR)) }
        }
        val shown = keepRiyalTogether(text)
        assertTrue("$RIYAL_SIGN$NO_BREAK_SPACE" in shown.text)
        assertEquals(text.spanStyles.map { it.start to it.end }, shown.spanStyles.map { it.start to it.end })
        val plain = buildAnnotatedString { append("بدون علامة") }
        assertSame(plain, keepRiyalTogether(plain))
    }
}
