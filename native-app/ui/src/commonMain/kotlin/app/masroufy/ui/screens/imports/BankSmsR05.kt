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
import app.masroufy.core.Category
import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.transferPartyOf
import app.masroufy.ui.shell.Toaster
import app.masroufy.core.UiKey
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
/** [employerPayer] 0.5.2: العملية من حساب مربوط بجهة شغل ⇒ مفيش «من هذا؟» (كارت «الفلوس دي إيه؟» تحت المرتب — `MoneyWhatPending`). */
class SmsR05(
    private val deps: ImportsDeps,
    private val toaster: Toaster,
    private val onChanged: () -> Unit = {},
    private val employerPayer: suspend (String) -> Boolean = { false },
) {
    val picks = PendingPicks()
    var skip by mutableStateOf(0)
    var parties by mutableStateOf(listOf<Triple<TransferPartyRef, String, Boolean>>())
    var employerTxn by mutableStateOf<String?>(null)

    private fun confirmed(r: SmsConfirmed?) {
        val n = r?.recorded ?: 0
        // r051: اتسجل حاجة ⇒ الهيكل يقرا تاني (لوحة «ادفع بعدين» بتطلع لوحدها — `BnplHost`)
        if (n > 0) onChanged()
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
        // smoke 0.5.4: الدفعة فيها الرسوم كعملية لوحدها (`SmsFeeEffect`) — أول معرّف ممكن يبقى الرسوم ⇒ الكارت ما كانش بيظهر.
        // العملية اللي ليها طرف هي الحوالة نفسها.
        for (id in r?.transactionIds.orEmpty()) {
            val txn = runCatching { deps.transactions.load(id).transaction }.getOrNull() ?: continue
            val party = transferPartyOf(txn) ?: continue
            if (runCatching { employerPayer(id) }.getOrDefault(false)) return
            parties = parties + Triple(party, id, txn.observedDirection == Direction.OUT)
            return
        }
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
