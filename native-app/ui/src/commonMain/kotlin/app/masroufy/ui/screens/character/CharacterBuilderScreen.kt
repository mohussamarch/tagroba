package app.masroufy.ui.screens.character

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.character.CharacterArt
import app.masroufy.ui.character.CharacterFromTop
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.screenBackground
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Route
import app.masroufy.ui.screens.common.ScreenHeader
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.shell.MeAvatar
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** «ابنِ شخصيتك» / «عدّل شخصيتك» — من «ملفك» ومن الضغطة الطويلة على مكانك في «الأشخاص» ومن «كمّل بروفايلك». */
object CharacterBuilderRoute : Route {
    override val name = "CharacterBuilder"
}

/**
 * البنّاء (`CharacterBuilder` @2df8134d · character-spec §3): المعاينة فوق (كبيرة + «في الرئيسية» + «في الأشخاص») · تلات أقسام واحد مفتوح ·
 * «احفظ» الزرار الأساسي الوحيد و«إلغاء». الاختيارات بتغيّر المعاينة بس؛ «احفظ» ⇒ `CharacterSetting.save` ⇒ «البيانات اتغيرت» ⇒ رجوع.
 * الحفظ ما نفعش ⇒ رسالة والشاشة مفتوحة. «إلغاء» والرجوع ⇒ من غير كتابة.
 */
@Composable
fun CharacterBuilderScreen() {
    val deps = LocalSpace.current
    val hook = deps.more.character
    val nav = LocalNavigator.current
    val changes = LocalDataChanges.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var s by remember(deps) { mutableStateOf<BuilderState?>(null) }
    var saving by remember { mutableStateOf(false) }
    // 2026-10-16: اللي البنّاء بدأ بيه (للمقارنة) · مسودة من مرة قبل كده · تأكيد الخروج
    var baseline by remember(deps) { mutableStateOf<CharacterLook?>(null) }
    var offer by remember(deps) { mutableStateOf<CharacterLook?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val drafts = hook?.draft
    LaunchedEffect(deps) {
        val saved = runCatching { hook?.current() }.getOrNull()
        val gender = runCatching { hook?.profileGender() }.getOrNull()
        val start = startBuilder(saved, gender)
        baseline = start.look
        offer = draftToOffer(decodeDraft(runCatching { drafts?.read() }.getOrNull()), start.look)
        if (offer == null) runCatching { drafts?.write(null) }
        s = start
    }
    // المسودة بعد كل تغيير (مش وعرض «كمّل من آخر تعديل» لسه مستني رد — عشان ما تتمسحش قبل ما المالك يختار)
    LaunchedEffect(s?.look, offer) {
        val look = s?.look ?: return@LaunchedEffect
        val b = baseline ?: return@LaunchedEffect
        if (offer == null) runCatching { drafts?.write(if (isDirty(look, b)) encodeDraft(look) else null) }
    }

    fun save() {
        val st = s ?: return
        if (hook == null) { toaster.show(t(UiKey.MORE_SAVE_FAILED), dark = true); return }
        saving = true
        scope.launch {
            val ok = runCatching { hook.save(st.look) }.isSuccess
            saving = false
            if (ok) { runCatching { drafts?.write(null) }; confirm = false; changes.changed(); nav.pop() } else toaster.show(t(UiKey.MORE_SAVE_FAILED), dark = true)
        }
    }

    // أي خروج (رجوع الجهاز · السهم · «إلغاء») بتعديلات ⇒ التأكيد جوه الصفحة
    fun requestExit() {
        val st = s
        val b = baseline
        val dirty = st != null && b != null && isDirty(st.look, b)
        if (exitStep(dirty) == ExitStep.LEAVE) { runCatching { drafts?.write(null) }; nav.pop() } else confirm = true
    }
    fun choose(choice: ExitChoice) {
        when (choice) {
            ExitChoice.SAVE -> save()
            ExitChoice.LEAVE -> { if (clearsDraft(choice)) runCatching { drafts?.write(null) }; confirm = false; nav.pop() }
            ExitChoice.STAY -> confirm = false
        }
    }
    val onBack by rememberUpdatedState { if (confirm) confirm = false else requestExit() }
    DisposableEffect(nav) {
        nav.holdBack(CharacterBuilderRoute) { onBack(); true }
        onDispose { nav.holdBack(CharacterBuilderRoute, null) }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().screenBackground()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = top + 20.dp, bottom = bottom + 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val st = s
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ScreenHeader(builderTitle(st?.built == true), onBack = { requestExit() })
                BasicText(t(UiKey.CHAR_SUB), Modifier.padding(horizontal = 4.dp), style = Type.of(12).copy(color = Ink.muted))
            }
            if (st == null) {
                Skeleton(Modifier.fillMaxWidth().height(216.dp), radius = 26.dp)
                Skeleton(Modifier.fillMaxWidth().height(300.dp), radius = 26.dp)
                return@Column
            }
            offer?.let { draft ->
                DraftOffer(
                    onResume = { s = st.copy(look = draft); offer = null },
                    onDiscard = { runCatching { drafts?.write(null) }; offer = null },
                )
            }
            PreviewRow(st) { s = it }
            for (sec in sectionsBefore(st)) SectionCard(sec, summary(st, sec)) { s = openSection(st, sec) }
            OpenSection(st) { s = it }
            for (sec in sectionsAfter(st)) SectionCard(sec, summary(st, sec)) { s = openSection(st, sec) }
        }
        Footer(bottom, saving, onSave = ::save, onCancel = { requestExit() }, Modifier.align(Alignment.BottomCenter))
        if (confirm) ExitConfirm(saving, ::choose)
    }
}

/** المعاينة الكبيرة (216 · زاوية 26) + عمود 112: دايرتك في الرئيسية · مكانك في الأشخاص. */
@Composable
internal fun PreviewRow(s: BuilderState, onChange: (BuilderState) -> Unit) {
    val layer = previewLayer(s)
    Row(Modifier.fillMaxWidth().height(216.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(Glass.lensOnLight)) {
            SwapIn(s.look, layer, Modifier.align(Alignment.BottomCenter)) {
                val box = if (layer == CharacterLayer.FULL) Modifier.size(width = (170 * 200 / 440).dp, height = 170.dp) else Modifier.size(196.dp)
                CharacterArt(s.look, layer, box, label = t(UiKey.CHAR_NAME))
            }
            ViewToggle(layer, { onChange(pickView(s, it)) }, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Column(Modifier.width(112.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xB3FFFFFF)).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                MeAvatar(46.dp, null, character = s.look)
                BasicText(t(UiKey.CHAR_ON_HOME), style = Type.of(11).copy(color = Ink.muted, textAlign = TextAlign.Center))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Glass.heroBase),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
            ) {
                // الجسم كله بطول 188 من فوق ومقصوص (الراس والكتاف ≈ 82) — زي كارت «الأشخاص». الكلمة بتتقاس الأول بمقاسها كامل
                // والرسمة تاخد الباقي (`weight`) — قبل كده الكلمة كانت بتتعصر لـ9 وبيبان منها نقط بس (r041b)
                CharacterFromTop(s.look, (188 * 200 / 440).dp, 188.dp, Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp))
                BasicText(t(UiKey.CHAR_IN_PEOPLE), Modifier.padding(bottom = 4.dp), style = Type.of(11).copy(color = Color(0xFFDCEBD6), textAlign = TextAlign.Center))
            }
        }
    }
}

/** كل تغيير في المعاينة: ظهور من 0.4 وتكبير من 0.97 في 450ms (ومن غير حركة لو «تقليل الحركة»). */
@Composable
private fun SwapIn(look: CharacterLook, layer: CharacterLayer, modifier: Modifier, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    val v = remember { Animatable(1f) }
    LaunchedEffect(look, layer) {
        if (!reduce) { v.snapTo(0f); v.animateTo(1f, tween(450)) }
    }
    Box(modifier.graphicsLayer { alpha = 0.4f + 0.6f * v.value; scaleX = 0.97f + 0.03f * v.value; scaleY = scaleX }) { content() }
}

/** تحت ثابت بتدرّج: «احفظ» (الأساسي الوحيد) و«إلغاء». */
@Composable
private fun Footer(navBottom: androidx.compose.ui.unit.Dp, saving: Boolean, onSave: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color(0x00EFF3ED), 0.3f to Color(0xF5EFF3ED), 1f to Color(0xF5EFF3ED)))
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 26.dp + navBottom),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrimaryButton(t(UiKey.CHAR_SAVE), onClick = onSave, enabled = !saving, loading = saving, height = 52.dp, modifier = Modifier.weight(1f))
        TonalButton(t(UiKey.CHAR_CANCEL), onClick = onCancel, height = 52.dp, modifier = Modifier.padding(horizontal = 0.dp))
    }
}

