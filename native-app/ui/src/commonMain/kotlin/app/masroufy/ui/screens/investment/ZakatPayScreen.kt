package app.masroufy.ui.screens.investment

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «دفع زكاة السنة» كشاشة لوحدها (`ZakatPayRoute`) — نفس القطعة اللي بتظهر جوه «الزكاة» بعد «ثبّت» ([ZakatPaySection])، لسنة بعينها.
 * سنة مش متثبّتة أو مش موجودة ⇒ رسالة حالة الاستخدام نفسها («أعد المحاولة»).
 */
@Composable
fun ZakatPayScreen(yearId: Id) {
    val space = LocalSpace.current
    val deps = space.investment
    val scope = rememberCoroutineScope()
    var pay by remember(space, yearId) { mutableStateOf<ZakatPayUi?>(null) }
    var failed by remember(space, yearId) { mutableStateOf<String?>(null) }
    suspend fun reload() {
        try { pay = loadZakatPay(deps, space.space, yearId); failed = null } catch (e: IllegalArgumentException) { failed = e.message }
    }
    LaunchedEffect(space, yearId) { reload() }
    InnerScaffold(t(TextKey.ZAKAT_PAY_TITLE)) {
        val p = pay
        val f = failed
        when {
            f != null -> item(key = "failed") { AlertBanner(t(TextKey.SHELL_LOAD_FAILED), f, t(TextKey.SHELL_RETRY)) { scope.launch { reload() } } }
            p == null -> item(key = "loading") { Skeleton(Modifier.fillMaxWidth().height(320.dp)) }
            else -> {
                item(key = "pay") { ZakatPaySection(p, space.space.currency, showTitle = false) { scope.launch { reload() } } }
                item(key = "guidance") {
                    BasicText(t(TextKey.ZAKAT_GUIDANCE), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center))
                }
            }
        }
    }
}
