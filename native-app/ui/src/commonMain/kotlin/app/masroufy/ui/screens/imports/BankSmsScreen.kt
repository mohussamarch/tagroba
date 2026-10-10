package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.AddOperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** اللي بيتصنّف من اللوحة: رسالة لسه مستنية (التصنيف بيتحفظ مع «سجّل الكل») أو عملية اتسجلت. */
private sealed interface PickFor {
    data class Line(val line: WaitLineUi) : PickFor

    data class Row(val row: RecordedUi) : PickFor
}

/**
 * «رسائل البنك» (`BankSms`): حالة القراءة · «بانتظار تأكيدك» (`SmsWaiting`) · اللي سُجّل تلقائيًا اليوم (لمعة نعناعي على اللي اتسجل دلوقتي)
 * بتصنيفه (المقترح يتأكد، والمتجر الجديد يتصنّف ⇒ «تم — سيُصنَّف دائمًا»). الترس ⇒ `BankSmsSettings`. الآيفون ⇒ «الصق رسالة».
 */
@Composable
fun BankSmsScreen() {
    val space = LocalSpace.current
    val deps = space.imports
    val currency = space.space.currency
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var ui by remember(deps) { mutableStateOf<BankSmsUi?>(null) }
    var categories by remember(deps) { mutableStateOf<List<Category>>(emptyList()) }
    var wallets by remember(deps) { mutableStateOf<List<Wallet>>(emptyList()) }
    var reload by remember(deps) { mutableStateOf(0) }
    var failure by remember(deps) { mutableStateOf<String?>(null) }
    val chosen = remember(deps) { mutableStateMapOf<String, String>() }
    var included by remember(deps) { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var handError by remember { mutableStateOf<Pair<String, String>?>(null) }
    var picking by remember { mutableStateOf<PickFor?>(null) }
    var bankSheet by remember { mutableStateOf<BankWaitingUi?>(null) }

    LaunchedEffect(deps, reload) {
        categories = runCatching { deps.categories.list() }.getOrDefault(categories)
        wallets = runCatching { deps.wallets() }.getOrDefault(wallets)
        val sms = deps.sms ?: run { ui = bankSmsUi(null, emptyList(), emptySet()); return@LaunchedEffect }
        try {
            val overview = sms.overview(record = true)
            val today = runCatching { sms.recordedToday() }.getOrDefault(emptyList())
            ui = bankSmsUi(overview, today, overview.fresh.toSet())
            failure = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ما بنعرضش «القراءة تعمل» من غير ما نعرف — الحالة بتفضل null والخطأ بسببه بس (قاعدة 10)
            failure = e.message ?: t(UiKey.IMPORTS_LOAD_FAILED)
        }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toaster.show(e.message ?: t(UiKey.IMPORTS_LOAD_FAILED), dark = true)
            }
            busy = false
            reload++
        }
    }

    val actions = WaitingActions(
        pickCategory = { picking = PickFor.Line(it) },
        pickWallet = { bankSheet = it },
        toggleSimilar = { line -> included = if (line.messageId in included) included - line.messageId else included + line.messageId },
        dismiss = { id -> act { deps.sms?.dismiss(listOf(id)); toaster.show(t(UiKey.BANK_SMS_DROPPED), dark = true) } },
        recordAll = {
            act {
                val n = deps.sms?.record(chosen.toMap(), included) ?: 0
                chosen.clear()
                included = emptySet()
                toaster.show(t(UiKey.BANK_SMS_RECORDED, opsCount(n)), dark = true)
            }
        },
        recordByHand = { f, amount ->
            scope.launch {
                when (val r = deps.sms?.recordByHand(f.messageId, f.sender, amount)) {
                    is AddOperationResult.Saved -> { handError = null; toaster.show(t(UiKey.BANK_SMS_HAND_SAVED), dark = true); reload++ }
                    is AddOperationResult.Invalid -> handError = f.messageId to r.message
                    null -> Unit
                }
            }
        },
    )

    InnerScaffold(
        t(UiKey.BANK_SMS_TITLE),
        actions = { SurfaceIconButton(Lucide.SETTINGS, t(UiKey.BANK_SMS_GEAR), { nav.push(BankSmsSettingsRoute) }) },
    ) {
        val u = ui
        item(key = "status") {
            SmsStatusBanner(u?.status, onSettings = { nav.push(BankSmsSettingsRoute) }, onPaste = { nav.push(SmsPasteRoute) })
        }
        if (u == null && failure == null) {
            item(key = "loading") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(120.dp))
                    Skeleton(Modifier.fillMaxWidth().height(220.dp))
                }
            }
            return@InnerScaffold
        }
        failure?.let { msg ->
            item(key = "failure") {
                TintedPanel(PanelTone.AMBER) {
                    PanelTitle(t(UiKey.IMPORTS_LOAD_FAILED), PanelTone.AMBER)
                    BasicText(msg, style = Type.of(13))
                    QuietButton(t(UiKey.IMPORTS_RETRY), { reload++ }, onTint = true, height = 44.dp)
                }
            }
        }
        if (u == null) return@InnerScaffold
        if (u.status != SmsStatus.UNAVAILABLE && (u.live || u.waitingCount > 0)) {
            item(key = "waiting") { SmsWaitingSection(u, currency, categories, chosen, included, busy, handError, actions) }
        }
        if (u.live && u.isEmpty) {
            item(key = "empty") { EmptyState(t(UiKey.BANK_SMS_EMPTY_TITLE), t(UiKey.BANK_SMS_EMPTY_BODY)) }
        }
        if (u.recorded.isNotEmpty()) {
            item(key = "recorded") {
                RecordedToday(
                    u.recorded, categories,
                    onConfirm = { row -> act { row.categoryId?.let { deps.transactions.setCategory(row.id, it) } } },
                    onCategorize = { picking = PickFor.Row(it) },
                )
            }
        }
        if (u.status != SmsStatus.UNAVAILABLE) {
            item(key = "footer") { SmsFooter { nav.push(app.masroufy.ui.screens.more.NotificationSettingsRoute) } }
        }
    }

    val pick = picking
    val title = when (pick) {
        is PickFor.Line -> pick.line.merchant.ifBlank { pick.line.sender }
        is PickFor.Row -> pick.row.merchant
        null -> ""
    }
    CategoryPickerSheet(
        visible = pick != null,
        title = t(UiKey.SMS_CATPICK_TITLE, title),
        categories = categories,
        selectedId = when (pick) { is PickFor.Line -> chosen[pick.line.messageId] ?: pick.line.categoryId; is PickFor.Row -> pick.row.categoryId; null -> null },
        onPick = { cat ->
            picking = null
            val sms = deps.sms
            when (pick) {
                is PickFor.Line -> {
                    chosen[pick.line.messageId] = cat.id
                    scope.launch { sms?.remember(pick.line.merchant, cat.id, pick.line.direction) }
                }
                is PickFor.Row -> act {
                    deps.transactions.setCategory(pick.row.id, cat.id)
                    sms?.remember(pick.row.merchant, cat.id, pick.row.direction)
                }
                null -> Unit
            }
            if (title.isNotBlank()) toaster.show(t(UiKey.BANK_SMS_REMEMBERED, title, cat.name), dark = true)
        },
        onDismiss = { picking = null },
    )
    BankWalletSheet(
        bank = bankSheet,
        wallets = wallets,
        onDismiss = { bankSheet = null },
        onSave = { bank, wallet ->
            bankSheet = null
            act {
                val r = deps.sms?.chooseWallet(bank.sender, wallet.id)
                toaster.show(t(UiKey.BANK_SMS_WALLET_SAVED, opsCount(r?.recorded ?: 0), wallet.name), dark = true)
            }
        },
    )
}
