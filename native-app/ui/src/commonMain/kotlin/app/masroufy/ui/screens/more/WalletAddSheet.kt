package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.currencySymbol
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** «أضف محفظة» (المحافظ) أو «تعديل رصيد البداية» (تفاصيل المحفظة) — نفس اللوحة بوضعين (§76). */
sealed interface WalletSheetMode {
    data object Add : WalletSheetMode

    data class Edit(val wallet: Wallet) : WalletSheetMode
}

/** اقتراحات القايمة القصيرة لكل نوع وبلد (الاسم بيتكتب في الخانة ويتعدّل). «آخر» ⇒ الخانة فاضية. */
private fun suggestions(kind: String, currency: Currency): List<TextKey> = when (kind) {
    "cash" -> listOf(TextKey.WADD_CASH_HOME, TextKey.WADD_CASH_WORK, TextKey.WADD_CASH_TRAVEL, TextKey.WADD_CASH_CAR)
    "digital_wallet" -> if (currency == Currency.EGP) listOf(TextKey.WADD_EG_VODAFONE, TextKey.WADD_EG_ORANGE, TextKey.WADD_OTHER_WALLET)
    else listOf(TextKey.WADD_SA_STCPAY, TextKey.WADD_SA_URPAY, TextKey.WADD_OTHER_WALLET)
    else -> if (currency == Currency.EGP) listOf(TextKey.WADD_EG_NBE, TextKey.WADD_EG_QNB, TextKey.WADD_EG_MISR, TextKey.WADD_EG_CIB, TextKey.WADD_EG_ALEX, TextKey.WADD_OTHER_BANK)
    else listOf(TextKey.WADD_SA_RAJHI, TextKey.WADD_SA_SNB, TextKey.WADD_SA_INMA, TextKey.WADD_SA_RIYAD, TextKey.WADD_SA_ANB, TextKey.WADD_OTHER_BANK)
}

private val OTHER = setOf(TextKey.WADD_OTHER_BANK, TextKey.WADD_OTHER_WALLET)

/**
 * لوحة المحفظة: النوع (حساب بنكي · محفظة إلكترونية · كاش — رد المالك «ضيف كاش تاني») · البنك من قايمة قصيرة · الاسم (من غير تكرار) ·
 * **آخر ٤ أرقام بس** (اللي اتلصق أطول بيتقص ويتقال ليه — CLAUDE.md #11) · رصيد البداية وتاريخه (فاضي = «غير متاح» مش صفر).
 * الحفظ نقطة ربط ([WalletEditor]) — مفيش حالة استخدام لإدارة المحافظ ⇒ «غير متاح بعد». سطر الأثر **من غير حساب** (بيكرر اللي اتكتب بس).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletAddSheet(visible: Boolean, mode: WalletSheetMode, existing: List<Wallet>, onClose: () -> Unit, onSaved: () -> Unit) {
    val deps = LocalSpace.current
    val editor = deps.more.walletEditor
    val currency = deps.space.currency
    val today = deps.shell.today()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val edit = mode as? WalletSheetMode.Edit
    var kind by remember { mutableStateOf("bank") }
    var picked by remember { mutableStateOf<TextKey?>(null) }
    var name by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var cut by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var tried by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        kind = "bank"; picked = null; name = ""; last4 = ""; cut = false; tried = false; serverError = null
        amountText = edit?.let { amountLabel(it.wallet.openingBalanceMinor, currency, showCurrency = false) } ?: ""
        date = edit?.wallet?.openingAt ?: today
    }
    val isCash = kind == "cash"
    val amount = amountText.trim().takeIf { it.isNotEmpty() }?.let { tryParseMoney(it, currency) }
    val amountBad = amountText.isNotBlank() && (amount == null || amount < 0)
    val dateOk = isValidIsoDate(date) && date <= today
    val dup = edit == null && walletNameTaken(name, existing)
    val errors = listOfNotNull(
        if (edit == null && !isCash && picked == null) t(if (kind == "bank") TextKey.WADD_ERR_BANK else TextKey.WADD_ERR_WALLET) else null,
        if (edit == null && name.isBlank()) t(TextKey.WADD_ERR_NAME) else null,
        if (dup) t(TextKey.WADD_ERR_DUP) else null,
        if (edit == null && !isCash && last4.isNotEmpty() && last4.length != 4) t(TextKey.WADD_ERR_LAST4) else null,
        if (amountBad) t(TextKey.WADD_ERR_AMOUNT) else null,
        if (edit != null && amountText.isBlank()) t(TextKey.WADD_ERR_AMOUNT_REQUIRED) else null,
        if (amount != null && !dateOk) t(TextKey.WADD_ERR_DATE) else null,
    )
    val changed = edit == null || amount != edit.wallet.openingBalanceMinor || date != edit.wallet.openingAt
    val valid = errors.isEmpty() && changed && editor != null
    val title = if (edit != null) t(TextKey.WADD_EDIT_TITLE) else t(TextKey.WADD_TITLE)

    Sheet(visible, onClose, title = title, closeLabel = t(TextKey.SHELL_CLOSE), corner = 28.dp, spacing = 10.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(
            if (edit != null) edit.wallet.name + (edit.wallet.accountLast4?.let { " •••• $it" } ?: "") else t(TextKey.WADD_SUB, t(if (currency == Currency.EGP) TextKey.WADD_SUB_EG else TextKey.WADD_SUB_SA)),
            style = Type.of(13).copy(color = Ink.muted),
        )
        if (edit == null) {
            FieldLabelText(t(TextKey.WADD_KIND))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, label) in listOf("bank" to TextKey.WLIST_KIND_BANK, "digital_wallet" to TextKey.WLIST_KIND_DIGITAL, "cash" to TextKey.WLIST_KIND_CASH)) {
                    SelectChip(t(label), kind == k, onClick = { kind = k; picked = null; name = "" }, modifier = Modifier.weight(1f), height = 48.dp)
                }
            }
            FieldLabelText(t(if (isCash) TextKey.WADD_SUGGESTED else if (kind == "bank") TextKey.WADD_BANK else TextKey.WADD_WALLET))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in suggestions(kind, currency)) SelectChip(t(s), if (isCash) name == t(s) else picked == s, onClick = {
                    if (!isCash) picked = s
                    name = if (s in OTHER) "" else t(s)
                }, height = 44.dp)
            }
            if (isCash) NoteBox(t(TextKey.WADD_CASH_NOTE), title = t(TextKey.WADD_CASH_HEAD))
            TextInput(name, { name = it }, label = t(TextKey.WADD_NAME), placeholder = t(if (isCash) TextKey.WADD_NAME_PH_CASH else TextKey.WADD_NAME_PH),
                error = when {
                    dup -> t(TextKey.WADD_ERR_DUP)
                    tried && name.isBlank() -> t(TextKey.WADD_ERR_NAME)
                    else -> null
                })
            if (!isCash) {
                TextInput(last4, { typed -> lastFour(typed).let { last4 = it.digits; cut = it.cut } }, label = t(if (kind == "bank") TextKey.WADD_LAST4_BANK else TextKey.WADD_LAST4_WALLET),
                    ltr = true, keyboard = KeyboardType.Number, error = if (tried && last4.isNotEmpty() && last4.length != 4) t(TextKey.WADD_ERR_LAST4) else null)
                BasicText((if (cut) t(TextKey.WADD_LAST4_CUT) + " " else "") + t(if (kind == "bank") TextKey.WADD_LAST4_NOTE_BANK else TextKey.WADD_LAST4_NOTE_WALLET), style = Type.caption().copy(color = Ink.muted))
            }
        }
        TextInput(
            amountText, { amountText = it }, label = t(if (edit != null) TextKey.WDET_OPENING else TextKey.WADD_AMOUNT), placeholder = "0.00", ltr = true,
            keyboard = KeyboardType.Decimal, error = if (amountBad) t(TextKey.WADD_ERR_AMOUNT) else null,
            trailing = { BasicText(currencySymbol(currency), Modifier.padding(horizontal = 12.dp), style = Type.caption().copy(color = Ink.muted)) },
        )
        TextInput(date, { date = it.trim() }, label = t(TextKey.WADD_DATE), placeholder = today, ltr = true, enabled = edit != null || amountText.isNotBlank(),
            error = if (amount != null && !dateOk) t(TextKey.WADD_ERR_DATE) else null)
        EffectNote(edit != null, isCash, amount, date, dateOk, currency)
        if (editor == null) NotYetLine(t(TextKey.WADD_NOT_YET))
        val shown = serverError ?: if (tried && !changed) t(TextKey.WADD_ERR_UNCHANGED) else if (tried && errors.isNotEmpty()) t(TextKey.WADD_ERR_SUMMARY) else null
        if (shown != null) FieldError(shown)
        PrimaryButton(
            if (edit != null) (if (changed) t(TextKey.WADD_SAVE_OPENING) else t(TextKey.WADD_UNCHANGED)) else t(TextKey.WADD_SAVE),
            onClick = {
                tried = true
                if (!valid || editor == null) return@PrimaryButton
                scope.launch {
                    val error = runCatching {
                        if (edit != null) editor.setOpening(edit.wallet.id, amount!!, date)
                        else editor.add(NewWallet(kind, name.trim(), if (isCash) null else last4.ifEmpty { null }, amount, if (amount == null) null else date))
                    }.getOrElse { it.message ?: t(TextKey.MORE_SAVE_FAILED) }
                    if (error == null) {
                        toaster.show(if (edit != null) t(TextKey.WADD_OPENING_SAVED) else t(TextKey.WADD_ADDED, name.trim()), dark = true)
                        onSaved()
                        onClose()
                    } else serverError = error
                }
            },
            enabled = editor != null && (changed || !tried), height = 52.dp, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FieldLabelText(text: String) = BasicText(text, style = Type.of(13, FontWeight.Bold))

/** سطر الأثر: بيكرر المبلغ والتاريخ اللي اتكتبوا بس — الرصيد الجديد بيتحسب في المنطق (مش هنا). */
@Composable
private fun EffectNote(edit: Boolean, cash: Boolean, amount: Long?, date: IsoDate, dateOk: Boolean, currency: Currency) {
    val (head, body) = when {
        !edit && amount == null -> t(TextKey.WADD_EFFECT_NONE) to t(if (cash) TextKey.WADD_EFFECT_NONE_CASH else TextKey.WADD_EFFECT_NONE_BANK)
        amount == null || !dateOk -> t(TextKey.WADD_EFFECT_NEED) to t(TextKey.WADD_EFFECT_LATER)
        edit -> t(TextKey.WADD_EFFECT_RECALC, fullDate(date) ?: date) to t(TextKey.WADD_EFFECT_LATER)
        else -> t(TextKey.WADD_EFFECT_FROM, amountLabel(amount, currency), fullDate(date) ?: date) to t(TextKey.WADD_EFFECT_LATER)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(head, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
        BasicText(body, style = Type.caption().copy(color = Ink.soft))
    }
}
