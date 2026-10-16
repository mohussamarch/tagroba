package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.isConsistentWithObservedDirection
import app.masroufy.core.ruleFor
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.BULK_KINDS
import app.masroufy.usecase.BulkUndo
import kotlinx.coroutines.launch

/**
 * «النوع» في تفاصيل العملية بقى بيتداس (طلب المالك 2026-10-16 — «ليه مينفعش اختار او احدد النوع وانا جوا العملية نفسها»): لوحة بنفس الأنواع اللي
 * التطبيق بيسمح بيها للاتجاه ده ([kindChoicesFor] — `BULK_KINDS` المتسقة مع الاتجاه) ⇒ تأكيد ⇒ `BulkTransactions.setKind` (نفس طريق «غيّر النوع»
 * — بيعلّم النوع مؤكد) و«تراجع» واحد (`BulkTransactions.undo`). «لسه ما اتحددش» بيفتح نفس اللوحة. «شراء» بعدها ⇒ «إيه الصنف؟».
 */
fun kindChoicesFor(direction: Direction): List<EconomicKind> = BULK_KINDS.filter { isConsistentWithObservedDirection(it, direction) }

/** النوع يتغير من هنا: مش رجل «تحويل لنفسك» ولا تحويل بين محافظك ولا نص زوج «رجعت» (نفس استثناءات `setKind`). */
fun kindEditable(t: Transaction, locked: Boolean): Boolean =
    !locked && t.transferToWalletId == null && t.reversalOfId == null && t.reversedById == null

/** اللي اتغير — للشريط بتاع «تراجع». */
data class KindChange(val label: String, val undo: BulkUndo)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KindPickerSheet(tx: Transaction?, onDismiss: () -> Unit, onSaved: (KindChange, EconomicKind) -> Unit) {
    val deps = LocalSpace.current.operations
    val bulk = deps.bulk
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<EconomicKind?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(tx?.id) { picked = null; error = null }
    val title = t(UiKey.KIND_PICK_TITLE)
    Sheet(tx != null && bulk != null, onDismiss, title = title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        val t0 = tx ?: return@Sheet
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in kindChoicesFor(t0.observedDirection)) {
                SelectChip(ruleFor(k).label, (picked ?: t0.economicKind.takeIf { t0.economicKindConfirmed }) == k, { picked = k; error = null }, height = 44.dp)
            }
        }
        error?.let { FieldError(it) }
        val k = picked
        PrimaryButton(
            if (k == null) t(UiKey.KIND_PICK_SET) else t(UiKey.KIND_PICK_CONFIRM, ruleFor(k).label),
            enabled = k != null && !saving, loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(),
            onClick = {
                val kind = picked ?: return@PrimaryButton
                saving = true
                scope.launch {
                    val r = runCatching { bulk?.setKind(listOf(t0.id), kind) }
                    saving = false
                    val undo = r.getOrNull()?.takeIf { it.changed > 0 }?.undo
                    when {
                        undo != null -> onSaved(KindChange(ruleFor(kind).label, undo), kind)
                        r.isFailure -> error = r.exceptionOrNull()?.message ?: t(UiKey.KIND_PICK_SKIPPED)
                        else -> error = t(UiKey.KIND_PICK_SKIPPED)
                    }
                }
            },
        )
        BasicText(t(UiKey.KIND_PICK_NOTE), style = Type.of(12).copy(color = Ink.muted))
    }
}

/** شريط «تراجع» 8 ثواني بعد تغيير النوع. */
@Composable
fun KindUndoStrip(change: KindChange?, onClear: () -> Unit, onChanged: () -> Unit) {
    val deps = LocalSpace.current.operations
    val scope = rememberCoroutineScope()
    val window = undoWindowFor(change) { onClear() }
    val c = change ?: return
    UndoStrip(t(UiKey.KIND_PICK_DONE, c.label), window) {
        onClear()
        scope.launch { runCatching { deps.bulk?.undo(c.undo) }; onChanged() }
    }
}
