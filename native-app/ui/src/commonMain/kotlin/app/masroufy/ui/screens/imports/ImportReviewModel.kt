package app.masroufy.ui.screens.imports

import app.masroufy.core.CategorizationSource
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.TextKey
import app.masroufy.core.jsTrim
import app.masroufy.usecase.ImportCountsPreview
import app.masroufy.usecase.ImportImpact
import app.masroufy.usecase.ImportPreview

/** سطر في «مراجعة الكشف»: حالته وسببها (جملة منع التكرار نفسها) وتصنيفه ومصدر التصنيف. [amountMinor] = null للسطر غير الصالح. */
data class ReviewLineUi(
    val lineNumber: Int,
    val name: String,
    val date: IsoDate?,
    val amountMinor: Halalas?,
    val direction: Direction?,
    val state: MatchingState,
    val reason: String,
    val categoryId: Id?,
    val source: CategorizationSource?,
    /** العملية الموجودة اللي السطر شبهها أو بيكررها (للمقارنة). */
    val matchedTransactionId: Id?,
) {
    /** الجديد والشبيه بيتختاروا؛ المكرر والتعارض وغير الصالح ثابتين (spec/05: لا استبدال صامت). */
    val selectable: Boolean get() = state == MatchingState.NEW || state == MatchingState.SIMILAR

    val canCompare: Boolean get() = state != MatchingState.NEW && state != MatchingState.INVALID
}

data class ImportReviewUi(
    val lines: List<ReviewLineUi>,
    val counts: ImportCountsPreview,
    /** أثر **الاختيار المبدئي** (الجديد بس) من المعاينة نفسها. */
    val impact: ImportImpact,
    /** نفس الملف اتستورد قبل كده (الدرجة ١). */
    val previous: ImportBatch?,
    val defaultSelection: Set<Int>,
) {
    /** الحالة «فاضي»: الملف ده اتستورد قبل كده وكل اللي فيه متسجل (مفيش جديد ولا شبيه). */
    val alreadyImported: Boolean get() = previous != null && lines.none { it.selectable }

    /**
     * الأثر للاختيار الحالي: لو هو المبدئي ⇒ رقم المعاينة؛ لو المالك غيّره ⇒ null («غير متاح» — حساب أثر اختيار تاني مالوش حالة استخدام لسه،
     * والشاشة ما بتحسبش فلوس — CLAUDE.md #4 · #10).
     */
    fun impactFor(selection: Set<Int>): ImportImpact? = if (selection == defaultSelection) impact else null

    fun count(state: MatchingState): Int = when (state) {
        MatchingState.NEW -> counts.newCount
        MatchingState.DUPLICATE -> counts.duplicates
        MatchingState.SIMILAR -> counts.similar
        MatchingState.CONFLICT -> counts.conflicts
        MatchingState.INVALID -> counts.invalid
    }
}

fun importReviewUi(preview: ImportPreview): ImportReviewUi {
    val lines = preview.lines.map { l ->
        ReviewLineUi(
            lineNumber = l.row.lineNumber,
            name = jsTrim(l.row.merchantName).ifEmpty { jsTrim(l.row.description) },
            date = l.row.date,
            amountMinor = l.row.amountMinor,
            direction = l.row.direction,
            state = l.state,
            reason = l.reason,
            categoryId = l.categoryId?.takeIf { it.isNotEmpty() },
            source = l.categorySource,
            matchedTransactionId = l.matchedTransactionId,
        )
    } + preview.errors.map { e ->
        ReviewLineUi(e.lineNumber, jsTrim(e.raw).take(48), null, null, null, MatchingState.INVALID, e.message, null, null, null)
    }
    val default = preview.lines.filter { it.selectedByDefault }.map { it.row.lineNumber }.toSet()
    return ImportReviewUi(lines.sortedBy { it.lineNumber }, preview.counts, preview.impact, preview.previousBatch, default)
}

/** مصدر التصنيف بكلمتين (مقترح من قاعدة · تصنيف المتجر المؤكد · متجر جديد …). */
fun sourceLabel(source: CategorizationSource?): TextKey = when (source) {
    CategorizationSource.USER_CONFIRMED -> TextKey.IMPORT_REVIEW_SRC_CONFIRMED
    CategorizationSource.VERIFIED_MERCHANT -> TextKey.IMPORT_REVIEW_SRC_MERCHANT
    CategorizationSource.RULE -> TextKey.IMPORT_REVIEW_SRC_RULE
    CategorizationSource.SOURCE_CATEGORY -> TextKey.IMPORT_REVIEW_SRC_BANK
    CategorizationSource.OTHER_COUNTRY_MERCHANT -> TextKey.IMPORT_REVIEW_SRC_OTHER_COUNTRY
    CategorizationSource.NONE, null -> TextKey.IMPORT_REVIEW_SRC_NEW
}

/** اسم الحالة (العدّادات والشارة). */
fun stateLabel(state: MatchingState): TextKey = when (state) {
    MatchingState.NEW -> TextKey.IMPORT_REVIEW_NEW
    MatchingState.DUPLICATE -> TextKey.IMPORT_REVIEW_DUP
    MatchingState.SIMILAR -> TextKey.IMPORT_REVIEW_SIMILAR
    MatchingState.CONFLICT -> TextKey.IMPORT_REVIEW_CONFLICT
    MatchingState.INVALID -> TextKey.IMPORT_REVIEW_INVALID
}

fun stateTone(state: MatchingState): TagTone = when (state) {
    MatchingState.NEW -> TagTone.NEW
    MatchingState.DUPLICATE -> TagTone.MUTED
    MatchingState.SIMILAR -> TagTone.AMBER
    MatchingState.CONFLICT -> TagTone.DANGER
    MatchingState.INVALID -> TagTone.AMBER
}

/** المقارنة: الفرق بين السطر الجديد والعملية الموجودة (المبلغ ولا التاريخ) — مساواة بس، من غير حساب. */
enum class DiffField { AMOUNT, DATE }

fun diffOf(newAmount: Halalas?, newDate: IsoDate?, oldAmount: Halalas?, oldDate: IsoDate?): DiffField? = when {
    newAmount != null && oldAmount != null && newAmount != oldAmount -> DiffField.AMOUNT
    newDate != null && oldDate != null && newDate != oldDate -> DiffField.DATE
    else -> null
}
