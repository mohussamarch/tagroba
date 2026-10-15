package app.masroufy.usecase

import app.masroufy.core.DedupeVerdict
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.ParsedRow
import app.masroufy.core.UiKey
import app.masroufy.core.jsTrim
import app.masroufy.core.uiText

/**
 * HANDOVER §6.5 بند 12: عملية جت من رسالة أو كشف **والمالك مسحها** («امسح» — `DeleteTransaction`) ما ترجعش لو نفس المصدر اتقرا تاني
 * («اقرأ اللي فات؟» · «اقرأ فترة» · الرسالة رجعت للصندوق · نفس الكشف). سجل المصدر بيفضل علامة (`SourceRecord.ownerDeletedAt`) بمرجعه، والمعاينة
 * بتطلّع أي صف وارد **بنفس المرجع** «مكرر» (مش جديد ولا «شبه»):
 * - مرجع الحساب ده (الكشف ومراجع البنك) من سجلات الحساب نفسها (`ExistingLedger` — مفيش قراية زيادة).
 * - رسايل البنك (`SMS:<بصمة>`) **في البلد كلها** — نفس قاعدة «اتسجلت في محفظة تانية» (مراجعة S1).
 * ⚠️ صف كشف **من غير مرجع** بيتعرف بالرصيد والمبلغ — ده ما بيتمنعش هنا (سؤال مفتوح للمالك).
 */
internal suspend fun ownerErasedReferences(
    deps: ImportStatementDeps,
    request: ImportRequest,
    rows: List<ParsedRow>,
    ledger: ExistingLedger,
): Set<String> {
    val local = ledger.records.filter { it.ownerDeletedAt != null }.mapNotNull { it.sourceReference?.let(::jsTrim)?.ifEmpty { null } }
    if (request.sourceType != ImportSourceType.SMS) return local.toSet()
    val refs = rows.mapNotNull { row -> row.reference?.let(::jsTrim)?.takeIf { it.startsWith("SMS:") } }.distinct()
    if (refs.isEmpty()) return local.toSet()
    val anywhere = deps.sources.listBySourceReferences(refs).filter { it.ownerDeletedAt != null }.mapNotNull { it.sourceReference?.let(::jsTrim) }
    return (local + anywhere).toSet()
}

/** الصف الجديد (أو «الشبه») اللي مرجعه المالك مسح عمليته ⇒ «مكرر» من غير عملية مربوطة — ما بيتسجلش ولا بيستنى. */
internal fun erasedVerdict(verdict: DedupeVerdict, ref: String?, erased: Set<String>): DedupeVerdict {
    if (ref == null || ref !in erased) return verdict
    if (verdict.state != MatchingState.NEW && verdict.state != MatchingState.SIMILAR) return verdict
    return DedupeVerdict(MatchingState.DUPLICATE, uiText(UiKey.ERASE_IMPORT_SKIPPED, ref), matchedTransactionId = null)
}
