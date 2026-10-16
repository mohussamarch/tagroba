package app.masroufy.ui.screens.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.ui.app.LocalPermissions
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Route
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.SmsHistoryQuestion
import app.masroufy.usecase.knownBankSenders
import app.masroufy.usecase.sendersWithKnown
import kotlinx.coroutines.launch

/**
 * تسليم «رسائل البنك» بعد أول تشغيل (قرار المالك 2026-10-15 مساءً — OVERRIDES §91): اختار «رسائل البنك» مصدر ⇒ بعد ما الرحلة تخلص
 * (بأي زرار) شرح التطبيق نفسه ([SmsPermissionSheet]) ثم نافذة النظام — الوعد «سنطلب الإذن في الخطوة التالية» بيتنفّذ. سمح ⇒ بنكه
 * بيتسجّل لوحده بمرسلينه المعروفين ([connectKnownBanks]) ⇒ «نقرأ الرسايل اللي فاتت؟» (r041 A8). رفض أو قفل الشرح ⇒ كارت هادي في
 * الرئيسية «اسمح لاحقًا» ([SmsAllowCard]) بيرجع كل تشغيل لحد ما يسمح أو يقول «لاحقًا» (r041 A10 — متخزن على الجهاز).
 * القواعد من غير واجهة في `SmsHandoffRules.kt`.
 */
object SmsHandoff {
    var state by mutableStateOf(HandoffState())

    /** r041 A6: الشاشة اللي بعد التسليم (لوحة الشكل من «كمّل بروفايلك») — بتتفتح لما [lookMayOpen] تبقى true. */
    var next by mutableStateOf<Route?>(null)

    /** r041 A10: الكارت المتخزن اتقرا في التشغيلة دي. */
    internal var restored = false

    /** «رسائل البنك» في أول تشغيل ⇒ الشرح أول ما الرحلة تخلص. */
    fun start() {
        state = HandoffState(pending = true)
    }

    /** الشرح اتقفل أو الإذن اترفض ⇒ الكارت في الرئيسية. */
    fun asked() {
        state = state.copy(asked = true, requesting = false)
    }

    /** «اسمح الآن» في الكارت ⇒ الشرح تاني. */
    fun askAgain() {
        state = state.copy(asked = false)
    }

    /** نافذة إذن النظام مفتوحة ([on]) أو اتقفلت. */
    fun requesting(on: Boolean) {
        state = state.copy(requesting = on)
    }

    /** الإذن اتدى والبنوك اتربطت وعلى الجوال رسايل أقدم ⇒ سؤال اللي فات. */
    fun history() {
        state = state.copy(history = true, requesting = false)
    }

    /** اتسجّل (وسؤال اللي فات اتجاوب أو اتقفل) أو قال «لاحقًا» في الكارت. */
    fun done() {
        state = HandoffState()
    }

    /** r041 A6: افتح [route] بعد التسليم — على طول لو مفيش تسليم شغال. نقطة واحدة عامة (لوحة الشكل النهارده، أي شاشة بكره). */
    fun openAfter(route: Route, push: (Route) -> Unit) {
        if (state.pending) next = route else push(route)
    }
}

/**
 * البنوك المعروفة لمحافظ البلد ⇒ تشغيل القراءة بمرسلينها + كل مرسل على محفظته (لو البنك عليه محفظة واحدة). بيرجّع عدد المرسلين الجداد
 * (0 ⇒ مفيش بنك معروف — الشاشة بتقول «ضيف بنكك من إعداد القراءة»). لازم الإذن يكون موجود (الجهاز بيرفض التشغيل من غيره).
 */
suspend fun connectKnownBanks(sms: SmsDeps, wallets: List<Wallet>, country: String?, current: List<String>): Int {
    val known = knownBankSenders(wallets, country)
    if (known.isEmpty()) return 0
    val senders = sendersWithKnown(current, known)
    sms.enable(senders)
    for (k in known) if (k.walletId != null && k.sender in senders) sms.chooseWallet(k.sender, k.walletId)
    return known.count { it.sender in senders }
}

/**
 * على مستوى الهيكل: الشرح ثم نافذة النظام، والتسجيل لما الإذن يبقى موجود (حتى لو كان موجود قبل كده)، ثم سؤال اللي فات، وبعدها بس
 * الشاشة اللي مستنية ([SmsHandoff.next]). وكمان بيقرا ويكتب الكارت المتخزن على الجهاز.
 */
@Composable
fun SmsHandoffHost() {
    val deps = LocalSpace.current
    val sms = deps.imports.sms
    val permissions = LocalPermissions.current
    val nav = LocalNavigator.current
    val st = SmsHandoff.state
    val granted = permissions.hasSms()
    val readable = sms != null

    // r041 A10: أول التشغيل نرجّع الكارت المتخزن (لو مفيش تسليم بدأ دلوقتي)، وبعدها كل تغيير في الحالة بيتكتب على الجهاز
    LaunchedEffect(sms) {
        val s = sms ?: return@LaunchedEffect
        var last = runCatching { s.handoffCard() }.getOrNull()
        if (!SmsHandoff.restored) {
            SmsHandoff.restored = true
            if (!SmsHandoff.state.pending) SmsHandoff.state = restoredHandoff(last)
        }
        snapshotFlow { persistedHandoff(SmsHandoff.state) }.collect { v ->
            if (v != last && runCatching { s.setHandoffCard(v) }.isSuccess) last = v
        }
    }

    // r041 A6: الشاشة اللي بعد التسليم (لوحة الشكل) بتستنى الشرح ونافذة الإذن وسؤال اللي فات
    val mayOpen = lookMayOpen(st, granted, readable)
    val next = SmsHandoff.next
    LaunchedEffect(mayOpen, next) {
        if (mayOpen && next != null) {
            SmsHandoff.next = null
            nav.push(next)
        }
    }

    if (!st.pending) return
    val toaster = LocalToaster.current
    val changes = LocalDataChanges.current
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf<SmsHistoryQuestion?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun connect() {
        if (connecting) return
        val s = sms ?: return SmsHandoff.done()
        connecting = true
        val n = runCatching {
            val current = s.overview(record = false).inbox.senders
            connectKnownBanks(s, deps.imports.wallets(), deps.space.countryCode, current)
        }.getOrDefault(0)
        toaster.show(t(if (n > 0) UiKey.SMS_HANDOFF_ON else UiKey.SMS_HANDOFF_ALLOWED), dark = true)
        changes.changed()
        // r041 A8: رسايل وصلت قبل الإذن ⇒ «نقرأ الرسايل اللي فاتت؟» هنا على طول (مش لما المالك يلاقي «رسائل البنك»)
        val q = runCatching { s.historyQuestion() }.getOrNull()
        connecting = false
        if (q != null) {
            question = q
            SmsHandoff.history()
        } else SmsHandoff.done()
    }

    fun answer(block: suspend (SmsDeps) -> Unit) {
        val s = sms ?: return SmsHandoff.done()
        scope.launch {
            busy = true
            runCatching { block(s) }.onFailure { toaster.show(it.message ?: t(UiKey.IMPORTS_LOAD_FAILED), dark = true) }
            busy = false
            question = null
            changes.changed()
            SmsHandoff.done()
        }
    }

    LaunchedEffect(st.pending, granted, deps.space.id) { if (handoffNeedsConnect(st, granted, readable)) connect() }
    val view = handoffView(st, granted, readable)
    SmsPermissionSheet(
        visible = view == HandoffView.EXPLAIN,
        denied = denied,
        onDismiss = { SmsHandoff.asked() },
        onAllow = {
            scope.launch {
                SmsHandoff.requesting(true)
                val ok = runCatching { permissions.requestSms() }.getOrDefault(false)
                SmsHandoff.requesting(false)
                if (ok) connect() else denied = true
            }
        },
        onPaste = { SmsHandoff.asked(); nav.push(SmsPasteRoute) },
    )
    val q = question
    // قفل اللوحة من غير رد = مفيش قرار (السؤال بيفضل في «رسائل البنك») — مش «لأ»
    Sheet(view == HandoffView.HISTORY && q != null, { if (!busy) { question = null; SmsHandoff.done() } }, t(UiKey.SMS_HISTORY_TITLE), closeLabel = t(UiKey.SHELL_CLOSE)) {
        if (q != null) HistoryQuestionPanel(
            q, deps.shell.today(), busy,
            onNo = { answer { it.historyDecline(q.senders) } },
            onStart = { day ->
                answer { s ->
                    val r = s.historyStart(q.senders, day)
                    if (r != null) toaster.show(t(UiKey.SMS_HISTORY_COUNTS, sentenceNumber(r.recorded), sentenceNumber(r.duplicates), sentenceNumber(r.waiting)), dark = true)
                    // r041b P3 (ج): رسايل مستنية «أكّد الأشكال» ⇒ «رسائل البنك» على طول (قبل كده كانت بتستنى لحد ما يلاقيها في «المزيد»).
                    // «كمّل بروفايلك» (لو اختاره) بيتفتح فوقها بعد التسليم ([SmsHandoff.next]) — ولما يقفله بيلاقيها
                    if (openConfirmAfterHistory(r)) nav.push(BankSmsRoute)
                }
            },
        )
    }
}

/** الكارت الهادي في الرئيسية لما الإذن اترفض أو الشرح اتقفل: «اسمح الآن» ⇒ الشرح تاني · «لاحقًا» ⇒ يختفي (وما بيرجعش — r041 A10). */
@Composable
fun SmsAllowCard() {
    TintedPanel(PanelTone.QUIET) {
        PanelTitle(t(UiKey.SMS_HANDOFF_CARD_TITLE), PanelTone.QUIET)
        BasicText(t(UiKey.SMS_HANDOFF_CARD_BODY), style = Type.of(13, lineHeight = 1.6).copy(color = Ink.muted))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton(t(UiKey.SMS_HANDOFF_ALLOW), { SmsHandoff.askAgain() }, Modifier.weight(1f), onTint = true, height = 44.dp)
            QuietButton(t(UiKey.SMS_HANDOFF_LATER), { SmsHandoff.done() }, Modifier.weight(1f), onTint = true, height = 44.dp)
        }
    }
}

/** الكارت يظهر في الرئيسية؟ */
@Composable
fun smsAllowCardShown(): Boolean =
    handoffView(SmsHandoff.state, LocalPermissions.current.hasSms(), LocalSpace.current.imports.sms != null) == HandoffView.CARD
