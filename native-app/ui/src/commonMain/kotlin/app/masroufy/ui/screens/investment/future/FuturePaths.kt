package app.masroufy.ui.screens.investment.future

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.FutureMix
import app.masroufy.core.FuturePath
import app.masroufy.core.FutureProjection
import app.masroufy.core.InvestFutureKey.*
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tabular
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

private val MINUS = Lucide("MINUS", "M5 12h14")
private val ROW_IDLE = Color(0x09193D33)
private val CHIP_IDLE = Color(0x0F193D33)
private val AMBER_BOX = Color(0x14B7791F)

/**
 * «اختار طريقك» (`InvestFuture` السطور 42-71 — مواصفة §1.3): الطرق (2 أو 3) كأزرار اختيار — المختار هو الرقم الكبير والخط العريض ·
 * «هتستثمر كام في الشهر؟» بـ«+» و«−» لما «استثمر أكتر» مختار · «بتستثمرها في» ومصدر المتوسط لما الطريق مش «زي ما إنت».
 */
@Composable
internal fun FuturePathsCard(state: InvestFutureState) {
    val pr = state.projection ?: return
    FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                BasicText(t(FUT_PATHS_TITLE), style = Type.of(16, FontWeight.Bold))
                BasicText(t(FUT_PATHS_SUB), style = Type.of(12).copy(color = Ink.muted))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (line in pr.lines) PathRow(line.path, pr, line.path == pr.selected) { state.pickPath(line.path) }
            }
            if (pr.selected == FuturePath.MORE) Stepper(state, pr)
            if (pr.selected != FuturePath.KEEP) MixChips(state, pr)
        }
    }
}

@Composable
private fun PathRow(path: FuturePath, pr: FutureProjection, on: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    val surface = if (on) Modifier.background(Ink.selected).border(1.5.dp, Ink.primary, shape) else Modifier.background(ROW_IDLE)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).then(surface).tap(press, role = Role.RadioButton, onClick = onClick)
            .semantics { selected = on }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(pathColor(path)))
        Column(Modifier.weight(1f)) {
            BasicText(pathTitle(path, pr.inputs), style = Type.of(15, FontWeight.Bold))
            BasicText(pathDesc(path, pr), style = Type.of(12).copy(color = Ink.soft))
        }
        BasicText(approx(pr.valueAt(path, pr.years), pr.inputs.currency), style = Type.of(14, FontWeight.Bold).tabular(), maxLines = 1, softWrap = false)
    }
}

/** «هتستثمر كام في الشهر؟»: «+» على اليمين (أول عنصر في RTL) والمبلغ في النص و«−». */
@Composable
private fun Stepper(state: InvestFutureState, pr: FutureProjection) {
    val cur = pr.inputs.currency
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(RoundedCornerShape(16.dp)).background(AMBER_BOX).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(t(FUT_STEP_TITLE), style = Type.of(12, FontWeight.Bold).copy(color = Ink.soft))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton(Lucide.PLUS, t(FUT_STEP_PLUS, whole(pr.stepMinor))) { state.plus() }
            BasicText(
                wholeMoney(pr.amountMinor, cur), Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = Type.of(20, FontWeight.Bold).copy(textAlign = TextAlign.Center).tabular(), maxLines = 1,
            )
            StepButton(MINUS, t(FUT_STEP_MINUS, whole(pr.stepMinor))) { state.minus() }
        }
        BasicText(amountHint(pr), style = Type.of(11).copy(color = Ink.muted))
    }
}

@Composable
private fun StepButton(icon: Lucide, label: String, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(48.dp).shadow(4.dp, shape, ambientColor = Color(0x1A1D3635), spotColor = Color(0x1A1D3635)).clip(shape).background(Color.White)
            .tap(press, label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 20.dp, tint = Ink.primary) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixChips(state: InvestFutureState, pr: FutureProjection) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(t(FUT_MIX_TITLE), style = Type.of(12, FontWeight.Bold).copy(color = Ink.soft))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (m in FutureMix.entries) {
                val on = m == pr.choice.mix
                val press = rememberPress()
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier.height(36.dp).clip(shape).background(if (on) Ink.primary else CHIP_IDLE).tap(press, role = Role.RadioButton) { state.pickMix(m) }
                        .semantics { selected = on }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText(mixLabel(m), style = Type.of(12, FontWeight.Bold).copy(color = if (on) Color.White else Ink.text)) }
            }
        }
        BasicText(mixSourceLine(pr), style = Type.of(11).copy(color = Ink.muted))
    }
}
