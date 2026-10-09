package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.port.Clock
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository

/**
 * تصليح أزواج «العملية اللي رجعت» (§77-D) — **آمن التكرار** (مرتين = نفس النتيجة). الشاشة أو دورة الخلفية بتناديه على فترة.
 * - رجوع ملغي والأصلية لسه ما اتعلّمتش (التطبيق وقع بعد الحفظ وقبل `afterCommit`) ⇒ الأصلية بتتلغي معاه.
 * - رجوع بيشاور على أصلية **مش موجودة** (اتمسحت) أو ملغية مع عملية تانية ⇒ يرجع «استرداد» مقترح ويسأل (زي «ما لقيناهاش»).
 * - المالك غيّر نوع **الرجوع** بنفسه (مش تحويل داخلي) ⇒ الربط بيتفك من الناحيتين والأصلية بترجع «غير محددة» تتراجع.
 * - المالك غيّر نوع **الأصلية** بنفسه ⇒ الربط بيتفك والرجوع بيرجع «استرداد» مقترح.
 * - أصلية بتشاور على رجوع مش موجود أو مش راجعلها ⇒ بترجع «غير محددة» تتراجع.
 * كل قرار بيتاخد على **آخر نسخة** من العمليتين (مش النسخة اللي اتقرت أول الفترة).
 */
data class RepairReversalsDeps(val txns: TransactionRepository, val clock: Clock)

data class RepairOutcome(
    /** أزواج اتكمّلت (الأصلية اتلغت). */
    val finished: Int = 0,
    /** رجوع اتفك ربطه ورجع «استرداد» مقترح. */
    val returnsReopened: Int = 0,
    /** أصليات رجعت «غير محددة». */
    val originalsRestored: Int = 0,
)

class RepairReversals(private val deps: RepairReversalsDeps) {
    private suspend fun fresh(id: Id): Transaction? = deps.txns.findByIds(listOf(id)).singleOrNull()

    suspend fun run(from: IsoDate, to: IsoDate): RepairOutcome {
        var finished = 0
        var reopened = 0
        var restored = 0
        val now = deps.clock.nowIso()
        for (row in deps.txns.listByDateRange(from, to)) {
            if (row.reversalOfId == null && row.reversedById == null) continue
            val t = fresh(row.id) ?: continue
            val originalId = t.reversalOfId
            if (originalId != null) {
                val original = fresh(originalId)
                when {
                    t.economicKind != EconomicKind.INTERNAL_TRANSFER -> {
                        // المالك غيّر نوع الرجوع ⇒ الزوج اتفك: الرجوع بيفضل بنوعه، والأصلية (لو كانت معاه) بترجع تتراجع
                        deps.txns.update(t.id, TransactionPatch(clearReversalOfId = true, updatedAt = now))
                        if (original?.reversedById == t.id) {
                            restoreOriginal(original.id, now)
                            restored++
                        }
                    }
                    original == null || (original.reversedById != null && original.reversedById != t.id) || original.reversalOfId != null -> {
                        reopenReturn(t.id, now)
                        reopened++
                    }
                    // الأصلية اتأكد نوعها في الوقت ده ⇒ ما تتلغيش لوحدها: الرجوع يرجع «نلغي الاتنين؟» (§77-D أمان)
                    original.reversedById == null && original.economicKindConfirmed -> {
                        deps.txns.update(t.id, checkAgainPatch(now))
                        reopened++
                    }
                    original.reversedById == null -> if (finishReversal(deps.txns, t.id, original.id, now)) finished++
                    original.economicKind != EconomicKind.INTERNAL_TRANSFER -> {
                        // المالك غيّر نوع الأصلية ⇒ الزوج اتفك: الأصلية بتفضل بنوعها، والرجوع بيرجع «استرداد» مقترح
                        deps.txns.update(original.id, TransactionPatch(clearReversedById = true, updatedAt = now))
                        reopenReturn(t.id, now)
                        reopened++
                    }
                }
            }
            // من ناحية الأصلية (الرجوع ممكن يبقى برّه الفترة) — نفس القواعد
            val returnId = t.reversedById ?: continue
            val original = fresh(t.id)?.takeIf { it.reversedById == returnId } ?: continue
            val ret = fresh(returnId)?.takeIf { it.reversalOfId == original.id }
            when {
                ret == null -> {
                    restoreOriginal(original.id, now)
                    restored++
                }
                ret.economicKind != EconomicKind.INTERNAL_TRANSFER -> {
                    deps.txns.update(ret.id, TransactionPatch(clearReversalOfId = true, updatedAt = now))
                    restoreOriginal(original.id, now)
                    restored++
                }
                original.economicKind != EconomicKind.INTERNAL_TRANSFER -> {
                    deps.txns.update(original.id, TransactionPatch(clearReversedById = true, updatedAt = now))
                    reopenReturn(ret.id, now)
                    reopened++
                }
            }
        }
        return RepairOutcome(finished, reopened, restored)
    }

    private suspend fun reopenReturn(id: Id, now: String) = deps.txns.update(id, reopenedReturnPatch(now))

    private suspend fun restoreOriginal(id: Id, now: String) = deps.txns.update(id, restoredOriginalPatch(now))
}

/** الرجوع اتفك ربطه ⇒ «استرداد» مقترح مستني تأكيد (النوع «غير محدد» — مش دخل). */
internal fun reopenedReturnPatch(now: String) = TransactionPatch(
    economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, reviewState = ReviewState.NEEDS_REVIEW,
    suggestedKind = EconomicKind.REFUND_RECEIVED, clearReversalOfId = true, updatedAt = now,
)

/** الرجوع يرجع سؤال «نلغي الاتنين؟» (اقتراح «تحويل داخلي» والنوع «غير محدد»). */
internal fun checkAgainPatch(now: String) = TransactionPatch(
    economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, reviewState = ReviewState.NEEDS_REVIEW,
    suggestedKind = EconomicKind.INTERNAL_TRANSFER, clearReversalOfId = true, updatedAt = now,
)

/** الأصلية اتفك ربطها ⇒ «غير محددة» ومحتاجة مراجعة (زي ما كانت قبل ما تتلغي لوحدها — الإلغاء التلقائي بس للنوع غير المؤكد). */
internal fun restoredOriginalPatch(now: String) = TransactionPatch(
    economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, reviewState = ReviewState.NEEDS_REVIEW,
    clearReversedById = true, clearSuggestedKind = true, updatedAt = now,
)
