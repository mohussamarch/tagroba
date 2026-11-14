package app.masroufy.ui.screens.investment.holdings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.PutAwayHolding
import app.masroufy.core.R06Key
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.investment.AssetDetailRoute
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * مراجعة todo-assets-3 (§120 — التصميم: «قسم صغير مطوي تحت حاجاتي»): المؤرشف واللي اتباع كله ([PutAwayHolding] من `LoadHoldings`).
 * سطر «مؤرشف ومتباع (N)» بيتفتح ⇒ كل حاجة: الضغط بيفتح صفحتها (بيع · امسح · رجّعها هناك) و«رجّعه» للمؤرشف على طول
 * (`ManageAssets.archiveAsset` — نفس زرار صفحة الحاجة). الشاشة ما بتحسبش حاجة. فاضي ⇒ مفيش سطر.
 */
@Composable
internal fun PutAwaySection(items: List<PutAwayHolding>, onChanged: () -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val assets = LocalSpace.current.investment.assets
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val title = t(R06Key.C120_AWAY_TITLE, "${items.size}")
    FloatingCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp), onClick = { open = !open }, clickLabel = title) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(title, style = Type.caption().copy(color = Ink.muted))
            if (open) for (a in items) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(a.name, style = Type.of(14))
                        BasicText(t(if (a.archived) R06Key.C120_AWAY_ARCHIVED else R06Key.C120_AWAY_SOLD), style = Type.caption().copy(color = Ink.muted))
                    }
                    if (a.archived) TonalButton(t(R06Key.DEL_UNARCHIVE), {
                        scope.launch {
                            try {
                                assets.archiveAsset(a.assetId, false)
                                toaster.show(t(R06Key.DEL_UNARCHIVED, a.name))
                                onChanged()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                toaster.show(e.message ?: t(app.masroufy.core.UiKey.SHELL_LOAD_FAILED))
                            }
                        }
                    }, height = 36.dp, muted = true)
                    TonalButton(t(R06Key.C120_AWAY_OPEN), { nav.push(AssetDetailRoute(a.assetId)) }, height = 36.dp)
                }
            }
        }
    }
}
