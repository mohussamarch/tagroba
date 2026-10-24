package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.core.UiKey
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.overlay.SheetScroll
import app.masroufy.ui.screens.people.DateField
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import kotlinx.coroutines.launch

/** الدورات اللي النموذج بيفهمها (`validateRecurring`): شهري · كل 3 شهور · سنوي. */
internal val SUB_ADD_CYCLES = listOf(1 to UiKey.SUBS_CYCLE_MONTHLY, 3 to UiKey.SUBS_CYCLE_QUARTERLY, 12 to UiKey.SUBS_CYCLE_YEARLY)

/**
 * r06-links2 (فجوة ٢): «ضيف اشتراك» — من «الاشتراكات والفواتير» ومن «+». الاسم · اشتراك/فاتورة · المبلغ · الدورة · الموعد الجاي ⇒
 * `ManageRecurring.addManual` (الشاشة ما بتحسبش — المبلغ بيتقرا بس). الحساب والتصنيف مش في نموذج الاشتراك لسه (HANDOVER §7).
 */
@Composable
internal fun SubscriptionAddSheet(visible: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit = {}) {
    Sheet(visible = visible, onDismiss = onDismiss, title = t(R06Key.L2_SUB_ADD_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        if (visible) SheetScroll { SubscriptionAddForm { onSaved(); onDismiss() } }
    }
}

@Composable
private fun SubscriptionAddForm(onSaved: () -> Unit) {
    val space = LocalSpace.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val today = space.shell.today()
    val currency = space.space.currency
    var name by remember { mutableStateOf("") }
    var bill by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var cycle by remember { mutableStateOf(1) }
    var next by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { next = today }
    TextInput(name, { name = it; error = null }, label = t(R06Key.L2_SUB_NAME), placeholder = t(R06Key.L2_SUB_NAME_HINT))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectChip(t(UiKey.SUBS_KIND_SUB), !bill, { bill = false }, Modifier.weight(1f), height = 44.dp)
        SelectChip(t(UiKey.SUBS_KIND_BILL), bill, { bill = true }, Modifier.weight(1f), height = 44.dp)
    }
    MoneyField(amount, { amount = it; error = null }, t(UiKey.SUBS_F_AMOUNT), currency)
    FieldLabel(t(UiKey.SUBS_F_CYCLE))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((months, key) in SUB_ADD_CYCLES) SelectChip(t(key), cycle == months, { cycle = months }, Modifier.weight(1f), height = 44.dp)
    }
    FieldLabel(t(UiKey.SUBS_F_NEXT))
    DateField(next, today, { next = it })
    error?.let { FieldError(it) }
    PrimaryButton(
        t(UiKey.SUBS_SAVE),
        onClick = {
            val minor = tryParseMoney(amount, currency)
            if (minor == null || minor <= 0) {
                error = t(UiKey.ADD_AMOUNT_INVALID)
                return@PrimaryButton
            }
            saving = true
            scope.launch {
                try {
                    val item = space.dues.recurring.addManual(name, if (bill) "bill" else "subscription", cycle, minor, currency, next ?: today)
                    toaster.show(t(R06Key.L2_SUB_ADDED, item.name))
                    DuesChanges.bump()
                    onSaved()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    error = failText(e)
                } finally {
                    saving = false
                }
            }
        },
        loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(),
    )
}
