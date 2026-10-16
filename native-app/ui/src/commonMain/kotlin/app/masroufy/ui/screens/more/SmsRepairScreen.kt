package app.masroufy.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.nav.Route
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.usecase.SmsRepairLine
import app.masroufy.usecase.SmsRepairUndo
import app.masroufy.usecase.SmsUnrecordedLine
import kotlinx.coroutines.launch

/** «صلّح رسايل البنك القديمة» (r04-sms-fix) — من «تفاصيل المحفظة». */
data class SmsRepairRoute(val walletId: Id) : Route {
    override val name = "SmsRepair"
}

private fun dirText(d: Direction) = t(if (d == Direction.IN) TextKey.SMS_REPAIR_DIR_IN else TextKey.SMS_REPAIR_DIR_OUT)

/** «اليوم · قبل ⇒ بعد» — والمحفظة لو هتتغير (الأسامي من «معك الآن»). */
private fun lineLabel(l: SmsRepairLine, names: Map<Id, String>): String {
    val day = fullDate(l.date) ?: l.date
    val dir = if (l.oldDirection == l.newDirection) dirText(l.newDirection) else "${dirText(l.oldDirection)} ⇒ ${dirText(l.newDirection)}"
    if (l.oldWalletId == l.newWalletId) return "$day · $dir"
    val from = l.oldWalletId?.let { names[it] ?: it } ?: "—"
    return "$day · $dir · $from ⇒ ${names[l.newWalletId] ?: l.newWalletId}"
}

/** r041-B: «اليوم · الاتجاه · التاجر أو الطرف · المحفظة» لسطر «الكشف غطّاها». */
private fun unrecordedLabel(l: SmsUnrecordedLine, names: Map<Id, String>): String {
    val day = fullDate(l.date) ?: l.date
    val title = l.title.ifBlank { null }?.let { " · $it" }.orEmpty()
    return "$day · ${dirText(l.direction)}$title · ${names[l.walletId] ?: l.walletId}"
}

/**
 * كل رسالة قديمة هتتغير على المحفظة دي (قبل ⇒ بعد) — كل اتجاه ومبلغ من `RepairOldSms.previewAll` (مفيش حساب هنا، CLAUDE.md #4). المالك يشيل
 * اللي مش عايزه بلمسة، و«صحّح» بيكتب **بعد نسخة متأكدة** (`RepairOldSms.applyAll`)، و«تراجع» واحد للدفعة. r041-B: قسم تاني للرسايل اللي «الكشف
 * غطّاها» ومبلغها مش فيه — بتتسجل بعلامة «مش في الكشف» في نفس النسخة ووحدة العمل و«تراجع». عدد المكرر بين الرسايل والكشف بيوديه لـ«راجع المكرر»
 * في «افهم رصيدك». مش متوصل ⇒ «غير متاح» (القاعدة 10).
 */
@Composable
fun SmsRepairScreen(walletId: Id) {
    val deps = LocalSpace.current
    val more = deps.more
    val repair = more.smsRepair
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var loaded by remember(deps) { mutableStateOf(false) }
    var lines by remember(deps) { mutableStateOf<List<SmsRepairLine>?>(null) }
    var fresh by remember(deps) { mutableStateOf<List<SmsUnrecordedLine>>(emptyList()) }
    var dupes by remember(deps) { mutableIntStateOf(0) }
    var names by remember(deps) { mutableStateOf<Map<Id, String>>(emptyMap()) }
    var currency by remember(deps) { mutableStateOf<Currency?>(null) }
    var skipped by remember { mutableStateOf<Set<Id>>(emptySet()) }
    var skippedNew by remember { mutableStateOf<Set<Id>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var lastUndo by remember { mutableStateOf<SmsRepairUndo?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(deps, tick) {
        val preview = runCatching { repair?.previewAll() }.getOrNull()
        lines = preview?.moves?.filter { it.oldWalletId == walletId || it.newWalletId == walletId }
        fresh = preview?.unrecorded.orEmpty().filter { it.walletId == walletId }
        dupes = runCatching { repair?.duplicates() }.getOrNull().orEmpty().size
        val rows = runCatching { more.wallets.load(deps.shell.today()) }.getOrNull()?.wallets.orEmpty()
        names = rows.associate { it.wallet.id to it.wallet.name }
        currency = rows.firstOrNull { it.wallet.id == walletId }?.wallet?.currency
        loaded = true
    }
    fun apply(moves: Set<Id>, unrecorded: Set<Id>) {
        val r = repair ?: return
        busy = true
        scope.launch {
            val res = runCatching { r.applyAll(moves, unrecorded) }.getOrNull()
            if (res != null) {
                lastUndo = res.undo
                skipped = emptySet()
                skippedNew = emptySet()
                toaster.show(t(TextKey.SMS_REPAIR_DONE, res.undo.count.toString()), dark = true)
            } else {
                toaster.show(t(UiKey.BWHY_REFUSED))
            }
            busy = false
            tick++
        }
    }
    fun undo(u: SmsRepairUndo) {
        val r = repair ?: return
        busy = true
        scope.launch {
            if (runCatching { r.undo(u) }.isSuccess) {
                lastUndo = null
                toaster.show(t(UiKey.BWHY_UNDONE), dark = true)
            } else {
                toaster.show(t(UiKey.BWHY_REFUSED))
            }
            busy = false
            tick++
        }
    }
    InnerScaffold(t(TextKey.SMS_REPAIR_TITLE)) {
        val list = lines
        val cur = currency
        val news = fresh
        if (!loaded) {
            item(key = "sk") { Skeleton(Modifier.fillMaxWidth().height(160.dp), radius = 26.dp, strong = true) }
            return@InnerScaffold
        }
        if (repair == null || list == null || cur == null) {
            item(key = "na") { EmptyState(t(UiKey.BWHY_UNAVAILABLE)) }
            return@InnerScaffold
        }
        item(key = "intro") { NoteBox(t(TextKey.SMS_REPAIR_INTRO)) }
        if (list.isEmpty() && news.isEmpty()) {
            item(key = "empty") { EmptyState(t(TextKey.SMS_REPAIR_EMPTY)) }
        }
        if (list.isNotEmpty()) {
            item(key = "lines") {
                GroupCard {
                    list.forEachIndexed { i, l ->
                        val off = l.transactionId in skipped
                        Box(Modifier.fillMaxWidth().clickable(enabled = !busy) { skipped = if (off) skipped - l.transactionId else skipped + l.transactionId }) {
                            FactRow(lineLabel(l, names), amountLabel(l.amountMinor, cur), last = i == list.lastIndex, muted = off)
                        }
                    }
                }
            }
        }
        if (news.isNotEmpty()) {
            item(key = "newTitle") { GroupTitle(t(TextKey.SMS_REPAIR_UNRECORDED_TITLE)) }
            item(key = "newNote") { NoteBox(t(TextKey.SMS_REPAIR_UNRECORDED_NOTE), title = t(TextKey.SMS_REPAIR_UNRECORDED_COUNT, news.size.toString())) }
            item(key = "newLines") {
                GroupCard {
                    news.forEachIndexed { i, l ->
                        val off = l.recordId in skippedNew
                        Box(Modifier.fillMaxWidth().clickable(enabled = !busy) { skippedNew = if (off) skippedNew - l.recordId else skippedNew + l.recordId }) {
                            FactRow(unrecordedLabel(l, names), amountLabel(l.amountMinor, l.currency), last = i == news.lastIndex, muted = off)
                        }
                    }
                }
            }
        }
        if (list.isNotEmpty() || news.isNotEmpty()) {
            // «طبّق» واحد للنوعين: نسخة واحدة · وحدة عمل واحدة · «تراجع» واحد
            val chosen = list.map { it.transactionId }.filter { it !in skipped }.toSet()
            val chosenNew = news.map { it.recordId }.filter { it !in skippedNew }.toSet()
            item(key = "apply") {
                TonalButton(
                    t(TextKey.SMS_REPAIR_APPLY, (chosen.size + chosenNew.size).toString()), onClick = { apply(chosen, chosenNew) },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy && (chosen.isNotEmpty() || chosenNew.isNotEmpty()), height = 48.dp,
                )
            }
        }
        val u = lastUndo
        if (u != null) {
            item(key = "undo") {
                TonalButton(t(TextKey.SMS_REPAIR_UNDO), onClick = { undo(u) }, modifier = Modifier.fillMaxWidth(), enabled = !busy, height = 48.dp, muted = true)
            }
        }
        if (dupes > 0) item(key = "dupes") { NoteBox(t(TextKey.SMS_REPAIR_DUPES, dupes.toString())) }
    }
}
