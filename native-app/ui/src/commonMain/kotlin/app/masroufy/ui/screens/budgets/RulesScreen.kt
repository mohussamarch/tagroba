package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «القواعد والتجار» (`Rules` — من «المزيد» ومن آخر «التصنيفات»): التجار اللي التطبيق فاكر تصنيفهم (أقوى من أي قاعدة) · قواعدك بالترتيب
 * (القفل مش المسح) · «طبّق على السابق» بمعاينة الأول (`ReviewHistory`) · قاعدة التجار المشتركة. الحالات: بيحمّل · خطأ · فاضي · عادي.
 * ⚠️ مزامنة القاعدة المشتركة (`SharedMerchants.sync`) مش متوصلة في التجميع لسه ⇒ الزرار مقفول و«غير متاح» بسببه (missingLogic).
 */
@Composable
fun RulesScreen() {
    val space = LocalSpace.current
    val deps = space.budgets
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var keep by remember { mutableStateOf(emptySet<String>()) }
    val state = rememberLoad(space, reload) { loadRules(deps, keep) }
    var sheet by remember { mutableStateOf<RulesSheet?>(null) }
    val apply = remember(space) { ApplyHolder() }
    val ui = (state as? Load.Ready)?.value
    DetailScaffold(title = t(UiKey.RULES_TITLE), actions = { GreenPlus(t(UiKey.RULES_NEW)) { if (ui != null) sheet = RulesSheet.Rule(null) } }) {
        item(key = "intro") { BasicText(t(UiKey.RULES_INTRO), style = Type.of(13).copy(color = Ink.muted)) }
        when (state) {
            Load.Loading -> {
                item { Skeleton(Modifier.fillMaxWidth().height(180.dp), strong = true) }
                item { Skeleton(Modifier.fillMaxWidth().height(220.dp)) }
            }
            is Load.Failed -> item { ErrorCard(t(UiKey.BUDGETS_ERROR_TITLE), t(UiKey.BUDGETS_ERROR_BODY), { reload++ }) }
            is Load.Ready -> {
                val r = state.value
                item(key = "merchants") {
                    Section(t(UiKey.RULES_MERCHANTS_TITLE), t(UiKey.RULES_MERCHANTS_NOTE), r.merchants.isEmpty(), t(UiKey.RULES_MERCHANTS_EMPTY)) {
                        r.merchants.forEach { m -> MerchantCard(m) { sheet = RulesSheet.Merchant(m) } }
                    }
                }
                item(key = "rules") {
                    Section(t(UiKey.RULES_YOURS_TITLE), t(UiKey.RULES_YOURS_NOTE), r.rules.isEmpty(), t(UiKey.RULES_EMPTY)) {
                        r.rules.forEach { row ->
                            RuleCard(row, onOpen = { sheet = RulesSheet.Rule(row) }) {
                                scope.launch {
                                    val error = attempt(t(UiKey.BUDGETS_ERROR_TITLE)) { deps.rules.setRuleEnabled(row.id, !row.enabled) }
                                    toaster.show(error ?: t(if (row.enabled) UiKey.RULES_OFF_TOAST else UiKey.RULES_ON_TOAST))
                                    if (error == null) { apply.reset(); reload++ }
                                }
                            }
                        }
                    }
                }
                item(key = "apply") { ApplyCard(apply, deps, today = space.shell.today(), hasRules = r.rules.any { it.enabled } || r.merchants.isNotEmpty()) { reload++ } }
                item(key = "sync") { SyncCard() }
            }
        }
    }
    RulesSheetHost(sheet, ui, deps, onDismiss = { sheet = null }) { message, forgotId ->
        sheet = null
        toaster.show(message)
        if (forgotId != null) keep = keep + forgotId
        apply.reset()
        reload++
    }
}

/** «+» أخضر 48 في الرأس. */
@Composable
internal fun GreenPlus(label: String, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(48.dp).pressScale(press).clip(RoundedCornerShape(Radius.control)).background(Glass.primary)
            .tap(press, role = Role.Button, label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(Lucide.PLUS, size = 22.dp, tint = Ink.onPrimary) }
}

@Composable
private fun Section(title: String, note: String, empty: Boolean, emptyText: String, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            BasicText(title, Modifier.semantics { heading() }, style = Type.of(15, FontWeight.Bold))
            BasicText(note, style = Type.caption().copy(color = Ink.muted))
        }
        if (empty) EmptyState(emptyText) else rows()
    }
}

/** الشارة 32 جنب الصف: رقم الترتيب للقاعدة · رمز محل للتاجر. */
@Composable
private fun Badge32(content: @Composable () -> Unit, bg: Color) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(bg), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CategoryLine(name: String, hex: String?, missing: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hex != null) ColorDot(parseHexColor(hex)) else Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x33193D33)))
        val ink = when {
            missing -> Ink.expense
            hex == null -> Ink.muted
            else -> Ink.text
        }
        BasicText(name, style = Type.of(13, if (hex != null || missing) FontWeight.Bold else FontWeight.Normal).copy(color = ink))
    }
}

@Composable
private fun MerchantCard(m: MerchantRowUi, onOpen: () -> Unit) {
    FloatingCard(
        Modifier.fillMaxWidth().alpha(if (m.categoryName == null) 0.6f else 1f), shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), onClick = onOpen, clickLabel = t(UiKey.CATS_EDIT_LABEL, m.name),
    ) {
        Row(Modifier.defaultMinSize(minHeight = 56.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge32({ LucideIcon(categoryIcon("store"), size = 18.dp, tint = Ink.primary) }, Ink.selected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(t(UiKey.RULES_MERCHANT), style = Type.caption().copy(color = Ink.muted))
                    BasicText(m.name, style = Type.of(15, FontWeight.Bold), maxLines = 1)
                }
                CategoryLine(m.categoryName ?: t(UiKey.RULES_NO_FIXED), m.categoryHex, missing = false)
                if (m.meta != null) BasicText(m.meta, style = Type.of(11).copy(color = Ink.muted))
            }
        }
    }
}

@Composable
private fun RuleCard(row: RuleRowUi, onOpen: () -> Unit, onToggle: () -> Unit) {
    val press = rememberPress()
    FloatingCard(
        Modifier.fillMaxWidth().alpha(if (row.enabled) 1f else 0.6f), shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).pressScale(press)
                    .tap(press, role = Role.Button, label = t(UiKey.RULES_RULE_ARIA, row.rule.matchText), onClick = onOpen),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Badge32({ BasicText(app.masroufy.core.sentenceNumber(row.position), style = Type.of(13, FontWeight.Bold)) }, Color(0x0F193D33))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(row.modeLabel, style = Type.caption().copy(color = Ink.muted))
                        BasicText(row.text, Modifier.widthIn(max = 220.dp), style = Type.of(15, FontWeight.Bold), maxLines = 1)
                    }
                    CategoryLine(row.categoryName, row.categoryHex, row.missing)
                    if (row.meta != null) BasicText(row.meta, style = Type.of(11).copy(color = Ink.muted))
                }
            }
            Switch(row.enabled, t(UiKey.RULES_SWITCH, row.rule.matchText), onToggle)
        }
    }
}
