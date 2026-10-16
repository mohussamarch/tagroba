package app.masroufy.ui.screens.investment.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.InvestInsightKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.TipGroupView

/**
 * «النصايح» (`InvestTips.dc.html` + `InvestTipCard` وضع «الكل»): مقدمة · المجموعات (ابدأ صح · وزّع · احمي فلوسك · الحلال) كل نصيحة
 * بمصدرها — **كلها**، حتى المقفولة من الكارت · سطر «نصايح عامة للتوعية، ومش توصية…». المصدر حسب بلد المساحة الشغالة.
 */
@Composable
internal fun InvestTipsScreen() {
    val space = LocalSpace.current
    val groups = remember(space.space.id) { space.investment.insights.tips.all(space.space) }
    InnerScaffold(t(InvestInsightKey.TIPS_TITLE)) { tipsPage(groups) }
}

internal fun androidx.compose.foundation.lazy.LazyListScope.tipsPage(groups: List<TipGroupView>) {
    item(key = "intro") { BasicText(t(InvestInsightKey.TIPS_INTRO), style = Type.of(13).copy(color = Ink.muted)) }
    // مجموعة مالهاش نصيحة موثّقة في بلد المساحة (زي «الحلال» لسه) ⇒ ما يترسمش عنوانها فاضي
    groups.filter { it.tips.isNotEmpty() }.forEachIndexed { i, g ->
        item(key = "group-$i") { TipGroupBlock(g) }
    }
    item(key = "footer") { BasicText(t(InvestInsightKey.TIPS_FOOTER), style = Type.of(12, lineHeight = 1.6).copy(color = Ink.soft)) }
}

@Composable
private fun TipGroupBlock(g: TipGroupView) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(g.title, style = Type.of(16, FontWeight.Bold).copy(color = Ink.text))
        for (tip in g.tips) {
            FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(tip.title, style = Type.of(14, FontWeight.Bold, 1.6).copy(color = Ink.text))
                    BasicText(tip.text, style = Type.of(13, lineHeight = 1.6).copy(color = Ink.soft))
                    BasicText(tip.source, style = Type.of(12).copy(color = Ink.muted))
                }
            }
        }
    }
}
