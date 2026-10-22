package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import app.masroufy.core.ProblemCode
import app.masroufy.problems.Problems
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import app.masroufy.ui.text.BasicText
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
import app.masroufy.ui.app.LocalPermissions
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
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.core.SmsKind
import app.masroufy.ui.screens.operations.EmployerPickSheet
import app.masroufy.ui.screens.operations.TransferPartyCard
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    var wallets by remember(deps) { mutableStateOf<List<Wallet>>(emptyList()) }
    var reload by remember(deps) { mutableStateOf(0) }
    // r06-1a3 «القراية في الخلفية»: آخر قراية للخلفية (سطر الساعة) · كارت «فيه رسايل فاتته» (بيتحسب مرة عند الفتح من مسح الصندوق نفسه)
    var lastRead by remember(deps) { mutableStateOf<LastReadState?>(null) }
    var away by remember(deps) { mutableStateOf<AwayState>(AwayState.Hidden) }
    var awayChecked by remember(deps) { mutableStateOf(false) }
    val permissions = LocalPermissions.current
    var failure by remember(deps) { mutableStateOf<String?>(null) }
    val chosen = remember(deps) { mutableStateMapOf<String, String>() }
    var included by remember(deps) { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var handError by remember { mutableStateOf<Pair<String, String>?>(null) }
    var picking by remember { mutableStateOf<PickFor?>(null) }
    var bankSheet by remember { mutableStateOf<BankWaitingUi?>(null) }
    // OVERRIDES §91-1: «أيوه، ده حسابي» ⇒ لوحة اختيار المحفظة
    var accountLine by remember { mutableStateOf<WaitLineUi?>(null) }
    // r05: الاختيار بيسجّل (4a) · صفحة الصندوق (4b) · كارت الطرف بعد التسجيل (4c) · جهة الشغل بعد «أيوه ده راتبي» (3b) · «ادفع بعدين»
    val changes = LocalDataChanges.current
    val payer = LocalSpace.current.operations.payerKinds
    val r05 = remember(deps) { SmsR05(deps, toaster, changes::changed) { id -> payer?.view(id) != null } }
    DisposableEffect(deps) { onDispose { r05.flush() } }

    LaunchedEffect(deps, reload) {
        categories = runCatching { deps.categories.list() }.getOrDefault(categories)
        wallets = runCatching { deps.wallets() }.getOrDefault(wallets)
        val sms = deps.sms ?: run { ui = bankSmsUi(null, emptyList(), emptySet()); return@LaunchedEffect }
        try {
            // r05 (4b · 4d): الصفحة المختارة من الصندوق، والشغل بره خيط الشاشة (`SmsScreenExtras`) — الهيكل بيظهر الأول
            // r051: الصفحة بتظهر الأول من غير تسجيل، وبعدين التسجيل لوحده للجديد بس (`recordNew`) — كانت الشاشة مستنية لفة كاملة على الصندوق
            runCatching { sms.lastBackgroundRead() }.onSuccess { lastRead = LastReadState(it) }
            val overview = sms.overviewPage(record = false, skip = r05.skip)
            if (!awayChecked && r05.skip == 0 && overview.inbox.enabled && overview.inbox.permission) {
                awayChecked = true
                val m = runCatching { sms.missedWhileAway(overview.inbox) }.getOrNull()
                if (m != null && m.n > 0) away = AwayState.Missed(m.n, m.more)
            }
            if (r05.skip > 0 && r05.skip >= overview.inbox.count) { r05.skip = 0; reload++; return@LaunchedEffect }
            val today = runCatching { sms.recordedToday() }.getOrDefault(emptyList())
            ui = withContext(Dispatchers.Default) { bankSmsUi(overview, today, emptySet()) }
            failure = null
            // r06-1a3: لو فيه رسايل فاتته الكارت بيستنى «اقراهم دلوقتي» — من غير ما القراية التلقائية تسبقه
            val fresh = if (r05.skip > 0 || away.holdsAutoRecord) emptyList() else runCatching { sms.recordNew() }.getOrDefault(emptyList())
            if (fresh.isNotEmpty()) {
                val again = sms.overviewPage(record = false, skip = r05.skip)
                val todayAgain = runCatching { sms.recordedToday() }.getOrDefault(today)
                ui = withContext(Dispatchers.Default) { bankSmsUi(again, todayAgain, fresh.toSet()) }
                // r051: عملية اتسجلت هنا ⇒ الهيكل يقرا تاني (لوحة «ادفع بعدين» بتطلع لوحدها — `BnplHost`)
                changes.changed()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ما بنعرضش «القراءة تعمل» من غير ما نعرف — الحالة بتفضل null والخطأ بسببه بس (قاعدة 10)
            Problems.report(ProblemCode.SMS_READ_FAILED, e)
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


    // r06-1a3: «اقراهم دلوقتي» = القراية العادية اللي في الشاشة (`recordNew`) — وقت الخلفية فوق ما بيتغيّرش
    fun readAway() {
        val a = away as? AwayState.Missed ?: return
        away = AwayState.Reading(a.n, a.more)
        scope.launch {
            try {
                val fresh = deps.sms?.recordNew().orEmpty()
                away = AwayState.Done(fresh.size)
                if (fresh.isNotEmpty()) changes.changed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                away = a
                toaster.show(e.message ?: t(UiKey.IMPORTS_LOAD_FAILED), dark = true)
            }
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
        answerAccount = { line, mine ->
            val last4 = line.askLast4
            val walletId = line.walletId
            if (mine) accountLine = line
            else if (last4 != null && walletId != null) act {
                val r = deps.sms?.answerAccount(walletId, last4, mine = false, messageId = line.messageId)
                toaster.show(t(UiKey.SMS_ACCOUNT_NOT_MINE_SAVED, last4, opsCount(r?.recorded ?: 0)), dark = true)
            }
        },
        confirmLine = { line -> act { r05.confirmLine(line) } },
        salaryYes = { line -> act { r05.salaryYes(line) } },
        recordParty = { line -> act { r05.recordParty(line) } },
        older = { r05.skip += ui?.pageSize ?: 0; reload++ },
        newest = { r05.skip = 0; reload++ },
    )

    InnerScaffold(
        t(UiKey.BANK_SMS_TITLE),
        actions = { SurfaceIconButton(Lucide.SETTINGS, t(UiKey.BANK_SMS_GEAR), { nav.push(BankSmsSettingsRoute) }) },
    ) {
        val u = ui
        item(key = "status") {
            val a = away
            if (a != AwayState.Hidden && u?.status == SmsStatus.READING) {
                SmsAwayCard(a, onRead = ::readAway, onKeep = permissions::openBatterySettings)
            } else {
                SmsStatusBanner(
                    u?.status, onSettings = { nav.push(BankSmsSettingsRoute) }, onPaste = { nav.push(SmsPasteRoute) },
                    neverRead = lastRead?.last == null && lastRead != null,
                )
            }
        }
        val line = lastRead
        if (line != null && (u?.status == SmsStatus.READING || u?.status == SmsStatus.PERMISSION)) item(key = "last-read") { SmsLastReadLine(line) }
        if (u == null && failure == null) {
            item(key = "loading") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(120.dp))
                    Skeleton(quiet = true, modifier = Modifier.fillMaxWidth().height(220.dp))
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
        // §83: «اقرأ اللي فات؟» وقايمة «أكّد رسالة واحدة من كل شكل» (`SmsHistorySection`)
        val history = deps.sms
        if (history != null && u.status == SmsStatus.READING) {
            item(key = "history") { SmsHistorySection(history, currency, deps.smsToday()) { reload++ } }
        }
        if (r05.picks.items.isNotEmpty()) item(key = "picks") { PendingPicksPanel(r05.picks) }

        for ((party, id, outgoing) in r05.parties) item(key = "party-$id") { TransferPartyCard(party, id, onChanged = { reload++ }, outgoing = outgoing) }
        if (u.status != SmsStatus.UNAVAILABLE && (u.live || u.trueWaiting > 0)) {
            smsWaitingItems(u, currency, categoryById, chosen, included, busy, handError, actions, r05.skip, r05.picks.ids)
        }
        item(key = "money-what") { app.masroufy.ui.screens.operations.MoneyWhatPending(reload) } // r06-1b3: «الفلوس دي إيه؟» تحت المرتب
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
    // r05 (3a): العنوان اسم التاجر أو الطرف — عمره ما يبقى اسم المرسل («AlRajhiBank»)، ومن غير اسم ⇒ «تصنيف العملية»
    val title = when (pick) {
        is PickFor.Line -> pick.line.merchant
        is PickFor.Row -> pick.row.title
        null -> ""
    }
    CategoryPickerSheet(
        visible = pick != null,
        title = if (title.isBlank()) t(UiKey.SMS_R05_CATPICK_PLAIN) else t(UiKey.SMS_CATPICK_TITLE, title),
        categories = categories,
        selectedId = when (pick) { is PickFor.Line -> chosen[pick.line.messageId] ?: pick.line.categoryId; is PickFor.Row -> pick.row.categoryId; null -> null },
        onPick = { cat ->
            picking = null
            val sms = deps.sms
            when (pick) {
                is PickFor.Line -> {
                    // r05 (4a): الاختيار بيشيلها على طول، و«تراجع» 8 ثواني، وبعدها بتتسجل (وشكلها بيتعلّم)
                    r05.picks.add(scope, pick.line, cat) { id, catId -> r05.commitPick(id, catId); reload++ }
                    val party = pick.line.kind == SmsKind.TRANSFER_IN || pick.line.kind == SmsKind.TRANSFER_OUT
                    if (!party && pick.line.merchant.isNotBlank()) scope.launch { sms?.remember(pick.line.merchant, cat.id, pick.line.direction) }
                }
                is PickFor.Row -> act {
                    deps.transactions.setCategory(pick.row.id, cat.id)
                    // r041b P9: اسم شخص عمره ما يدخل ذاكرة التجار (CLAUDE.md الأمان 7)
                    if (!pick.row.party) sms?.remember(pick.row.merchant, cat.id, pick.row.direction)
                }
                null -> Unit
            }
            if (title.isNotBlank() && (pick as? PickFor.Row)?.row?.party != true) toaster.show(t(UiKey.BANK_SMS_REMEMBERED, title, cat.name), dark = true)
        },
        onDismiss = { picking = null },
    )
    val asked = accountLine
    // r041b P10: «محفظة جديدة لهذا الحساب» + كل محافظ البنك (نقل الأرقام بملاحظة صريحة) — مفيش لوحة فاضية من غير مخرج
    // فحص المحاكي (بند 8): محافظ بنك الرسالة بس — رسالة الراجحي ما تعرضش محفظة بنك تاني
    AccountWalletSheet(asked?.askLast4, accountSheetWallets(wallets, asked?.walletId), onDismiss = { accountLine = null }) { choice ->
        accountLine = null
        val last4 = asked?.askLast4
        val messageId = asked?.messageId
        val from = asked?.walletId
        if (last4 != null) act {
            when (choice) {
                is AccountPick.Existing -> {
                    val r = deps.sms?.answerAccount(choice.wallet.id, last4, mine = true, messageId = messageId, replace = choice.replaces != null)
                    toaster.show(t(UiKey.SMS_ACCOUNT_MINE_SAVED, last4, choice.wallet.name, opsCount(r?.recorded ?: 0)), dark = true)
                }
                AccountPick.NewWallet -> if (from != null) deps.sms?.accountNewWallet(from, last4, messageId)?.let { (w, r) ->
                    toaster.show(t(UiKey.SMS_ACCOUNT_NEW_SAVED, w.name, last4, opsCount(r.recorded)), dark = true)
                }
            }
        }
    }

    val employer = r05.employerTxn
    if (employer != null && LocalSpace.current.operations.incomeAnswers != null) {
        EmployerPickSheet(true, employer, onDismiss = { r05.employerTxn = null }) { done -> r05.employerTxn = null; toaster.show(done.title, dark = true); reload++ }
    }
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
