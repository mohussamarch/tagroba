package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
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
 * «طبّق القواعد على السابق»: «اعرض ما سيتغير» ⇒ `ReviewHistory.previewAll` (**كل السجل** — رد المالك §82-16) ⇒ عدد العمليات لكل تصنيف + المؤكد بإيدك + اللي
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
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.tint(0x0A)).padding(horizontal = 12.dp, vertical = 4.dp)) {
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
                            var result: HistoryPreview? = null
                            val error = attempt(t(UiKey.BUDGETS_ERROR_TITLE)) { result = deps.history.previewAll(today) }
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
 * «قاعدة التجار المشتركة»: أسماء محلات وتصنيفها المبدئي من مستخدمين تانيين (من غير مبالغ ولا تواريخ). r06-shared: «حدّث الآن» بيعمل مزامنة
 * ([SharedMerchantsUi.syncNow]) وتحته آخر تحديث على الجهاز ده. [shared] = null (من غير توصيل) ⇒ الزرار مقفول و«غير متاح» بسببه.
 * [onSynced] بعد مزامنة جابت تجار جداد أو اقتراحات (قايمة التجار فوق بتتحدث).
 */
@Composable
fun SyncCard(shared: SharedMerchantsUi?, onSynced: () -> Unit) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var last by androidx.compose.runtime.remember(shared) { mutableStateOf<app.masroufy.ui.screens.imports.LastReadState?>(null) }
    var running by androidx.compose.runtime.remember(shared) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(shared) {
        if (shared != null) runCatching { shared.lastSync() }.onSuccess { last = app.masroufy.ui.screens.imports.LastReadState(it) }
    }
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(UiKey.RULES_SYNC_TITLE), style = Type.of(15, FontWeight.Bold))
            BasicText(t(UiKey.RULES_SYNC_BODY), style = Type.of(13).copy(color = Ink.muted))
            if (shared == null) {
                BasicText(t(UiKey.RULES_SYNC_NA), style = Type.captionBold().copy(color = Ink.focus))
                TonalButton(t(UiKey.RULES_SYNC_BTN), {}, Modifier.fillMaxWidth(), enabled = false)
                return@Column
            }
            last?.let { s ->
                val text = s.last?.let { t(app.masroufy.core.R06Key.RULES_SYNC_LAST, app.masroufy.ui.screens.imports.whenText(it)) }
                    ?: t(app.masroufy.core.R06Key.RULES_SYNC_NEVER)
                BasicText(text, style = Type.caption().copy(color = Ink.muted))
            }
            val label = if (running) t(app.masroufy.core.R06Key.RULES_SYNC_RUNNING) else t(UiKey.RULES_SYNC_BTN)
            TonalButton(label, {
                if (!running) scope.launch {
                    running = true
                    val r = runCatching { shared.syncNow() }.getOrNull()
                    running = false
                    if (r == null) {
                        toaster.show(t(app.masroufy.core.R06Key.RULES_SYNC_FAILED))
                    } else {
                        val n = r.added + r.filled + r.suggested
                        toaster.show(if (n == 0) t(app.masroufy.core.R06Key.RULES_SYNC_NOTHING) else t(app.masroufy.core.R06Key.RULES_SYNC_DONE, n.toString()))
                        runCatching { shared.lastSync() }.onSuccess { last = app.masroufy.ui.screens.imports.LastReadState(it) }
                        if (n > 0) onSynced()
                    }
                }
            }, Modifier.fillMaxWidth(), enabled = !running)
        }
    }
}
