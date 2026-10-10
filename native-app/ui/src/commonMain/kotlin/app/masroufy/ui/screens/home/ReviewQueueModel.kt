package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.ruleFor
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.text.t
import app.masroufy.usecase.CategorizationReport
import app.masroufy.usecase.SuggestionLine
import app.masroufy.usecase.SuggestionSummary

/**
 * المراجعة (`ReviewQueue` — spec/02 «لا تصنّف تحويلًا غامضًا كراتب» · §14 · §18 · §74): **الغامض بس**، متجمّع بالنوع المقترح وبدايله
 * (`SetEconomicKind.summarize`). المؤكد قبل كده ما بيظهرش (`alreadySet`). «أكّد الـN» للمجموعة بس لو اقتراحاتها قاطعة (`confirmBulk`).
 * ⚠️ **ناقص في المنطق** (§75 — النموذج راسمه كمجموعات لوحدها): «هل هذا راتبك؟» مرة واحدة · «سلفة أم دعم؟» لكل تحويل لشخص مربوط ·
 * «سداد سلفة؟» · «كم بالريال؟» للعملة الأجنبية · «تراجع» يرجّع العملية «غير مؤكدة» (مفيش حالة استخدام بتفك التأكيد — التراجع هنا بيفتح الاختيارات تاني).
 */
data class ReviewItem(
    val id: Id,
    val name: String,
    /** «٣ أكتوبر». */
    val sub: String,
    /** سبب الاقتراح من المنطق (`KindSuggestion.reason`). */
    val note: String,
    val amountMinor: Halalas,
    val currency: Currency,
    val tone: AmountTone,
    /** null = غامض (مفيش اقتراح) ⇒ الاختيارات بس. */
    val suggestion: EconomicKind?,
    val alternatives: List<EconomicKind>,
    /** اقتراح قاطع (ينفع يتأكد مع مجموعته مرة واحدة). */
    val bulk: Boolean,
)

data class ReviewGroup(val key: String, val title: String, val items: List<ReviewItem>) {
    /** «أكّد الـN» = القاطع اللي لسه ما اتأكدش في المجموعة، ولو أكتر من واحدة. */
    fun bulkIds(done: Set<Id>): List<Id> = items.filter { it.bulk && it.id !in done }.map { it.id }.takeIf { it.size > 1 }.orEmpty()
}

fun kindLabel(kind: EconomicKind): String = ruleFor(kind).label

private fun itemOf(line: SuggestionLine, bulk: Boolean): ReviewItem {
    val tx = line.transaction
    return ReviewItem(
        id = tx.id,
        name = transactionTitle(tx),
        sub = dayMonth(tx.occurredAt),
        note = line.suggestion.reason,
        amountMinor = tx.amountMinor,
        currency = tx.currency,
        tone = if (tx.observedDirection == Direction.IN) AmountTone.INCOME else AmountTone.EXPENSE,
        suggestion = line.suggestion.kind,
        alternatives = line.suggestion.alternatives.filter { it != line.suggestion.kind },
        bulk = bulk,
    )
}

/**
 * المجموعات بالترتيب: المقترح (بنوعه — «تبدو «استرداد»») ثم الغامض الوارد ثم الغامض الصادر. داخل كل مجموعة الأحدث الأول.
 */
fun reviewGroups(summary: SuggestionSummary): List<ReviewGroup> {
    fun items(lines: List<Pair<SuggestionLine, Boolean>>) =
        lines.sortedByDescending { it.first.transaction.occurredAt }.map { (line, bulk) -> itemOf(line, bulk) }
    val suggested = (summary.confirmable.map { it to true } + summary.needsLook.map { it to false })
        .filter { it.first.suggestion.kind != null }
        .groupBy { it.first.suggestion.kind!! }
        .map { (kind, lines) -> ReviewGroup("kind-${kind.wire}", t(UiKey.REVIEW_QUEUE_GROUP_KIND, kindLabel(kind)), items(lines)) }
    val (incoming, outgoing) = summary.ambiguous.map { it to false }.partition { it.first.transaction.observedDirection == Direction.IN }
    val ambiguous = listOfNotNull(
        incoming.takeIf { it.isNotEmpty() }?.let { ReviewGroup("in", t(UiKey.REVIEW_QUEUE_GROUP_IN), items(it)) },
        outgoing.takeIf { it.isNotEmpty() }?.let { ReviewGroup("out", t(UiKey.REVIEW_QUEUE_GROUP_OUT), items(it)) },
    )
    return suggested + ambiguous
}

/** كل اللي محتاج مراجعة في الشهر (للعنوان والشريط). */
fun reviewTotal(summary: SuggestionSummary): Int = summary.confirmable.size + summary.needsLook.size + summary.ambiguous.size

/** «٦ عمليات نوعها غير مؤكد» / «اكتملت المراجعة». */
fun reviewHeroTitle(left: Int): String = when {
    left <= 0 -> t(UiKey.REVIEW_QUEUE_DONE_TITLE)
    left == 1 -> t(UiKey.REVIEW_QUEUE_LEFT_ONE)
    left == 2 -> t(UiKey.REVIEW_QUEUE_LEFT_TWO)
    left <= 10 -> t(UiKey.REVIEW_QUEUE_LEFT_FEW, sentenceNumber(left))
    else -> t(UiKey.REVIEW_QUEUE_LEFT_MANY, sentenceNumber(left))
}

/** نسبة الشريط (عدّ عمليات — مش فلوس). */
fun reviewDonePercent(total: Int, done: Int): Int = if (total <= 0) 100 else (done.coerceIn(0, total) * 100) / total

/** معاينة «طبّق قواعدك على السابق»: كل تصنيف هيتحط وعدد عملياته (الأكتر الأول) — من `CategorizationReport.changed`. */
fun ruleRows(plan: CategorizationReport, categories: List<Category>): List<Pair<String, Int>> {
    val names = categories.associate { it.id to it.name }
    return plan.changed.groupBy { it.toCategoryId }
        .map { (id, rows) -> (id?.let { names[it] } ?: t(UiKey.REVIEW_QUEUE_RULES_UNCATEGORIZED)) to rows.size }
        .sortedByDescending { it.second }
}
