package app.masroufy.usecase

import app.masroufy.port.Clock
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * تجميع الشريحة S3 (§77-D · §75-12) لبلد واحدة في مكان واحد — اللي التطبيق هيعمله وقت التشغيل (التجميع يدوي — CLAUDE.md #6):
 * - [effects] ⇒ `ImportStatementDeps.effects` بتاعة **الكشف** و**رسايل البنك** في البلد (الأصلية ممكن تيجي من الكشف بعد رجوعها).
 *   خط رسايل البنك (`SmsLane.of`) بيضيفهم لوحده لو ناقصين ([withReturnEffects]) — بس من غير الروابط ⇒ ما بيلغيش لوحده، بيسأل بس.
 * - [undoers] ⇒ `RevertDeps.undoers` (التراجع عن دفعة بيرجّع الطرف التاني).
 * - [repair] ⇒ `BackgroundCycleDeps.reversalRepairs` (دورة الخلفية — بيصلّح بعد تراجع التطبيق القديم أو وقوع في النص).
 * - [refunds] · [asks] ⇒ الشاشة والعدّاد.
 */
class ReturnsWiring(
    private val txns: TransactionRepository,
    private val sources: SourceRecordRepository,
    private val links: ReversalLinkDeps,
    private val clock: Clock,
    private val uow: UnitOfWork? = null,
) {
    val effects: List<RecordEffect> = listOf(ReturnedSmsEffect(ReturnedSmsDeps(txns, sources, links)), ForeignSmsEffect())
    val undoers: List<BatchUndo> = listOf(ReversalUndo(txns, clock))
    val repair: RepairReversals = RepairReversals(RepairReversalsDeps(txns, clock))
    val refunds: RefundAsks = RefundAsks(RefundAsksDeps(txns, sources, links, clock, uow))

    fun asks(spaceId: String, foreign: ForeignSmsAsks? = null): ReturnsAskSource = ReturnsAskSource(spaceId, txns, foreign)
}

/**
 * خط رسايل البنك **لازم** فيه آثار الشريحة S3 (`SmsLane.of`): من غير `ReturnedSmsEffect` «التحويل رجع» كان بيتسجل داخل عادي (بيتحسب
 * دخل بالتخمين)، ومن غير `ForeignSmsEffect` إجابة المبلغ المحلي كانت بتتسجل من غير المبلغ الأجنبي — في صمت. الناقص بيتضاف في الآخر
 * (بعد آثار الشرايح التانية). `ReturnedSmsEffect` المضاف **من غير الروابط** ⇒ عمره ما بيلغي أصلية لوحده (بيسأل «نلغي الاتنين؟»)؛ التجميع
 * الكامل بيدّي [ReturnsWiring.effects] بالروابط.
 */
internal fun withReturnEffects(deps: ImportStatementDeps): ImportStatementDeps {
    val missing = buildList<RecordEffect> {
        if (deps.effects.none { it is ReturnedSmsEffect }) add(ReturnedSmsEffect(ReturnedSmsDeps(deps.txns, deps.sources, links = null)))
        if (deps.effects.none { it is ForeignSmsEffect }) add(ForeignSmsEffect())
    }
    return if (missing.isEmpty()) deps else deps.copy(effects = deps.effects + missing)
}
