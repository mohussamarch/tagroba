package app.masroufy.ui.screens.dues

import app.masroufy.core.UiKey
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.IsoDate
import app.masroufy.core.RoscaFrequency
import app.masroufy.core.RoscaQuestion
import app.masroufy.core.RoscaShare
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.RoscaSetupState

/** نص السؤال وسطره (مفاتيح `ROSCA_Q_*` نفسها اللي `RoscaSetup` بيستعملها). قيمة الدور: التلميح المحسوب من حالة الاستخدام لو هو السؤال الجاي. */
private fun promptOf(q: RoscaQuestion, s: RoscaSetupState): Pair<String, String?> = when (q) {
    RoscaQuestion.NAME -> t(TextKey.ROSCA_Q_NAME) to t(TextKey.ROSCA_Q_NAME_HINT)
    RoscaQuestion.TURNS_COUNT -> t(TextKey.ROSCA_Q_TURNS_COUNT) to t(TextKey.ROSCA_Q_TURNS_COUNT_HINT)
    RoscaQuestion.SHARE_AMOUNT -> t(TextKey.ROSCA_Q_SHARE_AMOUNT) to t(TextKey.ROSCA_Q_SHARE_AMOUNT_HINT)
    RoscaQuestion.FREQUENCY -> t(TextKey.ROSCA_Q_FREQUENCY) to null
    RoscaQuestion.FIRST_DATE -> t(TextKey.ROSCA_Q_FIRST_DATE) to t(UiKey.RW_FIRST_HINT)
    RoscaQuestion.SHARE -> t(TextKey.ROSCA_Q_SHARE) to null
    RoscaQuestion.MY_TURN -> t(TextKey.ROSCA_Q_MY_TURN) to ((s.draft.share ?: RoscaShare.ONE).turns.let { n -> if (n > 1) t(TextKey.ROSCA_TURNS_FOR_SHARE, sentenceNumber(n)) else t(UiKey.RW_TURN_HINT) })
    RoscaQuestion.PAYOUT -> t(TextKey.ROSCA_Q_PAYOUT) to (s.prompt?.takeIf { it.question == RoscaQuestion.PAYOUT }?.hint ?: t(UiKey.RW_PAYOUT_HINT))
}

@Composable
internal fun QuestionCard(q: RoscaQuestion, s: RoscaSetupState, i: WizardInputs, today: IsoDate, error: String?, onInputs: (WizardInputs) -> Unit) {
    val (text, hint) = promptOf(q, s)
    val currency = s.draft.currency
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(text, style = Type.of(20, FontWeight.Bold))
                if (hint != null) BasicText(hint, style = Type.of(13).copy(color = Ink.muted))
            }
            when (q) {
                RoscaQuestion.NAME -> TextInput(i.name, { onInputs(i.copy(name = it)) }, placeholder = t(TextKey.ROSCA_Q_NAME_HINT), height = 52.dp)
                RoscaQuestion.TURNS_COUNT -> Stepper(i.count, { onInputs(i.copy(count = stepCount(i.count, it))) })
                RoscaQuestion.SHARE_AMOUNT -> MoneyField(i.amount, { onInputs(i.copy(amount = it)) }, null, currency, big = true)
                RoscaQuestion.PAYOUT -> MoneyField(i.payout, { onInputs(i.copy(payout = it)) }, null, currency, big = true)
                RoscaQuestion.FREQUENCY -> for (f in RoscaFrequency.entries) {
                    RadioCard(t(f.labelKey), if (f == RoscaFrequency.MONTHLY) t(UiKey.RW_FREQ_COMMON) else null, i.frequency == f) { onInputs(i.copy(frequency = f)) }
                }
                RoscaQuestion.SHARE -> for (sh in RoscaShare.entries) RadioCard(t(sh.labelKey), null, i.share == sh) { onInputs(i.copy(share = sh)) }
                RoscaQuestion.FIRST_DATE -> DayPicker(i.first, today, { onInputs(i.copy(first = it)) })
                RoscaQuestion.MY_TURN -> TurnPicker(s, i, today, onInputs)
            }
            error?.let { FieldError(it) }
        }
    }
}

@Composable
private fun TurnPicker(s: RoscaSetupState, i: WizardInputs, today: IsoDate, onInputs: (WizardInputs) -> Unit) {
    val count = s.draft.cycleCount ?: 0
    val needed = (s.draft.share ?: RoscaShare.ONE).turns
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in (1..count).chunked(5)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (turn in row) {
                val on = !i.unknownTurn && turn in i.turns
                val whenText = turnWhen(s.draft, turn, today)
                TurnChip(Modifier.weight(1f), sentenceNumber(turn), whenText, on) { onInputs(i.copy(turns = toggleTurn(i.turns, turn, needed), unknownTurn = false)) }
            }
            repeat(5 - row.size) { Box(Modifier.weight(1f)) }
        }
        SelectChip(t(TextKey.ROSCA_TURN_UNKNOWN_CHOICE), i.unknownTurn, { onInputs(i.copy(unknownTurn = !i.unknownTurn)) }, Modifier.fillMaxWidth(), height = 52.dp)
    }
}

@Composable
private fun TurnChip(modifier: Modifier, num: String, whenText: String?, on: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    val surface = if (on) Modifier.background(Brush.linearGradient(listOf(Ink.heroStart, Ink.heroEnd)))
    else Modifier.background(Color.White).insetRing(shape, 1.dp, Ink.fieldEdge)
    Column(
        modifier.height(56.dp).pressScale(press).clip(shape).then(surface).tap(press, onClick = onClick)
            .semantics { selected = on; contentDescription = listOfNotNull(num, whenText).joinToString(t(UiKey.DUES_COMMA)) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(num, style = Type.of(15, FontWeight.Bold).copy(color = if (on) Color.White else Ink.text))
        if (whenText != null) BasicText(whenText, style = Type.of(10).copy(color = if (on) Ink.onHeroMuted else Ink.muted), maxLines = 1)
    }
}

/** اختيار بدايرة (الدورية · الأسهم): كارت 60 فيه الاسم وسطر اختياري والدايرة على الشمال. */
@Composable
private fun RadioCard(label: String, sub: String?, on: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).pressScale(press).clip(shape).background(if (on) Ink.selected else Color.White)
            .insetRing(shape, if (on) 1.5.dp else 1.dp, if (on) Ink.primary else Ink.fieldEdge).tap(press, onClick = onClick)
            .semantics { selected = on }.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = Type.of(15, FontWeight.Bold))
            if (sub != null) BasicText(sub, style = Type.caption().copy(color = Ink.muted))
        }
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(if (on) Color.White else Color.Transparent)
                .insetRing(RoundedCornerShape(11.dp), if (on) 7.dp else 1.5.dp, if (on) Ink.primary else Ink.faded),
        )
    }
}

/** عدّاد «كم دورًا؟»: + الرقم − (من 2 لـ60). */
@Composable
private fun Stepper(count: Int, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton(Lucide.PLUS, t(UiKey.RW_COUNT_UP)) { onStep(1) }
        BasicText(sentenceNumber(count), Modifier.weight(1f), style = Type.of(32, FontWeight.Bold).copy(textAlign = TextAlign.Center))
        StepButton(DuesIcons.MINUS, t(UiKey.RW_COUNT_DOWN)) { onStep(-1) }
    }
}


@Composable
private fun StepButton(icon: Lucide, label: String, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(52.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(Color(0x1408634F))
            .tap(press, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 22.dp, tint = Ink.primary) }
}

/** «التحليل قبل الحفظ»: البطاقة البترولية (هتقبض إمتى) + الأرقام + إجاباتك (الضغط على أي واحدة يرجّعك لسؤالها). */
@Composable
internal fun SummaryView(s: WizardSummaryUi, currency: Currency, onEdit: (RoscaQuestion) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(s.heroLabel, style = Type.body().copy(color = Ink.onHeroMuted))
                BasicText(s.heroValue ?: t(TextKey.NOT_AVAILABLE), style = Type.of(22, FontWeight.Bold).copy(color = Color.White))
                BasicText(s.heroSub, style = Type.caption().copy(color = Ink.onHeroMuted))
            }
        }
        CardList {
            s.facts.forEachIndexed { idx, f ->
                if (idx > 0) Divider()
                LabelValue(f.label) { StatValueView(f.value, currency) }
            }
        }
        BasicText(t(UiKey.RW_ANSWERS), style = Type.section())
        CardList {
            s.answers.forEachIndexed { idx, a ->
                if (idx > 0) Divider()
                AnswerRow(a) { onEdit(a.question) }
            }
        }
    }
}

@Composable
private fun AnswerRow(a: WizardAnswer, onClick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale(press).tap(press, label = t(UiKey.RW_EDIT, a.label), onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(a.label, Modifier.weight(1f), style = Type.of(14).copy(color = Ink.muted))
        BasicText(a.value, style = Type.bodyBold())
        LucideIcon(Lucide.PENCIL, size = 16.dp, tint = Ink.primary)
    }
}
