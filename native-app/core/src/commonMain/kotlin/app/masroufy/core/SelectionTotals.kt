package app.masroufy.core

/**
 * مجموع العمليات المحددة في «العمليات» (قايمة المالك §6.5 بند 15): عددها واللي خرج واللي دخل **لكل عملة لوحدها** —
 * عملتين مختلفتين عمرهم ما يتجمعوا في رقم واحد. أعداد صحيحة بالهللة بس (CLAUDE.md #1)، والاتجاه من الكشف ([Transaction.observedDirection]).
 */
data class SelectionTotal(val currency: Currency, val count: Int, val outMinor: Halalas, val inMinor: Halalas)

/** بترتيب أول ظهور للعملة في المحدد — نفس ترتيب القايمة. فاضية ⇒ قايمة فاضية (مش صفر مؤكد). */
fun selectionTotals(transactions: List<Transaction>): List<SelectionTotal> {
    val order = LinkedHashMap<Currency, SelectionTotal>()
    for (t in transactions) {
        val cur = order[t.currency] ?: SelectionTotal(t.currency, 0, 0L, 0L)
        order[t.currency] = if (t.observedDirection == Direction.IN) {
            cur.copy(count = cur.count + 1, inMinor = cur.inMinor + t.amountMinor)
        } else {
            cur.copy(count = cur.count + 1, outMinor = cur.outMinor + t.amountMinor)
        }
    }
    return order.values.toList()
}
