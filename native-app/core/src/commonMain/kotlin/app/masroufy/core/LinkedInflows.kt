package app.masroufy.core

/**
 * §6.5-14 «عمليات تابعة» (فكرة المالك 2026-10-15): من أي مصروف يختار فلوس **دخلت** (استرداد · تحويل من شخص · أي فلوس داخلة) ⇒ المصروف
 * بيتحسب **بالصافي** (أكلة 120 وتحويل تابع 60 ⇒ مصروف 60) وعليه علامة، والفلوس الداخلة **ما بتتحسبش دخل** في أي حتة وعليها علامة بترجّع
 * للمصروف. الرصيد ما بيتغيرش (الفلوس اتحركت فعلًا). دوال نقية — حالة الاستخدام (`LinkInflows`) بتجيب البيانات وتكتب.
 *
 * **الشكل (أقل مفاهيم جديدة):** حقلين اختياريين على العملية — [Transaction.linkedExpenseId] على الداخلة و[Transaction.linkedInflowMinor] على
 * المصروف (مجموع التابعة). الصافي بيدخل من [personalShareOf] اللي كل مجاميع الصرف بتعدّي عليها (الشهر · التصنيفات · الميزانيات · المساعد
 * · الوسوم والمشاريع)، والداخلة التابعة بتتخطّى في [computePeriodTotals] وبتبقى «استرداد» في العرض المعدود (`withEstimatedKinds`).
 * ليه مش زوج «العملية اللي رجعت» (§77-D)؟ ده بيلغي العمليتين **بالكامل** (تحويل داخلي)؛ هنا الخصم **جزئي** والمصروف بيفضل مصروف.
 * وليه مش «قسّمها على شخص»؟ ده محتاج شخص ودين؛ هنا أي فلوس داخلة حتى من محل.
 */

/** المرشحين بيتدوّروا في الـ60 يوم اللي قبل المصروف واللي بعده — التاريخ ما بيتعدلش (§13) فالمربوطة دايمًا جوه النافذة. */
const val LINK_WINDOW_DAYS: Int = 60

fun linkWindowStart(expenseDate: IsoDate): IsoDate = dayNumberToIso(toDayNumber(parseIsoDate(expenseDate.take(10))) - LINK_WINDOW_DAYS)

fun linkWindowEnd(expenseDate: IsoDate): IsoDate = dayNumberToIso(toDayNumber(parseIsoDate(expenseDate.take(10))) + LINK_WINDOW_DAYS)

/** الفلوس الداخلة دي تابعة لمصروف. */
fun isLinkedInflow(t: Transaction): Boolean = t.linkedExpenseId != null && t.observedDirection == Direction.IN

private const val SPLIT_SEP = '='

/** r06-1b3: نصيب كل مصروف من الداخلة دي (بالترتيب) — داخلة لمصروف واحد ⇒ المبلغ كله ليه · مش تابعة ⇒ فاضية. */
fun linkedSharesOf(t: Transaction): Map<Id, Halalas> {
    if (!isLinkedInflow(t)) return emptyMap()
    val split = t.linkedSplit ?: return mapOf(t.linkedExpenseId!! to t.amountMinor)
    return buildMap {
        for (part in split) {
            val at = part.lastIndexOf(SPLIT_SEP)
            val minor = if (at > 0) part.substring(at + 1).toLongOrNull() else null
            if (minor != null && minor > 0) put(part.substring(0, at), minor)
        }
    }
}

/** الداخلة دي تابعة للمصروف [expenseId] (كلها أو جزء منها). */
fun isLinkedTo(t: Transaction, expenseId: Id): Boolean = expenseId in linkedSharesOf(t)

/** نصيب [expenseId] من الداخلة [t] (صفر لو مش تابعة ليه). */
fun linkedShareTo(t: Transaction, expenseId: Id): Halalas = linkedSharesOf(t)[expenseId] ?: 0L

/** الشكل المتخزن لـ[Transaction.linkedSplit]. */
fun encodeLinkedSplit(shares: Map<Id, Halalas>): List<String> = shares.map { (id, minor) -> "$id$SPLIT_SEP$minor" }

/**
 * r06-1b3: توزيع الاسترداد [refundMinor] على المشتريات بالترتيب — كل واحد ياخد اللي فاضل فيه ([rooms]) لحد ما الاسترداد يخلص، واللي ما
 * خدش حاجة بيتشال. أعداد صحيحة بس.
 */
fun splitRefund(refundMinor: Halalas, rooms: List<Pair<Id, Halalas>>): Map<Id, Halalas> {
    var left = refundMinor
    return buildMap {
        for ((id, room) in rooms) {
            if (left <= 0) break
            val take = minOf(left, maxOf(0L, room))
            if (take > 0) { put(id, take); left -= take }
        }
    }
}

/** سطر المجموع تحت قايمة المشتريات: [diffMinor] = التحويل − المختار (صفر مطابق · موجب فاضل · سالب زيادة). */
data class RefundSum(val pickedMinor: Halalas, val diffMinor: Halalas)

fun refundSumOf(transferMinor: Halalas, picked: List<Halalas>): RefundSum {
    val total = sumMoney(picked)
    return RefundSum(total, subtractMoney(transferMinor, total))
}

/** اللي اتخصم من المصروف بالعمليات التابعة (صفر لو مفيش). */
fun linkedOffsetOf(t: Transaction): Halalas = if (t.observedDirection == Direction.OUT) t.linkedInflowMinor ?: 0L else 0L

/** المصروف بعد العمليات التابعة — للعرض: 120 − 60 = 60. */
fun netAfterLinks(t: Transaction): Halalas = maxOf(0L, subtractMoney(t.amountMinor, linkedOffsetOf(t)))

/** العملية تنفع يتربط بيها عمليات تابعة: صادرة، مش تحويل بين محافظك ولا ملغية، ونوعها مصروف (أو لسه غير محدد). */
fun canHaveLinkedInflows(t: Transaction): Boolean =
    t.observedDirection == Direction.OUT && t.transferToWalletId == null && t.reversalOfId == null && t.reversedById == null &&
        (t.economicKind == EconomicKind.UNCLASSIFIED || countsAsPersonalExpense(t.economicKind))

enum class LinkRefusal {
    /** العملية الأصلية مش مصروف. */
    NOT_EXPENSE,

    /** المختارة مش فلوس داخلة تنفع (تحويل بين محافظك · ملغية · عليها سؤال · برّه النافذة). */
    NOT_INFLOW,

    /** عملة تانية — ما بيتجمعش بين عملتين. */
    OTHER_CURRENCY,

    /** تابعة لمصروف تاني خلاص. */
    ALREADY_LINKED,

    /** مربوطة بدين أو تسوية أو تقسيم — هتتحسب مرتين. */
    LEDGER_LINKED,

    /** المجموع أكبر من المصروف ⇒ الصافي هيبقى أقل من صفر. */
    OVER_EXPENSE,
}

/** سبب رفض [inflow] كفلوس داخلة لـ[expense] من غير النظر للمبلغ — null = تنفع. */
fun inflowRefusal(expense: Transaction, inflow: Transaction): LinkRefusal? = when {
    inflow.currency != expense.currency -> LinkRefusal.OTHER_CURRENCY
    inflow.linkedExpenseId != null && !isLinkedTo(inflow, expense.id) -> LinkRefusal.ALREADY_LINKED
    inflow.id == expense.id || inflow.observedDirection != Direction.IN || inflow.transferToWalletId != null ||
        inflow.reversalOfId != null || inflow.reversedById != null || inflow.suggestedKind != null ||
        inflow.economicKind == EconomicKind.INTERNAL_TRANSFER ||
        inflow.occurredAt.take(10) < linkWindowStart(expense.occurredAt) || inflow.occurredAt.take(10) > linkWindowEnd(expense.occurredAt) ->
        LinkRefusal.NOT_INFLOW
    else -> null
}

/** اللي فاضل من المصروف يتخصم: المبلغ − نصيب الآخرين كدين − التابعة الموجودة (مش أقل من صفر). */
fun linkRoomOf(expense: Transaction, allocations: List<PersonAllocation>): Halalas {
    val receivable = sumMoney(allocations.filter { it.transactionId == expense.id && it.allocationKind == AllocationKind.RECEIVABLE }.map { it.amountMinor })
    return maxOf(0L, subtractMoney(subtractMoney(expense.amountMinor, receivable), linkedOffsetOf(expense)))
}

/** فحص ربط [inflows] الجديدة بـ[expense] (كل الشروط + المجموع مش أكبر من [linkRoomOf]) — null = ينفع. */
fun checkLink(expense: Transaction, inflows: List<Transaction>, allocations: List<PersonAllocation>): LinkRefusal? {
    if (!canHaveLinkedInflows(expense)) return LinkRefusal.NOT_EXPENSE
    for (i in inflows) inflowRefusal(expense, i)?.let { return it }
    val adding = sumMoney(inflows.filter { !isLinkedTo(it, expense.id) }.map { it.amountMinor })
    return if (adding > linkRoomOf(expense, allocations)) LinkRefusal.OVER_EXPENSE else null
}

/** الأقرب لتاريخ المصروف الأول، ولو نفس البعد فالأحدث الأول. */
fun orderByCloseness(expense: Transaction, inflows: List<Transaction>): List<Transaction> {
    val day = toDayNumber(parseIsoDate(expense.occurredAt.take(10)))
    return inflows.sortedWith(
        compareBy<Transaction> { kotlin.math.abs(toDayNumber(parseIsoDate(it.occurredAt.take(10))) - day) }
            .thenByDescending { it.occurredAt }.thenByDescending { it.sourceOrder },
    )
}

/** نافذة «نفس المبلغ» في اقتراح «قد تكون مرتبطة بـ» (فحص 0.5.3). */
const val QUICK_REFUND_SAME_AMOUNT_DAYS: Int = 30

/**
 * فحص 0.5.3 — «قد تكون مرتبطة بـ» كان بيقترح استردادات ملهاش علاقة (444 · 333 · 1,250 على شراء قهوة). الاقتراح السريع بس (مش قايمة
 * «عمليات تابعة» الكاملة) بقى: **نفس العملة** دايمًا (يعني نفس البلد)، وبعدها **نفس التاجر** أو **نفس المبلغ في حدود 30 يوم**.
 */
fun isQuickRefundCandidate(expense: Transaction, inflow: Transaction): Boolean {
    if (inflow.observedDirection != Direction.IN || inflow.currency != expense.currency) return false
    val sameMerchant = (expense.merchantId != null && expense.merchantId == inflow.merchantId) ||
        normalizeCompact(expense.rawMerchantName.orEmpty()).let { it.isNotEmpty() && it == normalizeCompact(inflow.rawMerchantName.orEmpty()) }
    if (sameMerchant) return true
    val days = daysBetween(expense.occurredAt.take(10), inflow.occurredAt.take(10))
    return inflow.amountMinor == expense.amountMinor && days in -QUICK_REFUND_SAME_AMOUNT_DAYS..QUICK_REFUND_SAME_AMOUNT_DAYS
}
