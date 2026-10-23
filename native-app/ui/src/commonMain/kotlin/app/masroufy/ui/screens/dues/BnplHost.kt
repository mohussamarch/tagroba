package app.masroufy.ui.screens.dues

import app.masroufy.core.BnplKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import app.masroufy.usecase.DueLinkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * r051 — لوحة «ادفع بعدين» **بتطلع لوحدها** لما خصم من تابي أو تمارا أو زيهم يتسجل (رسالة بنك في الخلفية أو في «رسائل البنك» أو «+»
 * بإيدك) — مش من زرار تجربة. عايشة في الهيكل (`AppShell`) وبتقرا عند فتح البلد وبعد كل «البيانات اتغيرت» (`DataChanges` — «+» · رسايل
 * البنك · تغييرات السيرفر). الشغل كله على `Dispatchers.Default`، والحساب في `BnplCharges` بس:
 * - القراية **ما بتتلغيش** لما تغيير جديد ييجي وهي شغالة (التغييرات بتتلم في قراية واحدة بعدها) ⇒ الربط اللي اتكتب رسالته و«تراجع» ما بيضيعوش.
 * - الخصم اللي اتربط لوحده بالقسط الجاي ⇒ رسالة «الدفعة 3 من 4 اتعلّمت اتدفعت» + «تراجع» (`undoAutoLink`) — واللوحة بتستنى الرسالة دي
 *   تخلص (أو «تراجع») عشان الرسالة ما تبقاش مدفونة ورا ستارة اللوحة.
 * - أول سؤال مستني ⇒ اللوحة («أول خصم» أو «أنهي مشترى»). «تخطّي» و«بعدين» أو السحب لتحت ⇒ مستخبي لكل بلد لوحدها، ومتفتكر بعد قفل
 *   البرنامج كمان ([BnplLater] — r06-polish).
 * - بعد أي رد اللوحة بتستخبى لحد قراية **بدأت بعد ما الرد اتحفظ** ⇒ مفيش سؤال قديم (خصم اتربط لوحده في نفس اللحظة) بيترد عليه.
 */
@Composable
fun BnplHost() {
    val space = LocalSpace.current
    val bnpl = space.dues.bnpl ?: return
    val changes = LocalDataChanges.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var asks by remember(bnpl) { mutableStateOf<List<BnplAsk>>(emptyList()) }
    val spaceId = space.space.id
    val later = BnplLater.ids(spaceId)
    var categories by remember(bnpl) { mutableStateOf<List<Category>>(emptyList()) }
    var today by remember(bnpl) { mutableStateOf("") }
    // بعد الرد: اللوحة مستخبية لحد قراية رقمها [freshFrom] أو أكبر
    var holding by remember(bnpl) { mutableStateOf(false) }
    var freshFrom by remember(bnpl) { mutableIntStateOf(0) }
    LaunchedEffect(bnpl) {
        snapshotFlow { changes.version }.conflate().collect { v ->
            val r = settleOff(bnpl)
            if (r != null) {
                today = runCatching { space.shell.today() }.getOrDefault(today)
                if (r.autoLinked.isNotEmpty()) {
                    DuesChanges.bump()
                    val last = r.autoLinked.last()
                    toaster.show(t(BnplKey.BNPL2_AUTO_LINKED, sentenceNumber(last.number), sentenceNumber(last.count), last.planName), t(BnplKey.BNPL2_UNDO)) {
                        scope.launch {
                            val ok = try {
                                withContext(Dispatchers.Default) { r.autoLinked.forEach { bnpl.undoAutoLink(it) } }
                                true
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                false
                            }
                            toaster.show(t(if (ok) BnplKey.BNPL2_UNDONE else BnplKey.BNPL2_UNDO_FAILED), dark = true)
                            DuesChanges.bump()
                            changes.changed()
                        }
                    }
                }
                if (r.asks.isNotEmpty() && categories.isEmpty()) categories = runCatching { space.imports.categories.list() }.getOrDefault(emptyList())
                asks = r.asks
            }
            if (v >= freshFrom) holding = false
        }
    }
    val ask = asks.firstOrNull { it.transaction.id !in later }
    val chips = remember(categories) { bnplCategoryChips(categories) }
    // fix053 (فحص 0.5.3 د): المفتاح السؤال كله مش معرّف العملية بس — خطة اتعملت للخصم اللي قبله بتجيب سؤال جديد لنفس الخصم (خطط أكتر)،
    // واللوحة كانت بتفضل بالخطط القديمة لحد قفل البرنامج
    val state = remember(ask, chips) { ask?.let { BnplSheetState(it, chips) } }
    var picking by remember { mutableStateOf(false) }
    var multi by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val maxBody = with(density) { (LocalWindowInfo.current.containerSize.height * 0.62f).toDp() }.coerceAtLeast(280.dp)
    val showing = bnplSheetShown(state != null, holding, toaster.holdsAction)

    fun answer(a: BnplAnswer) {
        BnplLater.add(spaceId, a.transactionId)
        holding = true
        scope.launch {
            try {
                val d = withContext(Dispatchers.Default) { bnpl.answer(a) }
                val name = d.planName
                val left = d.remainingMinor
                val currency = d.currency
                toaster.show(
                    if (name != null && left != null && currency != null) t(BnplKey.BNPL2_DONE_TOAST, name, amountLabel(left, currency)) else t(BnplKey.BNPL2_NORMAL_DONE),
                    dark = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // الرد ما اتحفظش ⇒ اللوحة ترجع، ورسالة الغلط اللي مكتوبة للمالك (الأقساط أو الربط) بتظهر
                BnplLater.remove(spaceId, a.transactionId)
                bnplErrorText(e)?.let { toaster.show(it, dark = true) }
            }
            DuesChanges.bump()
            freshFrom = changes.version + 1
            changes.changed()
        }
    }

    val title = state?.let { t(if (it.setup) BnplKey.BNPL2_SETUP_TITLE else BnplKey.BNPL2_WHICH_TITLE) } ?: ""
    Sheet(showing && !picking && multi == null, onDismiss = { state?.let { BnplLater.add(spaceId, it.ask.transaction.id) } }, title = title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 14.dp) {
        val s = state ?: return@Sheet
        val t0 = s.ask.transaction
        BnplSheetContent(
            s, today.ifEmpty { t0.occurredAt.take(10) }, chips,
            preview = { n, c, at -> bnpl.preview(t0.amountMinor, t0.currency, t0.occurredAt.take(10), n, c, at) },
            maxBody = maxBody,
            onOtherCategory = { picking = true },
            onAnswer = ::answer,
            onLater = { BnplLater.add(spaceId, t0.id) },
            onMulti = { multi = t0.id }.takeIf { space.dues.bnplSplit != null },
        )
    }
    BnplMultiSheet(multi.takeIf { showing }, onDismiss = { multi = null }) { undo, n ->
        multi = null
        BnplLater.add(spaceId, undo.transactionId)
        DuesChanges.bump()
        changes.changed()
        toaster.show(t(app.masroufy.core.R06Key.BNPL_MULTI_DONE, n.toString()), t(BnplKey.BNPL2_UNDO)) {
            scope.launch {
                val ok = runCatching { withContext(Dispatchers.Default) { space.dues.bnplSplit?.undo(undo) } }.isSuccess
                toaster.show(t(if (ok) BnplKey.BNPL2_UNDONE else BnplKey.BNPL2_UNDO_FAILED), dark = true)
                BnplLater.remove(spaceId, undo.transactionId)
                DuesChanges.bump()
                changes.changed()
            }
        }
    }
    CategoryPickerSheet(
        visible = showing && picking,
        title = t(BnplKey.BNPL2_CAT_LABEL),
        categories = pickableCategories(categories),
        selectedId = state?.category?.id,
        onPick = { state?.category = it; picking = false },
        onDismiss = { picking = false },
    )
}

/** اللوحة بتظهر لو فيه سؤال، ومفيش رد لسه بيتحفظ، ومفيش رسالة بزرار («تراجع») ظاهرة — الرسالة دي لازم تتداس. */
internal fun bnplSheetShown(hasAsk: Boolean, holding: Boolean, actionToast: Boolean): Boolean = hasAsk && !holding && !actionToast

/** رسالة الغلط للمالك: الأقساط (`InstallmentError`) والربط (`DueLinkError` — «العملية دي مربوطة بحاجة تانية» …). غير كده ⇒ null. */
internal fun bnplErrorText(e: Throwable): String? = when (e) {
    is InstallmentError -> e.message
    is DueLinkError -> e.message
    else -> null
}

/** `settle` برّه خيط الشاشة — الخطأ ما بيوقعش الهيكل (اللوحة بس ما بتطلعش). */
private suspend fun settleOff(bnpl: BnplCharges): BnplSettle? = try {
    withContext(Dispatchers.Default) { bnpl.settle() }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
