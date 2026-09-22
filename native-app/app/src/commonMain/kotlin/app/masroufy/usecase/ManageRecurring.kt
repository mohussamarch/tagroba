package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.RecurringCandidate
import app.masroufy.core.RecurringItem
import app.masroufy.core.Transaction
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.detectRecurring
import app.masroufy.core.parseIsoDate
import app.masroufy.core.recurringKey
import app.masroufy.core.recurringSummary
import app.masroufy.core.shiftMonths
import app.masroufy.core.toDayNumber
import app.masroufy.core.validateRecurring
import app.masroufy.port.CategoryRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.RecurringRepository
import app.masroufy.port.TransactionRepository

/**
 * ManageRecurring — نقل `manageRecurring.ts`: الاشتراكات والفواتير (ARCHITECTURE §20).
 * الاقتراح ما بيكتبش حركة مالية؛ التأكيد بيحفظ **خطة متابعة** بس.
 * القراية آخر 12 شهر بطلبات شهرية محدودة (ARCHITECTURE §5.6).
 */

data class RecurringChoice(val key: String, val name: String)

data class RecurringItemView(
    val item: RecurringItem,
    val paidMinor: Halalas?,
    val paidCount: Int,
    val annualMinor: Halalas,
    val overdue: Boolean,
)

data class RecurringView(
    val from: IsoDate,
    val to: IsoDate,
    val choices: List<RecurringChoice>,
    val candidates: List<RecurringCandidate>,
    val items: List<RecurringItemView>,
)

data class RecurringSaveInput(
    val id: String? = null,
    val name: String,
    val merchantKey: String,
    val kind: String,
    val cycleMonths: Int,
    val expectedMinor: Halalas,
    val currency: Currency,
    val nextDueAt: IsoDate,
    val active: Boolean,
)

data class ManageRecurringDeps(
    val items: RecurringRepository,
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val ids: IdGenerator,
)

class ManageRecurring(private val deps: ManageRecurringDeps) {
    private suspend fun readYear(today: IsoDate): List<Transaction> {
        parseIsoDate(today)
        var from = shiftMonths(today, -12)
        val rows = mutableListOf<Transaction>()
        for (i in 0 until 12) {
            val next = shiftMonths(shiftMonths(today, -12), i + 1)
            val to = if (i == 11) today else dayNumberToIso(toDayNumber(parseIsoDate(next)) - 1)
            rows += deps.txns.listByDateRange(from, to)
            from = next
        }
        // نفس دمج جافاسكربت: القيمة الأخيرة لكل معرّف بمكان أول ظهوره
        val byId = LinkedHashMap<String, Transaction>()
        for (t in rows) byId[t.id] = t
        return byId.values.toList()
    }

    suspend fun load(today: IsoDate): RecurringView {
        val items = deps.items.listAll()
        val rows = readYear(today)
        val categories = deps.categories.listAll()

        val choices = LinkedHashMap<String, RecurringChoice>()
        for (t in rows) {
            val name = t.rawMerchantName?.takeIf { it.isNotEmpty() } ?: continue
            choices[recurringKey(t)] = RecurringChoice(recurringKey(t), name)
        }

        return RecurringView(
            from = shiftMonths(today, -12),
            to = today,
            choices = choices.values.toList(),
            candidates = detectRecurring(rows, categories)
                .filter { c -> items.none { it.merchantKey == c.merchantKey && it.currency == c.currency } },
            items = items.map { item ->
                val s = recurringSummary(item, rows, today)
                RecurringItemView(item, s.paidMinor, s.paidCount, s.annualMinor, s.overdue)
            },
        )
    }

    suspend fun save(input: RecurringSaveInput): RecurringItem {
        val items = deps.items.listAll()
        if (input.id != null && items.none { it.id == input.id }) throw IllegalStateException("الالتزام مش موجود. حدّث الصفحة.")
        if (items.any { it.merchantKey == input.merchantKey && it.currency == input.currency && it.id != input.id }) {
            throw IllegalStateException("الخدمة دي مسجلة بالفعل بنفس العملة. عدّلها من قائمتك.")
        }
        val item = RecurringItem(
            id = input.id ?: "recurring:${input.merchantKey}|${input.currency.name}",
            name = input.name,
            merchantKey = input.merchantKey,
            kind = input.kind,
            cycleMonths = input.cycleMonths,
            expectedMinor = input.expectedMinor,
            currency = input.currency,
            nextDueAt = input.nextDueAt,
            active = input.active,
            confirmed = true,
        )
        validateRecurring(item)
        deps.items.save(item)
        return item
    }
}
