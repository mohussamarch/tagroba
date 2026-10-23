package app.masroufy.usecase

import app.masroufy.core.UsageProperty
import app.masroufy.core.UsageValue
import app.masroufy.core.bnplProviderOf
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.RoscaRepository
import app.masroufy.port.SavingsGoalRepository
import app.masroufy.port.WalletRepository
import app.masroufy.port.ZakatYearRepository

/** مستودعات بلد واحدة اللي اللقطة محتاجاها (عدّ بس — مفيش مبلغ ولا اسم بيطلع من هنا). */
class UsageSpaceSource(
    val wallets: WalletRepository,
    val installmentPlans: InstallmentPlanRepository,
    val roscas: RoscaRepository,
    val zakatYears: ZakatYearRepository,
    val savingsGoals: SavingsGoalRepository,
)

/** اللقطة نفسها — فئات وأعلام بس. */
data class UsageSnapshot(
    val accounts: Int,
    val countries: Int,
    val installments: Boolean,
    val bnpl: Boolean,
    val rosca: Boolean,
    val zakat: Boolean,
    val savings: Boolean,
)

/**
 * PLAN-0.6 §16 «الاستخدام»: المزايا المفعّلة (أقساط · تابي/تمارا · جمعيات · زكاة · ادخار) · عدد الحسابات (فئات 1 · 2-3 · 4+) · بلد ولا بلدين.
 * بتتبعت **خصائص مستخدم** (user properties) — حالة مش حدث. بتشتغل مرة لما الجلسة تجهز (برّه خيط الواجهة). أي فشل ⇒ ولا حاجة.
 */
class ReportUsageSnapshot(private val spaces: List<UsageSpaceSource>, private val send: (UsageProperty, UsageValue) -> Unit) {
    suspend fun read(): UsageSnapshot {
        var accounts = 0
        var installments = false
        var bnpl = false
        var rosca = false
        var zakat = false
        var savings = false
        for (s in spaces) {
            accounts += s.wallets.listAll().size
            val plans = s.installmentPlans.listAll()
            if (plans.any { bnplProviderOf(it.provider) == null }) installments = true
            if (plans.any { bnplProviderOf(it.provider) != null }) bnpl = true
            if (s.roscas.listAll().isNotEmpty()) rosca = true
            if (s.zakatYears.listAll().isNotEmpty()) zakat = true
            if (s.savingsGoals.listAll().isNotEmpty()) savings = true
        }
        return UsageSnapshot(accounts, spaces.size, installments, bnpl, rosca, zakat, savings)
    }

    suspend fun run() {
        val s = read()
        send(UsageProperty.ACCOUNTS, UsageValue.accountsBucket(s.accounts))
        send(UsageProperty.COUNTRIES, UsageValue.count(s.countries.coerceAtMost(2)))
        send(UsageProperty.INSTALLMENTS, UsageValue.flag(s.installments))
        send(UsageProperty.BNPL, UsageValue.flag(s.bnpl))
        send(UsageProperty.ROSCA, UsageValue.flag(s.rosca))
        send(UsageProperty.ZAKAT, UsageValue.flag(s.zakat))
        send(UsageProperty.SAVINGS, UsageValue.flag(s.savings))
    }
}
