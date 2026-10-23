package app.masroufy.ui.screens.dues

import app.masroufy.core.BnplKey
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.BnplStepState
import app.masroufy.core.IsoDate
import app.masroufy.core.UiKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.BnplPlanView

/** عنوان القسم «مشتريات بالتقسيط» وسطره تحته (`Installments.dc.html` — من غير زرار التجربة: اللوحة بتطلع لوحدها مع الخصم). */
@Composable
internal fun BnplSectionTitle() {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        BasicText(t(BnplKey.BNPL2_SECTION), style = Type.of(16, FontWeight.Bold))
        BasicText(t(BnplKey.BNPL2_SECTION_SUB), style = Type.of(12).copy(color = Ink.muted))
    }
}

/** «الجاية 7 نوفمبر» · «متأخرة من …» · «خلصت». */
internal fun bnplNextText(v: BnplPlanView, today: IsoDate): String {
    val next = v.progress.nextDueAt ?: return t(BnplKey.BNPL2_FINISHED)
    return if (next < today) t(BnplKey.BNPL2_LATE, dateText(next, today)) else t(BnplKey.BNPL2_NEXT, dateText(next, today))
}

/** سطر التذكير: يوم قبل · من غير · أو الافتراضي القديم (3 أيام) للخطط اللي اتعملت قبل المفتاح. */
internal fun bnplRemindText(v: BnplPlanView): String = t(
    when (v.plan.remindDayBefore) {
        true -> BnplKey.BNPL2_REMIND_ON
        false -> BnplKey.BNPL2_REMIND_OFF
        null -> BnplKey.BNPL2_REMIND_LEGACY
    },
)

/**
 * كارت خطة (`Installments.dc.html`): الشارة 44 · المشترى و«المزوّد · التصنيف · كل شهر» · القسط بالأحمر · شريط الدفعات (اتدفعت أخضر غامق ·
 * الجاية أصفر · الباقي رمادي) · «دفعت 2 من 4 · الباقي X» و«الجاية …» · التذكير بالجرس · «المواعيد» بتفتح الجدول بوسوم «اتدفعت/الجاية».
 */
@Composable
internal fun BnplPlanCard(v: BnplPlanView, today: IsoDate, startOpen: Boolean = false, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null) {
    var open by rememberSaveable(v.plan.id) { mutableStateOf(startOpen) }
    val p = v.plan
    // الضغط ⇒ «تفاصيل الخطة» (تعديل · حذف · الدفعات) زي باقي الخطط، والضغط المطوّل ⇒ نفس القايمة السريعة
    FloatingCard(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(16.dp),
        onClick = onClick, onLongClick = onLongClick, clickLabel = p.name,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BnplBadge(v.provider, size = 44.dp, fontSize = 13)
                Column(Modifier.weight(1f)) {
                    BasicText(p.name, style = Type.of(16, FontWeight.Bold))
                    val every = t(if (p.cycleWeeks == 2) BnplKey.BNPL2_TWO_WEEKS else BnplKey.BNPL2_MONTHLY)
                    BasicText(t(BnplKey.BNPL2_KIND, bnplName(v.provider), v.categoryName ?: t(UiKey.INST_KIND_PURCHASE), every), style = Type.of(12).copy(color = Ink.muted))
                }
                AmountText(p.installmentMinor, p.currency, size = 16, color = Ink.expense)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in v.steps) {
                    val fill = when (s.state) {
                        BnplStepState.PAID -> Ink.primary
                        BnplStepState.NEXT -> Ink.amber
                        BnplStepState.LATER -> Ink.tint(0x1A)
                    }
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(fill))
                }
            }
            val progress = t(BnplKey.BNPL2_PAID_LEFT, sentenceNumber(v.progress.paidCount), sentenceNumber(v.progress.count), amountLabel(v.progress.remainingMinor, p.currency))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(progress, Modifier.weight(1f), style = Type.of(12).copy(color = Ink.muted))
                BasicText(bnplNextText(v, today), style = Type.of(12).copy(color = Ink.muted))
            }
            if (open) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.tint(0x0A)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    for (s in v.steps) BnplStepRow(t(BnplKey.BNPL2_STEP, sentenceNumber(s.number), dateText(s.dueAt, today)), s.state, s.amountMinor, p.currency, dot = false)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val ink = if (p.remindDayBefore == false) Ink.muted else Ink.income
                    LucideIcon(Lucide.BELL, size = 14.dp, tint = ink)
                    BasicText(bnplRemindText(v), style = Type.of(12).copy(color = ink))
                }
                val label = t(if (open) BnplKey.BNPL2_SCHEDULE_HIDE else BnplKey.BNPL2_SCHEDULE)
                Box(Modifier.semantics { stateDescription = label }) { TonalButton(label, { open = !open }, height = 36.dp) }
            }
        }
    }
}

/** حتى لو مش مستخدم: نبرة القسط (أحمر من غير «−») زي النموذج. */
@Suppress("unused")
private val BnplInstallmentTone = AmountTone.PLAIN
