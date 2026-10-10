package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.masroufy.core.TextKey
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.LensOnHero
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import kotlin.math.hypot
import kotlin.math.min

/**
 * المسرح البترولي في «الأشخاص» (لوحة `People`): أقرب 5 فوق «أنت» بخيوط ملتوية من راسك لكل واحد (بتترسم أول ما الشاشة تفتح)،
 * و«أنت» تحت في النص (دايرتك + مكان الكاركتر — §73: الكاركتر لسه بيتصمم). الأماكن من النموذج بعرض 350 وبتتمدّ مع عرض الشاشة.
 */
private val SPOTS = listOf(298f to 184f, 250f to 112f, 175f to 82f, 100f to 112f, 52f to 184f)
private const val HEAD_X = 175f
private const val HEAD_Y = 196f
private const val STAGE_W = 350f
internal const val STAGE_H = 360

@Composable
internal fun PeopleStage(orbit: List<PersonChip>, meName: String?, onOpen: (PersonChip) -> Unit, modifier: Modifier = Modifier) {
    HeroCard(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(0.dp)) {
            val w = maxWidth
            val k = w.value / STAGE_W
            Box(Modifier.fillMaxWidth().size(width = w, height = STAGE_H.dp)) {
                Threads(orbit.size)
                BasicText(
                    t(UiKey.PEOPLE_ORBIT_NOTE),
                    Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp),
                    style = Type.caption().copy(color = Ink.onHeroMuted),
                )
                // الأماكن في النموذج من الشمال (left)؛ الإزاحة هنا من بداية السطر ⇒ في العربي نفس مكان النموذج، وفي الإنجليزي بالمراية
                orbit.take(SPOTS.size).forEachIndexed { i, p ->
                    val (x, y) = SPOTS[i]
                    val start = w.value - (x * k - 32f) - 64f
                    OrbitPerson(p, { onOpen(p) }, Modifier.offset(x = start.dp, y = (y - 48f).dp).zIndex(1f))
                }
                You(meName, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun OrbitPerson(p: PersonChip, onClick: () -> Unit, modifier: Modifier) {
    val press = rememberPress()
    Column(
        modifier.width(64.dp).pressScale(press).tap(press, label = p.name, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(p.name, style = Type.of(12, FontWeight.Bold).copy(color = Ink.onPrimary, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
        InitialCircle(p.name, 56.dp, onHero = true)
    }
}

/** «أنت»: عدسة 96 بأول حرف من اسمك + القوس تحته فيه مكان الكاركتر. */
@Composable
private fun You(meName: String?, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        LensOnHero(Modifier.size(96.dp).zIndex(1f), shape = CircleShape) {
            BasicText(meName?.let { initialOf(it) }?.ifEmpty { null } ?: t(UiKey.PEOPLE_YOU).take(1), Modifier.align(Alignment.Center), style = Type.of(34, FontWeight.Bold).copy(color = Ink.onPrimary))
        }
        LensOnHero(
            Modifier.offset(y = (-8).dp).size(width = 220.dp, height = 96.dp),
            shape = RoundedCornerShape(topStart = 110.dp, topEnd = 110.dp),
        ) {
            BasicText(
                t(UiKey.PEOPLE_CHARACTER_SLOT),
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Ink.heroStart.copy(alpha = 0.28f)).padding(horizontal = 10.dp, vertical = 2.dp),
                style = Type.of(11).copy(color = Ink.onHeroMuted),
            )
        }
    }
}

/** الخيوط من راسك لكل شخص فوق: منحنى بنقطتين تحكم على الجنبين بالتبادل، وبيترسم بالنابض الهادي («تقليل الحركة» ⇒ مرسوم على طول). */
@Composable
private fun Threads(count: Int) {
    val reduce = LocalReduceMotion.current
    val drawn = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(count, reduce) { if (reduce) drawn.snapTo(1f) else drawn.animateTo(1f, Springs.GENTLE.spec()) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxSize()) {
        val sx = size.width / STAGE_W
        val sy = density
        // الرسم بالبكسل الفعلي: في العربي زي النموذج، وفي الإنجليزي بالمراية (زي أماكن الأشخاص)
        fun px(x: Float) = if (rtl) x * sx else size.width - x * sx
        val measure = PathMeasure()
        SPOTS.take(count).forEachIndexed { i, (x, y) ->
            val ex = x
            val ey = y + 30f
            val dx = ex - HEAD_X
            val dy = ey - HEAD_Y
            val len = hypot(dx, dy).coerceAtLeast(1f)
            val nx = -dy / len
            val ny = dx / len
            val amp = (if (i % 2 == 1) 1f else -1f) * min(26f, len * 0.22f)
            val path = Path().apply {
                moveTo(px(HEAD_X), HEAD_Y * sy)
                cubicTo(
                    px(HEAD_X + dx * 0.3f + nx * amp), (HEAD_Y + dy * 0.3f + ny * amp) * sy,
                    px(HEAD_X + dx * 0.7f - nx * amp), (HEAD_Y + dy * 0.7f - ny * amp) * sy,
                    px(ex), ey * sy,
                )
            }
            measure.setPath(path, false)
            val part = Path()
            measure.getSegment(0f, measure.length * drawn.value, part, true)
            drawPath(part, Ink.selected.copy(alpha = 0.55f), style = Stroke(width = 1.5f * density, cap = StrokeCap.Round))
        }
    }
}

/** خلية في شبكة الأشخاص: الدايرة 56 · الاسم · «لك/عليك» بلونها. */
@Composable
internal fun GridPerson(p: PersonChip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPress()
    Column(
        modifier.pressScale(press).tap(press, label = p.name, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(app.masroufy.ui.glass.Glass.card).border(0.5.dp, Ink.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { BasicText(initialOf(p.name), style = Type.of(18, FontWeight.Bold).copy(color = Ink.primary)) }
        BasicText(p.name, Modifier.width(66.dp), style = Type.of(12, FontWeight.Bold).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(
            p.chip ?: "",
            style = Type.of(11, FontWeight.Bold).copy(color = p.tone?.color ?: Ink.muted, textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

/** خانة «إضافة» (دايرة متقطعة بزائد). */
@Composable
internal fun GridAdd(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPress()
    Column(
        modifier.pressScale(press).tap(press, label = t(UiKey.PEOPLE_ADD_PERSON), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).border(1.5.dp, Ink.primary.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
            app.masroufy.ui.icons.LucideIcon(app.masroufy.ui.icons.Lucide.PLUS, size = 22.dp, tint = Ink.primary)
        }
        BasicText(t(UiKey.PEOPLE_ADD), style = Type.of(12, FontWeight.Bold).copy(color = Ink.primary))
    }
}
