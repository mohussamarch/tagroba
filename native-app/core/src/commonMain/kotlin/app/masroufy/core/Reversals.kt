package app.masroufy.core

/**
 * §77-D (قرار المالك 2026-10-09): «تم رد المبلغ» / «التحويل رجع» ⇒ **يدوّر على العملية الأصلية برقمها المرجعي ويلغيها**؛ ولو ما لقاهاش ⇒
 * «استرداد» مقترح ويسأل. دوال نقية — حالة الاستخدام (`ReturnedSmsEffect` · `RefundAsks` · `RepairReversals`) بتجيب البيانات وتكتب.
 *
 * **«الإلغاء» (اختيار تقني للشريحة S3):** العمليتين **بيفضلوا** (رصيد المحفظة صح، ودمج الكشف والرسالة لسه شغال) وبيبقوا «تحويل داخلي»
 * مؤكد ومربوطين ببعض ([Transaction.reversalOfId] على اللي رجعت · [Transaction.reversedById] على الأصلية) ⇒ كل المجاميع و«حركة الفلوس»
 * والتطبيق القديم بيستبعدوهم **من غير أي تغيير في كود المجاميع**.
 * **أمان:** الأصلية المربوطة (دين · تخصيص · تسوية · مستحقات · حدث · مشروع · رجل تحويل بين البلاد …) أو اللي المالك أكد نوعها **ما بتتلغيش
 * لوحدها** ⇒ سؤال «نلغي الاتنين؟» ([ReversalMatch.Check]).
 */

/** العملية الأصلية بتتدوّر في الـ60 يوم اللي قبل الرجوع (لحد يوم الرجوع نفسه). */
const val REVERSAL_WINDOW_DAYS: Int = 60

fun reversalWindowStart(returnDate: IsoDate): IsoDate = dayNumberToIso(toDayNumber(parseIsoDate(returnDate)) - REVERSAL_WINDOW_DAYS)

/** مرشح: عملية + ذيول أرقامها المرجعية ([referenceTail]) من وصف الرسالة أو مرجع الكشف + هل مربوطة بحاجة. */
data class OriginalCandidate(val transaction: Transaction, val referenceTails: Set<String>, val linked: Boolean = false)

enum class ReversalMiss { NO_REFERENCE, NO_ORIGINAL, PARTIAL_AMOUNT, SEVERAL }

sealed interface ReversalMatch {
    /** أصلية واحدة بنفس المرجع والمبلغ، مش مربوطة ونوعها مش مؤكد ⇒ تتلغي لوحدها. */
    data class Cancel(val original: Transaction) : ReversalMatch

    /** أصلية واحدة بس مربوطة أو المالك أكد نوعها ⇒ سؤال «نلغي الاتنين؟». */
    data class Check(val original: Transaction) : ReversalMatch

    /** مفيش مرجع · ما لقيناش · مبلغ جزئي · أكتر من واحدة ⇒ «استرداد» مقترح ويسأل. */
    data class NotFound(val why: ReversalMiss) : ReversalMatch
}

/** الأصلية ممكن تتلغي مع [ret] (من غير شرط المرجع): نفس المحفظة والعملة والمبلغ، الاتجاه العكسي، قبلها في النافذة، ومش ملغية قبل كده. */
fun canReverse(ret: Transaction, original: Transaction): Boolean =
    original.id != ret.id && original.walletId == ret.walletId && original.currency == ret.currency &&
        original.observedDirection != ret.observedDirection && original.occurredAt <= ret.occurredAt &&
        original.occurredAt >= reversalWindowStart(ret.occurredAt) && original.reversedById == null && original.reversalOfId == null &&
        sourceAmountMinor(original.amountMinor, original.originalAmountMinor) == ret.amountMinor

/** بيدوّر على أصلية [ret] بذيل مرجعها [tail] وسط [candidates]. */
fun matchReversal(ret: Transaction, tail: String?, candidates: List<OriginalCandidate>): ReversalMatch {
    if (tail == null) return ReversalMatch.NotFound(ReversalMiss.NO_REFERENCE)
    val from = reversalWindowStart(ret.occurredAt)
    val sameReference = candidates.filter { c ->
        val t = c.transaction
        tail in c.referenceTails && t.id != ret.id && t.walletId == ret.walletId && t.currency == ret.currency &&
            t.observedDirection != ret.observedDirection && t.occurredAt >= from && t.occurredAt <= ret.occurredAt &&
            t.reversedById == null && t.reversalOfId == null
    }
    if (sameReference.isEmpty()) return ReversalMatch.NotFound(ReversalMiss.NO_ORIGINAL)
    val sameAmount = sameReference.filter { canReverse(ret, it.transaction) }
    if (sameAmount.isEmpty()) return ReversalMatch.NotFound(ReversalMiss.PARTIAL_AMOUNT)
    if (sameAmount.size > 1) return ReversalMatch.NotFound(ReversalMiss.SEVERAL)
    val found = sameAmount.single()
    return if (found.linked || found.transaction.economicKindConfirmed) ReversalMatch.Check(found.transaction) else ReversalMatch.Cancel(found.transaction)
}

/** الزوج اتلغى: الاتنين «تحويل داخلي» مؤكد ومربوطين ببعض، ومن غير اقتراح. */
fun cancelledReturn(ret: Transaction, originalId: Id, now: String): Transaction = ret.copy(
    economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED,
    suggestedKind = null, reversalOfId = originalId, updatedAt = now,
)

fun cancelledOriginal(original: Transaction, returnId: Id, now: String): Transaction = original.copy(
    economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED,
    suggestedKind = null, reversedById = returnId, updatedAt = now,
)

/** «استرداد» مقترح مستني تأكيد (§75-6): النوع بيفضل «غير محدد» — مش دخل. */
fun pendingRefund(ret: Transaction, now: String): Transaction =
    ret.copy(economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, suggestedKind = EconomicKind.REFUND_RECEIVED, reversalOfId = null, updatedAt = now)

/** «نلغي الاتنين؟» مستني تأكيد: الاقتراح «تحويل داخلي» (يتلغي مع الأصلية) — النوع بيفضل «غير محدد». */
fun pendingReversalCheck(ret: Transaction, now: String): Transaction =
    ret.copy(economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, suggestedKind = EconomicKind.INTERNAL_TRANSFER, updatedAt = now)

/** العملية مستنية «ده استرداد؟». */
fun awaitsRefundAnswer(t: Transaction): Boolean =
    t.suggestedKind == EconomicKind.REFUND_RECEIVED && t.economicKind == EconomicKind.UNCLASSIFIED && !t.economicKindConfirmed

/** العملية مستنية «نلغي الاتنين؟». */
fun awaitsReversalAnswer(t: Transaction): Boolean =
    t.suggestedKind == EconomicKind.INTERNAL_TRANSFER && t.economicKind == EconomicKind.UNCLASSIFIED && !t.economicKindConfirmed

/** زوج ملغي سليم: [ret] تحويل داخلي بيشاور على [original] والعكس. */
fun isCancelledPair(ret: Transaction, original: Transaction): Boolean =
    ret.reversalOfId == original.id && original.reversedById == ret.id &&
        ret.economicKind == EconomicKind.INTERNAL_TRANSFER && original.economicKind == EconomicKind.INTERNAL_TRANSFER
