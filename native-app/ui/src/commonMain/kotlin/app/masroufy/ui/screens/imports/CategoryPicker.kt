package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.CATEGORY_GROUPS
import app.masroufy.core.Category
import app.masroufy.core.TextKey
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.parseHex
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * اختيار التصنيف (`CategoryPicker` — مرسومة جوه `BankSms` و`OperationDetail`): مجموعة ← أساسي ← فرعي باللون، والمخفي ما بيظهرش هنا،
 * وبحث بالاسم («لا نتائج» لو مفيش). الاختيار بيرجع التصنيف — اللي بيفتح اللوحة هو اللي بيحفظ.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerSheet(visible: Boolean, title: String, categories: List<Category>, selectedId: String?, onPick: (Category) -> Unit, onDismiss: () -> Unit) {
    var query by remember(visible) { mutableStateOf("") }
    Sheet(visible, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE)) {
        BasicText(title, style = Type.of(17, androidx.compose.ui.text.font.FontWeight.Bold))
        TextInput(query, { query = it }, placeholder = t(UiKey.SMS_CATPICK_SEARCH))
        val groups = categoryGroups(pickableCategories(categories), query)
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (groups.isEmpty()) BasicText(t(UiKey.SMS_CATPICK_EMPTY), style = Type.body().copy(color = Ink.muted))
            for ((name, list) in groups) {
                if (name != null) BasicText(name, style = Type.captionBold().copy(color = Ink.muted))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in list) SelectChip(c.name, c.id == selectedId, { onPick(c) }, dot = parseHex(c.lightColor), height = 44.dp)
                }
            }
        }
    }
}

/**
 * الظاهر مجمّع بالمجموعة (الأساسي وبعده فرعياته) ومفلتر بالبحث — ترتيب عرض بس. المجموعة = مجموعة الأساسي (الفرعي بياخدها من أبوه);
 * من غير مجموعة (حسابات قديمة) ⇒ آخر حاجة من غير عنوان.
 */
fun categoryGroups(visible: List<Category>, query: String): List<Pair<String?, List<Category>>> {
    val q = query.trim()
    val byId = visible.associateBy { it.id }
    fun groupOf(c: Category): String? = c.groupKey ?: c.parentId?.let { byId[it]?.groupKey }
    fun ordered(list: List<Category>): List<Category> {
        val ids = list.map { it.id }.toSet()
        val mains = list.filter { it.parentId == null || it.parentId !in ids }.sortedBy { it.order }
        return mains.flatMap { m -> listOf(m) + list.filter { it.parentId == m.id }.sortedBy { it.order } }
    }
    val matching = visible.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
    val known = CATEGORY_GROUPS.map { g -> g.name to ordered(matching.filter { groupOf(it) == g.key }) }
    val rest = ordered(matching.filter { c -> CATEGORY_GROUPS.none { it.key == groupOf(c) } })
    return (known + listOf<Pair<String?, List<Category>>>(null to rest)).filter { it.second.isNotEmpty() }
}
