package app.masroufy.ui.screens.investment.future

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.FUTURE_YEARS_STEPS
import app.masroufy.core.InvestFutureKey.*
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tabular
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ROW_DP = 52
private const val TOP_DP = 104
private val CARD_BG = Brush.linearGradient(listOf(Color(0xF7FFFFFF), Color(0xF2FBFAF4)))

/**
 * بكرة السنين (`YearWheel` — مواصفة §1.5): فوق ضباب، والسنين فوق وتحت زي البكرة — الاختيار بيتبع السحب وبيتبلّغ بعد ما السحب يقف
 * بـ120 ملّي ثانية؛ السحب لبعد آخر سطر (خانة «اكتب السنة») ما بيغيّرش الاختيار. «سجّلها علامة» بتفضل على التشارت لحد ما تتشال بـ«×».
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun YearWheel(state: InvestFutureState) {
    val steps = FUTURE_YEARS_STEPS
    val rowPx = with(LocalDensity.current) { ROW_DP.dp.toPx() }
    val start = steps.indexOf(state.choice.years).coerceAtLeast(0)
    val scroll = rememberScrollState((start * rowPx).roundToInt())
    val scope = rememberCoroutineScope()
    var pos by remember { mutableStateOf(start) }
    var typed by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val last = steps.lastIndex
    val inRows = scroll.value <= last * rowPx + rowPx / 2
    if (inRows) pos = (scroll.value / rowPx).roundToInt().coerceIn(0, last)
    LaunchedEffect(scroll.isScrollInProgress) {
        if (scroll.isScrollInProgress) return@LaunchedEffect
        delay(120)
        if (scroll.value <= last * rowPx + rowPx / 2) {
            // الاختيار قبل التسنيم: التسنيم بيقلب `isScrollInProgress` فبيلغي الأثر ده ويبدأه من الأول — لو الاختيار بعده عمره ما بيتنفذ
            // (سموك r05: البكرة بتقف على 5 والهيرو فاضل «10 سنين»)
            if (steps[pos] != state.choice.years) state.pickYears(steps[pos])
            val target = (pos * rowPx).roundToInt()
            if (scroll.value != target) scroll.animateScrollTo(target)
        }
    }
    val veilPress = rememberPress()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Color(0x99143028)).tap(veilPress, label = t(FUT_WHEEL_CLOSE)) { state.closeWheel() })
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.width(300.dp).shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x4D102D35), spotColor = Color(0x4D102D35))
                    .clip(RoundedCornerShape(28.dp)).background(CARD_BG).padding(top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText(t(FUT_WHEEL_TITLE), Modifier.fillMaxWidth(), style = Type.of(16, FontWeight.Bold).copy(textAlign = TextAlign.Center))
                BasicText(t(FUT_WHEEL_HINT), Modifier.fillMaxWidth(), style = Type.of(12).copy(color = Ink.muted, textAlign = TextAlign.Center))
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    Box(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = TOP_DP.dp).fillMaxWidth().height(ROW_DP.dp).clip(RoundedCornerShape(16.dp))
                            .background(Ink.selected).border(1.5.dp, Color(0x5908634F), RoundedCornerShape(16.dp)),
                    )
                    Column(Modifier.fillMaxWidth().height(260.dp).verticalScroll(scroll)) {
                        Spacer(Modifier.height(TOP_DP.dp))
                        steps.forEachIndexed { idx, k ->
                            val dist = abs(scroll.value / rowPx - idx)
                            WheelRow(k, state.thisYear + k, idx == pos, dist) { scope.launch { scroll.animateScrollTo((idx * rowPx).roundToInt()) } }
                        }
                        TypeYear(typed, err, state.choice.marks, { typed = it; err = null }, {
                            err = state.addMark(typed)
                            if (err == null) typed = ""
                        }) { state.removeMark(it) }
                    }
                }
            }
            val press = rememberPress()
            Box(
                Modifier.height(48.dp).shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .tap(press) { state.closeWheel() }.padding(horizontal = 36.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(t(FUT_WHEEL_DONE), style = Type.of(15, FontWeight.Bold).copy(color = Ink.primary)) }
        }
    }
}

/** سطر في البكرة: الاسم كبير (20 المختار · 17 الباقي) والسنة على الشمال — بيبهت ويصغر كل ما يبعد عن النص. */
@Composable
private fun WheelRow(k: Int, year: Int, on: Boolean, dist: Float, onClick: () -> Unit) {
    val press = rememberPress()
    val alpha = maxOf(0.25f, 1f - 0.32f * dist)
    val scale = maxOf(0.82f, 1f - 0.08f * dist)
    Row(
        Modifier.fillMaxWidth().height(ROW_DP.dp).padding(horizontal = 30.dp).graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
            .tap(press, role = Role.Button, onClick = onClick).semantics { selected = on },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(yearsName(k), Modifier.weight(1f), style = Type.of(if (on) 20 else 17, FontWeight.Bold).copy(color = if (on) Ink.text else Ink.soft))
        BasicText(year.toString(), style = Type.of(12).copy(color = Ink.muted).tabular())
    }
}

/** «أو اكتب السنة نفسها» + «سجّلها علامة» + العلامات اللي بتتشال بـ«×». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeYear(typed: String, err: String?, marks: List<Int>, onType: (String) -> Unit, onMark: () -> Unit, onRemove: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).background(Color.Transparent).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xCCCCD8CC)))
        BasicText(t(FUT_WHEEL_TYPE), style = Type.of(13, FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput(typed, onType, Modifier.weight(1f), placeholder = "2030", ltr = true, keyboard = KeyboardType.Number, height = 48.dp, textSize = 17)
            val press = rememberPress()
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(Ink.primary).tap(press, onClick = onMark).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(t(FUT_WHEEL_MARK), style = Type.of(13, FontWeight.Bold).copy(color = Color.White), maxLines = 1) }
        }
        if (err != null) BasicText(err, style = Type.of(12, FontWeight.Bold).copy(color = Ink.expense))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (y in marks) {
                val press = rememberPress()
                Row(
                    Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x24B7791F)).tap(press, label = t(FUT_WHEEL_REMOVE, y.toString())) { onRemove(y) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(y.toString(), style = Type.of(13, FontWeight.Bold).tabular())
                    LucideIcon(Lucide.X, size = 14.dp, tint = Ink.text)
                }
            }
        }
    }
}
