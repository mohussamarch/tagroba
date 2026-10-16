package app.masroufy.ui.screens.dues

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.DueItem
import app.masroufy.core.DuesTotals
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.overlay.QuickMenu
import app.masroufy.ui.overlay.QuickMenuHost
import app.masroufy.ui.overlay.QuickTarget
import app.masroufy.ui.overlay.rememberQuickMenu
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.PersonRow

/** اللي الشاشة بتحمّله مرة واحدة — والفلتر والبحث بيتطبقوا عليه من غير ما يحمّل تاني. */
internal class DebtsData(val people: List<PersonRow>, val totals: DuesTotals, val dues: List<DueItem>, val today: IsoDate)

/**
 * «الديون» (`DuesDebts` — من مربع «الديون» وكارتي «لك/عليك» في المستحقات): الكارتين فوق بيصفّوا (الضغط تاني يرجّع الكل) · بحث بالاسم ·
 * «لك» الأهم أولًا (فات موعده ثم الأقرب) · «عليك» بالموعد · الأمانات لوحدها · كل دين بيفتح تفاصيله. مفيش زرار تذكير (قرار المالك — التذكير إشعار).
 */
@Composable
fun DuesDebtsScreen(initialSide: DebtSide) {
    val space = LocalSpace.current
    val deps = space.dues
    val nav = LocalNavigator.current
    val quick = rememberQuickMenu()
    var side by rememberSaveable { mutableStateOf(initialSide) }
    var query by rememberSaveable { mutableStateOf("") }
    val load = rememberLoad(deps) {
        val today = space.shell.today()
        val view = deps.loadDues.load(today, deps.period(today), space.space.currency, null)
        DebtsData(deps.people.listWithBalances(), view.totals, debtDueItems(deps, today), today)
    }
    // «امسح» من القايمة السريعة (§6.5 بند 12 + 13): التأكيد في [DebtEraseSheet] و«تراجع» في شريط فوق القايمة 8 ثواني
    var erasing by remember { mutableStateOf<DebtRowUi?>(null) }
    var erased by remember { mutableStateOf<DebtErased?>(null) }
    val toaster = app.masroufy.ui.shell.LocalToaster.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(erased) { if (erased != null) kotlinx.coroutines.delay(8_000).also { erased = null } }
    val onDelete: ((DebtRowUi) -> Unit)? = deps.deleteDebt?.let { { row -> erasing = row } }
    fun undoErase(e: DebtErased) {
        val erase = deps.deleteDebt ?: return
        erased = null
        scope.launch {
            val failed = try { erase.restore(e.removal); null } catch (x: kotlin.coroutines.cancellation.CancellationException) { throw x } catch (x: Exception) { x.message }
            toaster.show(failed ?: t(UiKey.ERASE_RESTORED))
            load.reload()
        }
    }
    DuesScaffold(t(UiKey.DEBTS_TITLE), t(UiKey.DEBTS_SUB)) {
        erased?.let { e -> item(key = "erase-undo") { app.masroufy.ui.screens.operations.UndoStrip(e.text) { undoErase(e) } } }
        when (val s = load.value) {
            Load.Loading -> item { LoadingBlocks() }
            Load.Failed -> item { LoadFailed(load::reload) }
            is Load.Ready -> {
                val d = s.value
                val ui = duesDebtsUi(d.people, d.totals, d.dues, d.today, space.space.currency, side, query)
                if (ui.empty) {
                    item {
                        EmptyState(t(UiKey.DEBTS_EMPTY_TITLE), t(UiKey.DEBTS_EMPTY_BODY), action = {
                            PrimaryButton(t(UiKey.DEBTS_EMPTY_GO), { nav.switchTab(Tab.PEOPLE) })
                        })
                    }
                    return@DuesScaffold
                }
                // r05: العمليات اللي تصنيفها فلوس مع شخص ومش مربوطة بحد — ضغطة ⇒ «مع مين؟»
                item(key = "needs-person") { app.masroufy.ui.screens.people.NeedsPersonCard() }
                item(key = "sides") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (sd in ui.sides) SideFilter(Modifier.weight(1f), sd, side == sd.side, ui.currency) { side = if (side == sd.side) DebtSide.ALL else sd.side }
                    }
                }
                item(key = "netting") { BasicText(t(UiKey.DUES_NO_NETTING), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center)) }
                item(key = "search") {
                    TextInput(
                        query, { query = it }, placeholder = t(UiKey.DEBTS_SEARCH_HINT), imeAction = ImeAction.Search, height = 52.dp,
                        trailing = { if (query.isNotEmpty()) ClearButton { query = "" } },
                    )
                }
                for (g in ui.groups) item(key = "g-${g.key}") { Group(g, ui.currency, quick, onDelete) { nav.push(DebtDetailRoute(it)) } }
                if (ui.noHitsTitle != null) item(key = "nohits") {
                    EmptyState(ui.noHitsTitle, ui.noHitsBody, action = {
                        TonalButton(t(UiKey.DUES_SHOW_ALL), { query = ""; side = DebtSide.ALL }, height = 44.dp)
                    })
                }
                item(key = "foot") { BasicText(t(UiKey.DEBTS_FOOT), style = Type.caption().copy(color = Ink.muted)) }
            }
        }
    }
    QuickMenuHost(quick)
    DebtEraseSheet(erasing, deps.deleteDebt, onDismiss = { erasing = null }) { e ->
        erasing = null
        erased = e
        load.reload()
    }
}

@Composable
private fun SideFilter(modifier: Modifier, sd: DebtSideUi, on: Boolean, currency: Currency, onClick: () -> Unit) {
    val forYou = sd.side == DebtSide.FOR_YOU
    val shape = RoundedCornerShape(22.dp)
    FloatingCard(
        modifier.then(if (on) Modifier.insetRing(shape, 2.dp, if (forYou) Ink.income else Ink.expense) else Modifier),
        contentPadding = PaddingValues(14.dp), onClick = onClick, clickLabel = sd.label,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(sd.label, style = Type.bodyBold())
                BasicText(sd.count, style = Type.of(11).copy(color = Ink.muted))
            }
            AmountText(sd.totalMinor, currency, size = 20, color = if (forYou) Ink.income else Ink.expense)
            BasicText(sd.hint, style = Type.of(11).copy(color = Ink.muted))
        }
    }
}

@Composable
private fun Group(g: DebtGroupUi, currency: Currency, quick: QuickMenu, onDelete: ((DebtRowUi) -> Unit)?, onOpen: (String) -> Unit) {
    val nav = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(g.title, style = Type.of(17, FontWeight.Bold))
            if (g.totalMinor != null) AmountText(g.totalMinor, currency, color = if (g.forYou) Ink.income else Ink.expense)
        }
        BasicText(g.order, style = Type.caption().copy(color = Ink.muted))
        CardList {
            g.rows.forEachIndexed { i, row ->
                if (i > 0) Divider()
                QuickTarget(quick, row.name, { debtQuickItems(row, nav, onDelete) }, subtitle = row.reason) { long ->
                    DebtRow(row, onLongClick = long, onClick = quick.guardTap { onOpen(row.obligationId) })
                }
            }
        }
    }
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    val press = rememberPress()
    val label = t(UiKey.DEBTS_SEARCH_CLEAR)
    Box(
        Modifier.size(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).tap(press, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(Lucide.X, size = 18.dp, tint = Ink.muted) }
}
