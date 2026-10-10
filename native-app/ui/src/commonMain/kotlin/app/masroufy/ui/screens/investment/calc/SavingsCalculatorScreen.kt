package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.SavingsCalcError
import app.masroufy.core.TextKey
import app.masroufy.core.addMonthsClamped
import app.masroufy.core.currencySymbol
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.operations.ReviewQueueRoute
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «حاسبة الادخار» (لوحة `SavingsCalculator`): الاتجاهين (أصل إلى مبلغ بتاريخ ⇐⇒ أدّخر مبلغًا كل شهر لمدة) · المقارنة بما تدّخره فعلًا
 * (آخر 3 أشهر مالية) · «لو وضعتها في…» (`SavingsGrowth`) · «حوّلها لخطة ادخار». كل رقم من `SavingsCalculator`.
 */
@Composable
fun SavingsCalculatorScreen() {
    val space = LocalSpace.current
    val deps = space.investment.calculators
    val currency = space.space.currency
    val toaster = LocalToaster.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val today = remember(deps) { deps.today() }
    var modeName by rememberSaveable { mutableStateOf(SavingsMode.TARGET.name) }
    val mode = SavingsMode.valueOf(modeName)
    val fields = rememberFields()
    var goalDone by rememberSaveable { mutableStateOf(false) }
    val check = checkSavings(mode, fields.snapshot(), currency, today)
    var outcome by remember(deps) { mutableStateOf<SavingsOutcome?>(null) }
    var calcError by remember(deps) { mutableStateOf<String?>(null) }
    // الحسبة نفسها وقعت (مثلًا قراية العمليات للمقارنة) ⇒ «غير متاح» بسببه، مش صفر ولا وقوع
    var failed by remember(deps) { mutableStateOf(false) }
    LaunchedEffect(deps, check.request) {
        val req = check.request
        failed = false
        if (req == null) {
            outcome = null
            calcError = null
            return@LaunchedEffect
        }
        delay(250)
        try {
            outcome = when (req) {
                is SavingsRequest.Target -> SavingsOutcome.Target(deps.savings.perMonth(req.targetMinor, req.haveMinor, req.date, currency, today))
                is SavingsRequest.Reach -> SavingsOutcome.Reach(deps.savings.reach(req.monthlyMinor, req.months, req.haveMinor, currency, today))
            }
            calcError = null
        } catch (e: SavingsCalcError) {
            outcome = null
            calcError = e.message
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            outcome = null
            failed = true
        }
    }
    val errors = check.errors + (calcError?.let { mapOf((if (mode == SavingsMode.TARGET) SavingsFields.DATE else SavingsFields.MONTHS) to it) } ?: emptyMap())
    val current = outcome?.takeIf { check.request != null && calcError == null && it.matches(mode) }
    val ui = current?.let { savingsResultUi(it, currency) }
    val put = { id: String, v: String -> fields[id] = v; goalDone = false }

    InnerScaffold(t(UiKey.SAVCALC_TITLE)) {
        item(key = "modes") { ModeSwitch(mode) { modeName = it.name; goalDone = false } }
        item(key = "fields") { SavingsFieldsCard(mode, fields, errors, currency, today, put) }
        item(key = "hero") {
            when {
                failed && check.request != null -> CalcHero(heroLabel(mode), null, currency, t(TextKey.NOT_AVAILABLE), t(UiKey.SHELL_LOAD_FAILED))
                check.request != null && ui == null && calcError == null -> Skeleton(Modifier.fillMaxWidth().height(132.dp), radius = 28.dp)
                else -> CalcHero(heroLabel(mode), ui?.heroAmountMinor, currency, t(UiKey.SAVCALC_FILL), ui?.heroSub ?: t(UiKey.SAVCALC_FILL_SUB))
            }
        }
        if (ui != null) {
            item(key = "compare") { CompareCard(ui) { nav.push(ReviewQueueRoute) } }
            if (ui.growthMonthlyMinor > 0) item(key = "growth") { SavingsGrowthPanel(ui.growthMonthlyMinor, ui.growthMonths, ui.growthStartMinor, today) }
            item(key = "goal") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (goalDone) TonalButton(t(UiKey.SAVCALC_GOAL_DONE), {}, Modifier.fillMaxWidth(), enabled = false, height = 52.dp)
                    else PrimaryButton(t(UiKey.SAVCALC_GOAL), {
                        val o = current
                        scope.launch {
                            try {
                                val goal = when (o) {
                                    is SavingsOutcome.Target -> deps.savings.turnIntoGoal(o.value.plan, currency)
                                    is SavingsOutcome.Reach -> deps.savings.turnIntoGoal(o.value.reach, currency, today)
                                    null -> return@launch
                                }
                                goalDone = true
                                toaster.show(goalToast(goal.name, ui, currency))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                toaster.show(e.message ?: t(UiKey.SAVCALC_GOAL_FAILED), dark = true)
                            }
                        }
                    }, Modifier.fillMaxWidth(), height = 52.dp)
                    BasicText(t(UiKey.SAVCALC_GOAL_NOTE), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center))
                }
            }
        }
    }
}

private fun SavingsOutcome.matches(mode: SavingsMode): Boolean = (this is SavingsOutcome.Target) == (mode == SavingsMode.TARGET)

/** الاتجاهين (النموذج: شريط رمادي فاتح والمختار أبيض بظل، والكلام على سطرين لو لزم). */
@Composable
private fun ModeSwitch(mode: SavingsMode, onPick: (SavingsMode) -> Unit) {
    val outer = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth().clip(outer).background(Color(0x0F193D33)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((m, key) in listOf(SavingsMode.TARGET to UiKey.SAVCALC_MODE_TARGET, SavingsMode.MONTHLY to UiKey.SAVCALC_MODE_MONTHLY)) {
            val on = m == mode
            val press = rememberPress()
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp).pressScale(press).clip(shape).then(if (on) Modifier.background(Color.White) else Modifier)
                    .tap(press, role = Role.RadioButton) { onPick(m) }.semantics { selected = on }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(t(key), style = Type.of(13, if (on) FontWeight.Bold else FontWeight.Normal, 1.35).copy(color = if (on) Ink.primary else Ink.muted, textAlign = TextAlign.Center))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavingsFieldsCard(mode: SavingsMode, f: FieldsState, errors: Map<String, String>, currency: app.masroufy.core.Currency, today: String, put: (String, String) -> Unit) {
    val unit = currencySymbol(currency)
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mode == SavingsMode.TARGET) {
                CalcField(t(UiKey.SAVCALC_TARGET_LABEL), f[SavingsFields.TARGET], { put(SavingsFields.TARGET, it) }, unit = unit, error = errors[SavingsFields.TARGET])
                CalcDateField(t(UiKey.SAVCALC_DATE_LABEL), f[SavingsFields.DATE], { put(SavingsFields.DATE, it) }, today, error = errors[SavingsFields.DATE])
                QuickChips(listOf(12, 24, 36, 60).map { m -> yearsPhrase(m / 12) to addMonthsClamped(today, m) }, f[SavingsFields.DATE]) { put(SavingsFields.DATE, it) }
            } else {
                CalcField(t(UiKey.SAVCALC_MONTHLY_LABEL), f[SavingsFields.MONTHLY], { put(SavingsFields.MONTHLY, it) }, unit = unit, error = errors[SavingsFields.MONTHLY])
                CalcField(
                    t(UiKey.SAVCALC_MONTHS_LABEL), f[SavingsFields.MONTHS], { put(SavingsFields.MONTHS, it) }, unit = t(UiKey.CALCUI_MONTHS_UNIT),
                    error = errors[SavingsFields.MONTHS], keyboard = KeyboardType.Number,
                )
                QuickChips(listOf(12, 24, 36, 60, 120).map { m -> yearsPhrase(m / 12) to m.toString() }, f[SavingsFields.MONTHS]) { put(SavingsFields.MONTHS, it) }
            }
            CalcField(t(UiKey.SAVCALC_HAVE_LABEL), f[SavingsFields.HAVE], { put(SavingsFields.HAVE, it) }, unit = unit, note = t(UiKey.SAVCALC_HAVE_NOTE), error = errors[SavingsFields.HAVE])
        }
    }
}

/** اختيارات سريعة تحت الخانة (سنة · سنتان · 3 سنوات …). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickChips(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, value) in options) SelectChip(label, current == value, { onPick(value) }, height = 36.dp)
    }
}
