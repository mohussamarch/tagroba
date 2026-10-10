package app.masroufy.ui.shell.ask

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.AskKey
import app.masroufy.core.ChipRef
import app.masroufy.core.ScreenLink
import app.masroufy.core.StartAction
import app.masroufy.core.StartItem
import app.masroufy.core.StartItemKind
import app.masroufy.core.TextKey
import app.masroufy.core.UiKey
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.ShadowLayer
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.ChatView

/** الكارت الهادي جوه الشات (`AssistantStart`): أبيض متدرج بزاوية 20 وظل قريب + بعيد خفيف. */
internal val calmShadow = listOf(
    ShadowLayer(0.dp, 1.dp, 0.dp, Color.White, inset = true),
    ShadowLayer(0.dp, 1.dp, 2.dp, Color(0x0D1D3635)),
    ShadowLayer(0.dp, 10.dp, 24.dp, Color(0x141D3635)),
)

/**
 * بداية محادثة جديدة (`AssistantStart`): التحية والسطر من المحرك + «أمور لم تُنجزها بعد» (أقصى ٤ — رسايل بنك مستنية · فاتورة ما اتسجلتش ·
 * تصنيف عند نسبة التنبيه · دين ليك فات ميعاده **معلومة بس**). **كل كارت عليه «×»** بيقفله على الحساب (L1 — قرار المالك)، وكلهم اتقفلوا ⇒
 * «مفيش حاجة مستنياك دلوقتي» (نص المحرك).
 */
@Composable
internal fun AssistantStart(view: ChatView, onClose: (StartItem) -> Unit, onOpen: (ScreenLink) -> Unit, onAsk: (ChipRef) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(view.greeting, Modifier.semantics { heading() }, style = Type.of(22, FontWeight.Bold))
            if (view.intro.isNotBlank()) BasicText(view.intro, style = Type.of(14, lineHeight = 1.7).copy(color = Ink.soft))
        }
        BasicText(t(TextKey.ASSIST_TODO_TITLE), Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp).semantics { heading() }, style = Type.of(15, FontWeight.Bold))
        for (item in view.start?.items.orEmpty()) StartCard(item, onClose, onOpen, onAsk)
        view.startNote?.let { note ->
            val shape = RoundedCornerShape(20.dp)
            BasicText(
                note,
                Modifier.fillMaxWidth().layeredShadow(shape, calmShadow).clip(shape).background(Glass.card).innerSheen(shape, calmShadow).padding(16.dp),
                style = Type.of(14, lineHeight = 1.6).copy(color = Ink.soft),
            )
        }
        BasicText(t(TextKey.ASSIST_START_BASIS), Modifier.padding(horizontal = 2.dp), style = Type.caption().copy(color = Ink.muted))
    }
}

@Composable
private fun StartCard(item: StartItem, onClose: (StartItem) -> Unit, onOpen: (ScreenLink) -> Unit, onAsk: (ChipRef) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val (icon, tint) = when (item.kind) {
        StartItemKind.SMS_WAITING -> Lucide.MESSAGE_SQUARE_TEXT to Color(0xFF956000)
        StartItemKind.BILL_UNRECORDED -> Lucide.RECEIPT to Color(0xFF956000)
        StartItemKind.CATEGORY_AT_THRESHOLD -> Lucide.UTENSILS to Color(0xFFA36A21)
        StartItemKind.DEBT_OVERDUE -> Lucide.HAND_COINS to Color(0xFFBE3D48)
    }
    Row(
        Modifier.fillMaxWidth().layeredShadow(shape, calmShadow).clip(shape).background(Glass.card).innerSheen(shape, calmShadow)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            LucideIcon(icon, size = 20.dp, tint = tint)
        }
        BasicText(item.text, Modifier.weight(1f), style = Type.of(14, lineHeight = 1.6))
        when (val a = item.action) {
            is StartAction.OpenScreen -> SmallAction(a.label) { onOpen(a.link) }
            is StartAction.AskInChat -> SmallAction(a.label) { onAsk(a.chip) }
            null -> Unit
        }
        val label = t(AskKey.CHAT_START_CLOSE, item.text)
        val press = rememberPress()
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).tap(press, label = label, onClick = { onClose(item) }).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { LucideIcon(Lucide.X, size = 18.dp, tint = Ink.muted) }
    }
}

/** زرار صغير على كارت (44 ارتفاع) — «راجعها» · «سجّلها» · «افتح». */
@Composable
internal fun SmallAction(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
            .then(if (primary) Modifier.background(Glass.primary) else Modifier.background(Ink.selected)).graphicsLayer { alpha = if (enabled) 1f else 0.5f }.tap(press, enabled, label = label, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = Type.of(13, FontWeight.Bold).copy(color = if (primary) Color.White else Ink.primary), maxLines = 1) }
}

/** فوق محادثة مفتوحة من السجل: «محادثة من السجل» + «ارجع للمحادثة الحالية». */
@Composable
internal fun PastBanner(onBack: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Ink.selected).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(t(AskKey.CHAT_HISTORY_VIEWING), Modifier.weight(1f), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
        SmallAction(t(AskKey.CHAT_HISTORY_BACK), onClick = onBack)
    }
}

/** «اتقفل الكارت» · «اتمسح السؤال» · «اتمسحت «…»» + «تراجع» (٤ ثواني — [ASK_UNDO_MS]). */
@Composable
internal fun UndoBar(text: String, onUndo: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xEB193D33)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, Modifier.weight(1f), style = Type.of(13, FontWeight.Bold).copy(color = Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
        val press = rememberPress()
        val label = t(AskKey.CHAT_UNDO)
        Box(
            Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1AFFFFFF)).tap(press, label = label, onClick = onUndo).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText(label, style = Type.of(13, FontWeight.Bold).copy(color = Ink.mint)) }
    }
}

/** «مصروفي يكتب»: ٣ نقط بتطلع وتنزل (900ms، كل واحدة متأخرة 150) — «تقليل الحركة» ⇒ ثابتة. */
@Composable
internal fun TypingDots() {
    val reduce = LocalReduceMotion.current
    val anim = rememberInfiniteTransition(label = "typing")
    val shape = AbsoluteRoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    Row(
        Modifier.semantics { contentDescription = t(UiKey.ASK_TYPING) }.layeredShadow(shape, Shadows.chip).clip(shape).background(Glass.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (i in 0 until 3) {
            val a by anim.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900, delayMillis = i * 150), RepeatMode.Reverse), label = "dot$i")
            Box(
                Modifier.size(7.dp).graphicsLayer { alpha = if (reduce) 1f else a; translationY = if (reduce) 0f else -3.dp.toPx() * a }
                    .clip(CircleShape).background(Ink.muted),
            )
        }
    }
}
