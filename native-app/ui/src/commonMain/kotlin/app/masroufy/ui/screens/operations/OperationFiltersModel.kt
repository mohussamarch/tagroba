package app.masroufy.ui.screens.operations

import app.masroufy.core.AMOUNT_TOLERANCE_PER_THOUSAND
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.Liquidity
import app.masroufy.core.Period
import app.masroufy.core.PersonEffect
import app.masroufy.core.ReviewState
import app.masroufy.core.SearchableTransaction
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.buildPeriod
import app.masroufy.core.formatMoney
import app.masroufy.core.parseQuery
import app.masroufy.core.ruleFor
import app.masroufy.core.searchTransactions
import app.masroufy.core.tryParseMoney
import app.masroufy.core.withinRelativeTolerance
import app.masroufy.ui.text.t
import app.masroufy.usecase.TransactionsScreenData

/** الفترة في التصفية: هذا الشهر · الشهر الماضي · آخر ٣ أشهر (الشهر المالي من يوم الراتب). */
enum class FilterPeriod(val months: Int, val skip: Int) { THIS(1, 0), LAST(1, 1), THREE(3, 0) }

/** النوع في التصفية (مجموعات العرض): مصروف · دخل · بين محافظك · ديون وأقساط. */
enum class KindGroup { OUT, IN, MOVE, DEBT }

enum class ReviewFilter { ALL, CONFIRMED, NEEDS }

/** «بلا تصنيف» في شرايح التصنيف. */
const val NO_CATEGORY = "-"

data class Filters(
    val query: String = "",
    val amountText: String = "",
    val near: Boolean = false,
    val period: FilterPeriod = FilterPeriod.THIS,
    val kinds: Set<KindGroup> = emptySet(),
    val categories: Set<String> = emptySet(),
    val wallets: Set<Id> = emptySet(),
    val review: ReviewFilter = ReviewFilter.ALL,
)

/** الفترات اللي التصفية بتقراها (الأحدث الأول) — حساب تواريخ بس (`buildPeriod` من `core`). */
fun filterPeriods(current: Period, period: FilterPeriod, payday: Int): List<Period> =
    (period.skip until period.skip + period.months).map { back -> monthsBack(current, back, payday) }

private fun monthsBack(p: Period, back: Int, payday: Int): Period {
    val (y, m) = p.key.split('-').map { it.toInt() }
    val index = y * 12 + (m - 1) - back
    return buildPeriod(index.floorDiv(12), index.mod(12) + 1, payday)
}

/**
 * مجموعة النوع من القاعدة (`ruleFor`): الداخلي ⇒ بين محافظك · سلفة أو أمانة أو سدادها (أثر على شخص ومش مصروف محسوب) أو مستحقات ⇒ ديون
 * وأقساط · وإلا بالاتجاه. الشراء والدعم ليهم أثر ممكن على شخص (التقسيم) لكنهم مصروف ⇒ «مصروف».
 */
fun kindGroupOf(tx: Transaction): KindGroup {
    val rule = ruleFor(tx.economicKind)
    val debt = rule.personEffect != PersonEffect.NONE && rule.personEffect != PersonEffect.BENEFICIARY_INFO && !rule.countsAsPersonalExpense
    return when {
        rule.liquidity == Liquidity.INTERNAL -> KindGroup.MOVE
        debt || tx.economicKind in DUES_KINDS -> KindGroup.DEBT
        tx.observedDirection == Direction.IN -> KindGroup.IN
        else -> KindGroup.OUT
    }
}

private val DUES_KINDS = setOf(EconomicKind.INSTALLMENT_PAID, EconomicKind.ROSCA_CONTRIBUTION, EconomicKind.FINANCING_RECEIVED, EconomicKind.ROSCA_PAYOUT)

private fun needsReview(tx: Transaction): Boolean = tx.reviewState != ReviewState.CONFIRMED || tx.economicKind == EconomicKind.UNCLASSIFIED

/**
 * النتايج: البحث بالكلام (`searchTransactions` — التاجر · الوصف · الملاحظة · التصنيف · الوسم) · المبلغ بالظبط أو «قريب منه ±٥٪»
 * (`withinRelativeTolerance`) · النوع · التصنيف · المحفظة · المراجعة. **اختيار بس — مفيش جمع مبالغ هنا.**
 */
fun applyFilters(data: List<TransactionsScreenData>, f: Filters, currency: Currency): List<Transaction> {
    val all = data.flatMap { it.transactions }
    val names = data.flatMap { it.categories }.associate { it.id to it.name }
    var rows = all
    if (f.query.isNotBlank()) {
        val tags = data.fold(emptyMap<Id, List<String>>()) { acc, d -> acc + d.tagNamesByTransaction }
        val merchants = data.fold(emptyMap<Id, List<String>>()) { acc, d -> acc + d.merchantNamesByTransaction }
        val items = rows.map { SearchableTransaction(it, it.categoryId?.let(names::get), tags[it.id], merchants[it.id]) }
        val hits = searchTransactions(items, parseQuery(f.query)).map { it.transaction.id }.toSet()
        rows = rows.filter { it.id in hits }
    }
    tryParseMoney(f.amountText, currency)?.takeIf { it > 0 }?.let { x ->
        rows = rows.filter { if (f.near) withinRelativeTolerance(it.amountMinor, x, AMOUNT_TOLERANCE_PER_THOUSAND) else it.amountMinor == x }
    }
    if (f.kinds.isNotEmpty()) rows = rows.filter { kindGroupOf(it) in f.kinds }
    if (f.categories.isNotEmpty()) rows = rows.filter { (it.categoryId ?: NO_CATEGORY) in f.categories }
    if (f.wallets.isNotEmpty()) rows = rows.filter { it.walletId in f.wallets }
    rows = when (f.review) {
        ReviewFilter.ALL -> rows
        ReviewFilter.CONFIRMED -> rows.filter { !needsReview(it) }
        ReviewFilter.NEEDS -> rows.filter(::needsReview)
    }
    return rows.sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.sourceOrder })
}

/** التصنيفات اللي بتظهر كشرايح: المستعملة في الفترة + «بلا تصنيف». */
fun filterCategories(data: List<TransactionsScreenData>): List<Pair<String, String>> {
    val used = data.flatMap { d -> d.transactions.mapNotNull { it.categoryId } }.toSet()
    val cats: List<Category> = data.flatMap { it.categories }.distinctBy { it.id }.filter { it.id in used }.sortedBy { it.order }
    return cats.map { it.id to it.name } + (NO_CATEGORY to t(TextKey.OPERATIONS_ROW_UNCLASSIFIED))
}

fun periodName(p: FilterPeriod): String = t(when (p) { FilterPeriod.THIS -> TextKey.OPERATION_FILTERS_THIS; FilterPeriod.LAST -> TextKey.OPERATION_FILTERS_LAST; FilterPeriod.THREE -> TextKey.OPERATION_FILTERS_THREE })

fun kindName(k: KindGroup): String = t(when (k) { KindGroup.OUT -> TextKey.OPERATION_FILTERS_KIND_OUT; KindGroup.IN -> TextKey.OPERATION_FILTERS_KIND_IN; KindGroup.MOVE -> TextKey.OPERATION_FILTERS_KIND_MOVE; KindGroup.DEBT -> TextKey.OPERATION_FILTERS_KIND_DEBT })

fun reviewName(r: ReviewFilter): String = t(when (r) { ReviewFilter.ALL -> TextKey.OPERATION_FILTERS_REVIEW_ALL; ReviewFilter.CONFIRMED -> TextKey.OPERATION_FILTERS_REVIEW_OK; ReviewFilter.NEEDS -> TextKey.OPERATION_FILTERS_REVIEW_NEEDS })

/** شريحة فلتر شغال في النتايج (الضغط بيشيله). */
data class ActiveChip(val label: String, val clear: (Filters) -> Filters)

fun activeChips(f: Filters, categories: List<Pair<String, String>>, wallets: List<Wallet>, currency: Currency): List<ActiveChip> = buildList {
    if (f.query.isNotBlank()) add(ActiveChip(t(TextKey.OPERATION_FILTERS_QUOTED, f.query.trim())) { it.copy(query = "") })
    tryParseMoney(f.amountText, currency)?.takeIf { it > 0 }?.let { x ->
        val text = formatMoney(x, currency, showCurrency = false)
        add(ActiveChip(if (f.near) t(TextKey.OPERATION_FILTERS_NEAR_CHIP, text) else text) { it.copy(amountText = "", near = false) })
    }
    if (f.period != FilterPeriod.THIS) add(ActiveChip(periodName(f.period)) { it.copy(period = FilterPeriod.THIS) })
    for (k in f.kinds) add(ActiveChip(kindName(k)) { it.copy(kinds = it.kinds - k) })
    for (c in f.categories) add(ActiveChip(categories.firstOrNull { it.first == c }?.second ?: c) { it.copy(categories = it.categories - c) })
    for (w in f.wallets) add(ActiveChip(wallets.firstOrNull { it.id == w }?.name ?: w) { it.copy(wallets = it.wallets - w) })
    if (f.review != ReviewFilter.ALL) add(ActiveChip(reviewName(f.review)) { it.copy(review = ReviewFilter.ALL) })
}
