package app.masroufy.ui.screens.investment.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.masroufy.core.InvestInsightKey
import app.masroufy.core.formatBp
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.ReturnsView

private val lineColor = Color(0xE6CCD8CC)
private val spendBg = Ink.tint(0x0D)
private val reBg = Color(0x1A0E8A6E)

/** نص الصندوقين والملاحظة — كل رقم من [ReturnsView] (المتوسطات ناقصة ⇒ «غير متاح» + السبب). */
internal data class ReturnsTexts(val reValue: String, val note: String)

internal fun returnsTexts(v: ReturnsView): ReturnsTexts {
    val m = v.math ?: return ReturnsTexts(t(InvestInsightKey.RET_NA), t(InvestInsightKey.RET_NA_NOTE))
    val per = money(m.perMonthMinor, v.currency)
    val spent = money(m.spentMinor, v.currency)
    val reinvest = money(m.reinvestMinor, v.currency)
    // مفيش أملاك بتكبر ⇒ متوسط الأسهم ومصدره (مش «متوسط أملاكك»)
    val note = v.sharesSource?.let { t(InvestInsightKey.RET_NOTE_SHARES, per, formatBp(m.mixBp), spent, reinvest, it) }
        ?: t(InvestInsightKey.RET_NOTE, per, formatBp(m.mixBp), spent, reinvest)
    // «≈ M» سطر واحد من الشمال لليمين زي النموذج (`dir="ltr"`): عزل LTR + مسافة ما بتتقسمش بعد «≈»
    val re = t(InvestInsightKey.RET_RE_VALUE, money(m.reinvestMinor, v.currency)).replace("≈ ", "≈ ")
    return ReturnsTexts("⁦$re⁩", note)
}

/** «اللي رجعلك» (`InvestReturns.dc.html`): السطور بالتصنيف · «المجموع من يناير» · «لو صرفته كله» / «لو رجّعته تستثمره» · الملاحظة. */
@Composable
internal fun ReturnsCard(v: ReturnsView?) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(InvestInsightKey.RET_TITLE), style = Type.of(16, FontWeight.Bold).copy(color = Ink.text))
            if (v == null) {
                BasicText(t(InvestInsightKey.RET_NONE), style = Type.of(13).copy(color = Ink.muted))
                return@Column
            }
            for (r in v.rows) {
                Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(r.label, style = Type.of(14).copy(color = Ink.text))
                        BasicText(r.sub, style = Type.of(11).copy(color = Ink.muted))
                    }
                    AmountText(r.amountMinor, v.currency, size = 14)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(lineColor))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(InvestInsightKey.RET_TOTAL), Modifier.weight(1f), style = Type.of(15, FontWeight.Bold).copy(color = Ink.text))
                AmountText(v.totalMinor, v.currency, size = 15)
            }
            val texts = returnsTexts(v)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReturnBox(t(InvestInsightKey.RET_SPEND_LABEL), t(InvestInsightKey.RET_SPEND_VALUE), spendBg, Ink.muted, 16, Modifier.weight(1f))
                ReturnBox(t(InvestInsightKey.RET_RE_LABEL), texts.reValue, reBg, Ink.primary, 18, Modifier.weight(1f))
            }
            BasicText(texts.note, style = Type.of(12, lineHeight = 1.6).copy(color = Ink.muted))
        }
    }
}

@Composable
private fun ReturnBox(label: String, value: String, bg: Color, labelInk: Color, size: Int, modifier: Modifier) {
    Column(
        modifier.background(bg, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(label, style = Type.of(12).copy(color = labelInk))
        // سطر واحد بيصغر لحد 12 لو الرقم طويل (بالهللة) — النموذج كان بيقرّب للريال، بس المنسّق الموجود بيعرض الهللة (§0)
        BasicText(
            AnnotatedString(value), style = Type.of(size, FontWeight.Bold, 1.3).copy(color = Ink.text), maxLines = 1, softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = size.sp, stepSize = 1.sp),
        )
    }
}
