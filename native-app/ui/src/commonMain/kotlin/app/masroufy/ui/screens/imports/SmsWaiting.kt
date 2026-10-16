package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.SmsKind
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.shell.parseHex
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** النبرة من اتجاه الرسالة (المبلغ ما بيورثش لون التصنيف). */
fun toneOf(direction: Direction) = if (direction == Direction.IN) AmountTone.INCOME else AmountTone.EXPENSE

/** أفعال قسم «بانتظار تأكيدك» — الشاشة هي اللي بتنادي حالات الاستخدام. */
class WaitingActions(
    val pickCategory: (WaitLineUi) -> Unit,
    val pickWallet: (BankWaitingUi) -> Unit,
    val toggleSimilar: (WaitLineUi) -> Unit,
    val dismiss: (String) -> Unit,
    val recordAll: () -> Unit,
    val recordByHand: (FailedUi, String) -> Unit,
    /** OVERRIDES §91-1: «الحساب ده بتاعك؟» — true = «أيوه، ده حسابي» (بيختار المحفظة) · false = «لأ». */
    val answerAccount: (WaitLineUi, Boolean) -> Unit = { _, _ -> },
    /** r05: «أكّد الشكل ده» (3c) · «أيوه ده راتبي» (3b) · «سجّلها وحدد الطرف» (4c) · صفحات الصندوق (4b). */
    val confirmLine: (WaitLineUi) -> Unit = {},
    val salaryYes: (WaitLineUi) -> Unit = {},
    val recordParty: (WaitLineUi) -> Unit = {},
    val older: () -> Unit = {},
    val newest: () -> Unit = {},
)

/**
 * «بانتظار تأكيدك» (`SmsWaiting`): بنك من غير محفظة · رسايل مفهومة مستنية تأكيدك (بسببها) · شبه عملية موجودة · رسالة ما اتفهمتش.
 * [chosen] = التصنيف اللي اختاره المالك لرسالة · [included] = الشبيه اللي هيتسجل مع «سجّل الكل» · [handError] = خطأ المبلغ المكتوب.
 */
fun LazyListScope.smsWaitingItems(
    ui: BankSmsUi,
    currency: Currency,
    categories: Map<String, Category>,
    chosen: Map<String, String>,
    included: Set<String>,
    busy: Boolean,
    handError: Pair<String, String>?,
    actions: WaitingActions,
    skip: Int = 0,
    hidden: Set<String> = emptySet(),
) {
    // r05 (4b): العدد الحقيقي في الصندوق (مش أول 200 بس) — واللي اتختار ومستني «تراجع» مش محسوب (4a)
    val n = maxOf(0, ui.trueWaiting - hidden.size)
    val lines = ui.confirm.filter { it.messageId !in hidden }
    item(key = "waiting-head") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(UiKey.SMS_WAITING_TITLE), style = Type.of(15, FontWeight.Bold))
                CountBadge(sentenceNumber(n), waiting = n > 0)
            }
            if (n == 0) TintedPanel(PanelTone.MINT, radius = 18.dp) { BasicText(t(UiKey.SMS_WAITING_ALL_DONE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary)) }
        }
    }
    val bankKeys = uniqueKeys(ui.banks.map { it.sender }, "waiting-bank-")
    itemsIndexed(ui.banks, key = { i, _ -> bankKeys[i] }) { _, bank -> BankCard(bank, currency) { actions.pickWallet(bank) } }
    if (lines.isNotEmpty()) item(key = "waiting-confirm-title") {
        BasicText(t(UiKey.SMS_WAITING_CONFIRM_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.focus))
    }
    // r051: سطر لكل رسالة (عنصر كسول بمفتاح الرسالة) — كانت الـ200 سطر كلهم في عنصر واحد ⇒ إطار واحد 13 ثانية بعد ما الصفحة تتحمّل
    val lineKeys = uniqueKeys(lines.map { it.messageId }, "waiting-line-")
    itemsIndexed(lines, key = { i, _ -> lineKeys[i] }) { _, line -> FloatingCard(Modifier.fillMaxWidth()) { ConfirmRow(line, currency, categories, chosen, actions) } }
    val similarKeys = uniqueKeys(ui.similar.map { it.messageId }, "waiting-similar-")
    itemsIndexed(ui.similar, key = { i, _ -> similarKeys[i] }) { _, line -> SimilarCard(line, currency, line.messageId in included, actions) }
    val failedKeys = uniqueKeys(ui.failed.map { it.messageId }, "waiting-failed-")
    itemsIndexed(ui.failed, key = { i, _ -> failedKeys[i] }) { _, f -> FailedCard(f, currency, handError?.takeIf { it.first == f.messageId }?.second, actions) }
    val toRecord = lines.size + ui.similar.count { it.messageId in included }
    if (toRecord > 0) item(key = "waiting-record") {
        PrimaryButton(t(UiKey.SMS_WAITING_RECORD, opsCountObj(toRecord)), actions.recordAll, Modifier.fillMaxWidth(), loading = busy)
    }
    if (ui.total > ui.pageSize || skip > 0) item(key = "waiting-pages") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(UiKey.SMS_R05_WAIT_SHOWN, sentenceNumber(ui.pageSize), sentenceNumber(ui.total)), style = Type.caption().copy(color = Ink.muted))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (skip > 0) QuietButton(t(UiKey.SMS_R05_WAIT_NEWEST), actions.newest, Modifier.weight(1f))
                if (skip + ui.pageSize < ui.total) TonalButton(t(UiKey.SMS_R05_WAIT_OLDER), actions.older, Modifier.weight(1f))
            }
        }
    }
}

/** مفتاح لكل سطر بمعرّف رسالته — رجلين تحويل بين محفظتين (نفس الرسالة مرتين) بياخدوا مفتاح تاني بالترتيب (المفتاح المكرر بيوقّع القايمة). */
private fun uniqueKeys(ids: List<String>, prefix: String): List<String> {
    val seen = HashMap<String, Int>()
    return ids.map { id ->
        val n = (seen[id] ?: 0) + 1
        seen[id] = n
        if (n == 1) prefix + id else "$prefix$id#$n"
    }
}

@Composable
private fun BankCard(bank: BankWaitingUi, currency: Currency, onPick: () -> Unit) {
    TintedPanel(PanelTone.AMBER) {
        BasicText(t(UiKey.SMS_WAITING_BANK_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.focus))
        BasicText(t(UiKey.SMS_WAITING_BANK_BODY, msgsCount(bank.count), bank.sender), style = Type.body())
        if (bank.samples.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.65f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                bank.samples.forEachIndexed { i, s ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText(s.merchant, Modifier.weight(1f), style = Type.of(13), maxLines = 1)
                        AmountText(s.amountMinor, currency, tone = toneOf(s.direction), size = 13, showCurrency = false)
                    }
                }
            }
        }
        BasicText(t(UiKey.SMS_WAITING_BANK_ONCE), style = Type.caption().copy(color = Ink.focus))
        PrimaryButton(t(UiKey.SMS_WAITING_PICK_WALLET), onPick, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConfirmRow(line: WaitLineUi, currency: Currency, categories: Map<String, Category>, chosen: Map<String, String>, actions: WaitingActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // r05 (3a): اسم المرسل («AlRajhiBank») عمره ما يبقى عنوان العملية
            BasicText(line.merchant.ifBlank { t(UiKey.SMS_R05_LINE_NO_NAME) }, Modifier.weight(1f), style = Type.bodyBold(), maxLines = 1)
            AmountText(line.amountMinor, currency, tone = toneOf(line.direction), showCurrency = false)
        }
        BasicText(dayMonth(line.date), style = Type.caption().copy(color = Ink.muted))
        if (line.reason != null) BasicText(line.reason, style = Type.caption().copy(color = Ink.focus))
        val asked = line.askLast4
        if (asked != null && line.walletId != null) {
            BasicText(t(UiKey.SMS_ACCOUNT_ASK, asked), style = Type.of(13, FontWeight.Bold))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalButton(t(UiKey.SMS_ACCOUNT_YES), { actions.answerAccount(line, true) }, Modifier.weight(1f), height = 40.dp)
                TonalButton(t(UiKey.SMS_ACCOUNT_NO), { actions.answerAccount(line, false) }, Modifier.weight(1f), height = 40.dp, muted = true)
            }
        }
        LineChoices(line, categories, chosen, actions)
    }
}

/**
 * r05: اختيارات الرسالة المستنية — رسالة «ده راتبك؟» ⇒ شريحة «راتب» و«أيوه ده راتبي» (3b) · تحويل لشخص أو منه ⇒ «سجّلها وحدد الطرف» (4c) ·
 * غير كده ⇒ شريحة التصنيف (اختيارها بيسجّل الرسالة — 4a) · و«أكّد الشكل ده» (3c) بيسجّلها وكل رسايل نفس الشكل.
 */
@Composable
private fun LineChoices(line: WaitLineUi, categories: Map<String, Category>, chosen: Map<String, String>, actions: WaitingActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            line.salaryAsk -> {
                SelectChip(t(UiKey.SMS_R05_SALARY_CHIP), true, {}, height = 36.dp)
                TonalButton(t(UiKey.SMS_R05_SALARY_YES), { actions.salaryYes(line) }, Modifier.weight(1f), height = 36.dp)
            }
            line.kind == SmsKind.TRANSFER_IN || line.kind == SmsKind.TRANSFER_OUT ->
                TonalButton(t(UiKey.SMS_R05_PARTY_RECORD), { actions.recordParty(line) }, Modifier.weight(1f), height = 36.dp)
            else -> {
                val catId = chosen[line.messageId] ?: line.categoryId
                val cat = catId?.let { categories[it] }
                SelectChip(cat?.name ?: t(UiKey.IMPORTS_UNCATEGORIZED), chosen.containsKey(line.messageId), { actions.pickCategory(line) }, dot = cat?.let { parseHex(it.lightColor) }, height = 36.dp)
                QuietButton(t(UiKey.SMS_R05_CONFIRM_SHAPE), { actions.confirmLine(line) }, Modifier.weight(1f), height = 36.dp)
            }
        }
    }
}

@Composable
private fun SimilarCard(line: WaitLineUi, currency: Currency, included: Boolean, actions: WaitingActions) {
    FloatingCard(Modifier.fillMaxWidth()) {
        val title = if (line.conflict) UiKey.SMS_WAITING_CONFLICT_TITLE else UiKey.SMS_WAITING_SIMILAR_TITLE
        BasicText(t(title), style = Type.of(13, FontWeight.Bold).copy(color = if (line.conflict) Ink.expense else Ink.focus))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            BasicText(t(UiKey.SMS_WAITING_FROM_MESSAGE), style = Type.of(11).copy(color = Ink.muted))
            BasicText(line.merchant.ifBlank { line.sender }, style = Type.bodyBold())
            AmountText(line.amountMinor, currency, tone = toneOf(line.direction), showCurrency = false)
            BasicText(dayMonth(line.date), style = Type.of(11).copy(color = Ink.muted))
        }
        if (line.reason != null) BasicText(line.reason, style = Type.caption().copy(color = Ink.muted))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton(t(UiKey.SMS_WAITING_SAME_DROP), { actions.dismiss(line.messageId) }, Modifier.weight(1f))
            if (!line.conflict) {
                if (included) TonalButton(t(UiKey.SMS_WAITING_WILL_RECORD), { actions.toggleSimilar(line) }, Modifier.weight(1f))
                else PrimaryButton(t(UiKey.SMS_WAITING_RECORD_IT), { actions.toggleSimilar(line) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FailedCard(f: FailedUi, currency: Currency, error: String?, actions: WaitingActions) {
    var typing by remember(f.messageId) { mutableStateOf(false) }
    var amount by remember(f.messageId) { mutableStateOf("") }
    FloatingCard(Modifier.fillMaxWidth()) {
        BasicText(t(UiKey.SMS_WAITING_UNCLEAR_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.focus))
        if (f.body != null) {
            BasicText(
                t(UiKey.IMPORTS_QUOTED, f.body),
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 12.dp, vertical = 10.dp),
                style = Type.of(13, lineHeight = 1.7).copy(color = Ink.muted),
                maxLines = 6,
            )
        }
        BasicText(t(UiKey.SMS_WAITING_REASON, f.reason), style = Type.caption().copy(color = Ink.muted))
        if (f.foreign) BasicText(t(UiKey.SMS_WAITING_FOREIGN_NOTE), style = Type.caption().copy(color = Ink.focus))
        if (typing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                TextInput(
                    amount, { amount = it }, Modifier.weight(1f), placeholder = "0.00", ltr = true, keyboard = KeyboardType.Decimal, imeAction = ImeAction.Done,
                    error = error, unit = currencySymbol(currency),
                )
                PrimaryButton(t(UiKey.SMS_WAITING_SAVE), { actions.recordByHand(f, amount) }, enabled = amount.isNotBlank())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton(t(UiKey.SMS_WAITING_DROP), { actions.dismiss(f.messageId) }, Modifier.weight(1f))
                TonalButton(t(UiKey.SMS_WAITING_BY_HAND), { typing = true }, Modifier.weight(1f))
            }
        }
    }
}
