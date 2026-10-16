package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeRope
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.InvestInsightKey
import app.masroufy.core.IsoDate
import app.masroufy.core.RopeIncome
import app.masroufy.core.RopeKnot
import app.masroufy.core.buildIncomeRope
import app.masroufy.core.ropeWindowStart
import app.masroufy.core.uiText
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.MerchantRepository
import app.masroufy.port.TransactionRepository

data class LoadIncomeRopeDeps(val incomeSources: IncomeSourceRepository, val txns: TransactionRepository, val merchants: MerchantRepository)

/**
 * «حبل مصادر دخلك» (مواصفة النقل §1.11 · §2.7): مصادر الدخل بعملة البلد + عمليات الدخل في آخر 12 شهر (راتب وتوابعه ⇒ خيط الوظيفة ·
 * شغل حر ⇒ عميل، لو النوع مالوش مصدر متسجّل — سموك r05) + البيع الشخصي (اسمه = الملاحظة، وإلا اسم التاجر، وإلا الكلمة الافتراضية).
 * null = مفيش ولا مصدر ولا دخل في الفترة ⇒ «لسه مفيش دخل متسجّل».
 */
class LoadIncomeRope(private val deps: LoadIncomeRopeDeps) {
    suspend fun load(today: IsoDate, currency: Currency): IncomeRope? {
        val sources = deps.incomeSources.listAll().filter { it.currency == currency }
        val window = deps.txns.listByDateRange(ropeWindowStart(today), today)
            .filter { it.currency == currency && it.reversedById == null && it.reversalOfId == null }
        val sales = window.filter { it.economicKind == EconomicKind.PERSONAL_SALE }
        val income = window.mapNotNull { t -> ropeKindOf(t.economicKind)?.let { t to it } }
        val needNames = sales.any { it.note.isNullOrBlank() && it.merchantId != null } || income.any { it.first.merchantId != null }
        val merchants = if (needNames) deps.merchants.listAll().associate { it.id to it.displayName } else emptyMap()
        val knots = sales.map { t ->
            RopeKnot(t.occurredAt.take(10), t.note?.trim()?.takeIf { it.isNotEmpty() } ?: t.merchantId?.let { merchants[it] })
        }
        val incomeRows = income.map { (t, kind) ->
            val label = t.merchantId?.let { merchants[it] } ?: t.note?.trim()?.takeIf { it.isNotEmpty() }
            RopeIncome(t.occurredAt.take(10), kind, label ?: uiText(if (kind == IncomeSourceKind.JOB) InvestInsightKey.ROPE_KIND_SALARY else InvestInsightKey.ROPE_KIND_CLIENT))
        }
        return buildIncomeRope(sources, knots, today, incomeRows)
    }

    private fun ropeKindOf(kind: EconomicKind): IncomeSourceKind? = when (kind) {
        EconomicKind.SALARY, EconomicKind.BONUS, EconomicKind.COMMISSION, EconomicKind.OVERTIME -> IncomeSourceKind.JOB
        EconomicKind.FREELANCE -> IncomeSourceKind.CLIENT
        else -> null
    }
}
