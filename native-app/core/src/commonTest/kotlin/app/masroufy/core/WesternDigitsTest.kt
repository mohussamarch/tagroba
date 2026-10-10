package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * قرار المالك OVERRIDES §79 (L5): **أرقام 0-9 بس** في كل حاجة التطبيق بيعرضها — ولا رقم عربي هندي ولا فارسي
 * ولا «٪ ٫ ٬» في أي نص من جداول النصوص التلاتة. قراءة رسايل البنك وكشوفه برا الاختبار ده (بتقبل الاتنين زي ما هي).
 */
class WesternDigitsTest {
    private val eastern = Regex("[٠-٩۰-۹٪٫٬]")

    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test
    fun noTextMapValueHasEasternDigits() {
        val tables = mapOf("MSA" to MSA_TEXTS, "EGYPTIAN" to EGYPTIAN_TEXTS, "ENGLISH" to ENGLISH_TEXTS)
        val bad = tables.flatMap { (name, table) ->
            table.filterValues { eastern.containsMatchIn(it) }.map { (k, v) -> "$name.${k.name} = $v" }
        }
        assertTrue(bad.isEmpty(), "نصوص فيها أرقام عربية شرقية:\n" + bad.joinToString("\n"))
    }

    /** نصوص المساعد (`AskKey` كلها + `TextKey.ASSIST_*`) زي ما بتطلع فعلًا في كل لغة ولهجة — مش بس الجداول. */
    @Test
    fun assistantTextsAreWesternInEveryLanguage() {
        val assistant: List<TextRef> = AskKey.entries + TextKey.entries.filter { it.name.startsWith("ASSIST_") }
        assertTrue(AskKey.entries.isNotEmpty() && assistant.size > AskKey.entries.size, "نصوص المساعد موجودة")
        val bad = mutableListOf<String>()
        for (language in Language.entries) for (variant in ArabicVariant.entries) {
            Texts.language = language
            Texts.arabicVariant = variant
            for (key in assistant) {
                val shown = uiText(key, "1", "2", "3", "4", "5")
                if (eastern.containsMatchIn(shown)) bad += "$language/$variant.${key.name} = $shown"
            }
        }
        assertTrue(bad.isEmpty(), "نصوص المساعد فيها أرقام عربية شرقية:\n" + bad.joinToString("\n"))
    }

    @Test
    fun sentenceNumbersAndDatesAreWesternInEveryLanguage() {
        for (variant in ArabicVariant.entries) {
            Texts.language = Language.AR
            Texts.arabicVariant = variant
            assertEquals("2026", sentenceNumber(2026))
            assertTrue(dayMonth("2026-10-18").startsWith("18 "), dayMonth("2026-10-18"))
            assertTrue(monthYear(2026, 10).endsWith(" 2026"), monthYear(2026, 10))
            assertEquals("1,234.50 ${currencyLabel(Currency.SAR)}", formatMoney(123450))
            assertEquals("12.5%", formatPercentOrNA(12.5))
        }
    }

    @Test
    fun typedEasternDigitsBecomeWesternBeforeParsing() {
        assertEquals("120.50", normalizeDigits("١٢٠٫٥٠"))
        assertEquals("1,250", normalizeDigits("۱٬۲۵۰"))
        assertEquals(12050L, parseMoney("١٢٠٫٥٠"))
        assertEquals(12050L, parseMoney("۱۲۰.۵۰"))
        assertEquals("قهوة 25", latinizeDigits("قهوة ٢٥"))
    }
}
