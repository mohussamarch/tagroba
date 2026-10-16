package app.masroufy.ui.screens.operations

import app.masroufy.core.Id

/**
 * «أكثر ما صرفت عليه» (OVERRIDES §100): الفترتين اللي الشاشة بتبدّل بينهم = نفس فترات التصفية (الشهر ده · اللي فات)، و[FilterPeriod.skip]
 * هو نفسه عدد الشهور لورا اللي حالة الاستخدام بتقراه (`LoadTopCategories.loadAll`).
 */
val TOP_SPEND_PERIODS: List<FilterPeriod> = listOf(FilterPeriod.THIS, FilterPeriod.LAST)

/** سطر ⇒ عمليات التصنيف ده (و«بلا تصنيف» = [NO_CATEGORY]) في نفس الفترة. */
fun categoryOperationsRoute(categoryId: Id?, period: FilterPeriod): CategoryOperationsRoute = CategoryOperationsRoute(categoryId ?: NO_CATEGORY, period)

/**
 * التصفية الجاهزة اللي شاشة التصفية بتفتح بيها: التصنيف والفترة، ونوع «مصروف» عشان «بلا تصنيف» ما يجيبش الراتب والتحويلات (السطر صرف
 * بس). كل واحدة منهم شريحة بتتشال عادي (اختيار Claude — سؤال مفتوح للمالك في HANDOVER §7).
 */
fun CategoryOperationsRoute.filters(): Filters = Filters(period = period, kinds = setOf(KindGroup.OUT), categories = setOf(categoryKey))
