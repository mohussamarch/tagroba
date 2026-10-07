package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * نسختين العربي (OVERRIDES §66): **فصحى مختصرة** للسعودية والافتراضي، و**المصري الحالي** لمصر. في commonTest عشان يشتغل على الآيفون كمان.
 * لقطة «المصري بالحرف» في `TextsTest` (jvmTest — بتقرا ملف).
 */
class ArabicVariantsTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val placeholder = Regex("""\{\d+\}""")

    private fun placeholders(text: String): List<String> = placeholder.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyKeyHasAnMsaAndAnEgyptianAndAnEnglishText() {
        for ((name, table) in listOf("فصحى" to MSA_TEXTS, "مصري" to EGYPTIAN_TEXTS, "إنجليزي" to ENGLISH_TEXTS)) {
            val missing = TextKey.entries.filter { it !in table }
            assertTrue(missing.isEmpty(), "مفاتيح من غير نص $name: $missing")
            val blank = table.filterValues { it.isBlank() }.keys
            assertTrue(blank.isEmpty(), "نص $name فاضي: $blank")
        }
    }

    @Test
    fun msaKeepsTheSamePlaceholdersAsEgyptian() {
        val broken = TextKey.entries.filter { placeholders(MSA_TEXTS.getValue(it)) != placeholders(EGYPTIAN_TEXTS.getValue(it)) }
        assertTrue(broken.isEmpty(), "متغيرات مختلفة بين الفصحى والمصري: " + broken.map { "$it: ${MSA_TEXTS[it]}" })
    }

    @Test
    fun msaKeepsCurrencySymbols() {
        for (symbol in listOf("ر.س", "ج.م")) {
            val lost = TextKey.entries.filter { EGYPTIAN_TEXTS.getValue(it).contains(symbol) && !MSA_TEXTS.getValue(it).contains(symbol) }
            assertTrue(lost.isEmpty(), "الفصحى ضيّعت «$symbol»: $lost")
        }
    }

    @Test
    fun msaHasNoEgyptianWords() {
        val found = TextKey.entries.mapNotNull { key ->
            val bad = egyptianTokens(MSA_TEXTS.getValue(key))
            if (bad.isEmpty()) null else "$key: $bad"
        }
        assertTrue(found.isEmpty(), "كلام مصري في الفصحى:\n" + found.joinToString("\n"))
        // الفحص نفسه بيمسك المصري (من غير ما يمسك جوه كلمة تانية)
        assertEquals(listOf("مش", "دلوقتي", "هتدفع", "اتسجل"), egyptianTokens("ده مش متاح دلوقتي — هتدفع لما اتسجل").filter { it != "ده" })
        assertTrue(egyptianTokens("المشروع مشترك ودليل الإيداع والاتصالات والاتجاه وبيع الأصل").isEmpty(), "ما يمسكش جوه كلمة")
        // وعلى الجدول المصري الحقيقي بيمسك كتير — يعني الفحص فعلًا شغال مش فاضي
        val egyptianHits = TextKey.entries.count { egyptianTokens(EGYPTIAN_TEXTS.getValue(it)).isNotEmpty() }
        assertTrue(egyptianHits >= 250, "الفحص مسك $egyptianHits نص مصري بس")
    }

    @Test
    fun missingVariantTextFallsBackToTheOtherVariantAndNeverCrashes() {
        val key = TextKey.NOT_AVAILABLE
        assertEquals("مصري", resolveArabic(key, emptyMap(), mapOf(key to "مصري")))
        assertEquals("فصحى", resolveArabic(key, mapOf(key to "فصحى"), mapOf(key to "مصري")))
        assertEquals(key.name, resolveArabic(key, emptyMap(), emptyMap()), "ولا نسخة ⇒ اسم المفتاح، من غير ما يقع")
    }

    @Test
    fun variantFollowsTheCountry() {
        Texts.followCountry("EG")
        assertEquals(ArabicVariant.EGYPTIAN, Texts.arabicVariant)
        assertEquals("مرتب", uiText(TextKey.KIND_SALARY))
        Texts.followCountry("sa")
        assertEquals(ArabicVariant.MSA, Texts.arabicVariant)
        assertEquals("راتب", uiText(TextKey.KIND_SALARY))
        Texts.followCountry("eg")
        assertEquals("مرتب", uiText(TextKey.KIND_SALARY))
        Texts.followCountry("XX")
        assertEquals(ArabicVariant.MSA, Texts.arabicVariant, "بلد مش معروفة ⇒ الفصحى")
        Texts.followCountry("EG")
        Texts.followCountry(null)
        assertEquals(ArabicVariant.MSA, Texts.arabicVariant, "مفيش بلد ⇒ الفصحى")
        assertEquals(ArabicVariant.EGYPTIAN, EGYPT_PACK.arabicVariant)
        assertEquals(ArabicVariant.MSA, SAUDI_PACK.arabicVariant)
    }

    @Test
    fun englishIgnoresTheArabicVariant() {
        Texts.language = Language.EN
        for (v in ArabicVariant.entries) {
            Texts.arabicVariant = v
            assertEquals("Salary", uiText(TextKey.KIND_SALARY))
            assertEquals("96.47 SAR", formatMoney(9647))
        }
    }

}
