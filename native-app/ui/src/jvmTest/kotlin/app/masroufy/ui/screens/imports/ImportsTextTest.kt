package app.masroufy.ui.screens.imports

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Language
import app.masroufy.core.Texts
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** العدّ في الجمل بقواعد العربي (المفرد · المثنى · 3–10 · 11+) بالنسختين والإنجليزي، والملف اللي بيتنقل بين الخطوات في الذاكرة. */
class ImportsTextTest {
    @AfterTest fun reset() = Fx.resetTexts()

    @Test fun countsFollowArabicGrammarWithWesternDigits() {
        assertEquals(listOf("عملية واحدة", "عمليتان", "3 عمليات", "10 عمليات", "11 عملية"), listOf(1, 2, 3, 10, 11).map(::opsCount))
        assertEquals("رسالتان", msgsCount(2))
        assertEquals("12 سطرًا", linesCount(12))
        assertEquals("4 صفحات", pagesCount(4))
    }

    @Test fun egyptianAndEnglishCounts() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals(listOf("عمليتين", "رسالتين", "5 رسايل", "42 سطر"), listOf(opsCount(2), msgsCount(2), msgsCount(5), linesCount(42)))
        Texts.language = Language.EN
        assertEquals(listOf("1 transaction", "2 transactions", "11 transactions"), listOf(opsCount(1), opsCount(2), opsCount(11)))
    }

    @Test fun anUnknownCategoryIsUncategorisedNotGuessed() {
        val cats = listOf(Fx.category("food", "مطاعم وقهوة"))
        assertEquals("مطاعم وقهوة", categoryName("food", cats))
        assertEquals("غير مصنّف", categoryName("deleted", cats))
        assertEquals("غير مصنّف", categoryName(null, cats))
    }

    @Test fun draftsLiveInMemoryAndOnlyTheLastFewAreKept() {
        val first = ImportDrafts.put(ImportDraft("أ.csv", csv = "date,name"))
        assertEquals("أ.csv", ImportDrafts[first]?.fileName)
        val later = (1..4).map { ImportDrafts.put(ImportDraft("$it.csv")) }
        assertNull(ImportDrafts[first], "أقدم ملف بيتشال — التطبيق اتقفل ⇒ «اختر الملف من جديد»")
        assertNotNull(ImportDrafts[later.last()])
    }
}
