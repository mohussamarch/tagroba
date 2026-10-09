package app.masroufy.usecase

import app.masroufy.core.EstimatePolicy
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.Transaction
import app.masroufy.core.countingDate
import app.masroufy.core.countingReadStart
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.TransactionRepository

/**
 * عمليات الفترة **زي ما بتتحسب** (قرار المالك §75-3): الراتب اللي نزل قبل أول الفترة بشوية جوه، وراتب الفترة الجاية اللي نزل في آخرها
 * برّه ([countedInNextPeriod] — عشان الشاشة تعرضه بعلامة «بيتحسب للشهر الجديد» وما يختفيش). القراية محدودة: من
 * `countingReadStart` لآخر الفترة (ARCHITECTURE §5.6). [EstimatePolicy.LEGACY] ⇒ نفس `listByDateRange(start, end)` بالظبط.
 * [rows] بالعمليات الأصلية (من غير التقدير) وبنفس ترتيب المستودع — الشاشة بتعمل التقدير عليها زي الأول. كل عملية بتطلع في فترة
 * واحدة بالظبط (يوم حسابها واحد).
 */
internal data class PeriodRows(val rows: List<Transaction>, val countedInNextPeriod: List<Transaction>)

internal suspend fun loadPeriodRows(txns: TransactionRepository, period: Period, payday: Int, names: Map<Id, String>): PeriodRows =
    loadRangeRows(txns, period.start, period.end, payday, names)

/** نفس القاعدة لمدى من [from] لـ[to] (ملخص «حركة الفلوس»). [payday] null ⇒ مفيش نقل (المدى مش شهور مالية). */
internal suspend fun loadRangeRows(txns: TransactionRepository, from: IsoDate, to: IsoDate, payday: Int?, names: Map<Id, String>): PeriodRows {
    val policy = EstimatePolicy.current
    if (policy == EstimatePolicy.LEGACY || payday == null) return PeriodRows(txns.listByDateRange(from, to), emptyList())
    val raw = txns.listByDateRange(countingReadStart(from, policy), to)
    // الراتب = نوعه بعد التقدير (المتخزن، أو المعروف بالتصنيف — §75-1)
    val counted = withEstimatedKinds(raw, names, policy).transactions
    val rows = ArrayList<Transaction>(raw.size)
    val next = mutableListOf<Transaction>()
    for (i in raw.indices) {
        val day = countingDate(counted[i], payday, policy)
        when {
            day in from..to -> rows += raw[i]
            day > to && raw[i].occurredAt <= to -> next += raw[i]
        }
    }
    return PeriodRows(rows, next)
}

/**
 * يوم الراتب من الفترة نفسها لما الطلب ما بعتهوش: أكبر يوم من أول الفترة وأول الفترة الجاية — عشان يوم 29–31 اللي اتقيد بآخر فبراير
 * (فترة بتبدأ 28 فبراير وبعدها 31 مارس ⇒ 31) يرجع زي ما هو.
 */
internal fun paydayOf(period: Period): Int {
    val nextStart = dayNumberToIso(toDayNumber(parseIsoDate(period.end)) + 1)
    return maxOf(parseIsoDate(period.start).day, parseIsoDate(nextStart).day)
}
