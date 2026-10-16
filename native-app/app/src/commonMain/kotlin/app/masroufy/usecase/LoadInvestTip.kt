package app.masroufy.usecase

import app.masroufy.core.ACTUAL_SAVING_MONTHS
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.BankTip
import app.masroufy.core.Texts
import app.masroufy.core.VERIFIED_TIPS
import app.masroufy.core.InvestInsightKey
import app.masroufy.core.IsoDate
import app.masroufy.core.Space
import app.masroufy.core.TipFacts
import app.masroufy.core.TipGroup
import app.masroufy.core.UserSetting
import app.masroufy.core.averageSavingMinor
import app.masroufy.core.closeTip
import app.masroufy.core.investableSplit
import app.masroufy.core.isInvestReturn
import app.masroufy.core.monthSavingMinor
import app.masroufy.core.monthSpendMinor
import app.masroufy.core.periodForDate
import app.masroufy.core.shiftMonths
import app.masroufy.core.situationsOf
import app.masroufy.core.sumMoney
import app.masroufy.core.tipQueue
import app.masroufy.core.uiText
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.ProfileRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UserSettingsStore

/** كارت النصيحة: [why] سطر «ليه دي» (حالتك أو «نصيحة») · [title] و[text] من البنك الموثّق · [source] «المصدر: …» جاهزة للعرض. */
data class TipView(val id: String, val why: String, val title: String, val text: String, val source: String)

data class TipLine(val id: String, val title: String, val text: String, val source: String)

data class TipGroupView(val title: String, val tips: List<TipLine>)

data class LoadInvestTipDeps(
    val settings: UserSettingsStore,
    val txns: TransactionRepository,
    val allocations: AllocationRepository,
    val categories: CategoryRepository,
    val withYouNow: LoadWithYouNow,
    val profile: ProfileRepository?,
    val currency: Currency,
    val countryCode: String,
    val clock: Clock,
)

/**
 * نصيحة «الاستثمار» (مواصفة النقل §1.7 · §1.8 · §2.7): [current] = أول نصيحة في الطابور (حالتك ⇒ العامة ⇒ من غير المقفولة) أو null ·
 * [close] = «×» (بتتحفظ في الحساب وعمرها ما ترجع) · [all] = صفحة «النصايح» (كلها، المقفولة كمان). الأرقام كلها من بياناتك (القاعدة 10).
 */
class LoadInvestTip(private val deps: LoadInvestTipDeps) {
    suspend fun current(today: IsoDate, holdings: HoldingsSnapshot?): TipView? {
        val closed = closedIds().toSet()
        val queued = tipQueue(situationsOf(facts(today, holdings)), closed, deps.countryCode).firstOrNull() ?: return null
        val w = queued.tip.words(Texts.arabicVariant, Texts.language)
        return TipView(queued.tip.id, uiText(queued.situation?.why ?: InvestInsightKey.TIP_WHY_GENERAL), w.title, w.text, sourceLine(queued.tip))
    }

    suspend fun close(tipId: String) {
        if (VERIFIED_TIPS.none { it.id == tipId }) return
        deps.settings.save(UserSetting.InvestTips(closeTip(closedIds(), tipId), deps.clock.nowIso()))
    }

    /** صفحة «النصايح»: نصايح البلد دي بس (والمشتركة)، مجموعة فاضية ما بتظهرش. */
    fun all(space: Space): List<TipGroupView> = TipGroup.entries.map { g ->
        TipGroupView(uiText(g.title), VERIFIED_TIPS.filter { it.group == g && it.fitsCountry(space.countryCode) }.map {
            val w = it.words(Texts.arabicVariant, Texts.language)
            TipLine(it.id, w.title, w.text, sourceLine(it))
        })
    }.filter { it.tips.isNotEmpty() }

    private fun sourceLine(tip: BankTip) = uiText(InvestInsightKey.TIP_SOURCE, tip.source.name)

    private suspend fun closedIds(): List<String> =
        deps.settings.listAll().filterIsInstance<UserSetting.InvestTips>().firstOrNull()?.closed.orEmpty()

    /** الحقايق: آخر 3 شهور كاملة (تحويش وصرف بنفس قواعد «حاسبة الادخار») · أرصدة المحافظ · «حاجاتي» · شراء أصل · عائد السنة. */
    internal suspend fun facts(today: IsoDate, holdings: HoldingsSnapshot?): TipFacts {
        val payday = deps.profile?.load()?.payday ?: DEFAULT_PAYDAY
        val names = deps.categories.listAll().associate { it.id to it.name }
        val current = periodForDate(today, payday)
        val saved = mutableListOf<Halalas?>()
        val spent = mutableListOf<Halalas?>()
        for (back in ACTUAL_SAVING_MONTHS downTo 1) {
            val rows = loadPeriodRows(deps.txns, shiftPeriod(current, -back, payday), payday, names).rows.filter { it.currency == deps.currency }
            val counted = withEstimatedKinds(rows, names).withoutPendingIncoming
            val alloc = deps.allocations.listByTransactionIds(counted.map { it.id })
            saved += monthSavingMinor(counted, alloc)
            spent += monthSpendMinor(counted, alloc)
        }
        val wallets = runCatching { deps.withYouNow.load(today).wallets.mapNotNull { it.balanceMinor } }.getOrNull()
        val cash = wallets?.takeIf { it.isNotEmpty() }?.let { sumMoney(it) }
        val from = minOf(shiftMonths(today, -ACTUAL_SAVING_MONTHS), today.take(4) + "-01-01")
        val recent = deps.txns.listByDateRange(from, today)
        val bought = recent.any {
            it.currency == deps.currency && it.economicKind == EconomicKind.ASSET_BUY && it.occurredAt.take(10) > shiftMonths(today, -ACTUAL_SAVING_MONTHS)
        }
        val returns = sumMoney(recent.filter { isInvestReturn(it, deps.currency, today) }.map { it.amountMinor })
        return TipFacts(
            cashMinor = cash,
            avgSpendMinor = averageSavingMinor(spent),
            split = holdings?.let { investableSplit(it.items) },
            saveMinor = averageSavingMinor(saved),
            boughtAssetLately = bought,
            investIncomeThisYearMinor = returns,
        )
    }
}
