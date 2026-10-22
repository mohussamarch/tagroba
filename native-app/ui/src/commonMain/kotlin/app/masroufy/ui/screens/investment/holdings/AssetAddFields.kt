package app.masroufy.ui.screens.investment.holdings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.GOLD_KARATS
import app.masroufy.core.Halalas
import app.masroufy.core.HoldingField
import app.masroufy.core.HoldingFormText
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingOrigin
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingType
import app.masroufy.core.InvestHoldKey as K
import app.masroufy.core.MetalForm
import app.masroufy.core.SILVER_PURITIES
import app.masroufy.core.TextRef
import app.masroufy.core.canBeRented
import app.masroufy.core.fieldsFor
import app.masroufy.core.withCurrency
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** محتوى «ضيف حاجة» من غير اللوحة نفسها (عشان الرسم في الاختبار) — عرض بس. */
@Composable
internal fun AssetAddContent(
    form: HoldingFormText?,
    only: HoldingGroup?,
    currency: Currency,
    preview: Halalas?,
    error: AddError?,
    saving: Boolean,
    onPick: (HoldingType) -> Unit,
    onBack: () -> Unit,
    onChange: (HoldingFormText) -> Unit,
    onSave: () -> Unit,
) {
    // سموك r05: والكيبورد مفتوح اللوحة بتقصر و«حفظ» كان بيستخبى تحته ⇒ المحتوى بيتمرر جوه المساحة اللي فاضلة
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (form != null) Box(Modifier.mirrorInLtr()) { SurfaceIconButton(Lucide.CHEVRON_RIGHT, t(K.HOLD_FORM_BACK), onBack) }
            BasicText(t(if (form == null) K.HOLD_SHEET_SUB else K.HOLD_FORM_SUB), style = Type.of(12).copy(color = Ink.muted))
        }
        if (form == null) TypeGroups(only, onPick) else FormStep(form, currency, preview, error, saving, onChange, onSave)
    }
}

@Composable
private fun TypeGroups(only: HoldingGroup?, onPick: (HoldingType) -> Unit) {
    for (group in HoldingGroup.entries) {
        if (only != null && group != only) continue
        val types = HoldingType.entries.filter { it.group == group }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val title = if (group == HoldingGroup.OTHER) t(K.HOLD_SHEET_GROUP_OTHER) else t(groupKey(group))
            BasicText(title, style = Type.of(13, FontWeight.Bold).copy(color = Ink.soft))
            for (row in types.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (type in row) TypeButton(type, Modifier.weight(1f)) { onPick(type) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TypeButton(type: HoldingType, modifier: Modifier, onClick: () -> Unit) {
    val label = t(typeKey(type))
    Column(
        modifier.heightIn(min = 84.dp).clip(RoundedCornerShape(18.dp)).background(CHIP_FILL).holdTap(label, onClick).padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(36.dp).shadow(4.dp, RoundedCornerShape(12.dp), ambientColor = SHADOW, spotColor = SHADOW).clip(RoundedCornerShape(12.dp))
                .background(Ink.card),
            contentAlignment = Alignment.Center,
        ) { LucideIcon(typeIcon(type), size = 20.dp, tint = Ink.primary) }
        BasicText(label, style = Type.of(12, FontWeight.Bold, 1.4))
    }
}

private val SHADOW = Color(0x141D3635)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(label: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label, style = Type.of(13, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((value, text) in options) {
                val on = value == selected
                val bg = if (on) Modifier.background(Brush.linearGradient(listOf(Color(0xFF0A7A62), Ink.primary))) else Modifier.background(CHIP_FILL)
                Box(Modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).then(bg).holdTap(text) { onPick(value) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    BasicText(text, style = Type.of(13, FontWeight.Bold).copy(color = if (on) Color.White else Ink.text))
                }
            }
        }
    }
}

@Composable
private fun FormStep(
    form: HoldingFormText, currency: Currency, preview: Halalas?, error: AddError?, saving: Boolean,
    onChange: (HoldingFormText) -> Unit, onSave: () -> Unit,
) {
    val type = form.type
    if (type.metal) {
        ChipRow(t(K.HOLD_F_FORM), listOf(MetalForm.BARS to t(K.HOLD_F_BARS), MetalForm.JEWEL to t(K.HOLD_F_JEWEL)), form.form) { onChange(form.withForm(it)) }
        val purities = if (type == HoldingType.GOLD) GOLD_KARATS else SILVER_PURITIES
        ChipRow(t(if (type == HoldingType.GOLD) K.HOLD_F_KARAT else K.HOLD_F_PURITY), purities.map { it to it.toString() }, form.karat) { onChange(form.copy(karat = it)) }
    }
    if (canBeRented(type)) ChipRow(t(K.HOLD_F_RENTED), listOf(false to t(K.HOLD_NO), true to t(K.HOLD_YES)), form.rented) { onChange(form.copy(rented = it)) }
    ChipRow(t(K.HOLD_F_ORIGIN), listOf(HoldingOrigin.BUY to t(K.HOLD_O_BUY), HoldingOrigin.INHERIT to t(K.HOLD_O_INHERIT), HoldingOrigin.GIFT to t(K.HOLD_O_GIFT)), form.origin) {
        onChange(form.copy(origin = it))
    }
    ChipRow(t(K.HOLD_F_PURPOSE), HoldingPurpose.entries.map { it to t(purposeKey(it)) }, form.purpose) { onChange(form.copy(purpose = it)) }
    for (field in fieldsFor(type, form.form, form.rented)) {
        val spec = fieldSpec(field, form, currency)
        HoldField(spec, form.text(field), error?.field == field) { onChange(form.with(field, it)) }
    }
    if (type == HoldingType.GOLD && preview != null) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.selected).padding(horizontal = 12.dp, vertical = 10.dp)) {
            BasicText(t(K.HOLD_PREVIEW), Modifier.weight(1f), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
            BasicText("≈ " + wholeMoney(preview, currency), style = Type.of(13, FontWeight.Bold))
        }
    }
    noteFor(type)?.let { BasicText(t(it), style = Type.of(12, lineHeight = 1.6).copy(color = Ink.soft)) }
    error?.let { BasicText(it.text, style = Type.of(13, FontWeight.Bold).copy(color = Ink.expense)) }
    PrimaryButton(t(K.HOLD_SAVE), onSave, Modifier.fillMaxWidth(), loading = saving, height = 52.dp)
}

private fun noteFor(type: HoldingType): TextRef? = when (type.group) {
    HoldingGroup.METAL -> if (type == HoldingType.GOLD) K.HOLD_NOTE_T_GOLD else K.HOLD_NOTE_T_SILVER
    HoldingGroup.PERSONAL -> K.HOLD_NOTE_T_PERSONAL
    HoldingGroup.COLLECTIBLES -> K.HOLD_NOTE_T_COLLECT
    HoldingGroup.PROPERTY -> K.HOLD_NOTE_T_PROPERTY
    else -> null
}

private data class FieldSpec(val label: String, val unit: String, val numeric: Boolean, val placeholder: String, val hint: String?)

@Composable
private fun fieldSpec(field: HoldingField, form: HoldingFormText, currency: Currency): FieldSpec {
    val cur = if (currency == Currency.EGP) "ج.م" else "⃁"
    val type = form.type
    return when (field) {
        HoldingField.GRAMS -> FieldSpec(t(K.HOLD_F_WEIGHT), t(K.HOLD_U_GRAM), true, "0", null)
        HoldingField.PRICE -> FieldSpec(t(if (type.metal && form.origin != HoldingOrigin.BUY) K.HOLD_F_PRICE_GOT else K.HOLD_F_PRICE_BUY), cur, true, "0", null)
        HoldingField.MAKING -> FieldSpec(t(K.HOLD_F_MAKING), cur, true, "0", t(K.HOLD_F_MAKING_HINT))
        HoldingField.VALUE -> FieldSpec(
            t(
                when {
                    type.property -> K.HOLD_F_MARKET
                    type == HoldingType.CO -> K.HOLD_F_SHARE_VALUE
                    type.group == HoldingGroup.COLLECTIBLES -> K.HOLD_F_SELL_VALUE
                    else -> K.HOLD_F_TODAY_VALUE
                },
            ),
            cur, true, "0", null,
        )
        HoldingField.AREA -> FieldSpec(t(K.HOLD_F_AREA), t(K.HOLD_U_SQM), true, "0", null)
        HoldingField.PLACE -> FieldSpec(t(K.HOLD_F_PLACE), "", false, t(K.HOLD_F_PLACE_PH), null)
        HoldingField.RENT -> FieldSpec(t(K.HOLD_F_RENT), cur, true, "0", null)
        HoldingField.NAME -> FieldSpec(
            t(if (type == HoldingType.STK) K.HOLD_F_STK_NAME else if (type == HoldingType.CO) K.HOLD_F_CO_NAME else K.HOLD_F_NAME), "", false,
            if (type == HoldingType.STK) t(K.HOLD_F_STK_PH) else if (type == HoldingType.CO) "" else t(typeKey(type)), null,
        )
        HoldingField.QTY -> FieldSpec(t(K.HOLD_F_QTY), t(K.HOLD_U_SHARES), true, "0", null)
        HoldingField.SHARE -> FieldSpec(t(K.HOLD_F_SHARE), "%", true, "0", null)
    }
}

@Composable
private fun HoldField(spec: FieldSpec, value: String, bad: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(spec.label, style = Type.of(13, FontWeight.Bold))
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(Ink.card)
                .insetBorder(if (bad) 1.5.dp else 1.dp, if (bad) Ink.expense else Ink.fieldEdge, 16.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) BasicText(spec.placeholder, style = Type.of(16, FontWeight.Bold).copy(color = Ink.faded))
                BasicTextField(
                    value, onChange, Modifier.fillMaxWidth(), singleLine = true, textStyle = Type.of(16, FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = if (spec.numeric) KeyboardType.Decimal else KeyboardType.Text),
                )
            }
            if (spec.unit.isNotEmpty()) BasicText(spec.unit, style = Type.of(13).copy(color = Ink.muted))
        }
        spec.hint?.let { BasicText(it, style = Type.of(12).copy(color = Ink.muted)) }
    }
}
