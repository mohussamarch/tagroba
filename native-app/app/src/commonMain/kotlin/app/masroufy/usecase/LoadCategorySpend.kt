package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.DataCoverage
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Merchant
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.shiftDays
import app.masroufy.core.assessCoverage
import app.masroufy.core.categoryDistribution
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.countsAsPersonalExpense
import app.masroufy.core.normalizeText
import app.masroufy.core.sumMoney
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * صرف تصنيف (أو أكتر) · محل · شخص في أي مدى — للمساعد (§78-٢ «صرفت كام على X في فترة Y»). **نفس دوال الرئيسية بالظبط**
 * (`withEstimatedKinds` + `categoryDistribution` + التخصيصات) فالرقم هو هو اللي في توزيع التصنيفات — واختبار بيثبت ده.
 */
data class CategorySpend(
    /** null = كل عمليات المدى من غير نوع (مجهول مش صفر — القاعدة 10). */
    val amountMinor: Halalas?,
    /** عدد عمليات المصروف (نفس `CategorySlice.count`). */
    val count: Int,
    val coverage: DataCoverage,
    /** كام عملية في المدى نوعها تقديري ومحتاجة تأكيد. */
    val needsReviewCount: Int,
) {
    /** فيه عمليات من غير نوع أو محتاجة تأكيد ⇒ «تقريبي». */
    val approximate: Boolean get() = amountMinor != null && (coverage.unclassified > 0 || needsReviewCount > 0)
}

/** هدايا الشخص وسلفه في المدى — **منفصلين، عمرهم ما بيتجمعوا** (التصميم). */
data class PersonSpend(val giftsMinor: Halalas, val lentMinor: Halalas, val giftCount: Int, val lentCount: Int)

data class LoadCategorySpendDeps(
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val allocations: AllocationRepository,
)

class LoadCategorySpend(private val deps: LoadCategorySpendDeps) {
    private suspend fun rows(from: IsoDate, to: IsoDate): Triple<List<Transaction>, Int, List<app.masroufy.core.PersonAllocation>> {
        val raw = deps.txns.listByDateRange(from, to)
        val names = deps.categories.listAll().associate { it.id to it.name }
        val estimated = withEstimatedKinds(raw, names)
        return Triple(estimated.transactions, estimated.needsReviewCount, deps.allocations.listByTransactionIds(raw.map { it.id }))
    }

    /** مجموع التصنيفات [categoryIds] (الأب بيتبعت مع ولاده من المستدعي) — من توزيع الرئيسية نفسه. */
    suspend fun load(from: IsoDate, to: IsoDate, categoryIds: Set<Id>): CategorySpend {
        val (rows, review, allocations) = rows(from, to)
        val coverage = assessCoverage(rows)
        val unknown = coverage.total > 0 && coverage.unclassified == coverage.total
        val slices = categoryDistribution(rows, allocations).slices.filter { it.categoryId != null && it.categoryId in categoryIds }
        return CategorySpend(if (unknown) null else sumMoney(slices.map { it.amountMinor }), slices.sumOf { it.count }, coverage, review)
    }

    /** صرف محل: عملياته بمعرّفه أو باسمه/أساميه التانية (العملية اللي ما اتربطتش بتاجر) — `computePeriodTotals` على عملياته بتخصيصاتها. */
    suspend fun byMerchant(from: IsoDate, to: IsoDate, merchant: Merchant): CategorySpend {
        val (rows, _, allocations) = rows(from, to)
        val mine = rows.filter { atMerchant(it, merchant) }
        val coverage = assessCoverage(mine)
        val unknown = coverage.total > 0 && coverage.unclassified == coverage.total
        val totals = computePeriodTotals(mine, allocations.filter { a -> mine.any { it.id == a.transactionId } })
        val count = mine.count { countsAsPersonalExpense(it.economicKind) && !it.excludedFromBudget }
        return CategorySpend(if (unknown) null else totals.personalExpenseMinor, count, coverage, 0)
    }

    /** هدايا وسلف شخص في المدى — من تخصيصاته على عمليات المدى (الهدية تخصيص `gift`، والسلفة/النصيب `receivable`). */
    suspend fun byPerson(from: IsoDate, to: IsoDate, personId: Id): PersonSpend {
        val (_, _, allocations) = rows(from, to)
        val mine = allocations.filter { it.personId == personId }
        val gifts = mine.filter { it.allocationKind == AllocationKind.GIFT }
        val lent = mine.filter { it.allocationKind == AllocationKind.RECEIVABLE }
        return PersonSpend(sumMoney(gifts.map { it.amountMinor }), sumMoney(lent.map { it.amountMinor }), gifts.size, lent.size)
    }
}

/** العملية عند المحل ده: بمعرّف التاجر، أو اسمها الخام (بعد `normalizeText`) = اسمه أو واحد من أساميه. */
fun atMerchant(t: Transaction, m: Merchant): Boolean {
    if (t.merchantId != null) return t.merchantId == m.id
    val raw = t.rawMerchantName?.let(::normalizeText) ?: return false
    return raw == m.normalizedName || raw == normalizeText(m.displayName) || m.aliases.orEmpty().any { it == raw }
}

/** آخر عملية عند محل (التصميم: `FindLastAtMerchant`) — المبلغ والتاريخ واسم المحفظة. */
data class LastAtMerchant(val transaction: Transaction, val wallet: Wallet?)

class FindLastAtMerchant(private val txns: TransactionRepository, private val wallets: WalletRepository) {
    /**
     * بيدوّر لورا ٣ شهور ٣ شهور لحد سنتين (كل استعلام محدود — ARCHITECTURE §5.6)، ويقف عند أول مدى فيه عملية. خارجة بس (مشتريات المحل).
     */
    suspend fun find(merchant: Merchant, today: IsoDate): LastAtMerchant? {
        var to = today
        repeat(8) {
            val from = shiftDays(to, -91)
            val hit = txns.listByDateRange(from, to).filter { it.observedDirection == Direction.OUT && atMerchant(it, merchant) }
                .maxWithOrNull(compareBy<Transaction> { it.occurredAt }.thenBy { it.sourceOrder })
            if (hit != null) return LastAtMerchant(hit, hit.walletId?.let { wallets.findById(it) })
            to = shiftDays(from, -1)
        }
        return null
    }
}
