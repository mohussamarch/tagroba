package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.PAYER_CUSTOM_EMOJIS
import app.masroufy.core.PAYER_CUSTOM_NAME_MAX
import app.masroufy.core.PayerCustomKind
import app.masroufy.core.PayerWhy
import app.masroufy.core.R06Key
import app.masroufy.core.RefundSum
import app.masroufy.core.dayMonth
import app.masroufy.core.formatMoney
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.RefundPicks

/** أيقونات الكارت من التصميم (`MoneyWhat` · `RefundWait`) — مش في المشتركة. */
internal object MoneyWhatIcons {
    val LOAN = Lucide("MW_LOAN", "m17 2 4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "m7 22-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3")
    val ROTATE = Lucide("MW_ROTATE", "M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5")
}

private val TileBg = ChipInk.greyBg

/** السطر الأصفر: ليه التحويل ده مش شبه المرتب. */
@Composable
internal fun MwWhyLine(why: PayerWhy) {
    val text = t(R06Key.MW_WHY) + (if (why.amountFar) t(R06Key.MW_WHY_AMOUNT) else "") + (if (why.dateFar) t(R06Key.MW_WHY_DATE) else "") + "."
    BasicText(
        text, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.alertBg).padding(horizontal = 10.dp, vertical = 8.dp),
        style = Type.of(12, lineHeight = 1.7).copy(color = Ink.focus),
    )
}

private class MwTile(val pick: MwPick, val index: Int, val label: String, val hint: String, val icon: Lucide?, val emoji: String?, val color: Color)

/** المربعات: الثلاثة الجاهزة · المحفوظة للحساب ده · «حاجة تانية» (بعرض الصف لو لوحدها). */
@Composable
internal fun MwTiles(customs: List<PayerCustomKind>, payerName: String, onPick: (MwPick, Int) -> Unit) {
    val tiles = listOf(
        MwTile(MwPick.REFUND, -1, t(R06Key.PAYER_ASK_REFUND), t(R06Key.MW_HINT_REFUND), Lucide.RECEIPT, null, Ink.transfer),
        MwTile(MwPick.BONUS, -1, t(R06Key.PAYER_ASK_BONUS), t(R06Key.MW_HINT_BONUS), Lucide.GIFT, null, Ink.focus),
        MwTile(MwPick.LOAN, -1, t(R06Key.PAYER_ASK_ADVANCE), t(R06Key.MW_HINT_LOAN), MoneyWhatIcons.LOAN, null, app.masroufy.ui.theme.CategoryInk.gifts),
    ) + customs.mapIndexed { i, c -> MwTile(MwPick.CUSTOM, i, c.name, t(R06Key.MW_SAVED_FOR, payerName), null, c.emoji, Ink.primary) } +
        MwTile(MwPick.OTHER, -1, t(R06Key.PAYER_ASK_OTHER), t(R06Key.MW_HINT_OTHER), Lucide.PENCIL, null, Ink.muted)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in tiles.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tile in row) MwTileBox(tile, Modifier.weight(1f)) { onPick(tile.pick, tile.index) }
            }
        }
    }
}

@Composable
private fun MwTileBox(tile: MwTile, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 92.dp).clip(RoundedCornerShape(18.dp)).background(TileBg)
            .selectable(selected = false, role = Role.RadioButton, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(tile.color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            if (tile.icon != null) LucideIcon(tile.icon, size = 18.dp, tint = tile.color) else BasicText(tile.emoji.orEmpty(), style = Type.of(18))
        }
        BasicText(tile.label, style = Type.of(14, FontWeight.Bold))
        BasicText(tile.hint, style = Type.of(11).copy(color = Ink.muted))
    }
}

/** مربع العلامة (22) — أخضر بعلامة صح لما يتعلّم. */
@Composable
internal fun MwTick(on: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(22.dp).clip(shape).background(if (on) Ink.primary else Color.White).then(if (on) Modifier else Modifier.border(1.5.dp, Ink.faded, shape)),
        contentAlignment = Alignment.Center,
    ) { if (on) LucideIcon(Lucide.CHECK, size = 14.dp, tint = Color.White) }
}

/** «ده استرداد لأنهي مشترى؟»: المشتريات بعلامات (أكتر من واحد) وسطر المجموع تحتها. */
@Composable
internal fun MwRefundList(picks: RefundPicks, chosen: Set<Id>, sum: RefundSum, onToggle: (Id) -> Unit) {
    BasicText(t(R06Key.MW_REFUND_ASK), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
    if (picks.candidates.isEmpty()) BasicText(t(R06Key.MW_REFUND_EMPTY), style = Type.of(12).copy(color = Ink.muted))
    for (c in picks.candidates) {
        val on = c.expense.id in chosen
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape).background(if (on) Ink.primary.copy(alpha = 0.05f) else Color.White)
                .border(1.5.dp, if (on) Ink.primary else Ink.line, shape)
                .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(c.expense.id) }).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MwTick(on)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(c.expense.rawMerchantName ?: c.expense.rawDescription.orEmpty(), Modifier.weight(1f, fill = false), style = Type.of(14, FontWeight.Bold), maxLines = 1)
                    if (c.waiting) MwBluePill(t(R06Key.MW_WAITING), small = true)
                }
                BasicText(dayMonth(c.expense.occurredAt.take(10)), style = Type.of(12).copy(color = Ink.muted))
            }
            BasicText("−" + formatMoney(c.netMinor, c.expense.currency), style = Type.of(14, FontWeight.Bold))
        }
    }
    val picked = formatMoney(sum.pickedMinor)
    val (text, color) = when {
        sum.diffMinor == 0L -> t(R06Key.MW_SUM_EXACT, picked) to Ink.primary
        sum.diffMinor > 0 -> t(R06Key.MW_SUM_LEFT, picked, formatMoney(sum.diffMinor)) to Ink.focus
        else -> t(R06Key.MW_SUM_OVER, picked, formatMoney(-sum.diffMinor)) to Ink.focus
    }
    BasicText(text, style = Type.of(12, FontWeight.Bold).copy(color = color))
}

/** العلامة الزرقا «مستني استرداد». */
@Composable
internal fun MwBluePill(text: String, small: Boolean = false) {
    BasicText(
        text,
        Modifier.clip(RoundedCornerShape(if (small) 8.dp else 9.dp)).background(if (small) ChipInk.blueBg else Ink.transfer)
            .padding(horizontal = if (small) 7.dp else 9.dp, vertical = if (small) 1.dp else 2.dp),
        style = Type.of(if (small) 11 else 12, FontWeight.Bold).copy(color = if (small) Ink.transfer else Ink.onPrimary),
    )
}

/** «حاجة تانية»: الاسم + إيموجي من الـ12. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MwOtherFields(name: String, onName: (String) -> Unit, emoji: String, onEmoji: (String) -> Unit) {
    TextInput(name, { onName(it.replace("\t", " ").take(PAYER_CUSTOM_NAME_MAX)) }, label = t(R06Key.MW_OTHER_ASK), placeholder = t(R06Key.MW_OTHER_PH))
    BasicText(t(R06Key.MW_EMOJI_ASK), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (e in PAYER_CUSTOM_EMOJIS) {
            val on = e == emoji
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.size(44.dp).clip(shape).background(if (on) Ink.primary.copy(alpha = 0.08f) else Color.White)
                    .border(if (on) 2.dp else 1.5.dp, if (on) Ink.primary else Ink.line, shape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onEmoji(e) }),
                contentAlignment = Alignment.Center,
            ) { BasicText(e, style = Type.of(20)) }
        }
    }
}

/** الأثر قبل الحفظ (أخضر). */
@Composable
internal fun MwEffect(text: String) {
    BasicText(
        text, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.primary.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 8.dp),
        style = Type.of(13, lineHeight = 1.7).copy(color = Ink.primary),
    )
}

/** «خليها قاعدة للمرات الجاية» — فاضية من الأول. */
@Composable
internal fun MwRuleBox(on: Boolean, body: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(TileBg)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() }).padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 1.dp)) { MwTick(on) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(t(R06Key.MW_RULE_TITLE), style = Type.of(13, FontWeight.Bold))
            BasicText(body, style = Type.of(12, lineHeight = 1.6).copy(color = Ink.muted))
        }
    }
}

/** بعد الحفظ: السطر الأخضر و«تراجع». */
@Composable
internal fun MwDoneBox(text: String, onUndo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.primary.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(text, Modifier.weight(1f), style = Type.of(13, lineHeight = 1.7).copy(color = Ink.primary))
        TonalButton(t(app.masroufy.core.UiKey.TRANSFER_PARTY_UNDO), onUndo, height = 40.dp)
    }
}
