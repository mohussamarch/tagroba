package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.ruleFor
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.PayerAskView
import kotlinx.coroutines.launch

/**
 * PLAN-0.6 §7 — جوه [IncomeKindCard]: تحويل من حساب جهة الشغل **مش راتب** ⇒ كارت «الفلوس دي إيه؟» ([MoneyWhatCard] — r06-1b3 بالتصميم).
 * الحساب ليه قاعدة ⇒ سطر «بتتسجل … لوحدها» و«الغي الاختيار ده». بيرجّع true لو عرض الكارت (الكارت العام ما يتعرضش).
 * [onDone] ما بقاش بيتنادى (الكارت بيعرض «اتسجّل» و«تراجع» جواه) — فاضل عشان [IncomeKindCard] ما يتغيرش.
 */
@Composable
fun payerAskShown(transactionId: Id, @Suppress("UNUSED_PARAMETER") onDone: (IncomeDone) -> Unit, onChanged: () -> Unit): Boolean {
    val payer = LocalSpace.current.operations.payerKinds ?: return false
    var view by remember(transactionId) { mutableStateOf<PayerAskView?>(null) }
    var reload by remember(transactionId) { mutableIntStateOf(0) }
    LaunchedEffect(transactionId, reload) { view = attempt { payer.view(transactionId) } }
    val v = view ?: return false
    val scope = rememberCoroutineScope()
    if (!v.asks) {
        v.ruleKind?.let { kind ->
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(t(R06Key.PAYER_RULE_LINE, v.ruleLabel ?: ruleFor(kind).label), style = Type.of(12).copy(color = Ink.muted))
                SelectChip(t(R06Key.PAYER_RULE_FORGET), false, { scope.launch { attempt { payer.forgetRule(transactionId) }; reload++; onChanged() } })
            }
        }
        return false
    }
    MoneyWhatCard(transactionId, v, onChanged)
    return true
}

/**
 * r06-1b3 — صفحة رسايل البنك تحت كارت المرتب (التصميم `SmsPending` + `MoneyWhat`): التحويلات اللي اتسجلت من حساب جهة الشغل ومش شبه المرتب
 * ومستنية «الفلوس دي إيه؟» (آخر 60 يوم، أحدث 3). الكارت نفسه بتاع تفاصيل العملية.
 */
@Composable
fun MoneyWhatPending(refreshKey: Any? = null) {
    val payer = LocalSpace.current.operations.payerKinds ?: return
    var ids by remember { mutableStateOf<List<Id>>(emptyList()) }
    LaunchedEffect(refreshKey) { ids = attempt { payer.pendingAsks() }.orEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (id in ids) {
            var view by remember(id) { mutableStateOf<PayerAskView?>(null) }
            LaunchedEffect(id) { view = attempt { payer.view(id) } }
            view?.let { MoneyWhatCard(id, it, onChanged = {}) }
        }
    }
}
