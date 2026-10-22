package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** r06-darahim (§12-2): الوجبة من ساعة العملية على «مطاعم» بس — ومن غير ساعة ⇒ من غير وجبة (القاعدة 10). */
class MealsTest {
    private val cats = listOf(
        Category(RESTAURANT_SUB_ID, seedCategoryId("مطاعم وقهوة"), "مطاعم", "soup", "#000", "#fff", true, 1),
        Category("cat-own-shawarma", RESTAURANT_SUB_ID, "شاورما", "soup", "#000", "#fff", true, 2),
        Category(seedCategoryId("مطاعم وقهوة", "قهوة ومشروبات"), seedCategoryId("مطاعم وقهوة"), "قهوة ومشروبات", "coffee", "#000", "#fff", true, 3),
    )

    private fun tx(time: String?, cat: Id? = RESTAURANT_SUB_ID, precision: String = "minute", meal: Meal? = null) = Transaction(
        id = "t", occurredAt = "2026-10-07", datePrecision = precision, sourceTime = time, sourceOrder = 0, economicKind = EconomicKind.PURCHASE,
        economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = 3_000, currency = Currency.SAR, categoryConfirmed = true,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = cat, meal = meal,
    )

    @Test fun thresholdsSplitTheDay() {
        assertEquals(Meal.BREAKFAST, mealForHour(0))
        assertEquals(Meal.BREAKFAST, mealForHour(10))
        assertEquals(Meal.LUNCH, mealForHour(11))
        assertEquals(Meal.LUNCH, mealForHour(16))
        assertEquals(Meal.DINNER, mealForHour(17))
        assertEquals(Meal.DINNER, mealForHour(23))
    }

    @Test fun suggestedOnlyForRestaurantsWithAKnownTime() {
        assertEquals(Meal.BREAKFAST, suggestedMealOf(tx("08:15"), cats))
        assertEquals(Meal.LUNCH, suggestedMealOf(tx("13:05:44"), cats))
        assertEquals(Meal.DINNER, suggestedMealOf(tx("20:32"), cats))
        assertEquals(Meal.DINNER, suggestedMealOf(tx("21:00", cat = "cat-own-shawarma"), cats), "فرعي تحت مطاعم")
        assertNull(suggestedMealOf(tx("13:00", cat = cats[2].id), cats), "القهوة مش وجبة")
        assertNull(suggestedMealOf(tx(null, precision = "day"), cats), "من غير ساعة ⇒ ولا حاجة")
        assertNull(suggestedMealOf(tx("00:00", precision = "day"), cats), "نص الليل من يوم بس ما بيتخمّنش")
        assertNull(suggestedMealOf(tx("25:10"), cats))
    }

    @Test fun ownerChoiceWinsAndNoneClears() {
        assertEquals(Meal.DINNER, shownMealOf(tx("08:00", meal = Meal.DINNER), cats))
        assertNull(shownMealOf(tx("13:00", meal = Meal.NONE), cats))
        assertEquals(Meal.LUNCH, shownMealOf(tx("13:00"), cats))
    }
}
