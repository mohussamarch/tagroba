package app.masroufy.ui.screens.investment.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.InheritanceScenario
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.InheritanceScenarioDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface SavedSheet {
    data class Rename(val id: String, val name: String) : SavedSheet
    data class Delete(val id: String, val name: String) : SavedSheet
}

private class Undo(val text: String, val restore: InheritanceScenario?)

/**
 * «الحسبات المحفوظة» (لوحة `InheritanceSaved`): على حسابك كله وبتبان على كل أجهزتك · افتح (على النتيجة) · إعادة تسمية (لوحة) · حذف (لوحة
 * تأكيد + «تراجع» ٤ ثواني) · فاضية ⇒ «احسب تركة». كل حاجة من `ManageInheritanceScenarios`.
 */
@Composable
fun InheritanceSavedScreen() {
    val deps = LocalSpace.current.investment.calculators
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var scenarios by remember(deps) { mutableStateOf<List<InheritanceScenario>?>(null) }
    var rows by remember(deps) { mutableStateOf<List<SavedRowUi>?>(null) }
    var failed by remember(deps) { mutableStateOf(false) }
    LaunchedEffect(deps, reload) {
        val people = runCatching { deps.people() }.getOrDefault(emptyList())
        val list = runCatching { deps.scenarios.list() }.getOrNull()
        failed = list == null
        scenarios = list
        rows = list?.map { s -> savedRowUi(s, people, runCatching { deps.scenarios.calculate(s.id) }.getOrNull()) }
    }
    var sheet by remember { mutableStateOf<SavedSheet?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var sheetError by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<Undo?>(null) }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(4300)
            undo = null
        }
    }
    val backToCalculator = { id: String? ->
        val below = nav.stack.getOrNull(nav.stack.lastIndex - 1)?.route
        if (below is InheritanceCalculatorRoute) nav.pop()
        if (id != null || below !is InheritanceCalculatorRoute) nav.replace(InheritanceCalculatorRoute(id))
    }

    Box(Modifier.fillMaxSize()) {
        InnerScaffold(t(TextKey.INHSAVED_TITLE)) {
            item(key = "intro") { BasicText(t(TextKey.INHSAVED_INTRO), style = Type.of(13).copy(color = Ink.muted)) }
            val list = rows
            when {
                failed -> item(key = "failed") { EmptyState(t(TextKey.INHSAVED_LOAD_FAILED)) }
                list == null -> items(2) { Skeleton(Modifier.fillMaxWidth().height(176.dp)) }
                list.isEmpty() -> item(key = "empty") {
                    EmptyState(t(TextKey.INHSAVED_EMPTY_TITLE), t(TextKey.INHSAVED_EMPTY_BODY), action = {
                        PrimaryButton(t(TextKey.INHSAVED_EMPTY_ACTION), { backToCalculator(null) }, Modifier.padding(top = 10.dp))
                    })
                }
                else -> list.forEach { r ->
                    item(key = r.id) {
                        SavedCard(
                            r,
                            onOpen = { backToCalculator(r.id) },
                            onRename = { nameInput = r.name; sheetError = null; sheet = SavedSheet.Rename(r.id, r.name) },
                            onDelete = { sheetError = null; sheet = SavedSheet.Delete(r.id, r.name) },
                        )
                    }
                }
            }
        }
        undo?.let { u -> UndoBar(u, Modifier.align(Alignment.BottomCenter)) {
            val s = u.restore ?: return@UndoBar
            undo = null
            scope.launch {
                runCatching { deps.scenarios.save(InheritanceScenarioDraft(s.name, s.estateOf, s.personId, s.input)) }
                undo = Undo(t(TextKey.INHSAVED_RESTORED, s.name), null)
                reload++
            }
        } }
    }
    val s = sheet
    Sheet(
        s != null, { sheet = null },
        when (s) {
            is SavedSheet.Rename -> t(TextKey.INHCALC_SAVE_TITLE)
            is SavedSheet.Delete -> t(TextKey.INHSAVED_DELETE_TITLE, s.name)
            null -> ""
        },
    ) {
        when (s) {
            is SavedSheet.Rename -> {
                BasicText(t(TextKey.INHSAVED_RENAME_BODY), style = Type.of(13).copy(color = Ink.muted))
                TextInput(nameInput, { nameInput = it.take(60); sheetError = null }, error = sheetError)
                PrimaryButton(t(TextKey.INHCALC_SAVE_BUTTON), {
                    scope.launch {
                        try {
                            val renamed = deps.scenarios.rename(s.id, nameInput.trim().ifEmpty { s.name })
                            sheet = null
                            undo = Undo(t(TextKey.INHSAVED_RENAMED, renamed.name), null)
                            reload++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: IllegalArgumentException) {
                            sheetError = e.message
                        }
                    }
                }, Modifier.fillMaxWidth())
            }
            is SavedSheet.Delete -> {
                BasicText(t(TextKey.INHSAVED_DELETE_BODY), style = Type.of(13).copy(color = Ink.muted))
                DangerButton(t(TextKey.INHSAVED_DELETE)) {
                    val gone = scenarios?.firstOrNull { it.id == s.id }
                    scope.launch {
                        runCatching { deps.scenarios.delete(s.id) }
                        sheet = null
                        undo = Undo(t(TextKey.INHSAVED_DELETED, s.name), gone)
                        reload++
                    }
                }
            }
            null -> Unit
        }
    }
}

@Composable
private fun SavedCard(r: SavedRowUi, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(r.name, style = Type.of(16, FontWeight.Bold))
                    BasicText(r.whose, style = Type.caption().copy(color = Ink.muted))
                }
                BasicText(
                    r.law,
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (r.lawEg) Color(0x1A2469BA) else Ink.selected).padding(horizontal = 10.dp, vertical = 3.dp),
                    style = Type.of(11, FontWeight.Bold).copy(color = if (r.lawEg) Ink.transfer else Ink.primary),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(r.itemsLine, Modifier.weight(1f), style = Type.of(13).copy(color = Ink.muted))
                AmountText(r.totalMinor, r.currency, size = 13)
            }
            BasicText(r.heirs, style = Type.of(13))
            BasicText(r.whenText, style = Type.of(11).copy(color = Ink.muted))
            Divider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(t(TextKey.INHSAVED_OPEN), onOpen, Modifier.weight(2f), height = 44.dp)
                TonalButton(t(TextKey.INHSAVED_RENAME), onRename, Modifier.weight(1f), height = 44.dp)
                val press = rememberPress()
                val label = t(TextKey.INHSAVED_DELETE_ONE, r.name)
                Box(
                    Modifier.size(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(Color(0x14BE3D48))
                        .tap(press, label = label, onClick = onDelete).semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { LucideIcon(Lucide.TRASH_2, size = 18.dp, tint = Ink.expense) }
            }
        }
    }
}

/** زرار الحذف الأحمر في لوحة التأكيد. */
@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.fillMaxWidth().height(48.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(Ink.expense).tap(press, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = Type.of(15, FontWeight.Bold).copy(color = Color.White)) }
}

/** الرسالة تحت بعد الحذف أو تغيير الاسم، و«تراجع» للحذف (٤ ثواني — النموذج). */
@Composable
private fun UndoBar(u: Undo, modifier: Modifier, onUndo: () -> Unit) {
    Row(
        modifier.padding(bottom = 28.dp, start = 20.dp, end = 20.dp).widthIn(max = 340.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xEB193D33))
            .semantics { liveRegion = LiveRegionMode.Polite }.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(u.text, Modifier.weight(1f, fill = false).padding(vertical = 6.dp), style = Type.of(13, FontWeight.Bold).copy(color = Color.White))
        if (u.restore != null) {
            val press = rememberPress()
            Box(
                Modifier.height(40.dp).pressScale(press).clip(RoundedCornerShape(12.dp)).background(Color(0x1AFFFFFF)).tap(press, onClick = onUndo).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(t(TextKey.INHSAVED_UNDO), style = Type.of(13, FontWeight.Bold).copy(color = Ink.mint)) }
        }
    }
}
