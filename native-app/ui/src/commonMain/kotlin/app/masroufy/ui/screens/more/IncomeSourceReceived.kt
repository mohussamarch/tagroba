package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.SourceIncome
import app.masroufy.core.monthYear
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.EmployerView
import kotlinx.coroutines.launch

/**
 * §120 (التصميم — صفحة مصدر الدخل، رد المالك «الاتنين»): سطر «وصل كام من المتوقع الشهر ده» + قايمة اللي وصل شهر بشهر. كل الأرقام من
 * `IncomeSourceSignals.received` (الحساب في `core/SourceIncome.kt`) — الشاشة بتعرض بس. ما اتحمّلش ⇒ «غير متاح» (مش صفر).
 */
@Composable
internal fun ReceivedSection(sourceId: Id, today: String, refresh: Int) {
    val more = LocalSpace.current.more
    var data by remember(sourceId) { mutableStateOf<SourceIncome?>(null) }
    var loaded by remember(sourceId) { mutableStateOf(false) }
    LaunchedEffect(sourceId, refresh) {
        data = runCatching { more.incomeSignals.received(sourceId, today) }.getOrNull()
        loaded = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(R06Key.C120_INC_MONTHS), Modifier.padding(horizontal = 4.dp), style = Type.of(15, FontWeight.Bold))
        val d = data
        if (d == null) {
            if (loaded) NotYetLine(t(app.masroufy.core.TextKey.NOT_AVAILABLE))
            return@Column
        }
        d.thisMonth?.let { m ->
            val got = amountLabel(m.receivedMinor, d.currency)
            val line = d.expectedMinor?.let { t(R06Key.C120_INC_THIS_MONTH, got, amountLabel(it, d.currency)) } ?: t(R06Key.C120_INC_THIS_MONTH_NA, got)
            BasicText(line, Modifier.padding(horizontal = 4.dp), style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary))
        }
        GroupCard(horizontal = 16.dp) {
            d.months.forEachIndexed { i, m ->
                val (y, mo) = m.period.key.split('-').let { it[0].toInt() to it[1].toInt() }
                val value = if (m.count == 0) t(R06Key.C120_INC_NONE) else amountLabel(m.receivedMinor, d.currency)
                FactRow(monthYear(y, mo), value, last = i == d.months.lastIndex, muted = m.count == 0)
            }
        }
        // §121-9: اللي نوعها لسه ما اتحددش في سطر لوحده — مش مع اللي وصل
        if (d.waitingCount > 0) GroupCard(horizontal = 16.dp) {
            FactRow(t(R06Key.C120_INC_WAITING, sentenceNumber(d.waitingCount)), amountLabel(d.waitingMinor, d.currency), last = true, muted = true)
        }
    }
}

/**
 * §120-7 «ده مرتب دايمًا من الحساب ده» لكل حساب محوِّل للجهة (`ManageEmployers.setAlwaysSalary`). مقفول ⇒ القرار الأحدث (المبلغ والميعاد
 * قريبين). مفيش حسابات أو مش جهة شغل ⇒ مفيش كارت.
 */
@Composable
internal fun AlwaysSalaryCard(sourceId: Id, onChanged: () -> Unit) {
    val employers = LocalSpace.current.more.employers ?: return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var view by remember(sourceId) { mutableStateOf<EmployerView?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(sourceId, version) { view = runCatching { employers.list().firstOrNull { it.source.id == sourceId } }.getOrNull() }
    val v = view ?: return
    if (v.accounts.isEmpty()) return
    GroupCard(horizontal = 16.dp) {
        v.accounts.forEachIndexed { i, acc ->
            val on = acc.key in v.source.alwaysSalaryKeys
            val name = acc.last4?.let { "••••$it" } ?: acc.label
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(t(R06Key.C120_ALWAYS_SALARY), style = Type.of(14, FontWeight.Bold))
                        BasicText(if (on) t(R06Key.C120_ALWAYS_SALARY_ON, name) else t(R06Key.C120_ALWAYS_SALARY_OFF), style = Type.caption().copy(color = Ink.muted))
                    }
                    ToggleSwitch(on, t(R06Key.C120_ALWAYS_SALARY)) {
                        scope.launch {
                            val err = runCatching { employers.setAlwaysSalary(sourceId, acc.key, !on) }.exceptionOrNull()
                            toaster.show(err?.message ?: t(R06Key.C120_ALWAYS_SALARY_SAVED))
                            version++
                            onChanged()
                        }
                    }
                }
                if (i != v.accounts.lastIndex) RowRule()
            }
        }
    }
}
