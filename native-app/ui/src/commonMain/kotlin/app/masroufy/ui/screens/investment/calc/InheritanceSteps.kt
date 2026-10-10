package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.EstateOwner
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** «المحفوظة» في رأس الحاسبة (أيقونة قايمة + الكلمة — زي النموذج). */
@Composable
internal fun SavedButton(onClick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.height(44.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(Color(0x1408634F)).tap(press, onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.LIST, size = 18.dp, tint = Ink.primary)
        BasicText(t(UiKey.INHCALC_SAVED), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
    }
}

/** الخطوات الأربعة (شريط 6 + الاسم 11): اللي خلصت تتلمس وترجعلها. */
@Composable
internal fun StepsBar(step: Int, onGo: (Int) -> Unit) {
    val labels = listOf(UiKey.INHCALC_STEP_WHOSE, UiKey.INHCALC_STEP_ITEMS, UiKey.INHCALC_STEP_HEIRS, UiKey.INHCALC_STEP_BEFORE)
    Row(Modifier.fillMaxWidth().semantics { contentDescription = t(UiKey.INHCALC_STEPS) }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, key ->
            val k = i + 1
            val on = step == k
            val done = step > k
            val press = rememberPress()
            Column(
                Modifier.weight(1f).pressScale(press, done).tap(press, done, role = Role.Tab) { onGo(k) }.semantics { selected = on },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).then(if (on || done) Modifier.background(Glass.primary) else Modifier.background(Color(0x1A193D33))))
                BasicText(t(key), style = Type.of(11, if (on) FontWeight.Bold else FontWeight.Normal).copy(color = if (on) Ink.primary else if (done) Ink.text else Color(0xFF8A9A95)), maxLines = 1)
            }
        }
    }
}

/** الخطوة ١: تركة مَن؟ (تركتي أنا · تركة شخص آخر من أشخاصك أو اسم جديد). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WhoseStep(d: InheritanceDraft, people: List<PersonChoice>, onChange: (InheritanceDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(t(UiKey.INHCALC_WHOSE_Q), style = Type.section())
        WhoseOption(t(UiKey.INHCALC_MINE), t(UiKey.INHCALC_MINE_SUB), d.estateOf == EstateOwner.MINE) { onChange(d.copy(estateOf = EstateOwner.MINE)) }
        WhoseOption(t(UiKey.INHCALC_OTHER), t(UiKey.INHCALC_OTHER_SUB), d.estateOf == EstateOwner.OTHER) { onChange(d.copy(estateOf = EstateOwner.OTHER)) }
        if (d.estateOf == EstateOwner.OTHER) {
            if (people.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in people) SelectChip(p.name, d.personId == p.id, { onChange(d.copy(personId = p.id, personName = p.name)) }, height = 44.dp)
                }
            }
            TextInput(
                if (d.personId == null) d.personName else "",
                { onChange(d.copy(personId = null, personName = it.take(40))) },
                placeholder = t(UiKey.INHCALC_NEW_NAME),
            )
        }
    }
}

@Composable
private fun WhoseOption(title: String, sub: String, on: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(20.dp)
    val body: @Composable () -> Unit = {
        BasicText(title, style = Type.of(16, FontWeight.Bold))
        BasicText(sub, style = Type.caption().copy(color = Ink.muted))
    }
    if (on) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).pressScale(press).clip(shape).background(Ink.selected).insetRing(shape, 1.5.dp, Ink.primary)
                .tap(press, role = Role.RadioButton, onClick = onClick).semantics { selected = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) { body() }
    } else {
        FloatingCard(Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics { selected = false }, shape = shape, onClick = onClick) { body() }
    }
}

/** الخطوة ٢: ماذا يترك؟ — «هات أملاكي من مصروفي» (لتركتك أنت فقط) + الأشياء بأسمائها وقيمها التقريبية (كل رقم يتعدّل). */
@Composable
internal fun ItemsStep(d: InheritanceDraft, canBring: Boolean, onChange: (InheritanceDraft) -> Unit, onBring: () -> Unit) {
    val unit = currencySymbol(d.currency)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(t(UiKey.INHCALC_ITEMS_Q), style = Type.section())
        BasicText(t(UiKey.INHCALC_ITEMS_INTRO), style = Type.of(13).copy(color = Ink.muted))
        PrimaryButton(t(UiKey.INHCALC_BRING), onBring, Modifier.fillMaxWidth(), enabled = canBring, height = 52.dp)
        BasicText(t(if (canBring) UiKey.INHCALC_BRING_NOTE else UiKey.INHCALC_BRING_ONLY_MINE), style = Type.caption().copy(color = Ink.muted))
        d.items.forEachIndexed { i, row ->
            val missing = (parseAmountField(row.value, d.currency).orNull ?: 0L) <= 0L
            FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(row.name, { v -> onChange(d.copy(items = d.items.mapIndexed { j, r -> if (j == i) r.copy(name = v.take(60)) else r })) }, placeholder = t(UiKey.INHCALC_ITEM_NAME), height = 44.dp, textSize = 15)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        TextInput(
                            row.value, { v -> onChange(d.copy(items = d.items.mapIndexed { j, r -> if (j == i) r.copy(value = v) else r })) },
                            Modifier.weight(1f), placeholder = t(UiKey.INHCALC_VALUE_UNKNOWN), ltr = true, keyboard = KeyboardType.Decimal, height = 44.dp,
                            trailing = { BasicText(unit, Modifier.padding(end = 12.dp), style = Type.caption().copy(color = Ink.muted)) },
                        )
                        RemoveButton(t(UiKey.INHCALC_REMOVE, row.name.ifBlank { t(UiKey.INHCALC_THIS_THING) })) { onChange(d.copy(items = d.items.filterIndexed { j, _ -> j != i })) }
                    }
                    if (missing) BasicText(t(UiKey.INHCALC_VALUE_MISSING), style = Type.captionBold().copy(color = warnInk))
                }
            }
        }
        TonalButton(t(UiKey.INHCALC_ADD_ITEM), { onChange(d.copy(items = d.items + EstateRow("", ""))) }, Modifier.fillMaxWidth())
        // المجموع من الأملاك المكتوبة بيبان في النتيجة («من تركة …» — من المحرك)، مش محسوب هنا
    }
}

@Composable
private fun RemoveButton(label: String, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(Color(0x14BE3D48)).tap(press, label = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(Lucide.TRASH_2, size = 18.dp, tint = Ink.expense) }
}

/** لوحة «اسم الحسبة» قبل الحفظ. */
@Composable
internal fun SaveSheet(visible: Boolean, name: String, error: String?, saving: Boolean, onName: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    Sheet(visible, onDismiss, t(UiKey.INHCALC_SAVE_TITLE)) {
        BasicText(t(UiKey.INHCALC_SAVE_BODY), style = Type.of(13).copy(color = Ink.muted))
        TextInput(name, onName, error = error)
        PrimaryButton(t(UiKey.INHCALC_SAVE_BUTTON), onSave, Modifier.fillMaxWidth(), loading = saving)
    }
}
