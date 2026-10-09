package app.masroufy.ui.screens.more

import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.daysBetween
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t
import app.masroufy.usecase.BackupPlanLine
import app.masroufy.usecase.FullBackupPlan
import app.masroufy.ui.shell.countryLabel

/**
 * النسخة الاحتياطية والاسترجاع للعرض — **دوال نقية** (JVM). الأعداد كلها من خطة الدمج (`FullBackup.plan` ⇒ `FullBackupPlan`): جاي ·
 * هيتضاف · هيتساب — مفيش ولا حرف بيتكتب قبل التأكيد. التصدير بالظبط من تاريخ لتاريخ (§49).
 */
data class ExportRange(val ok: Boolean, val line: String, val days: Int)

/** «٢٨ سبتمبر ٢٠٢٦ ← ٧ أكتوبر ٢٠٢٦، ١٠ أيام» — أو السبب لو المدى غلط. */
fun exportRange(from: IsoDate, to: IsoDate, today: IsoDate): ExportRange {
    if (!isValidIsoDate(from) || !isValidIsoDate(to) || to > today) return ExportRange(false, t(TextKey.BAK_RANGE_BAD_DATE), 0)
    if (from > to) return ExportRange(false, t(TextKey.BAK_RANGE_BAD_ORDER), 0)
    val days = daysBetween(from, to) + 1
    return ExportRange(true, t(TextKey.BAK_RANGE_LINE, fullDate(from) ?: from, fullDate(to) ?: to, countText(days, DAY_WORDS)), days)
}

fun backupFileName(today: IsoDate) = "masroufy-backup-$today.json"

fun exportFileName(from: IsoDate, to: IsoDate) = "masroufy-${from}_$to.csv"

/** سطر مجموعة في المعاينة: الاسم · جاي · هيتضاف · هيتساب (والمجموعات اللي مفيهاش حاجة جاية بتستخبى). */
data class PlanRow(val label: String, val incoming: Int, val toAdd: Int, val skipped: Int)

/** بلد في المعاينة: «الحساب والسعودية» (الجذر) أو بلد تانية (جديدة ⇒ بتتعمل · موجودة ⇒ بيتدمج فيها). */
data class PlanSpace(val title: String, val isNew: Boolean?, val rows: List<PlanRow>, val toAdd: Int)

data class PlanView(val totalToAdd: Int, val incoming: Int, val profileLine: TextKey, val spaces: List<PlanSpace>, val notes: List<String>)

private fun rows(lines: List<BackupPlanLine>) = lines.filter { it.incoming > 0 }.map { PlanRow(it.label, it.incoming, it.toAdd, it.skipped) }

fun planView(plan: FullBackupPlan): PlanView {
    val root = PlanSpace(t(TextKey.RST_ROOT), null, rows(plan.lines), plan.lines.sumOf { it.toAdd })
    val others = plan.spaces.map { s -> PlanSpace(countryLabel(s.space), s.isNew, rows(s.lines), s.totalToAdd) }
    val incoming = plan.lines.sumOf { it.incoming } + plan.spaces.sumOf { s -> s.lines.sumOf { it.incoming } }
    val profile = when {
        !plan.profile.incoming -> TextKey.RST_PROFILE_NONE
        plan.profile.toAdd -> TextKey.RST_PROFILE_ADD
        else -> TextKey.RST_PROFILE_KEEP
    }
    return PlanView(plan.totalToAdd, incoming, profile, listOf(root) + others, plan.warnings)
}

/** «سيُضاف ٣٢ سجلًا» / «كل شيء موجود عندك». */
fun planSpaceLine(s: PlanSpace, done: Boolean): String =
    if (s.toAdd == 0) t(TextKey.RST_ALL_THERE) else t(if (done) TextKey.RST_ADDED_N else TextKey.RST_WILL_ADD_N, countText(s.toAdd, RECORD_WORDS))

fun planCountText(n: Int): String = sentenceNumber(n)
