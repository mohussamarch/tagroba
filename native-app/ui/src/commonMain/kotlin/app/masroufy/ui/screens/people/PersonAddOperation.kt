package app.masroufy.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.PersonMeaning
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.currencySymbol
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.operations.PersonLinked
import app.masroufy.ui.screens.operations.linkedText
import app.masroufy.ui.screens.operations.meaningLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.ManagePersonLinks
import kotlinx.coroutines.launch

/** المعاني اللي تنفع لعملية جديدة (لازم اتجاه — «مع الشخص ده بس» مش هنا). */
internal val ADD_MEANINGS: List<PersonMeaning> = PersonMeaning.entries.filter { it != PersonMeaning.WITH }

/** خطأ «أضف عملية» قبل حالة الاستخدام — null = جاهز. */
internal fun addOperationError(meaning: PersonMeaning?, amount: Long?, date: String, wallet: Id?): String? = when {
    meaning == null -> t(UiKey.PLINK_ERR_MEANING)
    amount == null || amount <= 0L -> t(UiKey.PLINK_ERR_AMOUNT)
    !isValidIsoDate(date) -> t(UiKey.PLINK_ERR_DATE)
    wallet == null -> t(UiKey.PLINK_ERR_WALLET)
    else -> null
}

/**
 * «أضف عملية مع الشخص ده» (المالك 2026-10-16: «أو أضيف أنا عملية جديدة ممكن كانت كاش»): المعنى (سلفة · سداد · هدية · دعم…) بيحدد النوع والاتجاه ·
 * المبلغ · اليوم (النهارده جاهز) · المحفظة (الكاش أول واحدة) · ملاحظة. الحفظ = `PersonAccounts.addOperation` (العملية + الربط مع بعض)، و«تراجع» بيشيلهم.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonAddOperationSheet(
    open: Boolean,
    personId: Id,
    personName: String,
    links: ManagePersonLinks,
    today: IsoDate,
    onDismiss: () -> Unit,
    onDone: (PersonLinked) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var meaning by remember(open) { mutableStateOf<PersonMeaning?>(null) }
    var amountText by remember(open) { mutableStateOf("") }
    var date by remember(open) { mutableStateOf(today) }
    var wallet by remember(open) { mutableStateOf<Wallet?>(null) }
    var note by remember(open) { mutableStateOf("") }
    var error by remember(open) { mutableStateOf<String?>(null) }
    var saving by remember(open) { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        wallets = runCatching { links.accounts.wallets() }.getOrDefault(emptyList()).sortedBy { if (it.kind == "cash") 0 else 1 }
        wallet = wallets.firstOrNull()
    }
    val currency = wallet?.currency ?: wallets.firstOrNull()?.currency ?: app.masroufy.core.Currency.SAR
    Sheet(open, onDismiss, title = t(UiKey.PLINK_ADD_TITLE, personName), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        SheetHeading(t(UiKey.PLINK_ADD_TITLE, personName))
        FieldLabel(t(UiKey.PLINK_SHEET_MEANING))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in ADD_MEANINGS) SelectChip(meaningLabel(m), meaning == m, { meaning = m; error = null }, height = 44.dp)
        }
        TextInput(
            amountText, { amountText = it; error = null }, label = t(UiKey.PLINK_ADD_AMOUNT), placeholder = "0.00", ltr = true,
            keyboard = KeyboardType.Decimal, unit = currencySymbol(currency),
        )
        TextInput(date, { date = it.trim(); error = null }, label = t(UiKey.PLINK_ADD_DATE), placeholder = today, ltr = true)
        FieldLabel(t(UiKey.PLINK_ADD_WALLET))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in wallets) SelectChip(w.name, wallet?.id == w.id, { wallet = w; error = null }, height = 44.dp)
        }
        TextInput(note, { note = it }, label = t(UiKey.PLINK_ADD_NOTE))
        ErrorLine(error)
        PrimaryButton(t(UiKey.PLINK_ADD_SAVE), loading = saving, modifier = Modifier.fillMaxWidth(), onClick = {
            val amount = tryParseMoney(amountText, currency)
            val m = meaning
            val w = wallet
            error = addOperationError(m, amount, date, w?.id)
            if (error != null || m == null || amount == null || w == null || saving) return@PrimaryButton
            saving = true
            scope.launch {
                val out = runCatching { links.accounts.addOperation(personId, m, amount, date, w.id, note) }
                saving = false
                out.onSuccess { onDone(PersonLinked(it.link.transactionId, t(UiKey.PLINK_ADD_DONE, personName) + " — " + linkedText(it, currency))) }
                    .onFailure { error = it.message }
            }
        })
    }
}
