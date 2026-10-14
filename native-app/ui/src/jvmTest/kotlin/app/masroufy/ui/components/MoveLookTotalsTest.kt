package app.masroufy.ui.components

import app.masroufy.core.ArabicVariant
import app.masroufy.core.CashMovement
import app.masroufy.core.CashSummary
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Language
import app.masroufy.core.PeriodTotals
import app.masroufy.core.Texts
import app.masroufy.core.cashMovement
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.summarizeCash
import app.masroufy.core.walletBalancesOn
import app.masroufy.ui.screens.home.cashViewOf
import app.masroufy.ui.screens.more.walletMoves
import app.masroufy.ui.screens.operations.Fx
import app.masroufy.ui.screens.operations.RowContext
import app.masroufy.ui.screens.operations.dayGroups
import app.masroufy.ui.screens.operations.detailView
import app.masroufy.ui.screens.operations.operationsView
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **عرض بس** (HANDOVER §6.5-10): شكل التحويل بين محافظك (اللون المحايد · رمز «منه فيه» · «من ← إلى») ما بيغيّرش ولا هللة في المجاميع
 * (الدخل · المصروف الحقيقي · حركة الفلوس) ولا أرصدة المحافظ ولا الكاش. المجاميع قبل كل دوال العرض = بعدها = الأرقام المتوقعة، والعمليات
 * نفسها ما اتلمستش، وكل مبلغ معروض = مبلغ العملية بالظبط (من غير قلب علامة). بالسعودية (فصحى) ومصر (مصري).
 */
class MoveLookTotalsTest {
    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val wallets = listOf(Fx.bank, Fx.cash)
    private val names: Map<Id, String> = wallets.associate { it.id to it.name }

    private val txs = listOf(
        Fx.tx("salary", "2026-10-01", 900_000, Direction.IN, EconomicKind.SALARY, category = null, merchant = null),
        Fx.tx("food", "2026-10-02", 4_200),
        Fx.tx("atm", "2026-10-03", 50_000, kind = EconomicKind.INTERNAL_TRANSFER, category = null, merchant = null, toWallet = Fx.cash.id),
        Fx.tx("leg-out", "2026-10-04", 70_000, kind = EconomicKind.INTERNAL_TRANSFER, category = null, merchant = "حوالة بين حساباتك"),
        Fx.tx("leg-in", "2026-10-05", 20_000, Direction.IN, EconomicKind.INTERNAL_TRANSFER, category = null, merchant = "حوالة واردة"),
        Fx.tx("gift", "2026-10-06", 20_000, kind = EconomicKind.SUPPORT_GIFT, category = null, merchant = "TEST PERSON"),
        Fx.tx("coffee", "2026-10-07", 1_500, wallet = Fx.cash.id),
    )

    private data class Totals(val period: PeriodTotals, val movement: CashMovement, val balances: Map<Id, Halalas?>, val cash: CashSummary)

    private fun totals() = Totals(
        computePeriodTotals(txs, emptyList()),
        cashMovement(txs),
        walletBalancesOn(wallets, txs, Fx.TODAY),
        summarizeCash(Fx.cash, txs, emptyList(), "2026-09-28", "2026-10-27"),
    )

    /** كل دوال العرض اللي اتغيرت — الصفوف والأيام والتفاصيل وسجل المحفظتين ولوحة الكاش وخانة العمليات. بترجّع كل مبلغ معروض بمعرّفه. */
    private fun renderEverything(before: Totals): List<Pair<String, Halalas>> {
        val ctx = RowContext(listOf(Fx.food), wallets = wallets)
        val shown = mutableListOf<Pair<String, Halalas>>()
        dayGroups(txs, ctx, Fx.TODAY).flatMap { it.rows }.forEach { shown += it.id to it.amountMinor }
        txs.forEach { shown += it.id to detailView(it, listOf(Fx.food), wallets, emptyList(), emptySet(), Fx.TODAY).amountMinor }
        val screen = Fx.screen(txs, before.period.incomeMinor, before.period.personalExpenseMinor)
        for (w in wallets) walletMoves(screen, w.id, limit = 50, walletNames = names).forEach { shown += it.id to it.amountMinor }
        cashViewOf(before.cash, names)!!.rows.forEach { shown += it.id to it.amountMinor }
        val ops = operationsView(screen, wallets, Fx.TODAY, Currency.SAR)
        assertEquals(before.period.incomeMinor, ops.incomeMinor, "خانة العمليات بتعرض الدخل زي ما هو")
        assertEquals(before.period.personalExpenseMinor, ops.expenseMinor, "والمصروف زي ما هو")
        return shown
    }

    @Test fun displayChangesNoTotalNoBalanceAndNoAmount() {
        for (variant in ArabicVariant.entries) {
            Texts.arabicVariant = variant
            val snapshot = txs.map { it.copy() }
            val before = totals()
            val shown = renderEverything(before)
            assertEquals(before, totals(), "$variant: المجاميع والأرصدة بعد العرض = قبله")
            assertEquals(snapshot, txs, "$variant: العمليات نفسها ما اتلمستش")
            val byId = txs.associate { it.id to it.amountMinor }
            for ((id, minor) in shown) assertEquals(byId.getValue(id), minor, "$variant: $id بيتعرض بمبلغه بالظبط")

            // الأرقام نفسها: التحويل بين محافظك برّه الدخل والمصروف وحركة الفلوس، وبيحرّك الأرصدة بس
            assertEquals(900_000L, before.period.incomeMinor)
            assertEquals(4_200L + 20_000L + 1_500L, before.period.personalExpenseMinor, "الصرف + الدعم + قهوة الكاش — من غير التحويلات")
            assertEquals(CashMovement(900_000, 4_200L + 20_000L + 1_500L), before.movement)
            assertEquals(100_000L + 900_000 - 4_200 - 50_000 - 70_000 + 20_000 - 20_000, before.balances[Fx.bank.id])
            assertEquals(20_000L + 50_000 - 1_500, before.balances[Fx.cash.id])
            assertEquals(68_500L, before.cash.balanceMinor)
            assertEquals(1_500L, before.cash.spentInPeriodMinor, "السحب للكاش مش صرف")
        }
    }
}
