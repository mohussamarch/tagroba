package app.masroufy.ui.screens.character

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterPart
import app.masroufy.core.CharacterSection
import app.masroufy.core.UiKey
import app.masroufy.ui.character.CharacterArt
import app.masroufy.ui.character.CharacterPalette
import app.masroufy.ui.character.forKey
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Type

/** قطع البنّاء (character-spec §3): كارت القسم المقفول · القسم المفتوح بتبويباته · شبكة الاختيارات · الألوان · «قريب/الجسم كله». */

private val green = Ink.primary.copy(alpha = 0xFF / 255f)
private val greenSoft = Ink.primary.copy(alpha = 0x17 / 255f)
private val tabOff = Ink.muted

internal fun sectionIcon(section: CharacterSection): Lucide = when (section) {
    CharacterSection.FACE -> Lucide.SMILE
    CharacterSection.BODY -> Lucide.PERSON_STANDING
    CharacterSection.WEAR -> Lucide.SHIRT
}

@Composable
private fun IconBox(icon: Lucide, on: Boolean) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).then(if (on) Modifier.background(Glass.primary) else Modifier.background(greenSoft)),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 20.dp, tint = if (on) Color.White else green) }
}

/** القسم المقفول: أيقونة · العنوان · ملخص اللي مختار · سهم. */
@Composable
internal fun SectionCard(section: CharacterSection, summary: String, onOpen: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).pressScale(press).clip(shape).background(Color(0xB8FFFFFF))
            .tap(press, label = t(section.title), onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(sectionIcon(section), on = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(t(section.title), style = Type.of(15, FontWeight.Bold))
            BasicText(summary, style = Type.of(12).copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LucideIcon(Lucide.CHEVRON_DOWN, size = 18.dp, tint = Ink.muted)
    }
}

/** القسم المفتوح: الرأس · التبويبات · المحتوى (بيظهر في 320ms، ومن غير حركة لو «تقليل الحركة»). */
@Composable
internal fun OpenSection(s: BuilderState, onChange: (BuilderState) -> Unit) {
    val reduce = LocalReduceMotion.current
    val shown = remember(s.section) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(s.section) { if (!reduce) shown.animateTo(1f, tween(320)) }
    val part = openPart(s)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(sectionIcon(s.section), on = true)
            BasicText(t(s.section.title), style = Type.of(17, FontWeight.Bold))
        }
        PartTabs(partsOf(s, s.section), tabIndex(s)) { onChange(openTab(s, it)) }
        Column(Modifier.alpha(shown.value), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val options = optionsFor(s, part)
            if (options.isNotEmpty()) OptionGrid(options) { onChange(pick(s, part, it)) }
            if (hairNote(s)) BasicText(t(UiKey.CHAR_HAIR_HIDDEN), style = Type.of(13).copy(color = Ink.muted))
            val key = part.color
            if (key != null && showColors(s, part)) {
                ColorRow(colorLabel(s, part), CharacterPalette.forKey(key, s.look), colorIndex(s.look, key), options.isNotEmpty()) {
                    onChange(pickColor(s, key, it))
                }
            }
        }
    }
}

/** التبويبات بخط تحت المختار (ارتفاع 46 · 13 عريض · الشريط 3 بهامش 8). */
@Composable
private fun PartTabs(parts: List<CharacterPart>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        parts.forEachIndexed { i, p ->
            val on = i == selected
            val press = rememberPress()
            Box(
                Modifier.weight(1f).height(46.dp).tap(press, role = Role.Tab, onClick = { onPick(i) }).semantics { this.selected = on }
                    .drawBehind {
                        if (on) {
                            val inset = 8.dp.toPx()
                            val h = 3.dp.toPx()
                            drawRect(green, Offset(inset, size.height - h), androidx.compose.ui.geometry.Size(size.width - inset * 2, h))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    t(p.title), style = Type.of(13, FontWeight.Bold).copy(color = if (on) green else tabOff, textAlign = TextAlign.Center),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 4 أعمدة: كل اختيار صورة صغيرة من شكلك بالاختيار ده بس + اسمه. */
@Composable
private fun OptionGrid(options: List<OptionUi>, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in options.chunked(4)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in row) OptionCell(o, Modifier.weight(1f)) { onPick(o.id) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun OptionCell(o: OptionUi, modifier: Modifier, onPick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.pressScale(press).clip(shape).background(if (o.selected) Ink.selected else Color(0xFFF6F8F4))
            .insetRing(shape, if (o.selected) 2.dp else 1.dp, if (o.selected) green else Color(0xB3CCD8CC))
            .tap(press, role = Role.RadioButton, label = o.label, onClick = onPick).semantics { selected = o.selected }
            .padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val thumbShape = RoundedCornerShape(if (o.layer == CharacterLayer.HEAD) 30.dp else 16.dp)
        Box(Modifier.width(60.dp).height(if (o.layer == CharacterLayer.FULL) 96.dp else 60.dp).clip(thumbShape).background(Glass.lensOnLight)) {
            CharacterArt(o.look, o.layer, Modifier.fillMaxSize())
        }
        BasicText(o.label, style = Type.of(11, FontWeight.Bold).copy(textAlign = TextAlign.Center), maxLines = 2)
    }
}

/** الألوان بعد خط رفيع: دواير 44 والمختار بحلقة بيضا 3 + خضرا 2. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(label: String, colors: List<Color>, selected: Int, divider: Boolean, onPick: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().then(
            if (divider) Modifier.drawBehind { drawRect(Ink.tint(0x1F), Offset.Zero, androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }.padding(top = 14.dp)
            else Modifier,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(label, style = Type.of(13, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEachIndexed { i, c ->
                val on = i == selected
                val press = rememberPress()
                val name = t(UiKey.CHAR_COLOR_N, label, app.masroufy.core.sentenceNumber(i + 1))
                Box(
                    Modifier.size(44.dp).pressScale(press)
                        .then(if (on) Modifier.drawBehind { drawCircle(green, size.minDimension / 2f + 5.dp.toPx()); drawCircle(Color.White, size.minDimension / 2f + 3.dp.toPx()) } else Modifier)
                        .clip(CircleShape).background(c)
                        .then(if (on) Modifier else Modifier.insetRing(CircleShape, 1.dp, Ink.tint(0x26)))
                        .tap(press, role = Role.RadioButton, label = name, onClick = { onPick(i) }).semantics { this.selected = on },
                )
            }
        }
    }
}

/** «قريب» / «الجسم كله» فوق المعاينة (أبيض 0.8 بزاوية 13 · المختار أبيض على أخضر). */
@Composable
internal fun ViewToggle(layer: CharacterLayer, onPick: (CharacterLayer) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(13.dp)).background(Color(0xCCFFFFFF)).padding(2.dp)) {
        for ((l, key) in listOf(CharacterLayer.BUST to UiKey.CHAR_VIEW_CLOSE, CharacterLayer.FULL to UiKey.CHAR_VIEW_FULL)) {
            val on = l == layer
            val press = rememberPress()
            Box(
                Modifier.height(30.dp).clip(RoundedCornerShape(11.dp)).background(if (on) green else Color.Transparent)
                    .tap(press, role = Role.RadioButton, onClick = { onPick(l) }).semantics { selected = on }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(t(key), style = Type.of(11, FontWeight.Bold).copy(color = if (on) Color.White else green)) }
        }
    }
}
