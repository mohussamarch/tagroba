package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonRepository

/**
 * مراجعة r06 people-1 — تصليح مرة واحدة (آمن التكرار): `ManagePeople.addOpeningDebt` كان بيحفظ **الدين القديم** (من غير عملية —
 * `originTransactionId == null`) بالريال دايمًا، فدين اتكتب في بلد عملتها غير الريال (البداية في مصر مثلًا) اتخزن بعملة غلط والمبلغ نفسه
 * مكتوب بعملة البلد. التصليح بيرجّع عملة الدين لعملة البلد — **المبلغ ما بيتغيرش** (المالك كتبه بعملة البلد). الريال: مفيش حاجة تتصلح.
 * الديون اللي ليها عملية ما بتتلمسش (عملتها من العملية). بيرجّع عدد الديون اللي اتصلحت (0 في التشغيلة التانية).
 */
class RepairOpeningDebtCurrency(
    private val people: PersonRepository,
    private val obligations: ObligationRepository,
    private val spaceCurrency: Currency,
) {
    suspend fun run(): Int {
        if (spaceCurrency == Currency.SAR) return 0
        val wrong = people.listAll().flatMap { obligations.listByPerson(it.id) }
            .filter { it.originTransactionId == null && it.currency != spaceCurrency }
        if (wrong.isNotEmpty()) obligations.saveMany(wrong.map { it.copy(currency = spaceCurrency) })
        return wrong.size
    }
}
