package app.masroufy.core

import kotlin.math.abs

/**
 * «افهم رصيدك» (HANDOVER §6.5 بند 3 — المالك 2026-10-15: «ظاهرلي في حسابي سالب 20 الف ريال مش فاهم من ايه»): رصيد محفظة واحدة
 * متفكّك لأجزائه — رصيد البداية بتاريخه + الداخل والخارج **لكل مصدر** ⇒ الرصيد المحسوب، **بنفس قاعدة** `walletBalancesOn` بالظبط
 * (الحركة من يوم رصيد البداية لحد [BalanceExplanation.asOf]، والطرف الداخل للتحويل الداخلي وارد على المحفظة). اختبار بيتأكد إن المجموع
 * = رقم «معك الآن» بالهللة.
 * و«حاجات تستاهل تتراجع» ([BalanceReview]): مكرر محتمل · قبل رصيد البداية (مش محسوبة) · عملة تانية · رجوع/استرداد مستني قرار ·
 * تحويل لمحفظة مش موجودة · عمليات مش على أي محفظة. **قراية بس — ولا حاجة بتتمسح أو تتدمج من هنا** (الدمج بعد تأكيد المالك: `MergeDuplicates`).
 */

/** مصدر الحركة في التفكيك — كل حركة في مصدر واحد بس (الترتيب: تعديل رصيد ⇒ تحويل ⇒ الاتنين ⇒ كشف ⇒ رسالة ⇒ يدوي). */
enum class BalanceSource { STATEMENT, SMS, BOTH, MANUAL, TRANSFER, ADJUSTMENT }

/** العملية اتسجلت من كشف و/أو رسالة بنك (من سجلات مصدرها). مفيش سجلات ⇒ يدوي. */
data class TxnSources(val statement: Boolean, val sms: Boolean)

data class SourceTotal(val source: BalanceSource, val count: Int, val inMinor: Halalas, val outMinor: Halalas) {
    /** الصافي (الداخل − الخارج) — بيتعرض جنب اسم المصدر. */
    val netMinor: Halalas get() = subtractMoney(inMinor, outMinor)
}

/** سطر للعرض: الاتجاه **على المحفظة دي** (الطرف الداخل للتحويل = داخل). */
data class BalanceItem(
    val transactionId: Id,
    val date: IsoDate,
    val amountMinor: Halalas,
    val direction: Direction,
    val title: String,
    val currency: Currency,
    val source: BalanceSource,
)

/** رسالة وسطر كشف بنفس المبلغ (زي ما ورد) والاتجاه والعملة في ±النافذة، **والاتنين محسوبين** في الرصيد كعمليتين. */
data class DuplicatePair(
    val sms: BalanceItem,
    val statement: BalanceItem,
    val days: Int,
    /** زوج من «صلّح رسايل البنك القديمة» (`withRematchPairs`): الرسالة اتسجلت بعكس اتجاه الكشف (تحويل بين حساباتك) — الدمج بياخد اتجاه الكشف. */
    val oppositeDirection: Boolean = false,
)

data class BalanceReview(
    val duplicates: List<DuplicatePair>,
    /** قبل يوم رصيد البداية ⇒ **مش محسوبة** في الرصيد (المحفظة ما كانتش مفتوحة في التطبيق). */
    val beforeOpening: List<BalanceItem>,
    /** عملتها غير عملة المحفظة — **محسوبة برقمها** كأنها بعملة المحفظة (`walletBalancesOn` ما بيفرّقش). */
    val otherCurrency: List<BalanceItem>,
    /** شراء بعملة أجنبية اتسجل بالمبلغ المحلي اللي اتكتب (§75-12) — محسوب بالمحلي. */
    val foreignAmount: List<BalanceItem>,
    /** رجوع أو استرداد مستني قرارك (`suggestedKind` — §75-6 · §77-D) — محسوب بفلوسه، بس مش مربوط بالأصلية. */
    val reversals: List<BalanceItem>,
    /** تحويل لمحفظة مش موجودة في البلد — الفلوس خرجت من هنا وما دخلتش أي محفظة. */
    val oneSidedTransfers: List<BalanceItem>,
    /** عمليات بعملة المحفظة **مش على أي محفظة** — مش محسوبة في أي رصيد. */
    val unassigned: List<BalanceItem>,
) {
    val isEmpty: Boolean
        get() = duplicates.isEmpty() && beforeOpening.isEmpty() && otherCurrency.isEmpty() && foreignAmount.isEmpty() && reversals.isEmpty() &&
            oneSidedTransfers.isEmpty() && unassigned.isEmpty()
}

data class BalanceExplanation(
    val walletId: Id,
    val currency: Currency,
    /** null = رصيد البداية مش معروف (`Wallet.openingUnknown`) ⇒ «غير متاح». */
    val openingMinor: Halalas?,
    val openingAt: IsoDate,
    val asOf: IsoDate,
    /** المصادر اللي فيها حركة بس، بترتيب [BalanceSource]. */
    val totals: List<SourceTotal>,
    /** null = رصيد البداية بعد [asOf] (المحفظة لسه ما اتفتحتش يومها) ⇒ «غير متاح» زي «معك الآن». */
    val inMinor: Halalas?,
    val outMinor: Halalas?,
    val computedMinor: Halalas?,
    val review: BalanceReview,
)

// r041 A4: نفس اسم الصف والتفاصيل (`displayTitleOf`) — كان الوصف كله بسطوره قبل الملاحظة
private fun titleOf(t: Transaction): String = displayTitleOf(t)

private fun itemOf(t: Transaction, direction: Direction, source: BalanceSource) =
    BalanceItem(t.id, t.occurredAt, t.amountMinor, direction, titleOf(t), t.currency, source)

/** مصدر حركة المحفظة: الطرف الداخل للتحويل ⇒ تحويل؛ غير كده تعديل ⇒ تحويل ⇒ مصادر سجلاتها. */
private fun sourceOf(t: Transaction, incomingLeg: Boolean, sources: Map<Id, TxnSources>, isAdjustment: (Transaction) -> Boolean): BalanceSource {
    if (incomingLeg) return BalanceSource.TRANSFER
    if (isAdjustment(t)) return BalanceSource.ADJUSTMENT
    if (t.transferToWalletId != null || t.economicKind == EconomicKind.INTERNAL_TRANSFER) return BalanceSource.TRANSFER
    val s = sources[t.id] ?: return BalanceSource.MANUAL
    return when {
        s.statement && s.sms -> BalanceSource.BOTH
        s.statement -> BalanceSource.STATEMENT
        s.sms -> BalanceSource.SMS
        else -> BalanceSource.MANUAL
    }
}

/**
 * تفكيك رصيد [wallet] آخر يوم [asOf]. [transactions] = كل عمليات البلد اللي ممكن تلمس المحفظة (عليها أو تحويل ليها أو من غير محفظة) —
 * أي تاريخ (اللي قبل رصيد البداية بيتعرض ومش بيتحسب). [sources] = مصادر عمليات المحفظة. [wallets] = محافظ البلد (لتحويل لمحفظة مش موجودة).
 * [isAdjustment] = عملية معلّمة «تعديل رصيد» (الافتراضي ولا عملية). نقط «حدّث الرصيد» نفسها (`BalanceCheckpoint.kt`) مش عمليات: فرق كل نقطة لحد [asOf]
 * بيدخل سطر [BalanceSource.ADJUSTMENT] (موجب وارد · سالب صادر) ⇒ المجموع = «معك الآن» حتى مع النقط.
 */
fun explainWalletBalance(
    wallet: Wallet,
    wallets: List<Wallet>,
    transactions: List<Transaction>,
    sources: Map<Id, TxnSources>,
    asOf: IsoDate,
    windowDays: Int = CROSS_SOURCE_WINDOW_DAYS,
    isAdjustment: (Transaction) -> Boolean = { false },
): BalanceExplanation {
    val open = wallet.openingAt <= asOf
    val known = wallets.map { it.id }.toSet()
    val totals = LinkedHashMap<BalanceSource, Triple<Int, Halalas, Halalas>>()
    var inMinor = 0L
    var outMinor = 0L
    val before = mutableListOf<BalanceItem>()
    val other = mutableListOf<BalanceItem>()
    val foreign = mutableListOf<BalanceItem>()
    val reversals = mutableListOf<BalanceItem>()
    val oneSided = mutableListOf<BalanceItem>()
    val unassigned = mutableListOf<BalanceItem>()
    val counted = mutableListOf<Transaction>()

    fun add(source: BalanceSource, direction: Direction, amount: Halalas) {
        val (n, i, o) = totals[source] ?: Triple(0, 0L, 0L)
        totals[source] = if (direction == Direction.IN) Triple(n + 1, addMoney(i, amount), o) else Triple(n + 1, i, addMoney(o, amount))
        if (direction == Direction.IN) inMinor = addMoney(inMinor, amount) else outMinor = addMoney(outMinor, amount)
    }

    for (t in transactions.sortedWith(compareBy({ it.occurredAt }, { it.sourceOrder }, { it.id }))) {
        if (t.walletId == null && t.transferToWalletId == null) {
            if (t.currency == wallet.currency && t.occurredAt <= asOf) unassigned += itemOf(t, t.observedDirection, BalanceSource.MANUAL)
            continue
        }
        for (incoming in listOf(false, true)) {
            val mine = if (incoming) t.transferToWalletId == wallet.id else t.walletId == wallet.id
            if (!mine || t.occurredAt > asOf) continue
            val direction = if (incoming) Direction.IN else t.observedDirection
            val source = sourceOf(t, incoming, sources, isAdjustment)
            val item = itemOf(t, direction, source)
            if (t.occurredAt < wallet.openingAt) {
                before += item
                continue
            }
            add(source, direction, t.amountMinor)
            if (!incoming) counted += t
            if (t.currency != wallet.currency) other += item
            if (t.foreignCurrency != null && t.currency == wallet.currency) foreign += item
            if (!incoming && t.suggestedKind != null) reversals += item
            if (!incoming && t.transferToWalletId != null && t.transferToWalletId !in known) oneSided += item
        }
    }
    // «حدّث الرصيد» (r04-balance-now): فرق كل نقطة لحد [asOf] سطر «تعديلات» لوحده — مش عملية، بس داخل في رقم «معك الآن» (`balanceAdjustments`)
    if (open) for (e in balanceAdjustments(wallet, transactions, to = asOf)) {
        if (e.adjustmentMinor > 0L) add(BalanceSource.ADJUSTMENT, Direction.IN, e.adjustmentMinor)
        else if (e.adjustmentMinor < 0L) add(BalanceSource.ADJUSTMENT, Direction.OUT, subtractMoney(0L, e.adjustmentMinor))
    }
    val review = BalanceReview(findLikelyDuplicates(counted, sources, windowDays, isAdjustment), before, other, foreign, reversals, oneSided, unassigned)
    val lines = BalanceSource.entries.mapNotNull { s -> totals[s]?.let { (n, i, o) -> SourceTotal(s, n, i, o) } }
    // r041b P4: رصيد بداية مش معروف ولسه مفيش «حدّث الرصيد» ⇒ المحسوب «غير متاح» (نفس `walletBalancesOn`) — الحركات بتفضل ظاهرة
    val balanceKnown = balanceKnownFrom(wallet)?.let { it <= asOf } == true
    val computed = if (open && balanceKnown)addMoney(wallet.openingBalanceMinor, inMinor, -outMinor) else null
    return BalanceExplanation(
        wallet.id, wallet.currency, wallet.openingBalanceMinor.takeUnless { wallet.openingUnknown }, wallet.openingAt, asOf, lines,
        if (open) inMinor else null, if (open) outMinor else null, computed, review,
    )
}

/**
 * المكرر المحتمل (§75-10 من غير دمج): عملية من **رسالة بس** وعملية من **كشف بس** على نفس المحفظة، بنفس المبلغ زي ما ورد من المصدر
 * (قبل تعديل المالك — §32) ونفس الاتجاه والعملة، والفرق ≤ [windowDays] يوم — **واتسجلوا عمليتين**. ده بالظبط اللي الدمج كان هيعمله
 * لو كان شغال وقت التسجيل (نسخ 0.1 و0.2 ما كانش فيها دمج في الخلفية). كل عملية في زوج واحد بس: الأقرب في التاريخ الأول. التحويل
 * والتعديل والمدموجة (المصدرين) والعملية اليدوية ما بيدخلوش. **اقتراح بس** — المالك هو اللي بيقول «دول عملية واحدة».
 */
fun findLikelyDuplicates(
    own: List<Transaction>,
    sources: Map<Id, TxnSources>,
    windowDays: Int = CROSS_SOURCE_WINDOW_DAYS,
    isAdjustment: (Transaction) -> Boolean = { false },
): List<DuplicatePair> {
    require(windowDays >= 0) { "window must not be negative: $windowDays" }
    fun eligible(t: Transaction) = !isAdjustment(t) && t.transferToWalletId == null
    val smsOnly = own.filter { eligible(it) && sources[it.id]?.let { s -> s.sms && !s.statement } == true }
    val statementOnly = own.filter { eligible(it) && sources[it.id]?.let { s -> s.statement && !s.sms } == true }
    data class Key(val amount: Halalas, val direction: Direction, val currency: Currency, val wallet: Id?)
    fun keyOf(t: Transaction) = Key(sourceAmountMinor(t.amountMinor, t.originalAmountMinor), t.observedDirection, t.currency, t.walletId)
    val byKey = statementOnly.groupBy(::keyOf)
    data class Edge(val days: Int, val sms: Transaction, val statement: Transaction)
    val edges = mutableListOf<Edge>()
    for (m in smsOnly) {
        for (s in byKey[keyOf(m)].orEmpty()) {
            val days = abs(daysBetween(m.occurredAt, s.occurredAt))
            if (days <= windowDays) edges += Edge(days, m, s)
        }
    }
    val used = HashSet<Id>()
    val out = mutableListOf<DuplicatePair>()
    for (e in edges.sortedWith(compareBy({ it.days }, { it.sms.occurredAt }, { it.sms.id }, { it.statement.id }))) {
        if (e.sms.id in used || e.statement.id in used) continue
        used += e.sms.id
        used += e.statement.id
        out += DuplicatePair(itemOf(e.sms, e.sms.observedDirection, BalanceSource.SMS), itemOf(e.statement, e.statement.observedDirection, BalanceSource.STATEMENT), e.days)
    }
    return out.sortedWith(compareBy({ it.statement.date }, { it.sms.transactionId }))
}

/** مجموع أثر المكرر المحتمل على الرصيد: الخارج المكرر بينقّص والداخل المكرر بيزوّد — بالسالب = الرصيد أقل من الحقيقي بالقيمة دي. */
fun duplicateEffectMinor(pairs: List<DuplicatePair>): Halalas =
    sumMoney(pairs.map { if (it.sms.direction == Direction.IN) it.sms.amountMinor else -it.sms.amountMinor })

/**
 * «راجع المكرر» + «صلّح رسايل البنك القديمة» (r04-sms-fix — `RepairOldSms.duplicates`): أزواج رسالة⇄كشف **بعكس الاتجاه** (تحويل بين حساباتك
 * اتسجل بعكس الكشف) بتنضاف للمكرر المحتمل — لو الاتنين على المحفظة دي ومحسوبين (من يوم البداية لحد [BalanceExplanation.asOf]) وبنفس العملة
 * ومش تحويل لمحفظة ومش في زوج تاني. اقتراح بس — `MergeDuplicates` بيفحص الزوج من جديد من التخزين قبل أي كتابة.
 */
fun withRematchPairs(e: BalanceExplanation, pairs: List<SmsStatementPair>, byId: Map<Id, Transaction>): BalanceExplanation {
    val used = e.review.duplicates.flatMap { listOf(it.sms.transactionId, it.statement.transactionId) }.toMutableSet()
    val extra = mutableListOf<DuplicatePair>()
    fun counted(t: Transaction) = t.walletId == e.walletId && t.transferToWalletId == null && t.occurredAt >= e.openingAt && t.occurredAt <= e.asOf
    for (p in pairs) {
        if (!p.oppositeDirection || p.smsTransactionId in used || p.statementTransactionId in used) continue
        val sms = byId[p.smsTransactionId] ?: continue
        val st = byId[p.statementTransactionId] ?: continue
        if (!counted(sms) || !counted(st) || sms.currency != st.currency || sms.observedDirection == st.observedDirection) continue
        used += sms.id
        used += st.id
        extra += DuplicatePair(
            itemOf(sms, sms.observedDirection, BalanceSource.SMS), itemOf(st, st.observedDirection, BalanceSource.STATEMENT),
            abs(daysBetween(sms.occurredAt, st.occurredAt)), oppositeDirection = true,
        )
    }
    if (extra.isEmpty()) return e
    val all = (e.review.duplicates + extra).sortedWith(compareBy({ it.statement.date }, { it.sms.transactionId }))
    return e.copy(review = e.review.copy(duplicates = all))
}
