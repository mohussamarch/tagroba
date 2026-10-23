package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «ديونه كلها في المستحقات ›» (`PersonProfile.dc.html` — فرع التصميم `8575ee4b` · OVERRIDES §112-2): صفحة الشخص ما بتكررش قايمة الديون،
 * بتوصّل لـ«الديون» في «المستحقات» (البيت الوحيد للديون). سطر أخضر خفيف 44 بزاوية 14 وسهم.
 */
@Composable
internal fun DuesDebtsLink(modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    val press = rememberPress()
    val label = t(R06Key.PP_DEBTS_LINK)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).pressScale(press).clip(shape).background(Ink.primary.copy(alpha = 0.08f))
            .tap(press, label = label) { nav.push(DuesDebtsRoute()) }.padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary))
        LucideIcon(Lucide.CHEVRON_LEFT, size = 16.dp, tint = Ink.primary, modifier = Modifier.mirrorInLtr())
    }
}
