package app.masroufy.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.currencyName
import app.masroufy.core.currencySymbol
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.app.SpaceChoice
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.LensOnLight
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.Spinner
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.more.SpacesRoute
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** اسم البلد للعرض (من خرائط النصوص — مش الاسم المتخزن). */
fun countryLabel(space: Space): String = when (space.countryCode.uppercase()) {
    "SA" -> t(TextKey.COUNTRY_SA)
    "EG" -> t(TextKey.COUNTRY_EG)
    else -> space.name
}

/**
 * شارة البلد + لوحة التبديل (`SpaceSwitcher` — §41 · §64 · OVERRIDES §76): في الرئيسية تحت الاسم قبل التاريخ **حرفين لاتيني بس**
 * (`SA ⌄` · ارتفاع 22 · مساحة اللمس 44). اللوحة: كل بلد باسمها وعملتها و«معك الآن» بعملتها، والحالية عليها «الحالية»،
 * والتبديل على الجهاز ده بس ومن غير كتابة على البيانات. «إدارة البلدان» ⇒ `Spaces`. بعد التبديل رسالة صغيرة («انتقلت إلى حساب مصر»).
 */
@Composable
fun SpaceSwitcher(modifier: Modifier = Modifier) {
    val deps = LocalSpace.current
    val shell = deps.shell
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf<List<SpaceChoice>?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    val active = deps.space
    LaunchedEffect(open, deps) { if (open) choices = shell.spaces() }

    val press = rememberPress()
    val chipShape = RoundedCornerShape(11.dp)
    val chip = if (open) Modifier.clip(chipShape).background(Ink.selected).insetRing(chipShape, 1.5.dp, Ink.primary)
    else Modifier.layeredShadow(chipShape, Shadows.chip).clip(chipShape).background(Glass.card).innerSheen(chipShape, Shadows.chip)
    Box(
        modifier.defaultMinSize(minWidth = 44.dp).height(44.dp).padding(vertical = 11.dp).pressScale(press)
            .tap(press, label = t(TextKey.SPACE_TRIGGER, countryLabel(active)), onClick = { open = true }),
        contentAlignment = Alignment.Center,
    ) {
        Row(chip.height(22.dp).padding(start = 8.dp, end = 5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            // الحرفين لاتيني بخط النظام (زي النموذج: system-ui)
            BasicText(active.countryCode.uppercase(), style = Type.of(11, FontWeight.Bold).copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.5.sp))
            LucideIcon(Lucide.CHEVRON_DOWN, size = 14.dp, tint = Ink.muted)
        }
    }

    Sheet(visible = open, onDismiss = { if (pending == null) open = false }, title = t(TextKey.SPACE_SWITCH_TITLE), closeLabel = t(TextKey.SHELL_CLOSE), corner = 28.dp, spacing = 12.dp) {
        BasicText(t(TextKey.SPACE_SWITCH_TITLE), style = Type.of(17, FontWeight.Bold))
        BasicText(t(TextKey.SPACE_SWITCH_BODY), style = Type.of(13).copy(color = Ink.muted))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val list = choices
            if (list == null) {
                Skeleton(Modifier.fillMaxWidth().height(72.dp), radius = 18.dp)
                Skeleton(Modifier.fillMaxWidth().height(72.dp), radius = 18.dp)
            } else for (c in list) SpaceRow(c, going = pending == c.space.id) {
                if (pending != null) return@SpaceRow
                if (c.active) { open = false; return@SpaceRow }
                pending = c.space.id
                scope.launch {
                    val ok = shell.switchSpace(c.space.id)
                    pending = null
                    open = false
                    if (ok) toaster.show(t(TextKey.SPACE_SWITCHED, countryLabel(c.space)))
                }
            }
        }
        BasicText(t(TextKey.SPACE_SHARED_NOTE), style = Type.caption().copy(color = Ink.muted))
        TonalButton(t(TextKey.SPACE_MANAGE), onClick = { open = false; nav.push(SpacesRoute) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SpaceRow(c: SpaceChoice, going: Boolean, onPick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val press = rememberPress()
    val surface = if (c.active) Modifier.layeredShadow(shape, Shadows.chip).clip(shape).background(Color.White).insetRing(shape, 1.5.dp, Color(0x8C08634F))
    else Modifier.clip(shape).background(Color(0x0A193D33))
    val name = countryLabel(c.space)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp).pressScale(press).then(surface)
            .tap(press, role = Role.RadioButton, onClick = onPick).semantics { selected = c.active; contentDescription = name }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LensOnLight(Modifier.size(44.dp), shape = RoundedCornerShape(16.dp)) {
            BasicText(name.take(1), style = Type.of(17, FontWeight.Bold).copy(color = Ink.primary))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(name, style = Type.of(16, FontWeight.Bold))
                if (c.active) Badge(t(TextKey.SPACE_ACTIVE), BadgeKind.INFO)
            }
            BasicText(t(TextKey.SPACE_SUB, currencyName(c.currency), currencySymbol(c.currency)), style = Type.caption().copy(color = Ink.muted))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(t(TextKey.SPACE_WITH_YOU), style = Type.of(11).copy(color = Ink.muted))
            if (going) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(size = 14.dp, color = Ink.primary)
                BasicText(t(TextKey.SPACE_OPENING), style = Type.captionBold().copy(color = Ink.primary))
            } else AmountText(c.withYouNowMinor, c.currency)
        }
    }
}
