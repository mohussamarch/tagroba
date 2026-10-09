package app.masroufy.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Person
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.overlay.RouteSheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «أضف الديون القديمة» (لوحة `ProfileDebtSheet` — من سؤال الديون في «كمّل ملفك»): لكل شخص (موجود أو اسم جديد) مبلغ،
 * والاتجاه حسب السؤال (لك عنده · عليك له · الاتنين ⇒ يختار لكل سطر). بتتكتب في الأشخاص على طول من غير عملية (`addOpeningDebt` — §26 §27).
 */
private const val PEOPLE_CHIPS = 5

@Composable
internal fun ProfileDebtRouteSheet(mode: DebtMode, close: () -> Unit) {
    RouteSheet(t(TextKey.PROFILE_DEBT_TITLE), close) { dismiss -> ProfileDebtForm(mode, dismiss) }
}

@Composable
private fun ProfileDebtForm(mode: DebtMode, done: () -> Unit) {
    val space = LocalSpace.current
    val currency = space.space.currency
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    LaunchedEffect(space) { people = runCatching { space.people.people.listWithBalances().map { it.person }.filter { !it.archived } }.getOrDefault(emptyList()) }
    val startSide = if (mode == DebtMode.PAY) DebtSide.ALEK else DebtSide.LAK
    var rows by remember { mutableStateOf<List<OldDebtRow>>(emptyList()) }
    var d by remember { mutableStateOf(OldDebtDraft(side = startSide)) }
    var bad by remember { mutableStateOf<OldDebtCheck.Bad?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun addCurrent(): Boolean = when (val c = checkOldDebt(d, currency)) {
        is OldDebtCheck.Ok -> { rows = rows + c.row; d = OldDebtDraft(side = d.side); bad = null; true }
        is OldDebtCheck.Bad -> { bad = c; false }
        OldDebtCheck.Empty -> { bad = OldDebtCheck.Bad(true, true, t(TextKey.PROFILE_DEBT_NEED_NAME)); false }
    }

    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(t(TextKey.PROFILE_DEBT_TITLE), style = Type.section())
        BasicText(t(TextKey.PROFILE_DEBT_NOTE), style = Type.of(13).copy(color = Ink.muted))
        if (rows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PeopleInk.rowBg).padding(horizontal = 12.dp, vertical = 2.dp)) {
                rows.forEachIndexed { i, r ->
                    Rowed(i == rows.lastIndex) {
                        Column(Modifier.weight(1f)) {
                            BasicText(r.name, style = Type.of(15, FontWeight.Bold))
                            val lak = r.side == DebtSide.LAK
                            BasicText(t(if (lak) TextKey.PROFILE_DEBT_DIR_LAK else TextKey.PROFILE_DEBT_DIR_ALEK), style = Type.captionBold().copy(color = if (lak) Ink.income else Ink.expense))
                        }
                        AmountText(r.amountMinor, currency, tone = if (r.side == DebtSide.LAK) AmountTone.INCOME else AmountTone.EXPENSE)
                        SquareIcon(Lucide.X, t(TextKey.PPL_REMOVE_NAMED, r.name), { rows = rows.filterIndexed { j, _ -> j != i } })
                    }
                }
            }
        }
        FieldTitle(t(TextKey.PROFILE_DEBT_WHO))
        ChoiceFlow {
            for (p in people.take(PEOPLE_CHIPS)) {
                val on = d.who == p.id && d.newName.isBlank()
                Choice(p.name, on, { d = d.copy(who = p.id, whoName = p.name, newName = ""); bad = null }, filled = true)
            }
        }
        TextInput(
            d.newName, { d = d.copy(newName = it.take(80)); bad = null }, placeholder = t(TextKey.PROFILE_DEBT_NEW),
            error = if (bad?.nameBad == true) bad?.message else null,
        )
        if (mode == DebtMode.BOTH) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in DebtSide.entries) {
                    Choice(t(if (s == DebtSide.LAK) TextKey.PROFILE_DEBT_DIR_LAK else TextKey.PROFILE_DEBT_DIR_ALEK), d.side == s, { d = d.copy(side = s) }, Modifier.weight(1f), filled = true)
                }
            }
        }
        NumberInput(
            d.amount, { d = d.copy(amount = it); bad = null }, label = t(if (d.side == DebtSide.LAK) TextKey.PROFILE_DEBT_HOW_MUCH_LAK else TextKey.PROFILE_DEBT_HOW_MUCH_ALEK),
            placeholder = "0", currency = currency, error = if (bad?.amountBad == true && bad?.nameBad != true) bad?.message else null,
        )
        ErrorLine(err)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(t(TextKey.PROFILE_DEBT_ADD_ANOTHER), onClick = { addCurrent() }, modifier = Modifier.weight(1f), height = 52.dp)
            PrimaryButton(
                t(TextKey.PROFILE_DEBT_DONE),
                loading = busy,
                height = 52.dp,
                modifier = Modifier.weight(1f),
                onClick = {
                    val empty = checkOldDebt(d, currency) == OldDebtCheck.Empty
                    if ((!empty || rows.isEmpty()) && !addCurrent()) return@PrimaryButton
                    scope.launch {
                        busy = true
                        val saved = runCatching { saveOldDebts(space.people, rows, currency) }
                        busy = false
                        PeopleChanges.bump()
                        saved.onSuccess { done() }.onFailure { err = it.message }
                    }
                },
            )
        }
    }
}
