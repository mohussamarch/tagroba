package app.masroufy.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.OccasionKind
import app.masroufy.core.PersonCircle
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.overlay.RouteSheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import kotlinx.coroutines.launch

/**
 * «شخص جديد» (لوحة `AddPersonSheet`): الاسم (إجباري، ٨٠ حرف، ما يتكررش) · الدايرة · صلته بك · «+ مناسبة له» · «+ دين قديم بينكما».
 * متسجّلة كـ`AddPersonSheetRoute` (خانة «إضافة» في الأشخاص وزرار «+»). الحفظ ⇒ رسالة صغيرة والأشخاص بتحمّل تاني.
 */
@Composable
internal fun AddPersonRouteSheet(close: () -> Unit) {
    RouteSheet(t(TextKey.ADD_PERSON_TITLE), close) { dismiss -> AddPersonForm(dismiss) }
}

@Composable
private fun AddPersonForm(done: () -> Unit) {
    val space = LocalSpace.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf(NewPersonDraft()) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun edit(change: NewPersonDraft) {
        d = change
        err = null
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeading(t(TextKey.ADD_PERSON_TITLE))
        TextInput(d.name, { edit(d.copy(name = it.take(80))) }, label = t(TextKey.ADD_PERSON_NAME), placeholder = t(TextKey.ADD_PERSON_NAME_PH))
        FieldTitle(t(TextKey.ADD_PERSON_CIRCLE))
        ChoiceFlow {
            for (c in PersonCircle.entries) Choice(c.label, d.circle == c, { edit(d.copy(circle = if (d.circle == c) null else c)) })
        }
        TextInput(d.relation, { edit(d.copy(relation = it.take(30))) }, label = t(TextKey.ADD_PERSON_REL), placeholder = t(TextKey.ADD_PERSON_REL_PH))

        ExpandRow(t(TextKey.ADD_PERSON_OCC), d.occOpen, { edit(d.copy(occOpen = !d.occOpen)) })
        if (d.occOpen) {
            ChoiceFlow {
                for ((k, key) in listOf(OccasionKind.BIRTHDAY to TextKey.OCC_CHIP_BIRTHDAY, OccasionKind.WEDDING_ANNIVERSARY to TextKey.OCC_CHIP_ANNIV, OccasionKind.WEDDING to TextKey.OCC_CHIP_WEDDING)) {
                    Choice(t(key), d.occ.kind == k, { edit(d.copy(occ = d.occ.withKind(k))) })
                }
            }
            MonthGrid(d.occ.month, { edit(d.copy(occ = d.occ.copy(month = it))) })
            val wedding = d.occ.kind == OccasionKind.WEDDING
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput(d.occ.day, { edit(d.copy(occ = d.occ.copy(day = it.take(2)))) }, Modifier.weight(1f), label = t(TextKey.PPL_DAY_LABEL), placeholder = "1–31", decimal = false)
                NumberInput(
                    d.occ.year, { edit(d.copy(occ = d.occ.copy(year = it.take(4)))) }, Modifier.weight(1f),
                    label = t(if (wedding) TextKey.PPL_YEAR else TextKey.PPL_YEAR_OPTIONAL), placeholder = if (wedding) null else t(TextKey.PPL_YEAR_UNKNOWN), decimal = false,
                )
            }
            Note(t(if (wedding) TextKey.ADD_PERSON_OCC_NOTE_ONCE else TextKey.ADD_PERSON_OCC_NOTE_YEARLY))
        }

        ExpandRow(t(TextKey.ADD_PERSON_DEBT), d.debtOpen, { edit(d.copy(debtOpen = !d.debtOpen)) })
        if (d.debtOpen) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(t(TextKey.ADD_PERSON_SIDE_LAK), d.side == DebtSide.LAK, { edit(d.copy(side = DebtSide.LAK)) }, Modifier.weight(1f), height = 48.dp)
                Choice(t(TextKey.ADD_PERSON_SIDE_ALEK), d.side == DebtSide.ALEK, { edit(d.copy(side = DebtSide.ALEK)) }, Modifier.weight(1f), height = 48.dp)
            }
            NumberInput(d.amount, { edit(d.copy(amount = it)) }, label = t(TextKey.ADD_PERSON_AMOUNT), placeholder = "0.00", currency = space.space.currency)
            Note(t(TextKey.ADD_PERSON_DEBT_NOTE))
        }
        ErrorLine(err)
        PrimaryButton(
            t(TextKey.ADD_PERSON_SAVE),
            loading = busy,
            height = 52.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                when (val c = checkNewPerson(d, space.space.currency)) {
                    is PlanCheck.Bad -> err = c.message
                    is PlanCheck.Ok -> scope.launch {
                        busy = true
                        val saved = runCatching { saveNewPerson(space.people, c.plan) }
                        busy = false
                        PeopleChanges.bump()
                        saved.onSuccess { toaster.show(t(TextKey.ADD_PERSON_ADDED, c.plan.name)); done() }.onFailure { err = it.message }
                    }
                }
            },
        )
    }
}
