package app.masroufy.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.UiKey
import app.masroufy.core.bankByKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.choiceArea
import app.masroufy.ui.overlay.rememberChoice
import app.masroufy.ui.screens.more.BankPickerList
import app.masroufy.ui.screens.more.PickedBankRow
import app.masroufy.ui.screens.more.lastFour
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** المبلغ اللي اتكتب بالهللة (فاضي ⇒ null · غلط أو سالب ⇒ -1 عشان الخانة تقول الغلط). القراية بس — مفيش حساب. */
private fun typedMinor(text: String, currency: Currency): Long? {
    if (text.isBlank()) return null
    val v = tryParseMoney(text.trim(), currency) ?: return -1
    return if (v < 0) -1 else v
}

@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, label: String, currency: Currency, bad: Boolean) {
    TextInput(
        value, onChange, label = label, placeholder = "0.00", ltr = true, keyboard = KeyboardType.Decimal,
        error = if (bad) t(UiKey.WADD_ERR_AMOUNT) else null,
        unit = currencySymbol(currency),
    )
}

/** «إضافة» (أساسي) و«تخطي» جنب بعض — نص المالك (§81). */
@Composable
private fun AddOrSkip(addLabel: String, addEnabled: Boolean, onAdd: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(addLabel, onAdd, Modifier.weight(1f), enabled = addEnabled, height = 48.dp)
        TonalButton(t(UiKey.ONB_MONEY_SKIP), onSkip, Modifier.weight(1f), height = 48.dp)
    }
}

/** الكيبورد يقفل بعد «إضافة» (كان بيفضل مفتوح ورا لوحة الرد — رحلة أول تشغيل 2026-10-15). */
@Composable
private fun rememberHideKeyboard(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    return remember(keyboard, focus) { { focus.clearFocus(); keyboard?.hide() } }
}

/** «معاك كام كاش؟»: المبلغ ⇒ «إضافة» (رصيد افتتاح لمحفظة الكاش بتاريخ النهارده — `OnboardAccount`) · «تخطي». */
@Composable
internal fun CashCard(s: OnbState, currency: Currency, onChange: (OnbState) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val minor = typedMinor(text, currency)
    val hideKeyboard = rememberHideKeyboard()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MoneyField(text, { text = it }, t(UiKey.ONB_CASH_AMOUNT), currency, bad = minor == -1L)
        AddOrSkip(t(UiKey.ONB_MONEY_ADD), minor != null && minor >= 0, onAdd = {
            if (minor != null && minor >= 0) {
                hideKeyboard()
                onChange(addCash(s, minor))
            }
        }, onSkip = { hideKeyboard(); onChange(skip(s)) })
    }
}

/**
 * «عندك حساب بنكي؟»: «إضافة» ⇒ «اختار بنكك» (دليل بلد الحساب — البنوك بس) ⇒ البنك المختار + آخر 4 أرقام + رصيده دلوقتي ⇒ «ضيف البنك».
 * «تخطي» في كل خطوة. الحفظ نفسه في `OnboardAccount.finish` (نفس تأكيد «أضف محفظة» — `ManageWallets`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BankCard(s: OnbState, country: String, currency: Currency, onChange: (OnbState) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var bankKey by rememberSaveable { mutableStateOf<String?>(s.answers.bank?.bankKey) }
    var last4 by rememberSaveable { mutableStateOf(s.answers.bank?.last4.orEmpty()) }
    var cut by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val bank = bankByKey(bankKey)
    val hideKeyboard = rememberHideKeyboard()
    // «أضف البنك» يفضل باين فوق الكيبورد وهو بيكتب آخر 4 أو الرصيد (كان بيستخبى تحته — رحلة أول تشغيل 2026-10-15)
    val addRow = remember { BringIntoViewRequester() }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeOpen, last4, text, bankKey) {
        if (imeOpen && bankKey != null) {
            delay(250)
            runCatching { addRow.bringIntoView() }
        }
    }
    // قايمة البنوك مفتوحة ⇒ الضغط برّه الكارت أو الرجوع بيقفلها والبنك المختار قبلها زي ما هو (HANDOVER §6.5 بند 11)
    val choice = rememberChoice(picking, onClose = { picking = false })
    Column(Modifier.choiceArea(choice), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            picking -> {
                BankPickerList(country, "bank", onPick = { b -> if (b != null) { bankKey = b.key; picking = false } }, allowOther = false, maxHeight = 300.dp)
                TonalButton(t(UiKey.ONB_MONEY_SKIP), { onChange(skip(s)) }, Modifier.fillMaxWidth(), height = 44.dp, muted = true)
            }
            bank == null -> AddOrSkip(t(UiKey.ONB_MONEY_ADD), true, onAdd = { picking = true }, onSkip = { onChange(skip(s)) })
            else -> {
                PickedBankRow(bank) { picking = true }
                TextInput(last4, { typed -> lastFour(typed).let { last4 = it.digits; cut = it.cut } }, label = t(UiKey.WADD_LAST4_BANK), ltr = true,
                    keyboard = KeyboardType.Number, error = if (last4.isNotEmpty() && last4.length != 4) t(UiKey.WADD_ERR_LAST4) else null)
                BasicText((if (cut) t(UiKey.WADD_LAST4_CUT) + " " else "") + t(UiKey.WADD_LAST4_NOTE_BANK), style = Type.caption().copy(color = Ink.muted))
                // من غير آخر 4: رسايل التحويل بين حساباتك بتستنى وتسألك (قرار المالك 2026-10-15 مساءً)
                if (last4.isEmpty()) BasicText(t(UiKey.ONB_LAST4_TRANSFER_NOTE), style = Type.caption().copy(color = Ink.muted))
                val minor = typedMinor(text, currency)
                MoneyField(text, { text = it }, t(UiKey.ONB_BANK_BALANCE), currency, bad = minor == -1L)
                val ok = minor != -1L && (last4.isEmpty() || last4.length == 4)
                AddOrSkip(t(UiKey.ONB_BANK_ADD), ok, onAdd = {
                    if (ok) {
                        hideKeyboard()
                        onChange(addBank(s, OnbBank(bank.key, last4.ifEmpty { null }, minor)))
                    }
                }, onSkip = { hideKeyboard(); onChange(skip(s)) }, modifier = Modifier.bringIntoViewRequester(addRow))
            }
        }
    }
}
