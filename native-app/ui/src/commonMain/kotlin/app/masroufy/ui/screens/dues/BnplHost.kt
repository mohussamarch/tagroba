package app.masroufy.ui.screens.dues

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.InstallmentError
import app.masroufy.core.UiKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.imports.CategoryPickerSheet
import app.masroufy.ui.screens.imports.pickableCategories
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.usecase.BnplAnswer
import app.masroufy.usecase.BnplAsk
import app.masroufy.usecase.BnplCharges
import app.masroufy.usecase.BnplSettle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * r051 — لوحة «ادفع بعدين» **بتطلع لوحدها** لما خصم من تابي أو تمارا أو زيهم يتسجل (رسالة بنك في الخلفية أو في «رسائل البنك» أو «+»
 * بإيدك) — مش من زرار تجربة. عايشة في الهيكل (`AppShell`) وبتقرا عند فتح البلد وبعد كل «البيانات اتغيرت» (`DataChanges` — «+» · رسايل
 * البنك · تغييرات السيرفر). الشغل كله على `Dispatchers.Default` (مرة لكل تغيير)، والحساب في `BnplCharges` بس:
 * - الخصم اللي اتربط لوحده بالقسط الجاي ⇒ رسالة «الدفعة 3 من 4 اتعلّمت اتدفعت» + «تراجع» (`undoAutoLink`).
 * - أول سؤال مستني ⇒ اللوحة («أول خصم» أو «أنهي مشترى»). «بعدين» أو السحب لتحت ⇒ مستخبي لحد ما التطبيق يتفتح تاني.
 */
@Composable
fun BnplHost() {
    val space = LocalSpace.current
    val bnpl = space.dues.bnpl ?: return
    val changes = LocalDataChanges.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var asks by remember(bnpl) { mutableStateOf<List<BnplAsk>>(emptyList()) }
    val later = remember(bnpl) { mutableStateListOf<String>() }
    var categories by remember(bnpl) { mutableStateOf<List<Category>>(emptyList()) }
    var today by remember(bnpl) { mutableStateOf("") }
    LaunchedEffect(bnpl, changes.version) {
        val r = settleOff(bnpl) ?: return@LaunchedEffect
        today = runCatching { space.shell.today() }.getOrDefault(today)
        if (r.autoLinked.isNotEmpty()) {
            DuesChanges.bump()
            val last = r.autoLinked.last()
            toaster.show(t(UiKey.BNPL2_AUTO_LINKED, sentenceNumber(last.number), sentenceNumber(last.count), last.planName), t(UiKey.BNPL2_UNDO)) {
                scope.launch {
                    runCatching { withContext(Dispatchers.Default) { r.autoLinked.forEach { bnpl.undoAutoLink(it.transactionId, it.planId) } } }
                    toaster.show(t(UiKey.BNPL2_UNDONE), dark = true)
                    DuesChanges.bump()
                    changes.changed()
                }
            }
        }
        if (r.asks.isNotEmpty() && categories.isEmpty()) categories = runCatching { space.imports.categories.list() }.getOrDefault(emptyList())
        asks = r.asks
    }
    val ask = asks.firstOrNull { it.transaction.id !in later }
    val chips = remember(categories) { bnplCategoryChips(categories) }
    val state = remember(ask?.transaction?.id, chips) { ask?.let { BnplSheetState(it, chips) } }
    var picking by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val maxBody = with(density) { (LocalWindowInfo.current.containerSize.height * 0.62f).toDp() }.coerceAtLeast(280.dp)

    fun answer(a: BnplAnswer) {
        later += a.transactionId
        scope.launch {
            try {
                val d = withContext(Dispatchers.Default) { bnpl.answer(a) }
                val name = d.planName
                val left = d.remainingMinor
                val currency = d.currency
                toaster.show(
                    when {
                        name != null && left != null && currency != null -> t(UiKey.BNPL2_DONE_TOAST, name, amountLabel(left, currency))
                        a is BnplAnswer.Skip -> t(UiKey.BNPL2_SKIPPED)
                        else -> t(UiKey.BNPL2_NORMAL_DONE)
                    },
                    dark = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // الرد ما اتحفظش ⇒ اللوحة ترجع (رسالة الخطأ لو مكتوبة للمالك بس)
                later.remove(a.transactionId)
                (e as? InstallmentError)?.message?.let { toaster.show(it, dark = true) }
            }
            DuesChanges.bump()
            changes.changed()
        }
    }

    val title = state?.let { t(if (it.setup) UiKey.BNPL2_SETUP_TITLE else UiKey.BNPL2_WHICH_TITLE) } ?: ""
    Sheet(state != null && !picking, onDismiss = { state?.let { later += it.ask.transaction.id } }, title = title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 14.dp) {
        val s = state ?: return@Sheet
        val t0 = s.ask.transaction
        BnplSheetContent(
            s, today.ifEmpty { t0.occurredAt.take(10) }, chips,
            preview = { n, c -> bnpl.preview(t0.amountMinor, t0.currency, t0.occurredAt.take(10), n, c) },
            maxBody = maxBody,
            onOtherCategory = { picking = true },
            onAnswer = ::answer,
            onLater = { later += t0.id },
        )
    }
    CategoryPickerSheet(
        visible = state != null && picking,
        title = t(UiKey.BNPL2_CAT_LABEL),
        categories = pickableCategories(categories),
        selectedId = state?.category?.id,
        onPick = { state?.category = it; picking = false },
        onDismiss = { picking = false },
    )
}

/** `settle` برّه خيط الشاشة — الخطأ ما بيوقعش الهيكل (اللوحة بس ما بتطلعش). */
private suspend fun settleOff(bnpl: BnplCharges): BnplSettle? = try {
    withContext(Dispatchers.Default) { bnpl.settle() }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
