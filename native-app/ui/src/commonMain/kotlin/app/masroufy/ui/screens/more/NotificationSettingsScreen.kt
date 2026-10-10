package app.masroufy.ui.screens.more

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
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
import app.masroufy.core.AlertGroup
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** سطر مجموعة في إعدادات الإشعارات — [on] من المجموعات المقفولة، و[desc] بيتغيّر لرسائل البنك المقفولة (§74). */
data class NotifRow(val group: AlertGroup, val title: TextRef, val desc: TextRef, val on: Boolean, val isNew: Boolean = false)

/** الترتيب من OVERRIDES §74 (رسائل البنك أول مجموعة وعليها «جديد») + «ملفك» آخر حاجة (مجموعة في المحرك — SCREENS.md). */
private val ORDER = listOf(
    AlertGroup.BANK_SMS to (UiKey.NSET_BANK to UiKey.NSET_BANK_DESC),
    AlertGroup.DUES to (UiKey.NSET_DUES to UiKey.NSET_DUES_DESC),
    AlertGroup.BUDGET to (UiKey.NSET_BUDGET to UiKey.NSET_BUDGET_DESC),
    AlertGroup.QUESTIONS to (UiKey.NSET_ASK to UiKey.NSET_ASK_DESC),
    AlertGroup.INCOME to (UiKey.NSET_INCOME to UiKey.NSET_INCOME_DESC),
    AlertGroup.OCCASIONS to (UiKey.NSET_OCC to UiKey.NSET_OCC_DESC),
    AlertGroup.ZAKAT to (UiKey.NSET_ZAKAT to UiKey.NSET_ZAKAT_DESC),
    AlertGroup.BALANCE to (UiKey.NSET_BALANCE to UiKey.NSET_BALANCE_DESC),
    AlertGroup.ADVISOR to (UiKey.NSET_ADVISOR to UiKey.NSET_ADVISOR_DESC),
    AlertGroup.PROFILE to (UiKey.NSET_PROFILE to UiKey.NSET_PROFILE_DESC),
)

/** دالة نقية (بتتختبر على JVM): كل المجموعات شغالة ما عدا اللي المستخدم قفلها. «سؤال الكاش» مش هنا — مالوش مجموعة في المحرك (§74 ⚠️). */
fun notificationRows(disabled: Set<AlertGroup>): List<NotifRow> = ORDER.map { (group, keys) ->
    val on = group !in disabled
    val desc = if (group == AlertGroup.BANK_SMS && !on) UiKey.NSET_BANK_OFF_DESC else keys.second
    NotifRow(group, keys.first, desc, on, isNew = group == AlertGroup.BANK_SMS)
}

/**
 * إعدادات الإشعارات (`NotificationSettings` — من «المزيد» وصفحة الإشعارات): كل مجموعة بتتقفل لوحدها (`RunAlertEngine.setGroupEnabled`)،
 * والمقفولة بتفضل في صفحة الإشعارات بس. «سؤال الكاش» وتكراره نقطة ربط ([CashQuestionSetting]) — لسه مالوش منطق ⇒ «غير متاح بعد».
 * لافتة «إشعارات الجوال مغلقة» محتاجة قراية الإذن من غير ما نطلبه (`Permissions` فيه طلب بس) ⇒ مش ظاهرة لحد ما تتضاف.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsScreen() {
    val deps = LocalSpace.current
    val more = deps.more
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var disabled by remember(deps) { mutableStateOf<Set<AlertGroup>?>(null) }
    var cash by remember(deps) { mutableStateOf<CashQuestionEvery?>(null) }
    LaunchedEffect(deps) {
        disabled = runCatching { more.disabledAlertGroups() }.getOrDefault(emptySet())
        cash = runCatching { more.cashQuestion?.current() }.getOrNull()
    }
    InnerScaffold(t(UiKey.NSET_TITLE)) {
        item(key = "intro") { BasicText(t(UiKey.NSET_INTRO), Modifier.padding(horizontal = 4.dp), style = Type.of(13).copy(color = Ink.muted)) }
        val off = disabled
        if (off == null) {
            item(key = "sk") { Skeleton(Modifier.fillMaxWidth().height(420.dp)) }
            return@InnerScaffold
        }
        item(key = "groups") {
            GroupCard(horizontal = 16.dp) {
                val rows = notificationRows(off)
                rows.forEachIndexed { i, r ->
                    GroupSwitch(t(r.title), t(r.desc), r.on, r.isNew, last = i == rows.lastIndex) {
                        scope.launch {
                            val ok = runCatching { more.alerts.setGroupEnabled(r.group, !r.on) }.isSuccess
                            if (ok) disabled = if (r.on) off + r.group else off - r.group else toaster.show(t(UiKey.MORE_SAVE_FAILED), dark = true)
                        }
                    }
                    // «سؤال الكاش» بعد «الأسئلة» (ترتيب §74)
                    if (r.group == AlertGroup.QUESTIONS) CashQuestionRow(cash, more.cashQuestion != null) { every ->
                        scope.launch { runCatching { more.cashQuestion?.choose(every) }; cash = every }
                    }
                }
            }
        }
        item(key = "later") { NoteBox(t(UiKey.NSET_LATER_BODY), title = t(UiKey.NSET_LATER_TITLE)) }
        item(key = "lock") { BasicText(t(UiKey.NSET_LOCK_NOTE), Modifier.padding(horizontal = 4.dp), style = Type.caption().copy(color = Ink.muted)) }
    }
}

@Composable
private fun GroupSwitch(title: String, desc: String, on: Boolean, isNew: Boolean, last: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(title, style = Type.of(15, FontWeight.Bold))
                    if (isNew) Badge(t(UiKey.NSET_NEW), BadgeKind.INFO)
                }
                BasicText(desc, style = Type.caption().copy(color = Ink.muted))
            }
            ToggleSwitch(on, title, enabled, onToggle)
        }
        if (!last) RowRule()
    }
}

/** «سؤال الكاش»: مقفول افتراضيًا (قرار المالك §74)، ولما يشتغل: كل أسبوع / أسبوعين / شهر + «صباح الجمعة». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashQuestionRow(every: CashQuestionEvery?, editable: Boolean, onChoose: (CashQuestionEvery?) -> Unit) {
    Column {
        GroupSwitch(t(UiKey.NSET_CASH), t(UiKey.NSET_CASH_DESC), every != null, isNew = false, last = !editable, enabled = editable) {
            onChoose(if (every == null) CashQuestionEvery.WEEK else null)
        }
        if (!editable) {
            NotYetLine(t(UiKey.NSET_CASH_NOT_YET), Modifier.padding(bottom = 10.dp))
            RowRule()
        } else if (every != null) Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((value, key) in listOf(CashQuestionEvery.WEEK to UiKey.NSET_EVERY_WEEK, CashQuestionEvery.TWO_WEEKS to UiKey.NSET_EVERY_2WEEKS, CashQuestionEvery.MONTH to UiKey.NSET_EVERY_MONTH)) {
                    FillChip(t(key), every == value, height = 40.dp) { onChoose(value) }
                }
            }
            BasicText(t(UiKey.NSET_CASH_NOTE), style = Type.caption().copy(color = Ink.muted))
            RowRule()
        }
    }
}
