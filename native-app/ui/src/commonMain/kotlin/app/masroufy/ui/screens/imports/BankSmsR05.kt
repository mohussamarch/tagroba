package app.masroufy.ui.screens.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.BnplCycle
import app.masroufy.core.BnplMatch
import app.masroufy.core.Category
import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.transferPartyOf
import app.masroufy.ui.shell.Toaster
import app.masroufy.core.UiKey
import app.masroufy.core.formatMoney
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.BnplAnswer
import app.masroufy.usecase.BnplAsk
import app.masroufy.usecase.BnplPurchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** r05 (4a): مدة «تراجع» بعد اختيار تصنيف لرسالة مستنية. */
const val SMS_PICK_UNDO_MS = 8_000L

/**
 * r05 (4a — شكوى المالك: «اللي بدوس عليهم اختار حاجة مش بيحصل حالة ولا بيتشالو»): السبب كان إن اختيار التصنيف على رسالة مستنية كان
 * **تلميح بس** (`chosen`) بيستنى «سجّل الكل». دلوقتي الاختيار بيشيل الرسالة من القايمة على طول، و«تراجع» 8 ثواني، وبعدها بتتسجل فعلًا
 * (`SmsDeps.confirmMessages` — وشكلها بيتعلّم وباقي نفس الشكل بيتسجل). الشاشة لو اتقفلت قبل الـ8 ثواني ⇒ بتتسجل على طول ([flushDetached]).
 */
@Stable
class PendingPicks {
    class Pick(val line: WaitLineUi, val categoryId: Id, val categoryName: String, val job: Job)

    val items = mutableStateMapOf<String, Pick>()

    val ids: Set<String> get() = items.keys.toSet()

    fun add(scope: CoroutineScope, line: WaitLineUi, category: Category, commit: suspend (String, Id) -> Unit) {
        items.remove(line.messageId)?.job?.cancel()
        val job = scope.launch {
            delay(SMS_PICK_UNDO_MS)
            val pick = items[line.messageId] ?: return@launch
            try {
                commit(line.messageId, pick.categoryId)
            } finally {
                items.remove(line.messageId)
            }
        }
        items[line.messageId] = Pick(line, category.id, category.name, job)
    }

    fun undo(messageId: String) {
        items.remove(messageId)?.job?.cancel()
    }

    /** الشاشة اتقفلت قبل «تراجع» ما يخلص ⇒ اللي اتختار بيتسجل بره الشاشة (من غير توست ولا تحديث — الشاشة مش موجودة). */
    fun flushDetached(commit: suspend (List<String>, Map<String, Id>) -> Unit) {
        if (items.isEmpty()) return
        val all = items.values.toList()
        items.clear()
        all.forEach { it.job.cancel() }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching { commit(all.map { it.line.messageId }, all.associate { it.line.messageId to it.categoryId }) }
        }
    }
}

/**
 * حالة زيادات r05 على «رسائل البنك» (اتفصلت عن `BankSmsScreen` عشان حد الـ300 سطر): الاختيارات في مهلتها ([picks]) · صفحة الصندوق ([skip])
 * · كروت الطرف بعد التسجيل ([parties] — 4c) · جهة الشغل بعد «أيوه ده راتبي» ([employerTxn] — 3b) · «ادفع بعدين». كله بينادي `SmsDeps` بس.
 */
@Stable
class SmsR05(private val deps: ImportsDeps, private val toaster: Toaster) {
    val picks = PendingPicks()
    var skip by mutableStateOf(0)
    var parties by mutableStateOf(listOf<Triple<TransferPartyRef, String, Boolean>>())
    var employerTxn by mutableStateOf<String?>(null)
    var bnplAsks by mutableStateOf<List<BnplAsk>>(emptyList())
    var bnplOpen by mutableStateOf<BnplAsk?>(null)

    private fun confirmed(r: SmsConfirmed?) {
        val n = r?.recorded ?: 0
        val same = r?.sameShape ?: 0
        toaster.show(if (same > 0) t(UiKey.SMS_R05_CONFIRMED_SHAPE, opsCount(n), opsCount(same)) else t(UiKey.SMS_R05_CONFIRMED, opsCount(n)), dark = true)
    }

    suspend fun commitPick(id: String, categoryId: Id) = confirmed(deps.sms?.confirmMessages(listOf(id), mapOf(id to categoryId)))

    suspend fun confirmLine(line: WaitLineUi) = confirmed(deps.sms?.confirmMessages(listOf(line.messageId)))

    suspend fun salaryYes(line: WaitLineUi) {
        val r = deps.sms?.confirmMessages(listOf(line.messageId), isSalary = true)
        confirmed(r)
        employerTxn = r?.transactionIds?.firstOrNull()
    }

    suspend fun recordParty(line: WaitLineUi) {
        val r = deps.sms?.confirmMessages(listOf(line.messageId))
        confirmed(r)
        val id = r?.transactionIds?.firstOrNull() ?: return
        val txn = deps.transactions.load(id).transaction
        transferPartyOf(txn)?.let { p -> parties = parties + Triple(p, id, txn.observedDirection == Direction.OUT) }
    }

    suspend fun answerBnpl(a: BnplAnswer) {
        bnplOpen = null
        val done = deps.sms?.bnplAnswer(a)
        val name = done?.planName
        val left = done?.remainingMinor
        val currency = done?.currency
        toaster.show(if (name != null && left != null && currency != null) t(UiKey.BNPL_DONE, name, formatMoney(left, currency)) else t(UiKey.BNPL_SKIPPED), dark = true)
    }

    fun flush() = picks.flushDetached { ids, cats -> deps.sms?.confirmMessages(ids, cats) }
}

/** «ستُسجَّل «المحل» في «التصنيف»» + «تراجع» لكل اختيار لسه في مهلته. */
@Composable
fun PendingPicksPanel(picks: PendingPicks) {
    if (picks.items.isEmpty()) return
    TintedPanel(PanelTone.MINT, radius = 18.dp) {
        for (p in picks.items.values.toList()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val name = p.line.merchant.ifBlank { t(UiKey.SMS_R05_LINE_NO_NAME) }
                BasicText(t(UiKey.SMS_R05_PICK_PENDING, name, p.categoryName), Modifier.weight(1f), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
                TonalButton(t(UiKey.SMS_R05_UNDO), { picks.undo(p.line.messageId) }, height = 36.dp)
            }
        }
    }
}

/** كروت «اشتري دلوقتي وادفع بعدين» (الأقدم الأول — بعد الرد التحديث بيربط اللي بعده لوحده لو نفس القسط). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BnplAskCards(asks: List<BnplAsk>, onOpen: (BnplAsk) -> Unit, onAnswer: (BnplAnswer) -> Unit) {
    for (ask in asks.take(BNPL_CARDS_SHOWN)) {
        val t0 = ask.transaction
        TintedPanel(PanelTone.AMBER) {
            val which = ask.match is BnplMatch.AskWhich
            PanelTitle(if (which) t(UiKey.BNPL_WHICH_TITLE) else t(UiKey.BNPL_NEW_TITLE, ask.provider.brand), PanelTone.AMBER)
            BasicText(t(UiKey.BNPL_NEW_BODY, formatMoney(t0.amountMinor, t0.currency)), style = Type.body())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (plan in ask.plans) SelectChip(plan.name, false, { onAnswer(BnplAnswer.Of(t0.id, plan.id)) }, height = 36.dp)
                SelectChip(if (which) t(UiKey.BNPL_NEW_PURCHASE) else t(UiKey.BNPL_SAVE), true, { onOpen(ask) }, height = 36.dp)
                SelectChip(t(UiKey.BNPL_SKIP), false, { onAnswer(BnplAnswer.Skip(t0.id)) }, height = 36.dp)
            }
        }
    }
}

private const val BNPL_CARDS_SHOWN = 3

/** «اشتريت إيه من تابي؟» · التصنيف · «قسمتها على كام؟» (اختيارات المزوّد) · «كل قد إيه؟» ⇒ الخطة (أول قسط = الخصم ده). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BnplSheet(ask: BnplAsk?, categories: List<Category>, onDismiss: () -> Unit, onSave: (BnplAnswer) -> Unit) {
    var what by remember(ask) { mutableStateOf("") }
    var count by remember(ask) { mutableStateOf(ask?.provider?.defaultCount ?: 4) }
    var cycle by remember(ask) { mutableStateOf(BnplCycle.MONTHLY) }
    var category by remember(ask) { mutableStateOf<Category?>(null) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(ask) { picking = false }
    val title = ask?.let { t(UiKey.BNPL_NEW_TITLE, it.provider.brand) } ?: ""
    Sheet(ask != null && !picking, onDismiss, title = title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        val a = ask ?: return@Sheet
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        TextInput(what, { what = it }, Modifier.fillMaxWidth(), placeholder = t(UiKey.BNPL_WHAT_HINT))
        SelectChip(category?.name ?: t(UiKey.BNPL_PICK_CATEGORY), category != null, { picking = true }, height = 36.dp)
        BasicText(t(UiKey.BNPL_COUNT_Q), style = Type.of(14, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in a.provider.counts) SelectChip(sentenceNumber(n), count == n, { count = n }, height = 36.dp)
        }
        BasicText(t(UiKey.BNPL_CYCLE_Q), style = Type.of(14, FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip(t(UiKey.BNPL_MONTHLY), cycle == BnplCycle.MONTHLY, { cycle = BnplCycle.MONTHLY }, height = 36.dp)
            SelectChip(t(UiKey.BNPL_TWO_WEEKS), cycle == BnplCycle.TWO_WEEKS, { cycle = BnplCycle.TWO_WEEKS }, height = 36.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(t(UiKey.BNPL_SAVE), { onSave(BnplAnswer.New(a.transaction.id, BnplPurchase(what, category?.id, count, cycle))) }, Modifier.fillMaxWidth())
            QuietButton(t(UiKey.BNPL_SKIP), { onSave(BnplAnswer.Skip(a.transaction.id)) }, Modifier.fillMaxWidth())
        }
    }
    CategoryPickerSheet(
        visible = ask != null && picking,
        title = t(UiKey.BNPL_CATEGORY),
        categories = categories,
        selectedId = category?.id,
        onPick = { category = it; picking = false },
        onDismiss = { picking = false },
    )
}
