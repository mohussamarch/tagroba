package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.CATEGORY_GROUPS
import app.masroufy.core.TextKey
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * لوحة «تعديل تصنيف» (`CategoryEditSheet` — جوه «التصنيفات»): المعاينة (الرمز بلونه + المسار) · الاسم (من غير تكرار) · «تحت أي تصنيف؟»
 * (مقفول للرئيسي اللي تحته فروع) · المجموعة واللون (للرئيسي بس — 18 درجة) · الرمز · «احفظ/أضف» · «أخفِه من الاختيار».
 * الطول ثابت والمحتوى بيتمرر جواها. [onSaved] بياخد رسالة النجاح والرئيسي اللي يتفتح بعدها.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditSheet(target: CatEditTarget?, ui: CategoriesUi?, onDismiss: () -> Unit, onSaved: (String, String?) -> Unit) {
    val deps = app.masroufy.ui.app.LocalSpace.current.budgets
    val scope = rememberCoroutineScope()
    // اللوحة بتفضل بمحتواها وهي نازلة بعد القفل (حركة الخروج) — آخر تصنيف اتفتح
    var last by remember { mutableStateOf(target) }
    if (target != null) last = target
    val shown = last
    var draft by remember(shown) { mutableStateOf(if (shown != null && ui != null) shown.startDraft(ui.stored) else null) }
    var error by remember(shown) { mutableStateOf<String?>(null) }
    var busy by remember(shown) { mutableStateOf(false) }
    val view = if (shown != null && ui != null) draft?.let { catEditView(shown, it, ui.stored, ui.visibleMains) } else null
    Sheet(target != null && ui != null, onDismiss, view?.title.orEmpty(), spacing = 12.dp) {
        val d = draft ?: return@Sheet
        val v = view ?: return@Sheet
        val t0 = shown ?: return@Sheet
        val stored = ui?.stored ?: return@Sheet
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val color = parseHexColor(v.previewHex)
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                if (d.iconKey != null) LucideIcon(categoryIcon(d.iconKey), size = 24.dp, tint = color)
            }
            Column {
                BasicText(v.title, style = Type.of(18, FontWeight.Bold))
                BasicText(v.path, style = Type.caption().copy(color = Ink.muted))
            }
        }
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput(
                value = d.name,
                onChange = { draft = d.copy(name = it.take(80)); error = null },
                label = t(UiKey.CAT_EDIT_NAME),
                placeholder = t(UiKey.CAT_EDIT_NAME_HINT),
                error = v.nameError ?: error,
            )
            FieldLabel(t(UiKey.CAT_EDIT_PARENT))
            if (v.parentLocked) {
                BasicText(t(UiKey.CAT_EDIT_LOCKED), style = Type.caption().copy(color = Ink.muted))
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    v.parents.forEach { p ->
                        SelectChip(
                            p.name, selected = d.parentId == p.id, onClick = { draft = d.copy(parentId = p.id) }, height = 44.dp,
                            dot = p.colorHex?.let(::parseHexColor),
                        )
                    }
                }
            }
            if (v.isMain) {
                FieldLabel(t(UiKey.CAT_EDIT_GROUP))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_GROUPS.forEach { g ->
                        SelectChip(g.name, selected = d.groupKey == g.key, onClick = { draft = d.copy(groupKey = g.key) }, height = 44.dp)
                    }
                }
                FieldLabel(v.colorLabel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    v.swatches.forEach { sw -> Swatch(sw, on = sw.key == d.swatchKey) { draft = d.copy(swatchKey = sw.key) } }
                }
                BasicText(v.colorNote, style = Type.caption().copy(color = Ink.muted))
            } else if (v.subNote != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(parseHexColor(v.previewHex), size = 14.dp)
                    BasicText(v.subNote, Modifier.weight(1f), style = Type.caption().copy(color = Ink.muted))
                }
            }
            FieldLabel(t(UiKey.CAT_EDIT_ICON))
            IconGrid(selected = d.iconKey, color = parseHexColor(v.previewHex)) { draft = d.copy(iconKey = it) }
            if (v.needIcon) BasicText(t(UiKey.CAT_EDIT_NEED_ICON), style = Type.caption().copy(color = Ink.muted))
            if (v.canHide) {
                DangerButton(t(UiKey.CAT_EDIT_HIDE), {
                    val category = stored.firstOrNull { it.id == t0.id } ?: return@DangerButton
                    scope.launch {
                        error = attempt(t(UiKey.CATS_ERR_TITLE)) { deps.categories.save(catVisibilityInput(category, active = false)) }
                        if (error == null) onSaved(t(UiKey.CATS_HIDDEN_TOAST, category.name), category.parentId)
                    }
                }, Modifier.fillMaxWidth())
                BasicText(v.hideNote, style = Type.caption().copy(color = Ink.muted))
            }
        }
        if (error != null && v.nameError == null) FieldError(error!!)
        PrimaryButton(
            v.saveLabel,
            onClick = {
                busy = true
                scope.launch {
                    val input = catSaveInput(t0, d, stored)
                    val recolor = recolorsSubs(t0, d, stored)
                    var saved: app.masroufy.core.Category? = null
                    error = attempt(t(UiKey.CATS_ERR_TITLE)) { saved = deps.categories.save(input) }
                    busy = false
                    val item = saved ?: return@launch
                    val message = when {
                        t0.mode != CatEditMode.EDIT -> t(UiKey.CATS_ADDED, item.name)
                        recolor -> t(UiKey.CATS_SAVED_RECOLOR)
                        else -> t(UiKey.CATS_SAVED)
                    }
                    onSaved(message, item.parentId ?: item.id)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = v.canSave,
            loading = busy,
        )
    }
}

/** دايرة لون 30 (المختارة بحلقة بيضا ثم حلقة بلونها — النموذج). */
@Composable
private fun Swatch(sw: SwatchUi, on: Boolean, onPick: () -> Unit) {
    val color = parseHexColor(sw.colorHex)
    val press = rememberPress()
    Box(
        Modifier.size(44.dp).pressScale(press).clip(CircleShape).tap(press, role = Role.RadioButton, label = sw.name, onClick = onPick)
            .semantics { selected = on },
        contentAlignment = Alignment.Center,
    ) {
        val ring = if (on) Modifier.border(2.dp, color, CircleShape).padding(4.dp) else Modifier.padding(6.dp)
        Box(Modifier.size(40.dp).then(ring).clip(CircleShape).background(color))
    }
}

/** شبكة الرموز (44 لكل رمز) — المختار بلون التصنيف على خلفية فاتحة منه. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconGrid(selected: String?, color: Color, onPick: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CategoryIcons.byKey.forEach { (key, icon) ->
            val on = key == selected
            val press = rememberPress()
            Box(
                Modifier.size(44.dp).pressScale(press).clip(RoundedCornerShape(12.dp))
                    .then(if (on) Modifier.background(color.copy(alpha = 0.14f)).border(1.5.dp, color, RoundedCornerShape(12.dp)) else Modifier)
                    .tap(press, role = Role.RadioButton, label = key) { onPick(key) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) { LucideIcon(icon, size = 22.dp, tint = if (on) color else Ink.soft) }
        }
    }
}
