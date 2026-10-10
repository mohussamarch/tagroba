package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.INHERIT_MAX_COUNT
import app.masroufy.core.InheritanceLaw
import app.masroufy.core.SpecialCircumstance
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * الخطوة ٤ (لوحة `InheritanceBefore`): ما يُخرج قبل القسمة بالترتيب (التجهيز ⇒ الديون ⇒ الوصية) · الوصية لوارث وموافقة الورثة · مصر بس:
 * ابن أو بنت توفّي قبله وله أولاد (الوصية الواجبة) · الظروف الخاصة (أي واحدة ⇒ «غير مدعوم» بدل التخمين). الكلام بقانون الحسبة نفسها.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InheritanceBeforeStep(d: InheritanceDraft, onChange: (InheritanceDraft) -> Unit) {
    val b = d.before
    val eg = d.law == InheritanceLaw.EG
    val unit = currencySymbol(d.currency)
    val set = { nb: BeforeDraft -> onChange(d.copy(before = nb)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(t(UiKey.INHBEFORE_TITLE), style = Type.section())
            BasicText(t(UiKey.INHBEFORE_INTRO), style = Type.of(13).copy(color = Ink.muted))
        }
        FloatingCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CalcField(t(UiKey.INHBEFORE_FUNERAL), b.funeral, { set(b.copy(funeral = it)) }, unit = unit, note = t(if (eg) UiKey.INHBEFORE_FUNERAL_NOTE_EG else UiKey.INHBEFORE_FUNERAL_NOTE))
                CalcField(t(UiKey.INHBEFORE_DEBTS), b.debts, { set(b.copy(debts = it)) }, unit = unit, note = t(UiKey.INHBEFORE_DEBTS_NOTE))
                CalcField(t(UiKey.INHBEFORE_BEQUEST), b.bequest, { set(b.copy(bequest = it)) }, unit = unit, note = t(UiKey.INHBEFORE_BEQUEST_NOTE))
                Divider()
                CalcSwitchRow(t(UiKey.INHBEFORE_TO_HEIR), t(if (eg) UiKey.INHBEFORE_TO_HEIR_NOTE_EG else UiKey.INHBEFORE_TO_HEIR_NOTE_SA), b.toHeir, { set(b.copy(toHeir = !b.toHeir)) })
                Divider()
                CalcSwitchRow(t(UiKey.INHBEFORE_CONSENT), t(UiKey.INHBEFORE_CONSENT_NOTE), b.consent, { set(b.copy(consent = !b.consent)) })
            }
        }
        if (eg) {
            FloatingCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(t(UiKey.INHBEFORE_PRE_Q), style = Type.of(15, FontWeight.Bold))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip(t(UiKey.CALCUI_NO), !b.preOn, { set(b.copy(preOn = false)) }, Modifier.weight(1f), height = 44.dp)
                        SelectChip(t(UiKey.CALCUI_YES), b.preOn, { set(b.copy(preOn = true)) }, Modifier.weight(1f), height = 44.dp)
                    }
                    if (b.preOn) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip(t(UiKey.INHBEFORE_PRE_SON), b.preSon, { set(b.copy(preSon = true)) }, Modifier.weight(1f), height = 44.dp)
                            SelectChip(t(UiKey.INHBEFORE_PRE_DAUGHTER), !b.preSon, { set(b.copy(preSon = false)) }, Modifier.weight(1f), height = 44.dp)
                        }
                        KidsRow(t(UiKey.INHBEFORE_PRE_SONS), b.preSons) { set(b.copy(preSons = it)) }
                        KidsRow(t(UiKey.INHBEFORE_PRE_DAUGHTERS), b.preDaughters) { set(b.copy(preDaughters = it)) }
                        CalcField(t(UiKey.INHBEFORE_PRE_GIFT), b.preGift, { set(b.copy(preGift = it)) }, unit = unit)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(UiKey.INHBEFORE_SPECIAL), style = Type.bodyBold())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((sp, key) in SPECIALS) {
                    val on = sp in b.special
                    SelectChip(t(key), on, { set(b.copy(special = if (on) b.special - sp else b.special + sp)) }, height = 44.dp)
                }
            }
            BasicText(t(UiKey.INHBEFORE_SPECIAL_NOTE), style = Type.caption().copy(color = Ink.muted))
        }
    }
}

private val SPECIALS = listOf(
    SpecialCircumstance.PREGNANCY to UiKey.INHBEFORE_SP_PREGNANCY,
    SpecialCircumstance.MISSING_HEIR to UiKey.INHBEFORE_SP_MISSING,
    SpecialCircumstance.SUCCESSIVE_DEATHS to UiKey.INHBEFORE_SP_SUCCESSIVE,
    SpecialCircumstance.TAKHARUJ to UiKey.INHBEFORE_SP_TAKHARUJ,
)

@Composable
private fun KidsRow(label: String, count: Int, onSet: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = Type.bodyBold())
        Stepper(
            count,
            onMinus = { onSet(maxOf(0, count - 1)) },
            onPlus = { onSet(minOf(INHERIT_MAX_COUNT, count + 1)) },
            minusLabel = t(UiKey.INHHEIRS_LESS, label),
            plusLabel = t(UiKey.INHHEIRS_MORE_ONE, label),
            atMin = count <= 0,
            atMax = count >= INHERIT_MAX_COUNT,
        )
    }
}
