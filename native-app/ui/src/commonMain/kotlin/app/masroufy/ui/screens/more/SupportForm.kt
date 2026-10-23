package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.SupportChrome
import app.masroufy.core.SupportKind
import app.masroufy.core.FEEDBACK_TEXT_MAX
import app.masroufy.core.SupportMessage
import app.masroufy.usecase.SendSupportMessage
import app.masroufy.usecase.SupportSendResult
import androidx.compose.runtime.remember
import app.masroufy.ui.app.LocalApp
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** حالة الاستخدام: صندوق الجهاز + فايربيز، وفي المعاينة (من غير جهاز) صندوق الذاكرة من غير إرسال. */
@Composable
internal fun supportSender(): SendSupportMessage = LocalApp.current.support ?: androidx.compose.runtime.remember {
    SendSupportMessage(app.masroufy.memory.MemorySupportOutbox(), null, app.masroufy.memory.SequentialIdGenerator(), "preview")
}

/**
 * «ابعتلنا» (`SupportForm.dc.html`): نوعه (سؤال · اقتراح · مشكلة — السعودي «شكوى») + نص ⇐ «ابعت» ⇐ «اتبعت». بيتركّب جوه الأسئلة الشائعة
 * ([prefill] = كلمة البحث) وفي صفحة «الدعم». النسخة على الجهاز دايمًا + `inbox` على فايربيز (OVERRIDES §114): «اتبعت» بعد تأكيد السيرفر بس،
 * وإلا «محفوظ وهيتبعت لما النت يرجع» (دورة الخلفية بتعيد المحاولة).
 */
@Composable
internal fun SupportForm(egypt: Boolean, prefill: String = "", modifier: Modifier = Modifier) {
    val chrome = androidx.compose.runtime.remember(egypt) { SupportChrome(egypt) }
    val sender = supportSender()
    val space = LocalSpace.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(SupportKind.QUESTION.name) }
    var text by rememberSaveable { mutableStateOf(prefill) }
    // null = لسه · SENT / QUEUED (اسم الحالة — يتحفظ مع الشاشة)
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val current = SupportKind.valueOf(kind)
    if (result != null) {
        val ok = result == SupportSendResult.SENT.name
        FloatingCard(modifier.fillMaxWidth().heightIn(min = 52.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(if (ok) Lucide.CHECK else Lucide.CLOCK, size = 18.dp, tint = if (ok) Ink.primary else Ink.muted)
                BasicText(
                    if (ok) chrome.sent else chrome.queued,
                    style = Type.of(15, FontWeight.Bold).copy(color = if (ok) Ink.primary else Ink.muted, textAlign = TextAlign.Center),
                )
            }
        }
        return
    }
    FloatingCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(chrome.title, style = Type.of(16, FontWeight.Bold))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in SupportKind.entries) SelectChip(k.label(egypt), current == k, { kind = k.name }, Modifier.weight(1f), height = 40.dp)
            }
            TextInput(text, { text = it.take(FEEDBACK_TEXT_MAX) }, placeholder = current.hint(egypt), height = 112.dp, textSize = 15)
            PrimaryButton(chrome.send, onClick = {
                val message = text.trim()
                sending = true
                scope.launch {
                    val day = runCatching { space.shell.today() }.getOrDefault("")
                    val r = runCatching { sender.send(SupportMessage(current, message, day, space.space.countryCode)) }.getOrDefault(SupportSendResult.QUEUED)
                    result = r.name
                    sending = false
                }
            }, Modifier.fillMaxWidth(), enabled = text.isNotBlank() && !sending)
            BasicText(chrome.note, style = Type.caption().copy(color = Ink.muted))
        }
    }
}
