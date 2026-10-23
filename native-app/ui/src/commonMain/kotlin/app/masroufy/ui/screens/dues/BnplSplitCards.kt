package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.BNPL_DEFAULT_SPLIT
import app.masroufy.core.BNPL_SPLITS
import app.masroufy.core.BnplKey
import app.masroufy.core.Currency
import app.masroufy.core.R06Key
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.BnplOption

/** خطة مفتوحة في «الخصم ده فيه كام قسط؟»: متعلّمة ولا لأ + «قيمة القسط» مكتوبة نص لحد ما تتقري بالهللة. */
@Stable
internal class SplitPlanRow(val option: BnplOption, amount: String, on: Boolean) {
    var on by mutableStateOf(on)
    var amount by mutableStateOf(amount)
}

/** «مشترى جديد»: اشتريت إيه · قيمة الدفعة (بالباقي من الأول) · قسمتها على (4) · الدفعة دي رقم (1). */
@Stable
internal class SplitNewRow(val key: Int, amount: String) {
    var name by mutableStateOf("")
    var amount by mutableStateOf(amount)
    var split by mutableStateOf(BNPL_DEFAULT_SPLIT)
    var at by mutableStateOf(1)
}

private val CardShape = RoundedCornerShape(18.dp)

/** مربع العلامة 22: مختار ⇒ أخضر وجواه ✓ · غير كده أبيض بحد رمادي. */
@Composable
private fun Tick(on: Boolean) {
    val m = Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
    Box(if (on) m.background(Ink.primary) else m.background(Ink.card).border(1.5.dp, Ink.tint(0x47), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
        if (on) BasicText("✓", style = Type.of(13, FontWeight.Bold).copy(color = Color.White))
    }
}

/** خانة مبلغ صغيرة جنب عنوانها («قيمة القسط» · «قيمة الدفعة»). */
@Composable
private fun AmountRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(label, Modifier.weight(1f), style = Type.of(13).copy(color = Ink.muted))
        TextInput(value, onChange, Modifier.width(132.dp), label = null, keyboard = KeyboardType.Decimal, ltr = true, height = 42.dp, textSize = 15)
    }
}

@Composable
internal fun SplitPlanCard(row: SplitPlanRow, currency: Currency, onToggle: () -> Unit, onAmount: (String) -> Unit) {
    val o = row.option
    val press = rememberPress()
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Ink.card)
            .insetRing(CardShape, if (row.on) 1.5.dp else 1.dp, if (row.on) Ink.primary else Ink.fieldEdge)
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 4.dp).pressScale(press).tap(press, onClick = onToggle).semantics { role = Role.Checkbox; selected = row.on },
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Tick(row.on)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(o.name, style = Type.of(15, FontWeight.Bold), maxLines = 1)
                val next = o.nextDueAt?.let { dayMonth(it) } ?: t(BnplKey.BNPL2_FINISHED)
                val sub = t(R06Key.BS_PLAN_SUB, sentenceNumber(o.leftCount), sentenceNumber(o.count), amountLabel(o.installmentMinor, currency), next)
                BasicText(sub, style = Type.of(12).copy(color = Ink.muted)) // fix053: بيلف لسطرين بدل ما يتقص
            }
        }
        if (row.on) AmountRow(t(R06Key.BS_INST), row.amount, onAmount)
    }
}

@Composable
internal fun SplitNewCard(row: SplitNewRow, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Ink.card).insetRing(CardShape, 1.5.dp, Ink.primary).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(t(R06Key.BS_NEW_TITLE), Modifier.weight(1f), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
            BnplQuietButton(t(R06Key.BS_REMOVE), onRemove, Modifier.width(72.dp))
        }
        TextInput(row.name, { row.name = it }, Modifier.fillMaxWidth(), placeholder = t(R06Key.BS_NEW_PH), height = 46.dp)
        AmountRow(t(R06Key.BS_PAY), row.amount) { row.amount = it }
        BasicText(t(R06Key.BS_DIVIDE), style = Type.of(13).copy(color = Ink.muted))
        BnplChipRow { for (n in BNPL_SPLITS) BnplChip(sentenceNumber(n), row.split == n, { row.split = n; row.at = minOf(row.at, n) }, Modifier.weight(1f)) }
        BasicText(t(R06Key.BS_AT), style = Type.of(13).copy(color = Ink.muted))
        BnplChipRow { for (n in 1..row.split) BnplChip(sentenceNumber(n), row.at == n, { row.at = n }, Modifier.weight(1f)) }
    }
}

/** «+ مشترى جديد» بحد متقطع أخضر (هنا حد عادي — Compose ما عندوش متقطع جاهز). */
@Composable
internal fun SplitAddButton(label: String, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.fillMaxWidth().height(48.dp).pressScale(press).clip(shape).border(1.5.dp, Ink.primary.copy(alpha = 0.4f), shape)
            .tap(press, onClick = onClick).semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary)) }
}

/** سطر المجموع: أخضر لو مطابق · بني لو فاضل أو زيادة. */
@Composable
internal fun SplitSumLine(text: String, exact: Boolean) =
    BasicText(text, style = Type.of(13, FontWeight.Bold).copy(color = if (exact) Ink.primary else Ink.focus))

/** «غالبًا دول …» — سطر أخضر فاتح فوق الخطط. */
@Composable
internal fun SplitSuggLine(text: String) = BasicText(
    text,
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.primary.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 8.dp),
    style = Type.of(12, lineHeight = 1.7).copy(color = Ink.primary),
)

/** «اتوزّع» بكل جزء ومبلغه. */
@Composable
internal fun SplitDoneCard(title: String, rows: List<Pair<String, String>>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.primary.copy(alpha = 0.07f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(title, style = Type.of(16, FontWeight.Bold).copy(color = Ink.primary))
        for ((name, amount) in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(name, Modifier.weight(1f), style = Type.of(14))
                BasicText(amount, style = Type.of(14, FontWeight.Bold))
            }
        }
    }
}
