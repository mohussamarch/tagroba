package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.BNPL_DEFAULT_SPLIT
import app.masroufy.core.BNPL_SPLITS
import app.masroufy.core.BnplCycle
import app.masroufy.core.BnplMatch
import app.masroufy.core.BnplPreview
import app.masroufy.core.BnplStepState
import app.masroufy.core.Category
import app.masroufy.core.IsoDate
import app.masroufy.core.UiKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput

import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.BnplAnswer
import app.masroufy.usecase.BnplAsk
import app.masroufy.usecase.BnplPurchase

/** «مشترى جديد» و«مش قسط» في اختيارات «أنهي مشترى؟» (الباقي معرّفات خطط). */
internal const val BNPL_PICK_NEW = "\u0000new"
internal const val BNPL_PICK_NONE = "\u0000none"

/**
 * حالة اللوحة لخصم واحد: [setup] = «أول خصم» (ولو «أنهي مشترى» ⇒ «مشترى جديد» بيقلبها لـ«أول خصم») · الاختيارات بافتراضات التصميم
 * (أول شريحة تصنيف · 4 · كل شهر · التذكير شغال).
 */
@Stable
internal class BnplSheetState(val ask: BnplAsk, chips: List<Category>) {
    var setup by mutableStateOf(ask.match !is BnplMatch.AskWhich)
    var what by mutableStateOf("")
    var category by mutableStateOf(chips.firstOrNull())
    var split by mutableStateOf(BNPL_DEFAULT_SPLIT)
    var cycle by mutableStateOf(BnplCycle.MONTHLY)
    var remind by mutableStateOf(true)
    var pick by mutableStateOf<String?>(null)

    /** الرد اللي «اعمل الخطة»/«تأكيد» بيبعته — null = «مشترى جديد» (بيقلب اللوحة) أو مفيش اختيار. [providerName] بدل الاسم الفاضي. */
    fun answer(providerName: String): BnplAnswer? {
        val id = ask.transaction.id
        if (setup) return BnplAnswer.New(id, BnplPurchase(what.trim().ifEmpty { providerName }, category?.id, split, cycle, remind))
        return when (val p = pick) {
            null -> null
            BNPL_PICK_NEW -> { setup = true; pick = null; null }
            BNPL_PICK_NONE -> BnplAnswer.NotInstallment(id)
            else -> BnplAnswer.Of(id, p)
        }
    }
}

/** «النهارده» لو يوم الخصم هو النهارده، وإلا «7 نوفمبر». */
internal fun bnplDay(date: IsoDate, today: IsoDate): String = if (date == today) t(UiKey.BNPL2_TODAY) else dateText(date, today)

/**
 * اللوحة (`BnplSheet.dc.html`): الشارة وسطر الخصم والمبلغ · العنوان والشرح · الجسم (يتمرر) · «اعمل الخطة/تأكيد» و«تخطّي/بعدين» لازقين
 * تحت. [preview] = «خطتك» من حالة الاستخدام (`BnplCharges.preview`) — الشاشة ما بتحسبش ولا مبلغ.
 */
@Composable
internal fun ColumnScope.BnplSheetContent(
    s: BnplSheetState,
    today: IsoDate,
    chips: List<Category>,
    preview: (Int, BnplCycle) -> BnplPreview,
    maxBody: Dp,
    onOtherCategory: () -> Unit,
    onAnswer: (BnplAnswer) -> Unit,
    onLater: () -> Unit,
) {
    val t0 = s.ask.transaction
    val name = bnplName(s.ask.provider)
    val day = bnplDay(t0.occurredAt.take(10), today)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BnplBadge(s.ask.provider)
        Column(Modifier.weight(1f)) {
            val wallet = s.ask.walletName
            BasicText(if (wallet != null) t(UiKey.BNPL2_DEBIT_LINE, name, day, wallet) else t(UiKey.BNPL2_DEBIT_LINE_SHORT, name, day), style = Type.of(13).copy(color = Ink.muted))
            AmountText(t0.amountMinor, t0.currency, size = 20)
        }
    }
    Column(Modifier.weight(1f, fill = false).heightIn(max = maxBody).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(t(if (s.setup) UiKey.BNPL2_SETUP_TITLE else UiKey.BNPL2_WHICH_TITLE), style = Type.of(18, FontWeight.Bold))
            BasicText(if (s.setup) t(UiKey.BNPL2_SETUP_LEAD) else t(UiKey.BNPL2_WHICH_LEAD, name), style = Type.of(13, lineHeight = 1.7).copy(color = Ink.muted))
        }
        if (s.setup) BnplSetupBody(s, name, today, chips, preview, onOtherCategory) else BnplWhichBody(s)
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val off = !s.setup && s.pick == null
        PrimaryButton(t(if (s.setup) UiKey.BNPL2_MAKE_PLAN else UiKey.BNPL2_CONFIRM), { s.answer(name)?.let(onAnswer) }, Modifier.weight(2f), enabled = !off, height = 52.dp)
        val later = if (s.setup) t(UiKey.BNPL2_SKIP) else t(UiKey.BNPL2_LATER)
        BnplQuietButton(later, { if (s.setup) onAnswer(BnplAnswer.Skip(t0.id)) else onLater() }, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BnplSetupBody(s: BnplSheetState, name: String, today: IsoDate, chips: List<Category>, preview: (Int, BnplCycle) -> BnplPreview, onOther: () -> Unit) {
    BasicText(t(UiKey.BNPL2_WHAT_Q, name), style = Type.of(14, FontWeight.Bold))
    TextInput(s.what, { s.what = it }, Modifier.fillMaxWidth(), placeholder = t(UiKey.BNPL2_WHAT_HINT))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in chips) BnplChip(c.name, s.category?.id == c.id, { s.category = c })
        val picked = s.category?.takeIf { c -> chips.none { it.id == c.id } }
        BnplChip(picked?.name ?: t(UiKey.BNPL2_CAT_OTHER), picked != null, onOther)
    }
    BasicText(t(UiKey.BNPL2_SPLIT_Q), style = Type.of(14, FontWeight.Bold))
    BnplChipRow { for (n in BNPL_SPLITS) BnplChip(sentenceNumber(n), s.split == n, { s.split = n }, Modifier.weight(1f)) }
    BasicText(t(UiKey.BNPL2_EVERY_Q), style = Type.of(14, FontWeight.Bold))
    BnplChipRow {
        BnplChip(t(UiKey.BNPL2_MONTHLY), s.cycle == BnplCycle.MONTHLY, { s.cycle = BnplCycle.MONTHLY }, Modifier.weight(1f))
        BnplChip(t(UiKey.BNPL2_TWO_WEEKS), s.cycle == BnplCycle.TWO_WEEKS, { s.cycle = BnplCycle.TWO_WEEKS }, Modifier.weight(1f))
    }
    val plan = remember(s.split, s.cycle) { preview(s.split, s.cycle) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x0A193D33)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(t(UiKey.BNPL2_PLAN_TITLE), Modifier.weight(1f), style = Type.of(14, FontWeight.Bold))
            BasicText(t(UiKey.BNPL2_PLAN_TOTAL, amountLabel(plan.totalMinor, plan.currency), amountLabel(plan.remainingMinor, plan.currency)), style = Type.of(12).copy(color = Ink.muted))
        }
        for (step in plan.steps) {
            val day = if (step.number == 1) bnplDay(step.dueAt, today) else dateText(step.dueAt, today)
            BnplStepRow(t(UiKey.BNPL2_STEP, sentenceNumber(step.number), day), step.state, step.amountMinor, plan.currency, dot = true)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            BasicText(t(UiKey.BNPL2_REMIND_TITLE), style = Type.of(14, FontWeight.Bold))
            BasicText(t(UiKey.BNPL2_REMIND_SUB), style = Type.of(12).copy(color = Ink.muted))
        }
        BnplSwitch(s.remind, t(UiKey.BNPL2_REMIND_TITLE)) { s.remind = !s.remind }
    }
    BasicText(t(UiKey.BNPL2_AUTO_NOTE, name), style = Type.of(12, lineHeight = 1.6).copy(color = Ink.muted))
}

/** سطر دفعة: (نقطة) · «الدفعة 2 · 7 نوفمبر» · الوسم · المبلغ — في «خطتك» و«المواعيد». */
@Composable
internal fun BnplStepRow(label: String, state: BnplStepState, amountMinor: Long, currency: app.masroufy.core.Currency, dot: Boolean) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot) BnplDot(state == BnplStepState.PAID)
        BasicText(label, Modifier.weight(1f), style = Type.of(13))
        BnplTag(state)
        AmountText(amountMinor, currency, size = 13)
    }
}
