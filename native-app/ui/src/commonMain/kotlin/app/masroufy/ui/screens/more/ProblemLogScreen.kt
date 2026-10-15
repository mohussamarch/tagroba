package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.ProblemEntry
import app.masroufy.core.ProblemLevel
import app.masroufy.core.TextKey
import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.core.uiText
import app.masroufy.ui.app.LocalApp
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Route
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** «سجل المشاكل» (OVERRIDES §90) — من «القفل واللغة» (`AppSettings`). */
object ProblemLogRoute : Route {
    override val name = "ProblemLog"
}

/** سطر للعرض: نص الكود المقروء · الدرجة · الوقت («15 أكتوبر، 04:20» بوقت الجوال). */
data class ProblemRowView(val label: TextRef, val level: TextRef, val badge: BadgeKind, val time: String)

/** دالة نقية (JVM): الوقت من أول 16 حرف من ISO — مش مفهوم ⇒ «غير متاح» (القاعدة 10). */
fun problemRow(e: ProblemEntry): ProblemRowView {
    val level = when (e.level) {
        ProblemLevel.INFO -> UiKey.PLOG_LEVEL_INFO
        ProblemLevel.WARN -> UiKey.PLOG_LEVEL_WARN
        ProblemLevel.ERROR -> UiKey.PLOG_LEVEL_ERROR
    }
    val badge = if (e.level == ProblemLevel.INFO) BadgeKind.INFO else BadgeKind.APPROX
    val time = runCatching { "${dayMonth(e.at.take(10))}، ${e.at.substring(11, 16)}" }.getOrElse { uiText(TextKey.NOT_AVAILABLE) }
    return ProblemRowView(e.code.label, level, badge, time)
}

/** القسم في «القفل واللغة»: مفتاح «ابعت تقارير الأعطال» + الدخول لـ«سجل المشاكل». */
@Composable
internal fun ProblemsSection() {
    val crash = LocalApp.current.problems?.crashReports
    val nav = LocalNavigator.current
    val available = crash?.available == true
    var on by remember(crash) { mutableStateOf(crash?.isOn() == true) }
    val desc = when {
        !available -> UiKey.PLOG_CRASH_NA
        on -> UiKey.PLOG_CRASH_ON
        else -> UiKey.PLOG_CRASH_OFF
    }
    Section(t(UiKey.PLOG_HEAD)) {
        SwitchHeader(Lucide.TRIANGLE_ALERT, t(UiKey.PLOG_CRASH_TITLE), t(desc), on, enabled = available) {
            val c = crash ?: return@SwitchHeader
            c.setOn(!on)
            on = c.isOn()
        }
        RowRule()
        MenuRow(Lucide.FILE_TEXT, t(UiKey.PLOG_TITLE), t(UiKey.PLOG_OPEN_HINT), last = true, onClick = { nav.push(ProblemLogRoute) })
    }
}

/**
 * «سجل المشاكل»: الأحدث فوق، بنص مقروء لكل كود · «ابعته» = نافذة المشاركة بتاعة الجوال بالنص المصدَّر (`ExportProblems`) ·
 * «امسح السجل» بتأكيد جوه الصفحة نفسها. السجل مش موجود في النسخة دي ⇒ «غير متاح» (مش قايمة فاضية).
 */
@Composable
fun ProblemLogScreen() {
    val deps = LocalApp.current.problems
    val toaster = LocalToaster.current
    val share = rememberTextSharer()
    var entries by remember { mutableStateOf<List<ProblemEntry>?>(null) }
    var asking by remember { mutableStateOf(false) }
    LaunchedEffect(deps) {
        entries = deps?.let { d -> withContext(Dispatchers.Default) { runCatching { d.list.newestFirst() }.getOrNull() } }
    }
    InnerScaffold(t(UiKey.PLOG_TITLE)) {
        item(key = "intro") { BasicText(t(UiKey.PLOG_INTRO), Modifier.padding(horizontal = 4.dp), style = Type.of(13).copy(color = Ink.muted)) }
        if (deps == null) {
            item(key = "na") { WarnBox(null, t(UiKey.PLOG_NA)) }
            return@InnerScaffold
        }
        val list = entries
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(t(UiKey.PLOG_SEND), onClick = {
                    val sent = share?.invoke(deps.export.text()) == true
                    if (!sent) toaster.show(t(UiKey.PLOG_SEND_NA), dark = true)
                }, modifier = Modifier.fillMaxWidth(), enabled = share != null && !list.isNullOrEmpty(), leading = Lucide.UPLOAD)
                if (share == null) NotYetLine(t(UiKey.PLOG_SEND_NA))
                if (!asking) TonalButton(t(UiKey.PLOG_CLEAR), onClick = { asking = true }, modifier = Modifier.fillMaxWidth(), enabled = !list.isNullOrEmpty(), muted = true)
                else WarnBox(null, t(UiKey.PLOG_CLEAR_ASK)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TonalButton(t(UiKey.PLOG_CLEAR_YES), onClick = {
                            deps.clear.clear()
                            entries = emptyList()
                            asking = false
                            toaster.show(t(UiKey.PLOG_CLEARED), dark = true)
                        }, modifier = Modifier.weight(1f), height = 44.dp)
                        TonalButton(t(UiKey.MORE_CANCEL), onClick = { asking = false }, modifier = Modifier.weight(1f), height = 44.dp, muted = true)
                    }
                }
            }
        }
        when {
            list == null -> Unit
            list.isEmpty() -> item(key = "empty") { BasicText(t(UiKey.PLOG_EMPTY), Modifier.padding(horizontal = 4.dp), style = Type.of(14).copy(color = Ink.muted)) }
            else -> item(key = "rows") {
                GroupCard(horizontal = 16.dp) {
                    list.forEachIndexed { i, e -> ProblemRow(problemRow(e), last = i == list.lastIndex) }
                }
            }
        }
    }
}

@Composable
private fun ProblemRow(row: ProblemRowView, last: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(t(row.label), style = Type.of(14, FontWeight.Medium))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(t(row.level), row.badge)
            BasicText(row.time, style = Type.caption().copy(color = Ink.muted))
        }
    }
    if (!last) RowRule()
}
