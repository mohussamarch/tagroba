package app.masroufy.ui.screens.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.masroufy.core.Category
import app.masroufy.core.ImportBatch
import app.masroufy.core.MatchingState
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.ImportPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * «مراجعة الكشف» (`ImportReview`): أثر المختار (المحفظة · المصروف · الدخل — من المعاينة) · العدّادات (جديد · مكرر · شبيه · تعارض · غير صالح) وتصفية بيها
 * · كل سطر بحالته وسببها وتصنيفه ومصدره · «قارن» (`ImportDuplicateSheet`) · تأكيد ذري مرة واحدة (الدوس تاني ما بيكررش). نفس الملف اتستورد ⇒ «فاضي».
 */
@Composable
fun ImportReviewScreen(draftId: Long) {
    val space = LocalSpace.current
    val deps = space.imports
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val draft = remember(draftId) { ImportDrafts[draftId] }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var ui by remember { mutableStateOf<ImportReviewUi?>(null) }
    var categories by remember(deps) { mutableStateOf<List<Category>>(emptyList()) }
    var selection by remember { mutableStateOf(emptySet<Int>()) }
    var filter by remember { mutableStateOf<MatchingState?>(null) }
    var compare by remember { mutableStateOf<ReviewLineUi?>(null) }
    var saving by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<ImportBatch?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }

    LaunchedEffect(deps, round) {
        val request = draft?.request ?: return@LaunchedEffect
        categories = runCatching { deps.categories.list() }.getOrDefault(categories)
        try {
            val p = deps.importer.preview(request)
            val u = importReviewUi(p)
            preview = p
            ui = u
            selection = u.defaultSelection
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: t(TextKey.IMPORTS_LOAD_FAILED)
        }
    }

    fun commit() {
        val request = draft?.request ?: return
        val p = preview ?: return
        if (saving || done != null || selection.isEmpty()) return
        saving = true
        filter = null
        scope.launch {
            try {
                val batch = deps.importer.commit(request, p, selection.sortedBy { it })
                done = batch
                val notAdded = (ui?.lines?.size ?: 0) - batch.counts.imported
                toaster.show(t(TextKey.IMPORT_REVIEW_SAVED_TOAST, opsCount(batch.counts.imported), opsCount(maxOf(0, notAdded))), dark = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: t(TextKey.IMPORTS_LOAD_FAILED)
            }
            saving = false
        }
    }

    InnerScaffold(t(TextKey.IMPORT_REVIEW_TITLE)) {
        if (draft?.request == null) {
            item(key = "gone") { EmptyState(t(TextKey.IMPORTS_DRAFT_GONE), t(TextKey.IMPORTS_DRAFT_GONE_BODY)) }
            return@InnerScaffold
        }
        error?.let { msg ->
            item(key = "error") {
                TintedPanel(PanelTone.AMBER) {
                    PanelTitle(t(TextKey.IMPORT_REVIEW_ERR_TITLE), PanelTone.AMBER)
                    BasicText(msg, style = Type.of(13).copy(color = Ink.focus))
                    BasicText(t(TextKey.IMPORT_REVIEW_ERR_BODY), style = Type.of(13).copy(color = Ink.focus))
                    QuietButton(t(TextKey.IMPORT_REVIEW_REDO), { round++; toaster.show(t(TextKey.IMPORT_REVIEW_REDONE), dark = true) }, onTint = true, height = 44.dp)
                }
            }
        }
        val u = ui
        if (u == null) {
            if (error == null) item(key = "loading") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Skeleton(Modifier.fillMaxWidth().height(170.dp)); Skeleton(Modifier.fillMaxWidth().height(260.dp)) } }
            return@InnerScaffold
        }
        if (u.alreadyImported) {
            val prev = u.previous
            item(key = "empty") {
                EmptyState(
                    t(TextKey.IMPORT_REVIEW_EMPTY_TITLE),
                    prev?.let { t(TextKey.IMPORT_REVIEW_EMPTY_BODY, dayMonth(it.importedAt.take(10)), opsCount(it.counts.imported), opsCount(maxOf(0, it.counts.total - it.counts.imported))) },
                    action = { TonalButton(t(TextKey.IMPORT_BATCHES_TITLE), { nav.push(ImportBatchesRoute) }) },
                )
            }
            return@InnerScaffold
        }
        item(key = "hero") { ImpactHero(draft.fileName, draft.walletName.orEmpty(), selection.size, u.impactFor(selection), done, space.space.currency) }
        item(key = "counters") { Counters(u, filter) { filter = if (filter == it) null else it } }
        item(key = "lines") {
            ReviewLines(
                u.lines.filter { filter == null || it.state == filter }, categories, selection, locked = saving || done != null, currency = space.space.currency,
                onToggle = { n -> selection = if (n in selection) selection - n else selection + n },
                onCompare = { compare = it },
            )
        }
        item(key = "foot") { BasicText(t(TextKey.IMPORT_REVIEW_FOOT), style = Type.caption().copy(color = Ink.muted)) }
        item(key = "bar") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (done == null) {
                    PrimaryButton(
                        if (saving) t(TextKey.IMPORT_REVIEW_SAVING) else t(TextKey.IMPORT_REVIEW_COMMIT, opsCount(selection.size)), ::commit, Modifier.fillMaxWidth(),
                        enabled = selection.isNotEmpty() && error == null, loading = saving, height = 52.dp,
                    )
                } else {
                    TonalButton(t(TextKey.IMPORT_BATCHES_TITLE), { nav.push(ImportBatchesRoute) }, Modifier.fillMaxWidth())
                }
                val note = when {
                    done != null -> TextKey.IMPORT_REVIEW_NOTE_DONE
                    saving -> TextKey.IMPORT_REVIEW_NOTE_SAVING
                    else -> TextKey.IMPORT_REVIEW_NOTE_ATOMIC
                }
                BasicText(t(note), Modifier.fillMaxWidth(), style = Type.of(11).copy(color = Ink.muted, textAlign = TextAlign.Center))
            }
        }
    }

    ImportDuplicateSheet(
        line = compare,
        load = { id -> deps.transactions.load(id).transaction },
        currency = space.space.currency,
        onDecide = { line, include ->
            compare = null
            if (include != null) selection = if (include) selection + line.lineNumber else selection - line.lineNumber
        },
        onDismiss = { compare = null },
    )
}
