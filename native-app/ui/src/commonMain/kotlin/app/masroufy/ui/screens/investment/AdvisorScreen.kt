package app.masroufy.ui.screens.investment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.AlertGroup
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «التحليلات الذكية» (لوحة `Advisor`): كروت المساعد من صفحة الإشعارات (`RunAlertEngine.inbox` — مجموعة «المساعد المالي») بعنوانها
 * وشرحها وفعلها و«لماذا؟» · «المساعد ساكت الآن» · المفتاح الواحد اللي بيقفل المجموعة كلها (`setGroupEnabled`).
 */
@Composable
fun AdvisorScreen() {
    val space = LocalSpace.current
    val deps = space.investment
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var ui by remember(space) { mutableStateOf<AdvisorUi?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    suspend fun reload() {
        val enabled = runCatching { deps.advisorEnabled() }.getOrDefault(true)
        ui = advisorUi(runCatching { deps.alerts.inbox() }.getOrDefault(emptyList()), enabled)
    }
    LaunchedEffect(space) { reload() }
    val u = ui
    InnerScaffold(t(TextKey.ADVISOR_SCREEN_TITLE)) {
        item(key = "intro") { BasicText(t(TextKey.ADVISOR_SCREEN_INTRO), style = Type.of(13).copy(color = Ink.muted)) }
        when {
            u == null -> item(key = "loading") { Skeleton(Modifier.fillMaxWidth().height(180.dp)) }
            !u.enabled -> item(key = "off") {
                BasicText(t(TextKey.ADVISOR_SCREEN_OFF), Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.text.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 14.dp), style = Type.of(13).copy(color = Ink.soft))
            }
            u.quiet -> item(key = "quiet") { EmptyState(t(TextKey.ADVISOR_SCREEN_QUIET_TITLE), t(TextKey.ADVISOR_SCREEN_QUIET_BODY)) }
            else -> {
                item(key = "now") { BasicText(t(TextKey.ADVISOR_SCREEN_NOW), style = Type.section()) }
                for (c in u.cards) item(key = c.threadKey) {
                    AdvisorCardView(c, expanded = open == c.threadKey,
                        onWhy = {
                            open = if (open == c.threadKey) null else c.threadKey
                            scope.launch { runCatching { deps.alerts.opened(c.threadKey) } }
                        },
                        onAction = {
                            scope.launch { runCatching { deps.alerts.opened(c.threadKey) } }
                            when (c.link) {
                                AdvisorLink.GOALS -> nav.push(SavingsGoalsRoute)
                                // ⚠️ «الميزانيات» خانة جوه مبدّل «العمليات» (منطقة تانية) ومفيش طريقة نفتحها على خانة بعينها ⇒ تبويب العمليات
                                AdvisorLink.BUDGETS, AdvisorLink.OPS -> nav.switchTab(Tab.OPERATIONS)
                                null -> Unit
                            }
                        })
                }
            }
        }
        if (u != null) item(key = "group") {
            GroupSwitch(u.enabled) {
                scope.launch {
                    val turnOn = !u.enabled
                    runCatching { deps.alerts.setGroupEnabled(AlertGroup.ADVISOR, turnOn) }
                    open = null
                    reload()
                    toaster.show(t(if (turnOn) TextKey.ADVISOR_SCREEN_TURNED_ON else TextKey.ADVISOR_SCREEN_TURNED_OFF))
                }
            }
        }
    }
}

@Composable
private fun AdvisorCardView(c: AdvisorCard, expanded: Boolean, onWhy: () -> Unit, onAction: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                val (ink, bg) = if (c.good) Ink.primary to Ink.selected else Ink.focus to Ink.alertBg
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                    LucideIcon(iconFor(c.icon), size = 20.dp, tint = ink)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(c.title, style = Type.bodyBold())
                    BasicText(c.body, style = Type.of(13).copy(color = Ink.muted))
                    c.link?.let { link ->
                        val press = rememberPress()
                        Box(Modifier.heightIn(min = 40.dp).pressScale(press).tap(press, label = t(link.key), onClick = onAction), contentAlignment = Alignment.CenterStart) {
                            BasicText(t(link.key), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
                        }
                    }
                }
            }
            val press = rememberPress()
            Row(
                Modifier.height(36.dp).pressScale(press).clip(RoundedCornerShape(12.dp)).background(Ink.text.copy(alpha = 0.05f))
                    .tap(press, label = t(TextKey.ADVISOR_SCREEN_WHY), onClick = onWhy)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(t(TextKey.ADVISOR_SCREEN_WHY), style = Type.captionBold().copy(color = Ink.soft))
                BasicText(c.whenText, style = Type.of(11).copy(color = Ink.muted))
            }
            if (expanded) {
                BasicText(c.why, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.text.copy(alpha = 0.04f))
                    .padding(horizontal = 12.dp, vertical = 10.dp), style = Type.captionBold())
            }
        }
    }
}

private fun iconFor(i: AdvisorIcon): Lucide = when (i) {
    AdvisorIcon.TREND -> Lucide.TRENDING_UP
    AdvisorIcon.ALERT -> Lucide.CIRCLE_ALERT
    AdvisorIcon.PIGGY -> Lucide.PIGGY_BANK
    AdvisorIcon.RECEIPT -> Lucide.RECEIPT
    AdvisorIcon.CALENDAR -> Lucide.CALENDAR
}

/** «المساعد المالي» — مفتاح واحد بيقفل تنبيهاته كلها (مسار 44×26 والمقبض 22 — زي «إعدادات الإشعارات»). */
@Composable
private fun GroupSwitch(on: Boolean, onToggle: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        val press = rememberPress()
        Row(
            Modifier.fillMaxWidth().tap(press, role = Role.Switch, label = t(TextKey.ADVISOR_SCREEN_GROUP), onClick = onToggle).semantics { toggleableState = ToggleableState(on) },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(t(TextKey.ADVISOR_SCREEN_GROUP), style = Type.of(15, FontWeight.Bold))
                BasicText(t(TextKey.ADVISOR_SCREEN_GROUP_DESC), style = Type.caption().copy(color = Ink.muted))
            }
            Box(
                Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(if (on) Ink.primary else Ink.text.copy(alpha = 0.16f)),
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(2.dp).size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color.White))
            }
        }
    }
}
