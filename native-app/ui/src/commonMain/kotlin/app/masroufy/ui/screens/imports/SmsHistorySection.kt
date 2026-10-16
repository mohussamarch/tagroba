package app.masroufy.ui.screens.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.IsoDate
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.choiceArea
import app.masroufy.ui.overlay.rememberChoice
import app.masroufy.ui.screens.people.DateField
import app.masroufy.ui.screens.people.dayMonthYear
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.SmsHistoryQuestion
import app.masroufy.usecase.SmsHistoryReport
import app.masroufy.usecase.SmsRunProgress
import app.masroufy.usecase.shapeMessageIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** من إمتى (§83 — رد المالك «يسألني أنا من إمتى»): رصيد البداية · أقدم رسالة · يوم تاني. */
private enum class HistoryFrom { OPENING, OLDEST, OTHER }

/**
 * **§83 «اقرأ اللي فات؟»** فوق «رسائل البنك» (`BankSms`): السؤال لما بنك مربوط لسه ما اتسألش وعلى الجوال رسايل منه أقدم من القراية ⇒ «أيوه» (ولو فيه
 * رسايل قبل رصيد البداية: «من إمتى؟» — أقدم رسالة ورصيد البداية ويوم تاني) أو «لأ، من دلوقتي بس». بعد القراية: **قايمة واحدة** فيها رسالة
 * واحدة من كل شكل جديد و«+N زيها» و«أكّد الأشكال» (وضع التعلّم §77-A) — مش إشعار لكل رسالة. كل الحساب في `SmsHistory`؛ الشاشة بتعرض بس.
 * [onChanged] = الصفحة تحمّل تاني (اللي مستني تحت اتغيّر).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SmsHistorySection(sms: SmsDeps, currency: Currency, today: IsoDate, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var question by remember(sms) { mutableStateOf<SmsHistoryQuestion?>(null) }
    var report by remember(sms) { mutableStateOf<SmsHistoryReport?>(null) }
    var readFrom by remember(sms) { mutableStateOf<IsoDate?>(null) }
    var busy by remember(sms) { mutableStateOf(false) }
    // r051: تقدم «أكّد الأشكال» صفحة صفحة (اللي اتسجل · المكرر · اللي لسه في الصندوق) — العدّ بيتحرك والشغل شغال
    var live by remember(sms) { mutableStateOf<SmsRunProgress?>(null) }

    LaunchedEffect(sms) {
        question = runCatching { sms.historyQuestion() }.getOrNull()
        if (question == null) report = runCatching { sms.historyReport() }.getOrNull()
    }

    fun act(block: suspend ((SmsRunProgress) -> Unit) -> SmsHistoryReport?) {
        scope.launch {
            busy = true
            live = null
            try {
                // التقدم بييجي من خيط الشغل ⇒ بيتحط في الحالة على خيط الشاشة (مرة لكل صفحة، مش لكل عملية)
                report = block { p -> scope.launch { if (busy) live = p } }
                question = null
                onChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toaster.show(e.message ?: t(UiKey.IMPORTS_LOAD_FAILED), dark = true)
            }
            busy = false
            live = null
        }
    }

    val q = question
    if (q != null) {
        HistoryQuestionPanel(
            q, today, busy,
            onNo = { act { sms.historyDecline(q.senders); null } },
            onStart = { day -> readFrom = day ?: q.oldestDay; act { p -> sms.historyStart(q.senders, day, p) } },
        )
        return
    }
    val r = report ?: return
    HistoryReportPanel(r, readFrom, currency, busy, live) { act { p -> sms.confirmShapes(r.shapeMessageIds, p) } }
}

/**
 * سؤال «اقرأ اللي فات؟» نفسه (و«من إمتى؟» لو فيه رسايل قبل رصيد البداية) — في «رسائل البنك» وفي لوحة التسليم بعد أول إذن (r041 A8).
 * [onStart] بيوصله يوم البداية (null = من أقدم رسالة).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoryQuestionPanel(q: SmsHistoryQuestion, today: IsoDate, busy: Boolean, onNo: () -> Unit, onStart: (IsoDate?) -> Unit) {
    var choosing by remember(q) { mutableStateOf(false) }
    var from by remember(q) { mutableStateOf(HistoryFrom.OPENING) }
    var other by remember(q) { mutableStateOf<IsoDate?>(null) }
    // «من إمتى؟» مفتوح ⇒ الضغط برّه اللوحة أو الرجوع بيرجّع السؤال الأولاني ويلغي اللي اتختار من غير «ابدأ» (HANDOVER §6.5 بند 11)
    val choice = rememberChoice(choosing && !busy, onClose = { choosing = false; from = HistoryFrom.OPENING; other = null })
    run {
        TintedPanel(PanelTone.MINT, Modifier.choiceArea(choice)) {
            PanelTitle(t(if (choosing) UiKey.SMS_HISTORY_FROM_TITLE else UiKey.SMS_HISTORY_TITLE), PanelTone.MINT)
            val opening = q.opening
            if (!choosing || opening == null) {
                BasicText(t(UiKey.SMS_HISTORY_BODY, dayMonthYear(q.oldestDay)), style = Type.of(13, lineHeight = 1.7))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton(t(UiKey.SMS_HISTORY_NO), onNo, Modifier.weight(1f), enabled = !busy, onTint = true)
                    PrimaryButton(
                        t(UiKey.SMS_HISTORY_YES),
                        { if (q.askStart) choosing = true else onStart(null) },
                        Modifier.weight(1f), loading = busy,
                    )
                }
            } else {
                BasicText(t(UiKey.SMS_HISTORY_FROM_BODY, dayMonthYear(q.oldestDay), opening.walletName, dayMonthYear(opening.openingAt)), style = Type.of(13, lineHeight = 1.7))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip(t(UiKey.SMS_HISTORY_FROM_OPENING, dayMonthYear(opening.openingAt)), from == HistoryFrom.OPENING, { from = HistoryFrom.OPENING }, height = 44.dp)
                    SelectChip(t(UiKey.SMS_HISTORY_FROM_OLDEST, dayMonthYear(q.oldestDay)), from == HistoryFrom.OLDEST, { from = HistoryFrom.OLDEST }, height = 44.dp)
                    SelectChip(t(UiKey.SMS_HISTORY_FROM_OTHER), from == HistoryFrom.OTHER, { from = HistoryFrom.OTHER }, height = 44.dp)
                }
                if (from == HistoryFrom.OTHER) DateField(other, today, { other = it })
                val day = when (from) {
                    HistoryFrom.OPENING -> opening.openingAt
                    HistoryFrom.OLDEST -> q.oldestDay
                    HistoryFrom.OTHER -> other
                }
                PrimaryButton(
                    if (day == null) t(UiKey.SMS_HISTORY_YES) else t(UiKey.SMS_HISTORY_START, dayMonthYear(day)),
                    { day?.let { d -> onStart(d) } },
                    Modifier.fillMaxWidth(), enabled = day != null, loading = busy,
                )
            }
            if (busy) BasicText(t(UiKey.SMS_HISTORY_READING), style = Type.caption().copy(color = Ink.muted))
        }
    }
}

/** بعد القراية: العدّ و«قايمة واحدة» فيها رسالة من كل شكل جديد و«أكّد الأشكال» (§77-A). [started] = يوم البداية لو المالك لسه قاري. */
@Composable
private fun HistoryReportPanel(r: SmsHistoryReport, started: IsoDate?, currency: Currency, busy: Boolean, live: SmsRunProgress?, onConfirm: () -> Unit) {
    if (started == null && r.shapes.isEmpty() && live == null) return
    TintedPanel(PanelTone.MINT) {
        if (started != null) PanelTitle(t(UiKey.SMS_HISTORY_DONE_TITLE, dayMonthYear(started)), PanelTone.MINT)
        // r051: والشغل شغال العدّ من التقدم (اللي لسه في الصندوق بيقل) — وبعده «بانتظارك» = العدد الحقيقي في الصندوق كله
        if (live != null) {
            BasicText(t(UiKey.SMS_HISTORY_COUNTS, sentenceNumber(live.recorded), sentenceNumber(live.duplicates), sentenceNumber(live.queued)), style = Type.of(13))
            BasicText(t(UiKey.SMS_HISTORY_READING), style = Type.caption().copy(color = Ink.muted))
        } else if (started != null) {
            BasicText(t(UiKey.SMS_HISTORY_COUNTS, sentenceNumber(r.recorded), sentenceNumber(r.duplicates), sentenceNumber(r.waiting)), style = Type.of(13))
        }
        if (r.shapes.isNotEmpty()) {
            BasicText(t(UiKey.SMS_HISTORY_SHAPES_TITLE), style = Type.of(14, FontWeight.Bold))
            if (r.sameShape > 0) BasicText(t(UiKey.SMS_HISTORY_SHAPES_BODY, msgsCount(r.sameShape)), style = Type.of(13, lineHeight = 1.7))
            FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                r.shapes.forEachIndexed { i, shape ->
                    if (i > 0) Divider()
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(shape.merchant.ifBlank { shape.sender }, Modifier.weight(1f), style = Type.bodyBold(), maxLines = 1)
                            AmountText(shape.amountMinor, currency, tone = toneOf(shape.direction), size = 14)
                        }
                        val more = if (shape.count > 1) " · " + t(UiKey.SMS_HISTORY_SHAPE_MORE, sentenceNumber(shape.count - 1)) else ""
                        BasicText(shape.sender + " · " + dayMonth(shape.date) + more, style = Type.caption().copy(color = Ink.muted))
                    }
                }
            }
            PrimaryButton(t(UiKey.SMS_HISTORY_CONFIRM), onConfirm, Modifier.fillMaxWidth(), loading = busy)
        } else if (r.waiting > 0) {
            BasicText(t(UiKey.SMS_HISTORY_LEFT, msgsCount(r.waiting)), style = Type.of(13).copy(color = Ink.muted))
        }
        if (r.unread > 0) BasicText(t(UiKey.SMS_HISTORY_UNREAD, msgsCount(r.unread)), style = Type.of(13).copy(color = Ink.muted))
    }
}
