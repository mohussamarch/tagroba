package app.masroufy.ui.screens.onboarding

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
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
import app.masroufy.core.countryPack
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.screenBackground
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.imports.SmsHandoff
import app.masroufy.ui.screens.more.DayGrid
import app.masroufy.ui.screens.more.AccountLookRoute
import app.masroufy.ui.screens.more.WalletsRoute
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Space as Gaps
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.OnboardingResult
import app.masroufy.usecase.OnboardingStart
import kotlinx.coroutines.launch

/**
 * «أول تشغيل» بعد الدخول (`Onboarding`): كارت = سؤال، والرد الأخضر تحت بعد كل إجابة. الحالات: بيحمّل (القيم الموجودة
 * من `OnboardAccount.start`) · خطأ (القراية أو الحفظ ⇒ «أعد المحاولة») · غير متاح (رسايل البنك على الآيفون · مصر لو إنشاء البلد مش متاح ·
 * الإذن). «محتاج نت» بتاع كارت الدخول في `SignInFlow` (الكتابة هنا بتستنى في نسخة الجهاز لو النت قاطع).
 * «افتح الرئيسية» أو «كمّل بروفايلك» ⇒ `OnboardAccount.finish` (يوم الراتب + للحساب الجديد الكاش والبنك من كارتين §81 — من غير ديون) ⇒ البلد المختارة ⇒
 * لو اختار رسايل البنك: شرح الإذن ثم نافذة النظام فوق الرئيسية (`SmsHandoff`).
 */
@Composable
fun OnboardingScreen() {
    val deps = LocalSpace.current
    val onb = deps.onboarding
    val nav = LocalNavigator.current
    val changes = LocalDataChanges.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var start by remember(deps) { mutableStateOf<OnboardingStart?>(null) }
    var loadFailed by remember(deps) { mutableStateOf(false) }
    var open by remember(deps) { mutableStateOf(listOf(deps.space)) }
    var s by rememberSaveable(stateSaver = OnbStateSaver) { mutableStateOf(OnbState(OnbStep.COUNTRY)) }
    // اختار مصر ⇒ الكروت اللي بعدها بالمصري على طول (قبل ما أي نص تحت يتكتب — والحالة المحفوظة بترجّعها كمان)
    remember(s.answers.countryCode) { followChosenCountry(s.answers); s.answers.countryCode }
    var saving by remember { mutableStateOf<OnbExit?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deps, reload) {
        loadFailed = false
        val r = runCatching { onb.onboard.start() }
        start = r.getOrNull()
        // حساب جديد خالص ⇒ كارتين الفلوس (§81) بعملة وبنوك البلد المختارة (مصر كمان لو الفلوس تقدر تتكتب هناك)
        if (start?.freshAccount == true && !s.fresh) s = s.copy(fresh = true, home = deps.space.countryCode, otherCountryMoney = onb.countryMoney != null)
        loadFailed = r.isFailure
        open = runCatching { deps.shell.spaces().map { it.space } }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(deps.space)
    }

    /** «افتح الرئيسية» / «كمّل بروفايلك» — نفس الانتهاء بالظبط، والفرق بس الشاشة اللي بعده. */
    fun finish(exit: OnbExit) {
        val st = start ?: return
        saving = exit
        failure = null
        scope.launch {
            // اختار مصر ⇒ الكاش والبنك بيتكتبوا في محافظها بعد ما تتعمل (مش في البلد الشغالة)
            val elsewhere = moneyElsewhere(s)
            when (val r = runCatching { onb.onboard.finish(finishInput(st.profile, s.answers, money(s) && !elsewhere)) }.getOrNull()) {
                OnboardingResult.Ok -> {
                    val made = countrySpaceFor(s.answers, open, onb.spacesAdmin)
                    // مصر ما اتعملتش (غالبًا النت) ⇒ نفضل على «جاهز» برسالة و«ادخل التطبيق» تاني بيكمّل (الإجابات اتحفظت والإنشاء ما بيتكررش)
                    if (made.isFailure) {
                        failure = t(UiKey.ONB_FINISH_FAILED)
                        saving = null
                        return@launch
                    }
                    val target = spaceToOpen(s.answers, open, deps.space.id) ?: made.getOrNull()
                    val put = if (elsewhere) moneyInto(target, st.profile, s.answers, onb.countryMoney) else OnboardingResult.Ok
                    if (put is OnboardingResult.Failed) {
                        failure = put.message
                        saving = null
                        return@launch
                    }
                    if (target != null) runCatching { deps.shell.switchSpace(target) }
                    nav.pop()
                    // «رسائل البنك» ⇒ الرئيسية (أو «حسابك») والشرح ثم إذن النظام فوقها (`SmsHandoff`) — مش شاشة «رسائل البنك» من غير إذن
                    if (s.answers.source == OnbSource.SMS && bankSmsReadable()) SmsHandoff.start()
                    // حساب جديد خالص من غير كارتين الفلوس (بلد تانية من غير طريق للفلوس — المعاينة) ⇒ «المحافظ» عشان يضيف محافظه هناك
                    if (st.freshAccount && !money(s)) nav.push(WalletsRoute)
                    // «كمّل بروفايلك» ⇒ «حسابك» ولوحة الشكل مفتوحة (الرجوع منها ⇒ الرئيسية). r041 A6: لو تسليم رسايل البنك شغال، الشرح
                    // والإذن وسؤال اللي فات الأول — المضيف (`SmsHandoffHost`) بيفتحها بعدهم
                    if (exit == OnbExit.PROFILE) SmsHandoff.openAfter(AccountLookRoute) { nav.push(it) }
                    changes.changed()
                }
                is OnboardingResult.Failed -> failure = r.message
                null -> failure = t(UiKey.ONB_FINISH_FAILED)
            }
            saving = null
        }
    }

    val pad = WindowInsets.safeDrawing.asPaddingValues()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().screenBackground()) {
        // الكيبورد بيصغّر مساحة التمرير نفسها (`imePadding`) ⇒ «أضف البنك» يقدر يطلع فوقه (`BankCard` بيجيبه قدام العين)
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(
                start = Gaps.gutter, end = Gaps.gutter, top = pad.calculateTopPadding() + 20.dp,
                bottom = navBottom + if (s.reply != null) 230.dp else 48.dp,
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
            val currency = countryPack(moneyCountry(s, deps.space.countryCode)).currency
            val (title, line) = replyText(reply, s.answers, start?.profile?.payday ?: app.masroufy.core.DEFAULT_PAYDAY, currency)
            Box(Modifier.align(Alignment.BottomCenter)) {
                OnbReplyPanel(title, line, nextLabel(s), pad.calculateBottomPadding()) { s = next(s) }
            }
        }
    }
}

@Composable
private fun StepHead(step: OnbStep) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(t(stepTitle(step)), Modifier.semantics { heading() }, style = Type.of(22, FontWeight.Bold).copy(textAlign = TextAlign.Center))
        BasicText(stepSub(step), style = Type.of(14).copy(color = Ink.muted, textAlign = TextAlign.Center))
    }
}

@Composable
private fun StepBody(s: OnbState, open: List<Space>, onChange: (OnbState) -> Unit, saving: OnbExit?, failure: String?, onFinish: (OnbExit) -> Unit) {
    val onb = LocalSpace.current.onboarding
    val scope = rememberCoroutineScope()
    when (s.step) {
        OnbStep.COUNTRY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in countryOptions(open, onb.spacesAdmin != null)) {
                OnbOptionRow(o, s.answers.countryCode == o.id, null) { onChange(pickCountry(s, o.id)) }
            }
        }
        // عملة وبنوك البلد المختارة (مصر ⇒ جنيه وبنوك مصر) — الكتابة بعدين في محافظ البلد دي
        OnbStep.CASH -> CashCard(s, countryPack(moneyCountry(s, LocalSpace.current.space.countryCode)).currency, onChange)
        OnbStep.BANK -> moneyCountry(s, LocalSpace.current.space.countryCode).let { BankCard(s, it, countryPack(it).currency, onChange) }
        OnbStep.PAYDAY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayGrid(s.answers.day, onPick = { onChange(pickDay(s, it)) })
            TonalButton(t(UiKey.ONB_NOT_FIXED), onClick = { onChange(skip(s)) }, height = 44.dp, modifier = Modifier.fillMaxWidth())
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
            // زرارين (قرار المالك 2026-10-15 مساءً): الاتنين بيخلّصوا الرحلة زي بعض — «كمّل بروفايلك» بيفتح «حسابك» بعدها
            PrimaryButton(
                t(if (saving == OnbExit.HOME) UiKey.ONB_SAVING else UiKey.ONB_OPEN_HOME), onClick = { onFinish(OnbExit.HOME) },
                enabled = saving == null, loading = saving == OnbExit.HOME, height = 52.dp, modifier = Modifier.fillMaxWidth(),
            )
            TonalButton(
                t(if (saving == OnbExit.PROFILE) UiKey.ONB_SAVING else UiKey.ONB_COMPLETE_PROFILE), onClick = { onFinish(OnbExit.PROFILE) },
                enabled = saving == null, height = 48.dp, modifier = Modifier.fillMaxWidth(),
            )
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
        FieldError(t(UiKey.SHELL_LOAD_FAILED))
        TonalButton(t(UiKey.SHELL_RETRY), onClick = onRetry, height = 44.dp, modifier = Modifier.fillMaxWidth())
    }
}

/** الكارت اللي كان عليه يفضل لو الشاشة اتبنت من جديد (لفّ الجوال · رجوع من الخلفية) — «لو قفل في النص بيرجع لنفس الكارت». */
private val OnbStateSaver = listSaver<OnbState, Any?>(
    save = { st ->
        val a = st.answers
        listOf(st.step.name, null, a.countryCode, a.day, a.dayLater, a.source?.name, st.reply?.name, st.fresh, st.home, a.cashMinor,
            a.bank?.bankKey, a.bank?.last4, a.bank?.openingMinor, st.otherCountryMoney)
    },
    restore = { v ->
        OnbState(
            restoreStep(v[0]),
            OnbAnswers(
                v[2] as String?, v[3] as Int?, v[4] as Boolean, restoreSource(v[5]), v[9] as Long?,
                (v[10] as String?)?.let { OnbBank(it, v[11] as String?, v[12] as Long?) },
            ),
            restoreReply(v[6]),
            fresh = v[7] as Boolean,
            home = v[8] as String?,
            otherCountryMoney = v.getOrNull(13) as Boolean? ?: false,
        )
    },
)
