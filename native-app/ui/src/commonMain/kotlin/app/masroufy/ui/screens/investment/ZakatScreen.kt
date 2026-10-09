package app.masroufy.ui.screens.investment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.NOT_AVAILABLE
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.HeroDivider
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.more.MoreRoute
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** «الزكاة» (لوحة `Zakat` + `ZakatFactsSheet` + `HawlDaySheet` + `ZakatPay`) — التحميل في [loadZakat]. */
@Composable
fun ZakatScreen() {
    val space = LocalSpace.current
    val deps = space.investment
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var load by remember(space) { mutableStateOf<ZakatLoad?>(null) }
    var failed by remember(space) { mutableStateOf<String?>(null) }
    var facts by remember { mutableStateOf(false) }
    var hawl by remember { mutableStateOf(false) }
    suspend fun reload() {
        try { load = loadZakat(deps, space.space); failed = null } catch (e: IllegalArgumentException) { failed = e.message }
    }
    fun act(block: suspend () -> String?) {
        scope.launch {
            val msg = try { block() } catch (e: IllegalArgumentException) { e.message } catch (e: IllegalStateException) { e.message }
            reload()
            msg?.let { toaster.show(it) }
        }
    }
    LaunchedEffect(space) { reload() }
    val l = load
    InnerScaffold(t(TextKey.ZAKAT_SCREEN_TITLE)) {
        when {
            failed != null -> item(key = "failed") { AlertBanner(t(TextKey.SHELL_LOAD_FAILED), failed, t(TextKey.SHELL_RETRY)) { scope.launch { reload() } } }
            l == null -> item(key = "loading") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(150.dp), radius = 28.dp, strong = true)
                    Skeleton(Modifier.fillMaxWidth().height(120.dp))
                    Skeleton(Modifier.fillMaxWidth().height(260.dp))
                }
            }
            !l.visible -> item(key = "hidden") {
                EmptyState(t(TextKey.ZAKAT_SCREEN_HIDDEN_TITLE), t(TextKey.ZAKAT_SCREEN_HIDDEN_BODY), action = {
                    TonalButton(t(TextKey.ZAKAT_SCREEN_HIDDEN_ACTION), { nav.push(MoreRoute) }, Modifier.padding(top = 8.dp))
                })
            }
            else -> {
                val ui = l.ui!!
                val data = l.data!!
                item(key = "ref") { ReferenceBox(ui) }
                item(key = "hero") { ZakatHero(ui) }
                item(key = "hawl") {
                    HawlCardView(ui.hawl, onConfirm = {
                        act { deps.zakat.confirmDate(data.suggestion!!.hawlStart); t(TextKey.ZAKAT_SCREEN_CONFIRMED_TOAST) }
                    }, onChange = { hawl = true })
                }
                if (l.questions.isNotEmpty()) item(key = "facts") { FactsCard(factsSummary(l.questions)) { facts = true } }
                item(key = "lines") { LinesSection(ui) }
                if (ui.outs.isNotEmpty()) item(key = "outs") { OutsSection(ui) }
                if (ui.hasYear) item(key = "close") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrimaryButton(t(TextKey.ZAKAT_SCREEN_CLOSE), {
                            act { deps.zakat.close(data.openYear!!.id, deps.today(), data.prices); t(TextKey.ZAKAT_SCREEN_CLOSED_TOAST) }
                        }, Modifier.fillMaxWidth(), enabled = ui.canClose)
                        BasicText(t(if (ui.canClose) TextKey.ZAKAT_SCREEN_CLOSE_NOTE else TextKey.ZAKAT_CANNOT_CLOSE), Modifier.fillMaxWidth(),
                            style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center))
                    }
                }
                l.pay?.let { pay -> item(key = "pay") { ZakatPaySection(pay, ui.currency) { scope.launch { reload() } } } }
                item(key = "guidance") {
                    BasicText(t(TextKey.ZAKAT_GUIDANCE), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center))
                }
            }
        }
    }
    ZakatFactsSheet(facts, l?.questions.orEmpty(), onDismiss = { facts = false }) { subject, answer ->
        act {
            when (answer) {
                is FactAnswer.Purpose -> deps.zakat.setAssetFacts(subject, purpose = answer.value)
                is FactAnswer.Holding -> deps.zakat.setAssetFacts(subject, holding = answer.value)
                is FactAnswer.Saudi -> deps.zakat.setAssetFacts(subject, saudiCompany = answer.value)
                is FactAnswer.Karat -> deps.zakat.setAssetFacts(subject, karat = answer.value)
                is FactAnswer.Fineness -> deps.zakat.setAssetFacts(subject, fineness = answer.value)
                is FactAnswer.Collect -> deps.setReceivableFact(subject, answer.value)
            }
            null
        }
    }
    HawlDaySheet(hawl, l?.data?.let { it.openYear?.dueAt ?: it.suggestion?.dueAt }, onDismiss = { hawl = false })
}

@Composable
private fun ReferenceBox(ui: ZakatUi) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(ui.reference, style = Type.of(13, FontWeight.Bold))
        BasicText(t(TextKey.ZAKAT_SCREEN_REF_BODY), style = Type.caption().copy(color = Ink.soft))
        ui.scopeNote?.let { BasicText(it, style = Type.captionBold().copy(color = Ink.focus)) }
    }
}

@Composable
private fun ZakatHero(ui: ZakatUi) {
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(TextKey.ZAKAT_SCREEN_HERO), Modifier.weight(1f), style = Type.body().copy(color = Ink.onHeroMuted))
                ui.chip?.let { chip ->
                    val (ink, bg) = when (chip) {
                        OutcomeChip.DUE -> Ink.heroStart to Ink.mint
                        OutcomeChip.BELOW -> Ink.heroStart to Ink.selected
                        else -> Ink.focus to Ink.amber
                    }
                    ToneChip(t(chip.key), ink, bg)
                }
            }
            if (ui.heroMinor == null) BasicText(NOT_AVAILABLE, style = Type.of(30, FontWeight.Bold).copy(color = Color.White))
            else HeroAmount(ui.heroMinor, ui.currency, Modifier.fillMaxWidth(), size = 32)
            BasicText(ui.heroSub, style = Type.caption().copy(color = Ink.onHeroMuted))
            if (ui.hasYear) {
                HeroDivider(Modifier.padding(top = 2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(t(TextKey.ZAKAT_SCREEN_NISAB), Modifier.weight(1f), style = Type.caption().copy(color = Ink.onHeroMuted))
                    AmountText(ui.nisabMinor, ui.currency, size = 12, color = Color.White)
                }
                BasicText(ui.nisabRule, style = Type.of(11).copy(color = Ink.mint))
            }
            ui.pricesLine?.let { BasicText(it, style = Type.of(11).copy(color = Ink.mint)) }
        }
    }
}

@Composable
private fun HawlCardView(h: HawlCard, onConfirm: () -> Unit, onChange: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(TextKey.ZAKAT_SCREEN_HAWL), Modifier.weight(1f), style = Type.of(13).copy(color = Ink.muted))
                if (h.dateLine != null) {
                    if (h.confirmed) ToneChip(t(TextKey.ZAKAT_SCREEN_CONFIRMED), Ink.income, Ink.selected)
                    else ToneChip(t(TextKey.ZAKAT_SCREEN_SUGGESTED), Ink.focus, Ink.alertBg)
                }
            }
            BasicText(h.dateLine ?: NOT_AVAILABLE, style = Type.of(17, FontWeight.Bold).copy(color = if (h.dateLine == null) Ink.muted else Ink.text))
            h.gregorian?.let { BasicText(it, style = Type.caption().copy(color = Ink.muted)) }
            BasicText(h.why, style = Type.caption().copy(color = Ink.soft))
            if (h.rule.isNotEmpty()) BasicText(h.rule, style = Type.of(11).copy(color = Ink.muted))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (h.canConfirm) PrimaryButton(t(TextKey.ZAKAT_SCREEN_CONFIRM), onConfirm, Modifier.weight(1f))
                // «غيّره ليوم هجري» بيفضل ظاهر بعد «أكّده» (رد المالك §76 — 2026-10-09)
                TonalButton(t(TextKey.HAWL_OPEN), onChange, Modifier.weight(1f))
            }
        }
    }
}
