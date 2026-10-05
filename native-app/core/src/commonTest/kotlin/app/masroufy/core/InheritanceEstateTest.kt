package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.SON
import app.masroufy.core.HeirKind.WIFE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * التركة قبل الورثة: التجهيز ⇒ الديون ⇒ الوصية (السعودية م198 · مصر م4)، والوصية الواجبة في مصر (71/1946 م76–79).
 */
class InheritanceEstateTest {
    /**
     * **فتوى دار الإفتاء 8330** (https://www.dar-alifta.org/ar/fatwa/details/20658): زوجة · 3 أبناء · بنتين · بنت متوفاة قبله لها
     * 5 أبناء و3 بنات. الفتوى: التركة 59904 سهم ⇒ الواجبة 5824 (لكل حفيد 896 ولكل حفيدة 448) ⇒ الباقي 54080: الزوجة 6760 ·
     * كل ابن 11830 · كل بنت 5915. بتركة 59,904.00 ج.م السهم = 1 جنيه = 100 قرش.
     */
    @Test
    fun darAlIftaFatwa8330() {
        val r = computed(
            inheritanceCase(
                "EG", WIFE to 1, SON to 3, DAUGHTER to 2, estate = 5_990_400,
                predeceased = listOf(PredeceasedChild(isSon = false, sons = 5, daughters = 3)),
            ),
        )
        val w = r.wajiba.single()
        assertEquals(Frac.of(7, 72), w.share, "نصيب البنت لو كانت عايشة = 7/8 × 1/9")
        assertEquals(582_400L, r.wajibaMinor)
        assertEquals(List(5) { 89_600L }, w.sonsMinor)
        assertEquals(List(3) { 44_800L }, w.daughtersMinor)
        assertEquals(5_408_000L, r.heirsMinor)
        assertEquals(listOf(676_000L), r.amounts(WIFE))
        assertEquals(List(3) { 1_183_000L }, r.amounts(SON))
        assertEquals(List(2) { 591_500L }, r.amounts(DAUGHTER))
        assertShares(r, WIFE to "1/8", SON to "21/32", DAUGHTER to "7/32")
        assertTrue(r.hasNote(InheritanceNoteKind.WAJIBA, "76"))
        assertEquals(LawSource.EG_BEQUEST, r.notes.first { it.kind == InheritanceNoteKind.WAJIBA }.citation!!.source)
    }

    // ابن عايش + ابن مات قبله وله ابن وبنت: لو عايش ⇒ 1/2 > 1/3 ⇒ الواجبة الثلث (حفيد 2/9 · حفيدة 1/9) والابن الباقي 2/3
    @Test
    fun wajibaForAPredeceasedSonIsCappedAtAThird() {
        val r = computed(inheritanceCase("EG", SON to 1, estate = 900_000, predeceased = listOf(PredeceasedChild(true, 1, 1))))
        assertEquals(Frac.of(1, 3), r.wajiba.single().share)
        assertEquals(listOf(200_000L), r.wajiba.single().sonsMinor)
        assertEquals(listOf(100_000L), r.wajiba.single().daughtersMinor)
        assertEquals(listOf(600_000L), r.amounts(SON))
        assertTrue(r.hasNote(InheritanceNoteKind.WAJIBA_CAPPED))
    }

    // تحت الثلث: زوجة · 3 أبناء · ابن مات قبله وله بنت ⇒ لو عايش: الزوجة 1/8 والأبناء الأربعة 7/8 ⇒ 7/32 لكل ابن < 1/3
    // ⇒ الواجبة 7/32 من 32,000 = 7,000 للحفيدة · الباقي 25,000: الزوجة 1/8 = 3,125 · الأبناء 21,875 على 3
    @Test
    fun wajibaBelowTheThirdIsTheExactShareAsIfAlive() {
        val r = computed(inheritanceCase("EG", WIFE to 1, SON to 3, estate = 3_200_000, predeceased = listOf(PredeceasedChild(true, 0, 1))))
        assertEquals(Frac.of(7, 32), r.wajiba.single().share)
        assertEquals(listOf(700_000L), r.wajiba.single().daughtersMinor)
        assertEquals(listOf(312_500L), r.amounts(WIFE))
        assertEquals(listOf(729_167L, 729_167L, 729_166L), r.amounts(SON))
        assertTrue(!r.hasNote(InheritanceNoteKind.WAJIBA_CAPPED))
    }

    // بنت عايشة + بنت ماتت قبله ولها ابن: لو عايشة ⇒ بنتين 2/3 ثم رد ⇒ 1/2 لكل واحدة > 1/3 ⇒ الواجبة الثلث، والبنت الباقي 2/3
    @Test
    fun wajibaForAPredeceasedDaughterIsCappedAtAThird() {
        val r = computed(inheritanceCase("EG", DAUGHTER to 1, estate = 600_000, predeceased = listOf(PredeceasedChild(false, 1, 0))))
        assertEquals(Frac.of(1, 3), r.wajiba.single().share)
        assertEquals(200_000L, r.wajibaMinor)
        assertEquals(listOf(400_000L), r.amounts(DAUGHTER))
    }

    // ابنين ماتوا قبله (واحد له ابن وواحد له بنتين) مع ابن عايش: لو عايشين ⇒ 1/3 لكل واحد = 2/3 > 1/3 ⇒ الثلث بينهم نص ونص (1/6 لكل فرع)
    @Test
    fun twoPredeceasedSonsShareTheThird() {
        val r = computed(
            inheritanceCase("EG", SON to 1, estate = 1_200_000, predeceased = listOf(PredeceasedChild(true, 1, 0), PredeceasedChild(true, 0, 2))),
        )
        assertEquals(listOf(Frac.of(1, 6), Frac.of(1, 6)), r.wajiba.map { it.share })
        assertEquals(listOf(200_000L), r.wajiba[0].sonsMinor)
        assertEquals(listOf(100_000L, 100_000L), r.wajiba[1].daughtersMinor)
        assertEquals(listOf(800_000L), r.amounts(SON))
    }

    // اللي اداه لهم في حياته من غير مقابل بيتخصم (م76): 3,000 من 3,000 ⇒ الواجبة 0 · 1,000 ⇒ 2,000
    @Test
    fun lifetimeGiftReducesTheWajiba() {
        val full = computed(inheritanceCase("EG", SON to 1, estate = 900_000, predeceased = listOf(PredeceasedChild(true, 1, 0, givenInLifeMinor = 300_000))))
        assertEquals(0L, full.wajibaMinor)
        assertEquals(listOf(900_000L), full.amounts(SON))
        val part = computed(inheritanceCase("EG", SON to 1, estate = 900_000, predeceased = listOf(PredeceasedChild(true, 0, 2, givenInLifeMinor = 100_000))))
        assertEquals(200_000L, part.wajibaMinor)
        assertEquals(listOf(100_000L, 100_000L), part.wajiba.single().daughtersMinor)
        assertTrue(part.hasNote(InheritanceNoteKind.WAJIBA_GIFT))
    }

    // ابن مات قبله ومفيش ابن عايش ⇒ أولاده ممكن يورثوا بنفسهم ⇒ غير مدعوم (مش تخمين)
    @Test
    fun predeceasedSonWithoutALivingSonIsUnsupported() {
        val r = calculateInheritance(inheritanceCase("EG", DAUGHTER to 2, predeceased = listOf(PredeceasedChild(true, 1, 0))))
        assertEquals(UnsupportedReason.PREDECEASED_SON_GRANDCHILDREN_INHERIT, assertIs<InheritanceResult.Unsupported>(r).reason)
    }

    // السعودية: مفيش وصية واجبة ⇒ ملاحظة بس والقسمة زي ما هي
    @Test
    fun saudiHasNoWajiba() {
        val r = computed(inheritanceCase("SA", SON to 1, estate = 900_000, predeceased = listOf(PredeceasedChild(true, 1, 1))))
        assertEquals(0L, r.wajibaMinor)
        assertEquals(listOf(900_000L), r.amounts(SON))
        assertTrue(r.hasNote(InheritanceNoteKind.NO_WAJIBA))
    }

    // وصية 40,000 من 90,000 لغير وارث ⇒ الثلث 30,000 بس · بموافقة الورثة كلهم ⇒ 40,000 (السعودية م190 · مصر 71/1946 م37)
    @Test
    fun bequestOverAThirdIsCappedUnlessHeirsConsent() {
        for (country in listOf("SA", "EG")) {
            val capped = computed(inheritanceCase(country, SON to 2, estate = 9_000_000, bequest = Bequest(4_000_000)))
            assertEquals(3_000_000L, capped.bequestMinor)
            assertEquals(listOf(3_000_000L, 3_000_000L), capped.amounts(SON))
            assertTrue(capped.hasNote(InheritanceNoteKind.BEQUEST_CAPPED, if (country == "SA") "190" else "37"))
            val consented = computed(inheritanceCase(country, SON to 2, estate = 9_000_000, bequest = Bequest(4_000_000, heirsConsent = true)))
            assertEquals(4_000_000L, consented.bequestMinor)
            assertTrue(consented.hasNote(InheritanceNoteKind.BEQUEST_EXCESS_CONSENTED))
        }
    }

    // وصية لوارث: السعودية باطلة من غير موافقة باقي الورثة (م179) وبموافقتهم تنفذ · مصر جايزة في الثلث من غير موافقة (م37)
    @Test
    fun bequestToAnHeir() {
        val saNo = computed(inheritanceCase("SA", SON to 1, DAUGHTER to 1, estate = 9_000_000, bequest = Bequest(1_000_000, toHeir = true)))
        assertEquals(0L, saNo.bequestMinor)
        assertTrue(saNo.hasNote(InheritanceNoteKind.BEQUEST_HEIR_VOID, "179"))
        val saYes = computed(inheritanceCase("SA", SON to 1, DAUGHTER to 1, estate = 9_000_000, bequest = Bequest(1_000_000, toHeir = true, heirsConsent = true)))
        assertEquals(1_000_000L, saYes.bequestMinor)
        assertTrue(saYes.hasNote(InheritanceNoteKind.BEQUEST_HEIR_CONSENTED, "179"))
        val eg = computed(inheritanceCase("EG", SON to 1, DAUGHTER to 1, estate = 9_000_000, bequest = Bequest(1_000_000, toHeir = true)))
        assertEquals(1_000_000L, eg.bequestMinor)
        assertTrue(eg.hasNote(InheritanceNoteKind.BEQUEST_HEIR_VALID, "37"))
        val egOver = computed(inheritanceCase("EG", SON to 1, DAUGHTER to 1, estate = 9_000_000, bequest = Bequest(5_000_000, toHeir = true)))
        assertEquals(3_000_000L, egOver.bequestMinor)
    }

    // الواجبة الأول ثم الاختيارية من الباقي من الثلث (م78): واجبة 30,000 = الثلث كله ⇒ الوصية 0
    @Test
    fun wajibaComesBeforeTheOptionalBequest() {
        val r = computed(
            inheritanceCase("EG", SON to 1, estate = 9_000_000, bequest = Bequest(1_000_000), predeceased = listOf(PredeceasedChild(true, 1, 0))),
        )
        assertEquals(3_000_000L, r.wajibaMinor)
        assertEquals(0L, r.bequestMinor)
        assertEquals(6_000_000L, r.heirsMinor)
    }

    // الترتيب: تجهيز 5,000 ⇒ ديون 20,000 ⇒ وصية الثلث من 75,000 = 25,000 ⇒ الورثة 50,000
    @Test
    fun orderOfRights() {
        val r = computed(inheritanceCase("SA", WIFE to 1, SON to 1, estate = 10_000_000, funeral = 500_000, debts = 2_000_000, bequest = Bequest(9_000_000)))
        assertEquals(500_000L, r.funeralMinor)
        assertEquals(2_000_000L, r.debtsMinor)
        assertEquals(2_500_000L, r.bequestMinor)
        assertEquals(5_000_000L, r.heirsMinor)
        assertEquals(listOf(625_000L), r.amounts(WIFE))
        assertTrue(r.hasNote(InheritanceNoteKind.ORDER, "198"))
    }

    // الديون أكبر من التركة ⇒ ولا وصية ولا ورثة، والتجهيز الأول
    @Test
    fun debtsLargerThanTheEstate() {
        val r = computed(inheritanceCase("EG", SON to 1, estate = 1_000_000, funeral = 300_000, debts = 2_000_000, bequest = Bequest(100_000)))
        assertEquals(300_000L, r.funeralMinor)
        assertEquals(700_000L, r.debtsMinor)
        assertEquals(0L, r.heirsMinor)
        assertEquals(0L, r.bequestMinor)
        assertTrue(r.hasNote(InheritanceNoteKind.DEBTS_EXCEED, "4"))
    }

    // كل حاجة بتتقسم لوحدها بالهللة: شقة 1,000,000.01 · عربية 85,000.03 · ذهب 12,345.67 على زوجة و3 بنات وأخ
    @Test
    fun everyItemSplitsToTheHalala() {
        val items = listOf(EstateItem("شقة", 100_000_001), EstateItem("عربية", 8_500_003), EstateItem("ذهب", 1_234_567))
        val r = computed(inheritanceCase("SA", WIFE to 1, DAUGHTER to 3, FULL_BROTHER to 1, items = items, debts = 77_777, bequest = Bequest(1_111_111)))
        assertShares(r, WIFE to "1/8", DAUGHTER to "2/3", FULL_BROTHER to "5/24")
        assertEquals(3, r.items.size)
        assertEquals(109_734_571L, r.grossMinor)
    }
}
