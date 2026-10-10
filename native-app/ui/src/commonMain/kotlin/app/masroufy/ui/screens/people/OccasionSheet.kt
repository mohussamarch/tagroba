package app.masroufy.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionKind
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.RouteSheet
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.UpcomingOccasion
import kotlinx.coroutines.launch

/**
 * مناسبات الشخص في ملفه (لوحة `OccasionSheet`): كارت لكل مناسبة جاية بـ«تعديل» + زرار «+ أضف مناسبة لـ…» ⇒ اللوحة.
 * نفس اللوحة متسجّلة كـ`OccasionSheetRoute` عشان «ملفك» يفتحها لمناسباتك إنت.
 */
@Composable
internal fun OccasionCards(personId: Id?, personName: String?, items: List<UpcomingOccasion>, modifier: Modifier = Modifier) {
    val today = LocalSpace.current.shell.today()
    var editing by remember { mutableStateOf<Occasion?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (u in items) {
            FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Ink.focus) { LucideIcon(Lucide.GIFT, size = 20.dp, tint = Ink.focus) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(occasionCardTitle(u), style = Type.bodyBold())
                        BasicText(occasionCardSub(u, today), style = Type.caption().copy(color = Ink.muted))
                    }
                    TonalButton(t(TextKey.OCC_EDIT), onClick = { editing = u.occasion })
                }
            }
        }
        TonalButton(
            if (personName != null) t(TextKey.OCC_ADD_FOR, personName) else t(TextKey.OCC_ADD),
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val open = adding || editing != null
    Sheet(open, onDismiss = { adding = false; editing = null }, title = occasionSheetTitle(editing, personName)) {
        OccasionForm(personId, personName, editing, onDone = { adding = false; editing = null })
    }
}

private fun occasionSheetTitle(existing: Occasion?, personName: String?): String = when {
    existing != null -> t(TextKey.OCC_TITLE_EDIT)
    personName != null -> t(TextKey.OCC_TITLE_FOR, personName)
    else -> t(TextKey.OCC_TITLE_OWN)
}

/** اللوحة من مكان تاني (ملفك): بتقرا اسم الشخص والمناسبة الأول. */
@Composable
internal fun OccasionRouteSheet(personId: Id?, occasionId: Id?, close: () -> Unit) {
    val deps = LocalSpace.current.people
    val today = LocalSpace.current.shell.today()
    var name by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<Occasion?>(null) }
    var ready by remember { mutableStateOf(personId == null && occasionId == null) }
    LaunchedEffect(personId, occasionId) {
        name = personId?.let { id -> runCatching { deps.people.listWithBalances().firstOrNull { it.person.id == id }?.person?.name }.getOrNull() }
        existing = occasionId?.let { id -> runCatching { deps.occasions.upcoming(today).firstOrNull { it.occasion.id == id }?.occasion }.getOrNull() }
        ready = true
    }
    RouteSheet(occasionSheetTitle(existing, name), close) { dismiss ->
        if (ready) OccasionForm(personId, name, existing, onDone = dismiss)
    }
}

@Composable
private fun OccasionForm(personId: Id?, personName: String?, existing: Occasion?, onDone: () -> Unit) {
    val space = LocalSpace.current
    val deps = space.people
    val today = space.shell.today()
    val scope = rememberCoroutineScope()
    var d by remember(existing) { mutableStateOf(existing?.let { OccasionDraft.of(it) } ?: OccasionDraft()) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val preview = occasionPreview(d, today)
    Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeading(occasionSheetTitle(existing, personName))
        ChoiceFlow {
            for ((k, key) in listOf(
                OccasionKind.BIRTHDAY to TextKey.OCC_CHIP_BIRTHDAY, OccasionKind.WEDDING_ANNIVERSARY to TextKey.OCC_CHIP_ANNIV,
                OccasionKind.WEDDING to TextKey.OCC_CHIP_WEDDING, OccasionKind.OTHER to TextKey.OCC_CHIP_OTHER,
            )) Choice(t(key), d.kind == k, { d = d.withKind(k); err = null })
        }
        if (d.kind == OccasionKind.OTHER) {
            app.masroufy.ui.components.TextInput(d.label, { d = d.copy(label = it.take(60)); err = null }, placeholder = t(TextKey.OCC_LABEL_PH))
        }
        FieldTitle(t(TextKey.PPL_MONTH_LABEL))
        MonthGrid(d.month, { d = d.copy(month = it); err = null })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberInput(d.day, { d = d.copy(day = it.take(2)); err = null }, Modifier.weight(1f), label = t(TextKey.PPL_DAY_LABEL), placeholder = "1–31", decimal = false)
            NumberInput(d.year, { d = d.copy(year = it.take(4)); err = null }, Modifier.weight(1f), label = t(TextKey.PPL_YEAR_OPTIONAL), placeholder = t(TextKey.PPL_YEAR_UNKNOWN), decimal = false)
        }
        BasicText(
            preview.next,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PeopleInk.tonalSoft).padding(horizontal = 14.dp, vertical = 10.dp),
            style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(t(TextKey.OCC_YEARLY), d.yearly, { d = d.copy(yearly = true); err = null }, Modifier.weight(1f), height = 48.dp)
            Choice(t(TextKey.OCC_ONCE), !d.yearly, { d = d.copy(yearly = false); err = null }, Modifier.weight(1f), height = 48.dp)
        }
        FieldTitle(t(TextKey.OCC_LEAD_LABEL))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareIcon(PeopleIcons.MINUS, t(TextKey.OCC_LESS), { d = d.withLead(d.lead - 1) }, size = 48.dp, tint = Ink.primary)
            BasicText(leadName(d.lead), Modifier.weight(1f), style = Type.of(15, FontWeight.Bold).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            SquareIcon(Lucide.PLUS, t(TextKey.OCC_MORE), { d = d.withLead(d.lead + 1) }, size = 48.dp, tint = Ink.primary)
        }
        ChoiceFlow {
            for ((v, key) in listOf(1 to TextKey.OCC_CHIP_DAY, 7 to TextKey.OCC_CHIP_WEEK, 14 to TextKey.OCC_CHIP_TWO_WEEKS, 30 to TextKey.OCC_CHIP_MONTH)) {
                Choice(t(key), d.lead == v, { d = d.withLead(v) }, textSize = 13)
            }
        }
        Note(preview.remind)
        if (existing != null) {
            DangerButton(
                t(if (confirm) TextKey.OCC_REMOVE_CONFIRM else TextKey.OCC_REMOVE),
                armed = confirm,
                onClick = {
                    if (!confirm) confirm = true
                    else scope.launch {
                        err = runCatching { deps.occasions.remove(existing.id) }.exceptionOrNull()?.message
                        if (err == null) { PeopleChanges.bump(); onDone() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ErrorLine(err)
        PrimaryButton(
            t(if (existing != null) TextKey.PPL_SAVE_EDIT else TextKey.OCC_SAVE_NEW),
            onClick = {
                when (val c = checkDraft(d, personId)) {
                    is OccasionCheck.Bad -> err = c.message
                    is OccasionCheck.Ok -> scope.launch {
                        busy = true
                        err = runCatching { if (existing != null) deps.occasions.update(existing.id, c.input) else deps.occasions.add(c.input) }.exceptionOrNull()?.message
                        busy = false
                        if (err == null) { PeopleChanges.bump(); onDone() }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            loading = busy,
            height = 52.dp,
        )
    }
}

/** عنوان خانة (13 عريض). */
@Composable
internal fun FieldTitle(text: String) {
    BasicText(text, style = Type.of(13, FontWeight.Bold))
}
