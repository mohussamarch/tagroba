package app.masroufy.ui.screens.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.SchemaId
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.currencyName
import app.masroufy.core.currencySymbol
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** اسم البنك من القارئ (الراجحي · QNB مصر). */
fun bankOf(schema: SchemaId?): String = t(if (schema == SchemaId.QNB_PDF) TextKey.STATEMENT_IMPORT_BANK_QNB else TextKey.STATEMENT_IMPORT_BANK_ALRAJHI)

@Composable
internal fun PickBlock(pdf: List<SchemaId>, currency: Currency, picker: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    LucideIcon(Lucide.UPLOAD, size = 26.dp, tint = Color.White)
                }
                BasicText(t(TextKey.STATEMENT_IMPORT_PICK_TITLE), style = Type.of(18, FontWeight.Bold).copy(color = Color.White))
                BasicText(t(TextKey.STATEMENT_IMPORT_PICK_BODY), style = Type.of(13).copy(color = Ink.onHeroMuted))
                val press = rememberPress()
                Box(
                    Modifier.fillMaxWidth().height(48.dp).alpha(if (picker != null) 1f else 0.45f).pressScale(press, picker != null).clip(RoundedCornerShape(16.dp))
                        .background(Color.White).tap(press, picker != null, onClick = { picker?.invoke() }),
                    contentAlignment = Alignment.Center,
                ) { BasicText(t(TextKey.STATEMENT_IMPORT_PICK_BUTTON), style = Type.of(15, FontWeight.Bold).copy(color = Ink.primary)) }
                if (picker == null) BasicText(t(TextKey.STATEMENT_IMPORT_NO_PICKER), style = Type.caption().copy(color = Ink.onHeroMuted))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(TextKey.STATEMENT_IMPORT_SUPPORTED), style = Type.of(15, FontWeight.Bold))
            FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                val rows = pdf.map { t(TextKey.STATEMENT_IMPORT_STATEMENT_OF, bankOf(it)) to t(TextKey.STATEMENT_IMPORT_PDF_SUB) } +
                    listOf(t(TextKey.STATEMENT_IMPORT_CSV) to t(TextKey.STATEMENT_IMPORT_CSV_SUB))
                (rows.map { it to false } + listOf((t(TextKey.STATEMENT_IMPORT_OTHER_BANKS) to t(TextKey.STATEMENT_IMPORT_LATER)) to true)).forEachIndexed { i, (row, muted) ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(if (muted) Ink.faded else Ink.primary) { LucideIcon(Lucide.FILE_TEXT, size = 18.dp, tint = if (muted) Ink.faded else Ink.primary) }
                        Column(Modifier.weight(1f)) {
                            BasicText(row.first, style = Type.bodyBold())
                            BasicText(row.second, style = Type.caption().copy(color = Ink.muted))
                        }
                    }
                }
            }
            BasicText(t(TextKey.STATEMENT_IMPORT_CURRENCY_NOTE, currencyName(currency), currencySymbol(currency)), style = Type.caption().copy(color = Ink.muted))
        }
    }
}

@Composable
internal fun ReadingCard(fileName: String, page: Int, pages: Int, onCancel: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(fileName, style = Type.bodyBold())
            val line = if (pages > 0) t(TextKey.STATEMENT_IMPORT_READING_PAGE, sentenceNumber(minOf(page, pages)), sentenceNumber(pages)) else t(TextKey.STATEMENT_IMPORT_READING)
            BasicText(line, style = Type.of(13).copy(color = Ink.muted))
            // عرض التقدّم بس (صفحة من صفحات) — نسبة رسم، مش حساب فلوس
            val fraction = if (pages > 0) (page.toFloat() / (pages + 1)).coerceIn(0.05f, 1f) else 0.05f
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ink.text.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Ink.income))
            }
            BasicText(t(TextKey.STATEMENT_IMPORT_CANCEL_SAFE), style = Type.caption().copy(color = Ink.muted))
            QuietButton(t(TextKey.STATEMENT_IMPORT_CANCEL), onCancel, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadyCard(
    r: StatementReadyUi,
    currency: Currency,
    wallets: List<Wallet>,
    wallet: Wallet?,
    onWallet: (Wallet) -> Unit,
    busy: Boolean,
    onReview: () -> Unit,
    onAgain: () -> Unit,
) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(t(TextKey.STATEMENT_IMPORT_READ_CHIP), TagTone.NEW)
                val meta = r.pages?.let(::pagesCount) ?: r.lines?.let(::linesCount)
                if (meta != null) BasicText(meta, style = Type.caption().copy(color = Ink.muted))
            }
            BasicText(r.fileName, style = Type.bodyBold())
            val ops = r.operations
            if (ops != null) {
                val range = if (r.from != null && r.to != null) t(TextKey.STATEMENT_IMPORT_RANGE, dayMonth(r.from), dayMonth(r.to)) else ""
                BasicText(opsCount(ops) + range, style = Type.of(20, FontWeight.Bold))
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                val facts = listOf(
                    t(if (r.kind == StatementKind.PDF) TextKey.STATEMENT_IMPORT_FACT_BANK else TextKey.STATEMENT_IMPORT_FACT_TYPE) to
                        (if (r.kind == StatementKind.PDF) t(TextKey.STATEMENT_IMPORT_STATEMENT_OF, bankOf(r.schema)) else t(TextKey.STATEMENT_IMPORT_CSV_KNOWN)),
                    t(TextKey.STATEMENT_IMPORT_FACT_CURRENCY) to t(TextKey.STATEMENT_IMPORT_CURRENCY_VALUE, currencySymbol(currency)),
                    t(TextKey.STATEMENT_IMPORT_FACT_WALLET) to (wallet?.name ?: t(TextKey.STATEMENT_IMPORT_NO_WALLET)),
                )
                facts.forEachIndexed { i, (label, value) ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BasicText(label, style = Type.of(13).copy(color = Ink.muted))
                        BasicText(value, Modifier.weight(1f), style = Type.of(13, FontWeight.Bold).copy(textAlign = androidx.compose.ui.text.style.TextAlign.End))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(t(TextKey.STATEMENT_IMPORT_WALLET_Q), style = Type.of(13, FontWeight.Bold))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (w in wallets.filter { it.kind != "cash" }) SelectChip(w.name, w.id == wallet?.id, { onWallet(w) }, height = 44.dp)
                }
            }
            PrimaryButton(t(TextKey.STATEMENT_IMPORT_REVIEW), onReview, Modifier.fillMaxWidth(), enabled = wallet != null, loading = busy)
            BasicText(t(TextKey.STATEMENT_IMPORT_REVIEW_NOTE), style = Type.caption().copy(color = Ink.muted))
            TonalButton(t(TextKey.STATEMENT_IMPORT_OTHER_FILE), onAgain, Modifier.fillMaxWidth(), height = 44.dp)
        }
    }
}

@Composable
internal fun ErrorCard(fileName: String, message: String, columns: Boolean, onColumns: () -> Unit, onAgain: () -> Unit) {
    TintedPanel(PanelTone.AMBER) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(Lucide.TRIANGLE_ALERT, size = 20.dp, tint = Ink.focus)
            BasicText(t(if (columns) TextKey.STATEMENT_IMPORT_COLS_TITLE else TextKey.STATEMENT_IMPORT_ERR_TITLE), style = Type.of(15, FontWeight.Bold).copy(color = Ink.focus))
        }
        if (fileName.isNotBlank()) BasicText(fileName, style = Type.caption().copy(color = Ink.focus))
        BasicText(message, style = Type.of(14, lineHeight = 1.7))
        if (columns) QuietButton(t(TextKey.STATEMENT_IMPORT_MAP_COLUMNS), onColumns, Modifier.fillMaxWidth(), onTint = true, height = 44.dp)
        PrimaryButton(t(TextKey.STATEMENT_IMPORT_OTHER_FILE), onAgain, Modifier.fillMaxWidth())
    }
}
