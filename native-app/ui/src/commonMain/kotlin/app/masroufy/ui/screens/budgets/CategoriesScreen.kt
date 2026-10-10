package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion
import kotlinx.coroutines.launch

/**
 * «التصنيفات» (`Categories` — من «المزيد» ومن «أضف فرعيًا» في ميزانية التصنيف): مجموعة ← رئيسي (يتفتح لفروعه) ← فرعي · «المخفية» وسببها ·
 * «تصنيف رئيسي جديد» · رابط «القواعد والتجار». التعديل والإضافة والإخفاء في `CategoryEditSheet` ⇒ `ManageCategories.save`.
 * الحالات: بيحمّل · خطأ («تعذّر تحميل التصنيفات» + «أعد المحاولة») · عادي.
 */
@Composable
fun CategoriesScreen() {
    val space = LocalSpace.current
    val deps = space.budgets
    val toaster = LocalToaster.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val state = rememberLoad(space, reload) { loadCategories(deps) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var openedOnce by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CatEditTarget?>(null) }
    val ready = (state as? Load.Ready)?.value
    // زي النموذج: أول رئيسي مفتوح أول ما الشاشة تفتح
    LaunchedEffect(ready) {
        if (ready != null && !openedOnce) {
            open = ready.groups.firstOrNull()?.mains?.firstOrNull()?.id
            openedOnce = true
        }
    }
    DetailScaffold(title = t(UiKey.CATS_TITLE)) {
        item(key = "intro") { BasicText(t(UiKey.CATS_INTRO), style = Type.of(13).copy(color = Ink.muted)) }
        when (state) {
            Load.Loading -> {
                item { Skeleton(Modifier.fillMaxWidth().height(220.dp), strong = true) }
                item { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }
            }
            is Load.Failed -> item { ErrorCard(t(UiKey.CATS_ERR_TITLE), t(UiKey.CATS_ERR_BODY), { reload++ }) }
            is Load.Ready -> {
                val ui = state.value
                ui.groups.forEach { g ->
                    item(key = "g-${g.key}") {
                        GroupSection(g, open, onToggle = { id -> open = if (open == id) null else id }) { target -> editing = target }
                    }
                }
                if (ui.hidden.isNotEmpty()) item(key = "hidden") {
                    HiddenSection(ui.hidden) { row ->
                        val category = ui.stored.firstOrNull { it.id == row.id } ?: return@HiddenSection
                        scope.launch {
                            val error = attempt(t(UiKey.CATS_ERR_TITLE)) { deps.categories.save(catVisibilityInput(category, active = true)) }
                            toaster.show(error ?: t(UiKey.CATS_SHOWN_TOAST, row.name))
                            if (error == null) reload++
                        }
                    }
                }
                item(key = "add") {
                    PrimaryButton(t(UiKey.CATS_ADD_MAIN), { editing = CatEditTarget(CatEditMode.NEW_MAIN) }, Modifier.fillMaxWidth(), leading = Lucide.PLUS)
                }
                item(key = "rules") { RulesLink { nav.push(RulesRoute) } }
            }
        }
    }
    CategoryEditSheet(
        target = editing,
        ui = ready,
        onDismiss = { editing = null },
        onSaved = { message, parentOrSelf ->
            editing = null
            toaster.show(message)
            if (parentOrSelf != null) open = parentOrSelf
            reload++
        },
    )
}

@Composable
private fun GroupSection(g: CatGroupUi, open: String?, onToggle: (String) -> Unit, onEdit: (CatEditTarget) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.semantics { heading() }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(categoryIcon(g.iconKey), size = 16.dp, tint = Ink.muted)
            BasicText(g.name, style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        }
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            g.mains.forEachIndexed { i, m ->
                MainRow(m, open == m.id, { onToggle(m.id) }, onEdit)
                if (i < g.mains.lastIndex) Divider()
            }
        }
    }
}

@Composable
private fun CategoryTile(iconKey: String, colorHex: String, size: Dp, radius: Dp, dim: Boolean = false) {
    val color = parseHexColor(colorHex)
    IconTile(color, Modifier.alpha(if (dim) 0.55f else 1f), size = size, radius = radius) {
        LucideIcon(categoryIcon(iconKey), size = size * 0.5f, tint = color)
    }
}

@Composable
private fun MainRow(m: MainRowUi, isOpen: Boolean, onToggle: () -> Unit, onEdit: (CatEditTarget) -> Unit) {
    val press = rememberPress()
    val turn by animateFloatAsState(if (isOpen) 180f else 0f, motion(Springs.SNAPPY), label = "chevron")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.weight(1f).defaultMinSize(minHeight = 60.dp).pressScale(press).clip(RoundedCornerShape(16.dp))
                    .tap(press, role = Role.Button, label = m.name, onClick = onToggle).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryTile(m.iconKey, m.colorHex, 40.dp, 13.dp)
                Column(Modifier.weight(1f)) {
                    BasicText(m.name, style = Type.of(15, FontWeight.Bold), maxLines = 1)
                    BasicText(m.subsLabel, style = Type.caption().copy(color = Ink.muted))
                }
                LucideIcon(Lucide.CHEVRON_DOWN, Modifier.rotate(turn), size = 18.dp, tint = Ink.muted)
            }
            SmallIconButton(Lucide.PENCIL, t(UiKey.CATS_EDIT_LABEL, m.name)) { onEdit(CatEditTarget(CatEditMode.EDIT, m.id)) }
        }
        if (isOpen) {
            Column(Modifier.padding(start = 28.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                m.subs.forEach { sub ->
                    val subPress = rememberPress()
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).pressScale(subPress).clip(RoundedCornerShape(14.dp))
                            .tap(subPress, role = Role.Button, label = t(UiKey.CATS_EDIT_LABEL, sub.name)) { onEdit(CatEditTarget(CatEditMode.EDIT, sub.id)) }
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryTile(sub.iconKey, sub.colorHex, 30.dp, 10.dp)
                        BasicText(sub.name, Modifier.weight(1f), style = Type.of(14), maxLines = 1)
                        LucideIcon(Lucide.PENCIL, size = 16.dp, tint = Ink.muted)
                    }
                }
                TonalButton(t(UiKey.CATS_ADD_SUB), { onEdit(CatEditTarget(CatEditMode.NEW_SUB, parentId = m.id)) }, Modifier.padding(top = 4.dp), height = 44.dp)
            }
        }
    }
}

/** زرار رمز صغير 44 (قلم التعديل) — `#08634F` على أخضر خفيف (النموذج). */
@Composable
internal fun SmallIconButton(icon: Lucide, label: String, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(Color(0x0F08634F))
            .tap(press, role = Role.Button, label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 20.dp, tint = Ink.primary) }
}

@Composable
private fun HiddenSection(rows: List<HiddenRowUi>, onShow: (HiddenRowUi) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.CATS_HIDDEN_TITLE), Modifier.semantics { heading() }, style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        BasicText(t(UiKey.CATS_HIDDEN_INTRO), style = Type.caption().copy(color = Ink.muted))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x8CFFFFFF)).padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            rows.forEachIndexed { i, row ->
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryTile(row.iconKey, row.colorHex, 32.dp, 11.dp, dim = true)
                    Column(Modifier.weight(1f)) {
                        BasicText(row.name, style = Type.of(14, FontWeight.Bold).copy(color = Ink.soft), maxLines = 1)
                        BasicText(row.why, style = Type.caption().copy(color = Ink.muted))
                    }
                    if (row.canShow) TonalButton(t(UiKey.CATS_SHOW), { onShow(row) }, height = 44.dp)
                }
                if (i < rows.lastIndex) Divider()
            }
        }
    }
}

@Composable
private fun RulesLink(onOpen: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).pressScale(press).clip(RoundedCornerShape(18.dp)).background(Color(0x8CFFFFFF))
            .tap(press, role = Role.Button, label = t(UiKey.CATS_RULES_LINK), onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(t(UiKey.CATS_RULES_LINK), style = Type.bodyBold())
            BasicText(t(UiKey.CATS_RULES_HINT), style = Type.caption().copy(color = Ink.muted))
        }
        LucideIcon(Lucide.CHEVRON_LEFT, Modifier.mirrorInLtr(), size = 20.dp, tint = Ink.muted)
    }
}
