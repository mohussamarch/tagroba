package app.masroufy.ui.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.more.MoreRoute
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.Type

/**
 * هياكل الشاشات — **كل شاشة بتبدأ بواحد منهم** عشان المسافات والرأس يبقوا واحد (KOTLIN-MAP §٣ «رأس الأقسام الأربعة لازم يبقى متطابق»):
 * - [TabScaffold] للتبويبات الأربعة: صف أول 48 (العنوان 24 عريض + **الترس دايمًا آخر حاجة على الشمال**) والمسافة تحت 156 للشريطين.
 * - [InnerScaffold] للشاشات الداخلية: زرار رجوع 48 (سهم 22) + العنوان 24 عريض (زي `Notifications`/`Calendar`/`More` في النموذج)، ومن غير شريط التنقل.
 * الحواف 20 والمسافة بين الكتل 14 (DESIGN-SYSTEM «المقاسات»). المحتوى `LazyColumn` (القوايم الطويلة بتتمرر بنعومة).
 */
private val statusTop @Composable get() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
fun TabScaffold(
    title: String,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    LazyColumn(
        modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.gutter + statusTop, bottom = Space.tabContentBottom),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        item(key = "header") { header?.invoke() ?: TabHeader(title) }
        content()
    }
}

/** رأس التبويب (العمليات · الأشخاص · الاستثمار): العنوان على اليمين والترس على الشمال — نفس مكانه في الأربعة (٢٠، ٢٠). */
@Composable
fun TabHeader(title: String, modifier: Modifier = Modifier, actions: (@Composable () -> Unit)? = null) {
    val nav = LocalNavigator.current
    Row(modifier.fillMaxWidth().height(Space.headerRow), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, Modifier.weight(1f).semantics { heading() }, style = Type.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions?.invoke()
        GearButton { nav.push(MoreRoute) }
    }
}

/** الترس ← «المزيد» (قرار المالك: «مش عايز علامة الترس تختفي أبدًا»). */
@Composable
fun GearButton(onClick: () -> Unit) {
    SurfaceIconButton(Lucide.SETTINGS, t(TextKey.SHELL_GEAR), onClick)
}

@Composable
fun InnerScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val nav = LocalNavigator.current
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.gutter + statusTop, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        item(key = "header") { ScreenHeader(title, onBack = onBack ?: { nav.pop(); Unit }, actions = actions) }
        content()
    }
}

/** رأس شاشة داخلية: رجوع (سهم لليمين في العربي) + العنوان 24 عريض + أفعال على الشمال (أزرار 48 بزاوية 18 — `SurfaceIconButton`). */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.mirrorInLtr()) { SurfaceIconButton(Lucide.CHEVRON_RIGHT, t(TextKey.SHELL_BACK), onBack) }
        }
        BasicText(title, Modifier.weight(1f).semantics { heading() }, style = Type.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions?.invoke()
    }
}

/** شاشة لسه ما اتبنتش (أو مش متسجلة): رجوع + «هذه الشاشة قيد البناء.» — بصراحة، من غير شكل مزيف (CLAUDE.md #15). */
@Composable
fun PendingScreen(boardName: String, showBack: Boolean = true) {
    val nav = LocalNavigator.current
    Column(Modifier.fillMaxSize().testTag(boardName).padding(start = Space.gutter, end = Space.gutter, top = Space.gutter + statusTop)) {
        if (showBack) ScreenHeader("", onBack = { nav.pop(); Unit })
        Spacer(Modifier.height(24.dp))
        EmptyState(title = t(TextKey.SHELL_PENDING_SCREEN))
    }
}
