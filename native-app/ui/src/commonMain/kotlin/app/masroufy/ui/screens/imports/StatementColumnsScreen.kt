package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «تحديد الأعمدة» (`StatementColumns`) لملف CSV أعمدته مش معروفة: أول ٤ أسطر · اختيار معنى كل عمود · المحفظة.
 * ⚠️ **التعيين اليدوي نفسه لسه مالوش حالة استخدام** (`SchemaId` فيه أشكال ثابتة بس — OVERRIDES §76 «ناقص في كوتلن»: لا ربط يدوي ولا مبلغ بإشارة
 * ولا صيغة التاريخ ولا سلسلة الرصيد ولا حفظ القالب) ⇒ الاختيار بيتعرض، والمعاينة «غير متاح»، و«راجع العمليات» معطّل وبيقول ليه — من غير تخمين.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatementColumnsScreen(draftId: Long) {
    val deps = LocalSpace.current.imports
    val draft = remember(draftId) { ImportDrafts[draftId] }
    val table = remember(draft) { draft?.csv?.let { deps.csvTable(it, 4) } }
    var roles by remember(table) { mutableStateOf(List<ColumnRole?>(table?.header?.size ?: 0) { null }) }
    var sel by remember(table) { mutableIntStateOf(0) }
    var wallets by remember(deps) { mutableStateOf<List<Wallet>>(emptyList()) }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    LaunchedEffect(deps) { wallets = runCatching { deps.wallets() }.getOrDefault(emptyList()); wallet = defaultWallet(wallets) }

    InnerScaffold(t(UiKey.STATEMENT_COLUMNS_TITLE)) {
        if (draft == null || table == null) {
            item(key = "gone") { EmptyState(t(UiKey.IMPORTS_DRAFT_GONE), t(UiKey.IMPORTS_DRAFT_GONE_BODY)) }
            return@InnerScaffold
        }
        item(key = "intro") {
            Column {
                BasicText(draft.fileName, style = Type.bodyBold())
                BasicText(t(UiKey.STATEMENT_COLUMNS_INTRO), style = Type.of(13).copy(color = Ink.muted))
            }
        }
        item(key = "table") {
            FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
                CountTitle(t(UiKey.STATEMENT_COLUMNS_TABLE), t(UiKey.STATEMENT_COLUMNS_TABLE_NOTE, linesCount(table.lines)))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    table.header.forEachIndexed { i, head ->
                        ColumnCell(head, table.rows.map { it.getOrElse(i) { "" } }, roles[i], i == sel) { sel = i }
                    }
                }
            }
        }
        item(key = "roles") {
            FloatingCard(Modifier.fillMaxWidth()) {
                BasicText(t(UiKey.STATEMENT_COLUMNS_SELECTED, columnWord(sel), table.header.getOrElse(sel) { "" }), style = Type.of(15, FontWeight.Bold))
                BasicText(t(UiKey.STATEMENT_COLUMNS_FIRST, table.rows.firstOrNull()?.getOrNull(sel)?.ifBlank { "—" } ?: "—"), style = Type.caption().copy(color = Ink.muted))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (role in ColumnRole.entries) SelectChip(t(role.label), roles.getOrNull(sel) == role, { roles = assignRole(roles, sel, role); sel = nextUnset(roles, sel) }, height = 44.dp)
                }
            }
        }
        item(key = "checks") { ColumnChecks(table, roles) }
        item(key = "wallet") {
            FloatingCard(Modifier.fillMaxWidth()) {
                BasicText(t(UiKey.STATEMENT_IMPORT_WALLET_Q), style = Type.of(14, FontWeight.Bold))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (w in wallets.filter { it.kind != "cash" }) SelectChip(w.name, w.id == wallet?.id, { wallet = w }, height = 44.dp)
                }
            }
        }
        item(key = "go") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PrimaryButton(t(UiKey.STATEMENT_IMPORT_REVIEW), {}, Modifier.fillMaxWidth(), enabled = false, height = 52.dp)
                BasicText(t(UiKey.STATEMENT_COLUMNS_PENDING), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
        }
    }
}

@Composable
private fun ColumnCell(head: String, cells: List<String>, role: ColumnRole?, selected: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    val surface = if (selected) Modifier.background(Ink.primary.copy(alpha = 0.07f)).insetRing(shape, 1.5.dp, Ink.primary) else Modifier
    Column(Modifier.widthIn(min = 92.dp, max = 168.dp).clip(shape).then(surface).tap(press, onClick = onClick).pressScale(press).padding(top = 8.dp, bottom = 2.dp)) {
        Tag(role?.let { t(it.label) } ?: t(UiKey.STATEMENT_COLUMNS_PICK), if (role == null) TagTone.AMBER else if (role == ColumnRole.SKIP) TagTone.MUTED else TagTone.NEW, Modifier.padding(horizontal = 8.dp))
        BasicText(head, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = Type.captionBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        for (c in cells) {
            Divider()
            BasicText(c, Modifier.height(30.dp).padding(horizontal = 10.dp, vertical = 6.dp), style = Type.caption().copy(color = if (role == ColumnRole.SKIP) Ink.faded else Ink.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ColumnChecks(table: CsvTable, roles: List<ColumnRole?>) {
    FloatingCard(Modifier.fillMaxWidth()) {
        BasicText(t(UiKey.STATEMENT_COLUMNS_PREVIEW), style = Type.of(14, FontWeight.Bold))
        val first = table.rows.firstOrNull().orEmpty()
        val desc = roles.indexOf(ColumnRole.DESC)
        val date = roles.indexOf(ColumnRole.DATE)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f)) {
                BasicText(first.getOrNull(desc)?.takeIf { desc >= 0 } ?: t(UiKey.STATEMENT_COLUMNS_DESC_UNSET), style = Type.bodyBold(), maxLines = 1)
                BasicText(first.getOrNull(date)?.takeIf { date >= 0 } ?: t(UiKey.STATEMENT_COLUMNS_DATE_UNSET), style = Type.caption().copy(color = Ink.muted))
            }
            // المبلغ مش بيتقرا هنا: قراية الأعمدة يدويًا لسه مالهاش حالة استخدام (قاعدة 10 — «غير متاح» مش صفر)
            BasicText(t(TextKey.NOT_AVAILABLE), style = Type.caption().copy(color = Ink.muted))
        }
        for ((label, chosen) in columnChecks(roles)) {
            Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(Ink.focus, 8.dp)
                Column(Modifier.weight(1f)) {
                    BasicText(t(label), style = Type.of(13, FontWeight.Bold))
                    BasicText(chosen.ifEmpty { t(UiKey.STATEMENT_COLUMNS_UNSET) }, style = Type.caption().copy(color = Ink.muted))
                }
            }
        }
        BasicText(t(UiKey.STATEMENT_COLUMNS_BALANCE_WHY), style = Type.of(12, lineHeight = 1.7).copy(color = Ink.muted))
    }
}
