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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import app.masroufy.core.AskKey
import app.masroufy.core.latinizeDigits
import app.masroufy.core.ChipBar
import app.masroufy.core.ChipRef
import app.masroufy.core.UiKey
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
 * «اسأل مصروفي» — صفحة الشات (`AssistantChat` + `AssistantStart` + `AssistantMessage` + `AssistantHistory` + `AssistantChips` + `AssistantInput`
 * في النموذج) **فوق المحرك** ([AskPresenter] ⇒ `AssistantSuite`): الرأس (السجل · محادثة جديدة · القفل)، البداية (تحية + «أمور لم تُنجزها بعد»
 * بـ«×»)، الردود بأنواعها (نص · كارت عملية · كارت تقسيم · «بتصرف عادةً منين؟» · اختيار · زراير شاشات بتنقل)، الخانة والاقتراحات من المحرك.
 * الميكروفون = التعرّف على الكلام بتاع الجوال ([LocalSpeech]). **الشاشة ما بتحسبش ولا رقم.**
 */
@Composable
fun AssistantChat(visible: Boolean, startListening: Boolean, tab: Tab, state: AskState, onClose: () -> Unit, onNavigate: (AssistTarget) -> Unit) {
    if (!visible) return
    val deps = LocalSpace.current.ask
    val currentTab by rememberUpdatedState(tab)
    val p = remember(deps) { AskPresenter(deps) { assistTabOf(currentTab) } }
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val toaster = remember { Toaster() }
    val speech = LocalSpeech.current
    val rise = remember { Animatable(if (reduce) 1f else 0f) }
    val riseSpec = motion<Float>(Springs.GENTLE)
    LaunchedEffect(Unit) { rise.animateTo(1f, riseSpec) }
    val failed = t(UiKey.ASK_NOT_READY)
    fun go(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { toaster.show(it.message ?: failed, dark = true) } }
    }
    LaunchedEffect(p) { runCatching { app.masroufy.perf.PerfTrace.span("screen:assistant") { p.open() } }.onFailure { toaster.show(it.message ?: failed, dark = true) } }
    val undo = p.undo
    LaunchedEffect(undo) { if (undo != null) { delay(ASK_UNDO_MS); p.expireUndo() } }
    val noVoice = t(AskKey.CHAT_VOICE_UNAVAILABLE)
    val unheard = t(AskKey.CHAT_VOICE_FAILED)
    fun listen() {
        if (!speech.available) toaster.show(noVoice, dark = true)
        else speech.listen { heard -> if (heard.isNullOrBlank()) toaster.show(unheard, dark = true) else go { p.send(heard) } }
    }
    LaunchedEffect(startListening) { if (startListening) listen() }
    fun navigate(target: AssistTarget) {
        onClose()
        onNavigate(target)
    }
    val actions = MessageActions(
        confirm = { go { p.confirm(it) } }, cancel = { go { p.cancel(it) } }, edit = { go { p.edit(it) } }, keep = { go { p.keep(it) } },
        pick = { id, opt -> go { p.pick(id, opt) } }, open = { navigate(targetOf(it)) },
    )

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val title = t(UiKey.ASK_TITLE)
    val newStarted = t(UiKey.ASK_NEW_STARTED)
    Overlay(onBack = onClose) {
        Box(Modifier.fillMaxSize().semantics { paneTitle = t(UiKey.ASK_DIALOG, title) }) {
            VeilLayer(Veil.CHAT, rise.value.coerceIn(0f, 1f), onDismiss = null, closeLabel = null)
            val lift = Modifier.graphicsLayer { alpha = rise.value.coerceIn(0f, 1f); translationY = (1f - rise.value) * 16.dp.toPx() }
            Column(Modifier.fillMaxSize().padding(top = top, bottom = bottom).imePadding().then(lift)) {
                ChatHeader(
                    title = p.view?.title ?: title, subtitle = p.view?.subtitle ?: t(UiKey.ASK_BRAND_TITLE),
                    canStartNew = p.view?.messages?.isNotEmpty() == true,
                    onHistory = { go { p.showHistory() } },
                    onNew = { go { if (p.newConversation()) toaster.show(newStarted, dark = true) } },
                    onClose = onClose,
                )
                val listState = rememberLazyListState()
                val messages = p.messages
                LaunchedEffect(messages.size, p.busy) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size + 1) }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (p.past != null) item(key = "past") { PastBanner { p.backToCurrent() } }
                    val v = p.view
                    if (p.past == null && v != null && v.messages.isEmpty()) item(key = "start") {
                        AssistantStart(
                            v, onClose = { go { p.closeStart(it) } },
                            onOpen = { navigate(targetOf(it)) }, onAsk = { ref: ChipRef -> go { p.chip(ref) } },
                        )
                    }
                    items(messages, key = { it.id }) { MessageBubble(it, p.today, actions, live = p.past == null && !p.busy) }
                    item(key = "typing") { if (p.busy) TypingDots() }
                }
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (undo != null && !p.historyOpen) UndoBar(undo.message) { go { p.runUndo() } }
                    InputBox(state.draft, { state.draft = it }, onMic = ::listen, onSend = {
                        val text = state.draft
                        go { if (p.send(text)) state.draft = "" }
                    })
                    p.view?.chips?.let { bar -> Suggestions(bar) { chip -> if (chip.voice) listen() else go { p.chip(chip) } } }
                }
            }
            ToastHost(toaster, Modifier.align(Alignment.BottomCenter).padding(bottom = 166.dp + bottom, start = 20.dp, end = 20.dp))
            AssistantHistory(p, onClose = { p.historyOpen = false; go { p.expireUndo() } }, onCopied = { toaster.show(it, dark = true) }, go = ::go)
        }
    }
}

/** الرأس (ارتفاع 48 على بعد 20): عدسة المساعد 40 · الاسم 16 واللقب 12 · السجل · محادثة جديدة (معطّلة لو فاضية) · القفل — أزرار 44 بزاوية 16. */
@Composable
private fun ChatHeader(title: String, subtitle: String, canStartNew: Boolean, onHistory: () -> Unit, onNew: () -> Unit, onClose: () -> Unit) {
    Row(
        // 48 زي النموذج، بس الخط العربي ارتفاع سطره أكبر من 1.3 ⇒ «على الأقل» 48 عشان اللقب ما يتقصش (اتشاف على المحاكي)
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp).heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lens = RoundedCornerShape(16.dp)
        Box(
            Modifier.padding(end = 4.dp).size(40.dp).layeredShadow(lens, brandShadow).clip(lens).background(Glass.heroBase),
            contentAlignment = Alignment.Center,
        ) { LucideIcon(Lucide.ASSISTANT, size = 20.dp, tint = Ink.lensInk) }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.of(16, FontWeight.Bold, 1.3), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(subtitle, style = Type.of(12, lineHeight = 1.4).copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton44(Lucide.HISTORY, t(UiKey.ASK_HISTORY), onHistory)
        IconButton44(Lucide.SQUARE_PEN, t(UiKey.ASK_NEW), onNew, enabled = canStartNew)
        IconButton44(Lucide.X, t(UiKey.ASK_CLOSE), onClose)
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
        val inputLabel = t(UiKey.ASK_INPUT_LABEL, t(UiKey.ASK_TITLE))
        Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (draft.isEmpty()) BasicText(t(UiKey.ASK_INPUT_HINT), style = Type.of(15).copy(color = Color(0xFF6E7F7A)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                draft, { onDraft(latinizeDigits(it)) }, singleLine = true, textStyle = Type.of(15), cursorBrush = SolidColor(Ink.primary), interactionSource = focus,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = inputLabel },
            )
        }
        BoxAction(Lucide.MIC, t(UiKey.ASK_TALK), Modifier.clip(RoundedCornerShape(14.dp)).background(Ink.selected), Ink.primary, enabled = true, onMic)
        val can = draft.isNotBlank()
        BoxAction(
            Lucide.ARROW_LEFT, t(UiKey.ASK_SEND),
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

/** الاقتراحات تحت المستطيل من المحرك: «بتسأل عنها كتير» (لو التعلم شغال) · أسئلة متابعة · اقتراحات الصفحة — والصوت بيفتح الميكروفون. */
@Composable
private fun Suggestions(bar: ChipBar, onPick: (ChipRef) -> Unit) {
    val label = bar.label.orEmpty()
    if (label.isNotEmpty()) {
        BasicText(label, Modifier.padding(horizontal = 4.dp).height(18.dp), style = Type.caption().copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = label }.padding(top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (chip in bar.chips) SuggestionChip(chip.label, chip.learned) { onPick(chip.ref) }
    }
}

private val chipShadow = listOf(ShadowLayer(0.dp, 4.dp, 10.dp, Color(0x0F1D3635)))

@Composable
private fun SuggestionChip(label: String, learned: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.height(44.dp).pressScale(press).layeredShadow(shape, chipShadow).clip(shape).background(if (learned) Ink.selected else Color(0xE0FFFFFF))
            .insetRing(shape, 1.dp, Color(0x2E08634F)).tap(press, label = label, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = Type.of(13, FontWeight.Medium).copy(color = Ink.primary), maxLines = 1) }
}
