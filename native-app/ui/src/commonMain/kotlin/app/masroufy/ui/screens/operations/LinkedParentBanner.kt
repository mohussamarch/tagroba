package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.LinkRefusal
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.home.transactionTitle
import app.masroufy.ui.screens.imports.PanelTitle
import app.masroufy.ui.screens.imports.PanelTone
import app.masroufy.ui.screens.imports.QuietButton
import app.masroufy.ui.screens.imports.TintedPanel
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LinkInflows
import app.masroufy.usecase.LinkedInflowsView
import app.masroufy.usecase.UnlinkUndo

/**
 * r041 A11 — شريط «مربوطة بـ «…»» أول تفاصيل الداخلة التابعة: اسم المصروف (نفس اسم العرض — A4) ومبلغه وتاريخه، والشريط كله بيفتح المصروف.
 * المصروف اتمسح ⇒ «المصروف حُذف» ومش بيتفتح. [expenseId] = null ⇒ مش بيتداس.
 */
internal data class LinkedBanner(val title: String?, val expenseId: Id?, val amountMinor: Long?, val currency: Currency?, val day: String?) {
    val gone: Boolean get() = expenseId == null
    val textKey: UiKey get() = if (gone) UiKey.LINKED_BANNER_GONE else UiKey.LINKED_BANNER
}

internal fun linkedBanner(v: LinkedInflowsView.Inflow): LinkedBanner {
    val e = v.expense ?: return LinkedBanner(null, null, null, null, null)
    return LinkedBanner(transactionTitle(e), e.id, e.amountMinor, e.currency, e.occurredAt.take(10))
}

/** «عمليات تابعة» بتتحمّل مرة واحدة للصفحة (الشريط فوق والكارت تحت بيستخدموا نفس النتيجة). */
@Composable
internal fun rememberLinkedView(transactionId: Id, reloadKey: Int): LinkedInflowsView {
    val links = LocalSpace.current.operations.linkedInflows
    var view by remember(transactionId) { mutableStateOf<LinkedInflowsView>(LinkedInflowsView.None) }
    LaunchedEffect(transactionId, reloadKey, links) { view = links?.let { l -> attempt { l.load(transactionId) } } ?: LinkedInflowsView.None }
    return view
}

/**
 * «فك الربط» (r041 A11): تأكيد جوه الصفحة ([asking] = الداخلة اللي بنسأل عنها) ⇒ الفك ⇒ شريط «تراجع» 8 ثواني ([undo]) ⇒ «تراجع» بيربطها
 * تاني بنفس فحوص الربط (`LinkInflows.relink`). واحد للصفحة، للشريط فوق ولسطور المصروف تحت.
 */
@Stable
internal class UnlinkFlow {
    var asking by mutableStateOf<Transaction?>(null)
    var undo by mutableStateOf<UnlinkUndo?>(null)

    suspend fun confirm(links: LinkInflows, show: (String) -> Unit) {
        val inflow = asking ?: return
        asking = null
        var done: UnlinkUndo? = null
        val err = failureOf { done = links.unlinkWithUndo(inflow.id) }
        if (err != null) show(err)
        undo = done
    }

    suspend fun revert(links: LinkInflows, show: (String) -> Unit) {
        val u = undo ?: return
        undo = null
        var refusal: LinkRefusal? = null
        val err = failureOf { refusal = links.relink(u) }
        show(err ?: refusal?.let { t(UiKey.LINKED_RELINK_FAILED) } ?: t(UiKey.OPS_BULK_UNDONE))
    }
}

@Composable
internal fun LinkedParentBanner(v: LinkedInflowsView.Inflow, flow: UnlinkFlow, onConfirmUnlink: () -> Unit) {
    val nav = LocalNavigator.current
    val b = linkedBanner(v)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FloatingCard(
            Modifier.fillMaxWidth(),
            onClick = b.expenseId?.let { id -> { nav.push(OperationDetailRoute(id)) } },
            enabled = !b.gone,
            clickLabel = t(UiKey.LINKED_INFLOW_OPEN),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(Lucide.RECEIPT, size = 18.dp, tint = if (b.gone) Ink.muted else Ink.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(if (b.gone) t(b.textKey) else t(b.textKey, b.title.orEmpty()), style = Type.of(14, FontWeight.Bold))
                    b.day?.let { BasicText(dayMonth(it), style = Type.caption().copy(color = Ink.muted)) }
                }
                if (b.amountMinor != null && b.currency != null) AmountText(b.amountMinor, b.currency, tone = AmountTone.EXPENSE, size = 14)
            }
        }
        when {
            flow.asking?.id == v.inflow.id -> UnlinkAsk(v.inflow, flow, onConfirmUnlink)
            flow.undo == null -> TonalButton(t(UiKey.LINKED_UNLINK), { flow.asking = v.inflow }, Modifier.fillMaxWidth(), height = 44.dp, muted = true)
        }
    }
}

/** التأكيد جوه الصفحة: «فك الربط؟» + اللي هيحصل + «إلغاء» / «فك الربط». */
@Composable
internal fun UnlinkAsk(inflow: Transaction, flow: UnlinkFlow, onConfirm: () -> Unit) {
    TintedPanel(PanelTone.AMBER) {
        PanelTitle(t(UiKey.LINKED_UNLINK_ASK_TITLE), PanelTone.AMBER)
        BasicText(t(UiKey.LINKED_UNLINK_ASK_BODY, transactionTitle(inflow)), style = Type.of(13, lineHeight = 1.6))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton(t(UiKey.ERASE_CANCEL), { flow.asking = null }, Modifier.weight(1f), onTint = true, height = 44.dp)
            QuietButton(t(UiKey.LINKED_UNLINK), onConfirm, Modifier.weight(1f), danger = true, height = 44.dp)
        }
    }
}
