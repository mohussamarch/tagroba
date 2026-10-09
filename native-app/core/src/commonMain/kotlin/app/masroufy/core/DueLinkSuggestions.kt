package app.masroufy.core

import kotlin.math.abs

/**
 * §75-8 (قرار المالك 2026-10-08): **الأقساط والجمعية ⇒ يقترح الربط** (والقسط مصروف — §56، زي ما هو). الشريحة S4.
 *
 * عملية مش مربوطة بحاجة بتتقترح على أقرب ميعاد مفتوح **بنفس المبلغ بالظبط ونفس العملة**، والفرق ±٧ أيام:
 * قسط خطة (فلوس طالعة) · قسط جمعية (طالعة) · قبض دورك في الجمعية (داخلة) · ومبلغ التمويل المستلم (داخل — من قبل أول قسط بدورة وأسبوع
 * لحد أول قسط؛ **اختيار Claude**: الخطة مالهاش «ميعاد استلام»). كل عملية بتتقترح على حاجة واحدة، وكل ميعاد بياخد عملية واحدة (الأقرب).
 * «مش ده» بيتحفظ على الخطة أو الجمعية ([InstallmentPlan.dismissedTxnIds] · [Rosca.dismissedTxnIds]) فما يتقترحش عليها تاني.
 * الاقتراح ما بيكتبش حاجة — القبول بيعدّي على الربط العادي (`ManageInstallments.link` …) بكل فحوصه.
 */
const val DUE_LINK_DAYS = 7

enum class DueLinkKind(val direction: Direction) {
    INSTALLMENT(Direction.OUT), FINANCING_RECEIVED(Direction.IN), ROSCA_CONTRIBUTION(Direction.OUT), ROSCA_PAYOUT(Direction.IN),
}

/** ميعاد مفتوح ممكن عملية تكونه: رقم القسط أو الدور ([number] — صفر لمبلغ التمويل) · الفاضل منه · النافذة · اللي المالك رفضه. */
data class DueOpening(
    val kind: DueLinkKind,
    val ownerId: Id,
    val ownerName: String,
    val number: Int,
    val dueAt: IsoDate?,
    val amountMinor: Halalas,
    val currency: Currency,
    val from: IsoDate,
    val to: IsoDate,
    val dismissed: Set<Id> = emptySet(),
)

data class DueLinkSuggestion(
    val transactionId: Id,
    val transactionDate: IsoDate,
    val kind: DueLinkKind,
    val ownerId: Id,
    val ownerName: String,
    val number: Int,
    val dueAt: IsoDate?,
    val amountMinor: Halalas,
    val currency: Currency,
)

/** الأنواع اللي ممكن تتربط بالمستحقات من غير ما نكتب فوق قرار المالك: لسه ما اتحددش · شراء · أو نوع من المستحقات نفسها. */
private val LINKABLE_KINDS = setOf(
    EconomicKind.UNCLASSIFIED, EconomicKind.PURCHASE, EconomicKind.INSTALLMENT_PAID, EconomicKind.ROSCA_CONTRIBUTION, EconomicKind.ROSCA_PAYOUT,
    EconomicKind.FINANCING_RECEIVED,
)

/** العملية تنفع تتقترح؟ مش تحويل بين محافظك، ونوعها مش متأكد على حاجة تانية (سلفة · راتب · تحويل …). */
fun dueLinkCandidate(t: Transaction): Boolean = t.transferToWalletId == null && (!t.economicKindConfirmed || t.economicKind in LINKABLE_KINDS)

private fun addDays(date: IsoDate, days: Int): IsoDate = dayNumberToIso(toDayNumber(parseIsoDate(date)) + days)

/** الأقساط المفتوحة لحد [until] (ميعادها − ٧ ≤ [until]) + مبلغ التمويل لو لسه ما اتربطش. [paidMinor] = المربوط بالخطة. */
fun installmentOpenings(plan: InstallmentPlan, paidMinor: Halalas, until: IsoDate): List<DueOpening> {
    val out = mutableListOf<DueOpening>()
    val dismissed = plan.dismissedTxnIds.toSet()
    if (plan.kind == InstallmentKind.FINANCING && plan.receivedTransactionId == null) {
        val from = addDays(shiftMonths(plan.firstDueAt, -plan.cycleMonths), -DUE_LINK_DAYS)
        out += DueOpening(DueLinkKind.FINANCING_RECEIVED, plan.id, plan.name, 0, null, plan.principalMinor, plan.currency, from, plan.firstDueAt, dismissed)
    }
    val schedule = installmentSchedule(plan)
    out += scheduleOpenings(DueLinkKind.INSTALLMENT, plan.id, plan.name, schedule, paidMinor, until, dismissed)
    return out
}

/** أقساط الجمعية المفتوحة + أدوارك اللي لسه ما اتقبضتش كاملة (لو دورك اتحدد). */
fun roscaOpenings(rosca: Rosca, entries: List<RoscaEntry>, until: IsoDate): List<DueOpening> {
    val dismissed = rosca.dismissedTxnIds.toSet()
    val mine = entries.filter { it.roscaId == rosca.id }
    val paid = sumMoney(mine.filter { it.kind == RoscaEntryKind.CONTRIBUTION }.map { it.amountMinor })
    val out = scheduleOpenings(DueLinkKind.ROSCA_CONTRIBUTION, rosca.id, rosca.name, contributionSchedule(rosca), paid, until, dismissed).toMutableList()
    for (p in roscaStatus(rosca, mine, rosca.firstDueAt).payouts) {
        if (p.receivedMinor >= p.amountMinor || addDays(p.dueAt, -DUE_LINK_DAYS) > until) continue
        out += DueOpening(
            DueLinkKind.ROSCA_PAYOUT, rosca.id, rosca.name, p.turn, p.dueAt, subtractMoney(p.amountMinor, p.receivedMinor), rosca.currency,
            addDays(p.dueAt, -DUE_LINK_DAYS), addDays(p.dueAt, DUE_LINK_DAYS), dismissed,
        )
    }
    return out
}

private fun scheduleOpenings(kind: DueLinkKind, ownerId: Id, name: String, s: DueSchedule, paidMinor: Halalas, until: IsoDate, dismissed: Set<Id>): List<DueOpening> {
    val progress = dueProgress(s, paidMinor, s.firstDueAt)
    val first = progress.nextNumber ?: return emptyList()
    val out = mutableListOf<DueOpening>()
    for (n in first..progress.count) {
        val dueAt = dueDateOf(s, n)
        if (addDays(dueAt, -DUE_LINK_DAYS) > until) break
        val amount = if (n == first) progress.nextAmountMinor else installmentAmountOf(s, n)
        out += DueOpening(kind, ownerId, name, n, dueAt, amount, s.currency, addDays(dueAt, -DUE_LINK_DAYS), addDays(dueAt, DUE_LINK_DAYS), dismissed)
    }
    return out
}

/** العملية ممكن تكون الميعاد ده؟ (من غير فحص «مربوطة بحاجة» — ده على المستدعي). */
fun fitsOpening(t: Transaction, o: DueOpening): Boolean =
    t.observedDirection == o.kind.direction && t.currency == o.currency && t.amountMinor == o.amountMinor &&
        t.occurredAt >= o.from && t.occurredAt <= o.to && t.id !in o.dismissed && dueLinkCandidate(t)

/**
 * الاقتراحات: [linked] = عمليات مربوطة بحاجة خلاص (جمعية · قسط · مبلغ تمويل · زكاة · نقطة · رجل تحويل لنفسك) ⇒ ما بتتقترحش.
 * التوزيع بالأقرب: كل زوج (عملية، ميعاد) بالفرق بالأيام، والعملية والميعاد بياخدوا أول زوج ليهم بس — نفس النتيجة مهما كان الترتيب.
 */
fun suggestDueLinks(transactions: List<Transaction>, openings: List<DueOpening>, linked: Set<Id>): List<DueLinkSuggestion> {
    data class Pair2(val t: Transaction, val o: DueOpening, val distance: Int)
    val pairs = mutableListOf<Pair2>()
    for (t in transactions.distinctBy { it.id }) {
        if (t.id in linked) continue
        for (o in openings) if (fitsOpening(t, o)) pairs += Pair2(t, o, abs(daysBetween(o.dueAt ?: o.to, t.occurredAt)))
    }
    pairs.sortWith(compareBy<Pair2>({ it.distance }, { it.t.occurredAt }, { it.t.id }, { it.o.kind.ordinal }, { it.o.ownerId }, { it.o.number }))
    val takenTxns = HashSet<Id>()
    val takenOpenings = HashSet<String>()
    val out = mutableListOf<DueLinkSuggestion>()
    for (p in pairs) {
        val key = "${p.o.kind}|${p.o.ownerId}|${p.o.number}"
        if (p.t.id in takenTxns || key in takenOpenings) continue
        takenTxns += p.t.id
        takenOpenings += key
        out += DueLinkSuggestion(p.t.id, p.t.occurredAt, p.o.kind, p.o.ownerId, p.o.ownerName, p.o.number, p.o.dueAt, p.o.amountMinor, p.o.currency)
    }
    return out.sortedWith(compareBy({ it.transactionDate }, { it.transactionId }))
}

/** السؤال اللي بيتعرض على العملية («نربطه؟»). */
fun dueLinkQuestion(s: DueLinkSuggestion): String = when (s.kind) {
    DueLinkKind.INSTALLMENT -> uiText(TextKey.DUE_LINK_ASK_INSTALLMENT, s.ownerName, s.dueAt ?: "")
    DueLinkKind.ROSCA_CONTRIBUTION -> uiText(TextKey.DUE_LINK_ASK_ROSCA_CONTRIBUTION, s.ownerName, s.dueAt ?: "")
    DueLinkKind.ROSCA_PAYOUT -> uiText(TextKey.DUE_LINK_ASK_ROSCA_PAYOUT, s.ownerName, s.dueAt ?: "")
    DueLinkKind.FINANCING_RECEIVED -> uiText(TextKey.DUE_LINK_ASK_FINANCING_RECEIVED, s.ownerName)
}
