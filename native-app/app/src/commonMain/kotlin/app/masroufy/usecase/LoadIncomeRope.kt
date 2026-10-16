package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeRope
import app.masroufy.core.IsoDate
import app.masroufy.core.RopeKnot
import app.masroufy.core.buildIncomeRope
import app.masroufy.core.ropeWindowStart
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.MerchantRepository
import app.masroufy.port.TransactionRepository

data class LoadIncomeRopeDeps(val incomeSources: IncomeSourceRepository, val txns: TransactionRepository, val merchants: MerchantRepository)

/**
 * «حبل مصادر دخلك» (مواصفة النقل §1.11 · §2.7): مصادر الدخل بعملة البلد + البيع الشخصي في آخر 12 شهر (اسمه = الملاحظة، وإلا اسم
 * التاجر، وإلا الكلمة الافتراضية). null = مفيش ولا مصدر في الفترة ⇒ «لسه مفيش دخل متسجّل».
 */
class LoadIncomeRope(private val deps: LoadIncomeRopeDeps) {
    suspend fun load(today: IsoDate, currency: Currency): IncomeRope? {
        val sources = deps.incomeSources.listAll().filter { it.currency == currency }
        val sales = deps.txns.listByDateRange(ropeWindowStart(today), today)
            .filter { it.currency == currency && it.economicKind == EconomicKind.PERSONAL_SALE && it.reversedById == null && it.reversalOfId == null }
        val merchants = if (sales.any { it.note.isNullOrBlank() && it.merchantId != null }) deps.merchants.listAll().associate { it.id to it.displayName } else emptyMap()
        val knots = sales.map { t ->
            RopeKnot(t.occurredAt.take(10), t.note?.trim()?.takeIf { it.isNotEmpty() } ?: t.merchantId?.let { merchants[it] })
        }
        return buildIncomeRope(sources, knots, today)
    }
}
