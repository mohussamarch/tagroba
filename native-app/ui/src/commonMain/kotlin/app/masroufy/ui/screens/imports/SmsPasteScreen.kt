package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.dayMonth
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SegmentStyle
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.port.smsSenderKey
import app.masroufy.usecase.ImportPreview
import app.masroufy.usecase.SmsBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class PasteTab { PASTE, PERIOD }

private enum class PastePhase { INPUT, RESULTS, ERROR, DONE }

/**
 * «إضافة من الرسائل» (`SmsPaste`): «الصق رسالة» (كل الأجهزة — الآيفون ما بيسمحش بالقراءة) أو «اقرأ فترة» (أندرويد — من بنوك تختارها، ولو القراءة
 * التلقائية متوقفة). النتيجة: اللي اتفهم (جديد · مسجل سابقًا) واللي اتعدّى بسببه ⇒ المحفظة ⇒ «سجّل» (مفيش حاجة بتتسجل قبل الضغطة).
 */
@Composable
fun SmsPasteScreen() {
    val space = LocalSpace.current
    val deps = space.imports
    val reader = deps.readSms
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val canPeriod = reader?.available == true
    var tab by rememberSaveable { mutableStateOf(PasteTab.PASTE) }
    var text by rememberSaveable { mutableStateOf("") }
    /** §82-14: تاريخ الرسالة الملصوقة — لازم (null = لسه ما اتكتبش). بيفضل بعد «الصق رسالة أخرى» (غالبًا رسايل نفس اليوم). */
    var pastedOn by rememberSaveable { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf(PastePhase.INPUT) }
    var batch by remember { mutableStateOf<SmsBatch?>(null) }
    /** معاينة كل محفظة (§82-15 — كل بنك على محفظته) بمعرّف المحفظة. */
    var previews by remember { mutableStateOf<Map<Id, ImportPreview>>(emptyMap()) }
    /** المحفظة اللي المالك اختارها — للصق، ولبنوك «اقرأ فترة» اللي مالهاش محفظة مربوطة. */
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var wallets by remember(deps) { mutableStateOf<List<Wallet>>(emptyList()) }
    var ranges by remember(deps) { mutableStateOf<List<SmsRange>>(emptyList()) }
    var range by remember { mutableStateOf(SmsRangeKind.MONTH) }
    var senders by remember(deps) { mutableStateOf<List<String>>(emptyList()) }
    /** محفظة كل بنك مربوط (نفس التسجيل لوحده — `AutoRecordSms.walletOf`) بعد `smsSenderKey`. */
    var linked by remember(deps) { mutableStateOf<Map<String, Id>>(emptyMap()) }
    var off by remember { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(0) }
    var savedIn by remember { mutableStateOf("") }

    LaunchedEffect(deps) {
        wallets = runCatching { deps.wallets() }.getOrDefault(emptyList())
        ranges = runCatching { deps.smsRanges() }.getOrDefault(emptyList())
        val overview = deps.sms?.let { runCatching { it.overview(record = false) }.getOrNull() }
        senders = overview?.inbox?.senders.orEmpty()
        linked = overview?.senderWallets.orEmpty().mapNotNull { (s, w) -> w?.let { smsSenderKey(s) to it } }.toMap()
    }
    val period = tab == PasteTab.PERIOD
    // الأجزاء: الصق ⇒ كله على المحفظة المختارة · «اقرأ فترة» ⇒ كل بنك على محفظته، والباقي على المختارة (`ReadBankSms.byWallet`)
    val parts = remember(batch, wallet, linked, period) {
        val b = batch
        if (b == null || reader == null) emptyList() else reader.byWallet(b, { if (period) linked[it] else null }, wallet?.id)
    }
    // المعاينة على كل محفظة لوحدها (منع التكرار بيتحسب على المحفظة)
    LaunchedEffect(parts) {
        previews = parts.mapNotNull { part ->
            val w = wallets.firstOrNull { it.id == part.walletId } ?: return@mapNotNull null
            runCatching { w.id to deps.importer.preview(smsRequest(part.batch, w)) }.getOrNull()
        }.toMap()
    }

    fun read(block: suspend () -> SmsBatch) {
        scope.launch {
            busy = true
            error = null
            try {
                val b = block()
                batch = b
                wallet = defaultWallet(wallets)
                phase = if (b.rows.isEmpty()) PastePhase.ERROR else PastePhase.RESULTS
                if (b.rows.isEmpty()) error = b.skipped.firstOrNull()?.reason
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                phase = PastePhase.ERROR
                error = e.message
            }
            busy = false
        }
    }

    InnerScaffold(t(UiKey.SMS_PASTE_TITLE)) {
        item(key = "tabs") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedTabs(
                    listOf(PasteTab.PASTE to t(UiKey.SMS_PASTE_TAB_PASTE), PasteTab.PERIOD to t(UiKey.SMS_PASTE_TAB_PERIOD)),
                    tab, { if (it == PasteTab.PASTE || canPeriod) { tab = it; phase = PastePhase.INPUT; batch = null } },
                    Modifier.fillMaxWidth(), style = SegmentStyle.QUIET, height = 44.dp,
                )
                val note = when {
                    !canPeriod -> UiKey.SMS_PASTE_NOTE_IOS
                    tab == PasteTab.PERIOD -> UiKey.SMS_PASTE_NOTE_PERIOD
                    else -> UiKey.SMS_PASTE_NOTE_PASTE
                }
                BasicText(t(note), style = Type.caption().copy(color = Ink.muted))
            }
        }
        if (reader == null) {
            item(key = "none") { TintedPanel(PanelTone.QUIET) { BasicText(t(UiKey.SMS_PASTE_NO_READER), style = Type.of(13).copy(color = Ink.muted)) } }
            return@InnerScaffold
        }
        if (phase != PastePhase.DONE && tab == PasteTab.PASTE) {
            item(key = "paste") {
                PasteCard(
                    text, { text = it; if (phase == PastePhase.ERROR) phase = PastePhase.INPUT },
                    pastedOn, deps.smsToday(), { pastedOn = it; if (phase == PastePhase.ERROR) phase = PastePhase.INPUT }, busy,
                ) { pastedOn?.let { day -> read { reader.paste(text, day) } } }
            }
        }
        if (phase != PastePhase.DONE && tab == PasteTab.PERIOD) {
            item(key = "period") {
                PeriodCard(ranges, range, { range = it }, senders, off, { s -> off = if (s in off) off - s else off + s }, busy) {
                    val r = ranges.firstOrNull { it.kind == range } ?: return@PeriodCard
                    read { reader.read(r.from, r.to, senders.filter { it !in off }) }
                }
            }
        }
        if (phase == PastePhase.ERROR) {
            item(key = "error") {
                TintedPanel(PanelTone.AMBER) {
                    PanelTitle(t(UiKey.SMS_PASTE_ERR_TITLE), PanelTone.AMBER)
                    BasicText(error ?: t(UiKey.SMS_PASTE_ERR_BODY), style = Type.of(13, lineHeight = 1.7).copy(color = Ink.focus))
                    if (error != null) BasicText(t(UiKey.SMS_PASTE_ERR_BODY), style = Type.of(13, lineHeight = 1.7).copy(color = Ink.focus))
                }
            }
        }
        val b = batch
        if (phase == PastePhase.RESULTS && b != null) {
            item(key = "results") {
                val result = smsReadUi(b, previews.values.toList())
                // البنوك اللي ليها محفظة مربوطة ⇒ سطر «رسائل «البنك» تُسجَّل في «المحفظة»» · فيه بنك من غيرها (أو لصق) ⇒ المالك يختار
                val routes = if (!period) emptyList() else parts.flatMap { part ->
                    val name = wallets.firstOrNull { it.id == part.walletId }?.name ?: return@flatMap emptyList()
                    part.senders.filter { linked[smsSenderKey(it)] != null }.map { it to name }
                }
                val choose = !period || b.rows.any { linked[smsSenderKey(it.sourceName)] == null }
                ResultsBlock(result, period, space.space.currency, wallets, wallet, { wallet = it }, busy, routes, choose) {
                    val ready = parts.mapNotNull { part ->
                        val w = wallets.firstOrNull { it.id == part.walletId } ?: return@mapNotNull null
                        val p = previews[w.id] ?: return@mapNotNull null
                        val lines = part.batch.rows.map { it.lineNumber }.toSet()
                        Triple(part, w, p) to result.selected.filter { it in lines }
                    }.filter { it.second.isNotEmpty() }
                    if (ready.isEmpty()) return@ResultsBlock
                    scope.launch {
                        busy = true
                        try {
                            var imported = 0
                            for ((triple, selected) in ready) {
                                val (part, w, p) = triple
                                imported += deps.importer.commit(smsRequest(part.batch, w), p, selected).counts.imported
                            }
                            saved = imported
                            savedIn = ready.map { it.first.second.name }.distinct().joinToString("، ")
                            phase = PastePhase.DONE
                            text = ""
                            toaster.show(t(UiKey.SMS_PASTE_SAVED, opsCount(saved)), dark = true)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            toaster.show(e.message ?: t(UiKey.IMPORTS_LOAD_FAILED), dark = true)
                        }
                        busy = false
                    }
                }
            }
        }
        if (phase == PastePhase.DONE) {
            item(key = "done") {
                TintedPanel(PanelTone.MINT) {
                    PanelTitle(t(UiKey.SMS_PASTE_SAVED, opsCount(saved)), PanelTone.MINT)
                    BasicText(t(UiKey.SMS_PASTE_DONE_BODY, savedIn), style = Type.of(13))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietButton(t(UiKey.SMS_PASTE_AGAIN), { phase = PastePhase.INPUT; batch = null; wallet = null }, Modifier.weight(1f), onTint = true)
                        PrimaryButton(t(UiKey.BANK_SMS_TITLE), { if (!nav.popTo(BankSmsRoute.name)) nav.replace(BankSmsRoute) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

