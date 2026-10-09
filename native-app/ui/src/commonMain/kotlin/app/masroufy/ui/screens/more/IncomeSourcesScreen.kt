package app.masroufy.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.PayerQuestion
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceDigits
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «مصادر الدخل» (`IncomeSources` — §47 · §48 · §64 · §65): «هل هذا راتب من …؟» على أول إيداع من طرف جديد (`IncomeSourceSignals`) ·
 * الحالية والسابقة (المنتهي **بيتقفل بتاريخ** مش بيتمسح) · «غيّرت عملي» و«أضف مصدر دخل» (`JobChangeSheet`). التطبيق ما بيستنتجش حاجة
 * من تغيّر المبلغ — بيسأل.
 */
@Composable
fun IncomeSourcesScreen() {
    val deps = LocalSpace.current
    val more = deps.more
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var sources by remember(deps) { mutableStateOf<List<IncomeSource>?>(null) }
    var question by remember(deps) { mutableStateOf<PayerQuestion?>(null) }
    var monthStart by remember(deps) { mutableIntStateOf(28) }
    var sheet by remember { mutableStateOf<JobSheetMode?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(deps, tick) {
        sources = runCatching { more.incomeSources.list() }.getOrNull() ?: emptyList()
        question = runCatching { more.incomeSignals.payerQuestions().firstOrNull() }.getOrNull()
        monthStart = runCatching { more.profile.load().payday }.getOrDefault(monthStart)
    }
    InnerScaffold(t(TextKey.INCSRC_TITLE)) {
        item(key = "intro") { BasicText(t(TextKey.INCSRC_INTRO), Modifier.padding(horizontal = 4.dp), style = Type.of(13).copy(color = Ink.muted)) }
        val list = sources
        if (list == null) {
            item(key = "sk") { Skeleton(Modifier.fillMaxWidth().height(260.dp)) }
            return@InnerScaffold
        }
        question?.let { q ->
            item(key = "ask") {
                PayerCard(q) { yes ->
                    scope.launch {
                        val ok = runCatching { more.incomeSignals.answerPayer(q, yes) }.isSuccess
                        toaster.show(t(if (!ok) TextKey.MORE_SAVE_FAILED else if (yes) TextKey.INCSRC_PAYER_YES_DONE else TextKey.INCSRC_PAYER_NO_DONE), dark = true)
                        tick++
                    }
                }
            }
        }
        if (list.isEmpty()) item(key = "empty") { EmptyState(t(TextKey.INCSRC_EMPTY_TITLE), t(TextKey.INCSRC_EMPTY_BODY)) }
        val sections = incomeSections(list)
        for ((key, rows) in listOf(TextKey.INCSRC_CURRENT to sections.current, TextKey.INCSRC_PAST to sections.past)) {
            if (rows.isEmpty()) continue
            item(key = key.name) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(t(key, sentenceNumber(rows.size)), Modifier.padding(horizontal = 4.dp), style = Type.of(15, FontWeight.Bold))
                    for (r in rows) SourceCard(r) { nav.push(IncomeSourceDetailRoute(r.id)) }
                }
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sections.current.isNotEmpty()) PrimaryButton(t(TextKey.INCSRC_CHANGE), onClick = { sheet = JobSheetMode.Change }, height = 52.dp, modifier = Modifier.fillMaxWidth())
                if (list.isEmpty()) PrimaryButton(t(TextKey.INCSRC_ADD), onClick = { sheet = JobSheetMode.Add }, modifier = Modifier.fillMaxWidth())
                else TonalButton(t(TextKey.INCSRC_ADD), onClick = { sheet = JobSheetMode.Add }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    JobChangeSheet(sheet, sources.orEmpty().filter { it.endedAt == null }, monthStart, onClose = { sheet = null }, onDone = { tick++ })
}

/** «هل هذا راتب من «…»؟» — كارت كهرماني بزرارين (من غير المبلغ: السؤال من `payerQuestions` مالوش مبلغ). */
@Composable
private fun PayerCard(q: PayerQuestion, onAnswer: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(t(TextKey.INCSRC_PAYER_Q, q.sourceName), style = Type.of(15, FontWeight.Bold).copy(color = Color(0xFF6B4600)))
        val from = q.party.last4?.let { t(TextKey.INCSRC_PAYER_FROM_LAST4, dayMonth(q.date), q.party.label, sentenceDigits(it)) } ?: t(TextKey.INCSRC_PAYER_FROM, dayMonth(q.date), q.party.label)
        BasicText(from, style = Type.caption().copy(color = Color(0xFF6B4600)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(t(TextKey.MORE_YES), onClick = { onAnswer(true) }, modifier = Modifier.weight(1f))
            TonalButton(t(TextKey.MORE_NO), onClick = { onAnswer(false) }, modifier = Modifier.weight(1f), muted = true)
        }
    }
}

@Composable
private fun SourceCard(r: IncomeRowView, onClick: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth().alpha(if (r.ended) 0.85f else 1f), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = onClick, clickLabel = r.name) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (r.ended) Ink.muted else Ink.primary, size = 40.dp, radius = 14.dp) { LucideIcon(incomeIcon(r.kind), size = 18.dp, tint = if (r.ended) Ink.muted else Ink.primary) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(r.name, style = Type.of(15, FontWeight.Bold))
                BasicText(r.meta, style = Type.caption().copy(color = Ink.muted))
                BasicText(r.period, style = Type.of(12, FontWeight.Bold))
                if (r.expected != null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(t(TextKey.INCSRC_EXPECTED), style = Type.caption().copy(color = Ink.muted))
                    BasicText(r.expected, style = Type.of(12, FontWeight.Bold).copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
                }
            }
            LucideIcon(Lucide.CHEVRON_LEFT, Modifier.size(16.dp).mirrorInLtr(), size = 16.dp, tint = Ink.muted)
        }
    }
}

/** رمز النوع: وظيفة/دوام جزئي حقيبة · عميل شخص · إيجار بيت · استثمار ومعاش وأخرى سهم طالع. */
fun incomeIcon(kind: IncomeSourceKind): Lucide = when (kind) {
    IncomeSourceKind.JOB, IncomeSourceKind.PART_TIME -> Lucide.BRIEFCASE
    IncomeSourceKind.CLIENT -> MoreIcons.CLIENT
    IncomeSourceKind.RENT -> Lucide.HOUSE
    else -> Lucide.TRENDING_UP
}
