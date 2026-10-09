package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.Tag
import app.masroufy.core.TextKey
import app.masroufy.core.normalizeText
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.MAX_TAG_NAME
import kotlinx.coroutines.launch

/** الوسوم اللي مش على العملية، ومتصفّية بالكلام المكتوب (بالاسم المطبّع — نفس مطابقة `EditTransaction.addTag`). */
fun offTags(all: List<Tag>, on: List<Tag>, query: String): List<Tag> {
    val q = normalizeText(query)
    val onIds = on.map { it.id }.toSet()
    return all.filter { it.id !in onIds && (q.isEmpty() || it.normalizedName.contains(q)) }
}

/** رسالة بعد الإضافة: موجود على العملية · أُضيف · وسم جديد وأُضيف. */
fun tagAddedStatus(name: String, wasOnTxn: Boolean, existed: Boolean): String = when {
    wasOnTxn -> t(TextKey.TAGS_SHEET_ALREADY, name)
    existed -> t(TextKey.TAGS_SHEET_ADDED, name)
    else -> t(TextKey.TAGS_SHEET_CREATED, name)
}

/**
 * سطر «الوسوم» جوه كارت التفاصيل + لوحته (`TagsSheet`): الإضافة والشيل فوري (`EditTransaction.addTag`/`removeTag`)، والاسم المكرر بيرجع للوسم
 * الموجود. الوسم للبحث والتقارير بس — ما بيغيّرش المبلغ ولا بيتحسب مرتين، ومشترك بين البلاد.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsRow(transactionId: Id, tags: List<Tag>, onChanged: () -> Unit) {
    val deps = LocalSpace.current.operations
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var on by remember(tags) { mutableStateOf(tags) }
    var all by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(open) { if (open) all = attempt { deps.edit.listTags() }.orEmpty() }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(t(TextKey.TAGS_SHEET_LABEL), style = Type.caption().copy(color = Ink.muted))
            if (on.isEmpty()) BasicText(t(TextKey.TAGS_SHEET_NONE), style = Type.of(15, FontWeight.Bold).copy(color = Ink.muted))
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { for (tag in on) TagPill(tag.displayName) }
        }
        TonalButton(t(if (on.isEmpty()) TextKey.TAGS_SHEET_ADD_TRIGGER else TextKey.TAGS_SHEET_EDIT), { open = true; text = ""; error = null; status = "" }, height = 44.dp)
    }
    fun add(raw: String) = scope.launch {
        val name = raw.trim()
        val existed = all.any { it.normalizedName == normalizeText(name) }
        val wasOn = on.any { it.normalizedName == normalizeText(name) }
        var added: Tag? = null
        val err = failureOf { added = deps.edit.addTag(transactionId, name) }
        if (err != null) {
            error = err
            return@launch
        }
        val tag = added ?: return@launch
        if (!wasOn) on = on + tag
        if (!existed) all = all + tag
        text = ""
        error = null
        status = tagAddedStatus(tag.displayName, wasOn, existed)
        onChanged()
    }
    Sheet(open, { open = false }, title = t(TextKey.TAGS_SHEET_TITLE), closeLabel = t(TextKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(t(TextKey.TAGS_SHEET_TITLE), style = Type.of(17, FontWeight.Bold))
        BasicText(t(TextKey.TAGS_SHEET_BODY), style = Type.of(13).copy(color = Ink.muted))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (on.isEmpty()) BasicText(t(TextKey.TAGS_SHEET_EMPTY_LINE), style = Type.of(13).copy(color = Ink.muted))
            for (tag in on) RemovableTag(tag.displayName, t(TextKey.TAGS_SHEET_REMOVE, tag.displayName)) {
                scope.launch {
                    val err = failureOf { deps.edit.removeTag(transactionId, tag.id) }
                    if (err != null) error = err else {
                        on = on.filter { it.id != tag.id }
                        status = t(TextKey.TAGS_SHEET_REMOVED, tag.displayName)
                        onChanged()
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            TextInput(text, { if (it.length <= MAX_TAG_NAME) text = it; error = null }, Modifier.weight(1f), placeholder = t(TextKey.TAGS_SHEET_INPUT))
            PrimaryButton(t(TextKey.TAGS_SHEET_ADD), onClick = { add(text) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            error?.let { FieldError(it, Modifier.weight(1f)) } ?: BasicText("", Modifier.weight(1f))
            BasicText(t(TextKey.TAGS_SHEET_COUNTER, sentenceNumber(text.length), sentenceNumber(MAX_TAG_NAME)), style = Type.caption().copy(color = Ink.muted))
        }
        BasicText(t(TextKey.TAGS_SHEET_ALL), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        val off = offTags(all, on, text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (off.isEmpty()) BasicText(t(if (text.isBlank()) TextKey.TAGS_SHEET_ALL_ON else TextKey.TAGS_SHEET_NO_MATCH), style = Type.of(13).copy(color = Ink.muted))
            for (tag in off) AddableTag(tag.displayName, t(TextKey.TAGS_SHEET_ADD_ONE, tag.displayName)) { add(tag.displayName) }
        }
        BasicText(status, Modifier.heightIn(min = 18.dp), style = Type.of(12, FontWeight.Bold).copy(color = Ink.primary))
        PrimaryButton(t(TextKey.TAGS_SHEET_DONE), onClick = { open = false }, modifier = Modifier.fillMaxWidth())
    }
}

/** شريحة وسم صغيرة (أخضر فاتح ورمز وسم). */
@Composable
private fun TagPill(name: String) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Ink.selected).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.TAG, size = 12.dp, tint = Ink.primary)
        BasicText(name, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
    }
}

/** وسم على العملية جوه اللوحة: 44 بزاوية 14 و«×» لشيله. */
@Composable
private fun RemovableTag(name: String, label: String, onRemove: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.heightIn(min = 44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(Ink.selected)
            .tap(press, label = label, onClick = onRemove).padding(start = 14.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(name, style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary))
        LucideIcon(Lucide.X, size = 16.dp, tint = Ink.primary)
    }
}

/** وسم من «وسومك» مش على العملية: «+» واسمه على خلفية رمادي خفيفة. */
@Composable
private fun AddableTag(name: String, label: String, onAdd: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.heightIn(min = 44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(Color(0x0F193D33))
            .tap(press, label = label, onClick = onAdd).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.PLUS, size = 14.dp, tint = Ink.text)
        BasicText(name, style = Type.of(14))
    }
}
