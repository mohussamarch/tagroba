package app.masroufy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
            val key = TextKey.valueOf(name)
            // تبسيط الكلمات الثقيلة (جلسة 18 — `PlainWords.kt`): المفاتيح دي بس اتغيرت، وبالمقاطع المعتمدة بالظبط
            val expected = if (key in EGYPTIAN_PLAIN_WORDS_KEYS) plainEgyptian(old) else old
            if (key in EGYPTIAN_PLAIN_WORDS_KEYS) assertNotEquals(old, expected, "التبسيط ما غيّرش $name")
            else assertEquals(old, plainEgyptian(old), "مقطع تبسيط بيلمس نص مش في القايمة: $name")
            assertEquals(expected, EGYPTIAN_TEXTS[key], "المصري اتغير في $name")
        }
        // أي مفتاح بعد اللقطة لازم يكون في جدول معروف من بعدها: الرسايل اللي اتنقلت من الكود (ملفات المرجع بتتأكد من نصها) · شاشة الأشخاص (جلسة 16) · المساعد المالي (§68)
        val newer = TextKey.entries.map { it.name }.filter { it !in snapshot }
        val afterSnapshot = EGYPTIAN_USECASE_TEXTS.keys + EGYPTIAN_PEOPLE_TEXTS.keys + EGYPTIAN_FEED_ALERT_TEXTS.keys + EGYPTIAN_ADVISOR_TEXTS.keys +
            EGYPTIAN_ADVISOR_MORE_TEXTS.keys
        assertTrue(newer.all { TextKey.valueOf(it) in afterSnapshot }, "مفتاح جديد مالوش مكان معروف: $newer")
    }

    /**
     * تبسيط الكلمات الثقيلة (جلسة 18): رسايل حالات الاستخدام (مش في اللقطة) — النص القديم هنا بالحرف من التطبيق الحالي،
     * والجديد = القديم بعد المقاطع المعتمدة. وولا نص في الجدول لسه فيه مقطع قديم (المصري والفصحى).
     */
    @Test
    fun plainWordsReplacedTheHeavyTermsAndNothingElse() {
        val oldUseCaseTexts = mapOf(
            TextKey.ASSET_LABEL_PROCEEDS to "حصيلة البيع",
            TextKey.ASSET_FEES_OVER_PROCEEDS to "الرسوم ({0}) أكبر من الحصيلة ({1})",
            TextKey.REVERT_KEPT_ALLOCATION to "العملية دي متربطة بشخص (تخصيص أو التزام)، فمش هتتحذف",
            TextKey.BACKUP_CHECKSUM_MISMATCH to "بصمة سلامة النسخة غير مطابقة؛ الملف اتغير أو اتلف",
        )
        for ((key, old) in oldUseCaseTexts) assertEquals(plainEgyptian(old), EGYPTIAN_TEXTS[key], key.name)
        for ((key, text) in EGYPTIAN_TEXTS) assertEquals(text, plainEgyptian(text), "فاضل مقطع قديم في المصري: $key")
        val heavy = listOf("حصيلة", "الحصيلة", "عدّ مزدوج", "الوسيط", "شذوذ", "تقلب", "تقلّب", "التخصيصات", "بصمة سلامة", "تسويات الالتزام", "الربح المحقق")
        for (text in EGYPTIAN_TEXTS.values + MSA_TEXTS.values) assertTrue(heavy.none { it in text }, "كلمة ثقيلة لسه: $text")
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
