package app.masroufy.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * معايير القبول على الكشف الحقيقي (CLAUDE.md) — بكود كوتلن نفسه.
 * الملف `files/transactions_full.csv` **مش في Git** (ARCHITECTURE §9.5)، فالاختبار ده بيشتغل على جهاز المالك بس:
 * `core/build.gradle.kts` بيضيفه لما الملف موجود، ومش بيشغّله خالص لما مش موجود (GitHub). مفيش رقم بيتطبع.
 * الأرقام المستهدفة هي نفسها المكتوبة في CLAUDE.md.
 */
class RealStatementTest {
    private val csv = File(System.getProperty("masroufy.realStatement") ?: error("مسار الكشف الحقيقي مش متحدد"))

    @Test fun acceptanceCriteria() {
        val outcome = parseRows(parseCsv(csv.readText(Charsets.UTF_8)))
        assertEquals(SchemaId.LEGACY, outcome.schema)
        assertTrue(outcome.errors.isEmpty(), "فيه صفوف مرفوضة: ${outcome.errors.size}")
        assertEquals(1912, outcome.rows.size)

        val breakdown = computeExpenseBreakdown(
            outcome.rows.map {
                ClassifiableRow(if (it.direction == Direction.OUT) it.amountMinor else 0, if (it.direction == Direction.IN) it.amountMinor else 0, it.sourceCategory)
            },
        )
        assertEquals("302,171.45", formatAmount(breakdown.totalDebitMinor))
        assertEquals("201,703.18", formatAmount(breakdown.nonExpenseMinor))
        assertEquals("100,468.27", formatAmount(breakdown.realExpenseMinor))
        assertEquals("300,040.87", formatAmount(breakdown.totalCreditMinor))
        assertEquals(listOf("استثمار", "تحويلات", "تقسيط", "ذهب", "سحب نقدي", "محافظ رقمية").sorted(), breakdown.nonExpenseByCategory.keys.sorted())

        val movements = outcome.rows.mapIndexed { i, r ->
            LedgerMovement(
                r.date, i, if (r.direction == Direction.OUT) r.amountMinor else 0, if (r.direction == Direction.IN) r.amountMinor else 0,
                r.statedBalanceMinor, label = r.merchantName.ifEmpty { r.sourceCategory },
            )
        }
        val result = reconcileBalance(parseMoney("4837.83"), "2025-01-01", movements)
        assertEquals("2,707.25", formatAmount(result.closingMinor))
        assertEquals("2026-09-04", result.closingAt)
        assertEquals(0, result.mismatches.size, "عمود الرصيد لازم يطابق في كل سطر")
        assertTrue(result.checkedCount > 0)
    }
}
