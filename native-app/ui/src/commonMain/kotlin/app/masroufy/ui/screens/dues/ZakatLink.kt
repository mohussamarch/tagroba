package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.core.uiText
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.investment.ToolRow
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink

/**
 * «الزكاة» تحت «المستحقات» (قرار المالك 2026-10-23 — AppMap): بتظهر بس لو «المحتوى الإسلامي: ظاهر» وبلد ليها مرجع
 * (`ManageZakat.visible` — OVERRIDES §62-د). نفس الشاشة ونفس الحساب؛ اللي اتغير مكان المدخل بس (كان في «الاستثمار»).
 */
@Composable
internal fun ZakatLink() {
    val zakat = LocalSpace.current.investment.zakat
    val nav = LocalNavigator.current
    val hint by produceState<String?>(null, zakat) {
        value = runCatching {
            if (zakat.visible()) zakat.rules().firstOrNull()?.source?.authority?.label?.let { uiText(UiKey.INVEST_TOOL_ZAKAT_HINT, it) } else null
        }.getOrNull()
    }
    val sub = hint ?: return
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        ToolRow(t(UiKey.INVEST_TOOL_ZAKAT), sub, { nav.push(ZakatRoute) }, trailing = {
            LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        })
    }
}
