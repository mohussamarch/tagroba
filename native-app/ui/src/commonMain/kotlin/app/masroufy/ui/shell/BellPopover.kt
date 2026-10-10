package app.masroufy.ui.shell

import app.masroufy.core.UiKey
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.BellItem
import app.masroufy.ui.app.BellState
import app.masroufy.ui.app.BellTone
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Anchor
import app.masroufy.ui.overlay.GlassPopover
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.screens.home.Dismissals
import app.masroufy.ui.screens.home.NotificationsRoute
import kotlinx.coroutines.delay
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion
import kotlinx.coroutines.launch

/**
 * الجرس = **نافذة طالعة منه مش صفحة** (OVERRIDES §74): زجاج سائل بآخر 4 سطور (عرض بس) · أي ضغطة برّاها بتقفلها · «كل الإشعارات» ⇒
 * الصفحة الكاملة (فيها الأفعال) · «تعليم الكل كمقروء» (النقطة الحمرا بتختفي). الستارة خفيفة جدًا من غير بلور، والجرس نفسه فوقها.
 * [state] من الرئيسية (عشان النقط على التبويبات تتحدّث من نفس القراية) — [onChanged] بعد «تعليم الكل».
 */
@Composable
fun BellPopover(state: BellState?, onChanged: () -> Unit, modifier: Modifier = Modifier) {
    val shell = LocalSpace.current.shell
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val anchor = remember { Anchor() }
    val unread = state?.unread ?: 0
    val label = if (unread > 0) t(UiKey.BELL_LABEL_NEW) else t(UiKey.BELL_TITLE)

    val bell = @Composable { selected: Boolean ->
        SurfaceIconButton(Lucide.BELL, label, onClick = { open = !open }, selected = selected, badge = { BellDot(unread > 0) })
    }
    Box(modifier.onGloballyPositioned { anchor.update(it) }) { bell(false) }

    GlassPopover(
        visible = open, onDismiss = { open = false }, anchor = anchor, title = t(UiKey.BELL_TITLE), closeLabel = t(UiKey.BELL_CLOSE),
        anchorContent = { bell(true) },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            BasicText(t(UiKey.BELL_TITLE), style = Type.of(16, FontWeight.Bold))
            BasicText(
                if (unread > 0) t(UiKey.BELL_NEW_COUNT, sentenceNumber(unread)) else t(UiKey.BELL_ALL_READ),
                style = Type.caption().copy(color = Ink.muted),
            )
        }
        // «×» على كل سطر + «تراجع» 4 ثواني (قرار المالك 2026-10-09) — نفس الحالة اللي في صفحة الإشعارات (`Dismissals` — للجلسة بس،
        // والحفظ مع الحساب وشيل نقطة التبويب منطقهم في فرع `assistant-engine`)
        val dismissals = Dismissals.of(LocalSpace.current.space.id)
        val undo = dismissals.undo
        LaunchedEffect(undo) {
            if (undo != null) {
                delay(Dismissals.UNDO_MS)
                dismissals.expire(undo)
            }
        }
        val items = state?.items.orEmpty().filter { !dismissals.isGone(it.threadKey) }.take(4)
        if (items.isEmpty()) BasicText(t(UiKey.BELL_EMPTY), Modifier.padding(8.dp), style = Type.of(13).copy(color = Ink.muted))
        for (item in items) BellRow(item) { dismissals.drop(item.threadKey) }
        if (undo != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(UiKey.NOTIFICATIONS_DROPPED), style = Type.of(13, FontWeight.Bold))
                TonalButton(t(UiKey.NOTIFICATIONS_UNDO), onClick = { dismissals.restore() }, height = 44.dp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val readShape = RoundedCornerShape(18.dp)
            TonalButton(
                if (unread > 0) t(UiKey.BELL_MARK_READ) else t(UiKey.BELL_ALL_READ),
                onClick = { scope.launch { shell.markAllRead(); onChanged() } },
                enabled = unread > 0,
                modifier = Modifier.weight(1f).clip(readShape).background(Color(0xCCFFFFFF)).insetRing(readShape, 1.dp, Color(0x2E08634F)),
            )
            PrimaryButton(t(UiKey.BELL_ALL), onClick = { open = false; nav.push(NotificationsRoute) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BoxScope.BellDot(visible: Boolean) {
    val a by animateFloatAsState(if (visible) 1f else 0f, motion(Springs.SNAPPY), label = "bell-dot")
    // مكانها في النموذج: top 12 · left 13 من زرار 48 (فيزيائي)
    Box(
        Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(13.dp, 12.dp)
            .graphicsLayer { alpha = a; scaleX = 0.4f + 0.6f * a; scaleY = 0.4f + 0.6f * a }
            .size(6.dp).clip(CircleShape).background(Ink.expense),
    )
}

private fun BellTone.color(): Color = when (this) {
    BellTone.PRIMARY -> Ink.primary
    BellTone.TRANSFER -> Ink.transfer
    BellTone.EXPENSE -> Ink.expense
    BellTone.ALERT -> Ink.focus
}

@Composable
private fun BellRow(item: BellItem, onDrop: () -> Unit) {
    val c = item.tone.color()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).alpha(if (item.unread) 1f else 0.72f).clip(shape)
            .background(if (item.unread) Color(0xBFFFFFFF) else Color.Transparent).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (item.unread) c else Color.Transparent).insetRing(CircleShape, 1.5.dp, c))
        Column(Modifier.weight(1f)) {
            BasicText(item.title, style = Type.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(item.subtitle, style = Type.caption().copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val press = rememberPress()
        val label = t(UiKey.NOTIFICATIONS_DROP, item.title)
        Box(
            Modifier.size(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).tap(press, label = label, onClick = onDrop),
            contentAlignment = Alignment.Center,
        ) { LucideIcon(Lucide.X, size = 16.dp, tint = Ink.muted, contentDescription = label) }
    }
}
