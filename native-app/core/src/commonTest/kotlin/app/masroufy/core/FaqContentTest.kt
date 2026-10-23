package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * «الأسئلة الشائعة»: الأسئلة حسب البلد · البحث. العدد: مصر 38 + سؤال الاستثمار القديم = 39 · السعودية 40 + القديم = 41
 * (أرقام المالك 38 و40 في OVERRIDES هي الجديدة؛ الملف نفسه فيه القديم كمان تحت «الاستثمار»).
 */
class FaqContentTest {
    private val egypt = faqFor("EG")
    private val saudi = faqFor("SA")

    @Test
    fun countsPerCountry() {
        assertEquals(39, egypt.size)
        assertEquals(41, saudi.size)
        assertEquals(38, egypt.count { !it.question.startsWith("حساب «هيبقى معاك كام»") })
        assertEquals(40, saudi.count { !it.question.startsWith("على ماذا بُني حساب «كم سيصبح معك»") })
        assertEquals(egypt.size, faqFor("eg").size)
        assertEquals(saudi.size, faqFor(null).size)
    }

    @Test
    fun tabbyAndTamaraAreSaudiOnly() {
        assertTrue(saudi.any { it.question.contains("تابي") && it.section == FaqSection.BNPL })
        assertTrue(egypt.none { it.question.contains("تابي") || it.question.contains("تمارا") || it.lines.any { l -> l.contains("تابي") } })
        assertEquals(1, egypt.count { it.section == FaqSection.BNPL })
        assertEquals(3, saudi.count { it.section == FaqSection.BNPL })
    }

    @Test
    fun zakatKeepsTheCountrySource() {
        val eg = egypt.filter { it.section == FaqSection.ZAKAT }.joinToString(" ") { it.question + it.lines.joinToString(" ") }
        val sa = saudi.filter { it.section == FaqSection.ZAKAT }.joinToString(" ") { it.question + it.lines.joinToString(" ") }
        assertTrue(eg.contains("دار الإفتاء المصرية") && eg.contains("2279") && eg.contains("8848") && eg.contains("5025") && eg.contains("4399") && eg.contains("5890") && eg.contains("8767"))
        assertTrue(sa.contains("هيئة الزكاة والضريبة والجمارك") && sa.contains("595") && sa.contains("3.4") && sa.contains("3.2.1") && sa.contains("3.6"))
        assertTrue(!eg.contains("هيئة الزكاة") && !sa.contains("دار الإفتاء"))
    }

    @Test
    fun oldInvestmentQuestionStaysUnderInvestment() {
        val q = egypt.first { it.section == FaqSection.INVEST }
        assertTrue(q.question.contains("هيبقى معاك كام"))
        assertEquals(5, q.lines.size)
        assertTrue(saudi.first { it.section == FaqSection.INVEST }.question.contains("كم سيصبح معك"))
    }

    @Test
    fun sectionsFollowFixedOrderAndSaudiLabels() {
        assertEquals(FaqSection.entries, faqSections(egypt))
        assertEquals(FaqSection.entries, faqSections(saudi))
        assertEquals("الرصيد والنقد", FaqSection.BALANCE.label(egypt = false))
        assertEquals("الرصيد والكاش", FaqSection.BALANCE.label(egypt = true))
        assertEquals(egypt.map { it.section.ordinal }, egypt.map { it.section.ordinal }.sorted())
    }

    @Test
    fun searchLooksInQuestionAndAnswer() {
        assertEquals(egypt, faqSearch(egypt, "  "))
        val byQuestion = faqSearch(egypt, "الحول")
        assertTrue(byQuestion.any { it.question.contains("الحول") })
        val byAnswer = faqSearch(egypt, "2279")
        assertEquals(1, byAnswer.size)
        assertEquals(FaqSection.ZAKAT, byAnswer.single().section)
        assertEquals(emptyList(), faqSearch(egypt, "كلمة-مالهاش-وجود"))
    }

    @Test
    fun searchIgnoresHamzaTaMarbutaAndAlefMaksura() {
        val a = faqSearch(saudi, "الاسهم")
        val b = faqSearch(saudi, "الأسهم")
        assertTrue(a.isNotEmpty())
        assertEquals(a, b)
        assertEquals(faqSearch(saudi, "الزكاه"), faqSearch(saudi, "الزكاة"))
        assertEquals("ماذا يعني", faqNormalize(" ماذا يعني "))
        assertNotNull(faqSearch(egypt, "الرصيد").firstOrNull())
    }

    @Test
    fun noArabicIndicDigitsInContent() {
        val all = FAQ_ENTRIES.flatMap { listOfNotNull(it.eg, it.sa) }.joinToString(" ") { it.question + it.lines.joinToString(" ") }
        assertTrue(all.none { it in '٠'..'٩' || it in '۰'..'۹' || it == '٪' })
    }
}
