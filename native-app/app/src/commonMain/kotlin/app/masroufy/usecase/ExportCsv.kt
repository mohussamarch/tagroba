package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.Period
import app.masroufy.core.Transaction
import app.masroufy.core.formatAmount
import app.masroufy.core.periodForDate
import app.masroufy.port.TransactionRepository

/**
 * تصدير العمليات CSV بمخطط المعاينة — قابل للاستيراد تاني. نقل `exportCsv` من `exportBackup.ts`.
 * نسخة الإصدار 1 نفسها واسترجاعها **مش منقولين** (قرار المالك، OVERRIDES §49) — النسخة الشاملة هي البديل.
 * المبالغ بالريال نص عبر `formatAmount` (المحوِّل الوحيد)، والاقتباس بيتهرّب فالفاصلة في الاسم ما بتكسرش السطر.
 */

/** أقصى عدد فترات بيتقرا — كل استعلام محدود (ARCHITECTURE §5.6). المدى الأطول **بيتقص** (قرار المالك، OVERRIDES §49). */
private const val MAX_PERIODS = 60

/** علامة ترتيب البايت (U+FEFF) بالرقم — الحرف نفسه مش بيبان في الكود. */
private val BOM = Char(0xFEFF).toString()

private val NEEDS_QUOTES = Regex("[\",\n\r]")

class ExportCsv(private val txns: TransactionRepository) {
    /**
     * العمليات فترة فترة. ⚠️ **الفترة اللي فيها `from`** مش الفترة اللي بتبدأ في شهره: الشهر المالي بيبدأ يوم
     * الراتب، فبناء الفترة من الشهر مباشرة كان بيتخطى كل عملية قبل يوم الراتب.
     */
    private suspend fun readAll(from: String, to: String, payday: Int): List<Transaction> {
        val out = LinkedHashMap<Id, Transaction>()
        var period: Period = periodForDate(from, payday)
        var guard = 0
        while (period.start <= to && guard < MAX_PERIODS) {
            for (t in txns.listByDateRange(period.start, period.end)) out[t.id] = t
            guard++
            if (period.end >= to) break
            period = shiftPeriod(period, 1, payday)
        }
        return out.values.toList()
    }

    private fun escape(value: String): String = if (NEEDS_QUOTES.containsMatchIn(value)) "\"" + value.replace("\"", "\"\"") + "\"" else value

    /**
     * **بالظبط من `from` لـ`to`** (قرار المالك، OVERRIDES §49) — التطبيق الحالي كان بيطلّع الفترات كاملة فبيعدّي التاريخين.
     * المدى الأطول من [MAX_PERIODS] فترة بيتقص من غير رسالة — ده كمان قرار المالك، زي التطبيق الحالي.
     */
    suspend fun export(from: String, to: String, payday: Int): String {
        val rows = readAll(from, to, payday).filter { it.occurredAt in from..to }.sortedWith { a, b ->
            if (a.occurredAt == b.occurredAt) a.sourceOrder - b.sourceOrder else if (a.occurredAt < b.occurredAt) -1 else 1
        }
        val lines = mutableListOf("date,name,amount,type,source,reference")
        for (t in rows) {
            lines += listOf(
                t.occurredAt,
                escape(t.rawMerchantName?.takeIf { it.isNotEmpty() } ?: t.rawDescription?.takeIf { it.isNotEmpty() } ?: ""),
                formatAmount(t.amountMinor, t.currency, grouping = false),
                if (t.observedDirection == Direction.IN) "income" else "expense",
                escape(t.walletId ?: ""),
                escape(t.id),
            ).joinToString(",")
        }
        // BOM عشان إكسل العربي يفتحه صح
        return BOM + lines.joinToString("\n") + "\n"
    }
}
