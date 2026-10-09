package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.masroufy.core.InstallmentKind
import app.masroufy.core.TextKey
import app.masroufy.core.currencyName
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.InstallmentView
import kotlinx.coroutines.launch

/**
 * «خطة جديدة» / «تعديل الخطة» (`InstallmentEdit`): الاسم · الجهة · النوع (تمويل · تقسيط مشتريات — بيتقفل بعد الربط) · العملة (عملة البلد) ·
 * الأصل · الإجمالي · القسط · كل كم شهر · أول موعد · «هل فيها فوائد؟» ⇒ `ManageInstallments.save`. الخطأ جنب الخانة، ورسالة حالة الاستخدام تحت الحفظ.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstallmentEditScreen(planId: String?) {
    val space = LocalSpace.current
    val deps = space.dues
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val today = space.shell.today()
    val load = rememberLoad(deps, planId) { planId?.let { id -> deps.installments.list(today).firstOrNull { it.plan.id == id } } }
    val existing: InstallmentView? = (load.value as? Load.Ready)?.value
    var form by remember(planId) { mutableStateOf(PlanForm(firstDueAt = today)) }
    var prefilled by remember(planId) { mutableStateOf(planId == null) }
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(existing) { if (existing != null && !prefilled) { form = PlanForm.of(existing); prefilled = true } }
    val currency = existing?.plan?.currency ?: space.space.currency
    val check = checkPlanForm(form, planId, currency)
    fun err(f: PlanField) = if (tried) check.errors[f] else null
    val locked = existing?.locked() == true
    val isNew = planId == null
    DuesScaffold(t(if (isNew) TextKey.INST_EDIT_TITLE_NEW else TextKey.INST_EDIT_TITLE), if (isNew) t(TextKey.INST_EDIT_SUB_NEW) else existing?.plan?.name) {
        if (!isNew && load.value is Load.Loading) { item { LoadingBlocks(220, 300) }; return@DuesScaffold }
        if (!isNew && load.value is Load.Failed) { item { LoadFailed(load::reload) }; return@DuesScaffold }
        item(key = "who") {
            FloatingCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TextInput(form.name, { form = form.copy(name = it); saved = false }, label = t(TextKey.INST_F_NAME), placeholder = t(TextKey.INST_F_NAME_HINT), error = err(PlanField.NAME))
                    TextInput(form.provider, { form = form.copy(provider = it); saved = false }, label = t(TextKey.INST_F_PROVIDER), placeholder = t(TextKey.INST_F_PROVIDER_HINT))
                    FieldLabel(t(TextKey.INST_F_KIND))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((k, label, hint) in listOf(
                            Triple(InstallmentKind.FINANCING, TextKey.INST_KIND_FINANCING, TextKey.INST_KIND_FIN_HINT),
                            Triple(InstallmentKind.PURCHASE_PLAN, TextKey.INST_KIND_PURCHASE, TextKey.INST_KIND_BUY_HINT),
                        )) KindChoice(Modifier.weight(1f), t(label), t(hint), form.kind == k, !locked) { form = form.copy(kind = k); saved = false }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText(t(TextKey.INST_F_CURRENCY), style = Type.bodyBold())
                            BasicText(t(TextKey.INST_F_CURRENCY_HINT), style = Type.caption().copy(color = Ink.muted))
                        }
                        BasicText(currencyName(currency), style = Type.bodyBold())
                    }
                    if (locked) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(Lucide.LOCK, size = 16.dp, tint = Ink.muted)
                        BasicText(t(TextKey.DUE_LOCKED_AFTER_LINK), style = Type.caption().copy(color = Ink.muted))
                    }
                }
            }
        }
        item(key = "money") {
            FloatingCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val principalLabel = t(if (form.kind == InstallmentKind.FINANCING) TextKey.INST_F_PRINCIPAL_FIN else TextKey.INST_F_PRINCIPAL_BUY)
                    MoneyField(form.principal, { form = form.copy(principal = it); saved = false }, principalLabel, currency, err(PlanField.PRINCIPAL))
                    MoneyField(form.total, { form = form.copy(total = it); saved = false }, t(TextKey.INST_F_TOTAL), currency, err(PlanField.TOTAL))
                    MoneyField(form.installment, { form = form.copy(installment = it); saved = false }, t(TextKey.INST_F_INSTALLMENT), currency, err(PlanField.INSTALLMENT))
                    FieldLabel(t(TextKey.INST_F_CYCLE))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in PLAN_CYCLES) SelectChip(cycleText(c), form.cycleMonths == c, { form = form.copy(cycleMonths = c); saved = false }, height = 44.dp)
                    }
                    FieldLabel(t(TextKey.INST_F_FIRST))
                    DayPicker(form.firstDueAt, today, { form = form.copy(firstDueAt = it); saved = false }, cell = 36)
                    err(PlanField.FIRST)?.let { FieldError(it) }
                    InterestQuestion(form.hasInterest) { form = form.copy(hasInterest = it); saved = false }
                }
            }
        }
        item(key = "note") {
            BasicText(t(if (form.kind == InstallmentKind.FINANCING) TextKey.INST_EDIT_NOTE_FIN else TextKey.INST_EDIT_NOTE_BUY), style = Type.caption().copy(color = Ink.muted))
        }
        item(key = "save") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saved) SecondaryButton(t(if (isNew) TextKey.INST_DONE_NEW else TextKey.INST_DONE_EDIT), { nav.pop() }, Modifier.fillMaxWidth(), height = 52.dp)
                else PrimaryButton(
                    t(if (isNew) TextKey.INST_SAVE_NEW else TextKey.INST_SAVE),
                    onClick = {
                        tried = true
                        val input = check.input ?: return@PrimaryButton
                        saving = true
                        scope.launch {
                            try {
                                val plan = deps.installments.save(input)
                                toaster.show(if (isNew) t(TextKey.INST_SAVED_NEW, plan.name) else t(TextKey.INST_SAVED))
                                saved = true
                                failure = null
                                DuesChanges.bump()
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                failure = failText(e)
                            } finally {
                                saving = false
                            }
                        }
                    },
                    loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(),
                )
                if (tried && check.errors.isNotEmpty()) FieldError(t(TextKey.INST_SAVE_ERR))
                failure?.let { FieldError(it) }
            }
        }
    }
}

/** اختيار النوع: كارت فيه الاسم وسطر شرح، والمختار أخضر فاتح بحد (مقفول بعد الربط ⇒ باهت). */
@Composable
private fun KindChoice(modifier: Modifier, label: String, hint: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 56.dp).alpha(if (enabled || on) 1f else 0.5f).pressScale(press, enabled).clip(shape)
            .background(if (on) Ink.selected else androidx.compose.ui.graphics.Color.White)
            .insetRing(shape, if (on) 1.5.dp else 1.dp, if (on) Ink.primary else Ink.fieldEdge)
            .tap(press, enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(label, style = Type.bodyBold())
        BasicText(hint, style = Type.of(11).copy(color = Ink.muted))
    }
}
