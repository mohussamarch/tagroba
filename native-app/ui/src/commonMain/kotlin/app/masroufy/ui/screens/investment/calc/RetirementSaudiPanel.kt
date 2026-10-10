package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.EosEnd
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «حاسبة التقاعد» في السعودية (لوحة `RetirementSaudi` جوه `RetirementCalculator`): سؤال واحد بيحدد النظام (قبل يوليو ٢٠٢٤ ⇒ القديم) ·
 * المعاش المتوقع · المكافأة · كم تدّخر شهريًا · الخانات. الراتب وبداية العمل اقتراح من مصادر الدخل (فارغ = الاقتراح) ويتعدّلوا.
 */
@Composable
fun RetirementSaudiPanel() {
    val space = LocalSpace.current
    val deps = space.investment.calculators
    val currency = space.space.currency
    val today = remember(deps) { deps.today() }
    val f = rememberFields()
    var old by rememberSaveable { mutableStateOf(true) }
    var endName by rememberSaveable { mutableStateOf<String?>(null) }
    val end = endName?.let { EosEnd.valueOf(it) }
    val check = checkSaudi(f.snapshot(), old, end, currency, today)
    val state = rememberRetirement(check, "SA")
    val d = state.defaults
    val e = check.errors
    val unit = currencySymbol(currency)
    val monthsUnit = t(UiKey.CALCUI_MONTHS_UNIT)
    val yearsUnit = t(UiKey.CALCUI_YEARS_UNIT)
    val legal = state.result?.outcome?.pension?.legalAgeMonths
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FloatingCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(t(UiKey.RETSA_QUESTION), style = Type.of(15, FontWeight.Bold))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip(t(UiKey.CALCUI_YES), old, { old = true }, Modifier.weight(1f), height = 48.dp)
                    SelectChip(t(UiKey.CALCUI_NO), !old, { old = false }, Modifier.weight(1f), height = 48.dp)
                }
                BasicText(t(if (old) UiKey.RETSA_LAW_LINE_OLD else UiKey.RETSA_LAW_LINE_NEW), style = Type.caption().copy(color = Ink.muted))
            }
        }
        RetirementResults(state, check, currency, t(UiKey.RETSA_HERO_LABEL), ::saudiHeroDetails, UiKey.RETCALC_GAP_SUB) { saudiHow(it, currency) }
        RetirementSection(t(UiKey.RETCALC_SEC_CONTRIB)) {
            CalcDateField(t(UiKey.RETCALC_BIRTH), f[SaudiFields.BIRTH], { f[SaudiFields.BIRTH] = it }, today, error = e[SaudiFields.BIRTH])
            CalcField(t(UiKey.RETCALC_SO_FAR), f[SaudiFields.SO_FAR], { f[SaudiFields.SO_FAR] = it }, unit = monthsUnit, note = t(UiKey.RETCALC_SO_FAR_NOTE), error = e[SaudiFields.SO_FAR], keyboard = KeyboardType.Number)
            if (old) {
                CalcField(t(UiKey.RETSA_AT_2024), f[SaudiFields.AT_2024], { f[SaudiFields.AT_2024] = it }, unit = monthsUnit, note = t(UiKey.RETSA_AT_2024_NOTE), error = e[SaudiFields.AT_2024], keyboard = KeyboardType.Number)
                CalcField(t(UiKey.RETSA_M_1422), f[SaudiFields.M_1422], { f[SaudiFields.M_1422] = it }, unit = monthsUnit, note = t(UiKey.RETSA_M_1422_NOTE), error = e[SaudiFields.M_1422], keyboard = KeyboardType.Number)
            }
            CalcField(
                t(UiKey.RETCALC_RETIRE), f[SaudiFields.RETIRE], { f[SaudiFields.RETIRE] = it }, unit = yearsUnit,
                note = legal?.let { t(UiKey.RETCALC_RETIRE_NOTE_LEGAL, agePhrase(it)) } ?: t(UiKey.RETCALC_RETIRE_NOTE),
                error = e[SaudiFields.RETIRE], placeholder = legal?.let { (it / 12).toString() } ?: "", keyboard = KeyboardType.Number,
            )
        }
        RetirementSection(t(UiKey.RETSA_SEC_WAGE)) {
            val basic = d?.suggestedBasicMinor
            CalcField(
                t(UiKey.RETSA_BASIC), f[SaudiFields.BASIC], { f[SaudiFields.BASIC] = it }, unit = unit,
                note = basic?.let { t(UiKey.RETSA_BASIC_NOTE_SUGGEST, money(it, currency)) } ?: t(UiKey.RETSA_BASIC_NOTE),
                error = e[SaudiFields.BASIC], placeholder = basic?.let { plainAmount(it, currency) } ?: "0",
            )
            CalcField(t(UiKey.RETSA_HOUSING), f[SaudiFields.HOUSING], { f[SaudiFields.HOUSING] = it }, unit = unit, note = t(UiKey.RETSA_HOUSING_NOTE), error = e[SaudiFields.HOUSING])
            if ((d?.activeJobs ?: 0) > 1) NoteBox(t(UiKey.RETCALC_MANY_JOBS))
        }
        RetirementSection(t(UiKey.RETCALC_EOS)) {
            val job = d?.jobStartedAt
            CalcDateField(
                t(UiKey.RETSA_JOB), f[SaudiFields.JOB], { f[SaudiFields.JOB] = it }, today,
                note = job?.let { t(UiKey.RETSA_JOB_NOTE_SUGGEST, fullDate(it)) } ?: t(UiKey.RETSA_JOB_NOTE),
            )
            val salary = d?.salaryMinor
            CalcField(
                t(UiKey.RETSA_EOS_WAGE), f[SaudiFields.EOS_WAGE], { f[SaudiFields.EOS_WAGE] = it }, unit = unit,
                note = salary?.let { t(UiKey.RETSA_EOS_WAGE_NOTE_SUGGEST, money(it, currency)) } ?: t(UiKey.RETSA_EOS_WAGE_NOTE),
                error = e[SaudiFields.EOS_WAGE], placeholder = salary?.let { plainAmount(it, currency) } ?: "0",
            )
            ChoiceChips(
                t(UiKey.RETSA_END),
                listOf(EosEnd.EMPLOYER_OR_CONTRACT_END to t(UiKey.RETSA_END_CONTRACT), EosEnd.RESIGNATION to t(UiKey.RETSA_END_RESIGN), EosEnd.DISMISSED_ART80 to t(UiKey.RETSA_END_ART80)),
                end, t(UiKey.RETSA_END_NOTE),
            ) { endName = it.name }
        }
        RetirementSection(t(UiKey.RETCALC_SEC_LIVING)) {
            CalcField(t(UiKey.RETCALC_WANT), f[SaudiFields.WANT], { f[SaudiFields.WANT] = it }, unit = unit, error = e[SaudiFields.WANT])
            CalcField(t(UiKey.RETCALC_YEARS), f[SaudiFields.YEARS], { f[SaudiFields.YEARS] = it }, unit = yearsUnit, note = t(UiKey.RETCALC_YEARS_NOTE), error = e[SaudiFields.YEARS], keyboard = KeyboardType.Number)
            CalcField(t(UiKey.RETCALC_HAVE), f[SaudiFields.HAVE], { f[SaudiFields.HAVE] = it }, unit = unit, note = t(UiKey.RETCALC_HAVE_NOTE), error = e[SaudiFields.HAVE])
        }
        FootNote(t(UiKey.RETSA_ASSUME))
        FootNote(t(UiKey.RETSA_SOURCES))
    }
}
