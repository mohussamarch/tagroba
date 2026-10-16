package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.InvestInsightKey
import app.masroufy.core.TextRef
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** سؤال وإجابته سطور. */
internal data class FaqItem(val question: String, val lines: List<String>)

/**
 * «الأسئلة الشائعة» (`Faq.dc.html` — §1.13): أول سؤال بس دلوقتي («على ماذا بُني حساب كم سيصبح معك؟»)، والصياغة لسه هتتراجع مع المالك
 * (Q11). مفيش أرقام غير مصدر الغلاء الرسمي للبلد الشغالة. أكورديون: واحد مفتوح بس، والأول مفتوح من الأول.
 */
internal fun faqItems(countryCode: String?): List<FaqItem> {
    val cpi = t(if (countryCode.equals("EG", ignoreCase = true)) InvestInsightKey.FAQ_CPI_EG else InvestInsightKey.FAQ_CPI_SA)
    val answer: List<TextRef> = listOf(InvestInsightKey.FAQ_A1_1, InvestInsightKey.FAQ_A1_2, InvestInsightKey.FAQ_A1_3)
    return listOf(
        FaqItem(
            t(InvestInsightKey.FAQ_Q1),
            answer.map { t(it) } + t(InvestInsightKey.FAQ_A1_4, cpi) + t(InvestInsightKey.FAQ_A1_5) + t(InvestInsightKey.FAQ_A1_6),
        ),
    )
}

@Composable
fun FaqScreen() {
    val items = faqItems(LocalSpace.current.space.countryCode)
    InnerScaffold(t(InvestInsightKey.FAQ_TITLE)) { faqPage(items) }
}

internal fun LazyListScope.faqPage(items: List<FaqItem>) {
    item(key = "faq") { FaqCard(items) }
    item(key = "faq-footer") { BasicText(t(InvestInsightKey.FAQ_FOOTER), style = Type.of(12).copy(color = Ink.muted)) }
}

@Composable
internal fun FaqCard(items: List<FaqItem>) {
    var open by rememberSaveable { mutableStateOf(0) }
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        items.forEachIndexed { i, item ->
            val isOpen = open == i
            val press = rememberPress()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .tap(press, role = Role.Button, label = item.question) { open = if (isOpen) -1 else i }
                    .semantics { stateDescription = if (isOpen) "expanded" else "collapsed" },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(item.question, Modifier.weight(1f), style = Type.of(15, FontWeight.Bold).copy(color = Ink.text))
                Box(Modifier.rotate(if (isOpen) 180f else 0f)) { LucideIcon(Lucide.CHEVRON_DOWN, size = 16.dp, tint = Ink.muted) }
            }
            if (isOpen) {
                Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (line in item.lines) BasicText(line, style = Type.of(13, lineHeight = 1.7).copy(color = Ink.soft))
                }
            }
        }
    }
}
