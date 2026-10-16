package app.masroufy.usecase

import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Period
import app.masroufy.core.rateOfMoney
import app.masroufy.core.sharesOfLargest
import app.masroufy.core.sumMoney

/**
 * «أكثر ما صرفت عليه» (طلب المالك 2026-10-16 — OVERRIDES §98): ترتيب **الصرف الحقيقي** على التصنيفات في الشهر المالي. المصدر الوحيد
 * للترتيب هو [rankSpend] — كارت الرئيسية بياخد أول [TOP_CATEGORIES_COUNT] منه، وشاشة «أكثر ما صرفت عليه» في العمليات بتعرضه كله.
 * التعريف نفسه بتاع الرئيسية والميزانيات: `categoryDistribution` على عمليات الفترة زي ما بتتحسب (`LoadHomeScreen.distribution`) —
 * مفيش تعريف جديد للصرف هنا. كل المبالغ أعداد صحيحة بالهللة.
 */

/**
 * سطر في الترتيب. [categoryId] null = «بلا تصنيف» (سطر لوحده). [name]/[iconKey]/[colorHex] null = التصنيف مش موجود في الشجرة (اتمسح)
 * أو «بلا تصنيف». [shareTenthPercent] نسبته من مجموع الفترة بالعُشر من المية (125 = 12.5٪) و[sharePercent] نفس النسبة مقرّبة لأقرب
 * واحد صحيح للعرض. [barPercent] عرض الشريط من أكبر تصنيف (0–100).
 */
data class SpendRankRow(
    val categoryId: Id?,
    val name: String?,
    val iconKey: String?,
    val colorHex: String?,
    val amountMinor: Halalas,
    val count: Int,
    val shareTenthPercent: Long,
    val sharePercent: Int,
    val barPercent: Int,
)

/**
 * الترتيب كامل لفترة. [totalMinor] = مجموع السطور (المقام بتاع النسب) — null لو الرئيسية نفسها مش عارفة المصروف (كل العمليات نوعها مش
 * محدد ⇒ «غير متاح»، CLAUDE.md #10). [partial] = فيه عمليات لسه نوعها ما اتحددش ⇒ الأرقام «لحد دلوقتي».
 */
data class TopSpendView(
    val period: Period,
    val periodRange: String,
    val rows: List<SpendRankRow>,
    val totalMinor: Halalas?,
    val partial: Boolean,
)

/** الترتيب من بيانات الرئيسية اللي اتقرت: الأكبر الأول، والتعادل بمعرّف التصنيف (و«بلا تصنيف» آخرهم) عشان الترتيب ثابت. */
fun rankSpend(data: HomeScreenData): TopSpendView {
    val byId = data.categories.associateBy { it.id }
    val slices = data.distribution.filter { it.amountMinor > 0L }
        .sortedWith(compareByDescending<app.masroufy.core.CategorySlice> { it.amountMinor }.thenBy(nullsLast()) { it.categoryId })
    val total = sumMoney(slices.map { it.amountMinor })
    val bars = sharesOfLargest(slices.map { it.amountMinor })
    val rows = slices.mapIndexed { i, s ->
        val c = s.categoryId?.let(byId::get)
        val tenth = if (total == 0L) 0L else rateOfMoney(s.amountMinor, 1000, total)
        SpendRankRow(s.categoryId, c?.name, c?.iconKey, c?.lightColor, s.amountMinor, s.count, tenth, ((tenth + 5) / 10).toInt(), bars[i])
    }
    val known = data.expenseMinor != null
    return TopSpendView(data.period, data.periodRange, rows, if (known) total else null, data.partial)
}
