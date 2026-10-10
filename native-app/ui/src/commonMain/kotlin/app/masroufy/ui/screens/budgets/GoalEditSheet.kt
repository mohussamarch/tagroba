package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.DateParts
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.formatIsoDate
import app.masroufy.core.parseIsoDate
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.CalendarGrid
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «تعديل الخطة» (`GoalEditSheet` — جوه تفاصيل الخطة): الاسم · المبلغ · الموعد (شبكة التقويم المشتركة `CalendarGrid` — مفيش منتقي تاريخ
 * من النظام من غير Material) · الأرشفة · سطر «تحتاج X شهريًا» ([needLines]) ⇒ `ManageSavingsGoals.edit` و`archive`.
 * ⚠️ «بلا موعد» مش هنا (التاريخ لسه مطلوب في كوتلن — missingLogic).
 */
@Composable
fun GoalEditSheet(
    visible: Boolean,
    card: GoalCardUi,
    deps: BudgetsDeps,
    today: String,
    onDismiss: () -> Unit,
    onArchived: () -> Unit,
    refresh: () -> Unit,
) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val p = card.progress
    var d by remember(visible, card.id) { mutableStateOf(GoalEditDraft.from(p)) }
    var tried by remember(visible) { mutableStateOf(false) }
    var error by remember(visible) { mutableStateOf<String?>(null) }
    var picking by remember(visible) { mutableStateOf(false) }
    val title = t(UiKey.GOAL_EDIT_TITLE)
    Sheet(visible, onDismiss, title, spacing = 10.dp) {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(title, style = Type.of(17, FontWeight.Bold))
            val sub = when {
                p.savedMinor == null -> t(UiKey.GOAL_EDIT_SUB_NA)
                card.linked -> t(UiKey.GOAL_EDIT_SUB_LINKED, amountLabel(p.savedMinor, card.currency))
                else -> t(UiKey.GOAL_EDIT_SUB_SAVED, amountLabel(p.savedMinor, card.currency))
            }
            BasicText(sub, style = Type.of(13).copy(color = Ink.muted))
            TextInput(d.name, { d = d.copy(name = it.take(40)); error = null }, label = t(UiKey.GOAL_NEW_NAME))
            TextInput(
                d.targetText, { d = d.copy(targetText = it); error = null }, label = t(UiKey.GOAL_EDIT_TARGET), placeholder = "0.00", ltr = true,
                keyboard = KeyboardType.Decimal,
                trailing = { BasicText(currencySymbol(card.currency), Modifier.padding(end = 12.dp), style = Type.caption().copy(color = Ink.muted)) },
            )
            FieldLabel(t(UiKey.GOAL_NEW_WHEN))
            SecondaryButton(longDate(d.date), { picking = !picking }, Modifier.fillMaxWidth())
            if (picking) DatePicker(d.date, today) { picked -> d = d.copy(date = picked); picking = false; error = null }
            val (need, note) = needLines(d, p)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x0F08634F)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicText(need, style = Type.bodyBold().copy(color = Ink.primary))
                BasicText(note, style = Type.caption().copy(color = Ink.soft))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(t(UiKey.GOAL_DETAIL_ARCHIVE), style = Type.bodyBold())
                    BasicText(t(UiKey.GOAL_EDIT_ARCHIVE_NOTE), style = Type.caption().copy(color = Ink.muted))
                }
                Switch(d.archived, t(UiKey.GOAL_DETAIL_ARCHIVE), { d = d.copy(archived = !d.archived) })
            }
        }
        val check = checkGoalEdit(d, p, today)
        val shownError = error ?: when {
            !tried -> null
            check is GoalEditCheck.Unchanged -> t(UiKey.GOAL_EDIT_NOTHING_CHANGED)
            check is GoalEditCheck.Bad -> check.message
            else -> null
        }
        if (shownError != null) FieldError(shownError)
        val label = when {
            check is GoalEditCheck.Unchanged -> t(UiKey.GOAL_EDIT_NO_CHANGE)
            d.archived && !p.goal.archived -> t(UiKey.GOAL_EDIT_SAVE_ARCHIVE)
            else -> t(UiKey.GOAL_EDIT_SAVE)
        }
        PrimaryButton(label, {
            tried = true
            val ok = check as? GoalEditCheck.Ok ?: return@PrimaryButton
            scope.launch {
                error = attempt(t(UiKey.SHELL_LOAD_FAILED)) {
                    ok.change.input?.let { deps.goals.edit(p.goal.id, it) }
                    ok.change.archived?.let { deps.goals.archive(p.goal.id, it) }
                }
                if (error == null) {
                    val name = ok.change.input?.name ?: p.goal.name
                    val archived = ok.change.archived == true
                    toaster.show(t(if (archived) UiKey.GOAL_DETAIL_ARCHIVED_TOAST else UiKey.GOAL_EDIT_SAVED, name))
                    onDismiss()
                    if (archived) onArchived()
                    refresh()
                }
            }
        }, Modifier.fillMaxWidth(), enabled = check !is GoalEditCheck.Unchanged, height = 52.dp)
    }
}

/** اختيار يوم من شبكة الشهر (السبت أول الأسبوع) — النهارده وقبله باهتين والتحقق بيرفضهم («اختر موعدًا بعد اليوم»). */
@Composable
private fun DatePicker(selected: String, today: String, onPick: (String) -> Unit) {
    val start = parseIsoDate(selected)
    var year by remember { mutableStateOf(start.year) }
    var month by remember { mutableStateOf(start.month) }
    val sel = if (start.year == year && start.month == month) start.day else null
    CalendarGrid(
        year = year,
        month = month,
        today = today,
        marks = emptyList(),
        selectedDay = sel,
        onPick = { day -> onPick(formatIsoDate(DateParts(year, month, day))) },
        onMonth = { delta ->
            val total = year * 12 + (month - 1) + delta
            year = total.floorDiv(12)
            month = total.mod(12) + 1
        },
        onToday = {
            val t0 = parseIsoDate(today)
            year = t0.year
            month = t0.month
        },
    )
}
