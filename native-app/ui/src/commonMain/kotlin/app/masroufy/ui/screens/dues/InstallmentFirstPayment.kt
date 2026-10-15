package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** «12 أكتوبر، 250.00 ر.س» — اليوم والمبلغ زي ما هم من العملية (عرض بس). */
fun firstPaymentLabel(txn: Transaction): String = t(UiKey.INST_FIRST_TXN_ROW, dayMonth(txn.occurredAt.take(10)), amountLabel(txn.amountMinor, txn.currency))

/**
 * §82-20 «اشتري دلوقتي وادفع بعدين»: «أول قسط اتدفع بالعملية دي» — العمليات اللي ممكن تكونه من حالة الاستخدام
 * (`ManageInstallments.firstPaymentCandidates`: صادرة بالقسط بالظبط ±7 أيام من يوم الشراء ومش مربوطة) + «لسه ما ظهرتش». من غير عملية أول قسط
 * بيفضل مستحق يوم الشراء (مدفوع من غير مصدر = رقم من غير مصدر) واقتراح الربط بيلاقيها لما توصل.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FirstPaymentPick(form: PlanForm, currency: Currency, onPick: (String?) -> Unit) {
    val deps = LocalSpace.current.dues
    val minor = tryParseMoney(form.installment, currency)?.takeIf { it > 0 }
    val day = form.firstDueAt
    var found by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    LaunchedEffect(minor, day, currency) {
        found = if (minor == null || day == null) emptyList()
        else runCatching { deps.installments.firstPaymentCandidates(currency, minor, day) }.getOrDefault(emptyList())
    }
    FieldLabel(t(UiKey.INST_FIRST_TXN))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (txn in found) SelectChip(firstPaymentLabel(txn), form.firstTxnId == txn.id, { onPick(txn.id) }, height = 44.dp)
        SelectChip(t(UiKey.INST_FIRST_NONE), form.firstTxnId == null, { onPick(null) }, height = 44.dp)
    }
    if (form.firstTxnId == null) BasicText(t(UiKey.INST_FIRST_NONE_HINT), style = Type.caption().copy(color = Ink.muted))
}
