package app.masroufy.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.IncomeSource
import app.masroufy.core.SALARIED_KINDS
import app.masroufy.core.SourceStartComparison
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.NotAvailableBadge
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * تفاصيل مصدر الدخل (`IncomeSourceDetail` — §48 · §64 · §65-٧): الحالة والنوع والمدة · موعد القبض والمتوقع والجهة المحوِّلة ·
 * العمليات المنسوبة له (مالهاش حالة استخدام ⇒ «غير متاح بعد») · قبل البداية وبعدها (`compareAroundStart` — `null` ⇒ «غير متاح» بسببه) ·
 * «تعديل» (`IncomeSourceEditSheet`) · «إنهاء المصدر» (`JobChangeSheet`).
 */
@Composable
fun IncomeSourceDetailScreen(sourceId: Id) {
    val deps = LocalSpace.current
    val more = deps.more
    var source by remember(deps) { mutableStateOf<IncomeSource?>(null) }
    var loaded by remember(deps) { mutableStateOf(false) }
    var cmp by remember(deps) { mutableStateOf<SourceStartComparison?>(null) }
    var cmpLoaded by remember(deps) { mutableStateOf(false) }
    var monthStart by remember(deps) { mutableIntStateOf(28) }
    var editing by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<JobSheetMode?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(deps, tick) {
        source = runCatching { more.incomeSources.list().firstOrNull { it.id == sourceId } }.getOrNull()
        loaded = true
        monthStart = runCatching { more.profile.load().payday }.getOrDefault(monthStart)
        cmp = runCatching { more.incomeSignals.compareAroundStart(sourceId, deps.shell.today()) }.getOrNull()
        cmpLoaded = true
    }
    InnerScaffold(t(TextKey.INCSRC_DETAIL_TITLE)) {
        val s = source
        if (s == null) {
            item(key = "x") { if (loaded) EmptyState(t(TextKey.INCSRC_GONE)) else Skeleton(Modifier.fillMaxWidth().height(160.dp), radius = 26.dp, strong = true) }
            return@InnerScaffold
        }
        item(key = "hero") { SourceHero(s) }
        item(key = "facts") {
            GroupCard(horizontal = 16.dp) {
                FactRow(t(TextKey.INCSRC_PAY_DAY), payText(s), last = false)
                FactRow(t(TextKey.INCSRC_EXPECTED), s.expectedMinor?.let { amountLabel(it, s.currency) } ?: t(TextKey.INCSRC_EXPECTED_NONE), last = false, muted = s.expectedMinor == null)
                FactRow(t(TextKey.INCSRC_PAYER), t(payerKey(s)), last = true, muted = s.kind !in SALARIED_KINDS || s.payerKeys.isEmpty())
            }
        }
        item(key = "tx") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(t(TextKey.INCSRC_TX_TITLE), Modifier.padding(horizontal = 4.dp), style = Type.of(15, FontWeight.Bold))
                // العمليات المنسوبة للمصدر مالهاش حالة استخدام (بيتنسب بالجهة المحوِّلة جوه المنطق بس) ⇒ مش «لا عمليات» (القاعدة 10)
                NotYetLine(t(TextKey.INCSRC_TX_NOT_YET))
            }
        }
        item(key = "cmp") { CompareCard(cmp, cmpLoaded) }
        item(key = "acts") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.endedAt == null) SecondaryButton(t(TextKey.INCSRC_CLOSE), onClick = { closing = JobSheetMode.Close(s) }, modifier = Modifier.fillMaxWidth())
                TonalButton(t(TextKey.INCSRC_EDIT), onClick = { editing = true }, modifier = Modifier.fillMaxWidth(), height = 44.dp)
            }
        }
    }
    source?.let { s ->
        IncomeSourceEditSheet(editing, s, monthStart, onClose = { editing = false }, onSaved = { tick++ })
        JobChangeSheet(closing, listOf(s), monthStart, onClose = { closing = null }, onDone = { tick++ })
    }
}

private fun payerKey(s: IncomeSource): TextKey = when {
    s.kind !in SALARIED_KINDS -> TextKey.INCSRC_PAYER_NOT_ASKED
    s.payerKeys.isEmpty() -> TextKey.INCSRC_PAYER_UNKNOWN
    else -> TextKey.INCSRC_PAYER_KNOWN
}

@Composable
private fun SourceHero(s: IncomeSource) {
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(s.name, Modifier.weight(1f), style = Type.of(20, FontWeight.Bold).copy(color = Ink.onPrimary))
                val ended = s.endedAt != null
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (ended) Color(0x29FFFFFF) else Ink.mint).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    BasicText(t(if (ended) TextKey.INCSRC_ENDED else TextKey.INCSRC_ONGOING), style = Type.of(12, FontWeight.Bold).copy(color = if (ended) Color.White else Ink.heroStart))
                }
            }
            BasicText(t(TextKey.INCSRC_META, t(incomeKindLabel(s.kind)), t(if (s.currency == Currency.EGP) TextKey.INCSRC_IN_EGP else TextKey.INCSRC_IN_SAR)), style = Type.of(13).copy(color = Ink.onHeroMuted))
            BasicText(periodText(s), style = Type.of(14, FontWeight.Bold).copy(color = Ink.onPrimary))
        }
    }
}

/** «قبل البداية وبعدها»: الدخل والمصروف (متوسط ٣ أشهر قبل وبعد) والنسبة من حالة الاستخدام، و«الأرقام ناقصة» لو فيه نوع مش محدد. */
@Composable
private fun CompareCard(c: SourceStartComparison?, loaded: Boolean) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(TextKey.INCSRC_CMP_TITLE), Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
                if (c != null && !c.totalsReliable) Badge(t(TextKey.INCSRC_CMP_PARTIAL), BadgeKind.APPROX)
            }
            when {
                !loaded -> Skeleton(Modifier.fillMaxWidth().height(120.dp), radius = 16.dp)
                c == null -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    NotAvailableBadge()
                    BasicText(t(TextKey.INCSRC_CMP_NA), style = Type.caption().copy(color = Ink.muted))
                }
                else -> {
                    for (row in compareRows(c)) CompareBox(row)
                    if (!c.totalsReliable) BasicText(t(TextKey.INCSRC_CMP_PARTIAL_NOTE), style = Type.of(12, FontWeight.Bold).copy(color = Ink.focus))
                    if (c.incomeChangeTenthPercent == null) BasicText(t(TextKey.INCSRC_CMP_NO_PCT), style = Type.caption().copy(color = Ink.muted))
                }
            }
            BasicText(t(TextKey.INCSRC_CMP_NOTE), style = Type.caption().copy(color = Ink.muted))
        }
    }
}

@Composable
private fun CompareBox(r: CompareRow) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x0A193D33)).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(t(r.label), Modifier.weight(1f), style = Type.of(13, FontWeight.Bold))
            val (ink, bg) = when (r.trend) {
                Trend.GOOD -> Ink.income to Color(0x1A13764D)
                Trend.BAD -> Ink.expense to Color(0x14BE3D48)
                Trend.FLAT -> Ink.muted to Color(0x0F193D33)
            }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 2.dp)) {
                BasicText(r.change, style = Type.of(13, FontWeight.Bold).copy(color = ink, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, minor) in listOf(TextKey.INCSRC_CMP_BEFORE to r.before, TextKey.INCSRC_CMP_AFTER to r.after)) Column(Modifier.weight(1f)) {
                BasicText(t(label), style = Type.of(11).copy(color = Ink.muted))
                AmountText(minor, r.currency, showCurrency = false)
            }
        }
    }
}
