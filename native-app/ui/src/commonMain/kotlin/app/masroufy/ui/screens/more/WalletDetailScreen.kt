package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.currencyName
import app.masroufy.core.currencySymbol
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LoadTransactionsScreenRequest
import app.masroufy.usecase.ReconcileOutcome
import app.masroufy.usecase.WithYouNow
import kotlinx.coroutines.launch

/**
 * تفاصيل المحفظة (`WalletDetail`): الرصيد (أو «غير متاح») · النوع والعملة ورصيد البداية · «المحفظة الأساسية» (نقطة ربط [MainWalletAccess] —
 * بتتبني على `assistant-engine`) · «تعديل رصيد البداية» (`WalletAddSheet` — نقطة ربط) · آخر حركات الشهر المالي · مطابقة الرصيد للبنوك
 * (`ReconcileBalance` — اختيارية، والنتيجة بأرقامها من حالة الاستخدام).
 */
@Composable
fun WalletDetailScreen(walletId: Id) {
    val deps = LocalSpace.current
    val more = deps.more
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var now by remember(deps) { mutableStateOf<WithYouNow?>(null) }
    var moves by remember(deps) { mutableStateOf<List<WalletMove>?>(null) }
    var mainId by remember(deps) { mutableStateOf<Id?>(null) }
    var payday by remember(deps) { mutableStateOf(28) }
    var rec by remember { mutableStateOf<RecState>(RecState.Idle) }
    var editing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(deps, tick) {
        val today = deps.shell.today()
        now = runCatching { more.wallets.load(today) }.getOrNull()
        payday = runCatching { more.profile.load().payday }.getOrDefault(payday)
        moves = runCatching { walletMoves(more.transactions.load(LoadTransactionsScreenRequest(today = today, payday = payday)), walletId) }.getOrNull()
        mainId = runCatching { more.mainWallet?.current() }.getOrNull()
    }
    val n = now
    val w = n?.let { findWallet(it, walletId) }
    InnerScaffold(t(TextKey.WDET_TITLE)) {
        if (n == null) {
            item(key = "sk") { Skeleton(Modifier.fillMaxWidth().height(160.dp), radius = 26.dp, strong = true) }
            return@InnerScaffold
        }
        if (w == null) {
            item(key = "gone") { EmptyState(t(TextKey.WDET_GONE)) }
            return@InnerScaffold
        }
        val row = walletRow(w, mainId)
        item(key = "hero") {
            HeroCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(row.name, Modifier.weight(1f), style = Type.of(18, FontWeight.Bold).copy(color = Ink.onPrimary))
                        if (row.last4 != null) BasicText("•••• " + row.last4, style = Type.of(13).copy(color = Ink.onHeroMuted, textDirection = TextDirection.Ltr))
                    }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(t(TextKey.WDET_BALANCE), style = Type.of(13).copy(color = Ink.onHeroMuted))
                        if (row.approx) Badge(t(TextKey.BADGE_APPROX), BadgeKind.APPROX)
                    }
                    HeroAmount(row.balanceMinor, n.currency, Modifier.fillMaxWidth(), size = 30)
                    BasicText(
                        t(
                            when {
                                row.balanceMinor == null -> TextKey.WDET_SUB_NA
                                row.isCash -> TextKey.WDET_SUB_CASH
                                else -> TextKey.WDET_SUB_BANK
                            },
                        ),
                        style = Type.caption().copy(color = Ink.onHeroMuted),
                    )
                }
            }
        }
        item(key = "facts") {
            GroupCard(horizontal = 16.dp) {
                FactRow(t(TextKey.WDET_KIND), t(row.kindLabel), last = false)
                FactRow(t(TextKey.WDET_CURRENCY), t(TextKey.SPACE_SUB, currencyName(n.currency), currencySymbol(n.currency)), last = false)
                FactRow(t(TextKey.WDET_OPENING), t(TextKey.WDET_OPENING_VALUE, amountLabel(row.openingMinor, n.currency), fullDate(row.openingAt) ?: row.openingAt), last = true)
            }
        }
        item(key = "main") {
            MainWalletCard(row.isMain, more.mainWallet != null, mainName = n.wallets.firstOrNull { it.wallet.id == mainId }?.wallet?.name) {
                scope.launch {
                    runCatching { more.mainWallet?.set(walletId) }.onSuccess { toaster.show(t(TextKey.WDET_MAIN_DONE, row.name), dark = true); tick++ }
                }
            }
        }
        item(key = "opening") { TonalButton(t(TextKey.WADD_EDIT_OPEN), onClick = { editing = true }, modifier = Modifier.fillMaxWidth(), height = 48.dp) }
        item(key = "moves") { MovesCard(moves, n) }
        item(key = "rec") {
            if (row.isCash) NoteBox(t(TextKey.WDET_CASH_NOTE))
            else ReconcileCard(rec) {
                rec = RecState.Running
                scope.launch {
                    rec = runCatching { more.reconcile.run(walletId, deps.shell.today(), payday) }.fold({ RecState.Done(it) }, { RecState.Failed })
                }
            }
        }
    }
    if (w != null) WalletAddSheet(editing, WalletSheetMode.Edit(w.wallet), existing = emptyList(), onClose = { editing = false }, onSaved = { tick++ })
}

@Composable
private fun MainWalletCard(isMain: Boolean, editable: Boolean, mainName: String?, onMake: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(t(if (isMain) TextKey.WDET_MAIN_IS else TextKey.WDET_MAIN_TITLE), style = Type.of(14, FontWeight.Bold))
                    val sub = when {
                        isMain -> t(TextKey.WDET_MAIN_IS_SUB)
                        mainName != null -> t(TextKey.WDET_MAIN_NOW, mainName)
                        else -> t(TextKey.WDET_MAIN_UNSET)
                    }
                    BasicText(sub, style = Type.caption().copy(color = Ink.muted))
                }
                if (isMain) Badge(t(TextKey.WLIST_MAIN), BadgeKind.INFO)
                else TonalButton(t(TextKey.WDET_MAIN_MAKE), onClick = onMake, enabled = editable, height = 44.dp)
            }
            if (!editable) NotYetLine(t(TextKey.WDET_MAIN_NOT_YET))
        }
    }
}

@Composable
private fun MovesCard(moves: List<WalletMove>?, n: WithYouNow) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(t(TextKey.WDET_MOVES), Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
            BasicText(t(TextKey.WDET_MOVES_PERIOD), style = Type.caption().copy(color = Ink.muted))
        }
        GroupCard(horizontal = 16.dp) {
            when {
                moves == null -> Skeleton(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp))
                moves.isEmpty() -> BasicText(t(TextKey.WDET_NO_MOVES), Modifier.padding(vertical = 14.dp), style = Type.of(13).copy(color = Ink.muted))
                else -> moves.forEachIndexed { i, m ->
                    Column {
                        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                BasicText(m.title, style = Type.of(14, FontWeight.Bold), maxLines = 1)
                                BasicText(fullDate(m.date) ?: m.date, style = Type.caption().copy(color = Ink.muted))
                            }
                            AmountText(m.amountMinor, n.currency, tone = m.tone, showCurrency = false)
                        }
                        if (i != moves.lastIndex) RowRule()
                    }
                }
            }
        }
    }
}

/** مطابقة الرصيد: لسه ما اتشغلتش · بتشتغل · خلصت بنتيجتها · فشلت. */
sealed interface RecState {
    data object Idle : RecState
    data object Running : RecState
    data class Done(val outcome: ReconcileOutcome) : RecState
    data object Failed : RecState
}

@Composable
private fun ReconcileCard(state: RecState, onRun: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(TextKey.WDET_REC_TITLE), Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
                Badge(t(TextKey.WDET_REC_OPTIONAL), BadgeKind.NOT_AVAILABLE)
            }
            BasicText(t(TextKey.WDET_REC_BODY), style = Type.caption().copy(color = Ink.muted))
            when (state) {
                RecState.Running -> {
                    Skeleton(Modifier.fillMaxWidth(0.6f).height(22.dp), radius = 8.dp)
                    Skeleton(Modifier.fillMaxWidth().height(56.dp), radius = 14.dp)
                }
                is RecState.Done -> for (line in reconcileLines(state.outcome)) BasicText(line.text, style = line.style())
                RecState.Failed -> WarnBox(null, t(TextKey.WDET_REC_FAILED))
                RecState.Idle -> Unit
            }
            val noData = state is RecState.Done && state.outcome.result.checkedCount == 0
            if (noData) BasicText(t(TextKey.WDET_REC_NA), style = Type.caption().copy(color = Ink.muted))
            TonalButton(
                t(if (state is RecState.Done) TextKey.WDET_REC_AGAIN else TextKey.WDET_REC_RUN),
                onClick = onRun, enabled = state != RecState.Running, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** لون السطر: الحالة (أخضر مطابق · كهرماني فرق) · الأرقام كهرماني · الباقي رمادي. */
@Composable
private fun RecLine.style() = when (kind) {
    RecLineKind.OK -> Type.of(16, FontWeight.Bold).copy(color = Ink.income)
    RecLineKind.GAP_STATUS -> Type.of(16, FontWeight.Bold).copy(color = Ink.focus)
    RecLineKind.GAP_DETAIL -> Type.of(13)
    RecLineKind.GAP_AMOUNTS -> Type.of(13, FontWeight.Bold).copy(color = Ink.focus)
    RecLineKind.NOTE -> Type.caption().copy(color = Ink.muted)
}
