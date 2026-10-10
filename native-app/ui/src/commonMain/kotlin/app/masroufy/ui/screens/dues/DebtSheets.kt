package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.TextKey
import app.masroufy.core.formatAmount
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** اللي لوحة التسوية محتاجاه عن الالتزام (من «تفاصيل الدين» أو من القايمة لو اتفتحت من ملف الشخص). */
data class SettleTarget(val obligationId: String, val personId: String, val personName: String, val forYou: Boolean, val remainingMinor: Halalas, val currency: Currency)

internal fun DebtDetailUi.settleTarget() = SettleTarget(obligationId, personId, personName, forYou, remainingMinor, currency)

/**
 * جسم لوحة «سداد أو تحصيل» (`SettleSheet`): المتبقي · المبلغ (ما يزيدش عن المتبقي — الخطأ جنب الخانة) · «المتبقي كاملًا» · حفظ ⇒
 * `ManagePeople.settle` (من غير عملية). [onSaved] بياخد «اتسدد بالكامل؟» (المبلغ = المتبقي — مقارنة مش حساب).
 * ⚠️ مش متاح بعد: ربطها بعملية موجودة (الحوالة المستنية) · تسجيل الزيادة أمانة في نفس الخطوة · حالة «يحتاج اتصالًا» (مفيش إشارة نت للشاشات).
 */
@Composable
internal fun SettleForm(target: SettleTarget, onSaved: (full: Boolean) -> Unit) {
    val deps = LocalSpace.current.dues
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var text by remember(target.obligationId) { mutableStateOf("") }
    var linkMode by remember(target.obligationId) { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val check = if (linkMode) SettleInput.Empty else settleInput(text, target.remainingMinor, target.currency)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        BasicText(t(if (target.forYou) TextKey.SETTLE_TITLE_FOR else TextKey.SETTLE_TITLE_ON, target.personName), Modifier.weight(1f), style = Type.section())
        BasicText(t(TextKey.SETTLE_REMAINING, amountLabel(target.remainingMinor, target.currency)), style = Type.caption().copy(color = Ink.muted))
    }
    // «كيف تمّت؟»: بلا عملية · عملية موجودة (الحوالة اللي مستنية سؤال «هل هي سداد؟» — §75، مش متبني لسه ⇒ مفيش مرشح)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectChip(t(TextKey.SETTLE_HOW_FREE), !linkMode, { linkMode = false; failure = null }, Modifier.weight(1f), height = 44.dp)
        SelectChip(t(TextKey.SETTLE_HOW_LINK), linkMode, { linkMode = true; failure = null }, Modifier.weight(1f), height = 44.dp)
    }
    if (linkMode) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(t(TextKey.SETTLE_NO_CAND), style = Type.bodyBold())
            BasicText(t(TextKey.SETTLE_NO_CAND_SUB, target.personName), style = Type.caption().copy(color = Ink.muted))
        }
    } else {
        MoneyField(text, { text = it; failure = null }, t(TextKey.SETTLE_AMOUNT), target.currency, check.errorText(target.remainingMinor, target.currency), big = true)
        TonalButton(
            t(TextKey.SETTLE_FILL, amountLabel(target.remainingMinor, target.currency, showCurrency = false)),
            { text = formatAmount(target.remainingMinor, target.currency, grouping = false); failure = null }, height = 44.dp,
        )
    }
    failure?.let { FieldError(it) }
    val ok = check as? SettleInput.Ok
    PrimaryButton(
        if (ok == null) t(if (target.forYou) TextKey.SETTLE_SAVE_FOR else TextKey.SETTLE_SAVE_ON)
        else t(if (target.forYou) TextKey.SETTLE_SAVE_FOR_AMOUNT else TextKey.SETTLE_SAVE_ON_AMOUNT, amountLabel(ok.minor, target.currency, showCurrency = false)),
        onClick = {
            val go = ok ?: return@PrimaryButton
            saving = true
            scope.launch {
                try {
                    deps.people.settle(target.obligationId, target.personId, go.minor)
                    toaster.show(settledToast(go, target))
                    DuesChanges.bump()
                    onSaved(go.full)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    failure = failText(e)
                } finally {
                    saving = false
                }
            }
        },
        enabled = check is SettleInput.Ok, loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(),
    )
}

/** اللوحة المتسجلة (من ملف الشخص): بتحمّل الالتزام الأول، وبعد الحفظ بتقفل. */
@Composable
internal fun SettleSheetBody(obligationId: String, dismiss: () -> Unit) {
    val space = LocalSpace.current
    val load = rememberLoad(space.dues, obligationId) {
        val today = space.shell.today()
        debtDetailUi(space.dues.people.listWithBalances(), emptyList(), obligationId, today)?.settleTarget()
    }
    when (val s = load.value) {
        Load.Loading -> Skeleton(Modifier.fillMaxWidth().height(200.dp))
        Load.Failed -> LoadFailed(load::reload)
        is Load.Ready -> s.value?.let { SettleForm(it) { dismiss() } } ?: EmptyState(t(TextKey.DEBT_NOT_OPEN))
    }
}

/**
 * جسم لوحة «دين قديم مع X» (`OpeningDebtSheet` — §27): «لي عنده» أو «عليّ له» + المبلغ ⇒ `ManagePeople.addOpeningDebt` (من غير عملية ولا محفظة).
 * بعد الحفظ بتفتح صفحة الدين الجديد ([onSaved] بياخد الالتزام).
 */
@Composable
internal fun OpeningDebtBody(personId: String, personName: String, onSaved: (Obligation) -> Unit) {
    val space = LocalSpace.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf<ObligationKind?>(null) }
    var text by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val currency = space.space.currency
    BasicText(t(TextKey.OPENING_DEBT_TITLE, personName), style = Type.section())
    BasicText(t(TextKey.OPENING_DEBT_BODY), style = Type.of(13).copy(color = Ink.soft))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectChip(t(TextKey.OPENING_DEBT_FOR), kind == ObligationKind.RECEIVABLE, { kind = ObligationKind.RECEIVABLE; shown = null }, Modifier.weight(1f), height = 48.dp)
        SelectChip(t(TextKey.OPENING_DEBT_ON), kind == ObligationKind.LOAN_PAYABLE, { kind = ObligationKind.LOAN_PAYABLE; shown = null }, Modifier.weight(1f), height = 48.dp)
    }
    FieldLabel(t(TextKey.SETTLE_AMOUNT))
    MoneyField(text, { text = it; shown = null }, null, currency)
    shown?.let { FieldError(it) }
    PrimaryButton(
        t(TextKey.OPENING_DEBT_SAVE),
        onClick = {
            val check = openingInput(kind, text, currency)
            val ok = check as? OpeningInput.Ok
            if (ok == null) {
                shown = check.errorText()
                return@PrimaryButton
            }
            saving = true
            scope.launch {
                try {
                    val o = space.dues.people.addOpeningDebt(personId, ok.kind, ok.minor)
                    toaster.show(t(TextKey.OPENING_DEBT_SAVED))
                    DuesChanges.bump()
                    onSaved(o)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    shown = failText(e)
                } finally {
                    saving = false
                }
            }
        },
        loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(),
    )
}
