package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.Id
import app.masroufy.core.Project
import app.masroufy.core.UiKey
import app.masroufy.ui.components.ListRow
import app.masroufy.ui.components.NotAvailableBadge
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.dues.ConfirmSheet
import app.masroufy.ui.screens.home.kindLabel
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.BULK_KINDS
import app.masroufy.usecase.BulkAction
import app.masroufy.usecase.BulkResult
import app.masroufy.usecase.BulkUndo
import kotlinx.coroutines.launch

/** نتيجة فعل جماعي للشاشة: الجملة اللي تتعرض و«تراجع» للدفعة كلها (`null` = مفيش حاجة اتكتبت). */
data class BulkDone(val text: String, val undo: BulkUndo?)

/**
 * لوحات الأفعال الجماعية في «العمليات» (قايمة المالك §6.5 بند 15): التصنيف (نفس `CategoryPicker`) · النوع ([BULK_KINDS] بس) ·
 * ضيف لمشروع (موجود أو جديد) · وسم · تأكيد المسح. كل فعل نداء واحد لـ`BulkTransactions` (CLAUDE.md #4)، والفشل رسالة صغيرة من غير ما
 * الوضع يتقفل. القفل برّه اللوحة أو بالرجوع ما بيكتبش حاجة.
 */
@Composable
internal fun BulkSheets(open: BulkAction?, deps: OperationsDeps, ids: List<Id>, deletable: Int, onDismiss: () -> Unit, onDone: (BulkDone) -> Unit) {
    val bulk = deps.bulk ?: return
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var busy by remember { mutableStateOf(false) }
    fun launchBulk(deleting: Boolean = false, op: suspend () -> BulkResult) {
        if (busy) return
        busy = true
        scope.launch {
            var result: BulkResult? = null
            val err = failureOf { result = op() }
            busy = false
            val r = result
            if (err != null || r == null) toaster.show(err ?: t(UiKey.OPERATIONS_SAVE_FAILED)) else onDone(BulkDone(doneText(r, deleting), r.undo))
        }
    }

    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    LaunchedEffect(open) {
        when (open) {
            BulkAction.CATEGORY -> cats = attempt { deps.categories.list() }.orEmpty()
            BulkAction.PROJECT -> {
                name = ""
                projects = attempt { deps.projects.list().active.map { it.project } }
            }
            BulkAction.TAG -> tag = ""
            else -> Unit
        }
    }

    CategoryPicker(
        visible = open == BulkAction.CATEGORY,
        title = t(UiKey.OPS_BULK_CATEGORY_TITLE),
        choices = pickerChoices(cats, spendOnly = false, currentId = null),
        selectedId = null,
        onDismiss = onDismiss,
    ) { cat -> launchBulk { bulk.setCategory(ids, cat.id) } }

    val kindTitle = t(UiKey.OPS_BULK_KIND_TITLE)
    Sheet(open == BulkAction.KIND, onDismiss, kindTitle, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(kindTitle, style = Type.section())
        BasicText(t(UiKey.OPS_BULK_KIND_NOTE), style = Type.of(13).copy(color = Ink.muted))
        for (line in BULK_KINDS.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in line) SelectChip(kindLabel(k), false, { launchBulk { bulk.setKind(ids, k) } }, enabled = !busy)
            }
        }
    }

    val projectTitle = t(UiKey.OPS_BULK_PROJECT_TITLE)
    Sheet(open == BulkAction.PROJECT, onDismiss, projectTitle, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(projectTitle, style = Type.section())
        val list = projects
        when {
            list == null -> NotAvailableBadge()
            list.isEmpty() -> BasicText(t(UiKey.OPS_BULK_PROJECT_NONE), style = Type.of(13).copy(color = Ink.muted))
            else -> for (p in list) ListRow(p.name, onClick = { launchBulk { bulk.addToProject(ids, p.id) } }, chevron = false)
        }
        TextInput(name, { name = it }, Modifier.fillMaxWidth(), label = t(UiKey.OPS_BULK_PROJECT_NEW))
        PrimaryButton(
            t(UiKey.OPS_BULK_PROJECT_CREATE),
            {
                val n = name
                launchBulk { bulk.addToProject(ids, deps.projects.create(n).id) }
            },
            Modifier.fillMaxWidth(), enabled = name.isNotBlank() && !busy, loading = busy,
        )
    }

    val tagTitle = t(UiKey.OPS_BULK_TAG_TITLE)
    Sheet(open == BulkAction.TAG, onDismiss, tagTitle, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(tagTitle, style = Type.section())
        TextInput(tag, { tag = it }, Modifier.fillMaxWidth(), label = t(UiKey.OPS_BULK_TAG_INPUT))
        PrimaryButton(
            t(UiKey.OPS_BULK_TAG_ADD),
            {
                val n = tag
                launchBulk { bulk.addTag(ids, n) }
            },
            Modifier.fillMaxWidth(), enabled = tag.isNotBlank() && !busy, loading = busy,
        )
    }

    ConfirmSheet(
        visible = open == BulkAction.DELETE,
        title = t(UiKey.OPS_BULK_DELETE_TITLE, "$deletable"),
        body = t(UiKey.OPS_BULK_DELETE_BODY),
        confirm = t(UiKey.OPS_BULK_DELETE),
        onConfirm = { launchBulk(deleting = true) { bulk.delete(ids) } },
        onDismiss = onDismiss,
    )
}

private fun doneText(r: BulkResult, deleting: Boolean): String = when {
    r.changed == 0 -> t(UiKey.OPS_BULK_NOTHING)
    deleting -> t(UiKey.OPS_BULK_DELETED, "${r.changed}")
    r.skipped > 0 -> t(UiKey.OPS_BULK_DONE_SKIPPED, "${r.changed}", "${r.skipped}")
    else -> t(UiKey.OPS_BULK_DONE, "${r.changed}")
}
