package app.masroufy.ui.screens.dues

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.R06Key
import app.masroufy.core.UiKey
import app.masroufy.core.currencyName
import app.masroufy.core.fxRateLabel
import app.masroufy.core.parseFxRate4
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.screens.investment.calc.Parsed
import app.masroufy.ui.screens.investment.calc.parseAmountField
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * §111-4 «سداد بعملة البلد التانية»: المبلغ بالعملة اللي اتدفع بيها ([paid]) + سعر الصرف (إجباري — مفيش سعر في ملف الأسعار للجنيه ⇒ الخانة
 * فاضية، القاعدة 10). اللي بينزل من الدين بييجي من حالة الاستخدام (`SettleOtherCurrency.quote`) — الشاشة ما بتحسبش.
 */
@Composable
internal fun SettleFxPanel(target: SettleTarget, paid: Currency, onSaved: (full: Boolean) -> Unit) {
    val fx = LocalSpace.current.dues.people.otherCurrency
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var amount by remember(target.obligationId) { mutableStateOf("") }
    var rate by remember(target.obligationId) { mutableStateOf(fx.prefillRate4(paid, target.currency)?.let(::fxRateLabel).orEmpty()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val paidMinor = (parseAmountField(amount, paid) as? Parsed.Ok)?.value
    val rate4 = parseFxRate4(rate)
    val debtMinor = fx.quote(paidMinor, rate4)
    MoneyField(amount, { amount = it; failure = null }, t(R06Key.CTY_FX_PAID), paid, big = true)
    TextInput(
        rate, { rate = it; failure = null }, Modifier.fillMaxWidth(), label = t(R06Key.CTY_FX_RATE, currencyName(paid), currencyName(target.currency)),
        placeholder = "0.0000", error = if (rate.isNotBlank() && rate4 == null) t(R06Key.CTY_FX_RATE_NEEDED) else null,
    )
    BasicText(
        if (debtMinor != null) t(R06Key.CTY_FX_RESULT, amountLabel(debtMinor, target.currency)) else t(R06Key.CTY_FX_RATE_NEEDED),
        style = Type.caption().copy(color = Ink.muted),
    )
    failure?.let { FieldError(it) }
    PrimaryButton(
        t(if (target.forYou) UiKey.SETTLE_SAVE_FOR else UiKey.SETTLE_SAVE_ON),
        onClick = {
            val p = paidMinor ?: return@PrimaryButton
            saving = true
            scope.launch {
                try {
                    val s = fx.settle(target.obligationId, target.personId, p, paid, rate4)
                    toaster.show(t(R06Key.CTY_FX_DONE, amountLabel(p, paid), fxRateLabel(s.fx!!.rate4)))
                    DuesChanges.bump()
                    onSaved(s.amountMinor >= target.remainingMinor)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    failure = failText(e)
                } finally {
                    saving = false
                }
            }
        },
        enabled = debtMinor != null, loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(),
    )
}
