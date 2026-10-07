package app.masroufy.core

/**
 * حاسبة الورث — تحويل الكسور لهللات **من غير ما هللة تضيع أو تزيد** (OVERRIDES §69: «كل قسمة مجموعها بالظبط قد الكل»).
 *
 * - [largestRemainder]: مبلغ واحد على كسور مجموعها 1 — كل واحد ياخد الجزء الصحيح، والهللات الفاضلة تروح لأكبر باقي
 *   (التعادل ⇒ الأول في الترتيب). الفرق عن الكسر بالظبط أقل من هللة.
 * - [roundMatrix]: قسمة كل حاجة على الكل بحيث **مجموع كل حاجة = قيمتها** و**مجموع كل واحد = مبلغه الكلي** في نفس الوقت.
 *   الخلية = (قيمة الحاجة × مبلغ الشخص ÷ التركة) مقرّبة لتحت أو لفوق بس. اختيار الخلايا اللي بتتقرب لفوق = مسألة تدفق
 *   (max-flow) بين الحاجات والأشخاص — الحل الكسري موجود، فالحل الصحيح موجود (تكامل مسألة النقل — total unimodularity).
 */
internal fun largestRemainder(total: Halalas, fractions: List<Frac>): List<Halalas> {
    if (fractions.isEmpty()) return emptyList()
    require(total >= 0)
    val d = fractions.fold(1L) { acc, f -> lcmOf(acc, f.den) }
    val units = fractions.map { mulChecked(it.num, d / it.den) }
    check(units.fold(0L) { a, u -> addChecked(a, u) } == d) { "fractions must sum to 1" }
    val parts = units.map { mulDivRem(total, it, d) }
    val out = parts.map { it.first }.toMutableList()
    var left = total - out.sum()
    val order = parts.indices.sortedWith(compareByDescending<Int> { parts[it].second }.thenBy { it })
    for (i in order) {
        if (left <= 0) break
        out[i] += 1
        left -= 1
    }
    return out
}

/**
 * [values] قيمة كل حاجة · [totals] مبلغ كل مستحق — والمجموعين لازم يتساووا. الناتج `[حاجة][مستحق]` كله موجب أو صفر،
 * ومجموع كل صف = قيمة الحاجة، ومجموع كل عمود = مبلغ المستحق.
 */
internal fun roundMatrix(values: List<Halalas>, totals: List<Halalas>): List<List<Halalas>> {
    val grand = values.fold(0L) { a, v -> addChecked(a, v) }
    check(grand == totals.fold(0L) { a, t -> addChecked(a, t) }) { "rows and columns must have the same total" }
    if (grand == 0L) return values.map { List(totals.size) { 0L } }
    val rows = values.size
    val cols = totals.size
    val cell = Array(rows) { LongArray(cols) }
    val canRound = Array(rows) { BooleanArray(cols) }
    val rowNeed = LongArray(rows)
    val colNeed = LongArray(cols)
    for (k in 0 until rows) {
        for (c in 0 until cols) {
            val (q, r) = mulDivRem(values[k], totals[c], grand)
            cell[k][c] = q
            canRound[k][c] = r > 0
        }
        rowNeed[k] = values[k] - cell[k].sum()
    }
    for (c in 0 until cols) colNeed[c] = totals[c] - (0 until rows).sumOf { cell[it][c] }
    val used = Array(rows) { BooleanArray(cols) }

    // مسار متبادل: الصف ياخد عمود فاضي، أو ياخد عمود مليان ويزقّ الصف اللي فيه لعمود تاني (Kuhn)
    fun augment(k: Int, seen: BooleanArray): Boolean {
        for (c in 0 until cols) {
            if (!canRound[k][c] || used[k][c] || seen[c]) continue
            seen[c] = true
            if (colNeed[c] > 0) {
                colNeed[c] -= 1
                used[k][c] = true
                return true
            }
            for (other in 0 until rows) {
                if (used[other][c] && augment(other, seen)) {
                    used[other][c] = false
                    used[k][c] = true
                    return true
                }
            }
        }
        return false
    }
    for (k in 0 until rows) {
        repeat(rowNeed[k].toInt()) {
            check(augment(k, BooleanArray(cols))) { "no exact rounding found" }
        }
    }
    return (0 until rows).map { k -> (0 until cols).map { c -> cell[k][c] + if (used[k][c]) 1 else 0 } }
}
