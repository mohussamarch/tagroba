package app.masroufy.core

/**
 * فحص عمود الرصيد في كشف أعمدته اتحددت باليد (رد المالك L3 — OVERRIDES §76 «ردود المالك»):
 * - قيمة مكتوبة في عمود الرصيد ومش رقم ⇒ [BalanceColumnCheck.NotNumbers] — **بيوقف** (غالبًا العمود الغلط).
 * - الرصيد مش متسلسل (مقلوب أو ناقص سطر) ⇒ [BalanceColumnCheck.NotChained] — **تنبيه بس**: الاستيراد بيكمل والسطور دي بتظهر في المراجعة.
 * التسلسل: رصيد السطر = رصيد اللي قبله − المدين + الدائن (أو + المبلغ بإشارته). خانة فاضية بتتخطى. **هللات صحيحة بس.**
 */
data class MappedBalanceRow(val balance: String, val debit: String? = null, val credit: String? = null, val signed: String? = null)

sealed interface BalanceColumnCheck {
    /** مفيش سطرين متتاليين برصيد ومبلغ مقروءين نقارن بيهم (أو مفيش عمود مبلغ) ⇒ ولا تنبيه. */
    data object Unchecked : BalanceColumnCheck

    data object Chained : BalanceColumnCheck

    /** أرقام السطور من 1 (أول سطر بيانات). */
    data class NotNumbers(val rows: List<Int>) : BalanceColumnCheck

    data class NotChained(val rows: List<Int>) : BalanceColumnCheck
}

fun checkBalanceColumn(rows: List<MappedBalanceRow>, currency: Currency): BalanceColumnCheck {
    val bad = rows.indices.filter { i -> JsText.trim(rows[i].balance).isNotEmpty() && tryParseMoney(rows[i].balance, currency) == null }
    if (bad.isNotEmpty()) return BalanceColumnCheck.NotNumbers(bad.map { it + 1 })
    val balances = rows.map { if (JsText.trim(it.balance).isEmpty()) null else tryParseMoney(it.balance, currency) }
    var compared = 0
    val broken = mutableListOf<Int>()
    for (i in 1 until rows.size) {
        val before = balances[i - 1] ?: continue
        val now = balances[i] ?: continue
        val move = movement(rows[i], currency) ?: continue
        compared++
        if (addMoney(before, move) != now) broken += i + 1
    }
    return when {
        compared == 0 -> BalanceColumnCheck.Unchecked
        broken.isEmpty() -> BalanceColumnCheck.Chained
        else -> BalanceColumnCheck.NotChained(broken)
    }
}

/** حركة السطر بالهللة: المبلغ بإشارته، أو الدائن − المدين (المدين بيتقري موجب حتى لو البنك كاتبه بالسالب). null = مش مقروءة. */
private fun movement(row: MappedBalanceRow, currency: Currency): Halalas? {
    fun read(text: String?): Halalas? = if (text == null || JsText.trim(text).isEmpty()) 0L else tryParseMoney(text, currency)
    if (row.signed != null) return if (JsText.trim(row.signed).isEmpty()) null else tryParseMoney(row.signed, currency)
    if (row.debit == null && row.credit == null) return null
    val debit = read(row.debit) ?: return null
    val credit = read(row.credit) ?: return null
    return subtractMoney(credit, absMoney(debit))
}
