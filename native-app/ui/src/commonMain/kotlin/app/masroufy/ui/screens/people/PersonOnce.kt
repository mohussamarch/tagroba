package app.masroufy.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.core.parseIsoDate
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.CalendarGrid
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.PersonInk
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.UpcomingOccasion

private fun inkOf(wedding: Boolean): Pair<Color, Color> = if (wedding) PersonInk.weddingInk to PersonInk.weddingBg else Ink.transfer to Ink.tintOf(Ink.transfer, 0x1A)

/** التنبيه (`PersonProfile.dc.html`): مناسبة مرة واحدة قرّبت — بعد كروت «لك/عليك» دايمًا فما بيزقهاش (§115-1). الدوسة بتفتح التقويم. */
@Composable
internal fun OccasionNoticeCard(n: OccNotice, modifier: Modifier, onClick: () -> Unit) {
    val press = rememberPress()
    val (ink, bg) = inkOf(n.wedding)
    Row(
        modifier.fillMaxWidth().pressScale(press).clip(RoundedCornerShape(20.dp)).background(bg).tap(press, label = n.title, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ink.surface.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { LucideIcon(n.glyph.icon, size = 20.dp, tint = ink) }
        Column(Modifier.weight(1f)) {
            BasicText(n.title, style = Type.of(14, FontWeight.Bold))
            BasicText(n.sub, style = Type.caption().copy(color = Ink.muted))
        }
    }
}

/** شريط «مناسبات مرة واحدة» ومربع التقويم على **الشمال** (آخر عنصر في الصف العربي). أي دوسة بتفتح التقويم. */
@Composable
internal fun OnceStrip(once: List<OccCard>, modifier: Modifier, openCalendar: () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(R06Key.PH_ONCE_TITLE), style = Type.of(15, FontWeight.Bold).copy(color = Ink.muted))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (once.isEmpty()) BasicText(t(R06Key.PH_ONCE_EMPTY), Modifier.padding(vertical = 14.dp), style = Type.body().copy(color = Ink.muted))
                for (o in once) {
                    FloatingCard(shape = RoundedCornerShape(16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp), onClick = openCalendar, clickLabel = o.title) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OccTile(o)
                            Column {
                                BasicText(o.title, style = Type.of(13, FontWeight.Bold))
                                BasicText(o.date, style = Type.of(11).copy(color = Ink.muted))
                            }
                        }
                    }
                }
            }
            val press = rememberPress()
            Box(
                Modifier.width(56.dp).fillMaxHeight().heightIn(min = 56.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(Ink.tintOf(Ink.primary, 0x1A))
                    .tap(press, label = t(R06Key.PH_CAL), onClick = openCalendar),
                contentAlignment = Alignment.Center,
            ) { LucideIcon(PeopleIcons.CAL_PLUS, size = 22.dp, tint = Ink.primary) }
        }
    }
}

@Composable
private fun OccTile(o: OccCard) {
    val (ink, bg) = inkOf(o.wedding)
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) { LucideIcon(o.glyph.icon, size = 16.dp, tint = ink) }
}

/**
 * لوحة «مناسبات {الاسم}»: شبكة الشهر بتاعة التقويم (`CalendarGrid` — نفس شكل التقويم في التطبيق) بأيام المناسبات معلّمة · قايمة مناسبات المرة
 * الواحدة · «ضيف مناسبة» ⇒ لوحة المناسبة الموجودة ([OccasionCards] نفسها — نفس حالات الاستخدام `ManageOccasions`).
 */
@Composable
internal fun OccasionCalendarSheet(visible: Boolean, personId: String, name: String, items: List<UpcomingOccasion>, once: List<OccCard>, today: String, onDismiss: () -> Unit) {
    val now = parseIsoDate(today)
    var ym by remember(visible) { mutableStateOf(now.year to now.month) }
    var picked by remember(visible) { mutableStateOf<Int?>(null) }
    Sheet(visible, onDismiss = onDismiss, title = t(R06Key.PH_CAL_TITLE, name), veil = app.masroufy.ui.overlay.Veil.NORMAL) {
        if (!visible) return@Sheet
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(t(R06Key.PH_CAL_TITLE, name), style = Type.section())
            CalendarGrid(
                ym.first, ym.second, today, occasionMarks(items, ym.first, ym.second), picked, { picked = it },
                onMonth = { d ->
                    val m = ym.second + d
                    ym = when { m < 1 -> (ym.first - 1) to 12; m > 12 -> (ym.first + 1) to 1; else -> ym.first to m }
                    picked = null
                },
                onToday = { ym = now.year to now.month; picked = null },
            )
            for (o in once) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OccTile(o)
                BasicText(o.title, Modifier.weight(1f), style = Type.of(14, FontWeight.Bold))
                BasicText(o.date, style = Type.caption().copy(color = Ink.muted))
            }
            // «ضيف مناسبة» وتعديل الموجود: نفس الكروت واللوحة الموجودين (من غير ما نعيد الحفظ)
            OccasionCards(personId, name, items)
        }
    }
}
