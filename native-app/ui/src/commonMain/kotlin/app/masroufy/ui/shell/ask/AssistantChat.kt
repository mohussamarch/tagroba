package app.masroufy.ui.shell.ask

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.IconButton44
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.ShadowLayer
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.overlay.Overlay
import app.masroufy.ui.overlay.Veil
import app.masroufy.ui.overlay.VeilLayer
import app.masroufy.ui.shell.ToastHost
import app.masroufy.ui.shell.Toaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «اسأل مصروفي» — صفحة الشات (`AskSheet` = `AssistantChat` + `AssistantStart` + `AssistantMessage` + `AssistantHistory` في النموذج، آخر §76):
 * فوق الشاشة كلها (الشريطين بيستخبوا)، رأس فيه «مصروفي» و«مساعدك المالي الذكي» والسجل و«محادثة جديدة» والقفل، البداية (ترحيب + «أمور لم تُنجزها
 * بعد»)، الفقاعات، ومستطيل كتابة 52 واضح والاقتراحات تحته حسب التبويب.
 * ⚠️ **مفيش مساعد فعلًا:** مفيش خدمة ذكاء اصطناعي على الباقة المجانية ومزوّدها مش معتمد (§73 ❓)، و«أمور لم تُنجزها» والتعلّم من الأسئلة
 * مالهمش حالات استخدام لسه (آخر §76 «ناقص في كوتلن») ⇒ أي سؤال بيترد بصراحة «غير متاح بعد» (من غير ردود ولا أرقام مخترعة)، والصوت كمان.
 * الشكل كله زي النموذج عشان لما المساعد يتوصل يتحط مكان الرد بس. المحادثة نفسها في [AskState] (بتفضل لحد «محادثة جديدة»).
 */
@Composable
fun AssistantChat(visible: Boolean, startListening: Boolean, tab: Tab, state: AskState, onClose: () -> Unit) {
    if (!visible) return
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val toaster = remember { Toaster() }
    var historyOpen by remember { mutableStateOf(false) }
    val rise = remember { Animatable(if (reduce) 1f else 0f) }
    val riseSpec = motion<Float>(Springs.GENTLE)
    LaunchedEffect(Unit) { rise.animateTo(1f, riseSpec) }
    val notReady = t(TextKey.ASK_NOT_READY)
    val voiceNotReady = t(TextKey.ASK_VOICE_NOT_READY)
    LaunchedEffect(startListening) { if (startListening) toaster.show(voiceNotReady, dark = true) }

    fun ask(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        state.add(mine = true, text = clean)
        scope.launch {
            state.typing = true
            delay(700)
            state.typing = false
            state.add(mine = false, text = notReady)
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val title = t(TextKey.ASK_TITLE)
    val newStarted = t(TextKey.ASK_NEW_STARTED)
    Overlay(onBack = onClose) {
        Box(Modifier.fillMaxSize().semantics { paneTitle = t(TextKey.ASK_DIALOG, title) }) {
            VeilLayer(Veil.CHAT, rise.value.coerceIn(0f, 1f), onDismiss = null, closeLabel = null)
            val lift = Modifier.graphicsLayer { alpha = rise.value.coerceIn(0f, 1f); translationY = (1f - rise.value) * 16.dp.toPx() }
            Column(Modifier.fillMaxSize().padding(top = top, bottom = bottom).imePadding().then(lift)) {
                ChatHeader(
                    canStartNew = !state.current.isEmpty,
                    onHistory = { historyOpen = true },
                    onNew = { if (state.startNew()) toaster.show(newStarted, dark = true) },
                    onClose = onClose,
                )
                val listState = rememberLazyListState()
                val messages = state.current.messages
                LaunchedEffect(messages.size, state.typing) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size + 1) }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (messages.isEmpty()) item(key = "start") { AssistantStart() }
                    items(messages, key = { it.id }) { MessageBubble(it) }
                    item(key = "typing") { if (state.typing) TypingDots() }
                }
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputBox(state.draft, { state.draft = it }, onMic = { toaster.show(voiceNotReady, dark = true) }, onSend = { ask(state.draft); state.draft = "" })
                    Suggestions(tab) { key -> if (key == TextKey.ASK_CHIP_VOICE) toaster.show(voiceNotReady, dark = true) else ask(t(key)) }
                }
            }
            ToastHost(toaster, Modifier.align(Alignment.BottomCenter).padding(bottom = 166.dp + bottom, start = 20.dp, end = 20.dp))
            AssistantHistory(historyOpen, state) { historyOpen = false }
        }
    }
}

/** الرأس (ارتفاع 48 على بعد 20): عدسة المساعد 40 · الاسم 16 واللقب 12 · السجل · محادثة جديدة (معطّلة لو فاضية) · القفل — أزرار 44 بزاوية 16. */
@Composable
private fun ChatHeader(canStartNew: Boolean, onHistory: () -> Unit, onNew: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp).height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lens = RoundedCornerShape(16.dp)
        Box(
            Modifier.padding(end = 4.dp).size(40.dp).layeredShadow(lens, brandShadow).clip(lens).background(Glass.heroBase),
            contentAlignment = Alignment.Center,
        ) { LucideIcon(Lucide.ASSISTANT, size = 20.dp, tint = Ink.lensInk) }
        Column(Modifier.weight(1f)) {
            BasicText(t(TextKey.ASK_TITLE), style = Type.of(16, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(t(TextKey.ASK_BRAND_TITLE), style = Type.caption().copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton44(Lucide.HISTORY, t(TextKey.ASK_HISTORY), onHistory)
        IconButton44(Lucide.SQUARE_PEN, t(TextKey.ASK_NEW), onNew, enabled = canStartNew)
        IconButton44(Lucide.X, t(TextKey.ASK_CLOSE), onClose)
    }
}

private val brandShadow = listOf(ShadowLayer(0.dp, 1.dp, 0.dp, Color(0x38FFFFFF), inset = true), ShadowLayer(0.dp, 8.dp, 18.dp, Color(0x40064B40)))
private val boxShadow = listOf(ShadowLayer(0.dp, 10.dp, 26.dp, Color(0x1A1D3635)))
private val boxShadowFocused = listOf(ShadowLayer(0.dp, 10.dp, 26.dp, Color(0x2E08634F)))

/**
 * مستطيل الكتابة (طلب المالك: «واضح إن في مستطيل أدوس عليه علشان أكتب»): 52 بزاوية 18 أبيض بإطار باين 1.5 بيقوى لـ2 أخضر لما تدوس،
 * وجواه الميكروفون (44 على `#DCEBD6`) والإرسال (44 أخضر — باهت لحد ما تكتب).
 */
@Composable
private fun InputBox(draft: String, onDraft: (String) -> Unit, onMic: () -> Unit, onSend: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val focus = remember { MutableInteractionSource() }
    val focused by focus.collectIsFocusedAsState()
    val ring = if (focused) Modifier.insetRing(shape, 2.dp, Ink.primary) else Modifier.insetRing(shape, 1.5.dp, Color(0x4008634F))
    Row(
        Modifier.fillMaxWidth().height(52.dp).layeredShadow(shape, if (focused) boxShadowFocused else boxShadow).clip(shape).background(Color.White)
            .then(ring).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val inputLabel = t(TextKey.ASK_INPUT_LABEL, t(TextKey.ASK_TITLE))
        Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (draft.isEmpty()) BasicText(t(TextKey.ASK_INPUT_HINT), style = Type.of(15).copy(color = Color(0xFF6E7F7A)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                draft, onDraft, singleLine = true, textStyle = Type.of(15), cursorBrush = SolidColor(Ink.primary), interactionSource = focus,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = inputLabel },
            )
        }
        BoxAction(Lucide.MIC, t(TextKey.ASK_TALK), Modifier.background(Ink.selected), Ink.primary, enabled = true, onMic)
        val can = draft.isNotBlank()
        BoxAction(
            Lucide.ARROW_LEFT, t(TextKey.ASK_SEND),
            Modifier.layeredShadow(RoundedCornerShape(14.dp), if (can) Shadows.segment else emptyList()).clip(RoundedCornerShape(14.dp)).background(Glass.primary),
            Color.White, enabled = can, onSend,
        )
    }
}

@Composable
private fun BoxAction(icon: Lucide, label: String, surface: Modifier, ink: Color, enabled: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(44.dp).graphicsLayer { alpha = if (enabled) 1f else 0.5f }.pressScale(press, enabled).then(surface).clip(RoundedCornerShape(14.dp))
            .tap(press, enabled, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 20.dp, tint = ink) }
}

/** الاقتراحات تحت المستطيل حسب التبويب اللي إنت فيه (`BY_CTX` في النموذج — من غير أسامي ناس مخترعة). التعلّم من أسئلتك لسه ما اتبناش. */
private fun suggestionsFor(tab: Tab): List<TextKey> = when (tab) {
    Tab.HOME -> listOf(TextKey.ASK_CHIP_SPENT, TextKey.ASK_CHIP_VOICE, TextKey.ASK_CHIP_SPLIT, TextKey.ASK_CHIP_PLAN)
    Tab.OPERATIONS -> listOf(TextKey.ASK_CHIP_VOICE, TextKey.ASK_CHIP_SPENT, TextKey.ASK_CHIP_SPLIT)
    Tab.PEOPLE -> listOf(TextKey.ASK_CHIP_OWED, TextKey.ASK_CHIP_SPLIT, TextKey.ASK_CHIP_VOICE)
    Tab.INVESTMENT -> listOf(TextKey.ASK_CHIP_PLAN, TextKey.ASK_CHIP_SPENT, TextKey.ASK_CHIP_VOICE)
}

@Composable
private fun Suggestions(tab: Tab, onPick: (TextKey) -> Unit) {
    val label = t(TextKey.ASK_CHIPS_FOR, t(tab.label))
    BasicText(label, Modifier.padding(horizontal = 4.dp).height(18.dp), style = Type.caption().copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = label }.padding(top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (key in suggestionsFor(tab)) SuggestionChip(t(key)) { onPick(key) }
    }
}

private val chipShadow = listOf(ShadowLayer(0.dp, 4.dp, 10.dp, Color(0x0F1D3635)))

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.height(44.dp).pressScale(press).layeredShadow(shape, chipShadow).clip(shape).background(Color(0xE0FFFFFF))
            .insetRing(shape, 1.dp, Color(0x2E08634F)).tap(press, label = label, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = Type.of(13, FontWeight.Medium).copy(color = Ink.primary), maxLines = 1) }
}

/** الاسم ونص الساعة للتحية — من الهيكل (`ShellDeps.me` · `hourNow`). */
@Composable
internal fun rememberGreeting(): String {
    val shell = LocalSpace.current.shell
    var name by remember(shell) { mutableStateOf<String?>(null) }
    LaunchedEffect(shell) { name = runCatching { shell.me().displayName }.getOrNull()?.takeIf { it.isNotBlank() } }
    val morning = shell.hourNow() in 4..11
    val who = name
    return when {
        who != null && morning -> t(TextKey.GREETING_MORNING_NAME, who)
        who != null -> t(TextKey.GREETING_EVENING_NAME, who)
        morning -> t(TextKey.GREETING_MORNING)
        else -> t(TextKey.GREETING_EVENING)
    }
}
