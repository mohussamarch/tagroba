package app.masroufy.ui.screens.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.StagedCleanupOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * «دفعات الاستيراد» (`ImportBatches`): كل كشف أو مجموعة رسائل اتسجلت دفعة واحدة — المصدر · التاريخ · الأعداد · الحالة · «إرجاع الدفعة»
 * (`RevertBatchSheet` بمعاينة). استيراد اتقطع في النص ⇒ «نظّفه الآن» (`ResumeStagedBatch.cleanup`) ثم «استورد الملف من جديد».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportBatchesScreen() {
    val deps = LocalSpace.current.imports
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var ui by remember(deps) { mutableStateOf<ImportBatchesUi?>(null) }
    var reload by remember(deps) { mutableStateOf(0) }
    var revert by remember { mutableStateOf<ImportBatch?>(null) }
    var cleaning by remember { mutableStateOf<String?>(null) }
    var cleaned by remember { mutableStateOf<StagedCleanupOutcome?>(null) }

    LaunchedEffect(deps, reload) {
        val history = runCatching { deps.batches.history() }.getOrDefault(emptyList())
        val staged = runCatching { deps.staged.findStaged() }.getOrDefault(emptyList())
        ui = importBatchesUi(history, staged)
    }

    InnerScaffold(t(TextKey.IMPORT_BATCHES_TITLE)) {
        item(key = "intro") { BasicText(t(TextKey.IMPORT_BATCHES_INTRO), style = Type.of(13).copy(color = Ink.muted)) }
        val u = ui
        if (u == null) {
            item(key = "loading") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { Skeleton(Modifier.fillMaxWidth().height(120.dp)) } } }
            return@InnerScaffold
        }
        cleaned?.let { c ->
            item(key = "cleaned") {
                TintedPanel(if (c.error == null) PanelTone.MINT else PanelTone.AMBER) {
                    PanelTitle(t(if (c.error == null) TextKey.IMPORT_BATCHES_CLEANED_TITLE else TextKey.IMPORT_BATCHES_CLEAN_FAILED), if (c.error == null) PanelTone.MINT else PanelTone.AMBER)
                    BasicText(c.fileName, style = Type.caption())
                    BasicText(c.error ?: t(TextKey.IMPORT_BATCHES_CLEANED_BODY, opsCount(c.deletedTransactions)), style = Type.of(13, lineHeight = 1.7))
                    QuietButton(t(TextKey.IMPORT_BATCHES_REIMPORT), { nav.push(StatementImportRoute) }, onTint = true, height = 44.dp)
                }
            }
        }
        if (u.isEmpty && cleaned == null) {
            item(key = "empty") {
                EmptyState(t(TextKey.IMPORT_BATCHES_EMPTY_TITLE), t(TextKey.IMPORT_BATCHES_EMPTY_BODY), action = {
                    PrimaryButton(t(TextKey.IMPORT_BATCHES_IMPORT), { nav.push(StatementImportRoute) })
                })
            }
            return@InnerScaffold
        }
        for (s in u.staged) {
            item(key = "staged-${s.id}") {
                TintedPanel(PanelTone.AMBER) {
                    PanelTitle(t(TextKey.IMPORT_BATCHES_STAGED_TITLE), PanelTone.AMBER)
                    BasicText(t(TextKey.IMPORT_REVIEW_HERO_META, s.fileName, dayMonth(s.importedAt.take(10))), style = Type.caption().copy(color = Ink.focus))
                    BasicText(t(TextKey.IMPORT_BATCHES_STAGED_BODY), style = Type.of(13, lineHeight = 1.7))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(t(if (cleaning == s.id) TextKey.IMPORT_BATCHES_CLEANING else TextKey.IMPORT_BATCHES_CLEAN), {
                            if (cleaning != null) return@PrimaryButton
                            cleaning = s.id
                            scope.launch {
                                cleaned = try {
                                    deps.staged.cleanup(s.id)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    StagedCleanupOutcome(s.id, s.fileName, 0, 0, e.message ?: t(TextKey.IMPORTS_LOAD_FAILED))
                                }
                                cleaned?.takeIf { it.error == null }?.let { toaster.show(t(TextKey.IMPORT_BATCHES_CLEANED_TOAST, opsCount(it.deletedTransactions)), dark = true) }
                                cleaning = null
                                reload++
                            }
                        }, loading = cleaning == s.id, height = 44.dp)
                        QuietButton(t(TextKey.IMPORT_BATCHES_REIMPORT), { nav.push(StatementImportRoute) }, onTint = true, height = 44.dp)
                    }
                }
            }
        }
        for (b in u.batches) {
            item(key = b.id) {
                BatchCard(b) { revert = b }
            }
        }
        item(key = "foot") { BasicText(t(TextKey.IMPORT_BATCHES_FOOT), style = Type.caption().copy(color = Ink.muted)) }
    }

    RevertBatchSheet(
        batch = revert,
        deps = deps,
        onDone = { plan ->
            revert = null
            toaster.show(t(TextKey.IMPORT_BATCHES_REVERTED, revertResult(plan.toDelete.size, plan.toKeep.size)), dark = true)
            reload++
        },
        onDismiss = { revert = null },
    )
}

/** «حُذفت N عمليات، وبقيت M لارتباطها بغيرها.» */
fun revertResult(deleted: Int, kept: Int): String =
    if (kept > 0) t(TextKey.IMPORT_BATCHES_RESULT_KEPT, opsCount(deleted), opsCount(kept)) else t(TextKey.IMPORT_BATCHES_RESULT, opsCount(deleted))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchCard(b: ImportBatch, onRevert: () -> Unit) {
    val reverted = b.state == ImportBatchState.REVERTED
    FloatingCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            IconTile(if (reverted) Ink.faded else Ink.primary) {
                LucideIcon(if (b.sourceType == ImportSourceType.SMS) Lucide.MESSAGE_SQUARE_TEXT else Lucide.FILE_TEXT, size = 18.dp, tint = if (reverted) Ink.faded else Ink.primary)
            }
            Column(Modifier.weight(1f)) {
                val name = if (b.sourceType == ImportSourceType.SMS) t(TextKey.BANK_SMS_TITLE) else b.fileName
                BasicText(name, style = Type.bodyBold())
                BasicText(t(TextKey.IMPORT_REVIEW_HERO_META, t(batchSourceLabel(b.sourceType)), dayMonth(b.importedAt.take(10))), style = Type.caption().copy(color = Ink.muted))
            }
            Tag(t(if (reverted) TextKey.IMPORT_BATCHES_REVERTED_CHIP else TextKey.IMPORT_BATCHES_COMMITTED_CHIP), if (reverted) TagTone.MUTED else TagTone.NEW)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, n) in batchCounts(b)) Tag(t(TextKey.IMPORT_BATCHES_COUNT, t(label), sentenceNumber(n)), if (label == TextKey.IMPORT_BATCHES_ADDED) TagTone.NEW else TagTone.MUTED)
        }
        if (!reverted) QuietButton(t(TextKey.IMPORT_BATCHES_REVERT), onRevert, danger = true, height = 44.dp)
    }
}
