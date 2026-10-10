package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** رد المالك L3: الرصيد مش متسلسل ⇒ تنبيه بس · قيم مش أرقام ⇒ بيوقف. أرقام مخترعة. */
class StatementBalanceCheckTest {
    private fun row(balance: String, debit: String = "", credit: String = "") = MappedBalanceRow(balance, debit, credit)

    @Test fun aChainedColumnIsFine() {
        val rows = listOf(row("1,000.00"), row("950.50", debit = "49.50"), row("1,200.50", credit = "250"), row("1,180.50", debit = "-20"))
        assertEquals(BalanceColumnCheck.Chained, checkBalanceColumn(rows, Currency.SAR))
    }

    @Test fun aBrokenOrReversedChainOnlyWarnsWithTheLines() {
        val missingLine = listOf(row("1,000.00"), row("950.00", debit = "50"), row("700.00", debit = "100"))
        assertEquals(BalanceColumnCheck.NotChained(listOf(3)), checkBalanceColumn(missingLine, Currency.SAR))
        val newestFirst = listOf(row("900.00", debit = "100"), row("1,000.00"))
        assertEquals(BalanceColumnCheck.NotChained(listOf(2)), checkBalanceColumn(newestFirst, Currency.SAR), "مقلوب ⇒ تنبيه برضه")
    }

    @Test fun valuesThatAreNotNumbersBlock() {
        val rows = listOf(row("1,000.00"), row("مشتريات", debit = "50"), row("", debit = "5"))
        assertEquals(BalanceColumnCheck.NotNumbers(listOf(2)), checkBalanceColumn(rows, Currency.SAR), "الفاضي بيتخطى، والكلام بيوقف")
    }

    @Test fun signedAmountsAndArabicDigitsChainToo() {
        val rows = listOf(MappedBalanceRow("١٠٠٫٠٠", signed = ""), MappedBalanceRow("٧٥٫٠٠", signed = "-25"), MappedBalanceRow("80", signed = "5"))
        assertEquals(BalanceColumnCheck.Chained, checkBalanceColumn(rows, Currency.SAR))
    }

    @Test fun withoutAmountsNothingIsClaimed() {
        assertEquals(BalanceColumnCheck.Unchecked, checkBalanceColumn(listOf(MappedBalanceRow("10"), MappedBalanceRow("20")), Currency.SAR))
    }
}
