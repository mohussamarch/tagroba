package app.masroufy.core

/**
 * r06-darahim (PLAN-0.6 §12-2): **الوجبة علامة، مش تصنيف** (اختيار المالك). بتتقترح للمطعم من ساعة العملية المحلية:
 * قبل [LUNCH_FROM_HOUR] = فطور · لحد قبل [DINNER_FROM_HOUR] = غدا · بعدها = عشا (❓ مقترحة — OVERRIDES §109).
 * **من غير ساعة = من غير وجبة** (القاعدة 10): العملية اللي دقتها «يوم» ما بتتخمّنش من نص الليل.
 */
const val LUNCH_FROM_HOUR = 11
const val DINNER_FROM_HOUR = 17

/** «مطاعم وقهوة › مطاعم» بمعرّف البذرة (ثابت لو الاسم المعروض اتغير). */
val RESTAURANT_SUB_ID: Id = seedCategoryId("مطاعم وقهوة", "مطاعم")

private val CLOCK = Regex("^(\\d{1,2}):(\\d{2})")

/** ساعة العملية المحلية (0..23) — بس لو المصدر إداها بالدقيقة ([Transaction.datePrecision] = "minute" ومعاها [Transaction.sourceTime]). */
fun localHourOf(t: Transaction): Int? {
    if (t.datePrecision != "minute") return null
    val m = CLOCK.find(t.sourceTime?.trim().orEmpty()) ?: return null
    return m.groupValues[1].toInt().takeIf { it in 0..23 && m.groupValues[2].toInt() in 0..59 }
}

fun mealForHour(hour: Int): Meal = when {
    hour < LUNCH_FROM_HOUR -> Meal.BREAKFAST
    hour < DINNER_FROM_HOUR -> Meal.LUNCH
    else -> Meal.DINNER
}

/** العملية على «مطاعم» (أو فرعي تحتها لو المالك عمل). */
fun isRestaurantCategory(categoryId: Id?, categories: List<Category>): Boolean {
    if (categoryId == null) return false
    if (categoryId == RESTAURANT_SUB_ID) return true
    return categories.firstOrNull { it.id == categoryId }?.parentId == RESTAURANT_SUB_ID
}

/** الاقتراح بس (من غير اختيار المالك): مطعم + ساعة معروفة ⇒ وجبة، غير كده null. */
fun suggestedMealOf(t: Transaction, categories: List<Category>): Meal? =
    if (isRestaurantCategory(t.categoryId, categories)) localHourOf(t)?.let(::mealForHour) else null

/** اللي يتعرض: اختيار المالك الأول ([Meal.NONE] = شالها) وبعده الاقتراح. */
fun shownMealOf(t: Transaction, categories: List<Category>): Meal? = when (t.meal) {
    Meal.NONE -> null
    null -> suggestedMealOf(t, categories)
    else -> t.meal
}
