package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.Transaction
import app.masroufy.core.awaitsRefundAnswer
import app.masroufy.core.awaitsReversalAnswer
import app.masroufy.core.isCancelledPair
import app.masroufy.port.Clock
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository

/**
 * تصليح أزواج «العملية اللي رجعت» (§77-D) — **آمن التكرار** (مرتين = نفس النتيجة). دورة الخلفية بتشغّله (`RunBackgroundCycle`) وفتح
 * التطبيق كمان (التجميع)، والنسخة الشاملة بتطبّق نفس القواعد على الملف (`core/ReversalBackup.kt`).
 * **القراية على قد الأزواج:** العمليات اللي عليها ربط بس (`listReversalLinked` — شرط على الحقل، مش فترة عمليات كاملة كل دورة على باقة
 * Spark)، والزوج السليم ما بيتقراش تاني ولا بيتكتب. الربط بيفضل عليه لحد ما يتصلح، فمفيش زوج مكسور بيفلت مهما قدم.
 * - رجوع ملغي والأصلية لسه ما اتعلّمتش (التطبيق وقع بعد الحفظ وقبل `afterCommit`) ⇒ الأصلية بتتلغي معاه — إلا لو اتأكد نوعها في الوقت
 *   ده ⇒ الرجوع يرجع «نلغي الاتنين؟».
 * - أصلية بتشاور على رجوع **لسه مستني سؤاله** (وقع بين كتابتين «أيوه نلغيهم» · أو الأصلية اتسجلت بعد رجوعها) ⇒ الرجوع بيتكمّل.
 * - رجوع بيشاور على أصلية **مش موجودة** (اتمسحت — زي التراجع في التطبيق القديم `revertImportBatch.ts` اللي ما يعرفش الربط) أو ملغية
 *   مع عملية تانية ⇒ يرجع «استرداد» مقترح ويسأل (زي «ما لقيناهاش»).
 * - المالك غيّر نوع **الرجوع** بنفسه ⇒ الربط بيتفك من الناحيتين والأصلية بترجع **زي ما كانت** (`restoredKindOf` — نوع المالك لو اتحفظ).
 * - المالك غيّر نوع **الأصلية** بنفسه ⇒ الربط بيتفك (نوعه ما بيتلمسش) والرجوع بيرجع «استرداد» مقترح.
 * - أصلية بتشاور على رجوع مش موجود أو مش راجعلها ⇒ بترجع زي ما كانت.
 * كل قرار بيتاخد على **آخر نسخة** من العمليتين (مش النسخة اللي اتقرت أول الدورة).
 */
data class RepairReversalsDeps(val txns: TransactionRepository, val clock: Clock)

data class RepairOutcome(
    /** أزواج اتكمّلت (الأصلية أو الرجوع اتلغى). */
    val finished: Int = 0,
    /** رجوع اتفك ربطه ورجع يسأل. */
    val returnsReopened: Int = 0,
    /** أصليات رجعت زي ما كانت (أو اتفك ربطها). */
    val originalsRestored: Int = 0,
)

/** الزوج سليم من غير قراية تانية: الطرف التاني وسط العمليات المربوطة اللي اتقرت، والاتنين بيشاوروا على بعض «تحويل داخلي». */
private fun healthy(t: Transaction, linked: Map<Id, Transaction>): Boolean {
    val originalId = t.reversalOfId
    val returnId = t.reversedById
    return when {
        originalId != null && returnId != null -> false
        originalId != null -> linked[originalId]?.let { isCancelledPair(t, it) } == true
        returnId != null -> linked[returnId]?.let { isCancelledPair(it, t) } == true
        else -> true
    }
}

class RepairReversals(private val deps: RepairReversalsDeps) {
    private suspend fun fresh(id: Id): Transaction? = deps.txns.findByIds(listOf(id)).singleOrNull()

    suspend fun run(): RepairOutcome {
        var finished = 0
        var reopened = 0
        var restored = 0
        val now = deps.clock.nowIso()
        val linked = deps.txns.listReversalLinked()
        val byId = linked.associateBy { it.id }
        for (row in linked) {
            if (healthy(row, byId)) continue
            val t = fresh(row.id) ?: continue
            val originalId = t.reversalOfId
            if (originalId != null) {
                val original = fresh(originalId)
                when {
                    t.economicKind != EconomicKind.INTERNAL_TRANSFER -> {
                        // المالك غيّر نوع الرجوع ⇒ الزوج اتفك: الرجوع بيفضل بنوعه، والأصلية (لو كانت معاه) بترجع زي ما كانت
                        deps.txns.update(t.id, TransactionPatch(clearReversalOfId = true, updatedAt = now))
                        if (original?.reversedById == t.id && original.economicKind == EconomicKind.INTERNAL_TRANSFER) {
                            deps.txns.update(original.id, restoredOriginalPatch(original, now))
                            restored++
                        }
                    }
                    original == null || (original.reversedById != null && original.reversedById != t.id) || original.reversalOfId != null -> {
                        deps.txns.update(t.id, reopenedReturnPatch(now))
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
                        deps.txns.update(original.id, unlinkedOriginalPatch(now))
                        deps.txns.update(t.id, reopenedReturnPatch(now))
                        reopened++
                    }
                }
            }
            // من ناحية الأصلية (الرجوع ممكن يبقى برّه الفترة) — نفس القواعد
            val returnId = t.reversedById ?: continue
            val original = fresh(t.id)?.takeIf { it.reversedById == returnId } ?: continue
            val ret = fresh(returnId)
            when {
                original.economicKind != EconomicKind.INTERNAL_TRANSFER -> {
                    // المالك غيّر نوع الأصلية — نوعه ما بيتلمسش، الربط بس بيتشال
                    deps.txns.update(original.id, unlinkedOriginalPatch(now))
                    if (ret?.reversalOfId == original.id) {
                        deps.txns.update(ret.id, reopenedReturnPatch(now))
                        reopened++
                    } else {
                        restored++
                    }
                }
                ret?.reversalOfId == original.id && ret.economicKind == EconomicKind.INTERNAL_TRANSFER -> Unit
                ret != null && ret.reversalOfId == null && (awaitsRefundAnswer(ret) || awaitsReversalAnswer(ret)) ->
                    if (finishReturn(deps.txns, original.id, ret.id, now)) finished++
                ret?.reversalOfId == original.id -> {
                    // الرجوع اتغيّر نوعه بإيد المالك
                    deps.txns.update(ret.id, TransactionPatch(clearReversalOfId = true, updatedAt = now))
                    deps.txns.update(original.id, restoredOriginalPatch(original, now))
                    restored++
                }
                else -> {
                    deps.txns.update(original.id, restoredOriginalPatch(original, now))
                    restored++
                }
            }
        }
        return RepairOutcome(finished, reopened, restored)
    }
}

/** الأصلية اللي المالك غيّر نوعها بنفسه: الربط بس بيتشال. */
private fun unlinkedOriginalPatch(now: String) = TransactionPatch(clearReversedById = true, clearKindBeforeReversal = true, updatedAt = now)
