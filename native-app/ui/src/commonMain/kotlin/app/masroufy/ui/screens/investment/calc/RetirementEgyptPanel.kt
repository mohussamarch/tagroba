package app.masroufy.ui.screens.investment.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.EgyptInsuredCategory
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t

/**
 * «حاسبة التقاعد» في مصر (لوحة `RetirementEgypt` جوه `RetirementCalculator` — قانون 148/2019): المعاش من جدول 5 بأجر التسوية اللي المستخدم
 * بيكتبه · المكافأة «لا تنطبق» · ملاحظات الحد الأدنى والسقف المجهولين · اقتراح آخر رقم رسمي لحدود أجر الاشتراك (بيتكتب بلمسة).
 */
@Composable
fun RetirementEgyptPanel() {
    val space = LocalSpace.current
    val deps = space.investment.calculators
    val currency = space.space.currency
    val today = remember(deps) { deps.today() }
    val f = rememberFields()
    var categoryName by rememberSaveable { mutableStateOf(EgyptInsuredCategory.EMPLOYEE.name) }
    val category = EgyptInsuredCategory.valueOf(categoryName)
    val check = checkEgypt(f.snapshot(), category, currency, today)
    val state = rememberRetirement(check, "EG")
    val outcome = state.result?.outcome
    val e = check.errors
    val unit = currencySymbol(currency)
    val monthsUnit = t(TextKey.CALCUI_MONTHS_UNIT)
    val yearsUnit = t(TextKey.CALCUI_YEARS_UNIT)
    val legal = outcome?.pension?.legalAgeMonths
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RetirementResults(state, check, currency, t(TextKey.RETEG_HERO_LABEL), ::egyptHeroDetails, TextKey.RETEG_GAP_SUB) { egyptHow(it, currency) }
        val notes = egyptNotes(outcome?.takeIf { it.pension.pensionMinor != null }, state.defaults, currency)
        notes.forEach { NoteBox(it) }
        RetirementSection(t(TextKey.RETCALC_SEC_CONTRIB)) {
            ChoiceChips(
                t(TextKey.RETEG_CATEGORY),
                listOf(EgyptInsuredCategory.EMPLOYEE to t(TextKey.RETEG_CAT_EMPLOYEE), EgyptInsuredCategory.SELF_EMPLOYED_OR_ABROAD to t(TextKey.RETEG_CAT_SELF)),
                category, t(TextKey.RETEG_CATEGORY_NOTE),
            ) { categoryName = it.name }
            CalcDateField(t(TextKey.RETCALC_BIRTH), f[EgyptFields.BIRTH], { f[EgyptFields.BIRTH] = it }, today, error = e[EgyptFields.BIRTH])
            CalcField(t(TextKey.RETCALC_SO_FAR), f[EgyptFields.SO_FAR], { f[EgyptFields.SO_FAR] = it }, unit = monthsUnit, note = t(TextKey.RETCALC_SO_FAR_NOTE), error = e[EgyptFields.SO_FAR], keyboard = KeyboardType.Number)
            CalcField(t(TextKey.RETEG_PRE), f[EgyptFields.PRE], { f[EgyptFields.PRE] = it }, unit = monthsUnit, note = t(TextKey.RETEG_PRE_NOTE), error = e[EgyptFields.PRE], keyboard = KeyboardType.Number)
            CalcField(
                t(TextKey.RETEG_LEGAL), f[EgyptFields.LEGAL], { f[EgyptFields.LEGAL] = it }, unit = yearsUnit,
                note = egyptLegalNote(outcome, !f[EgyptFields.LEGAL].isBlankField(), category), error = e[EgyptFields.LEGAL],
                placeholder = legal?.let { (it / 12).toString() } ?: "", keyboard = KeyboardType.Number,
            )
            CalcField(
                t(TextKey.RETCALC_RETIRE), f[EgyptFields.RETIRE], { f[EgyptFields.RETIRE] = it }, unit = yearsUnit, note = t(TextKey.RETEG_RETIRE_NOTE),
                error = e[EgyptFields.RETIRE], placeholder = legal?.let { (it / 12).toString() } ?: "", keyboard = KeyboardType.Number,
            )
        }
        RetirementSection(t(TextKey.RETEG_SEC_WAGE)) {
            CalcField(t(TextKey.RETEG_WAGE), f[EgyptFields.WAGE], { f[EgyptFields.WAGE] = it }, unit = unit, note = t(TextKey.CALC_EGYPT_SETTLEMENT_WAGE_HELP), error = e[EgyptFields.WAGE])
        }
        RetirementSection(t(TextKey.RETEG_SEC_LIMITS)) {
            CalcField(t(TextKey.RETEG_MIN), f[EgyptFields.MIN], { f[EgyptFields.MIN] = it }, unit = unit, note = t(TextKey.RETEG_MIN_NOTE), error = e[EgyptFields.MIN])
            CalcField(t(TextKey.RETEG_MAX), f[EgyptFields.MAX], { f[EgyptFields.MAX] = it }, unit = unit, note = t(TextKey.RETEG_MAX_NOTE), error = e[EgyptFields.MAX])
            state.defaults?.egyptLatestLimits?.let { latest ->
                TonalButton(
                    t(TextKey.RETEG_SUGGEST, money(latest.minMinor, currency), money(latest.maxMinor, currency), sentenceNumber(latest.year)),
                    {
                        f[EgyptFields.MIN] = plainAmount(latest.minMinor, currency)
                        f[EgyptFields.MAX] = plainAmount(latest.maxMinor, currency)
                    },
                    Modifier.fillMaxWidth(), height = 52.dp,
                )
            }
        }
        RetirementSection(t(TextKey.RETCALC_SEC_LIVING)) {
            CalcField(t(TextKey.RETCALC_WANT), f[EgyptFields.WANT], { f[EgyptFields.WANT] = it }, unit = unit, error = e[EgyptFields.WANT])
            CalcField(t(TextKey.RETCALC_YEARS), f[EgyptFields.YEARS], { f[EgyptFields.YEARS] = it }, unit = yearsUnit, note = t(TextKey.RETCALC_YEARS_NOTE), error = e[EgyptFields.YEARS], keyboard = KeyboardType.Number)
            CalcField(t(TextKey.RETCALC_HAVE), f[EgyptFields.HAVE], { f[EgyptFields.HAVE] = it }, unit = unit, note = t(TextKey.RETCALC_HAVE_NOTE), error = e[EgyptFields.HAVE])
        }
        FootNote(t(TextKey.RETEG_ASSUME))
        FootNote(t(TextKey.RETEG_SOURCES))
    }
}
