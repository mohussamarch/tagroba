package app.masroufy.ui.screens.more

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
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
private fun suggestions(kind: String, currency: Currency): List<TextRef> = when (kind) {
    "cash" -> listOf(UiKey.WADD_CASH_HOME, UiKey.WADD_CASH_WORK, UiKey.WADD_CASH_TRAVEL, UiKey.WADD_CASH_CAR)
    "digital_wallet" -> if (currency == Currency.EGP) listOf(UiKey.WADD_EG_VODAFONE, UiKey.WADD_EG_ORANGE, UiKey.WADD_OTHER_WALLET)
    else listOf(UiKey.WADD_SA_STCPAY, UiKey.WADD_SA_URPAY, UiKey.WADD_OTHER_WALLET)
    else -> if (currency == Currency.EGP) listOf(UiKey.WADD_EG_NBE, UiKey.WADD_EG_QNB, UiKey.WADD_EG_MISR, UiKey.WADD_EG_CIB, UiKey.WADD_EG_ALEX, UiKey.WADD_OTHER_BANK)
    else listOf(UiKey.WADD_SA_RAJHI, UiKey.WADD_SA_SNB, UiKey.WADD_SA_INMA, UiKey.WADD_SA_RIYAD, UiKey.WADD_SA_ANB, UiKey.WADD_OTHER_BANK)
}

private val OTHER = setOf(UiKey.WADD_OTHER_BANK, UiKey.WADD_OTHER_WALLET)

/**
 * لوحة المحفظة: النوع (حساب بنكي · محفظة إلكترونية · كاش — رد المالك «ضيف كاش تاني») · البنك من قايمة قصيرة · الاسم (من غير تكرار) ·
 * **آخر 4 أرقام بس** (اللي اتلصق أطول بيتقص ويتقال ليه — CLAUDE.md #11) · رصيد البداية وتاريخه (فاضي = «غير متاح» مش صفر).
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
    var picked by remember { mutableStateOf<TextRef?>(null) }
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
        if (edit == null && !isCash && picked == null) t(if (kind == "bank") UiKey.WADD_ERR_BANK else UiKey.WADD_ERR_WALLET) else null,
        if (edit == null && name.isBlank()) t(UiKey.WADD_ERR_NAME) else null,
        if (dup) t(UiKey.WADD_ERR_DUP) else null,
        if (edit == null && !isCash && last4.isNotEmpty() && last4.length != 4) t(UiKey.WADD_ERR_LAST4) else null,
        if (amountBad) t(UiKey.WADD_ERR_AMOUNT) else null,
        if (edit != null && amountText.isBlank()) t(UiKey.WADD_ERR_AMOUNT_REQUIRED) else null,
        if (amount != null && !dateOk) t(UiKey.WADD_ERR_DATE) else null,
    )
    val changed = edit == null || amount != edit.wallet.openingBalanceMinor || date != edit.wallet.openingAt
    val valid = errors.isEmpty() && changed && editor != null
    val title = if (edit != null) t(UiKey.WADD_EDIT_TITLE) else t(UiKey.WADD_TITLE)

    Sheet(visible, onClose, title = title, closeLabel = t(UiKey.SHELL_CLOSE), corner = 28.dp, spacing = 10.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(
            if (edit != null) edit.wallet.name + (edit.wallet.accountLast4?.let { " •••• $it" } ?: "") else t(UiKey.WADD_SUB, t(if (currency == Currency.EGP) UiKey.WADD_SUB_EG else UiKey.WADD_SUB_SA)),
            style = Type.of(13).copy(color = Ink.muted),
        )
        if (edit == null) {
            FieldLabelText(t(UiKey.WADD_KIND))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, label) in listOf("bank" to UiKey.WLIST_KIND_BANK, "digital_wallet" to UiKey.WLIST_KIND_DIGITAL, "cash" to UiKey.WLIST_KIND_CASH)) {
                    SelectChip(t(label), kind == k, onClick = { kind = k; picked = null; name = "" }, modifier = Modifier.weight(1f), height = 48.dp)
                }
            }
            FieldLabelText(t(if (isCash) UiKey.WADD_SUGGESTED else if (kind == "bank") UiKey.WADD_BANK else UiKey.WADD_WALLET))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in suggestions(kind, currency)) SelectChip(t(s), if (isCash) name == t(s) else picked == s, onClick = {
                    if (!isCash) picked = s
                    name = if (s in OTHER) "" else t(s)
                }, height = 44.dp)
            }
            if (isCash) NoteBox(t(UiKey.WADD_CASH_NOTE), title = t(UiKey.WADD_CASH_HEAD))
            TextInput(name, { name = it }, label = t(UiKey.WADD_NAME), placeholder = t(if (isCash) UiKey.WADD_NAME_PH_CASH else UiKey.WADD_NAME_PH),
                error = when {
                    dup -> t(UiKey.WADD_ERR_DUP)
                    tried && name.isBlank() -> t(UiKey.WADD_ERR_NAME)
                    else -> null
                })
            if (!isCash) {
                TextInput(last4, { typed -> lastFour(typed).let { last4 = it.digits; cut = it.cut } }, label = t(if (kind == "bank") UiKey.WADD_LAST4_BANK else UiKey.WADD_LAST4_WALLET),
                    ltr = true, keyboard = KeyboardType.Number, error = if (tried && last4.isNotEmpty() && last4.length != 4) t(UiKey.WADD_ERR_LAST4) else null)
                BasicText((if (cut) t(UiKey.WADD_LAST4_CUT) + " " else "") + t(if (kind == "bank") UiKey.WADD_LAST4_NOTE_BANK else UiKey.WADD_LAST4_NOTE_WALLET), style = Type.caption().copy(color = Ink.muted))
            }
        }
        TextInput(
            amountText, { amountText = it }, label = t(if (edit != null) UiKey.WDET_OPENING else UiKey.WADD_AMOUNT), placeholder = "0.00", ltr = true,
            keyboard = KeyboardType.Decimal, error = if (amountBad) t(UiKey.WADD_ERR_AMOUNT) else null,
            trailing = { BasicText(currencySymbol(currency), Modifier.padding(horizontal = 12.dp), style = Type.caption().copy(color = Ink.muted)) },
        )
        TextInput(date, { date = it.trim() }, label = t(UiKey.WADD_DATE), placeholder = today, ltr = true, enabled = edit != null || amountText.isNotBlank(),
            error = if (amount != null && !dateOk) t(UiKey.WADD_ERR_DATE) else null)
        EffectNote(edit != null, isCash, amount, date, dateOk, currency)
        if (editor == null) NotYetLine(t(UiKey.WADD_NOT_YET))
        val shown = serverError ?: if (tried && !changed) t(UiKey.WADD_ERR_UNCHANGED) else if (tried && errors.isNotEmpty()) t(UiKey.WADD_ERR_SUMMARY) else null
        if (shown != null) FieldError(shown)
        PrimaryButton(
            if (edit != null) (if (changed) t(UiKey.WADD_SAVE_OPENING) else t(UiKey.WADD_UNCHANGED)) else t(UiKey.WADD_SAVE),
            onClick = {
                tried = true
                if (!valid || editor == null) return@PrimaryButton
                scope.launch {
                    val error = runCatching {
                        if (edit != null) editor.setOpening(edit.wallet.id, amount!!, date)
                        else editor.add(NewWallet(kind, name.trim(), if (isCash) null else last4.ifEmpty { null }, amount, if (amount == null) null else date))
                    }.getOrElse { it.message ?: t(UiKey.MORE_SAVE_FAILED) }
                    if (error == null) {
                        toaster.show(if (edit != null) t(UiKey.WADD_OPENING_SAVED) else t(UiKey.WADD_ADDED, name.trim()), dark = true)
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
        !edit && amount == null -> t(UiKey.WADD_EFFECT_NONE) to t(if (cash) UiKey.WADD_EFFECT_NONE_CASH else UiKey.WADD_EFFECT_NONE_BANK)
        amount == null || !dateOk -> t(UiKey.WADD_EFFECT_NEED) to t(UiKey.WADD_EFFECT_LATER)
        edit -> t(UiKey.WADD_EFFECT_RECALC, fullDate(date) ?: date) to t(UiKey.WADD_EFFECT_LATER)
        else -> t(UiKey.WADD_EFFECT_FROM, amountLabel(amount, currency), fullDate(date) ?: date) to t(UiKey.WADD_EFFECT_LATER)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(head, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
        BasicText(body, style = Type.caption().copy(color = Ink.soft))
    }
}
