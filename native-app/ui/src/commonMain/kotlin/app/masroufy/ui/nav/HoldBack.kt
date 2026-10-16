package app.masroufy.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * r041b P2: الشاشة [route] بتمسك زرار رجوع الجهاز طول ما هي مرسومة ([Navigator.holdBack]) — [handler] بيرجّع true لو عمل حاجة،
 * و false عشان الجهاز يقفل التطبيق (الشاشة ما بتتشالش). اللوحات المفتوحة فوقها بتتقفل الأول زي الأول.
 */
@Composable
fun HoldBack(route: Route, handler: () -> Boolean) {
    val nav = LocalNavigator.current
    val latest by rememberUpdatedState(handler)
    DisposableEffect(nav, route) {
        nav.holdBack(route) { latest() }
        onDispose { nav.holdBack(route, null) }
    }
}
