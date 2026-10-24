package app.masroufy.ui.screens.dues

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.core.UiKey
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.operations.UndoStrip
import app.masroufy.ui.screens.operations.undoWindowFor
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.EntityRemover
import app.masroufy.usecase.RemovalAsk
import app.masroufy.usecase.RemovalDone
import app.masroufy.usecase.RemoveChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * r06-delete (OVERRIDES §118-1 — قاعدة المالك): **زرار «امسح» واحد ولوحة واحدة** لكل حاجة. الزرار بيسأل حالة الاستخدام ([EntityRemover.ask])
 * ⇒ [DeleteSheet] («امسح <الاسم>؟» أو «<الاسم> عليه N عملية» بالاختيارات) ⇒ [EntityRemover.remove] ⇒ شريط «تراجع» مكان الزرار (نفس شريط
 * مسح العملية — `UndoStrip`) ⇒ لما الشباك يخلص [afterDone] (الافتراضي: رجوع للصفحة اللي قبلها). الشاشة ما بتكتبش ولا بتحسب حاجة.
 * [remover] null (مش متوصل) ⇒ مفيش زرار.
 * [leaveOnDelete] (صفحة الحاجة نفسها — المحفظة والأصل ومصدر الدخل، r06-fix055b): بعد المسح (مش الأرشفة) رجوع فوري للقايمة و«تراجع»
 * في شريط الشل ([app.masroufy.ui.shell.Toaster.showUndo]) — عشان الصفحة بتتحول لـ«لم تعد موجودة» والشريط اللي جواها كان بيختفي.
 */
@Composable
internal fun DeleteWithChoices(
    remover: EntityRemover?,
    id: String,
    modifier: Modifier = Modifier,
    afterDone: ((String) -> Unit)? = null,
    leaveOnDelete: Boolean = false,
    onChanged: () -> Unit = {},
) {
    if (remover == null) return
    val toaster = LocalToaster.current
    val nav = LocalNavigator.current
    val changes = app.masroufy.ui.app.LocalDataChanges.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf<RemovalAsk?>(null) }
    var removed by remember { mutableStateOf<Removed?>(null) }
    val window = undoWindowFor(removed) {
        val gone = removed
        if (gone != null) afterDone?.invoke(gone.text) ?: nav.pop()
    }
    val r = removed
    if (r != null) {
        UndoStrip(r.text, window) {
            removed = null
            scope.launch { toaster.show(runCatching { r.undo() }.exceptionOrNull()?.message ?: t(UiKey.OPS_BULK_UNDONE)); onChanged() }
        }
    } else {
        TonalButton(t(R06Key.DEL_ACTION), {
            scope.launch {
                try {
                    val a = remover.ask(id)
                    val refused = a.refused
                    if (refused != null) toaster.show(refused) else ask = a
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toaster.show(e.message ?: t(UiKey.SHELL_LOAD_FAILED))
                }
            }
        }, modifier.fillMaxWidth(), muted = true)
    }
    DeleteSheet(ask, onDismiss = { ask = null }) { choice, moveTo ->
        val a = ask
        ask = null
        if (a != null) scope.launch {
            try {
                val done = remover.remove(id, choice, moveTo)
                if (leaveOnDelete && choice != RemoveChoice.ARCHIVE) {
                    // r06-fix055b: الصفحة نفسها اتمسحت ⇒ رجوع للقايمة و«تراجع» في شريط الشل (بيعيش بعد قفل الصفحة)
                    nav.pop()
                    changes.changed()
                    val undone = t(UiKey.OPS_BULK_UNDONE)
                    toaster.showUndo(doneText(done), t(UiKey.OPS_BULK_UNDO)) {
                        val failed = runCatching { done.undo() }.exceptionOrNull()?.message
                        changes.changed()
                        failed ?: undone
                    }
                } else {
                    removed = Removed(doneText(done)) { done.undo() }
                    onChanged()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toaster.show(e.message ?: t(UiKey.SHELL_LOAD_FAILED))
            }
        }
    }
}

/** نص الشريط بعد المسح أو الأرشفة أو النقل. */
internal fun doneText(done: RemovalDone): String = when (done.choice) {
    RemoveChoice.ARCHIVE -> t(R06Key.DEL_ARCHIVED, done.name)
    RemoveChoice.MOVE_OPERATIONS -> t(R06Key.DEL_MOVED, done.name)
    RemoveChoice.UNARCHIVE -> t(R06Key.DEL_UNARCHIVED, done.name)
    else -> t(R06Key.DEL_DONE, done.name)
}

/** اللوحة الواحدة: من غير عمليات ⇒ «امسح» · بعمليات ⇒ الاختيارات اللي الحاجة بتدعمها (والمقفول بيقول ليه). */
@Composable
internal fun DeleteSheet(ask: RemovalAsk?, onDismiss: () -> Unit, onPick: (RemoveChoice, String?) -> Unit) {
    val title = when {
        ask == null -> ""
        ask.linkedCount == 0 -> t(R06Key.DEL_TITLE, ask.name)
        else -> t(R06Key.DEL_LINKED_TITLE, ask.name, "${ask.linkedCount}")
    }
    var moveTo by remember(ask) { mutableStateOf<String?>(null) }
    Sheet(ask != null, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE)) {
        if (ask != null) {
            BasicText(title, style = Type.section())
            BasicText(t(if (ask.linkedCount == 0) R06Key.DEL_BODY else R06Key.DEL_LINKED_BODY), style = Type.of(14).copy(color = Ink.soft))
            if (ask.linkedCount == 0) DangerButton(t(R06Key.DEL_CONFIRM), { onPick(RemoveChoice.DELETE, null) }, Modifier.fillMaxWidth())
            for (c in ask.choices.takeIf { ask.linkedCount > 0 }.orEmpty()) {
                when (c) {
                    RemoveChoice.ARCHIVE -> TonalButton(t(R06Key.DEL_ARCHIVE), { onPick(c, null) }, Modifier.fillMaxWidth())
                    RemoveChoice.KEEP_OPERATIONS -> {
                        DangerButton(t(R06Key.DEL_KEEP_OPS), { onPick(c, null) }, Modifier.fillMaxWidth(), enabled = ask.keepBlocked == null)
                        ask.keepBlocked?.let { BasicText(it, style = Type.of(13).copy(color = Ink.soft)) }
                    }
                    RemoveChoice.WITH_OPERATIONS -> DangerButton(t(R06Key.DEL_WITH_OPS), { onPick(c, null) }, Modifier.fillMaxWidth())
                    RemoveChoice.MOVE_OPERATIONS -> {
                        BasicText(t(R06Key.DEL_MOVE_PICK), style = Type.of(13).copy(color = Ink.soft))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (target in ask.targets) SelectChip(target.name, moveTo == target.id, { moveTo = target.id })
                        }
                        TonalButton(t(R06Key.DEL_MOVE_OPS), { moveTo?.let { onPick(c, it) } }, Modifier.fillMaxWidth(), enabled = moveTo != null)
                    }
                    RemoveChoice.DELETE, RemoveChoice.UNARCHIVE -> Unit
                }
            }
            TonalButton(t(UiKey.DUES_CANCEL), onDismiss, Modifier.fillMaxWidth(), muted = true)
        }
    }
}
