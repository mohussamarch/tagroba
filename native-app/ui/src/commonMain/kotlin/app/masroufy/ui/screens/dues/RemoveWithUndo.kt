package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.operations.UndoStrip
import app.masroufy.ui.screens.operations.undoWindowFor
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** اللي اتشال: نص الشريط و«تراجع» (من حالة الاستخدام). */
internal class Removed(val text: String, val undo: suspend () -> Unit)

/**
 * r06-links — «امسح/أرشف» بنفس طريق مسح العملية (OVERRIDES §91-11): زرار ⇒ تأكيد بيقول هيحصل إيه ⇒ حالة الاستخدام ⇒ شريط «تراجع» 8 ثواني
 * مكان الزرار ⇒ رجوع للقايمة لما الشباك يخلص. الرفض (مثلًا خطة عليها دفعات) بيطلع رسالته. الشاشة ما بتكتبش حاجة بنفسها.
 */
@Composable
internal fun RemoveWithUndo(label: String, title: String, body: String, remove: suspend () -> Removed, onUndone: () -> Unit = {}) {
    val toaster = LocalToaster.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var removed by remember { mutableStateOf<Removed?>(null) }
    val window = undoWindowFor(removed) { if (removed != null) nav.pop() }
    val r = removed
    if (r != null) {
        UndoStrip(r.text, window) {
            removed = null
            scope.launch { toaster.show(runCatching { r.undo() }.exceptionOrNull()?.message ?: t(UiKey.OPS_BULK_UNDONE)); onUndone() }
        }
    } else {
        TonalButton(label, { asking = true }, Modifier.fillMaxWidth(), muted = true)
    }
    ConfirmSheet(asking, title, body, t(UiKey.ERASE_CONFIRM), onConfirm = {
        asking = false
        scope.launch {
            try {
                removed = remove()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toaster.show(e.message ?: t(UiKey.SHELL_LOAD_FAILED))
            }
        }
    }, onDismiss = { asking = false })
}

/** خطة التقسيط: مسح لو مالهاش دفعات (`RemoveDues.deletePlan`) — عليها دفعات ⇒ رسالة الرفض بتقول ليه. */
@Composable
internal fun PlanRemove(remove: app.masroufy.usecase.RemoveDues, planId: String, name: String) = RemoveWithUndo(
    t(app.masroufy.core.R06Key.REMOVE_PLAN_ACTION), t(app.masroufy.core.R06Key.REMOVE_PLAN_TITLE, name), t(app.masroufy.core.R06Key.REMOVE_PLAN_BODY),
    remove = {
        val plan = remove.deletePlan(planId)
        Removed(t(app.masroufy.core.R06Key.REMOVE_PLAN_DONE, name)) { remove.restorePlan(plan) }
    },
)

/** الشخص: أرشفة بس (`ManagePeople.archivePerson` — سجله وديونه زي ما هم)، والتراجع بيرجّعه. */
@Composable
internal fun PersonArchive(personId: String, name: String) {
    val people = app.masroufy.ui.app.LocalSpace.current.people.people
    androidx.compose.foundation.layout.Column(
        Modifier.padding(horizontal = app.masroufy.ui.theme.Space.gutter),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        RemoveWithUndo(
            t(app.masroufy.core.R06Key.PERSON_ARCHIVE_ACTION), t(app.masroufy.core.R06Key.PERSON_ARCHIVE_TITLE, name), t(app.masroufy.core.R06Key.PERSON_ARCHIVE_BODY),
            remove = {
                people.archivePerson(personId, true)
                Removed(t(app.masroufy.core.R06Key.PERSON_ARCHIVE_DONE, name)) { people.archivePerson(personId, false) }
            },
        )
        // r06-delete (§118-1): «امسح» — الديون المفتوحة بتقفل «امسحه وسيب العمليات» وبتقول ليه
        DeleteWithChoices(app.masroufy.ui.app.LocalSpace.current.shell.removals?.person, personId)
    }
}

