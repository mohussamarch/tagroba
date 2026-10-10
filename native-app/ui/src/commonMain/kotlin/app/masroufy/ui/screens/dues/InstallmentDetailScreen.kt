package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.IconButton44
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «تفاصيل الخطة» (`InstallmentDetail`): البطاقة البترولية (المتبقي · القسط والإجمالي · دفعت N من M · القادم) · الجدول (اتدفع · متأخر ·
 * القادم · متبقٍ) · المبلغ المستلم (فك الربط — `ManageInstallments.unlink`) · أرباح التمويل للعلم (الأجزاء المحسوبة «غير متاح» — مش في
 * حالة الاستخدام) · رمز القلم ⇒ `InstallmentEdit`.
 */
@Composable
fun InstallmentDetailScreen(planId: String) {
    val space = LocalSpace.current
    val deps = space.dues
    val nav = LocalNavigator.current
    val load = rememberLoad(deps, planId) {
        val today = space.shell.today()
        deps.installments.list(today).firstOrNull { it.plan.id == planId }?.let { planDetailUi(it, today) }
    }
    val ui = (load.value as? Load.Ready)?.value
    DuesScaffold(
        ui?.card?.name ?: t(TextKey.INSTALLMENTS_TITLE), ui?.card?.kindText,
        actions = { IconButton44(Lucide.PENCIL, t(TextKey.INST_EDIT), { nav.push(InstallmentEditRoute(planId)) }) },
    ) {
        when (val s = load.value) {
            Load.Loading -> item { LoadingBlocks(170, 220) }
            Load.Failed -> item { LoadFailed(load::reload) }
            is Load.Ready -> {
                val d = s.value
                if (d == null) {
                    item { EmptyState(t(TextKey.INSTALLMENT_NOT_FOUND)) }
                    return@DuesScaffold
                }
                item(key = "hero") { PlanHero(d) }
                item(key = "schedule") { Schedule(d) }
                item(key = "received") { Received(d) }
                if (d.card.financing) item(key = "profit") { Profit(d) }
                item(key = "note") { BasicText(d.expenseNote, style = Type.caption().copy(color = Ink.muted)) }
            }
        }
    }
}

@Composable
private fun PlanHero(d: PlanDetailUi) {
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(d.leftLabel, style = Type.body().copy(color = Ink.onHeroMuted))
            HeroAmount(d.leftMinor, d.card.currency, Modifier.fillMaxWidth(), size = 34)
            BasicText(d.instLine, style = Type.caption().copy(color = Ink.onHeroMuted))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(d.card.progressText, style = Type.caption().copy(color = Color.White, fontWeight = FontWeight.Bold))
                BasicText(d.nextText, style = Type.caption().copy(color = if (d.card.late) Ink.amber else Ink.onHeroMuted))
            }
            CountBar(d.card.paidCount, d.card.count, onHero = true)
        }
    }
}

@Composable
private fun Schedule(d: PlanDetailUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(TextKey.INST_SCHEDULE), style = Type.section())
        FloatingCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = d.schedAria }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (y in d.years) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(y.label, style = Type.caption().copy(color = Ink.muted))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until 12) Box(Modifier.weight(1f)) { if (i < y.cells.size) CellBox(y.cells[i], Modifier.fillMaxWidth().aspectRatio(1f)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    for ((cell, key) in listOf(Cell.PAID to TextKey.INST_LEG_PAID, Cell.LATE to TextKey.INST_LEG_LATE, Cell.NEXT to TextKey.INST_LEG_NEXT, Cell.UP to TextKey.INST_LEG_UP)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CellBox(cell, Modifier.size(12.dp))
                            BasicText(t(key), style = Type.of(11).copy(color = Ink.muted))
                        }
                    }
                }
                BasicText(d.lastLine, style = Type.caption().copy(color = Ink.muted))
            }
        }
    }
}

@Composable
private fun CellBox(cell: Cell, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val m = when (cell) {
        Cell.PAID -> Modifier.clip(shape).background(Ink.primary)
        Cell.LATE -> Modifier.clip(shape).background(Ink.alertBg).insetRing(shape, 1.5.dp, Ink.focus)
        Cell.NEXT -> Modifier.clip(shape).background(Color.White).insetRing(shape, 2.dp, Ink.primary)
        Cell.UP -> Modifier.clip(shape).background(Color(0x14193D33))
    }
    Box(modifier.then(m))
}

@Composable
private fun Received(d: PlanDetailUi) {
    val deps = LocalSpace.current.dues
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val c = d.card
    FloatingCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(t(TextKey.INSTALLMENTS_RECEIVED), style = Type.bodyBold())
                val sub = when {
                    !c.financing -> t(TextKey.INST_RECV_NA_SUB)
                    c.receivedMinor == null -> t(TextKey.INST_RECV_HINT)
                    else -> null
                }
                if (sub != null) BasicText(sub, style = Type.caption().copy(color = Ink.muted))
            }
            when {
                !c.financing -> ValueText(t(TextKey.INST_RECV_NA), muted = true)
                c.receivedMinor == null -> ValueText(t(TextKey.INST_NOT_LINKED), color = Ink.focus)
                else -> AmountText(c.receivedMinor, c.currency, color = Ink.income)
            }
        }
        if (d.receivedTransactionId != null) TonalButton(t(TextKey.INST_UNLINK), { ask = true }, Modifier.fillMaxWidth(), height = 44.dp)
        error?.let { FieldError(it) }
    }
    ConfirmSheet(ask, t(TextKey.INST_UNLINK_RECV_TITLE), t(TextKey.DUES_UNLINK_BODY), t(TextKey.DUES_UNLINK_YES), onConfirm = {
        val txn = d.receivedTransactionId ?: return@ConfirmSheet
        ask = false
        scope.launch {
            try {
                deps.installments.unlink(txn)
                toaster.show(t(TextKey.DUES_UNLINKED))
                DuesChanges.bump()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = failText(e)
            }
        }
    }, onDismiss = { ask = false })
}

@Composable
private fun Profit(d: PlanDetailUi) {
    val c = d.card
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(t(TextKey.INST_PROFIT_TITLE), style = Type.section())
            StatusChip(t(TextKey.INST_INFO_ONLY), Chip.SOON)
        }
        CardList {
            LabelValue(t(TextKey.INST_TOTAL_PAY)) { AmountText(d.totalMinor, c.currency) }
            Divider()
            LabelValue(t(TextKey.INST_PRINCIPAL)) { AmountText(d.principalMinor, c.currency) }
            for (key in listOf(TextKey.INST_PROFIT, TextKey.INST_PROFIT_PAID, TextKey.INST_PROFIT_LEFT, TextKey.INST_PROFIT_PER)) {
                Divider()
                // الأجزاء دي محتاجة حسبة تكلفة التمويل للخطة — مش في حالة الاستخدام ⇒ «غير متاح» (القاعدة 10)
                LabelValue(t(key)) { AmountText(null, c.currency) }
            }
        }
    }
}
