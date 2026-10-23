package app.masroufy.ui.screens.people

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.PERSON_BADGES
import app.masroufy.core.PERSON_HERO_COLOR_COUNT
import app.masroufy.core.R06Key
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.PersonInk
import app.masroufy.ui.theme.Type

/**
 * دايرة الشخص (`PersonHero.dc.html`): صورته المحفوظة على الجهاز ([LocalPersonPhotos]) أو **رمز شخص** على أخضر فاتح — مش أول حرف (§115-2) ·
 * حلقة 5 بلون الخلفية · الرمز المختار فوق يمين (36) · «ضيف صورة» تحت شمال (34) بيفتح اختيار الصور بتاع أندرويد.
 */
@Composable
internal fun PersonAvatar(personId: String, badge: String?, size: Dp) {
    val photos = LocalPersonPhotos.current
    val toaster = LocalToaster.current
    val version = PersonPhotoChanges.version
    val photo = remember(personId, version) { runCatching { photos.load(personId) }.getOrNull() }
    Box(Modifier.size(size + 10.dp)) {
        Box(Modifier.align(Alignment.Center).size(size + 10.dp).clip(CircleShape).background(PersonInk.ring), contentAlignment = Alignment.Center) {
            Box(Modifier.size(size).clip(CircleShape).background(Brush.verticalGradient(listOf(PersonInk.avatarTop, PersonInk.avatarBottom))), contentAlignment = Alignment.BottomCenter) {
                if (photo != null) Image(photo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else PersonFigure(Modifier.size(size * 108 / 132))
            }
        }
        badge?.let {
            Box(
                Modifier.align(AbsoluteAlignment.TopRight).size(36.dp).border(3.dp, PersonInk.ring, CircleShape).clip(CircleShape).background(Ink.surface),
                contentAlignment = Alignment.Center,
            ) { BasicText(it, style = Type.of(18)) }
        }
        val press = rememberPress()
        Box(
            Modifier.align(AbsoluteAlignment.BottomLeft).padding(4.dp).size(34.dp).pressScale(press).border(3.dp, PersonInk.ring, CircleShape).clip(CircleShape).background(Ink.primary)
                .tap(press, label = t(R06Key.PH_PHOTO), onClick = {
                    if (!photos.available) toaster.show(t(R06Key.PH_NO_PHOTO_PICKER))
                    else photos.pick(personId) { saved -> if (saved) PersonPhotoChanges.bump() }
                }),
            contentAlignment = Alignment.Center,
        ) { app.masroufy.ui.icons.LucideIcon(PeopleIcons.CAMERA, size = 16.dp, tint = Ink.white(0xFF)) }
    }
}

/** رمز الشخص المليان (viewBox 24: راس r4.2 عند 12،8.2 وجسم من 3.5 لـ20.5). */
@Composable
private fun PersonFigure(modifier: Modifier) {
    val ink = PersonInk.avatarFigure
    Canvas(modifier) {
        val s = this.size.width / 24f
        drawCircle(ink, radius = 4.2f * s, center = Offset(12f * s, 8.2f * s))
        val body = Path().apply {
            moveTo(3.5f * s, 24f * s)
            cubicTo(3.5f * s, 19f * s, 7.3f * s, 15.4f * s, 12f * s, 15.4f * s)
            cubicTo(16.7f * s, 15.4f * s, 20.5f * s, 19f * s, 20.5f * s, 24f * s)
            close()
        }
        drawPath(body, ink)
    }
}

/** لوحة «خصّص» البيضا مكان الكروت: «لون الخلفية» (5) · «رمز على الصورة» (8 — الضغطة التانية بتشيله) · «تمام». كل اختيار بيتحفظ على طول. */
@Composable
internal fun LookPanel(name: String, look: PersonLookUi, top: Dp, onLook: (Int, String?) -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 72.dp + top).clip(RoundedCornerShape(24.dp)).background(PersonInk.editPanel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(R06Key.PH_CUSTOMIZE_TITLE, name), Modifier.weight(1f), style = Type.of(16, FontWeight.Bold))
            val press = rememberPress()
            Box(
                Modifier.height(36.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(Glass.heroBase).tap(press, label = t(R06Key.PH_DONE), onClick = onDone).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(t(R06Key.PH_DONE), style = Type.of(14, FontWeight.Bold).copy(color = Ink.white(0xFF))) }
        }
        BasicText(t(R06Key.PH_COLOR), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0 until PERSON_HERO_COLOR_COUNT) {
                val press = rememberPress()
                val on = look.heroColor == i
                Box(
                    Modifier.size(48.dp).then(if (on) Modifier.border(2.dp, PersonInk.heroColors[i].second, CircleShape) else Modifier).padding(2.dp)
                        .then(if (on) Modifier.border(2.dp, Ink.white(0xFF), CircleShape) else Modifier).clip(CircleShape).background(personHeroBrush(i))
                        .pressScale(press).tap(press, label = t(R06Key.PH_COLOR_N, sentenceNumber(i + 1)), onClick = { onLook(i, look.badge) }),
                )
            }
        }
        BasicText(t(R06Key.PH_BADGE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (b in PERSON_BADGES) {
                val press = rememberPress()
                val on = look.badge == b
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Ink.tintOf(Ink.primary, 0x24) else Ink.selected)
                        .then(if (on) Modifier.border(2.dp, Ink.primary, RoundedCornerShape(12.dp)) else Modifier)
                        .pressScale(press).tap(press, label = t(R06Key.PH_BADGE_PUT, b), onClick = { onLook(look.heroColor, if (on) null else b) }),
                    contentAlignment = Alignment.Center,
                ) { BasicText(b, style = Type.of(18)) }
            }
        }
    }
}
