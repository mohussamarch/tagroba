package app.masroufy.ui.screens.operations

import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.ui.text.t
import app.masroufy.usecase.SpendRankRow
import app.masroufy.usecase.TopSpendView

/**
 * «أكثر ما صرفت عليه» (OVERRIDES §100 + تصميم `TopSpending` في 0.5.1): الفترات اللي الشاشة بتبدّل بينهم = نفس فترات التصفية (الشهر ده ·
 * اللي فات · آخر 3 شهور)، و[FilterPeriod.skip]/[FilterPeriod.months] هما نفسهم اللي حالة الاستخدام بتقراهم (`LoadTopCategories.loadAll`)
 * ⇒ الدوسة على سطر بتفتح التصفية على نفس الأيام بالظبط.
 */
val TOP_SPEND_PERIODS: List<FilterPeriod> = listOf(FilterPeriod.THIS, FilterPeriod.LAST, FilterPeriod.THREE)

/** سطر ⇒ عمليات التصنيف ده (و«بلا تصنيف» = [NO_CATEGORY]) في نفس الفترة. */
fun categoryOperationsRoute(categoryId: Id?, period: FilterPeriod): CategoryOperationsRoute = CategoryOperationsRoute(categoryId ?: NO_CATEGORY, period)

/**
 * التصفية الجاهزة اللي شاشة التصفية بتفتح بيها: التصنيف والفترة، ونوع «مصروف» عشان «بلا تصنيف» ما يجيبش الراتب والتحويلات (السطر صرف
 * بس). كل واحدة منهم شريحة بتتشال عادي (اختيار Claude — سؤال مفتوح للمالك في HANDOVER §7).
 */
fun CategoryOperationsRoute.filters(): Filters = Filters(period = period, kinds = setOf(KindGroup.OUT), categories = setOf(categoryKey))

/** «28 سبتمبر – 27 أكتوبر»، و«(الشهر المالي ده)» وراه لو الفترة هي الشهر المالي الحالي لوحده. */
fun topSpendRange(v: TopSpendView): String =
    t(if (v.current) UiKey.TOP_SPEND_RANGE_NOW else UiKey.TOP_SPEND_RANGE, dayMonth(v.period.start), dayMonth(v.period.end))

/** «على 10 تصنيفات · 87 عملية» — عدد السطور (و«بلا تصنيف» واحد منهم) وعدد العمليات فيهم. */
fun topSpendSummary(v: TopSpendView): String = t(UiKey.TOP_SPEND_SUMMARY, v.rows.size.toString(), v.operationCount.toString())

/**
 * «↑ 14% عن الشهر اللي فات» (أحمر — زاد) أو «↓ 6% …» (أخضر — قلّ). null = مفيش سهم: الفرق مش معروف، أو صفر (التصميم ما بيعرضش
 * «0%»). النسبة جوه عزل اتجاه عشان «14%» ما تتقلبش.
 */
fun topSpendChange(r: SpendRankRow): Pair<String, Boolean>? {
    val c = r.changePercent ?: return null
    if (c == 0L) return null
    val pct = "⁦${if (c > 0) c else -c}%⁩"
    return t(if (c > 0) UiKey.TOP_SPEND_UP else UiKey.TOP_SPEND_DOWN, pct) to (c > 0)
}
