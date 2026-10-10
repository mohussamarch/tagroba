package app.masroufy.ui.screens.investment

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.FeedPrice
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.FeedState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier

/**
 * «اربطه بالسعر اليومي» في تفاصيل الأصل: سطور ملف الأسعار **بعملة الأصل بس** (مفيش تحويل بسعر صرف — §41) ⇒ `ManageAssets.linkToFeed`.
 * الملف مش متاح ⇒ سببه (`FeedState.Unavailable`) بدل القايمة.
 */
@Composable
internal fun FeedPickSheet(visible: Boolean, asset: AssetDetailUi?, onDismiss: () -> Unit, onPick: (symbol: String, name: String) -> Unit) {
    val deps = LocalSpace.current.investment
    var rows by remember(visible) { mutableStateOf<List<FeedPrice>?>(null) }
    var reason by remember(visible) { mutableStateOf<String?>(null) }
    LaunchedEffect(visible, asset?.assetId) {
        if (!visible || asset == null) return@LaunchedEffect
        when (val feed = runCatching { deps.feeds.prices() }.getOrNull()) {
            is FeedState.Ready -> rows = feed.feed.prices.filter { it.currency == asset.currency.name }
            is FeedState.Unavailable -> { reason = feed.reason; rows = emptyList() }
            null -> { reason = t(UiKey.SHELL_LOAD_FAILED); rows = emptyList() }
        }
    }
    val title = t(UiKey.ASSET_DETAIL_PICK_FEED)
    Sheet(visible, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        val list = rows
        when {
            list == null -> Skeleton(Modifier.fillMaxWidth().height(120.dp))
            list.isEmpty() -> BasicText(reason ?: t(UiKey.ASSET_DETAIL_PICK_FEED_EMPTY), style = Type.of(13).copy(color = Ink.muted))
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (p in list) {
                    OpChoice(t(UiKey.INVEST_ROW_SUB, p.name, dateText(p.asOf)), null, false) { onPick(p.symbol, p.name); onDismiss() }
                }
            }
        }
    }
}
