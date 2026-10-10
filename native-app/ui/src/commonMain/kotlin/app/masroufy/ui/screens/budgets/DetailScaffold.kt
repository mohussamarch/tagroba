package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.Type

/**
 * هيكل الشاشات الداخلية في المنطقة (زي `InnerScaffold` بالظبط: رجوع 48 · حواف 20 · مسافة 14 · من غير الشريطين) — بس العنوان **22** زي لوحات
 * المنطقة في النموذج، وتحته سطر رمادي اختياري ونقطة لون اختيارية (عنوان «ميزانية تصنيف»: «● مطاعم وقهوة» وتحته «ميزانية أكتوبر …»).
 */
@Composable
fun DetailScaffold(
    title: String,
    subtitle: String? = null,
    dot: Color? = null,
    actions: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val nav = LocalNavigator.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.gutter + top, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.mirrorInLtr()) { SurfaceIconButton(Lucide.CHEVRON_RIGHT, t(UiKey.SHELL_BACK), { nav.pop() }) }
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (dot != null) {
                            // نقطة 12 بهالة 4 من نفس اللون (النموذج: `box-shadow: 0 0 0 4px` بلون التصنيف 12٪)
                            Box(Modifier.size(20.dp).clip(CircleShape).background(dot.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                ColorDot(dot, size = 12.dp)
                            }
                        }
                        BasicText(
                            title, Modifier.semantics { heading() }, style = Type.of(22, FontWeight.Bold, 1.4), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (subtitle != null) BasicText(subtitle, style = Type.caption().copy(color = Ink.muted))
                }
                actions?.invoke()
            }
        }
        content()
    }
}
