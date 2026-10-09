package app.masroufy.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.TextKey
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.overlay.RouteSheet
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.EventInput
import kotlinx.coroutines.launch

/**
 * «حدث جديد» (`EventAddSheet` — من الأحداث والتقويم) و«تعديل الحدث» (`EventEditSheet` — جوه تفاصيل الحدث): نفس الشكل —
 * الاسم · النوع (التسعة — رد المالك §76) · التاريخ · حدث مين — والتعديل فيه «مؤرشف» (مفيش مسح). من غير مبلغ (التجهيزات من صفحة الحدث).
 */
private const val WHO_CHIPS = 5

internal data class EventDraft(
    val name: String = "",
    val kind: LifeEventKind = LifeEventKind.WEDDING,
    val date: IsoDate? = null,
    val mine: Boolean = true,
    val host: Id? = null,
    val archived: Boolean = false,
) {
    companion object {
        fun of(e: LifeEvent) = EventDraft(e.name, e.kind, e.date, e.mine, e.hostPersonId, e.archived)
    }
}

/** الفحص قبل الحفظ (الاسم والتاريخ) — الباقي بتفحصه `ManageEvents` برسالته (الطول · التكرار · صاحب الحدث). */
internal fun checkEventDraft(d: EventDraft): Pair<EventInput?, String?> {
    val name = d.name.trim()
    if (name.isEmpty()) return null to t(TextKey.EVENT_FORM_NEED_NAME)
    val date = d.date ?: return null to t(TextKey.PPL_PICK_DATE)
    return EventInput(name, d.kind, date, d.mine, if (d.mine) null else d.host) to null
}

@Composable
internal fun EventAddRouteSheet(defaultDate: IsoDate?, close: () -> Unit) {
    RouteSheet(t(TextKey.EVENT_FORM_NEW), close) { dismiss -> EventForm(null, defaultDate, null, dismiss) }
}

/** زرار «تعديل الحدث» آخر صفحة الحدث ولوحته. */
@Composable
internal fun EventEditButton(data: EventScreenData) {
    var open by remember { mutableStateOf(false) }
    SecondaryButton(t(TextKey.EVENT_FORM_EDIT_OPEN), onClick = { open = true }, leading = PeopleIcons.PEN, modifier = Modifier.fillMaxWidth())
    Sheet(open, onDismiss = { open = false }, title = t(TextKey.EVENT_FORM_EDIT_TITLE)) {
        EventForm(data.ui.event, null, data, done = { open = false })
    }
}

@Composable
private fun EventForm(existing: LifeEvent?, defaultDate: IsoDate?, data: EventScreenData?, done: () -> Unit) {
    val space = LocalSpace.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val today = space.shell.today()
    var people by remember { mutableStateOf(data?.people.orEmpty()) }
    LaunchedEffect(space) { if (data == null) people = runCatching { space.people.people.listWithBalances().map { it.person } }.getOrDefault(emptyList()) }
    val start = defaultDate ?: dayNumberToIso(toDayNumber(parseIsoDate(today)) + 14)
    var d by remember(existing) { mutableStateOf(existing?.let { EventDraft.of(it) } ?: EventDraft(date = start)) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // حدثك اللي عليه نقوط جاتلك ما يتحوّلش لحدث حد تاني (EVENT_GIFT_IN_NOT_MINE)
    val lockMine = existing != null && existing.mine && data?.ui?.hasGiftsIn == true
    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeading(t(if (existing == null) TextKey.EVENT_FORM_NEW else TextKey.EVENT_FORM_EDIT_TITLE))
        TextInput(d.name, { d = d.copy(name = it.take(60)); err = null }, label = t(TextKey.EVENT_FORM_NAME), placeholder = t(TextKey.EVENT_FORM_NAME_PH))
        FieldTitle(t(TextKey.EVENT_FORM_KIND))
        ChoiceFlow { for (k in LifeEventKind.entries) Choice(k.label, d.kind == k, { d = d.copy(kind = k); err = null }) }
        when (d.kind) {
            LifeEventKind.CONDOLENCE -> Note(t(TextKey.EVENT_DETAIL_NO_PREP_CONDOLENCE))
            LifeEventKind.SCHOOL -> Note(t(TextKey.EVENT_FORM_SCHOOL_NOTE))
            else -> Unit
        }
        FieldTitle(t(TextKey.EVENT_FORM_WHEN))
        DateField(d.date, today, { d = d.copy(date = it); err = null })
        if (existing == null) Note(t(TextKey.EVENT_FORM_DATE_NOTE))
        FieldTitle(t(TextKey.EVENT_FORM_WHO))
        val visible = people.filter { !it.archived }.take(WHO_CHIPS).let { list ->
            val host = d.host?.let { h -> people.firstOrNull { it.id == h } }
            if (host != null && host !in list) list + host else list
        }
        ChoiceFlow {
            Choice(t(TextKey.EVENT_FORM_ME), d.mine, { d = d.copy(mine = true, host = null); err = null })
            for (p in visible) Choice(p.name, !d.mine && d.host == p.id, { d = d.copy(mine = false, host = p.id); err = null }, enabled = !lockMine)
        }
        val hostName = d.host?.let { h -> people.firstOrNull { it.id == h }?.name }
        when {
            lockMine -> Note(t(TextKey.EVENT_FORM_LOCK_MINE))
            !d.mine && hostName != null -> Note(t(TextKey.EVENT_FORM_HOST_NOTE, hostName))
        }
        if (existing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(t(TextKey.PPL_ARCHIVED), style = Type.bodyBold())
                    Note(t(TextKey.EVENT_FORM_ARCHIVED_SUB))
                }
                ToggleSwitch(d.archived, t(TextKey.PPL_ARCHIVED), { d = d.copy(archived = !d.archived) })
            }
        } else Note(t(TextKey.EVENT_FORM_HINT))
        ErrorLine(err)
        PrimaryButton(
            t(if (existing == null) TextKey.EVENT_FORM_ADD else TextKey.PPL_SAVE_EDIT),
            loading = busy,
            height = 52.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val (input, problem) = checkEventDraft(d)
                if (input == null) { err = problem; return@PrimaryButton }
                scope.launch {
                    busy = true
                    val r = runCatching {
                        val ev = space.people.events
                        if (existing == null) ev.create(input)
                        else {
                            ev.update(existing.id, input)
                            if (d.archived != existing.archived) ev.setArchived(existing.id, d.archived)
                        }
                    }
                    busy = false
                    r.onSuccess {
                        PeopleChanges.bump()
                        toaster.show(if (existing == null) t(TextKey.EVENT_FORM_ADDED, input.name) else t(TextKey.PPL_SAVED))
                        done()
                    }.onFailure { err = it.message }
                }
            },
        )
    }
}

/** خانة التاريخ: النص («١٨ أكتوبر ٢٠٢٦») والضغط بيفتح شبكة الشهر (`CalendarGrid` — السبت أول الأسبوع). */
@Composable
internal fun DateField(date: IsoDate?, today: IsoDate, onPick: (IsoDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val base = parseIsoDate(date ?: today)
    var year by remember(date) { mutableStateOf(base.year) }
    var month by remember(date) { mutableStateOf(base.month) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(date?.let { dayMonthYear(it) } ?: t(TextKey.PPL_PICK_DATE), onClick = { open = !open }, leading = app.masroufy.ui.icons.Lucide.CALENDAR, modifier = Modifier.fillMaxWidth())
        if (open) {
            app.masroufy.ui.shell.CalendarGrid(
                year = year, month = month, today = today, marks = emptyList(),
                selectedDay = date?.let { parseIsoDate(it) }?.takeIf { it.year == year && it.month == month }?.day,
                onPick = { day -> onPick(app.masroufy.core.formatIsoDate(app.masroufy.core.DateParts(year, month, day))); open = false },
                onMonth = { step ->
                    val m = month + step
                    when {
                        m < 1 -> { month = 12; year -= 1 }
                        m > 12 -> { month = 1; year += 1 }
                        else -> month = m
                    }
                },
                onToday = { parseIsoDate(today).let { year = it.year; month = it.month } },
            )
        }
    }
}
