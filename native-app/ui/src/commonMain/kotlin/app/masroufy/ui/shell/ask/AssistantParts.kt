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
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
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

/** الكارت الهادي جوه الشات (`AssistantStart`): أبيض متدرج بزاوية 20 وظل قريب + بعيد خفيف. */
private val calmShadow = listOf(
    ShadowLayer(0.dp, 1.dp, 0.dp, Color.White, inset = true),
    ShadowLayer(0.dp, 1.dp, 2.dp, Color(0x0D1D3635)),
    ShadowLayer(0.dp, 10.dp, 24.dp, Color(0x141D3635)),
)

/**
 * بداية محادثة جديدة (`AssistantStart`): تحية بالوقت والاسم + «أنا مصروفي، مساعدك المالي الذكي…» + «أمور لم تُنجزها بعد».
 * القايمة دي (رسايل بنك مستنية · فاتورة متكررة ما اتسجلتش · تصنيف وصل نسبة التنبيه · دين فات ميعاده — أقصى ٤) **مالهاش حالة استخدام لسه**
 * (آخر §76 «ناقص في كوتلن») ⇒ كارت واحد بيقول «غير متاح بعد» بدل كروت بأرقام مخترعة (CLAUDE.md #10 · #15).
 */
@Composable
internal fun AssistantStart() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(rememberGreeting(), Modifier.semantics { heading() }, style = Type.of(22, FontWeight.Bold))
            BasicText(t(TextKey.ASK_INTRO, t(TextKey.ASK_TITLE), t(TextKey.ASK_BRAND_TITLE)), style = Type.of(14, lineHeight = 1.7).copy(color = Ink.soft))
        }
        BasicText(t(TextKey.ASK_TODO_TITLE), Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp).semantics { heading() }, style = Type.of(15, FontWeight.Bold))
        val shape = RoundedCornerShape(20.dp)
        Row(
            Modifier.fillMaxWidth().layeredShadow(shape, calmShadow).clip(shape).background(Glass.card).innerSheen(shape, calmShadow)
                .padding(start = 10.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x1A637570)), contentAlignment = Alignment.Center) {
                LucideIcon(Lucide.CLOCK, size = 20.dp, tint = Ink.muted)
            }
            BasicText(t(TextKey.ASK_TODO_NOT_READY), Modifier.weight(1f), style = Type.of(14, lineHeight = 1.6))
        }
        BasicText(t(TextKey.ASK_TODO_BASIS), Modifier.padding(horizontal = 2.dp), style = Type.caption().copy(color = Ink.muted))
    }
}

/**
 * فقاعة (`AssistantMessage`): بتاعتك يمين الشاشة في الإنجليزي/شمالها في العربي (آخر السطر) خضرا بعرض 80٪ بالكتير، ورد المساعد كارت أبيض
 * بعرض 88٪ بالكتير. الزوايا بالظبط زي النموذج (ركن صغير 6).
 */
@Composable
internal fun MessageBubble(message: ChatMessage) {
    val mine = message.mine
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth(if (mine) 0.8f else 0.88f), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
            val shape = if (mine) AbsoluteRoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else AbsoluteRoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
            val surface = if (mine) Modifier.layeredShadow(shape, mineShadow).clip(shape).background(Glass.primary)
            else Modifier.layeredShadow(shape, calmShadow).clip(shape).background(Glass.card).innerSheen(shape, calmShadow)
            BasicText(
                message.text,
                surface.padding(horizontal = 14.dp, vertical = 10.dp),
                style = Type.of(14, lineHeight = 1.7).copy(color = if (mine) Color.White else Ink.text),
            )
        }
    }
}

private val mineShadow = listOf(ShadowLayer(0.dp, 8.dp, 18.dp, Color(0x3808634F)))

/** «مصروفي يكتب»: ٣ نقط بتطلع وتنزل (900ms، كل واحدة متأخرة 150) — «تقليل الحركة» ⇒ ثابتة. */
@Composable
internal fun TypingDots() {
    val reduce = LocalReduceMotion.current
    val anim = rememberInfiniteTransition(label = "typing")
    val shape = AbsoluteRoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    Row(
        Modifier.semantics { contentDescription = t(TextKey.ASK_TYPING) }.layeredShadow(shape, Shadows.chip).clip(shape).background(Glass.card)
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
