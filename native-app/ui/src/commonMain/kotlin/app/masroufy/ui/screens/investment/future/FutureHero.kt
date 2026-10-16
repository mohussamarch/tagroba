package app.masroufy.ui.screens.investment.future

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.masroufy.core.InvestFutureKey.*
import app.masroufy.core.InvestRatesState
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.HeroDivider
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tabular
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.investment.RetirementCalculatorRoute
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** سهمين لفوق ولتحت (زرار المدة — النموذج `M8 9l4-4 4 4M8 15l4 4 4-4`). */
internal val UP_DOWN = Lucide("CHEVRONS_UP_DOWN", "m8 9 4-4 4 4", "m8 15 4 4 4-4")
private val DELTA_UP = Color(0xFFA6DEC1)
private val DELTA_DOWN = Color(0xFFFFA8AC)

/**
 * البطاقة البطلة «إنت كمان (10 سنين)» (`InvestFuture` السطور 24-37 — مواصفة §1.1): المدة بتفتح بكرة السنين · السنة والعمر · الرقم الكبير
 * على الطريق المختار وجملته · «النهارده ≈» والفرق. المتوسطات ناقصة ⇒ «غير متاح» وسببه. العمر مش مكتوب ⇒ رابط حاسبة التقاعد.
 */
@Composable
internal fun FutureHero(state: InvestFutureState) {
    if (state.loading) {
        Skeleton(Modifier.fillMaxWidth().height(132.dp), radius = 28.dp)
        return
    }
    val i = state.inputs ?: return
    val pr = state.projection
    val years = pr?.years ?: state.choice.years
    val white = Ink.onPrimary
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(t(FUT_YOU_IN), Modifier.semantics { heading() }, style = Type.of(20, FontWeight.Bold).copy(color = white))
                YearsPill(yearsName(years)) { state.openWheel() }
            }
            BasicText(yearLine(i.thisYear + years, i.ageNow?.plus(years)), style = Type.of(12).copy(color = Ink.onHeroMuted))
            if (pr != null) {
                BasicText(
                    AnnotatedString(approx(pr.endMinor, i.currency)), Modifier.fillMaxWidth().padding(top = 6.dp),
                    style = Type.of(36, FontWeight.Bold, 1.35).copy(color = white, textAlign = TextAlign.Right).tabular(),
                    maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 36.sp, stepSize = 1.sp),
                )
                BasicText(heroSentence(pr), style = Type.of(14).copy(color = white))
                HeroDivider(Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(t(FUT_TODAY_ROW, wholeMoney(i.netNowMinor, i.currency)), style = Type.of(12).copy(color = Ink.onHeroMuted))
                    val up = pr.deltaMinor >= 0
                    BasicText(
                        (if (up) "+" else "") + whole(pr.deltaMinor),
                        style = Type.of(12, FontWeight.Bold).copy(color = if (up) DELTA_UP else DELTA_DOWN).tabular(),
                    )
                }
                if (i.unknownCount > 0) BasicText(t(FUT_UNKNOWN_COUNT, i.unknownCount.toString()), style = Type.of(12).copy(color = Ink.onHeroMuted))
            } else if (i.rates is InvestRatesState.Unavailable) {
                BasicText(app.masroufy.core.NOT_AVAILABLE, style = Type.of(30, FontWeight.Bold).copy(color = white))
                BasicText(t(FUT_NA_BODY), style = Type.of(12).copy(color = Ink.onHeroMuted))
            }
            if (i.ageNow == null) AgeLink()
        }
    }
}

/** زرار المدة «10 سنين ⇕» (بيفتح بكرة السنين — `aria-haspopup=dialog`). */
@Composable
private fun YearsPill(label: String, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.height(40.dp).clip(shape).background(Color(0x29FFFFFF)).border(1.dp, Color(0x47FFFFFF), shape)
            .tap(press, label = t(FUT_WHEEL_A11Y, label), onClick = onClick).padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(label, style = Type.of(20, FontWeight.Bold).copy(color = Ink.onPrimary))
        LucideIcon(UP_DOWN, size = 16.dp, tint = Ink.onPrimary)
    }
}

/** «اكتب سنك في حاسبة التقاعد ←» — تاريخ الميلاد بيتحفظ هناك (§87-8). */
@Composable
private fun AgeLink() {
    val nav = LocalNavigator.current
    val press = rememberPress()
    BasicText(
        t(FUT_AGE_LINK),
        Modifier.heightIn(min = 32.dp).tap(press, role = Role.Button) { nav.push(RetirementCalculatorRoute) }.padding(vertical = 6.dp),
        style = Type.of(13, FontWeight.Bold).copy(color = Ink.mint),
    )
}
