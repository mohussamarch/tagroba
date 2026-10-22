package app.masroufy.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import app.masroufy.core.R06Key
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Appearance
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.ThemeMode
import app.masroufy.ui.theme.Type

/**
 * «المظهر» (تصميم `NightPrivacy`): فاتح · غامق · زي الجوال (الافتراضي، بيتحفظ على الجهاز) + «اخفي الأرقام لما أفتح البرنامج».
 * الاتنين في [Appearance] — الشاشة ما بتحسبش حاجة، وتبديل المظهر بيتحرك في 300ms من `MasroufyTheme`.
 */
@Composable
internal fun AppearanceSection() {
    Section(t(R06Key.NP_THEME_TITLE)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.softSurface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ModeOption(ThemeMode.LIGHT, t(R06Key.NP_THEME_LIGHT))
            ModeOption(ThemeMode.DARK, t(R06Key.NP_THEME_DARK))
            ModeOption(ThemeMode.SYSTEM, t(R06Key.NP_THEME_AUTO))
        }
        SwitchHeader(Lucide.EYE_OFF, t(R06Key.NP_START_TITLE), t(R06Key.NP_START_SUB), Appearance.hideOnStart, enabled = true) {
            Appearance.chooseHideOnStart(!Appearance.hideOnStart)
        }
    }
}

@Composable
private fun RowScope.ModeOption(mode: ThemeMode, label: String) {
    val on = Appearance.mode == mode
    val press = rememberPress()
    Box(
        Modifier.weight(1f).height(44.dp).pressScale(press).clip(RoundedCornerShape(12.dp))
            .background(if (on) Ink.segmentOn else Ink.softSurface.copy(alpha = 0f))
            .tap(press, role = Role.RadioButton, onClick = { Appearance.chooseMode(mode) })
            .semantics { selected = on }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Type.of(14, if (on) FontWeight.Bold else FontWeight.Normal).copy(color = if (on) Ink.text else Ink.muted), maxLines = 1)
    }
}
