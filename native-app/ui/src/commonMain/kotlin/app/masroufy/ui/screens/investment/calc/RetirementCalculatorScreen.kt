package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.EndOfServiceError
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.RetirementDefaults
import app.masroufy.usecase.RetirementOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * «حاسبة التقاعد» (لوحة `RetirementCalculator`): البلد تلقائي من المساحة الشغالة (مش اختيار — §69) ⇒ `RetirementSaudi` أو `RetirementEgypt`.
 * بتجاوب سؤالين: كم سيكون معاشك، وكم تدّخر شهريًا لتعيش بالمبلغ الذي تريده. كل رقم من `RetirementCalculator`.
 */
@Composable
fun RetirementCalculatorScreen() {
    val code = LocalSpace.current.space.countryCode.uppercase()
    InnerScaffold(t(UiKey.RETCALC_TITLE)) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val law = when (code) {
                    "SA" -> t(UiKey.RETCALC_LAW_SA)
                    "EG" -> t(UiKey.RETCALC_LAW_EG)
                    else -> null
                }
                if (law != null) BasicText(law, style = Type.caption().copy(color = Ink.muted))
                BasicText(t(UiKey.RETCALC_INTRO), style = Type.of(13).copy(color = Ink.muted))
            }
        }
        item(key = "panel") {
            when (code) {
                "SA" -> RetirementSaudiPanel()
                "EG" -> RetirementEgyptPanel()
                else -> EmptyState(t(UiKey.RETCALC_COUNTRY_NA))
            }
        }
    }
}

/** الحسبة نفسها (مشتركة بين اللوحتين): الراتب من مصادر الدخل مرة، والنتيجة كل ما الخانات تتغير. */
internal class RetirementState(val defaults: RetirementDefaults?, val result: RetirementResult?, val error: String?, val failed: Boolean = false)

@Composable
internal fun rememberRetirement(check: RetirementCheck, countryCode: String): RetirementState {
    val deps = LocalSpace.current.investment.calculators
    val today = remember(deps) { deps.today() }
    var defaults by remember(deps) { mutableStateOf<RetirementDefaults?>(null) }
    LaunchedEffect(deps, countryCode) { defaults = runCatching { deps.retirement.defaults(countryCode, today) }.getOrNull() }
    var result by remember(deps) { mutableStateOf<RetirementResult?>(null) }
    var error by remember(deps) { mutableStateOf<String?>(null) }
    var failed by remember(deps) { mutableStateOf(false) }
    LaunchedEffect(deps, check.request, check.eosChosen) {
        val req = check.request
        failed = false
        if (req == null) {
            result = null
            error = null
            return@LaunchedEffect
        }
        delay(250)
        try {
            result = try {
                RetirementResult(deps.retirement.calculate(req, today), check.eosChosen)
            } catch (e: EndOfServiceError) {
                // بيانات المكافأة غلط (مثلًا بداية العمل بعد يوم التقاعد): المعاش يتحسب من غيرها، والمكافأة «غير متاح» بالسبب
                RetirementResult(deps.retirement.calculate(req.copy(noCurrentJob = true, desiredMonthlyMinor = null), today), check.eosChosen, e.message)
            }
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            result = null
            error = e.message
        } catch (e: Exception) {
            // قراية مصادر الدخل وقعت ⇒ «غير متاح» بسببه (مش «أكمل البيانات» ولا وقوع)
            result = null
            failed = true
        }
    }
    return RetirementState(defaults, result, error, failed)
}

/** البطاقة البطلة + سطري النتيجة + «كيف حُسب المعاش؟». */
@Composable
internal fun RetirementResults(
    state: RetirementState,
    check: RetirementCheck,
    currency: Currency,
    heroLabel: String,
    details: (RetirementOutcome) -> String?,
    gapSubKey: TextRef,
    how: (RetirementOutcome) -> List<String>,
) {
    var howOpen by remember { mutableStateOf(false) }
    val result = state.result
    val ui = when {
        check.request == null -> retirementIncomplete()
        state.failed -> retirementFailed()
        state.error != null -> retirementIncomplete(state.error)
        result == null -> null
        else -> retirementUi(result, currency, details, gapSubKey)
    }
    if (ui == null) {
        Skeleton(Modifier.fillMaxWidth().height(132.dp), radius = 28.dp)
        return
    }
    CalcHero(heroLabel, ui.pensionMinor, currency, ui.heroNa, listOfNotNull(ui.heroSub.takeIf { it.isNotEmpty() }, ui.heroReason).joinToString("\n"))
    if (ui.outs.isNotEmpty()) {
        val lines = result?.takeIf { ui.pensionMinor != null }?.let { how(it.outcome) }.orEmpty()
        OutsCard(ui.outs, currency) {
            if (lines.isNotEmpty()) HowToggle(howOpen, t(UiKey.RETCALC_HOW_SHOW), t(UiKey.RETCALC_HOW_HIDE), lines) { howOpen = !howOpen }
        }
    }
}

/** قسم بعنوان وكارت فيه الخانات. */
@Composable
internal fun RetirementSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CalcSectionTitle(title)
        FloatingCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
    }
}

/** اختيار واحد من شرائح (إزاي الشغل هيخلص · إنت مؤمَّن عليك إزاي). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceChips(label: String, options: List<Pair<T, String>>, selected: T?, note: String?, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        app.masroufy.ui.components.FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((value, text) in options) SelectChip(text, value == selected, { onPick(value) }, height = 44.dp)
        }
        if (!note.isNullOrEmpty()) BasicText(note, style = Type.caption().copy(color = Ink.muted))
    }
}
