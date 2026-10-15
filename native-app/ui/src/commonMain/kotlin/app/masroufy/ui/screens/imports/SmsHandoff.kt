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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.ui.app.LocalPermissions
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.knownBankSenders
import app.masroufy.usecase.sendersWithKnown
import kotlinx.coroutines.launch

/**
 * تسليم «رسائل البنك» بعد أول تشغيل (قرار المالك 2026-10-15 مساءً — OVERRIDES §91): اختار «رسائل البنك» مصدر ⇒ بعد ما الرحلة تخلص
 * (بأي زرار) شرح التطبيق نفسه ([SmsPermissionSheet]) ثم نافذة النظام — الوعد «سنطلب الإذن في الخطوة التالية» بيتنفّذ. سمح ⇒ بنكه
 * بيتسجّل لوحده بمرسلينه المعروفين ([connectKnownBanks]). رفض أو قفل الشرح ⇒ كارت هادي في الرئيسية «اسمح لاحقًا» ([SmsAllowCard]).
 * [pending] = الحالة دي في الجلسة دي بس (مش متخزنة — سؤال للمالك في HANDOVER). [asked] = الشرح اتعرض مرة ⇒ الكارت بدل الشرح.
 */
data class HandoffState(val pending: Boolean = false, val asked: Boolean = false)

/** اللي يظهر دلوقتي: لا حاجة · الشرح (لوحة) · الكارت في الرئيسية. */
enum class HandoffView { NONE, EXPLAIN, CARD }

/** [granted] = الإذن موجود (⇒ التسجيل على طول من غير شرح) · [readable] = الجهاز بيقرا رسايل (الآيفون لأ). */
fun handoffView(st: HandoffState, granted: Boolean, readable: Boolean): HandoffView = when {
    !st.pending || !readable || granted -> HandoffView.NONE
    !st.asked -> HandoffView.EXPLAIN
    else -> HandoffView.CARD
}

/** حالة التسليم في الجلسة (الرئيسية والهيكل بيقروها). */
object SmsHandoff {
    var state by mutableStateOf(HandoffState())

    /** «رسائل البنك» في أول تشغيل ⇒ الشرح أول ما الرحلة تخلص. */
    fun start() {
        state = HandoffState(pending = true)
    }

    /** الشرح اتقفل أو الإذن اترفض ⇒ الكارت في الرئيسية. */
    fun asked() {
        state = state.copy(asked = true)
    }

    /** «اسمح الآن» في الكارت ⇒ الشرح تاني. */
    fun askAgain() {
        state = state.copy(asked = false)
    }

    /** اتسجّل أو قال «لاحقًا» في الكارت. */
    fun done() {
        state = HandoffState()
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

/** على مستوى الهيكل: الشرح ثم نافذة النظام، والتسجيل لما الإذن يبقى موجود (حتى لو كان موجود قبل كده). */
@Composable
fun SmsHandoffHost() {
    val st = SmsHandoff.state
    if (!st.pending) return
    val deps = LocalSpace.current
    val sms = deps.imports.sms
    val permissions = LocalPermissions.current
    val toaster = LocalToaster.current
    val changes = LocalDataChanges.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }

    suspend fun connect() {
        val s = sms ?: return SmsHandoff.done()
        val n = runCatching {
            val current = s.overview(record = false).inbox.senders
            connectKnownBanks(s, deps.imports.wallets(), deps.space.countryCode, current)
        }.getOrDefault(0)
        SmsHandoff.done()
        toaster.show(t(if (n > 0) UiKey.SMS_HANDOFF_ON else UiKey.SMS_HANDOFF_ALLOWED), dark = true)
        changes.changed()
    }

    val granted = permissions.hasSms()
    LaunchedEffect(st.pending, granted, deps.space.id) { if (sms != null && granted) connect() }
    SmsPermissionSheet(
        visible = handoffView(st, granted, sms != null) == HandoffView.EXPLAIN,
        denied = denied,
        onDismiss = { SmsHandoff.asked() },
        onAllow = {
            scope.launch {
                if (permissions.requestSms()) connect() else denied = true
            }
        },
        onPaste = { SmsHandoff.asked(); nav.push(SmsPasteRoute) },
    )
}

/** الكارت الهادي في الرئيسية لما الإذن اترفض أو الشرح اتقفل: «اسمح الآن» ⇒ الشرح تاني · «لاحقًا» ⇒ يختفي. */
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
