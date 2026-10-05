package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** قواعد «المساعد المالي» الثلاثة (OVERRIDES §68) — حدود التنبيه وعدمه، أرقام مخترعة. */
class AdvisorRulesTest {
    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    // الشهر المالي من يوم الراتب 28: 28 سبتمبر ⇒ 27 أكتوبر (30 يوم). المعدل بيبدأ اليوم التالت = 30 سبتمبر (رد المالك)
    private val period = Period("2026-09", "2026-09-28", "2026-10-27", 30)
    private val day1 = "2026-09-28"
    private val day2 = "2026-09-29"
    private val day3 = "2026-09-30"
    private val day4 = "2026-10-01"
    private val day5 = "2026-10-02"
    private val day6 = "2026-10-03"

    private fun cap(amounts: List<Long>, cap: Long, today: String) =
        capPaceCandidate("ترفيه", "cat:x", cap, readPace(amounts, cap, today, period), today, period, Currency.SAR)

    @Test fun ownerExampleOneBigShopIsALumpNotAPace() {
        // 800 أول يوم في الشهر على سقف 1000 في عملية واحدة ⇒ مفيش تنبيه معدل (لا أول يوم ولا بعدين)
        assertNull(cap(listOf(80_000), 100_000, day1))
        assertNull(cap(listOf(80_000), 100_000, "2026-10-07"))
        val r = readPace(listOf(80_000), 100_000, "2026-10-07", period)
        assertEquals(1, r.lumpCount)
        assertEquals(80_000L, r.spentMinor, "الخبطة في «صرفت» بس")
        assertNull(r.projectedMinor)
    }

    @Test fun ownerExampleSameAmountSplitInFourFires() {
        val c = assertNotNull(cap(List(4) { 20_000L }, 100_000, day5))
        assertEquals(AlertKind.CAP_PACE, c.kind)
        assertEquals("cappace|2026-09-28|cat:x", c.threadKey)
        // 800 في 5 أيام ⇒ 4,800 للشهر؛ الباقي 200 ÷ 160 في اليوم ⇒ بعد يومين
        assertEquals(uiText(TextKey.ADVISOR_CAP_PACE_BODY, formatMoney(80_000), formatMoney(100_000), formatMoney(480_000), "2026-10-04"), c.body)
        assertNull(cap(List(4) { 20_000L }, 100_000, day2), "قبل اليوم التالت ما بنحكمش")
        assertNotNull(cap(List(4) { 20_000L }, 100_000, day3), "اليوم التالت ⇒ بنحكم (رد المالك — كانت الخامس)")
        assertNotNull(cap(List(4) { 20_000L }, 100_000, day4))
    }

    @Test fun paceNeedsThreeRepeatsAndCrossesStrictly() {
        assertNull(cap(listOf(2_000, 3_000), 10_000, day5), "مرتين بس")
        assertNotNull(cap(listOf(2_000, 2_000, 1_000), 29_999, day5), "3 مرات ⇒ 30,000 متوقع > 29,999")
        assertNull(cap(listOf(2_000, 2_000, 1_000), 30_000, day5), "المتوقع = السقف بالظبط ⇒ مش هيتعدّى")
        assertNull(cap(listOf(9_000, 9_000, 9_000), 26_000, day5), "السقف اتعدّى خلاص ⇒ ده تنبيه «عدّى السقف» مش المعدل")
    }

    @Test fun lumpCountsInSpentButNotInThePace() {
        // 500 خبطة + 3 × 20 ⇒ المتوقع 500 + 60 × 6 = 860 < 1,000 ⇒ ساكت (لو الخبطة اتمدت كانت 3,360)
        val r = readPace(listOf(50_000, 2_000, 2_000, 2_000), 100_000, day5, period)
        assertEquals(86_000L, r.projectedMinor)
        assertEquals(56_000L, r.spentMinor)
        assertNull(cap(listOf(50_000, 2_000, 2_000, 2_000), 100_000, day5))
        assertTrue(isLump(50_000, 100_000), "النص بالظبط ⇒ خبطة (رد المالك)")
        assertFalse(isLump(49_999, 100_000))
        assertFalse(isLump(40_000, 100_000), "الـ40% القديمة مابقتش خبطة")
        assertFalse(isLump(90_000, null), "مفيش أساس ⇒ مفيش حكم")
    }

    private val goal = SavingsGoal("g-1", "خطة وهمية", 1_200_000, Currency.SAR, "2026-01-01", "2026-12-31", createdAt = "c", updatedAt = "c")

    // يوم 2 أكتوبر: معاك 920,000 ⇒ ماشي على الخطة، وبمعدلك هتوصل 1,222,190 (فوق الهدف بـ22,190)
    private val onTrack = goalProgress(goal, 920_000, 0, day5)

    private fun habit(amounts: List<Long>, usual: Long?, progress: GoalProgress? = onTrack, today: String = day6) =
        habitVsGoalCandidate(HabitCheck("cat-coffee", "القهوة", readPace(amounts, usual, today, period), usual, progress, period, Currency.SAR))

    @Test fun habitBoundaries() {
        assertEquals(1_222_190L, onTrack.projectedAtTargetMinor)
        assertEquals(3, onTrack.monthsLeft)
        // معتاد 400؛ يوم 6 والمتوقع = المجموع × 5. 130% = 520
        val atLine = assertNotNull(habit(listOf(3_400, 3_500, 3_500), 40_000), "520 بالظبط ⇒ أعلى بوضوح")
        assertEquals(AlertKind.HABIT_VS_GOAL, atLine.kind)
        assertNull(habit(listOf(3_400, 3_500, 3_499), 40_000), "519.95 ⇒ مش أعلى بوضوح")
        assertNull(habit(listOf(15_000, 15_000), 40_000), "مرتين بس — حتى لو أعلى بكتير")
        assertNotNull(habit(listOf(15_000, 15_000, 15_000), 40_000), "3 مرات أعلى من المعتاد")
    }

    @Test fun habitStaysQuietWhenTheGoalStillMakesItOrDataIsUnknown() {
        // معاك 1,000,000 ⇒ هتوصل 1,328,467 (فايض 128,467). زيادة 140 في الشهر × 3 = 420 ⇒ الخطة لسه هتكمل
        val rich = goalProgress(goal, 1_000_000, 0, day5)
        assertNull(habit(listOf(3_600, 3_600, 3_600), 40_000, progress = rich), "الخطة لسه هتكمل")
        assertNotNull(habit(listOf(3_600, 3_600, 3_600), 40_000), "نفس الزيادة على خطة فايضها 22,190 بس ⇒ مش هتكمل")
        assertNull(habit(listOf(15_000, 15_000, 15_000), null), "المعتاد مش معروف")
        assertNull(habit(listOf(15_000, 15_000, 15_000), 0), "المعتاد صفر ⇒ مفيش «أعلى من المعتاد»")
        assertNull(habit(listOf(15_000, 15_000, 15_000), 40_000, progress = null), "مفيش خطة")
        assertNull(habit(listOf(15_000, 15_000, 15_000), 40_000, progress = goalProgress(goal, null, 0, day5)), "رصيد الخطة مش معروف")
        assertNull(habit(listOf(15_000, 15_000, 15_000), 40_000, progress = goalProgress(goal, 10_000, 0, "2026-01-20")), "الخطة جديدة — معدلها مش معروف")
        assertNull(habit(listOf(15_000, 15_000, 15_000), 40_000, today = day2), "قبل اليوم التالت")
        assertNotNull(habit(listOf(15_000, 15_000, 15_000), 40_000, today = day3), "اليوم التالت")
        assertNull(habit(listOf(20_000, 20_000, 20_000), 40_000), "كل واحدة خبطة (≥ 50% من المعتاد) ⇒ مفيش معدل")
        assertNotNull(habit(listOf(18_000, 18_000, 18_000), 40_000), "45% من المعتاد مابقتش خبطة ⇒ معدل")
    }

    @Test fun habitTextFollowsTheOwnerShapeAndCountsTimesNotItems() {
        val c = assertNotNull(habit(listOf(15_000, 15_000, 15_000), 40_000))
        // 450 في 6 أيام ⇒ 2,250 للشهر؛ زيادة 1,850 × 3 شهور ⇒ 12,221.90 − 5,550 = 6,671.90
        val shape = uiText(TextKey.ADVISOR_HABIT_BODY_YEAR_END, "القهوة", formatMoney(45_000), formatMoney(667_190), formatMoney(1_200_000))
        assertEquals("القهوة هذا الشهر 450.00 ر.س — بهذا المعدل ستحوش 6,671.90 ر.س بدل 12,000.00 ر.س آخر السنة", shape)
        // متوسط المرة 150 ⇒ 13 مرة في الشهر ⇒ 4 في الأسبوع (ومش أكتر من مراتك دلوقتي: 3 × 7 ÷ 6 ⇒ 4)
        assertEquals("$shape · ${uiText(TextKey.ADVISOR_HABIT_SUGGEST, "4")}", c.body)
        assertTrue(c.body.contains("قلّل 4 مرات في الأسبوع"))
        assertEquals("habit|2026-09-28|cat-coffee", c.threadKey)
        val dated = assertNotNull(habit(listOf(15_000, 15_000, 15_000), 40_000, progress = goalProgress(goal.copy(targetDate = "2026-12-20"), 920_000, 0, day5)))
        assertTrue(dated.body.contains("بحلول 2026-12-20"), dated.body)
    }

    @Test fun discretionaryIsAWrittenListWithChildren() {
        val parents = mapOf("cat-مطاعم-وقهوه--قهوه-ومشروبات" to "cat-مطاعم-وقهوه", "cat-مطاعم-وقهوه" to null, "sub-mine" to "cat-ترفيه--سينما", "cat-ترفيه--سينما" to "cat-ترفيه")
        assertTrue(isDiscretionary("cat-مطاعم-وقهوه--قهوه-ومشروبات", parents))
        assertTrue(isDiscretionary("sub-mine", parents), "فرع المستخدم تحت الترفيه")
        assertFalse(isDiscretionary("cat-بقاله-وسوبرماركت--سوبرماركت", mapOf("cat-بقاله-وسوبرماركت--سوبرماركت" to "cat-بقاله-وسوبرماركت")))
        assertFalse(isDiscretionary(null, parents))
        assertEquals(40_000L, usualMonthMinor(listOf(50_000, 30_000, 40_000)))
        assertEquals(0L, usualMonthMinor(listOf(0, 0, 90_000)), "الصفر المحسوب معروف")
        assertNull(usualMonthMinor(listOf(50_000, null, 40_000)))
        assertNull(usualMonthMinor(listOf(50_000, 40_000)))
    }

    private fun projection(onHand: Long?, counted: Long, mode: LeftoverMode = LeftoverMode.UNTIL_MONTH_END, approx: Boolean = false) =
        LeftoverProjection(mode, onHand, counted, 3, onHand?.let { it - counted }, approx, null, 0)

    @Test fun overcommitFiresOnceThenOnlyWhenTheGapGrows() {
        val c = assertNotNull(overcommitCandidate(projection(410_000, 520_000), Currency.SAR, "2026-09-28", emptyList()))
        assertEquals("حجزت 5,200.00 ر.س لهذا الشهر ومعك 4,100.00 ر.س. ينقصك 1,100.00 ر.س", c.body)
        assertEquals("overcommit|2026-09-28|110000", c.threadKey)
        assertEquals(110_000L, c.amountMinor)
        assertNull(overcommitCandidate(projection(410_000, 410_000), Currency.SAR, "2026-09-28", emptyList()), "قد اللي معاك بالظبط")
        assertNotNull(overcommitCandidate(projection(410_000, 410_001), Currency.SAR, "2026-09-28", emptyList()))
        assertNull(overcommitCandidate(projection(null, 520_000), Currency.SAR, "2026-09-28", emptyList()), "رصيد مش معروف ⇒ ساكت")
        val sent = listOf(110_000L)
        assertEquals("overcommit|2026-09-28|110000", overcommitCandidate(projection(272_501, 410_000), Currency.SAR, "2026-09-28", sent)?.threadKey, "137,499 ⇒ نفس الموضوع")
        assertEquals("overcommit|2026-09-28|137500", overcommitCandidate(projection(272_500, 410_000), Currency.SAR, "2026-09-28", sent)?.threadKey, "137,500 = +25%")
        assertEquals("overcommit|2026-09-28", overcommitCandidate(projection(0, 1), Currency.SAR, "2026-09-28", null)?.threadKey, "من غير إيصالات ⇒ مرة للفترة")
    }

    @Test fun overcommitWordingFollowsTheLeftoverMode() {
        val free = assertNotNull(overcommitCandidate(projection(410_000, 520_000, LeftoverMode.FROM_WHAT_YOU_HAVE, approx = true), Currency.SAR, "a", null))
        assertFalse(free.body.contains("الشهر"), "من غير مرتب ⇒ من غير «لهذا الشهر»")
        assertTrue(free.body.endsWith(uiText(TextKey.ADVISOR_APPROXIMATE)))
        assertTrue(overcommitCandidate(projection(1, 2, LeftoverMode.UNTIL_NEXT_PAY), Currency.SAR, "a", null)!!.body.contains("القبض القادم"))
        val keys = listOf("overcommit|2026-09-28|110000|overcommitted", "eg:overcommit|2026-09-28|90000|overcommitted", "overcommit|2026-08-28|5|overcommitted", "overcommit|2026-09-28|x|overcommitted")
        assertEquals(listOf(110_000L), overcommitSentGaps(keys, "", "2026-09-28"))
        assertEquals(listOf(90_000L), overcommitSentGaps(keys, "eg:", "2026-09-28"))
    }

    @Test fun deliveryClassesForTheThreeKinds() {
        val noon = LocalMoment("2026-10-05", 12)
        fun c(kind: AlertKind) = AlertCandidate(kind, "t", "x", "y")
        assertEquals(AlertDelivery.DIGEST, decideAlert(c(AlertKind.HABIT_VS_GOAL), KindStats(), UsualHours(), noon).delivery)
        assertEquals(AlertDelivery.DIGEST, decideAlert(c(AlertKind.CAP_PACE), KindStats(), UsualHours(), noon).delivery)
        assertEquals(AlertDelivery.SEND_NOW, decideAlert(c(AlertKind.OVERCOMMITTED), KindStats(), UsualHours(), noon).delivery, "لسه ما اتعلمتش ساعاتك ⇒ دلوقتي")
        // ساعاتك اتعلمت والساعة دي مش معتادة ⇒ الحجز يستنى وقتك، والعادة والمعدل يفضلوا ملخص
        val learned = UsualHours(List(24) { if (it == 21) 6 else 0 })
        assertEquals(AlertDelivery.AT_USUAL_TIME, decideAlert(c(AlertKind.OVERCOMMITTED), KindStats(), learned, noon).delivery)
        assertEquals(AlertDelivery.DIGEST, decideAlert(c(AlertKind.HABIT_VS_GOAL), KindStats(), learned, noon).delivery)
        assertTrue(AlertKind.OVERCOMMITTED.needsDecision)
        assertEquals(AlertGroup.ADVISOR, AlertKind.CAP_PACE.group)
    }

    @Test fun advisorTextsNameVisitsNotItems() {
        val banned = listOf("أكواب", "كوب", "كاسات", "وجبات", "علب")
        val english = Regex("""\bcups?\b""", RegexOption.IGNORE_CASE)
        for ((name, table) in listOf(
            "فصحى" to MSA_ADVISOR_TEXTS + MSA_ADVISOR_MORE_TEXTS, "مصري" to EGYPTIAN_ADVISOR_TEXTS + EGYPTIAN_ADVISOR_MORE_TEXTS,
            "إنجليزي" to ENGLISH_ADVISOR_TEXTS + ENGLISH_ADVISOR_MORE_TEXTS,
        )) {
            for ((key, text) in table) {
                assertTrue(banned.none { text.contains(it) } && !english.containsMatchIn(text), "[$name] $key فيه وحدة صنف: $text")
            }
        }
        assertTrue(MSA_ADVISOR_TEXTS.getValue(TextKey.ADVISOR_HABIT_SUGGEST).contains("مرات"))
        assertTrue(EGYPTIAN_ADVISOR_TEXTS.getValue(TextKey.ADVISOR_HABIT_SUGGEST).contains("مرات"))
        assertTrue(ENGLISH_ADVISOR_TEXTS.getValue(TextKey.ADVISOR_HABIT_SUGGEST).contains("times"))
        // والفحص نفسه شغال
        assertTrue(english.containsMatchIn("2 fewer cups a day") && banned.any { "قلّل 4 أكواب".contains(it) })
    }
}
