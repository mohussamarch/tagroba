package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.HistoryPreview
import kotlinx.coroutines.launch

/** مراحل «طبّق القواعد على السابق»: من غير معاينة · بيحسب · المعاينة قدامك · اتطبقت. */
enum class ApplyPhase { IDLE, LOADING, PREVIEW, DONE }

/** حالة كارت «طبّق على السابق» — بترجع لأولها لما قاعدة أو تاجر يتغير (المعاينة القديمة ما بقتش صح). */
@Stable
class ApplyHolder {
    var phase by mutableStateOf(ApplyPhase.IDLE)
    var preview by mutableStateOf<HistoryPreview?>(null)
    var view by mutableStateOf<ApplyPreviewUi?>(null)

    fun reset() {
        phase = ApplyPhase.IDLE
        preview = null
        view = null
    }
}

/**
 * «طبّق القواعد على السابق»: «اعرض ما سيتغير» ⇒ `ReviewHistory.preview` (آخر خمس سنين) ⇒ عدد العمليات لكل تصنيف + المؤكد بإيدك + اللي
 * مالهاش قاعدة ⇒ «طبّق (N)» ⇒ `applyCategories` (بيرفض لو الخطة اتغيرت من ساعة المعاينة). المؤكد عمره ما بيتلمس.
 */
@Composable
fun ApplyCard(holder: ApplyHolder, deps: BudgetsDeps, today: IsoDate, hasRules: Boolean, onApplied: () -> Unit) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val view = holder.view
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(UiKey.RULES_APPLY_TITLE), style = Type.of(15, FontWeight.Bold))
            val body = when {
                holder.phase == ApplyPhase.DONE -> t(UiKey.RULES_APPLY_DONE)
                !hasRules || (view != null && view.changed == 0) -> t(UiKey.RULES_APPLY_NONE)
                else -> t(UiKey.RULES_APPLY_BODY)
            }
            BasicText(body, style = Type.of(13).copy(color = Ink.muted))
            if (holder.phase == ApplyPhase.PREVIEW && view != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0A193D33)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    view.rows.forEachIndexed { i, row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                ColorDot(parseHexColor(row.colorHex))
                                BasicText(row.label, style = Type.of(13))
                            }
                            BasicText(row.count, style = Type.of(13, FontWeight.Bold))
                        }
                        if (i < view.rows.lastIndex) Divider()
                    }
                }
            }
            when (holder.phase) {
                ApplyPhase.IDLE, ApplyPhase.LOADING -> TonalButton(
                    t(UiKey.RULES_APPLY_PREVIEW),
                    {
                        holder.phase = ApplyPhase.LOADING
                        scope.launch {
                            val (from, to) = historyRange(today)
                            var result: HistoryPreview? = null
                            val error = attempt(t(UiKey.BUDGETS_ERROR_TITLE)) { result = deps.history.preview(from, to) }
                            val preview = result
                            if (error != null || preview == null) {
                                toaster.show(error ?: t(UiKey.BUDGETS_ERROR_TITLE))
                                holder.phase = ApplyPhase.IDLE
                                return@launch
                            }
                            holder.preview = preview
                            holder.view = applyPreview(preview.categoryPlan, deps.categories.list())
                            holder.phase = ApplyPhase.PREVIEW
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = hasRules && holder.phase == ApplyPhase.IDLE,
                )
                ApplyPhase.PREVIEW -> PrimaryButton(
                    t(UiKey.RULES_APPLY_GO, app.masroufy.core.sentenceNumber(view?.changed ?: 0)),
                    {
                        val preview = holder.preview ?: return@PrimaryButton
                        holder.phase = ApplyPhase.LOADING
                        scope.launch {
                            var changed = 0
                            val error = attempt(t(UiKey.BUDGETS_ERROR_TITLE)) {
                                changed = deps.history.applyCategories(preview.rows.map { it.id }, preview.categoryPlan).changed.size
                            }
                            if (error != null) {
                                toaster.show(error)
                                holder.reset()
                            } else {
                                toaster.show(t(UiKey.RULES_APPLIED_TOAST, opsLabel(changed)))
                                holder.phase = ApplyPhase.DONE
                                onApplied()
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = (view?.changed ?: 0) > 0,
                )
                ApplyPhase.DONE -> TonalButton(t(UiKey.RULES_APPLY_DONE_BTN), {}, Modifier.fillMaxWidth(), enabled = false)
            }
        }
    }
}

/**
 * «قاعدة التجار المشتركة»: أسماء محلات وتصنيفها المبدئي من مستخدمين تانيين (من غير مبالغ ولا تواريخ). ⚠️ `SharedMerchants.sync` محتاج كتالوج
 * فايربيز المشترك ومؤشرات المزامنة — مش موجودين في التجميع (`SpaceRepositories`/`DeviceEnv`) ⇒ الزرار مقفول و«غير متاح» بسببه، من غير ما نتظاهر.
 */
@Composable
fun SyncCard() {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(UiKey.RULES_SYNC_TITLE), style = Type.of(15, FontWeight.Bold))
            BasicText(t(UiKey.RULES_SYNC_BODY), style = Type.of(13).copy(color = Ink.muted))
            BasicText(t(UiKey.RULES_SYNC_NA), style = Type.captionBold().copy(color = Ink.focus))
            TonalButton(t(UiKey.RULES_SYNC_BTN), {}, Modifier.fillMaxWidth(), enabled = false)
        }
    }
}
