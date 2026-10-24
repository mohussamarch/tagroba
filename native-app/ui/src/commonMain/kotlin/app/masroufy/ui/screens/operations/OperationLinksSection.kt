package app.masroufy.ui.screens.operations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Direction
import app.masroufy.core.R06Key
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Route
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LinkedOp
import app.masroufy.usecase.OpLink
import app.masroufy.usecase.OpLinkKind

/** سطر رابط في تفاصيل العملية: نوع الحاجة · اسمها · الصفحة اللي بتتفتح. */
data class OpLinkRow(val label: String, val name: String, val route: Route)

/**
 * r06-links2 (فجوة ٣): من الرابط للصفحة — بالمسارات الموجودة بس (حساب · شخص · دين · خطة (التقسيط و«ادفع بعدين» نفس الصفحة) · اشتراك ·
 * الأصل اللي اتباع · مصدر الدخل). من غير حساب ولا قراية.
 */
fun opLinkRows(links: List<OpLink>): List<OpLinkRow> = links.map { l ->
    when (l.kind) {
        OpLinkKind.WALLET -> OpLinkRow(t(R06Key.L2_LK_WALLET), l.name, app.masroufy.ui.screens.more.WalletDetailRoute(l.id))
        OpLinkKind.PERSON -> OpLinkRow(t(R06Key.L2_LK_PERSON), l.name, app.masroufy.ui.screens.people.PersonProfileRoute(l.id))
        OpLinkKind.DEBT -> OpLinkRow(t(R06Key.L2_LK_DEBT), l.name, app.masroufy.ui.screens.dues.DebtDetailRoute(l.id))
        OpLinkKind.PLAN -> OpLinkRow(t(R06Key.L2_LK_PLAN), l.name, app.masroufy.ui.screens.dues.InstallmentDetailRoute(l.id))
        OpLinkKind.SUBSCRIPTION -> OpLinkRow(t(R06Key.L2_LK_SUB), l.name, app.masroufy.ui.screens.dues.SubscriptionDetailRoute(l.id))
        OpLinkKind.ASSET_SALE -> OpLinkRow(t(R06Key.L2_LK_SALE), l.name, app.masroufy.ui.screens.investment.AssetDetailRoute(l.id))
        OpLinkKind.INCOME_SOURCE -> OpLinkRow(t(R06Key.L2_LK_INCOME), l.name, app.masroufy.ui.screens.more.IncomeSourceDetailRoute(l.id))
    }
}

/** «مربوطة بـ» في تفاصيل العملية — مخفي لو مفيش ولا رابط أو الربط مش متوصل. */
@Composable
internal fun OperationLinksSection(transactionId: String, version: Int = 0) {
    val links = LocalSpace.current.shell.opLinks ?: return
    val nav = LocalNavigator.current
    var rows by remember(transactionId) { mutableStateOf<List<OpLinkRow>>(emptyList()) }
    LaunchedEffect(transactionId, version) { rows = runCatching { opLinkRows(links.of(transactionId)) }.getOrDefault(emptyList()) }
    if (rows.isEmpty()) return
    LinkCard(t(R06Key.L2_LK_TITLE)) {
        rows.forEach { r -> LinkLine(r.label, r.name.ifEmpty { r.label }, onClick = { nav.push(r.route) }) }
    }
}

/** عمليات الدين أو الخطة — كل سطر بيفتح تفاصيل العملية. فاضي ⇒ مخفي. */
@Composable
internal fun LinkedOpsSection(key: String, version: Int = 0, load: suspend () -> List<LinkedOp>) {
    val nav = LocalNavigator.current
    var ops by remember(key) { mutableStateOf<List<LinkedOp>>(emptyList()) }
    LaunchedEffect(key, version) { ops = runCatching { load() }.getOrDefault(emptyList()) }
    if (ops.isEmpty()) return
    LinkCard(t(R06Key.L2_LK_OPS)) {
        ops.forEach { o ->
            LinkLine(o.name.ifEmpty { t(R06Key.L2_LK_OP) }, o.day, onClick = { nav.push(OperationDetailRoute(o.transactionId)) }) {
                AmountText(o.amountMinor, o.currency, tone = if (o.direction == Direction.IN) AmountTone.INCOME else AmountTone.EXPENSE, showCurrency = false)
            }
        }
    }
}

/** صفحة الدين ⇒ العملية اللي عملته وعمليات السداد. */
@Composable
fun DebtOps(obligationId: String, personId: String) {
    val links = LocalSpace.current.shell.opLinks ?: return
    LinkedOpsSection("debt-$obligationId", app.masroufy.ui.screens.dues.DuesChanges.version) { links.opsOfDebt(obligationId, personId) }
}

/** صفحة خطة التقسيط أو «ادفع بعدين» ⇒ الأقساط المدفوعة والفلوس اللي استلمتها. */
@Composable
fun PlanOps(planId: String) {
    val links = LocalSpace.current.shell.opLinks ?: return
    LinkedOpsSection("plan-$planId", app.masroufy.ui.screens.dues.DuesChanges.version) { links.opsOfPlan(planId) }
}

@Composable
private fun LinkCard(title: String, content: @Composable () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = Type.of(14, FontWeight.Bold))
            content()
        }
    }
}

@Composable
private fun LinkLine(title: String, sub: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = Type.bodyBold(), maxLines = 1)
            BasicText(sub, style = Type.caption().copy(color = Ink.muted), maxLines = 1)
        }
        trailing?.invoke()
        BasicText("‹", style = Type.of(16).copy(color = Ink.muted))
    }
}
