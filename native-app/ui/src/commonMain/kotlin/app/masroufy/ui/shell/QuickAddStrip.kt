package app.masroufy.ui.shell

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.amount
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.Type

/**
 * حاجة بتتدفع كل شهر وما اتسجلتش الشهر ده («دفعت الإيجار؟»). [question] و[name] من حالة الاستخدام (أو المستخدم)، و[usualMinor] المبلغ المعتاد.
 * ⚠️ **المنطق نفسه لسه مش في كوتلن** (OVERRIDES §76: «مفيش منطق حاجة بتتكرر كل شهر وما ظهرتش الشهر ده … ومفيش تخطّي الشهر ده») ⇒
 * الشريط بيستنى حالة استخدام تدّيله السطور؛ من غيرها ما بيظهرش.
 */
data class QuickAddItem(
    val key: String,
    val question: String,
    val name: String,
    val usualMinor: Halalas,
    val usualDay: Int,
    val icon: Lucide,
    val color: Color,
    val currency: Currency,
    val defaultWalletId: Id?,
)

/** محفظة في «من أين دفعتها؟». */
data class WalletChoice(val id: Id, val label: String)

/**
 * شريط «إضافة سريعة» فوق العمليات (`QuickAddStrip` — OVERRIDES §76): حبّات صغيرة (52، زاوية 26) فيها دايرة 40 بأيقونة 24 والسؤال 13 عريض،
 * **من غير عنوان قسم**. الضغطة ⇒ لوحة صغيرة المبلغ المعتاد جاهز ويتعدّل + منين ⇒ «سجّلها» ([onRecord] — نص المبلغ زي ما اتكتب، الحساب
 * في حالة الاستخدام) أو «لن أدفعها هذا الشهر» ([onSkip]). [error] = سبب الرفض من حالة الاستخدام (جنب المبلغ).
 */
@Composable
fun QuickAddStrip(
    items: List<QuickAddItem>,
    wallets: List<WalletChoice>,
    onRecord: (item: QuickAddItem, amountText: String, walletId: Id?) -> Unit,
    onSkip: (QuickAddItem) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf<QuickAddItem?>(null) }
    var amountText by remember { mutableStateOf("") }
    var walletId by remember { mutableStateOf<Id?>(null) }
    LazyRow(
        modifier.fullBleed().semantics { contentDescription = t(UiKey.QUICK_TITLE) },
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.key }) { item ->
            QuickPill(item) {
                open = item
                amountText = amount(item.usualMinor, item.currency)
                walletId = item.defaultWalletId ?: wallets.firstOrNull()?.id
            }
        }
    }
    val current = open
    Sheet(visible = current != null, onDismiss = { open = null }, title = current?.question.orEmpty(), closeLabel = t(UiKey.SHELL_CLOSE), corner = 28.dp, spacing = 10.dp) {
        if (current == null) return@Sheet
        BasicText(current.question, style = Type.of(17, FontWeight.Bold))
        BasicText(t(UiKey.QUICK_SHEET_BODY), style = Type.of(13).copy(color = Ink.muted))
        TextInput(
            amountText, { amountText = it }, ltr = true, keyboard = KeyboardType.Decimal, height = 56.dp, textSize = 24, error = error,
            trailing = { BasicText(currencySymbol(current.currency), Modifier.padding(end = 16.dp), style = Type.of(14).copy(color = Ink.muted)) },
        )
        BasicText(t(UiKey.QUICK_FROM), style = Type.of(13, FontWeight.Bold))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in wallets) SelectChip(w.label, w.id == walletId, { walletId = w.id }, Modifier.weight(1f), height = 44.dp)
        }
        PrimaryButton(t(UiKey.QUICK_SAVE), onClick = { onRecord(current, amountText, walletId) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        TonalButton(t(UiKey.QUICK_SKIP), onClick = { onSkip(current); open = null }, modifier = Modifier.fillMaxWidth(), muted = true, height = 44.dp)
    }
}

@Composable
private fun QuickPill(item: QuickAddItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val press = rememberPress()
    val usual = t(UiKey.QUICK_USUAL, amountLabel(item.usualMinor, item.currency), sentenceNumber(item.usualDay))
    Row(
        Modifier.height(52.dp).pressScale(press).layeredShadow(shape, Shadows.chip).clip(shape).background(Glass.card).innerSheen(shape, Shadows.chip)
            .tap(press, label = item.question, onClick = onClick).semantics { contentDescription = item.question + " — " + usual }
            .padding(start = 6.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(item.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            LucideIcon(item.icon, size = 24.dp, tint = item.color)
        }
        BasicText(item.question, style = Type.of(13, FontWeight.Bold), maxLines = 1)
    }
}
