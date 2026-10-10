package app.masroufy.ui.screens.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.IconButton44
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * لوحتا «القواعد والتجار»: «قاعدة جديدة/تعديل قاعدة» (النص · طريقة المطابقة · التصنيف · الترتيب) و«تعديل التاجر» (الاسم · الأسماء البديلة ·
 * التصنيف · «انسَ تصنيفه»). [onDone] بياخد رسالة النجاح والتاجر اللي اتنسى تصنيفه (عشان يفضل ظاهر).
 */
@Composable
fun RulesSheetHost(sheet: RulesSheet?, ui: RulesUi?, deps: BudgetsDeps, onDismiss: () -> Unit, onDone: (String, String?) -> Unit) {
    val ruleSheet = sheet as? RulesSheet.Rule
    val merchantSheet = sheet as? RulesSheet.Merchant
    RuleSheet(ruleSheet.takeIf { ui != null }, ui, deps, onDismiss, onDone)
    MerchantSheet(merchantSheet.takeIf { ui != null }, ui, deps, onDismiss, onDone)
}

@Composable
private fun RuleSheet(sheet: RulesSheet.Rule?, ui: RulesUi?, deps: BudgetsDeps, onDismiss: () -> Unit, onDone: (String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    // المحتوى بيفضل وهي نازلة بعد القفل (حركة الخروج)
    var last by remember { mutableStateOf(sheet) }
    if (sheet != null) last = sheet
    val shown = last
    var draft by remember(shown) { mutableStateOf(if (shown != null && ui != null) shown.startDraft(ui) else null) }
    var error by remember(shown) { mutableStateOf<String?>(null) }
    var busy by remember(shown) { mutableStateOf(false) }
    val title = t(if (shown?.row != null) TextKey.RULES_SHEET_EDIT else TextKey.RULES_NEW)
    Sheet(sheet != null, onDismiss, title, spacing = 10.dp) {
        val d = draft ?: return@Sheet
        val r = ui ?: return@Sheet
        val editing = shown?.row
        BasicText(title, style = Type.of(18, FontWeight.Bold))
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val problem = ruleProblem(r, editing, d)
            val duplicate = problem?.takeIf { d.text.isNotBlank() && d.categoryId != null }
            TextInput(
                d.text, { draft = d.copy(text = it.take(120)); error = null }, label = t(TextKey.RULES_TEXT_LABEL), placeholder = t(TextKey.RULES_TEXT_HINT),
                error = duplicate,
            )
            FieldLabel(t(TextKey.RULES_MODE_LABEL))
            SegmentedTabs(
                listOf(RuleMatchMode.CONTAINS, RuleMatchMode.STARTS_WITH, RuleMatchMode.EXACT).map { it to modeLabel(it) }, d.mode,
                { draft = d.copy(mode = it) }, Modifier.fillMaxWidth(), height = 44.dp,
            )
            CategoryPicker(r, d.group, d.categoryId, onGroup = { draft = d.copy(group = it) }) { draft = d.copy(categoryId = it) }
            PositionStepper(d.position, maxPosition(r, editing)) { draft = d.copy(position = it) }
            BasicText(t(TextKey.RULES_NOTE_RULE), style = Type.caption().copy(color = Ink.muted))
        }
        error?.let { FieldError(it) }
        PrimaryButton(
            t(TextKey.RULES_SAVE),
            {
                busy = true
                scope.launch {
                    error = attempt(t(TextKey.BUDGETS_ERROR_TITLE)) { saveRule(deps, r, editing, d) }
                    busy = false
                    if (error == null) onDone(t(if (editing != null) TextKey.RULES_SAVED else TextKey.RULES_ADDED), null)
                }
            },
            Modifier.fillMaxWidth(),
            enabled = ruleProblem(r, editing, d) == null,
            loading = busy,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MerchantSheet(sheet: RulesSheet.Merchant?, ui: RulesUi?, deps: BudgetsDeps, onDismiss: () -> Unit, onDone: (String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf(sheet) }
    if (sheet != null) last = sheet
    val shown = last
    var draft by remember(shown) { mutableStateOf(if (shown != null && ui != null) shown.startDraft(ui) else null) }
    var aliases by remember(shown) { mutableStateOf(shown?.row?.merchant?.aliases.orEmpty()) }
    var error by remember(shown) { mutableStateOf<String?>(null) }
    var busy by remember(shown) { mutableStateOf(false) }
    val title = t(TextKey.RULES_SHEET_MERCHANT)
    Sheet(sheet != null, onDismiss, title, spacing = 10.dp) {
        val d = draft ?: return@Sheet
        val r = ui ?: return@Sheet
        val row = shown?.row ?: return@Sheet
        BasicText(title, style = Type.of(18, FontWeight.Bold))
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextInput(d.name, { draft = d.copy(name = it.take(120)); error = null }, label = t(TextKey.RULES_MERCHANT_NAME), placeholder = t(TextKey.RULES_MERCHANT_NAME))
            FieldLabel(t(TextKey.RULES_ALIAS_LABEL))
            if (aliases.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    aliases.forEach { a ->
                        BasicText(a, Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x0F193D33)).padding(horizontal = 10.dp, vertical = 6.dp), style = Type.caption())
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                TextInput(d.alias, { draft = d.copy(alias = it.take(120)); error = null }, Modifier.weight(1f), placeholder = t(TextKey.RULES_ALIAS_HINT), height = 44.dp, textSize = 14)
                TonalButton(t(TextKey.RULES_ALIAS_ADD), {
                    val alias = d.alias.trim()
                    if (alias.isNotEmpty()) scope.launch {
                        error = attempt(t(TextKey.BUDGETS_ERROR_TITLE)) { deps.rules.addAlias(row.id, alias) }
                        if (error == null) {
                            aliases = deps.rules.listMerchants().firstOrNull { it.merchant.id == row.id }?.merchant?.aliases.orEmpty()
                            draft = d.copy(alias = "")
                        }
                    }
                }, height = 44.dp)
            }
            CategoryPicker(r, d.group, d.categoryId, onGroup = { draft = d.copy(group = it) }) { draft = d.copy(categoryId = it) }
            BasicText(t(TextKey.RULES_NOTE_MERCHANT), style = Type.caption().copy(color = Ink.muted))
            if (row.merchant.verifiedCategoryId != null) {
                DangerButton(t(TextKey.RULES_FORGET), {
                    scope.launch {
                        error = attempt(t(TextKey.BUDGETS_ERROR_TITLE)) { deps.rules.setMerchantCategory(row.id, null) }
                        if (error == null) onDone(t(TextKey.RULES_FORGOT), row.id)
                    }
                }, Modifier.fillMaxWidth())
            }
        }
        error?.let { FieldError(it) }
        PrimaryButton(
            t(TextKey.RULES_SAVE),
            {
                busy = true
                scope.launch {
                    error = attempt(t(TextKey.BUDGETS_ERROR_TITLE)) { saveMerchant(deps, row, d) }
                    busy = false
                    if (error == null) onDone(t(TextKey.RULES_MERCHANT_SAVED), null)
                }
            },
            Modifier.fillMaxWidth(),
            enabled = d.name.isNotBlank() && d.categoryId != null,
            loading = busy,
        )
    }
}

/** اختيار التصنيف: شرايح المجموعات (بتتمرر) ⇒ التصنيفات الظاهرة في المجموعة بلونها (الفرعي بعد أبوه). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(ui: RulesUi, group: String?, selected: String?, onGroup: (String?) -> Unit, onPick: (String) -> Unit) {
    FieldLabel(t(TextKey.RULES_CAT_LABEL))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ui.picker.forEach { g -> SelectChip(g.name, selected = g.key == group, onClick = { onGroup(g.key) }, height = 44.dp) }
    }
    val cats = ui.picker.firstOrNull { it.key == group }?.categories.orEmpty()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cats.forEach { c -> SelectChip(c.name, selected = c.id == selected, onClick = { onPick(c.id) }, height = 44.dp, dot = parseHexColor(c.colorHex)) }
    }
}

/** «الترتيب»: − [الرقم] + (١ = بيتطبق الأول). */
@Composable
private fun PositionStepper(position: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            BasicText(t(TextKey.RULES_PRI_LABEL), style = Type.of(13, FontWeight.Bold))
            BasicText(t(TextKey.RULES_PRI_NOTE), style = Type.caption().copy(color = Ink.muted))
        }
        // «−» = قدّمها (رقم أصغر) · «+» = أخّرها — زي النموذج
        IconButton44(BudgetsIcons.MINUS, t(TextKey.RULES_PRI_UP), { onChange((position - 1).coerceAtLeast(1)) }, enabled = position > 1)
        BasicText(sentenceNumber(position), Modifier.width(32.dp), style = Type.of(16, FontWeight.Bold).copy(textAlign = TextAlign.Center))
        IconButton44(Lucide.PLUS, t(TextKey.RULES_PRI_DOWN), { onChange((position + 1).coerceAtMost(max)) }, enabled = position < max)
    }
}
