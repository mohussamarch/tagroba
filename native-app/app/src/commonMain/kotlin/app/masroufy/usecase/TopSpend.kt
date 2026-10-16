package app.masroufy.usecase

import app.masroufy.core.CategorySlice
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Period
import app.masroufy.core.rateOfMoney
import app.masroufy.core.sharesOfLargest
import app.masroufy.core.subtractMoney
import app.masroufy.core.sumMoney

/**
 * «أكثر ما صرفت عليه» (طلب المالك 2026-10-16 — OVERRIDES §100، وتصميم `TopSpending` في 0.5.1): ترتيب **الصرف الحقيقي** على التصنيفات.
 * المصدر الوحيد للترتيب هو [rankSpendOver] — كارت الرئيسية بياخد أول [TOP_CATEGORIES_COUNT] منه، والشاشة بتعرضه كله.
 * التعريف نفسه بتاع الرئيسية والميزانيات: `categoryDistribution` على عمليات الفترة زي ما بتتحسب (`LoadHomeScreen.distribution`) —
 * مفيش تعريف جديد للصرف هنا. كل المبالغ أعداد صحيحة بالهللة، والنسب أعداد صحيحة (`rateOfMoney`).
 */

/** عدد الحصص الملوّنة في شريط المجموع (التصميم: أول 5 بألوانهم والباقي رمادي). */
const val TOP_SPEND_SHARE_SEGMENTS = 5

/**
 * سطر في الترتيب. [categoryId] null = «بلا تصنيف» (سطر لوحده). [name]/[iconKey]/[colorHex] null = التصنيف مش موجود في الشجرة (اتمسح)
 * أو «بلا تصنيف». [shareTenthPercent] نسبته من مجموع الفترة بالعُشر من المية (125 = 12.5٪) و[sharePercent] نفس النسبة مقرّبة لأقرب
 * واحد صحيح للعرض. [barPercent] عرض الشريط من أكبر تصنيف (0–100). [changePercent] الفرق عن الشهر اللي فات بالمية (موجب = زاد):
 * null = مش معروف أو مش متقارن (مش «الشهر ده»، أو **الشهر ده نفسه** أو اللي فات مش معروف/جزئي، أو التصنيف ما كانش فيه صرف) ⇒ مفيش
 * سهم — عمره ما بيبقى صفر مزيف ولا سهم من مبلغ «لحد دلوقتي» (CLAUDE.md #10). نوعه [Long] مش [Int]: من 0.01 لـ250,000 = 2,499,999,900٪
 * والـInt كان بيلف ويقلب الإشارة (سهم أخضر لزيادة كبيرة — مراجعة r051 F3).
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
    val changePercent: Long? = null,
)

/** حصة في شريط المجموع: [colorHex] لون التصنيف (null = من غير لون)، و[tenthPercent] نصيبها بالعُشر من المية. */
data class ShareSegment(val categoryId: Id?, val colorHex: String?, val tenthPercent: Long)

/**
 * الترتيب كامل لفترة (شهر مالي أو أكتر). [period] = الفترة كلها: من أول يوم في أقدم شهر لآخر يوم في أحدثهم، و[months] عدد الشهور،
 * و[current] = الفترة هي الشهر المالي الحالي لوحده. [totalMinor] = مجموع السطور (المقام بتاع النسب) — null لو الرئيسية نفسها مش عارفة
 * المصروف في كل الشهور (كل العمليات نوعها مش محدد ⇒ «غير متاح»). [partial] = فيه عمليات لسه نوعها ما اتحددش ⇒ الأرقام «لحد دلوقتي».
 * [operationCount] عدد العمليات في السطور كلها. [shares] أول [TOP_SPEND_SHARE_SEGMENTS] حصص بألوانهم و[restTenthPercent] الباقي (رمادي).
 */
data class TopSpendView(
    val period: Period,
    val periodRange: String,
    val rows: List<SpendRankRow>,
    val totalMinor: Halalas?,
    val partial: Boolean,
    val months: Int = 1,
    val current: Boolean = false,
    val operationCount: Int = rows.sumOf { it.count },
    val shares: List<ShareSegment> = emptyList(),
    val restTenthPercent: Long = 0,
)

/** الترتيب من بيانات شهر واحد اللي اتقرت (كارت الرئيسية). */
fun rankSpend(data: HomeScreenData): TopSpendView = rankSpendOver(listOf(data))

/**
 * الترتيب على شهر أو أكتر ([months]: الأحدث الأول). الأكبر الأول، والتعادل بمعرّف التصنيف (و«بلا تصنيف» آخرهم) عشان الترتيب ثابت.
 * [previous] = الشهر اللي قبل الشهر الوحيد ده ⇒ الفرق لكل تصنيف — بس لو **الشهرين** معروفين وكاملين: الشهر الجزئي مبالغه حد أدنى، والسهم
 * منه يبقى ادعاء مش محسوب (مراجعة r051 F1).
 */
fun rankSpendOver(months: List<HomeScreenData>, previous: HomeScreenData? = null, current: Boolean = false): TopSpendView {
    require(months.isNotEmpty()) { "months" }
    val newest = months.first()
    val oldest = months.last()
    val byId = newest.categories.associateBy { it.id }
    val slices = mergeSlices(months.flatMap { it.distribution })
    val total = sumMoney(slices.map { it.amountMinor })
    val bars = sharesOfLargest(slices.map { it.amountMinor })
    val before = previous?.takeIf { p -> (months + p).all { it.expenseMinor != null && !it.partial } }
        ?.let { p -> mergeSlices(p.distribution).associate { it.categoryId to it.amountMinor } }
    val rows = slices.mapIndexed { i, s ->
        val c = s.categoryId?.let(byId::get)
        val tenth = tenthOf(s.amountMinor, total)
        val change = before?.get(s.categoryId)?.takeIf { it > 0L }?.let { prev -> rateOfMoney(subtractMoney(s.amountMinor, prev), 100, prev) }
        SpendRankRow(s.categoryId, c?.name, c?.iconKey, c?.lightColor, s.amountMinor, s.count, tenth, ((tenth + 5) / 10).toInt(), bars[i], change)
    }
    val anyKnown = months.any { it.expenseMinor != null }
    val partial = months.any { it.partial } || (anyKnown && months.any { it.expenseMinor == null })
    val period = if (months.size == 1) newest.period else Period(newest.period.key, oldest.period.start, newest.period.end, months.sumOf { it.period.days })
    val top = rows.take(TOP_SPEND_SHARE_SEGMENTS)
    val rest = subtractMoney(total, sumMoney(top.map { it.amountMinor }))
    return TopSpendView(
        period = period,
        periodRange = if (months.size == 1) newest.periodRange else "${period.start} ← ${period.end}",
        rows = rows,
        totalMinor = if (anyKnown) total else null,
        partial = partial,
        months = months.size,
        current = current,
        shares = top.map { ShareSegment(it.categoryId, it.colorHex, it.shareTenthPercent) },
        restTenthPercent = tenthOf(rest, total),
    )
}

private fun tenthOf(amount: Halalas, total: Halalas): Long = if (total == 0L) 0L else rateOfMoney(amount, 1000, total)

/** نفس التصنيف في أكتر من شهر ⇒ سطر واحد (المبلغ والعدد مجموعين)، والصفر برّه، والترتيب من الأكبر. */
private fun mergeSlices(slices: List<CategorySlice>): List<CategorySlice> {
    val amounts = LinkedHashMap<Id?, MutableList<Halalas>>()
    val counts = HashMap<Id?, Int>()
    for (s in slices) {
        amounts.getOrPut(s.categoryId) { ArrayList() }.add(s.amountMinor)
        counts[s.categoryId] = (counts[s.categoryId] ?: 0) + s.count
    }
    return amounts.map { (id, list) -> CategorySlice(id, sumMoney(list), counts[id] ?: 0, 0) }
        .filter { it.amountMinor > 0L }
        .sortedWith(compareByDescending<CategorySlice> { it.amountMinor }.thenBy(nullsLast()) { it.categoryId })
}
