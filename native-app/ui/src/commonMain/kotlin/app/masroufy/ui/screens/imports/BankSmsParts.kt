package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.mintSheen
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.parseHex
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** سطر الحالة فوق (`BankSms`): تعمل (أخضر) · متوقفة أو الإذن مسحوب (كهرماني + زرار) · الآيفون (رمادي + «الصق رسالة»). null = بيحمّل. */
@Composable
fun SmsStatusBanner(status: SmsStatus?, onSettings: () -> Unit, onPaste: () -> Unit) {
    if (status == null) return
    val (title, body) = when (status) {
        SmsStatus.READING -> UiKey.BANK_SMS_STATUS_OK to UiKey.BANK_SMS_STATUS_OK_BODY
        SmsStatus.OFF -> UiKey.BANK_SMS_STATUS_STOPPED to UiKey.BANK_SMS_STATUS_OFF_BODY
        SmsStatus.PERMISSION -> UiKey.BANK_SMS_STATUS_STOPPED to UiKey.BANK_SMS_STATUS_PERM_BODY
        SmsStatus.UNAVAILABLE -> UiKey.BANK_SMS_STATUS_IOS to UiKey.BANK_SMS_STATUS_IOS_BODY
    }
    val (ink, bg) = when (status) {
        SmsStatus.READING -> Ink.primary to Ink.selected
        SmsStatus.OFF, SmsStatus.PERMISSION -> Ink.focus to Ink.alertBg
        SmsStatus.UNAVAILABLE -> Ink.muted to Ink.text.copy(alpha = 0.06f)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(ink)
        Column(Modifier.weight(1f)) {
            BasicText(t(title), style = Type.of(13, FontWeight.Bold).copy(color = ink))
            BasicText(t(body), style = Type.caption().copy(color = ink))
        }
        val button = when (status) {
            SmsStatus.READING -> null
            SmsStatus.OFF -> UiKey.BANK_SMS_OPEN_SETTINGS to onSettings
            SmsStatus.PERMISSION -> UiKey.BANK_SMS_REGRANT to onSettings
            SmsStatus.UNAVAILABLE -> UiKey.BANK_SMS_PASTE to onPaste
        }
        if (button != null) {
            val press = rememberPress()
            Box(
                Modifier.height(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.85f))
                    .tap(press, onClick = button.second).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(t(button.first), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary)) }
        }
    }
}

/** «سُجّلت تلقائيًا اليوم»: صف لكل عملية (التصنيف بلونه · المقترح يتأكد · المتجر الجديد يتصنّف) + لمعة على اللي اتسجل دلوقتي. */
@Composable
fun RecordedToday(rows: List<RecordedUi>, categories: List<Category>, onConfirm: (RecordedUi) -> Unit, onCategorize: (RecordedUi) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CountTitle(t(UiKey.BANK_SMS_AUTO_TITLE), opsCount(rows.size))
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Divider()
                RecordedRow(row, categories.firstOrNull { it.id == row.categoryId }, onConfirm, onCategorize)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordedRow(row: RecordedUi, category: Category?, onConfirm: (RecordedUi) -> Unit, onCategorize: (RecordedUi) -> Unit) {
    val color = category?.let { parseHex(it.lightColor) } ?: Ink.muted
    Row(
        Modifier.fillMaxWidth().mintSheen(row.sheen).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(color) { LucideIcon(Lucide.TAG, size = 18.dp, tint = color) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(row.merchant, style = Type.bodyBold(), maxLines = 1)
            BasicText(category?.name ?: t(UiKey.IMPORTS_UNCATEGORIZED), style = Type.caption().copy(color = Ink.muted), maxLines = 1)
            if (row.need != RecordedNeed.NONE) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (row.need == RecordedNeed.CONFIRM) Tag(t(UiKey.BANK_SMS_SUGGESTED), TagTone.AMBER) else Tag(t(UiKey.BANK_SMS_NEW_SHOP), TagTone.MUTED)
                    SmallAction(t(if (row.need == RecordedNeed.CONFIRM) UiKey.BANK_SMS_CONFIRM else UiKey.BANK_SMS_CATEGORIZE)) {
                        if (row.need == RecordedNeed.CONFIRM) onConfirm(row) else onCategorize(row)
                    }
                }
            }
        }
        AmountText(row.amountMinor, row.currency, tone = toneOf(row.direction), showCurrency = false)
    }
}

/** زرار صغير 32 (أخضر على أخضر 8%) جوه الصف — «تأكيد» · «صنّفها» · «قارن». */
@Composable
fun SmallAction(text: String, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.height(32.dp).pressScale(press).clip(RoundedCornerShape(12.dp)).background(Ink.primary.copy(alpha = 0.08f)).tap(press, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = Type.of(12, FontWeight.Bold).copy(color = Ink.primary)) }
}

/** آخر الشاشة: الإشعار بييجي للمستني بس، ومن مجموعة «رسائل البنك» ⇒ إعدادات الإشعارات. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmsFooter(onLink: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(t(UiKey.BANK_SMS_FOOTER), style = Type.caption().copy(color = Ink.muted))
        val press = rememberPress()
        BasicText(t(UiKey.BANK_SMS_FOOTER_LINK), Modifier.tap(press, onClick = onLink), style = Type.captionBold().copy(color = Ink.primary))
    }
}

/** «رسائل «البنك» أين تُسجَّل؟» — مرة واحدة لكل بنك، والمستني بيتسجل أول ما تختار. المحافظ غير الكاش (حسابات بنك ومحافظ إلكترونية). */
@Composable
fun BankWalletSheet(bank: BankWaitingUi?, wallets: List<Wallet>, onDismiss: () -> Unit, onSave: (BankWaitingUi, Wallet) -> Unit) {
    var picked by remember(bank) { mutableStateOf<Wallet?>(null) }
    val title = bank?.let { t(UiKey.SMS_WAITING_SHEET_TITLE, it.sender) }.orEmpty()
    Sheet(bank != null, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE)) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(t(UiKey.SMS_WAITING_SHEET_BODY), style = Type.of(13).copy(color = Ink.muted))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in wallets.filter { it.kind != "cash" }) {
                RadioRow(w.name, t(UiKey.SMS_WAITING_SHEET_BANK), picked?.id == w.id, { picked = w })
            }
        }
        PrimaryButton(
            t(UiKey.SMS_WAITING_SHEET_SAVE),
            { val b = bank; val w = picked; if (b != null && w != null) onSave(b, w) },
            Modifier.fillMaxWidth(),
            enabled = picked != null,
        )
    }
}
