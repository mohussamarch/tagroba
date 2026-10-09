package app.masroufy.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth as fullWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Overlay
import app.masroufy.ui.overlay.Veil
import app.masroufy.ui.overlay.VeilLayer
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Bubble(val id: Int, val mine: Boolean, val text: String)

/**
 * «اسأل مصروفي» — المحادثة (`AskSheet` = `AskBar` في النموذج، §73/§74): الشاشة بتتموّه (`rgba(250,249,243,.66)` + بلور 18)، رأس فيه «مصروفي»
 * و«أنت في «[context]»»، ترحيب، اقتراحات حسب التبويب، وخانة كتابة فيها الميكروفون وزرار الإرسال.
 * ⚠️ **مفيش مساعد فعلًا:** مفيش خدمة ذكاء اصطناعي على الباقة المجانية ومزوّدها مش معتمد (§73 ❓) ⇒ أي سؤال بيترد بصراحة «غير متاح بعد»
 * (من غير ردود مخترعة)، والصوت كمان. الشكل كله زي النموذج عشان لما المساعد يتوصل يتحط مكان الرد بس.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskSheet(visible: Boolean, startListening: Boolean, context: String, onClose: () -> Unit) {
    if (!visible) return
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val log = remember { mutableStateListOf<Bubble>() }
    var typing by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val rise = remember { Animatable(if (reduce) 1f else 0f) }
    val riseSpec = motion<Float>(Springs.GENTLE)
    LaunchedEffect(Unit) { rise.animateTo(1f, riseSpec) }

    fun reply(text: String) = scope.launch {
        typing = true
        delay(900)
        typing = false
        log += Bubble(log.size, false, text)
    }
    fun ask(text: String) {
        if (text.isBlank()) return
        log += Bubble(log.size, true, text.trim())
        reply(t(TextKey.ASK_NOT_READY))
    }
    fun listen() {
        if (listening) return
        listening = true
        scope.launch {
            delay(1800)
            listening = false
            reply(t(TextKey.ASK_VOICE_NOT_READY))
        }
    }
    LaunchedEffect(startListening) { if (startListening) listen() }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Overlay(onBack = onClose) {
        Box(Modifier.fillMaxSize()) {
            VeilLayer(Veil.CHAT, rise.value.coerceIn(0f, 1f), onDismiss = null, closeLabel = null)
            val lift = Modifier.graphicsLayer { alpha = rise.value.coerceIn(0f, 1f); translationY = (1f - rise.value) * 16.dp.toPx() }
            Column(Modifier.fillMaxSize().padding(top = top, bottom = bottom).imePadding().then(lift)) {
                Header(context, onClose)
                val listState = rememberLazyListState()
                LaunchedEffect(log.size, typing) { if (log.isNotEmpty()) listState.animateScrollToItem(log.size + 1) }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { BubbleView(Bubble(-1, false, t(TextKey.ASK_GREETING))) }
                    if (log.isEmpty()) item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (key in listOf(TextKey.ASK_CHIP_SPENT, TextKey.ASK_CHIP_VOICE, TextKey.ASK_CHIP_SPLIT, TextKey.ASK_CHIP_PLAN)) {
                                Chip(t(key)) { if (key == TextKey.ASK_CHIP_VOICE) listen() else ask(t(key)) }
                            }
                        }
                    }
                    items(log, key = { it.id }) { BubbleView(it) }
                    item { if (typing) TypingDots() }
                }
                InputBar(draft, { draft = it }, listening, onMic = { listen() }, onSend = { ask(draft); draft = "" })
            }
        }
    }
}

@Composable
private fun Header(context: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp).height(48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).layeredShadow(RoundedCornerShape(16.dp), Shadows.hero).clip(RoundedCornerShape(16.dp)).background(Glass.heroBase), contentAlignment = Alignment.Center) {
            LucideIcon(Lucide.SPARKLES, size = 20.dp, tint = Ink.lensInk)
        }
        Column(Modifier.weight(1f)) {
            BasicText(t(TextKey.ASK_TITLE), style = Type.of(16, FontWeight.Bold))
            BasicText(t(TextKey.ASK_CONTEXT, context), style = Type.caption().copy(color = Ink.muted), maxLines = 1)
        }
        SurfaceIconButton(Lucide.X, t(TextKey.ASK_CLOSE), onClose, iconSize = 20.dp)
    }
}

@Composable
private fun BubbleView(b: Bubble) {
    val shape = if (b.mine) AbsoluteRoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else AbsoluteRoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    Box(Modifier.fullWidth(), contentAlignment = if (b.mine) Alignment.CenterEnd else Alignment.CenterStart) {
        val surface = if (b.mine) Modifier.layeredShadow(shape, Shadows.segment).clip(shape).background(Glass.primary)
        else Modifier.layeredShadow(shape, Shadows.chip).clip(shape).background(Glass.card).innerSheen(shape, Shadows.chip)
        BasicText(
            b.text,
            Modifier.widthIn(max = 300.dp).then(surface).padding(horizontal = 14.dp, vertical = 10.dp),
            style = Type.of(14, lineHeight = 1.7).copy(color = if (b.mine) Color.White else Ink.text),
        )
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(18.dp)
    BasicText(
        label,
        Modifier.defaultMinSize(minHeight = 40.dp).pressScale(press).layeredShadow(shape, Shadows.chip).clip(shape).background(Color(0xC7FFFFFF))
            .insetRing(shape, 1.dp, Color(0x2E08634F)).tap(press, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        style = Type.of(13, FontWeight.Medium).copy(color = Ink.primary),
    )
}

@Composable
private fun TypingDots() {
    val reduce = LocalReduceMotion.current
    val anim = rememberInfiniteTransition(label = "typing")
    val shape = AbsoluteRoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    Row(
        Modifier.semantics { contentDescription = t(TextKey.ASK_TYPING) }.layeredShadow(shape, Shadows.chip).clip(shape).background(Glass.card).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (i in 0 until 3) {
            val a by anim.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900, delayMillis = i * 150), RepeatMode.Reverse), label = "dot$i")
            Box(Modifier.size(7.dp).graphicsLayer { alpha = if (reduce) 1f else a; translationY = if (reduce) 0f else -3.dp.toPx() * a }.clip(CircleShape).background(Ink.muted))
        }
    }
}

@Composable
private fun InputBar(draft: String, onDraft: (String) -> Unit, listening: Boolean, onMic: () -> Unit, onSend: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 8.dp).height(56.dp).layeredShadow(shape, Shadows.ask).clip(shape)
            .background(Glass.nav(true)).innerSheen(shape, Shadows.ask).padding(start = 8.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (listening) Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(Lucide.MIC, size = 20.dp, tint = Ink.primary)
                BasicText(t(TextKey.ASK_LISTENING), style = Type.bodyBold().copy(color = Ink.primary))
            } else Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) BasicText(t(TextKey.ASK_INPUT_HINT), style = Type.of(14).copy(color = Color(0xFF8A9A95)), maxLines = 1)
                BasicTextField(
                    draft, onDraft, singleLine = true, textStyle = Type.of(14), cursorBrush = SolidColor(Ink.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = t(TextKey.ASK_INPUT_LABEL) },
                )
            }
        }
        RoundAction(Lucide.MIC, t(TextKey.ASK_TALK), Ink.selected, Ink.primary, onMic)
        RoundAction(Lucide.ARROW_LEFT, t(TextKey.ASK_SEND), null, Color.White, onSend)
    }
}

@Composable
private fun RoundAction(icon: Lucide, label: String, bg: Color?, ink: Color, onClick: () -> Unit) {
    val press = rememberPress()
    val surface = if (bg == null) Modifier.layeredShadow(CircleShape, Shadows.segment).clip(CircleShape).background(Glass.primary) else Modifier.clip(CircleShape).background(bg)
    Box(
        Modifier.size(44.dp).pressScale(press).then(surface).tap(press, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 20.dp, tint = ink) }
}
