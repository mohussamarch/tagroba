package app.masroufy.ui.screens.investment.holdings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.ui.overlay.MenuItem
import app.masroufy.ui.screens.common.QuickAct
import app.masroufy.ui.screens.common.holdingQuickActs
import app.masroufy.ui.screens.common.quickItems
import app.masroufy.ui.screens.dues.DeleteSheet
import app.masroufy.ui.screens.dues.doneText
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.usecase.EntityRemover
import app.masroufy.usecase.RemovalAsk
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * OVERRIDES §121-2 — الضغطة الطويلة على حاجة في «حاجاتي»: قايمة صغيرة فيها «بيع» (تفاصيل الحاجة ولوحة البيع مفتوحة — `SellAsset`)
 * و«امسح» (اللوحة الواحدة بتاعة المسح `RemoveAsset` بسؤال «متأكد؟» فوق، وبعدها «تراجع»). الشاشة ما بتكتبش ولا بتحسب حاجة.
 */
@Stable
internal class HoldingQuick(val items: (assetId: Id) -> List<MenuItem>, internal val del: DeleteState)

internal class DeleteState {
    var id by mutableStateOf<Id?>(null)
    var ask by mutableStateOf<RemovalAsk?>(null)
}

/** [sell] = افتح التفاصيل ولوحة البيع · [remover] null (مش متوصل) ⇒ «امسح» مش ظاهرة */
@Composable
internal fun rememberHoldingQuick(remover: EntityRemover?, sell: (Id) -> Unit): HoldingQuick {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val del = remember { DeleteState() }
    fun askDelete(id: Id) {
        val r = remover ?: return
        scope.launch {
            try {
                val a = r.ask(id)
                val refused = a.refused
                if (refused != null) toaster.show(refused) else { del.id = id; del.ask = a }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toaster.show(e.message ?: t(UiKey.SHELL_LOAD_FAILED))
            }
        }
    }
    val acts = holdingQuickActs().filter { it != QuickAct.DELETE || remover != null }
    return remember(remover, acts) {
        HoldingQuick({ id ->
            quickItems(acts) { act ->
                when (act) {
                    QuickAct.SELL -> sell(id)
                    QuickAct.DELETE -> askDelete(id)
                    else -> Unit
                }
            }
        }, del)
    }
}

/** لوحة «متأكد؟» — برّه القايمة الطويلة (جنب `QuickMenuHost`). */
@Composable
internal fun HoldingQuickHost(q: HoldingQuick, remover: EntityRemover?, onChanged: () -> Unit) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val del = q.del
    DeleteSheet(del.ask, onDismiss = { del.ask = null; del.id = null }, sure = true) { choice, moveTo ->
        val id = del.id
        del.ask = null
        del.id = null
        val r = remover
        if (id != null && r != null) scope.launch {
            try {
                val done = r.remove(id, choice, moveTo)
                onChanged()
                toaster.showUndo(doneText(done), t(UiKey.OPS_BULK_UNDO)) {
                    val failed = runCatching { done.undo() }.exceptionOrNull()?.message
                    onChanged()
                    failed ?: t(UiKey.OPS_BULK_UNDONE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toaster.show(e.message ?: t(UiKey.SHELL_LOAD_FAILED))
            }
        }
    }
}
