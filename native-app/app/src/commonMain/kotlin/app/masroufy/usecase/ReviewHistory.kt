package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.daysBetween
import app.masroufy.core.isConsistentWithObservedDirection
import app.masroufy.core.normalizeText
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.core.uiText

/**
 * ReviewHistory — نقل `reviewHistory.ts`: مراجعة السجل القديم (لحد خمس سنين) على دفعات شهرية،
 * تصنيف جماعي بمعاينة الأول، ونوع اقتصادي واحد لمجموعة عمليات لنفس الاسم والاتجاه والعملة.
 */

/** الأنواع اللي ينفع تتحط لمجموعة مرة واحدة — الديون والتحويلات محتاجة شخص أو محفظة لكل عملية. */
val BULK_KINDS = listOf(
    EconomicKind.PURCHASE, EconomicKind.SUPPORT_GIFT, EconomicKind.FEE, EconomicKind.SALARY, EconomicKind.BONUS,
    EconomicKind.COMMISSION, EconomicKind.OVERTIME, EconomicKind.FREELANCE, EconomicKind.PERSONAL_SALE,
)

data class HistoryPreview(
    val rows: List<Transaction>,
    val categoryPlan: CategorizationReport,
    /** عمليات نوعها مش متأكد لنفس الاسم والاتجاه والعملة — مجموعات من اتنين وأكتر. */
    val groups: List<List<Transaction>>,
)

class ReviewHistory(private val deps: CategorizeTransactionsDeps) {
    private val categorize = CategorizeTransactions(deps)
    private val kinds = SetEconomicKind(SetEconomicKindDeps(deps.txns, deps.categories, deps.uow, deps.clock))

    private suspend fun read(from: String, to: String): List<Transaction> {
        parseIsoDate(from)
        parseIsoDate(to)
        val length = daysBetween(from, to)
        if (length < 0 || length > 1830) throw IllegalArgumentException(uiText(TextKey.REVIEW_RANGE))
        val rows = LinkedHashMap<Id, Transaction>()
        var start = from
        var i = 0
        // شهر ورا شهر: كل استعلام محدود بمداه (ARCHITECTURE §5.6)
        while (i < 61 && start <= to) {
            val end = dayNumberToIso(minOf(toDayNumber(parseIsoDate(start)) + 30, toDayNumber(parseIsoDate(to))))
            for (t in deps.txns.listByDateRange(start, end)) rows[t.id] = t
            start = dayNumberToIso(toDayNumber(parseIsoDate(end)) + 1)
            i++
        }
        return rows.values.toList()
    }

    suspend fun preview(from: String, to: String): HistoryPreview {
        val rows = read(from, to)
        val plan = categorize.plan(rows)
        val groups = LinkedHashMap<String, MutableList<Transaction>>()
        for (t in rows) {
            if (t.economicKindConfirmed) continue
            val name = normalizeText(t.rawMerchantName ?: "")
            if (name.isEmpty()) continue
            groups.getOrPut("$name|${t.observedDirection.wire}|${t.currency.name}") { mutableListOf() } += t
        }
        return HistoryPreview(rows, plan, groups.values.filter { it.size > 1 })
    }

    /** بيطبّق بس لو الخطة لسه زي اللي المستخدم شافها بالظبط — غير كده «اعرض المعاينة من جديد». */
    suspend fun applyCategories(ids: List<Id>, expected: CategorizationReport): CategorizationReport {
        val rows = deps.txns.findByIds(ids.distinct())
        val fresh = categorize.plan(rows)
        fun signature(r: CategorizationReport) = r.copy(
            changed = r.changed.sortedBy { it.transactionId },
            skippedConfirmed = r.skippedConfirmed.sorted(),
            stillNeedsReview = r.stillNeedsReview.sorted(),
        )
        if (signature(fresh) != signature(expected)) throw IllegalStateException(uiText(TextKey.REVIEW_CHANGED))
        return categorize.apply(rows)
    }

    suspend fun setGroup(ids: List<Id>, kind: EconomicKind): BulkConfirmResult {
        if (kind !in BULK_KINDS) throw IllegalArgumentException(uiText(TextKey.REVIEW_KIND_NOT_BULK))
        val rows = deps.txns.findByIds(ids.distinct())
        if (rows.isEmpty()) throw IllegalArgumentException(uiText(TextKey.REVIEW_PICK_ROWS))
        val first = rows.first()
        val firstName = normalizeText(first.rawMerchantName ?: "")
        if (rows.any { normalizeText(it.rawMerchantName ?: "") != firstName || it.currency != first.currency || it.observedDirection != first.observedDirection }) {
            throw IllegalArgumentException(uiText(TextKey.REVIEW_SAME_GROUP))
        }
        if (!isConsistentWithObservedDirection(kind, first.observedDirection)) throw IllegalArgumentException(uiText(TextKey.REVIEW_KIND_DIRECTION))
        var applied = 0
        for (row in rows) {
            val current = deps.txns.findByIds(listOf(row.id)).firstOrNull()
            if (current == null || current.economicKindConfirmed) continue
            kinds.setOne(row.id, kind)
            applied++
        }
        return BulkConfirmResult(applied, rows.size - applied)
    }
}
