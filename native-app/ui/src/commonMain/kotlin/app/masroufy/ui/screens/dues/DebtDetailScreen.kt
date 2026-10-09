package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.DebtTerms
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Confetti
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «تفاصيل الدين» (`DebtDetail`): البطاقة البترولية بالمتبقي · أصل الدين · الموعد (حدّد · غيّر · إزالة — `setDebtTerms`/`clearDebtTerms`) ·
 * «هل فيها فوائد؟» · «سجّل تحصيلًا/سدادًا» ([SettleForm]) · «دين قديم مع X» ([OpeningDebtBody]). التسوية بالكامل = قصاصات ملونة مرة واحدة.
 * مفيش زرار «ذكّره» (قرار المالك: أي تذكير = إشعار في الجرس).
 */
@Composable
fun DebtDetailScreen(obligationId: String) {
    val space = LocalSpace.current
    val deps = space.dues
    var last by remember(obligationId) { mutableStateOf<DebtDetailUi?>(null) }
    var celebrate by remember(obligationId) { mutableStateOf(false) }
    val load = rememberLoad(deps, obligationId) {
        val today = space.shell.today()
        debtDetailUi(deps.people.listWithBalances(), debtDueItems(deps, today), obligationId, today)
    }
    val ready = (load.value as? Load.Ready)?.value
    LaunchedEffect(ready) { if (ready != null) last = ready }
    val sideChip = (ready ?: last)?.let { t(if (it.forYou) TextKey.DUES_FOR_YOU else TextKey.DUES_ON_YOU) }
    DuesScaffold(t(TextKey.DEBT_TITLE), actions = { sideChip?.let { StatusChip(it, if ((ready ?: last)?.forYou == true) Chip.UPCOMING else Chip.OVERDUE) } }) {
        when (val s = load.value) {
            Load.Loading -> item { LoadingBlocks(180, 240) }
            Load.Failed -> item { LoadFailed(load::reload) }
            is Load.Ready -> {
                // اختفى من القايمة النشطة بعد ما اتسدد هنا ⇒ «سُدّد بالكامل»؛ اتفتح وهو مش موجود ⇒ جملة صريحة
                val ui = s.value ?: last?.settledFully()
                if (ui == null) item { EmptyState(t(TextKey.DEBT_NOT_OPEN)) } else debtBody(ui, celebrate) { celebrate = it }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.debtBody(ui: DebtDetailUi, celebrate: Boolean, onCelebrate: (Boolean) -> Unit) {
    item(key = "hero") { Hero(ui, celebrate) }
    item(key = "origin") { Origin(ui) }
    if (!ui.done) item(key = "terms") { Terms(ui) }
    item(key = "settle") { SettleAction(ui, onCelebrate) }
    item(key = "opening") { OpeningCard(ui) }
}

@Composable
private fun Hero(ui: DebtDetailUi, celebrate: Boolean) {
    Box {
        HeroCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Ink.surface), contentAlignment = Alignment.Center) {
                        BasicText(ui.initial, style = Type.of(17, FontWeight.Bold).copy(color = Ink.primary))
                    }
                    Column {
                        BasicText(ui.personName, style = Type.of(16, FontWeight.Bold).copy(color = Color.White))
                        BasicText(ui.kindLine, style = Type.caption().copy(color = Ink.onHeroMuted))
                    }
                }
                BasicText(ui.remainLabel, style = Type.of(13).copy(color = Ink.onHeroMuted))
                HeroAmount(ui.remainingMinor, ui.currency, Modifier.fillMaxWidth(), size = 34)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(if (ui.done) t(TextKey.DEBT_DONE) else ui.ofOriginal, Modifier.weight(1f), style = Type.caption().copy(color = Ink.onHeroMuted))
                    if (ui.signal != null && ui.signalText != null) StatusChip(ui.signalText, ui.signal, onHero = true)
                }
            }
        }
        if (celebrate) Confetti(Modifier.fillMaxWidth().height(160.dp), key = ui.obligationId)
    }
}

@Composable
private fun Origin(ui: DebtDetailUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(TextKey.DEBT_ORIGIN_TITLE), style = Type.section())
        CardList {
            StackedRow(t(TextKey.DEBT_ORIGIN_LABEL), ui.originHint, chip = if (ui.opening) ({ StatusChip(t(TextKey.DEBT_ORIGIN_OLD_CHIP), Chip.SOON) }) else null) {
                ValueText(ui.originValue)
            }
            Divider()
            StackedRow(t(TextKey.DEBT_ORIGINAL)) { AmountText(ui.originalMinor, ui.currency) }
            Divider()
            StackedRow(t(TextKey.DEBT_TYPE), ui.typeHint) { ValueText(ui.typeText) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Terms(ui: DebtDetailUi) {
    val deps = LocalSpace.current.dues
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable(ui.obligationId) { mutableStateOf(false) }
    var interest by rememberSaveable(ui.obligationId) { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(done: String, block: suspend () -> Unit) = scope.launch {
        try {
            block()
            toaster.show(done)
            editing = false
            error = null
            DuesChanges.bump()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = failText(e)
        }
    }
    fun save(date: String, hasInterest: Boolean?) = DebtTerms(ui.obligationId, ui.personId, firstDueAt = date, hasInterest = hasInterest)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(TextKey.DEBT_TERMS_TITLE), style = Type.section())
        FloatingCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(ui.termsValue, style = Type.bodyBold())
                        if (ui.signal != null && ui.signalText != null) StatusChip(ui.signalText, ui.signal)
                    }
                    if (ui.dueAt == null) PrimaryButton(t(TextKey.DEBT_TERMS_SET), { editing = !editing }, height = 44.dp)
                    else {
                        TonalButton(t(TextKey.DEBT_TERMS_CHANGE), { editing = !editing }, height = 44.dp)
                        TonalButton(t(TextKey.DEBT_TERMS_REMOVE), { run(t(TextKey.DEBT_TERMS_REMOVED)) { deps.installments.clearDebtTerms(ui.obligationId) } }, height = 44.dp)
                    }
                }
                if (editing) {
                    BasicText(t(TextKey.DEBT_TERMS_PICK), style = Type.caption().copy(color = Ink.muted))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (o in ui.termOptions) SelectChip(t(TextKey.DUES_COMMA_JOIN, o.label, o.rel), o.date == ui.dueAt, {
                            run(t(TextKey.DEBT_TERMS_SAVED, o.label)) { deps.installments.setDebtTerms(save(o.date, interest)) }
                        }, height = 44.dp)
                    }
                }
                if (ui.dueAt != null) {
                    Divider()
                    InterestQuestion(interest) { v ->
                        interest = v
                        run(t(TextKey.DEBT_INTEREST_SAVED)) { deps.installments.setDebtTerms(save(ui.dueAt, v)) }
                    }
                }
                error?.let { app.masroufy.ui.components.FieldError(it) }
            }
        }
    }
}

@Composable
private fun SettleAction(ui: DebtDetailUi, onCelebrate: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PrimaryButton(
            t(if (ui.forYou) TextKey.SETTLE_TRIGGER_FOR else TextKey.SETTLE_TRIGGER_ON), { open = true },
            Modifier.fillMaxWidth(), enabled = !ui.done, height = 52.dp,
        )
        if (ui.done) BasicText(t(TextKey.SETTLE_DONE_WHY), style = Type.caption().copy(color = Ink.muted))
    }
    Sheet(open, { open = false }, t(TextKey.SETTLE_SHEET_TITLE_GENERIC), closeLabel = t(TextKey.SHELL_CLOSE)) {
        SettleForm(ui.settleTarget()) { full ->
            open = false
            if (full) onCelebrate(true)
        }
    }
}

@Composable
private fun OpeningCard(ui: DebtDetailUi) {
    val nav = LocalNavigator.current
    var open by remember { mutableStateOf(false) }
    FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(t(TextKey.OPENING_DEBT_CARD_TITLE, ui.personName), style = Type.bodyBold())
                BasicText(t(TextKey.OPENING_DEBT_CARD_BODY), style = Type.caption().copy(color = Ink.muted))
            }
            TonalButton(t(TextKey.OPENING_DEBT_OPEN), { open = true }, height = 44.dp)
        }
    }
    Sheet(open, { open = false }, t(TextKey.OPENING_DEBT_TITLE, ui.personName), closeLabel = t(TextKey.SHELL_CLOSE)) {
        OpeningDebtBody(ui.personId, ui.personName) { o ->
            open = false
            nav.replace(DebtDetailRoute(o.id))
        }
    }
}
