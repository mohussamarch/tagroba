package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.AutoMeaning
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Person
import app.masroufy.core.PersonMeaning
import app.masroufy.core.UiKey
import app.masroufy.core.meaningsFor
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LinkOutcome
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePersonLinks
import kotlinx.coroutines.launch

/**
 * «مع مين؟» (جولة r05): لوحة واحدة لكل طرق «العملية دي مع الشخص ده» — «اربطها بـ › شخص» لأي نوع · بعد تصنيف فلوس مع شخص · «محتاجة تربطها
 * بشخص» · اقتراحات صفحة الشخص ([MeaningTarget.personId] معروف ⇒ المعنى بس). الشخص الجديد بيتضاف من نفس اللوحة. الحفظ = `ManagePersonLinks.link`
 * (الدين أو السداد أو النصيب في نفس الخطوة)، والشاشة بتعرض «تراجع» بعده.
 */
data class MeaningTarget(
    val transactionId: Id,
    val direction: Direction,
    val title: String,
    val amountMinor: Halalas,
    val currency: Currency,
    val personId: Id? = null,
)

/** بعد الربط: الرسالة اللي تتعرض جنب «تراجع». */
data class PersonLinked(val transactionId: Id, val text: String)

fun meaningLabel(m: PersonMeaning): String = t(
    when (m) {
        PersonMeaning.LENT -> UiKey.PLINK_M_LENT
        PersonMeaning.REPAID -> UiKey.PLINK_M_REPAID
        PersonMeaning.GAVE -> UiKey.PLINK_M_GAVE
        PersonMeaning.BORROWED -> UiKey.PLINK_M_BORROWED
        PersonMeaning.COLLECTED -> UiKey.PLINK_M_COLLECTED
        PersonMeaning.RECEIVED -> UiKey.PLINK_M_RECEIVED
        PersonMeaning.SUPPORTED -> UiKey.PLINK_M_SUPPORTED
        PersonMeaning.WITH -> UiKey.PLINK_M_WITH
    },
)

fun autoLabel(a: AutoMeaning): String = t(
    when (a) {
        AutoMeaning.DUES -> UiKey.PLINK_AUTO_DUES
        AutoMeaning.LOAN -> UiKey.PLINK_AUTO_LOAN
        AutoMeaning.GIFT -> UiKey.PLINK_AUTO_GIFT
        AutoMeaning.SUPPORT -> UiKey.PLINK_AUTO_SUPPORT
        AutoMeaning.OTHER -> UiKey.PLINK_AUTO_OTHER
    },
)

/** «اتربطت بـ{الشخص} — {المعنى}» + لو جزء من السداد مالقاش دين مفتوح. */
fun linkedText(out: LinkOutcome, currency: Currency): String {
    val done = t(UiKey.PLINK_DONE, out.personName, meaningLabel(out.link.meaning))
    return if (out.unmatchedMinor > 0L) "$done. ${t(UiKey.PLINK_DONE_UNMATCHED, amountLabel(out.unmatchedMinor, currency))}" else done
}

/** خطأ قبل الحفظ (الشخص · المعنى) — null = جاهز. */
fun meaningError(hasPerson: Boolean, meaning: PersonMeaning?): String? = when {
    !hasPerson -> t(UiKey.PLINK_ERR_PERSON)
    meaning == null -> t(UiKey.PLINK_ERR_MEANING)
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonMeaningSheet(
    target: MeaningTarget?,
    links: ManagePersonLinks?,
    people: ManagePeople,
    onDismiss: () -> Unit,
    onLinked: (PersonLinked) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<Person>>(emptyList()) }
    var person by remember { mutableStateOf<Person?>(null) }
    var newName by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf<PersonMeaning?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        person = null; newName = null; query = ""; meaning = null; error = null; saving = false
        if (target == null) return@LaunchedEffect
        all = attempt { people.listWithBalances().map { it.person } }.orEmpty()
        target.personId?.let { id -> person = all.firstOrNull { it.id == id } }
    }
    val tg = target
    val known = tg?.personId != null
    Sheet(tg != null && links != null, onDismiss, title = t(if (known) UiKey.PLINK_SHEET_MEANING_TITLE else UiKey.PLINK_SHEET_WHO), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        if (tg != null && links != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(t(if (known) UiKey.PLINK_SHEET_MEANING_TITLE else UiKey.PLINK_SHEET_WHO), style = Type.of(17, FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(tg.title, Modifier.weight(1f, fill = false), style = Type.of(13).copy(color = Ink.muted))
                    AmountText(tg.amountMinor, tg.currency, tone = if (tg.direction == Direction.IN) AmountTone.INCOME else AmountTone.EXPENSE, size = 13)
                }
            }
            if (!known) {
                TextInput(query, { query = it; error = null }, placeholder = t(UiKey.LINK_PERSON_SEARCH))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    newName?.let { n -> SelectChip(n, true, {}, height = 44.dp) }
                    for (p in personChoices(all, query, person)) SelectChip(p.name, newName == null && person?.id == p.id, { person = p; newName = null; error = null }, height = 44.dp)
                    if (canAddPerson(all, query)) SelectChip(t(UiKey.LINK_PERSON_ADD_NEW, query.trim()), false, { newName = query.trim(); person = null; query = ""; error = null }, height = 44.dp)
                }
            } else person?.let { BasicText(it.name, style = Type.of(15, FontWeight.Bold)) }
            FieldLabel(t(UiKey.PLINK_SHEET_MEANING))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in meaningsFor(tg.direction)) SelectChip(meaningLabel(m), meaning == m, { meaning = m; error = null }, height = 44.dp)
            }
            BasicText(t(UiKey.PLINK_SHEET_NOTE), style = Type.of(12).copy(color = Ink.muted))
            error?.let { FieldError(it) }
            PrimaryButton(t(UiKey.PLINK_SHEET_SAVE), loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(), onClick = {
                val m = meaning
                error = meaningError(person != null || newName != null, m)
                if (error != null || m == null || saving) return@PrimaryButton
                saving = true
                scope.launch {
                    var text = ""
                    val err = failureOf {
                        val p = person ?: people.addPerson(newName.orEmpty())
                        text = linkedText(links.link(tg.transactionId, p.id, m), tg.currency)
                    }
                    saving = false
                    if (err != null) error = err else onLinked(PersonLinked(tg.transactionId, text))
                }
            })
        }
    }
}
