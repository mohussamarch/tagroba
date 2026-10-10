package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalBell
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Navigator
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.imports.BankSmsRoute
import app.masroufy.ui.screens.more.MoreRoute
import app.masroufy.ui.screens.more.NotificationSettingsRoute
import app.masroufy.ui.screens.onboarding.ProfileQuestionRoute
import app.masroufy.ui.screens.operations.ReviewQueueRoute
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.AlertInboxView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * صفحة الإشعارات الكاملة (من «كل الإشعارات» في نافذة الجرس): القسمين · «×» على كل سطر بـ«تراجع» ٤ ثواني · صندوق «هكذا يظهر على شاشة القفل» ·
 * ترس ⇒ إعدادات الإشعارات. الضغط على السطر = «اتفتح» (`RunAlertEngine.opened` — المحرك بيتعلم إنك بتفتح النوع ده) ثم الشاشة المرتبطة.
 */
@Composable
fun NotificationsScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val bell = LocalBell.current
    val scope = rememberCoroutineScope()
    val dismissals = Dismissals.of(deps.space.id)
    var inbox by remember(deps) { mutableStateOf<List<AlertInboxView>?>(null) }
    var failed by remember(deps) { mutableStateOf(false) }
    LaunchedEffect(deps) {
        runCatching { deps.home.alerts.inbox() }.onSuccess { inbox = it }.onFailure { failed = true }
    }
    val undo = dismissals.undo
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(Dismissals.UNDO_MS)
            dismissals.expire(undo)
        }
    }
    val unread = bell.state?.items.orEmpty().filter { it.unread }.map { it.threadKey }.toSet()
    val gone = dismissals.goneKeys
    val today = deps.shell.today()
    Box(Modifier.fillMaxSize()) {
        InnerScaffold(
            t(UiKey.NOTIFICATIONS_TITLE),
            actions = { SurfaceIconButton(Lucide.SETTINGS, t(UiKey.NOTIFICATIONS_SETTINGS), { nav.push(NotificationSettingsRoute) }) },
        ) {
            val list = inbox
            when {
                failed -> item(key = "failed") { EmptyState(t(UiKey.SHELL_LOAD_FAILED)) }
                list == null -> item(key = "loading") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Skeleton(Modifier.fillMaxWidth().height(180.dp))
                        Skeleton(Modifier.fillMaxWidth().height(120.dp))
                    }
                }
                else -> {
                    val sections = notificationSections(list, unread, gone, today)
                    if (sections.isEmpty()) item(key = "empty") { EmptyState(t(UiKey.NOTIFICATIONS_EMPTY_TITLE), t(UiKey.NOTIFICATIONS_EMPTY_BODY)) }
                    for (sec in sections) item(key = "sec-${sec.title}") {
                        Section(sec, onOpen = { row ->
                            scope.launch {
                                runCatching { deps.home.alerts.opened(row.threadKey) }
                                bell.refresh()
                            }
                            openTarget(nav, row.target)
                        }, onDrop = { row -> scope.launch { runCatching { dismissals.dropAndSave(row.threadKey, deps.shell) }; bell.refresh() } })
                    }
                    lockPreviewOf(list, gone)?.let { notice -> item(key = "lock") { LockPreview(notice.title, notice.body) } }
                }
            }
        }
        if (undo != null) UndoBar(Modifier.align(Alignment.BottomCenter)) { scope.launch { runCatching { dismissals.undoAndSave(deps.shell) }; bell.refresh() } }
    }
}

internal fun openTarget(nav: Navigator, target: NotifTarget) {
    when (target) {
        NotifTarget.BANK_SMS -> nav.push(BankSmsRoute)
        NotifTarget.REVIEW -> nav.push(ReviewQueueRoute)
        NotifTarget.OPERATIONS -> nav.switchTab(Tab.OPERATIONS)
        NotifTarget.PEOPLE -> nav.switchTab(Tab.PEOPLE)
        NotifTarget.INVESTMENT -> nav.switchTab(Tab.INVESTMENT)
        NotifTarget.PROFILE -> nav.push(ProfileQuestionRoute)
        NotifTarget.MORE -> nav.push(MoreRoute)
    }
}

@Composable
private fun Section(sec: NotifSection, onOpen: (NotifRow) -> Unit, onDrop: (NotifRow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(sec.title, style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            sec.rows.forEachIndexed { i, row ->
                NotifRowView(row, onOpen, onDrop)
                if (i < sec.rows.lastIndex) Divider()
            }
        }
    }
}

@Composable
private fun NotifRowView(row: NotifRow, onOpen: (NotifRow) -> Unit, onDrop: (NotifRow) -> Unit) {
    val press = rememberPress()
    Row(verticalAlignment = Alignment.Top) {
        Row(
            Modifier.weight(1f).pressScale(press).tap(press, onClick = { onOpen(row) }).padding(top = 12.dp, bottom = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box {
                app.masroufy.ui.components.IconTile(row.icon.color) { LucideIcon(row.icon.icon, size = 20.dp, tint = row.icon.color) }
                if (row.unread) {
                    Box(
                        Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset((-3).dp, (-3).dp).size(10.dp).clip(CircleShape)
                            .background(Ink.primary).insetRing(CircleShape, 2.dp, Color.White),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(row.title, Modifier.weight(1f), style = Type.bodyBold())
                    BasicText(row.whenText, style = Type.of(11).copy(color = Ink.muted))
                }
                BasicText(row.body, style = Type.of(13))
                BasicText(row.why, style = Type.of(11).copy(color = Ink.muted))
                if (row.muted || row.spaceLabel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (row.muted) Badge(t(UiKey.NOTIFICATIONS_MUTED), BadgeKind.NOT_AVAILABLE)
                        row.spaceLabel?.let { Badge(it, BadgeKind.INFO) }
                    }
                }
            }
        }
        val dropPress = rememberPress()
        Box(
            Modifier.padding(top = 8.dp).size(44.dp).pressScale(dropPress).clip(RoundedCornerShape(14.dp))
                .tap(dropPress, label = t(UiKey.NOTIFICATIONS_DROP, row.title), onClick = { onDrop(row) }),
            contentAlignment = Alignment.Center,
        ) { LucideIcon(Lucide.X, size = 18.dp, tint = Ink.muted, contentDescription = t(UiKey.NOTIFICATIONS_DROP, row.title)) }
    }
}

/** «هكذا يظهر على شاشة القفل»: شريط داكن فيه «م» ونص الشريط العام — من غير مبالغ ولا أسامي ولا عدد. */
@Composable
private fun LockPreview(title: String, body: String) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.NOTIFICATIONS_LOCK_TITLE), style = Type.captionBold().copy(color = Ink.muted))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xD1193D33)).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Ink.primary), contentAlignment = Alignment.Center) {
                BasicText(t(UiKey.NOTIFICATIONS_LOCK_LETTER), style = Type.of(14, FontWeight.Bold).copy(color = Color.White))
            }
            Column(Modifier.weight(1f)) {
                BasicText(title, style = Type.captionBold().copy(color = Color.White))
                BasicText(body, style = Type.of(13).copy(color = Color.White))
            }
            BasicText(t(UiKey.NOTIFICATIONS_LOCK_NOW), style = Type.of(11).copy(color = Ink.onHeroMuted))
        }
        BasicText(t(UiKey.NOTIFICATIONS_LOCK_NOTE), style = Type.caption().copy(color = Ink.muted))
    }
}

/** «حُذف الإشعار — تراجع» (٤ ثواني) — نفس شريط النموذج الداكن تحت. */
@Composable
internal fun UndoBar(modifier: Modifier = Modifier, onUndo: () -> Unit) {
    Row(
        modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color(0xEB193D33)).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(t(UiKey.NOTIFICATIONS_DROPPED), Modifier.weight(1f), style = Type.of(13, FontWeight.Bold).copy(color = Color.White))
        val press = rememberPress()
        Box(
            Modifier.height(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF))
                .tap(press, onClick = onUndo).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText(t(UiKey.NOTIFICATIONS_UNDO), style = Type.of(13, FontWeight.Bold).copy(color = Ink.selected)) }
    }
}
