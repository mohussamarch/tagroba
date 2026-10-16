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
import androidx.compose.foundation.layout.ime
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
import app.masroufy.ui.nav.HoldBack
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.imports.SmsHandoff
import app.masroufy.ui.screens.more.DayGrid
import app.masroufy.ui.screens.more.AccountCharacterRoute
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
    var s by rememberSaveable(stateSaver = OnbStateSaver) { mutableStateOf(OnbState(OnbStep.NAME)) }
    // اختار مصر ⇒ الكروت اللي بعدها بالمصري على طول (قبل ما أي نص تحت يتكتب — والحالة المحفوظة بترجّعها كمان)
    remember(s.answers.countryCode) { followChosenCountry(s.answers); s.answers.countryCode }
    var saving by remember { mutableStateOf<OnbExit?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // r041b P2: المسودة على الجهاز اتقرت؟ (قبلها ما بنكتبش — عشان الحالة الفاضية ما تمسحهاش)
    var draftRead by remember(deps) { mutableStateOf(false) }
    LaunchedEffect(deps, reload) {
        loadFailed = false
        if (!draftRead) {
            s = resumeFrom(s, decodeDraft(runCatching { onb.draft?.read() }.getOrNull()))
            draftRead = true
        }
        val r = runCatching { onb.onboard.start() }
        // حساب قديم فيه اسم ونوع ⇒ «بماذا نناديك؟» بيبدأ بيهم (قبل ما الكارت يترسم)
        r.getOrNull()?.profile?.let { p -> s = prefillName(s, p.displayName, p.gender) }
        start = r.getOrNull()
        // حساب جديد خالص ⇒ كارتين الفلوس (§81) بعملة وبنوك البلد المختارة (مصر كمان لو الفلوس تقدر تتكتب هناك)
        if (start?.freshAccount == true && !s.fresh) s = s.copy(fresh = true, home = deps.space.countryCode, otherCountryMoney = onb.countryMoney != null)
        loadFailed = r.isFailure
        open = runCatching { deps.shell.spaces().map { it.space } }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(deps.space)
    }
    // r041b P2: كل تغيير في الإجابات أو الكارت بيتكتب على الجهاز (الفتح الجاي بيكمّل من هنا)
    LaunchedEffect(s, draftRead) { if (draftRead) runCatching { onb.draft?.write(encodeDraft(s)) } }
    // r041b P2: رجوع الجهاز = الكارت اللي قبله (والحفظ شغال ⇒ ولا حاجة) · أول كارت ⇒ التطبيق يتقفل — عمره ما يفتح رئيسية نص مبنية
    HoldBack(OnboardingRoute) {
        if (saving != null) true else systemBack(s)?.let { s = it; true } ?: false
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
                    // r041b P2: الرحلة خلصت ⇒ المسودة تتمسح (والشاشة بتتشال قبل ما تكتبها تاني)
                    draftRead = false
                    runCatching { onb.draft?.write(null) }
                    nav.pop()
                    // «رسائل البنك» ⇒ الرئيسية (أو «حسابك») والشرح ثم إذن النظام فوقها (`SmsHandoff`) — مش شاشة «رسائل البنك» من غير إذن
                    if (s.answers.source == OnbSource.SMS && bankSmsReadable()) SmsHandoff.start()
                    // حساب جديد خالص من غير كارتين الفلوس (بلد تانية من غير طريق للفلوس — المعاينة) ⇒ «المحافظ» عشان يضيف محافظه هناك
                    if (st.freshAccount && !money(s)) nav.push(WalletsRoute)
                    // «كمّل بروفايلك» ⇒ «ملفك» و«ابنِ شخصيتك» فوقها. r041 A6: لو تسليم رسايل البنك شغال، الشرح والإذن وسؤال اللي فات
                    // الأول — المضيف (`SmsHandoffHost`) بيفتح «ملفك» بعدهم، وهي بتفتح البناء
                    if (exit == OnbExit.PROFILE) SmsHandoff.openAfter(AccountCharacterRoute) { nav.push(it) }
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
    // r041 A9: الكيبورد اتقفل ⇒ الصفحة ترجع لأولها (قبل كده كانت بتفضل متمررة بعد «أضف البنك»)
    val scroll = rememberScrollState()
    val imeOpen = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    var imeWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(imeOpen) {
        if (scrollBackOnImeClose(imeWasOpen, imeOpen)) scroll.animateScrollTo(0)
        imeWasOpen = imeOpen
    }
    Box(Modifier.fillMaxSize().screenBackground()) {
        // الكيبورد بيصغّر مساحة التمرير نفسها (`imePadding`) ⇒ «أضف البنك» يقدر يطلع فوقه (`BankCard` بيجيبه قدام العين)
        Column(Modifier.fillMaxSize().imePadding()) {
            // r041 A9: الشريط (رجوع · التقدّم · «تخطَّ») **ثابت برّه التمرير** وتحت شريط الحالة — عمره ما يستخبى تحته
            Box(Modifier.fillMaxWidth().padding(start = Gaps.gutter, end = Gaps.gutter, top = pad.calculateTopPadding() + 20.dp, bottom = 14.dp)) {
                OnbTopBar(s, onBack = { s = back(s) }, onSkip = { s = skip(s) })
            }
            OnbScrollBody(scroll, bottom = navBottom + if (s.reply != null) 230.dp else 48.dp) {
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

/** r041 A9 — الجزء اللي بيتمرر تحت الشريط الثابت (الرسمة والكارت). */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.OnbScrollBody(
    scroll: androidx.compose.foundation.ScrollState,
    bottom: androidx.compose.ui.unit.Dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(start = Gaps.gutter, end = Gaps.gutter, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** r041 A9 — الكيبورد كان مفتوح واتقفل ⇒ ارجع لأول الصفحة. */
internal fun scrollBackOnImeClose(wasOpen: Boolean, open: Boolean): Boolean = wasOpen && !open

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
        OnbStep.NAME -> NameCard(s, onChange)
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
