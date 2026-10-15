package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * علامة الريال الرسمية (قرار المالك 2026-10-15): «⃁ 1,250.00» — العلامة شمال الرقم بمسافة، جوه عزل LTR (U+2066 … U+2069)
 * في الفصحى والمصري والإنجليزي. الجنيه زي ما هو («1,000.00 ج.م»). النص الطالع برا التطبيق بيرجع «ر.س».
 */
class RiyalSignTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val variants = listOf(
        "فصحى" to { Texts.language = Language.AR; Texts.arabicVariant = ArabicVariant.MSA },
        "مصري" to { Texts.language = Language.AR; Texts.arabicVariant = ArabicVariant.EGYPTIAN },
        "إنجليزي" to { Texts.language = Language.EN; Texts.arabicVariant = ArabicVariant.MSA },
    )

    @Test
    fun riyalSignIsLeftOfTheNumberInsideAnLtrIsolateInEveryLanguage() {
        for ((name, use) in variants) {
            use()
            assertEquals("\u2066\u20C1 1,250.00\u2069", formatMoney(125_000), name)
            assertEquals("\u2066\u20C1 -42.00\u2069", formatMoney(-4_200), name)
            assertEquals("\u2066\u20C1 +42.00\u2069", formatMoney(4_200, alwaysSign = true), name)
            assertEquals("\u2066\u20C1 ••••\u2069", formatMoney(4_200, hidden = true), name)
            assertEquals("1,250.00", formatMoney(125_000, showCurrency = false), name)
            assertEquals("\u20C1", currencySymbol(Currency.SAR), name)
            val text = formatMoney(125_000)
            assertTrue(text.first() == LTR_ISOLATE && text.last() == POP_ISOLATE, "$name: العزل")
            assertTrue(text.indexOf(RIYAL_SIGN) < text.indexOfFirst { it.isDigit() }, "$name: العلامة قبل الرقم")
            assertFalse(text.contains("ر.س") || text.contains("SAR"), name)
        }
    }

    @Test
    fun egyptianPoundStaysAfterTheNumber() {
        variants.take(2).forEach { (name, use) -> use(); assertEquals("1,000.00 ج.م", formatMoney(100_000, Currency.EGP), name) }
        variants[2].second()
        assertEquals("1,000.00 EGP", formatMoney(100_000, Currency.EGP))
        assertEquals("1,000.00 EGP", withCurrency("1,000.00", Currency.EGP))
    }

    @Test
    fun textLeavingTheAppGoesBackToThePlainLabel() {
        variants[0].second()
        assertEquals("بقي 1,250.00 ر.س", outgoingText("بقي ${formatMoney(125_000)}"))
        assertEquals("1,000.00 ج.م", outgoingText(formatMoney(100_000, Currency.EGP)))
        assertEquals("بالريال (ر.س)", outgoingText("بالريال (\u20C1)"))
        assertFalse(isLockSafe("\u20C1") || isLockSafe("ر.س"))
        variants[2].second()
        assertEquals("left 1,250.00 SAR", outgoingText("left ${formatMoney(125_000)}"))
    }

    /** الماسح: مفيش نص عرض جوه التطبيق لسه فيه «ر.س» — غير مفتاح النص الطالع برا بس. */
    @Test
    fun noInAppTextStillWritesTheOldRiyalLabel() {
        for ((name, table) in listOf("فصحى" to MSA_TEXTS, "مصري" to EGYPTIAN_TEXTS, "إنجليزي" to ENGLISH_TEXTS)) {
            val old = table.filter { (k, v) -> k != TextKey.CURRENCY_SAR_PLAIN && v.contains("ر.س") }.keys
            assertTrue(old.isEmpty(), "نص $name لسه فيه «ر.س»: $old")
            assertEquals("\u20C1", table[TextKey.CURRENCY_SAR], name)
        }
    }
}
