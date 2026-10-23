package app.masroufy.ui.screens.people

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.core.UiKey
import app.masroufy.ui.components.LensOnHero
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.cssLinear
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Anchor
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.PersonInk
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** المقاسات من `PersonHero.dc.html`: الأخضر 196 مقفول و470 مفتوح (+ شريط الحالة) · الصف (الصورة وزرارينها) يبدأ قبل آخر الأخضر بـ76. */
private val CLOSED = 196.dp
private val OPEN = 470.dp

/** الخلفية بلون الشخص — 0 = الأخضر الأصلي (`Glass.heroBase` — بيتبدل مع الغامق). */
internal fun personHeroBrush(color: Int) =
    if (color <= 0 || color >= PersonInk.heroColors.size) Glass.heroBase
    else PersonInk.heroColors[color].let { (a, b) -> cssLinear(125f, 0f to a, 1f to b) }

/**
 * رأس صفحة الشخص (`PersonHero.dc.html` — فرع التصميم 59eac9b8 · OVERRIDES §116-ب): الرجوع · المناسبات السنوية أيقونات من غير كلام · دايرته ·
 * **الستارة**: السحب لتحت أو الضغط على المقبض بيطوّل الأخضر لحد ما الصورة تبقى تحت، وجواه كروت كل المناسبات بتتقلب بالسحب و«خصّص»
 * (لوحة بيضا: 5 ألوان · 8 رموز · «تمام» — بيتحفظوا على طول) · الصورة 132 (أو رمز شخص — مش أول حرف) وعلامة «ضيف صورة» ·
 * «معاه» يمينها بيفرد الأيقونات ([onFan]) · «مش متصل» شمالها ([onLink]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PersonHero(
    ui: PersonPageUi,
    occ: PersonOccasionsUi,
    look: PersonLookUi,
    top: Dp,
    fanOpen: Boolean,
    onBack: () -> Unit,
    onLook: (Int, String?) -> Unit,
    onAddOccasion: () -> Unit,
    onFan: (Anchor) -> Unit,
    onLink: () -> Unit,
) {
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val range = (OPEN - CLOSED).value
    val pull = remember { Animatable(0f) }
    var edit by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(0f) }
    val open = pull.value > range / 2
    fun settle(toOpen: Boolean) {
        scope.launch { if (reduce) pull.snapTo(if (toOpen) range else 0f) else pull.animateTo(if (toOpen) range else 0f) }
        if (!toOpen) edit = false
    }
    val h = CLOSED + top + pull.value.dp
    Box(Modifier.fillMaxWidth().height(h + 80.dp)) {
        Box(
            Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(personHeroBrush(look.heroColor))
                .pointerInput(range) {
                    detectVerticalDragGestures(
                        onDragStart = { start = pull.value },
                        onDragEnd = { settle(pull.value > range / 3 && !(start > 0f && pull.value < start)) },
                        onDragCancel = { settle(start > 0f) },
                    ) { change, dy ->
                        change.consume()
                        scope.launch { pull.snapTo((pull.value + with(density) { dy.toDp().value }).coerceIn(0f, range)) }
                    }
                },
        ) {
            TopRow(ui, occ, top, onBack) { settle(true) }
            if (open && !edit) OccasionCurtain(occ, top, onAddOccasion) { edit = true }
            if (open && edit) LookPanel(ui.name, look, top, onLook) { edit = false }
            val hp = rememberPress()
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(56.dp, 24.dp)
                    .tap(hp, label = t(if (open) R06Key.PH_HIDE else R06Key.PH_SHOW), onClick = { settle(!open) }),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(PersonInk.handle)) }
        }
        Row(
            Modifier.fillMaxWidth().offset(y = h - 76.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val anchor = remember { Anchor() }
            SideButton(if (fanOpen) Lucide.X else Lucide.PLUS, t(R06Key.PH_WITH), t(R06Key.PH_QUICK), Ink.primary, anchor) { onFan(anchor) }
            PersonAvatar(ui.id, look.badge, 132.dp)
            SideButton(PeopleIcons.LINK_OFF, t(R06Key.PH_LINK_LABEL), t(R06Key.PH_LINK_ARIA, ui.name), Ink.muted, null, onLink)
        }
    }
}

/** اللون والرمز المحفوظين للشخص (من `PersonProfile`). */
internal data class PersonLookUi(val heroColor: Int = 0, val badge: String? = null)

@Composable
private fun TopRow(ui: PersonPageUi, occ: PersonOccasionsUi, top: Dp, onBack: () -> Unit, openCurtain: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp + top), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val press = rememberPress()
        LensOnHero(Modifier.size(44.dp).pressScale(press).tap(press, label = t(UiKey.SHELL_BACK), onClick = onBack)) {
            LucideIcon(Lucide.CHEVRON_RIGHT, size = 22.dp, tint = Ink.lensInk, modifier = Modifier.align(Alignment.Center).mirrorInLtr())
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            for (y in occ.yearly) {
                val p = rememberPress()
                Box(
                    Modifier.size(34.dp).pressScale(p).clip(CircleShape).background(PersonInk.onHeroButton).tap(p, label = joinLine(y.title, y.date), onClick = openCurtain),
                    contentAlignment = Alignment.Center,
                ) { LucideIcon(y.glyph.icon, size = 17.dp, tint = Ink.white(0xFF)) }
            }
        }
        BasicText(ui.circleLine, Modifier.width(44.dp), style = Type.of(12).copy(color = Ink.onHeroMuted))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OccasionCurtain(occ: PersonOccasionsUi, top: Dp, onAdd: () -> Unit, onCustomize: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 72.dp + top), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val state = rememberLazyListState()
        LazyRow(state = state, flingBehavior = rememberSnapFlingBehavior(state), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(occ.cards, key = { it.id }) { c -> BigCard(c.glyph.icon, c.title, c.date, c.left, null) }
            item(key = "+add") { BigCard(Lucide.PLUS, t(R06Key.PH_ADD_OCCASION), "", null, onAdd) }
        }
        val press = rememberPress()
        Row(
            Modifier.height(36.dp).pressScale(press).clip(RoundedCornerShape(18.dp)).background(PersonInk.onHeroButton).tap(press, label = t(R06Key.PH_CUSTOMIZE), onClick = onCustomize).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LucideIcon(PeopleIcons.PEN, size = 15.dp, tint = Ink.white(0xFF))
            BasicText(t(R06Key.PH_CUSTOMIZE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.white(0xFF)))
        }
    }
}

@Composable
private fun BigCard(icon: Lucide, title: String, date: String, left: String?, onClick: (() -> Unit)?) {
    val press = rememberPress()
    Column(
        Modifier.width(220.dp).heightIn(min = 168.dp).clip(RoundedCornerShape(24.dp)).background(PersonInk.onHeroCard)
            .then(if (onClick != null) Modifier.pressScale(press).tap(press, label = title, onClick = onClick) else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(PersonInk.onHeroButton), contentAlignment = Alignment.Center) { LucideIcon(icon, size = 26.dp, tint = Ink.white(0xFF)) }
        BasicText(title, style = Type.of(18, FontWeight.Bold).copy(color = Ink.white(0xFF)))
        if (date.isNotEmpty()) BasicText(date, style = Type.of(13).copy(color = Ink.onHeroMuted))
        left?.let {
            BasicText(it, Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(Ink.onHeroMuted).padding(horizontal = 10.dp, vertical = 4.dp), style = Type.of(12, FontWeight.Bold).copy(color = Ink.primary))
        }
    }
}

@Composable
private fun SideButton(icon: Lucide, label: String, aria: String, tint: androidx.compose.ui.graphics.Color, anchor: Anchor?, onClick: () -> Unit) {
    val press = rememberPress()
    Column(Modifier.width(72.dp).pressScale(press).tap(press, label = aria, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(44.dp).then(if (anchor != null) Modifier.onPlaced(anchor) else Modifier).clip(CircleShape).background(Ink.surface),
            contentAlignment = Alignment.Center,
        ) { LucideIcon(icon, size = 20.dp, tint = tint) }
        BasicText(label, style = Type.of(11, FontWeight.Bold).copy(color = tint))
    }
}

private fun Modifier.onPlaced(anchor: Anchor): Modifier = this.onGloballyPositioned { anchor.update(it) }
