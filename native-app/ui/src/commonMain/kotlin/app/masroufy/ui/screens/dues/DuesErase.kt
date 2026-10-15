package app.masroufy.ui.screens.dues

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import app.masroufy.core.UiKey
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.usecase.DebtRemoval
import app.masroufy.usecase.DeleteDebt
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** الدين اللي اتمسح من القايمة السريعة — [text] للشريط و[removal] لـ«تراجع» (`DeleteDebt.restore` بنفس المعرّفات). */
internal class DebtErased(val text: String, val removal: DebtRemoval)

/**
 * «امسح» الدين من القايمة السريعة (قايمة المالك §6.5 بند 12 + 13): تأكيد جوه الصفحة بيقول هيتشال إيه بالظبط (`DeleteDebt.preview` —
 * من غير كتابة) ثم المسح ذرّيًا. الدين اللي اختفى قبل التأكيد ⇒ رسالة «غير موجود» والتأكيد بيقفل. الشاشة ما بتحسبش ولا بتكتب حاجة هنا.
 */
@Composable
internal fun DebtEraseSheet(target: DebtRowUi?, erase: DeleteDebt?, onDismiss: () -> Unit, onDone: (DebtErased) -> Unit) {
    if (erase == null) return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val plan by produceState<DebtRemoval?>(null, target) {
        value = null
        val row = target ?: return@produceState
        value = try {
            erase.preview(row.personId, row.obligationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toaster.show(t(UiKey.ERASE_DEBT_GONE))
            onDismiss()
            null
        }
    }
    val row = target
    val p = plan
    val body = if (row == null || p == null) "" else eraseDebtBody(row, p)
    ConfirmSheet(
        visible = row != null && p != null,
        title = t(UiKey.ERASE_DEBT_TITLE),
        body = body,
        confirm = t(UiKey.ERASE_CONFIRM),
        onConfirm = {
            if (row != null) scope.launch {
                try {
                    val removal = erase.delete(row.personId, row.obligationId)
                    onDone(DebtErased(t(UiKey.ERASE_DEBT_DONE, row.name), removal))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toaster.show(e.message ?: t(UiKey.ERASE_DEBT_GONE))
                    onDismiss()
                }
            }
        },
        onDismiss = onDismiss,
    )
}

/** سطور التأكيد من خطة المسح: المبلغ مع مين · السداد اللي هيتشال · العملية بتفضل · الموعد بيتشال. */
private fun eraseDebtBody(row: DebtRowUi, p: DebtRemoval): String = buildList {
    val currency = p.obligation.currency
    add(t(UiKey.ERASE_DEBT_LINE, amountLabel(p.obligation.originalMinor, currency), row.name))
    if (p.settlements.isNotEmpty()) add(t(UiKey.ERASE_DEBT_LINE_SETTLED, amountLabel(p.settledMinor, currency)))
    if (p.fromTransaction) add(t(UiKey.ERASE_DEBT_LINE_TXN))
    if (p.terms != null) add(t(UiKey.ERASE_DEBT_LINE_TERMS))
}.joinToString("\n")
