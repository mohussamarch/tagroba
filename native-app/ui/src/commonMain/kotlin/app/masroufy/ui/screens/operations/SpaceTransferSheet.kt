package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SegmentStyle
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.NewSpaceTransfer
import kotlinx.coroutines.launch

/**
 * «تحويل جديد لنفسك»: الاتجاه · لكل رجل المحفظة والمبلغ بعملتها · التاريخ والملاحظة · السعر لايف (للعرض بس) ⇒ `TransferBetweenSpaces.recordNew`
 * (الزوج ورجليه مع بعض أو ولا حاجة). الأخطاء من حالة الاستخدام نفسها (المبلغ · التاريخ · المحفظة).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewSpaceTransferSheet(visible: Boolean, books: List<SpaceWallets>, activeId: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val space = LocalSpace.current
    val deps = space.operations
    val scope = rememberCoroutineScope()
    val dirs = directions(books.map { it.space }, activeId)
    var dir by remember { mutableStateOf(dirs.firstOrNull()?.key) }
    var fromWallet by remember { mutableStateOf<Id?>(null) }
    var toWallet by remember { mutableStateOf<Id?>(null) }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        dir = dirs.firstOrNull()?.key; fromText = ""; toText = ""; note = ""; error = null; date = space.shell.today()
    }
    val d = dirs.firstOrNull { it.key == dir }
    val fromBook = books.firstOrNull { it.space.id == d?.from?.id }
    val toBook = books.firstOrNull { it.space.id == d?.to?.id }
    LaunchedEffect(dir) { fromWallet = fromBook?.wallets?.firstOrNull()?.id; toWallet = toBook?.wallets?.firstOrNull()?.id }
    val fromMinor = d?.let { tryParseMoney(fromText, it.from.currency) }
    val toMinor = d?.let { tryParseMoney(toText, it.to.currency) }
    val ready = d != null && fromWallet != null && toWallet != null && (fromMinor ?: 0) > 0 && (toMinor ?: 0) > 0 && date.isNotBlank()
    Sheet(visible, onDismiss, title = t(TextKey.SPACE_TRANSFER_SCREEN_NEW_TITLE), closeLabel = t(TextKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_NEW_TITLE), style = Type.of(17, FontWeight.Bold))
        BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_NEW_BODY), style = Type.of(13).copy(color = Ink.muted))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (dirs.size > 1) SegmentedTabs(dirs.map { it.key to routeName(it.from, it.to) }, dir ?: "", { dir = it; error = null }, style = SegmentStyle.QUIET, height = 44.dp)
            if (d != null) {
                LegForm(t(TextKey.SPACE_TRANSFER_SCREEN_LEFT, d.from.name), fromBook, fromWallet, { fromWallet = it }, fromText, { fromText = it; error = null },
                    t(TextKey.SPACE_TRANSFER_SCREEN_AMOUNT_OUT, currencySymbol(d.from.currency)))
                LegForm(t(TextKey.SPACE_TRANSFER_SCREEN_ARRIVED, d.to.name), toBook, toWallet, { toWallet = it }, toText, { toText = it; error = null },
                    t(TextKey.SPACE_TRANSFER_SCREEN_AMOUNT_IN, currencySymbol(d.to.currency)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(date, { date = it; error = null }, Modifier.weight(1f), label = t(TextKey.SPACE_TRANSFER_SCREEN_DATE), placeholder = "2026-10-07", ltr = true)
                TextInput(note, { if (it.length <= 1000) note = it }, Modifier.weight(1.4f), label = t(TextKey.SPACE_TRANSFER_SCREEN_NOTE))
            }
            val live = d?.let { rateLine(fromMinor, it.from.currency, toMinor, it.to.currency) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x142469BA)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_LIVE_RATE), style = Type.caption().copy(color = Ink.transfer))
                BasicText(live ?: t(TextKey.SPACE_TRANSFER_SCREEN_AFTER_AMOUNTS), style = Type.of(13, FontWeight.Bold).copy(color = Ink.transfer))
            }
        }
        SheetError(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(t(TextKey.SPACE_TRANSFER_SCREEN_RECORD), loading = saving, enabled = ready, modifier = Modifier.weight(1.4f), onClick = {
                val dd = d ?: return@PrimaryButton
                val fw = fromWallet ?: return@PrimaryButton
                val tw = toWallet ?: return@PrimaryButton
                if (!ready || saving) return@PrimaryButton
                saving = true
                scope.launch {
                    val err = failureOf {
                        deps.spaceTransfers.recordNew(NewSpaceTransfer(dd.from.id, fw, fromMinor ?: 0, dd.to.id, tw, toMinor ?: 0, date.trim(), note.ifBlank { null }))
                    }
                    saving = false
                    if (err != null) error = err else onSaved()
                }
            })
            TonalButton(t(TextKey.SPACE_TRANSFER_SCREEN_CANCEL), onDismiss, Modifier.weight(1f), muted = true)
        }
    }
}

/** رجل في التحويل الجديد: المحفظة (شرايح) + المبلغ بعملتها. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegForm(title: String, book: SpaceWallets?, wallet: Id?, onWallet: (Id) -> Unit, amount: String, onAmount: (String) -> Unit, amountLabel: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x0A193D33)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(title, style = Type.of(13, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (w in book?.wallets.orEmpty()) SelectChip(w.name, wallet == w.id, { onWallet(w.id) }, height = 44.dp)
        }
        TextInput(amount, onAmount, placeholder = amountLabel, ltr = true, keyboard = KeyboardType.Decimal, textSize = 17)
    }
}
