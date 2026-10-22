package app.masroufy.ui.screens.dues

import app.masroufy.core.BnplKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.core.absMoney
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «ده قسط من أنهي مشترى؟» (الحالة التانية في `BnplSheet.dc.html`): كارت لكل خطة مفتوحة عند نفس المزوّد («فاضل 2 من 4 · الجاية 7 نوفمبر»
 * والقسط) + «مشترى جديد» + «مش قسط — عملية عادية». الخطة المختارة ومبلغها مختلف ⇒ السطر الأصفر بالفرق ([app.masroufy.usecase.BnplOption.diffMinor]
 * من حالة الاستخدام).
 */
@Composable
internal fun BnplWhichBody(s: BnplSheetState, onMulti: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in s.ask.options) {
            val on = s.pick == o.planId
            val next = o.nextDueAt?.let { dayMonthOf(it) } ?: t(BnplKey.BNPL2_FINISHED)
            val note = if (!on || o.diffMinor == 0L) null else t(
                if (o.diffMinor > 0) BnplKey.BNPL2_DIFF_MORE else BnplKey.BNPL2_DIFF_LESS,
                amountLabel(absMoney(o.diffMinor), o.currency),
            )
            BnplChoice(o.name, t(BnplKey.BNPL2_LEFT_OF, sentenceNumber(o.leftCount), sentenceNumber(o.count), next), on, note, { s.pick = o.planId }) {
                AmountText(o.installmentMinor, o.currency, size = 14)
            }
        }
        // r06-1b4: «مجموع أكتر من قسط» لو المزوّد عنده خطتين مفتوحتين أو أكتر — بيفتح «الخصم ده فيه كام قسط؟» على طول
        if (onMulti != null && s.ask.plans.size >= 2) {
            val names = s.ask.splitHint.mapNotNull { id -> s.ask.options.firstOrNull { it.planId == id }?.name }
            val hint = if (names.size >= 2) t(app.masroufy.core.R06Key.BS_HINT, names.joinToString(" + ") { "«$it»" }) else null
            BnplChoice(t(app.masroufy.core.R06Key.BS_CHOICE), t(app.masroufy.core.R06Key.BS_CHOICE_SUB), false, hint, onMulti)
        }
        BnplChoice(t(BnplKey.BNPL2_NEW), t(BnplKey.BNPL2_NEW_SUB), s.pick == BNPL_PICK_NEW, null, { s.pick = BNPL_PICK_NEW })
        BnplChoice(t(BnplKey.BNPL2_NOT_INST), null, s.pick == BNPL_PICK_NONE, null, { s.pick = BNPL_PICK_NONE })
    }
}

/** «7 نوفمبر» من غير سنة لو نفس السنة — زي باقي «المستحقات». */
private fun dayMonthOf(date: String): String = app.masroufy.core.dayMonth(date)

/** كارت اختيار: نقطة الاختيار 20 · الاسم وسطر صغير · المبلغ على الطرف · ملاحظة صفرا تحت لو موجودة. */
@Composable
private fun BnplChoice(title: String, sub: String?, on: Boolean, note: String?, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val press = rememberPress()
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().pressScale(press).clip(shape).background(Color.White)
            .insetRing(shape, if (on) 1.5.dp else 1.dp, if (on) Ink.primary else Ink.fieldEdge)
            .tap(press, onClick = onClick).semantics { role = Role.RadioButton; selected = on }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val dot = Modifier.size(20.dp).clip(CircleShape)
            Box(if (on) dot.background(Color.White).border(6.dp, Ink.primary, CircleShape) else dot.border(2.dp, Ink.tint(0x4D), CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(title, style = Type.of(15, FontWeight.Bold))
                if (sub != null) BasicText(sub, style = Type.of(12).copy(color = Ink.muted))
            }
            trailing?.invoke()
        }
        if (note != null) {
            BasicText(
                note,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFBF0DD)).padding(horizontal = 10.dp, vertical = 8.dp),
                style = Type.of(12, lineHeight = 1.6).copy(color = Color(0xFF6B4600)),
            )
        }
    }
}
