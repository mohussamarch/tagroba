package app.masroufy.ui.screens.imports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.countryPack
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.PdfStatementResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class StatementStage { PICK, READING, READY, ERROR }

/**
 * «كشف الحساب» (`StatementImport`): اختيار ملف (PDF من تطبيق البنك أو CSV بأعمدة معروفة) ⇒ القراءة على الجوال بتقدّم صفحة صفحة (الإلغاء آمن)
 * ⇒ «قُرئ»: البنك · العملة من البلد الحالي · المحفظة ⇒ «راجع العمليات» (`ImportReview`). الخطأ بسببه (كلمة مرور · مفيش عمليات · أعمدة مش معروفة
 * ⇒ «حدّد الأعمدة يدويًا»). مفيش حاجة بتتسجل قبل المراجعة.
 */
@Composable
fun StatementImportScreen() {
    val space = LocalSpace.current
    val deps = space.imports
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(StatementStage.PICK) }
    var fileName by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf<StatementReadyUi?>(null) }
    var pdfResult by remember { mutableStateOf<PdfStatementResult?>(null) }
    var csvText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var columns by remember { mutableStateOf(false) }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var wallets by remember(deps) { mutableStateOf<List<Wallet>>(emptyList()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(deps) { wallets = runCatching { deps.wallets() }.getOrDefault(emptyList()) }

    fun fail(message: String, cols: Boolean = false) {
        error = message
        columns = cols
        stage = StatementStage.ERROR
    }

    val picker = rememberFilePicker { file ->
        if (file == null) return@rememberFilePicker
        fileName = file.name
        pdfResult = null
        csvText = null
        columns = false
        when {
            file.tooLarge -> fail(t(TextKey.STATEMENT_IMPORT_TOO_LARGE))
            file.bytes.isEmpty() -> fail(t(TextKey.FILE_EMPTY))
            else -> when (statementKind(file.name, file.bytes)) {
                null -> fail(t(TextKey.STATEMENT_IMPORT_UNSUPPORTED))
                StatementKind.CSV -> {
                    val text = file.bytes.decodeToString()
                    csvText = text
                    ready = csvReadyUi(file.name, deps.csvTable(text))
                    wallet = defaultWallet(wallets)
                    stage = StatementStage.READY
                }
                StatementKind.PDF -> {
                    val reader = deps.pdf ?: return@rememberFilePicker fail(t(TextKey.STATEMENT_IMPORT_NO_PDF))
                    page = 0
                    pages = 0
                    stage = StatementStage.READING
                    job = scope.launch {
                        try {
                            val result = reader.read(file.bytes) { p, total -> page = p; pages = total }
                            pdfResult = result
                            ready = pdfReadyUi(file.name, result)
                            wallet = defaultWallet(wallets)
                            stage = StatementStage.READY
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            fail(e.message ?: t(TextKey.STATEMENT_IMPORT_ERR_TITLE))
                        }
                    }
                }
            }
        }
    }

    fun review() {
        val w = wallet ?: return
        val pdf = pdfResult
        val csv = csvText
        if (pdf != null) {
            nav.push(ImportReviewRoute(ImportDrafts.put(ImportDraft(fileName, pdfRequest(fileName, pdf, w), w.name))))
            return
        }
        if (csv == null) return
        scope.launch {
            busy = true
            try {
                // المعاينة هنا بتكشف الأعمدة المش معروفة قبل المراجعة (من غير كتابة)
                val preview = deps.importer.preview(csvRequest(fileName, csv, w))
                nav.push(ImportReviewRoute(ImportDrafts.put(ImportDraft(fileName, csvRequest(fileName, csv, w, preview.schema), w.name))))
            } catch (e: CancellationException) {
                throw e
            } catch (e: app.masroufy.core.SchemaError) {
                fail(e.message ?: t(TextKey.STATEMENT_IMPORT_ERR_TITLE), cols = true)
            } catch (e: Exception) {
                fail(e.message ?: t(TextKey.STATEMENT_IMPORT_ERR_TITLE))
            }
            busy = false
        }
    }

    val again = {
        job?.cancel()
        stage = StatementStage.PICK
        picker?.invoke()
        Unit
    }

    InnerScaffold(t(TextKey.STATEMENT_IMPORT_TITLE)) {
        item(key = "intro") { BasicText(t(TextKey.STATEMENT_IMPORT_INTRO), style = Type.of(13).copy(color = Ink.muted)) }
        when (stage) {
            StatementStage.PICK -> item(key = "pick") {
                PickBlock(pdfSchemas(countryPack(space.space.countryCode).statementSchemas), space.space.currency, picker)
            }
            StatementStage.READING -> item(key = "reading") {
                ReadingCard(fileName, page, pages) {
                    job?.cancel()
                    stage = StatementStage.PICK
                    toaster.show(t(TextKey.STATEMENT_IMPORT_CANCELLED), dark = true)
                }
            }
            StatementStage.READY -> ready?.let { r ->
                item(key = "ready") { ReadyCard(r, space.space.currency, wallets, wallet, { wallet = it }, busy, ::review, again) }
            }
            StatementStage.ERROR -> item(key = "error") {
                ErrorCard(fileName, error.orEmpty(), columns, onColumns = {
                    val csv = csvText ?: return@ErrorCard
                    nav.push(StatementColumnsRoute(ImportDrafts.put(ImportDraft(fileName, csv = csv))))
                }, onAgain = again)
            }
        }
        item(key = "batches") { LinkRow(t(TextKey.STATEMENT_IMPORT_BATCHES_LINK), { nav.push(ImportBatchesRoute) }, Modifier) }
    }
}
