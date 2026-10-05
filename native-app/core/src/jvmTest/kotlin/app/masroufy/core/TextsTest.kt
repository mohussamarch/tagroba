package app.masroufy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * جدول النصوص (OVERRIDES §40 · §66): العربي بنسختين (فصحى للسعودية · المصري الحالي لمصر) + الإنجليزي.
 * المطابقة مع التطبيق الحالي بتتأكد في ملفات المرجع نفسها (بالنسخة المصرية) — هنا بنتأكد من الجداول نفسها.
 */
class TextsTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    /** المصري = الجداول القديمة **بالحرف** (لقطة اتاخدت من الكود قبل الفصحى — `egyptian-texts-snapshot.json`). */
    @Test
    fun egyptianIsTheOldTextByteForByte() {
        val raw = TextsTest::class.java.getResource("/egyptian-texts-snapshot.json")!!.readText(Charsets.UTF_8)
        val snapshot = Json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.content }
        assertEquals(710, snapshot.size, "اللقطة فيها كل مفاتيح ما قبل الفصحى")
        for ((name, old) in snapshot) {
            assertEquals(old, EGYPTIAN_TEXTS[TextKey.valueOf(name)], "المصري اتغير في $name")
        }
        // أي مفتاح بعد اللقطة لازم يكون في جدول معروف من بعدها: الرسايل اللي اتنقلت من الكود (ملفات المرجع بتتأكد من نصها) · شاشة الأشخاص (جلسة 16)
        val newer = TextKey.entries.map { it.name }.filter { it !in snapshot }
        val afterSnapshot = EGYPTIAN_USECASE_TEXTS.keys + EGYPTIAN_PEOPLE_TEXTS.keys
        assertTrue(newer.all { TextKey.valueOf(it) in afterSnapshot }, "مفتاح جديد مالوش مكان معروف: $newer")
    }

    @Test
    fun placeholdersAreFilledInOrder() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("«بقالة»: شراء أو فاتورة", uiText(TextKey.SUGGEST_PURCHASE, "بقالة"))
        Texts.arabicVariant = ArabicVariant.MSA
        assertEquals("مضى 3 يومًا فقط — التوقع يحتاج 7 أيام على الأقل.", uiText(TextKey.FORECAST_TOO_EARLY, "3", "7"))
        Texts.language = Language.EN
        assertEquals("“بقالة”: a purchase or a bill", uiText(TextKey.SUGGEST_PURCHASE, "بقالة"))
    }

    @Test
    fun languageChangesTheLabelNotTheStoredName() {
        assertEquals("راتب", ruleFor(EconomicKind.SALARY).label)
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("مرتب", ruleFor(EconomicKind.SALARY).label)
        assertEquals("salary", EconomicKind.SALARY.wire)
        Texts.language = Language.EN
        assertEquals("Salary", ruleFor(EconomicKind.SALARY).label)
        assertEquals("salary", EconomicKind.SALARY.wire)
    }

    @Test
    fun backToArabicGivesTheSameTextAgain() {
        Texts.language = Language.EN
        Texts.language = Language.AR
        assertEquals("96.47 ر.س", formatMoney(9647))
        assertEquals("غير متاح", formatMoneyOrNA(null))
    }
}
