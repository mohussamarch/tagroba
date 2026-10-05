package app.masroufy.core

/**
 * «حركة الفلوس» جنب «الدخل الحقيقي» — قرار المالك 2026-10-01 (OVERRIDES §58): «هو يظهر ك دخل في الداش بورد … ولكن هو مدام
 * هيرجعها تاني اذن هو مش دخل حقيقي … اخر السنة مثلا ممكن نقول ان كان في حركة فلوس بمليون ريال ولكن الدخل الحقيقي كان 600».
 * ⇒ السلفة اللي دخلتلك بتبان في **اللي دخل**، بس مش في **الدخل** (\`computePeriodTotals\` ما اتغيرش).
 * حقيقة بنكية بالاتجاه المرصود بس — من غير نوع، فمفيش «غير متاح» هنا. التحويل بين محافظك مش حركة (فلوسك من جيب لجيب).
 */
data class CashMovement(val inMinor: Halalas, val outMinor: Halalas)

fun cashMovement(transactions: List<Transaction>): CashMovement {
    var inflow = 0L
    var outflow = 0L
    for (t in transactions) {
        if (ruleFor(t.economicKind).liquidity == Liquidity.INTERNAL) continue
        if (t.observedDirection == Direction.IN) inflow = addMoney(inflow, t.amountMinor) else outflow = addMoney(outflow, t.amountMinor)
    }
    return CashMovement(inflow, outflow)
}
