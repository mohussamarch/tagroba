package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.masroufy.core.displayTitleOf
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.theme.Radius
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.ExclusionScope
import app.masroufy.core.Meal
import app.masroufy.core.R06Key
import app.masroufy.core.Transaction
import app.masroufy.core.isExcludedFrom
import app.masroufy.core.isRestaurantCategory
import app.masroufy.core.shownMealOf
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * r06-darahim (PLAN-0.6 §12) — أجزاء صغيرة في «تفاصيل العملية» (ملف لوحده عشان الشاشة على حد الـ300 سطر):
 * الوجبة على المطعم (§12-2) · «برّه الميزانية» و«برّه التقارير» (§12-3). الحساب كله في `EditTransaction` و`Meals.kt` — هنا عرض بس.
 */
@Composable
internal fun DarahimDetailExtras(t0: Transaction, categories: List<Category>, onErased: (TxnErased) -> Unit, onChanged: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (t0.duplicateAskPending == true) DuplicateAskCard(t0, onErased, onChanged)
        if (t0.meal != null || isRestaurantCategory(t0.categoryId, categories)) MealChips(t0, categories, onChanged)
        ExclusionChips(t0, onChanged)
    }
}

/** §12-5 «مكرر؟»: «أيوه مكرر — شيله» = لوحة المسح الموجودة (بتراجعها) · «لأ، الاتنين صح» = السؤال يتقفل وما يرجعش. */
@Composable
private fun DuplicateAskCard(t0: Transaction, onErased: (TxnErased) -> Unit, onChanged: () -> Unit) {
    val ops = LocalSpace.current.operations
    val scope = rememberCoroutineScope()
    var erasing by remember(t0.id) { mutableStateOf(false) }
    var error by remember(t0.id) { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Color(0x14C98A1E)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(t(R06Key.DUP_ASK_TITLE), style = Type.of(14, FontWeight.Bold))
        BasicText(t(R06Key.DUP_ASK_BODY, displayTitleOf(t0)), style = Type.of(13).copy(color = Ink.muted))
        if (ops.erase != null) PrimaryButton(t(R06Key.DUP_ASK_REMOVE), onClick = { erasing = true }, modifier = Modifier.fillMaxWidth())
        TonalButton(t(R06Key.DUP_ASK_KEEP), {
            val dup = ops.duplicates ?: return@TonalButton
            scope.launch { error = failureOf { dup.keepBoth(t0.id) }; if (error == null) onChanged() }
        }, Modifier.fillMaxWidth(), muted = true)
        error?.let { FieldError(it) }
    }
    OperationEraseSheet(if (erasing) t0.id else null, ops.erase, onDismiss = { erasing = false }) { e -> erasing = false; onErased(e) }
}

private val MEAL_LABELS = listOf(Meal.BREAKFAST to R06Key.MEAL_BREAKFAST, Meal.LUNCH to R06Key.MEAL_LUNCH, Meal.DINNER to R06Key.MEAL_DINNER)

/** شريحة الوجبة: الاقتراح بيبان متعلّم ومعاه سطر «مقترحة» لحد ما المالك يختار — «من غير» بتشيلها ومش بترجع. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealChips(t0: Transaction, categories: List<Category>, onChanged: () -> Unit) {
    val edit = LocalSpace.current.operations.edit
    val scope = rememberCoroutineScope()
    var error by remember(t0.id) { mutableStateOf<String?>(null) }
    val shown = shownMealOf(t0, categories)
    fun pick(meal: Meal) = scope.launch { error = failureOf { edit.setMeal(t0.id, meal) }; if (error == null) onChanged() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(t(R06Key.MEAL_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((meal, key) in MEAL_LABELS) SelectChip(t(key), shown == meal, { pick(meal) })
            SelectChip(t(R06Key.MEAL_CLEAR), shown == null, { pick(Meal.NONE) })
        }
        if (t0.meal == null && shown != null) BasicText(t(R06Key.MEAL_SUGGESTED), style = Type.of(12).copy(color = Ink.muted))
        error?.let { FieldError(it) }
    }
}

/** العلامتين المنفصلين (العلامة القديمة الواحدة = الاتنين متعلّمين). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExclusionChips(t0: Transaction, onChanged: () -> Unit) {
    val edit = LocalSpace.current.operations.edit
    val scope = rememberCoroutineScope()
    var error by remember(t0.id) { mutableStateOf<String?>(null) }
    val budget = t0.isExcludedFrom(ExclusionScope.BUDGET)
    val reports = t0.isExcludedFrom(ExclusionScope.REPORTS)
    fun run(block: suspend () -> Unit) = scope.launch { error = failureOf { block() }; if (error == null) onChanged() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(t(R06Key.EXCLUDE_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip(t(R06Key.EXCLUDE_BUDGET), budget, { run { edit.setExcludedFromBudget(t0.id, !budget) } })
            SelectChip(t(R06Key.EXCLUDE_REPORTS), reports, { run { edit.setExcludedFromReports(t0.id, !reports) } })
        }
        error?.let { FieldError(it) }
    }
}
