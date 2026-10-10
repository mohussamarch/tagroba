package app.masroufy.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.screenBackground
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.imports.BankSmsRoute
import app.masroufy.ui.screens.more.DayGrid
import app.masroufy.ui.screens.more.LookGrid
import app.masroufy.ui.screens.more.NotYetLine
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Space as Gaps
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.OnboardingResult
import app.masroufy.usecase.OnboardingStart
import kotlinx.coroutines.launch

/**
 * «أول تشغيل» بعد الدخول (`Onboarding` الخطوات ١ و٣–٦): كارت = سؤال، والرد الأخضر تحت بعد كل إجابة. الحالات: بيحمّل (القيم الموجودة
 * من `OnboardAccount.start`) · خطأ (القراية أو الحفظ ⇒ «أعد المحاولة») · غير متاح (رسايل البنك على الآيفون · مصر لو إنشاء البلد مش متاح ·
 * حفظ الشكل). «محتاج نت» بتاع كارت الدخول في `SignInFlow` (الكتابة هنا بتستنى في نسخة الجهاز لو النت قاطع).
 * «ادخل التطبيق» ⇒ `OnboardAccount.finish` (يوم الراتب بس — مفيش كاش ولا ديون في رحلة §63) ⇒ البلد المختارة ⇒ رسايل البنك لو اختارها.
 */
@Composable
fun OnboardingScreen() {
    val deps = LocalSpace.current
    val onb = deps.onboarding
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var start by remember(deps) { mutableStateOf<OnboardingStart?>(null) }
    var loadFailed by remember(deps) { mutableStateOf(false) }
    var open by remember(deps) { mutableStateOf(listOf(deps.space)) }
    var s by rememberSaveable(stateSaver = OnbStateSaver) { mutableStateOf(OnbState(OnbStep.LOOK)) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deps, reload) {
        loadFailed = false
        val r = runCatching { onb.onboard.start() }
        start = r.getOrNull()
        loadFailed = r.isFailure
        open = runCatching { deps.shell.spaces().map { it.space } }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(deps.space)
    }

    fun finish() {
        val st = start ?: return
        saving = true
        failure = null
        scope.launch {
            when (val r = runCatching { onb.onboard.finish(finishInput(st.profile, s.answers)) }.getOrNull()) {
                OnboardingResult.Ok -> {
                    val target = spaceToOpen(s.answers, open, deps.space.id) ?: createdSpace(s.answers, open, onb)
                    if (target != null) runCatching { deps.shell.switchSpace(target) }
                    nav.pop()
                    if (s.answers.source == OnbSource.SMS) nav.push(BankSmsRoute)
                }
                is OnboardingResult.Failed -> failure = r.message
                null -> failure = t(TextKey.ONB_FINISH_FAILED)
            }
            saving = false
        }
    }

    val pad = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().screenBackground()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                start = Gaps.gutter, end = Gaps.gutter, top = pad.calculateTopPadding() + 20.dp,
                bottom = pad.calculateBottomPadding() + if (s.reply != null) 230.dp else 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnbTopBar(s, onBack = { s = back(s) }, onSkip = { s = skip(s) })
            if (s.step == OnbStep.READY) OnbRing() else OnbIllustration(s.step)
            FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StepHead(s.step)
                    when {
                        loadFailed -> LoadFailed { reload++ }
                        start == null -> Loading()
                        else -> StepBody(s, open, onChange = { s = it }, saving = saving, failure = failure, onFinish = ::finish)
                    }
                }
            }
        }
        val reply = s.reply
        if (reply != null) {
            val (title, line) = replyText(reply, s.answers, start?.profile?.payday ?: app.masroufy.core.DEFAULT_PAYDAY)
            Box(Modifier.align(Alignment.BottomCenter)) {
                OnbReplyPanel(title, line, nextLabel(s), pad.calculateBottomPadding()) { s = next(s) }
            }
        }
    }
}

/** مصر مش مفتوحة في الحساب ⇒ إنشاؤها من نقطة الربط لو موجودة (وإلا الخيار كان مقفول من الأول). */
private suspend fun createdSpace(a: OnbAnswers, open: List<Space>, onb: OnboardingDeps): String? {
    val code = a.countryCode ?: return null
    if (open.any { it.countryCode.equals(code, true) }) return null
    return runCatching { onb.spacesAdmin?.create(code)?.id }.getOrNull()
}

@Composable
private fun StepHead(step: OnbStep) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(t(stepTitle(step)), Modifier.semantics { heading() }, style = Type.of(22, FontWeight.Bold).copy(textAlign = TextAlign.Center))
        BasicText(stepSub(step), style = Type.of(14).copy(color = Ink.muted, textAlign = TextAlign.Center))
    }
}

@Composable
private fun StepBody(s: OnbState, open: List<Space>, onChange: (OnbState) -> Unit, saving: Boolean, failure: String?, onFinish: () -> Unit) {
    val onb = LocalSpace.current.onboarding
    val scope = rememberCoroutineScope()
    when (s.step) {
        OnbStep.LOOK -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val hook = onb.look
            // من غير مكان يتحفظ فيه الشكل ⇒ الاختيار ما بيتعملش «حُفظ» مزيّف — «تخطَّ» فوق بيكمّل
            LookGrid(s.answers.look) { k -> if (hook != null) scope.launch { if (runCatching { hook.choose(k) }.isSuccess) onChange(pickLook(s, k)) } }
            BasicText(t(TextKey.LOOK_NOTE), Modifier.fillMaxWidth(), style = Type.of(12).copy(color = Ink.muted, textAlign = TextAlign.Center))
            if (hook == null) NotYetLine(t(TextKey.LOOK_NOT_YET))
        }
        OnbStep.COUNTRY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in countryOptions(open, onb.spacesAdmin != null)) {
                OnbOptionRow(o, s.answers.countryCode == o.id, null) { onChange(pickCountry(s, o.id)) }
            }
        }
        OnbStep.PAYDAY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayGrid(s.answers.day, onPick = { onChange(pickDay(s, it)) })
            TonalButton(t(TextKey.ONB_NOT_FIXED), onClick = { onChange(skip(s)) }, height = 44.dp, modifier = Modifier.fillMaxWidth())
        }
        OnbStep.SOURCE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in sourceOptions(bankSmsReadable())) {
                val source = OnbSource.valueOf(o.id)
                val icon = when (source) {
                    OnbSource.SMS -> Lucide.MESSAGE_SQUARE_TEXT
                    OnbSource.FILE -> Lucide.FILE_TEXT
                    OnbSource.LATER -> Lucide.CLOCK
                }
                OnbOptionRow(o, s.answers.source == source, icon) { onChange(pickSource(s, source)) }
            }
        }
        OnbStep.READY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(t(if (saving) TextKey.ONB_SAVING else TextKey.ONB_ENTER), onClick = onFinish, loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth())
            if (failure != null) FieldError(failure)
        }
    }
}

@Composable
private fun Loading() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Skeleton(Modifier.fillMaxWidth().height(60.dp), radius = 18.dp)
        Skeleton(Modifier.fillMaxWidth().height(60.dp), radius = 18.dp)
    }
}

@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FieldError(t(TextKey.SHELL_LOAD_FAILED))
        TonalButton(t(TextKey.SHELL_RETRY), onClick = onRetry, height = 44.dp, modifier = Modifier.fillMaxWidth())
    }
}

/** الكارت اللي كان عليه يفضل لو الشاشة اتبنت من جديد (لفّ الجوال · رجوع من الخلفية) — «لو قفل في النص بيرجع لنفس الكارت». */
private val OnbStateSaver = listSaver<OnbState, Any?>(
    save = { st ->
        val a = st.answers
        listOf(st.step.ordinal, a.look, a.countryCode, a.day, a.dayLater, a.source?.ordinal, st.reply?.ordinal)
    },
    restore = { v ->
        OnbState(
            OnbStep.entries[v[0] as Int],
            OnbAnswers(v[1] as Int?, v[2] as String?, v[3] as Int?, v[4] as Boolean, (v[5] as Int?)?.let { OnbSource.entries[it] }),
            (v[6] as Int?)?.let { OnbReply.entries[it] },
        )
    },
)
