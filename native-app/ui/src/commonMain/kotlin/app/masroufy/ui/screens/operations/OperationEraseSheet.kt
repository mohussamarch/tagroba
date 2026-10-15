package app.masroufy.ui.screens.operations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.screens.dues.ConfirmSheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.usecase.BulkAction
import app.masroufy.usecase.BulkUndo
import app.masroufy.usecase.DeleteTransaction
import app.masroufy.usecase.TransactionRemoval
import app.masroufy.usecase.blockText
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** العملية اللي اتمسحت — [text] لشريط «تراجع» و[removal] لـ`DeleteTransaction.restore` (بنفس المعرّفات). */
internal class TxnErased(val text: String, val removal: TransactionRemoval) {
    /** نفس «تراجع» المسح الجماعي (`BulkTransactions.undo` ⇒ `restore`) — شريط واحد ومدة واحدة (8 ثواني) في «العمليات». */
    fun asBulk(): BulkDone = BulkDone(text, BulkUndo(BulkAction.DELETE, removals = listOf(removal)))
}

/**
 * «امسح» للعملية الواحدة (OVERRIDES §91-11 — قايمة الضغطة الطويلة وشاشة التفاصيل): تأكيد جوه الصفحة بيقول هيحصل إيه بالظبط
 * (`DeleteTransaction.preview` — من غير كتابة) ثم المسح. الممنوع (فلوس تانية معتمدة عليها) ⇒ رسالته الموجودة («ما ينفعش تتمسح دلوقتي» + السبب)
 * من غير تأكيد. الشاشة ما بتحسبش ولا بتكتب حاجة هنا.
 */
@Composable
internal fun OperationEraseSheet(targetId: Id?, erase: DeleteTransaction?, onDismiss: () -> Unit, onDone: (TxnErased) -> Unit) {
    if (erase == null) return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val plan by produceState<TransactionRemoval?>(null, targetId) {
        value = null
        val id = targetId ?: return@produceState
        val p = try {
            erase.preview(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toaster.show(e.message ?: t(UiKey.OPERATION_DETAIL_ERROR_TITLE))
            onDismiss()
            return@produceState
        }
        if (!p.allowed) {
            toaster.show((listOf(t(UiKey.ERASE_TXN_BLOCKED_TITLE)) + p.blocks.map { t(blockText(it)) }).joinToString("\n"))
            onDismiss()
            return@produceState
        }
        value = p
    }
    val p = plan
    ConfirmSheet(
        visible = targetId != null && p != null,
        title = t(UiKey.ERASE_TXN_TITLE),
        body = p?.let(::eraseBody).orEmpty(),
        confirm = t(UiKey.ERASE_CONFIRM),
        onConfirm = {
            val id = targetId
            val name = p?.let(::nameOf).orEmpty()
            if (id != null) scope.launch {
                try {
                    onDone(TxnErased(t(UiKey.ERASE_TXN_DONE, name), erase.delete(id)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toaster.show(e.message ?: t(UiKey.OPERATION_DETAIL_ERROR_TITLE))
                    onDismiss()
                }
            }
        },
        onDismiss = onDismiss,
    )
}

private fun nameOf(p: TransactionRemoval): String =
    p.transaction.rawMerchantName?.takeIf { it.isNotBlank() } ?: p.transaction.rawDescription?.takeIf { it.isNotBlank() } ?: t(UiKey.OPERATIONS_ROW_UNCLASSIFIED)

/** سطور التأكيد من خطة المسح: العملية بمبلغها ويومها · الروابط اللي هتتشال · جاية من رسالة أو كشف · الرصيد. */
private fun eraseBody(p: TransactionRemoval): String = buildList {
    val tx = p.transaction
    add(t(UiKey.ERASE_TXN_LINE, nameOf(p), amountLabel(tx.amountMinor, tx.currency), dayMonth(tx.occurredAt)))
    if (p.unlinkCount > 0) add(t(UiKey.ERASE_TXN_LINE_LINKS, p.unlinkCount.toString()))
    if (p.imported) add(t(UiKey.ERASE_TXN_LINE_IMPORTED))
    add(t(UiKey.ERASE_TXN_LINE_BALANCE))
}.joinToString("\n")
