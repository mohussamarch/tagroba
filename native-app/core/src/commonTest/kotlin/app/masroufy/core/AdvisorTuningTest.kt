package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ردود المالك على صفحة ضبط المساعد (2026-10-05) — OVERRIDES §68: الخبطة 50% · المعدل من اليوم التالت · الهدايا والصالون/الحلاق
 * اختيارية (والنقوط لأ) · habitVsGoal على الخطة اللي عليها نجمة ⭐. أرقام وأسامي مخترعة.
 */
class AdvisorTuningTest {
    private val period = Period("2026-09", "2026-09-28", "2026-10-27", 30)

    @Test fun lumpIsHalfTheBaseAndThePaceStartsOnDayThree() {
        assertEquals(50, LUMP_PERCENT_OF_BASE)
        assertEquals(3, PACE_MIN_ELAPSED_DAYS)
        // 45% من السقف ⇒ مش خبطة ⇒ داخلة في المعدل وفي عدّ المرات
        val r = readPace(listOf(45_000, 2_000, 2_000), 100_000, "2026-10-03", period)
        assertEquals(0, r.lumpCount)
        assertEquals(3, r.regularCount)
        assertEquals(1, readPace(listOf(50_000, 2_000, 2_000), 100_000, "2026-10-03", period).lumpCount)
        // اليوم التاني ⇒ بدري · اليوم التالت ⇒ 6,000 في 3 أيام = 60,000 للشهر
        assertNull(readPace(List(3) { 2_000L }, null, "2026-09-29", period).projectedMinor)
        assertEquals(60_000L, readPace(List(3) { 2_000L }, null, "2026-09-30", period).projectedMinor)
        // توقّع شاشة الميزانية ثابت تاني بمعنى تاني — ما اتغيرش
        assertEquals(5, MIN_DAYS_FOR_FORECAST)
    }

    private val personal = "cat-العنايه-الشخصيه"
    private val parents = mapOf(
        GiftCategories.ROOT to null,
        GiftCategories.EVENT_GIFTS to GiftCategories.ROOT,
        "sub-gift-mine" to GiftCategories.ROOT,
        "sub-nuqoot-mine" to GiftCategories.EVENT_GIFTS,
        personal to null,
        "$personal--حلاقه" to personal,
        "$personal--تجميل" to personal,
        "$personal--منتجات-عنايه" to personal,
        "$personal--ملابس" to personal,
    )

    @Test fun giftsAndSalonAreDiscretionaryButNuqootIsNot() {
        assertTrue(isDiscretionary(GiftCategories.ROOT, parents), "الهدايا العامة")
        assertTrue(isDiscretionary("sub-gift-mine", parents), "فرع المستخدم تحت الهدايا")
        assertFalse(isDiscretionary(GiftCategories.EVENT_GIFTS, parents), "النقوط ارتباط متبادل (§44 · §64)")
        assertFalse(isDiscretionary("sub-nuqoot-mine", parents), "فرع تحت النقوط")
        assertTrue(isDiscretionary("$personal--حلاقه", parents), "الحلاق")
        assertTrue(isDiscretionary("$personal--تجميل", parents), "الصالون")
        assertFalse(isDiscretionary("$personal--منتجات-عنايه", parents))
        assertFalse(isDiscretionary("$personal--ملابس", parents))
        assertFalse(isDiscretionary(personal, parents), "العناية الشخصية كلها مش اختيارية — فروع معينة بس")
        assertEquals(setOf(GiftCategories.EVENT_GIFTS), NOT_DISCRETIONARY_CATEGORY_IDS)
    }

    private val today = "2026-10-02"
    private fun goal(id: String, target: String, starred: Boolean = false, archived: Boolean = false, currency: Currency = Currency.SAR) =
        SavingsGoal(id, "خطة وهمية $id", 1_200_000, currency, "2026-01-01", target, archived = archived, createdAt = "c", updatedAt = "c", starred = starred)

    private fun progress(vararg goals: SavingsGoal) = goals.map { goalProgress(it, 920_000, 0, today) }

    @Test fun habitMeasuresAgainstTheStarredGoalEvenWhenAnotherIsNearer() {
        val near = goal("g-near", "2026-12-31")
        val far = goal("g-far", "2027-06-30", starred = true)
        assertEquals("g-far", goalForHabit(progress(near, far), Currency.SAR)?.goal?.id)
        assertEquals("g-far", goalForHabit(progress(far, near), Currency.SAR)?.goal?.id, "الترتيب ما يفرقش")
    }

    @Test fun withoutAStarTheNearestGoalIsTheFallback() {
        val near = goal("g-near", "2026-12-31")
        val far = goal("g-far", "2027-06-30")
        assertEquals("g-near", goalForHabit(progress(far, near), Currency.SAR)?.goal?.id)
        assertNull(goalForHabit(emptyList(), Currency.SAR))
    }

    @Test fun anIgnoredStarFallsBackToTheNearestRunningGoal() {
        val near = goal("g-near", "2026-12-31")
        assertEquals("g-near", goalForHabit(progress(near, goal("g-far", "2027-06-30", starred = true, archived = true)), Currency.SAR)?.goal?.id, "النجمة على مؤرشفة")
        assertEquals("g-near", goalForHabit(progress(near, goal("g-far", "2027-06-30", starred = true, currency = Currency.EGP)), Currency.SAR)?.goal?.id, "النجمة على خطة بالجنيه")
        val reached = goalProgress(goal("g-done", "2027-06-30", starred = true), 1_200_000, 0, today)
        assertEquals(GoalState.REACHED, reached.state)
        assertEquals("g-near", goalForHabit(progress(near) + reached, Currency.SAR)?.goal?.id, "النجمة على خطة اكتملت")
        // نسخة قديمة فيها نجمتين ⇒ الأقرب منهم (مش أول واحدة في القايمة)
        val twoStars = progress(goal("g-b", "2027-06-30", starred = true), goal("g-a", "2027-03-31", starred = true), near)
        assertEquals("g-a", assertNotNull(goalForHabit(twoStars, Currency.SAR)).goal.id)
    }
}
