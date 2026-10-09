package app.masroufy.ui.shell.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.IconButton44
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.ShadowLayer
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.delay

private val listShadow = listOf(
    ShadowLayer(0.dp, 1.dp, 0.dp, Color.White, inset = true),
    ShadowLayer(0.dp, 1.dp, 2.dp, Color(0x0D1D3635)),
    ShadowLayer(0.dp, 10.dp, 24.dp, Color(0x121D3635)),
)

/**
 * سجل المحادثات (`AssistantHistory` — لوحة من تحت بزاوية 28): بحث · المحادثات (كلها «اليوم» لأنها في الذاكرة بس) بعنوانها وأول رد وعلامة
 * «الحالية» · تفتح واحدة · تمسح بتراجع. ⚠️ كارت «ما تعلّمته عنك» ومفتاحه **ما اتبنوش**: التعلّم من الأسئلة مالوش حالة استخدام ولا مكان تخزين
 * (❓ مستني المالك — آخر §76) ⇒ مش بنعرض مفتاح ما بيعملش حاجة.
 */
@Composable
internal fun AssistantHistory(visible: Boolean, state: AskState, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var undo by remember { mutableStateOf<Conversation?>(null) }
    LaunchedEffect(undo) { if (undo != null) { delay(5000); undo = null } }
    val title = t(TextKey.ASK_HISTORY)
    Sheet(visible, onClose, title, corner = Radius.menu, closeLabel = t(TextKey.ASK_HISTORY_CLOSE), spacing = 12.dp) {
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, Modifier.weight(1f).semantics { heading() }, style = Type.section())
            IconButton44(Lucide.X, t(TextKey.ASK_HISTORY_CLOSE), onClose)
        }
        SearchBox(query) { query = it }
        val q = query.trim()
        val rows = state.history.filter { q.isEmpty() || (it.title + " " + it.firstReply).contains(q) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (rows.isEmpty()) item {
                BasicText(
                    t(if (q.isEmpty()) TextKey.ASK_HISTORY_EMPTY else TextKey.ASK_HISTORY_NO_MATCH),
                    Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp),
                    style = Type.body().copy(color = Ink.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                )
            } else {
                item { BasicText(t(TextKey.ASK_HISTORY_TODAY), Modifier.padding(horizontal = 4.dp).semantics { heading() }, style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted)) }
                item {
                    val shape = RoundedCornerShape(20.dp)
                    Column(Modifier.fillMaxWidth().layeredShadow(shape, listShadow).clip(shape).background(Color.White).innerSheen(shape, listShadow).padding(start = 2.dp, end = 4.dp)) {
                        rows.forEachIndexed { i, c ->
                            HistoryRow(c, current = c.id == state.current.id, onOpen = { state.open(c.id); onClose() }) { undo = state.delete(c.id) }
                            if (i < rows.lastIndex) Divider()
                        }
                    }
                }
            }
        }
        undo?.let { gone -> UndoBar(t(TextKey.ASK_HISTORY_DELETED, gone.title)) { state.restore(gone); undo = null } }
    }
}

@Composable
private fun HistoryRow(c: Conversation, current: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val press = rememberPress()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).heightIn(min = 64.dp).tap(press, label = c.title, onClick = onOpen).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(c.title, Modifier.weight(1f, fill = false), style = Type.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (current) BasicText(
                    t(TextKey.ASK_HISTORY_CURRENT),
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Ink.selected).padding(horizontal = 8.dp, vertical = 1.dp),
                    style = Type.of(11, FontWeight.Bold).copy(color = Ink.primary),
                )
            }
            BasicText(c.firstReply, style = Type.caption().copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val label = t(TextKey.ASK_HISTORY_DELETE, c.title)
        val del = rememberPress()
        Box(Modifier.padding(4.dp).clip(RoundedCornerShape(14.dp)).tap(del, label = label, onClick = onDelete).semantics { contentDescription = label }.padding(13.dp)) {
            LucideIcon(Lucide.TRASH_2, size = 18.dp, tint = Ink.muted)
        }
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    val hint = t(TextKey.ASK_HISTORY_SEARCH)
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x0D193D33)).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.SEARCH, size = 18.dp, tint = Ink.muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) BasicText(hint, style = Type.body().copy(color = Ink.muted), maxLines = 1)
            BasicTextField(
                query, onQuery, singleLine = true, textStyle = Type.body(), cursorBrush = SolidColor(Ink.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            )
        }
    }
}

/** «اتمسحت «…»» + «تراجع» (٥ ثواني) — زي النموذج. */
@Composable
private fun UndoBar(text: String, onUndo: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xEB193D33)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, Modifier.weight(1f), style = Type.of(13, FontWeight.Bold).copy(color = Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
        val press = rememberPress()
        val label = t(TextKey.ASK_HISTORY_UNDO)
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1AFFFFFF)).tap(press, label = label, onClick = onUndo).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText(label, style = Type.of(13, FontWeight.Bold).copy(color = Ink.mint)) }
    }
}
