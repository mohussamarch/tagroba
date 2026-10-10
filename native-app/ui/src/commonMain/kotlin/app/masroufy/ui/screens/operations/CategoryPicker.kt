package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.CATEGORY_GROUPS
import app.masroufy.core.Category
import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.parseHex
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Type

/**
 * التصنيفات اللي بتتعرض في اللوحة: الأساسية الشغالة (المقفول ما بيظهرش — §28.1) بترتيبها المحفوظ، وللمصروف مجموعات الصرف بس.
 * التصنيف الحالي بيظهر دايمًا (حتى لو مقفول أو فرعي) عشان يبان اختيارك.
 */
fun pickerChoices(categories: List<Category>, view: DetailView): List<Category> =
    pickerChoices(categories, view.transaction.observedDirection == Direction.OUT, view.category?.categoryId)

/** [spendOnly] = مصروف ⇒ مجموعات الصرف بس. [currentId] التصنيف الحالي (بيظهر دايمًا). */
fun pickerChoices(categories: List<Category>, spendOnly: Boolean, currentId: Id?): List<Category> {
    val spend = CATEGORY_GROUPS.filter { it.kind == "spend" }.map { it.key }.toSet()
    val mains = categories.filter { it.active && it.parentId == null && (!spendOnly || it.groupKey == null || it.groupKey in spend) }.sortedBy { it.order }
    val current = currentId?.let { id -> categories.firstOrNull { it.id == id } }
    return if (current == null || mains.any { it.id == current.id }) mains else listOf(current) + mains
}

/**
 * لوحة اختيار التصنيف (`CategoryPicker` — جوه `OperationDetail` و`BankSms`): شرايح بلون كل تصنيف، والمختار أخضر. الاختيار بيقفل اللوحة.
 * من غير تصنيفات ⇒ الحالة الفاضية.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    visible: Boolean,
    title: String,
    choices: List<Category>,
    selectedId: Id?,
    onDismiss: () -> Unit,
    body: String? = null,
    onPick: (Category) -> Unit,
) {
    Sheet(visible, onDismiss, title = title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(title, style = Type.of(17, androidx.compose.ui.text.font.FontWeight.Bold))
        if (body != null) BasicText(body, style = Type.of(13).copy(color = app.masroufy.ui.theme.Ink.muted))
        if (choices.isEmpty()) {
            EmptyState(t(UiKey.CATEGORY_PICKER_EMPTY))
            return@Sheet
        }
        FlowRow(
            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (c in choices) SelectChip(c.name, c.id == selectedId, { onPick(c) }, dot = parseHex(c.lightColor), height = 44.dp)
        }
    }
}
