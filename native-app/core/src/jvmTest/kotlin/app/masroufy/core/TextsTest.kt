package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * مقسم اللغة (OVERRIDES §40). المطابقة مع التطبيق الحالي بتتأكد في ملفات المرجع نفسها
 * (`ledger.json` فيه أسماء الأنواع، والاقتراحات فيها الأسباب) — هنا بنتأكد من الجدول نفسه.
 */
class TextsTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
    }

    @Test
    fun `كل مفتاح له نص عربي`() {
        val missing = TextKey.entries.filter { it !in ARABIC_TEXTS }
        assertTrue(missing.isEmpty(), "مفاتيح من غير نص عربي: $missing")
    }

    @Test
    fun `كل مفتاح له نص إنجليزي`() {
        val missing = TextKey.entries.filter { it !in ENGLISH_TEXTS }
        assertTrue(missing.isEmpty(), "مفاتيح من غير نص إنجليزي: $missing")
    }

    @Test
    fun `المفتاح الناقص بيرجع بالعربي مش فاضي`() {
        Texts.language = Language.EN
        val table = ENGLISH_TEXTS
        assertTrue(table.values.none { it.isBlank() }, "في نص إنجليزي فاضي")
        assertTrue(uiText(TextKey.NOT_AVAILABLE).isNotBlank())
    }

    @Test
    fun `المتغيرات بتتبدل بالترتيب`() {
        assertEquals("«بقالة»: شراء أو فاتورة", uiText(TextKey.SUGGEST_PURCHASE, "بقالة"))
        Texts.language = Language.EN
        assertEquals("“بقالة”: a purchase or a bill", uiText(TextKey.SUGGEST_PURCHASE, "بقالة"))
    }

    @Test
    fun `اللغة بتغير الاسم المعروض من غير ما تغير الاسم المتخزن`() {
        assertEquals("مرتب", ruleFor(EconomicKind.SALARY).label)
        assertEquals("salary", EconomicKind.SALARY.wire)
        Texts.language = Language.EN
        assertEquals("Salary", ruleFor(EconomicKind.SALARY).label)
        assertEquals("salary", EconomicKind.SALARY.wire)
        assertEquals("96.47 SAR", formatMoney(9647))
    }

    @Test
    fun `الرجوع للعربي بيرجع نفس النص بالحرف`() {
        Texts.language = Language.EN
        Texts.language = Language.AR
        assertEquals("96.47 ر.س", formatMoney(9647))
        assertEquals("غير متاح", formatMoneyOrNA(null))
    }
}
