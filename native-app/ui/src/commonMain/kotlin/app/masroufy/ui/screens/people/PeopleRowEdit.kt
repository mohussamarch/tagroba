package app.masroufy.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.core.rowAppend
import app.masroufy.core.rowMove
import app.masroufy.core.rowRemove
import app.masroufy.core.rowRestore
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** مدة شريط «أُزيل «فلان» من الصف — تراجع» (النموذج: 4 ثواني). */
internal const val ROW_UNDO_MS = 4_000L

/** آخر «×» — للتراجع لنفس المكان. [stamp] بيفرّق بين «×» ورا بعض لنفس الشخص (عشان مدة الشريط تبدأ من جديد). */
internal data class RowRemoval(val id: Id, val name: String, val index: Int, val stamp: Long)

/**
 * حالة وضع تعديل صف «أهم 5» (OVERRIDES §84 — `PeopleOrbit` في النموذج): الصف · وضع التعديل · السحب · «×» و«تراجع» · «أضف للصف».
 * **كل تغيير بيتحفظ على طول** ([save] = `ManagePeopleRow.save` — مفيش «إلغاء»). العمليات على الصف نفسها من `core/PeopleRow.kt`.
 * بتعيش بـ`remember` في الشاشة ⇒ تبديل التبويب بيخرج من وضع التعديل لوحده.
 */
@Stable
internal class RowEditor(private val scope: CoroutineScope, private val save: suspend (List<Id>) -> Unit) {
    var ids by mutableStateOf<List<Id>>(emptyList())
        private set
    var custom by mutableStateOf(false)
        private set
    var editing by mutableStateOf(false)
        private set

    /** الترتيب وإنت بتسحب (الباقي بيتزق) — بيتحفظ لما ترفع صباعك. */
    var draft by mutableStateOf<List<Id>?>(null)
        private set
    var lifted by mutableStateOf<Id?>(null)
        private set

    /** مكان الشخص المرفوع لما اترفع (بالـdp من شمال المسرح) + حركة الصباع من ساعتها. */
    var liftOrigin by mutableStateOf(Offset.Zero)
        private set
    var liftDelta by mutableStateOf(Offset.Zero)
        private set
    var undo by mutableStateOf<RowRemoval?>(null)
        private set
    var picking by mutableStateOf(false)
    private var stamps = 0L

    val shown: List<Id> get() = draft ?: ids

    /** من قراية الشاشة. وإنت بتعدّل ما بيتغيرش من تحتك (الترتيب المحلي هو الأحدث). */
    fun load(row: List<Id>, isCustom: Boolean) {
        if (editing || lifted != null) return
        ids = row
        custom = isCustom
    }

    fun enter() {
        editing = true
    }

    fun exit() {
        editing = false
        draft = null
        lifted = null
        picking = false
    }

    fun lift(id: Id, origin: Offset) {
        lifted = id
        liftOrigin = origin
        liftDelta = Offset.Zero
        draft = ids
    }

    /** الصباع اتحرك [delta] — [nearest] = أقرب مكان للشخص المرفوع (هو مكانه الجديد والباقي بيتزق). */
    fun dragTo(delta: Offset, nearest: Int) {
        val id = lifted ?: return
        liftDelta = delta
        draft = rowMove(draft ?: ids, id, nearest)
    }

    fun drop(commit: Boolean) {
        val d = draft
        lifted = null
        draft = null
        if (commit && d != null && d != ids) apply(d)
    }

    fun remove(id: Id, name: String) {
        val i = ids.indexOf(id)
        if (i < 0) return
        apply(rowRemove(ids, id))
        undo = RowRemoval(id, name, i, stamps++)
    }

    fun undoRemove() {
        val u = undo ?: return
        undo = null
        apply(rowRestore(ids, u.id, u.index))
    }

    fun clearUndo(u: RowRemoval) {
        if (undo == u) undo = null
    }

    fun add(id: Id) {
        apply(rowAppend(ids, id))
        picking = false
    }

    private fun apply(next: List<Id>) {
        ids = next
        custom = true
        scope.launch { runCatching { save(next) } }
    }
}

/** «أُزيل «فلان» من الصف — تراجع» (4 ثواني) — نفس الشريط الداكن في النموذج (`People` — `bottom: 148`). */
@Composable
internal fun RowUndoBar(editor: RowEditor, modifier: Modifier = Modifier) {
    val u = editor.undo ?: return
    LaunchedEffect(u) {
        delay(ROW_UNDO_MS)
        editor.clearUndo(u)
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xEB193D33))
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(t(UiKey.PEOPLE_ROW_REMOVED, u.name), Modifier.weight(1f), style = Type.of(13, FontWeight.Bold).copy(color = Color.White), maxLines = 2)
        val press = rememberPress()
        val label = t(UiKey.PEOPLE_ROW_UNDO)
        Box(
            Modifier.height(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF))
                .tap(press, label = label, onClick = editor::undoRemove).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText(label, style = Type.of(13, FontWeight.Bold).copy(color = Ink.selected)) }
    }
}

/**
 * «أضف للصف» (لوحة `PeopleRowPick`): شبكة 4 أعمدة بالناس اللي مش في الصف ([people] — من `ManagePeopleRow.pickable`)،
 * والضغطة بتضيف الشخص في آخر الصف (أول مكان فاضي) وتقفل اللوحة.
 */
@Composable
internal fun PeopleRowPick(editor: RowEditor, people: List<PersonChip>) {
    val title = t(UiKey.PEOPLE_ROW_PICK_TITLE)
    Sheet(editor.picking, { editor.picking = false }, title) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SheetHeading(title)
            BasicText(t(UiKey.PEOPLE_ROW_PICK_SUB), style = Type.of(13).copy(color = Ink.muted))
        }
        if (people.isEmpty()) {
            BasicText(t(UiKey.PEOPLE_ROW_PICK_EMPTY), Modifier.fillMaxWidth().padding(vertical = 12.dp), style = Type.of(13).copy(color = Ink.muted, textAlign = TextAlign.Center))
        } else {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (line in people.chunked(4)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until 4) Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        line.getOrNull(i)?.let { p -> PickCell(p) { editor.add(p.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickCell(p: PersonChip, onClick: () -> Unit) {
    val press = rememberPress()
    Column(
        Modifier.pressScale(press).tap(press, label = t(UiKey.PEOPLE_ROW_PICK_ITEM, p.name), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // نفس دايرة شبكة الناس (`GridPerson`): الزجاج الفاتح بحرف أخضر
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(app.masroufy.ui.glass.Glass.card).border(0.5.dp, Ink.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { BasicText(initialOf(p.name), style = Type.of(18, FontWeight.Bold).copy(color = Ink.primary)) }
        BasicText(p.name, Modifier.width(72.dp), style = Type.of(12, FontWeight.Bold).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
