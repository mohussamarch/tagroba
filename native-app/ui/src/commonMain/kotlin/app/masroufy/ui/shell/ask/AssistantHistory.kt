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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.AskKey
import app.masroufy.core.HistoryGroup
import app.masroufy.core.UiKey
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
import app.masroufy.ui.screens.more.ToggleSwitch
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.HistoryRow

private val listShadow = listOf(
    ShadowLayer(0.dp, 1.dp, 0.dp, Color.White, inset = true),
    ShadowLayer(0.dp, 1.dp, 2.dp, Color(0x0D1D3635)),
    ShadowLayer(0.dp, 10.dp, 24.dp, Color(0x121D3635)),
)

/**
 * سجل المحادثات (`AssistantHistory` — لوحة من تحت بزاوية 28) من المحرك: بحث · «اللي اتعلمته عنك» (كل بند بـ«×» · «امسح الكل» بتأكيد · مفتاح
 * التعلّم · «أسئلة لم أفهمها بعد (n)» آخر ٥ بـ«×» و«تراجع» ٤ ثواني و«انسخ القائمة») · المحادثات بمجموعاتها (النهارده · امبارح · الأسبوع ده ·
 * أقدم) — تفتح واحدة للقراية · تمسح بتراجع. المحادثات بتتمسح لوحدها بعد ٣ شهور (المحرك).
 */
@Composable
internal fun AssistantHistory(p: AskPresenter, onClose: () -> Unit, onCopied: (String) -> Unit, go: (suspend () -> Unit) -> Unit) {
    var query by remember { mutableStateOf("") }
    val title = t(UiKey.ASK_HISTORY)
    Sheet(p.historyOpen, onClose, title, corner = Radius.menu, closeLabel = t(UiKey.ASK_HISTORY_CLOSE), spacing = 12.dp) {
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, Modifier.weight(1f).semantics { heading() }, style = Type.section())
            IconButton44(Lucide.X, t(UiKey.ASK_HISTORY_CLOSE), onClose)
        }
        SearchBox(query) { query = it }
        val q = query.trim()
        val rows = p.history.filter { q.isEmpty() || (it.conversation.title + " " + it.conversation.firstReply).contains(q) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (q.isEmpty()) item(key = "memory") { MemoryCard(p, onCopied, go) }
            if (rows.isEmpty()) item(key = "empty") {
                BasicText(
                    t(if (q.isEmpty()) UiKey.ASK_HISTORY_EMPTY else UiKey.ASK_HISTORY_NO_MATCH),
                    Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp),
                    style = Type.body().copy(color = Ink.muted, textAlign = TextAlign.Center),
                )
            }
            for (group in HistoryGroup.entries) {
                val inGroup = rows.filter { it.group == group }
                if (inGroup.isEmpty()) continue
                item(key = "g-${group.name}") {
                    BasicText(t(groupLabel(group)), Modifier.padding(horizontal = 4.dp).semantics { heading() }, style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
                }
                item(key = "l-${group.name}") {
                    val shape = RoundedCornerShape(20.dp)
                    Column(Modifier.fillMaxWidth().layeredShadow(shape, listShadow).clip(shape).background(Color.White).innerSheen(shape, listShadow).padding(start = 2.dp, end = 4.dp)) {
                        inGroup.forEachIndexed { i, row ->
                            HistoryRowView(row, current = row.conversation.id == p.view?.conversation?.id, onOpen = { go { p.openPast(row.conversation.id) } }) {
                                go { p.deleteConversation(row) }
                            }
                            if (i < inGroup.lastIndex) Divider()
                        }
                    }
                }
            }
            item(key = "auto") { BasicText(t(AskKey.CHAT_HISTORY_AUTO_DELETE), Modifier.padding(horizontal = 4.dp), style = Type.caption().copy(color = Ink.muted)) }
        }
        p.undo?.let { u -> UndoBar(u.message) { go { p.runUndo() } } }
    }
}

private fun groupLabel(g: HistoryGroup): AskKey = when (g) {
    HistoryGroup.TODAY -> AskKey.CHAT_HISTORY_TODAY
    HistoryGroup.YESTERDAY -> AskKey.CHAT_HISTORY_YESTERDAY
    HistoryGroup.THIS_WEEK -> AskKey.CHAT_HISTORY_THIS_WEEK
    HistoryGroup.OLDER -> AskKey.CHAT_HISTORY_OLDER
}

/** «اللي اتعلمته عنك» + مفتاح التعلّم + الأسئلة اللي ما اتفهمتش — كله من المحرك (`AssistantMemory` · `AssistantUnknownLog`). */
@Composable
private fun MemoryCard(p: AskPresenter, onCopied: (String) -> Unit, go: (suspend () -> Unit) -> Unit) {
    val m = p.memory ?: return
    val clipboard = LocalClipboardManager.current
    var confirming by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().layeredShadow(shape, listShadow).clip(shape).background(Color.White).innerSheen(shape, listShadow).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(t(AskKey.CHAT_MEMORY_TITLE), Modifier.semantics { heading() }, style = Type.bodyBold())
        // اسم المفتاح ظاهر جنبه في نفس السطر (في المحاكي كان المفتاح لوحده في سطر من غير اسم مكتوب)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(t(AskKey.CHAT_MEMORY_LEARNING), Modifier.weight(1f), style = Type.body())
            ToggleSwitch(m.learningOn, t(AskKey.CHAT_MEMORY_LEARNING)) { go { p.setLearning(!m.learningOn) } }
        }
        BasicText(t(AskKey.CHAT_MEMORY_LEARNING_NOTE), style = Type.caption().copy(color = Ink.muted))
        if (m.items.isEmpty()) BasicText(t(AskKey.CHAT_MEMORY_EMPTY), style = Type.body().copy(color = Ink.muted))
        for (item in m.items) {
            RemovableLine(item.text, item.sourceLabel, t(AskKey.CHAT_MEMORY_FORGET, item.text)) { go { p.forget(item) } }
        }
        if (m.items.isNotEmpty() && !confirming) SmallAction(t(AskKey.CHAT_MEMORY_CLEAR_ALL)) { confirming = true }
        if (confirming) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.selected).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(t(AskKey.CHAT_MEMORY_CLEAR_TITLE), style = Type.bodyBold())
                BasicText(t(AskKey.CHAT_MEMORY_CLEAR_BODY), style = Type.caption().copy(color = Ink.soft))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(t(AskKey.CHAT_MEMORY_CLEAR_YES), primary = true) { confirming = false; go { p.clearMemory() } }
                    SmallAction(t(AskKey.CHAT_MEMORY_CLEAR_NO)) { confirming = false }
                }
            }
        }
        if (p.unknownCount > 0) {
            Divider()
            BasicText(t(AskKey.CHAT_UNKNOWN_TITLE, p.unknownCount.toString()), style = Type.bodyBold())
            for (question in p.unknown) {
                RemovableLine(question.text, null, t(AskKey.CHAT_UNKNOWN_REMOVE, question.text)) { go { p.removeUnknown(question) } }
            }
            val copied = t(AskKey.CHAT_UNKNOWN_COPIED)
            SmallAction(t(AskKey.CHAT_UNKNOWN_COPY)) {
                go { clipboard.setText(AnnotatedString(p.unknownText())); onCopied(copied) }
            }
        }
    }
}

/** سطر بـ«×» (بند اتعلمه · سؤال ما اتفهمش). */
@Composable
private fun RemovableLine(text: String, sub: String?, removeLabel: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(text, style = Type.body(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) BasicText(sub, style = Type.caption().copy(color = Ink.muted), maxLines = 1)
        }
        val press = rememberPress()
        Box(Modifier.clip(RoundedCornerShape(14.dp)).tap(press, label = removeLabel, onClick = onRemove).semantics { contentDescription = removeLabel }.padding(13.dp)) {
            LucideIcon(Lucide.X, size = 18.dp, tint = Ink.muted)
        }
    }
}

@Composable
private fun HistoryRowView(row: HistoryRow, current: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val c = row.conversation
    val press = rememberPress()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).heightIn(min = 64.dp).tap(press, label = c.title, onClick = onOpen).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(c.title, Modifier.weight(1f, fill = false), style = Type.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (current) BasicText(
                    t(UiKey.ASK_HISTORY_CURRENT),
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Ink.selected).padding(horizontal = 8.dp, vertical = 1.dp),
                    style = Type.of(11, FontWeight.Bold).copy(color = Ink.primary),
                )
            }
            BasicText(c.firstReply, style = Type.caption().copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val label = t(UiKey.ASK_HISTORY_DELETE, c.title)
        val del = rememberPress()
        Box(Modifier.padding(4.dp).clip(RoundedCornerShape(14.dp)).tap(del, label = label, onClick = onDelete).semantics { contentDescription = label }.padding(13.dp)) {
            LucideIcon(Lucide.TRASH_2, size = 18.dp, tint = Ink.muted)
        }
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    val hint = t(UiKey.ASK_HISTORY_SEARCH)
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
