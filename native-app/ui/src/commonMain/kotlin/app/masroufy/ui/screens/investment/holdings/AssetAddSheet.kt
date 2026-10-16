package app.masroufy.ui.screens.investment.holdings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.HoldingField
import app.masroufy.core.HoldingFormCheck
import app.masroufy.core.HoldingFormText
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingType
import app.masroufy.core.InvestHoldKey
import app.masroufy.core.parseQuantity
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import kotlinx.coroutines.launch

/**
 * لوحة «ضيف حاجة» (النموذج `AssetAddSheet`): خطوة الأنواع ⇒ خطوة التفاصيل. الخانات ⇒ `checkHoldingForm` (core) ⇒ `AddHolding`.
 * [only] = مفتوحة من صفحة نوع ⇒ أنواع المجموعة دي بس. [onSaved] بياخد اسم الحاجة (للرسالة) بعد الحفظ.
 */
@Composable
internal fun AssetAddSheet(
    visible: Boolean,
    only: HoldingGroup?,
    currency: Currency,
    deps: InvestHoldingsDeps,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var form by remember(visible) { mutableStateOf<HoldingFormText?>(null) }
    var error by remember(visible) { mutableStateOf<AddError?>(null) }
    var saving by remember(visible) { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Halalas?>(null) }
    val scope = rememberCoroutineScope()
    val f = form
    // «قيمته النهارده ≈» للدهب بس — من حالة الاستخدام (مفيش حساب هنا)
    val gramsText = f?.text(HoldingField.GRAMS).orEmpty()
    LaunchedEffect(f?.type, f?.karat, gramsText) {
        preview = null
        if (f == null || f.type != HoldingType.GOLD) return@LaunchedEffect
        val grams = runCatching { parseQuantity(gramsText) }.getOrNull()?.takeIf { it > 0L } ?: return@LaunchedEffect
        preview = runCatching { deps.preview.metalToday(f.type, f.karat, grams, currency) }.getOrNull()
    }
    val title = if (f == null) t(InvestHoldKey.HOLD_SHEET_TITLE) else t(typeKey(f.type))
    Sheet(visible, onDismiss, title, closeLabel = t(InvestHoldKey.HOLD_CLOSE)) {
        AssetAddContent(
            form = f, only = only, currency = currency, preview = preview, error = error, saving = saving,
            onPick = { form = HoldingFormText(it); error = null },
            onBack = { form = null; error = null },
            onChange = { form = it; error = null },
            onSave = save@{
                val cur = form ?: return@save
                when (val check = checkForm(cur, currency)) {
                    is HoldingFormCheck.Missing -> error = AddError(check.field, missingText(check.field))
                    is HoldingFormCheck.Invalid -> error = AddError(check.field, t(InvestHoldKey.HOLD_ERR_NUMBER))
                    is HoldingFormCheck.Ok -> {
                        saving = true
                        scope.launch {
                            val result = runCatching { deps.add.add(check.holding, deps.today()) }
                            saving = false
                            result.onSuccess { onSaved(check.holding.name) }
                                .onFailure { e -> error = AddError(null, e.message ?: t(InvestHoldKey.HOLD_ERR_VALUE)) }
                        }
                    }
                }
            },
        )
    }
}

/** خطأ بعد «حفظ»: [field] الخانة اللي حدها بيبقى أحمر (null = خطأ من الحفظ نفسه). */
internal data class AddError(val field: HoldingField?, val text: String)

private fun checkForm(form: HoldingFormText, currency: Currency): HoldingFormCheck =
    app.masroufy.core.checkHoldingForm(form, currency, app.masroufy.core.uiText(typeKey(form.type)))

private fun missingText(field: HoldingField): String = app.masroufy.core.uiText(
    when (field) {
        HoldingField.GRAMS -> InvestHoldKey.HOLD_ERR_GRAMS
        HoldingField.QTY -> InvestHoldKey.HOLD_ERR_QTY
        else -> InvestHoldKey.HOLD_ERR_VALUE
    },
)
