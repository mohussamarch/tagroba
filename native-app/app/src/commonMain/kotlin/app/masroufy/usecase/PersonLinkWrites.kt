package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.PersonAllocation
import app.masroufy.core.PersonLink
import app.masroufy.core.PersonMeaning
import app.masroufy.core.Settlement
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransferVerdict
import app.masroufy.core.addMoney
import app.masroufy.core.formatMoney
import app.masroufy.core.kindForMeaning
import app.masroufy.core.partyMatches
import app.masroufy.core.remainingOfObligation
import app.masroufy.core.subtractMoney
import app.masroufy.core.sumMoney
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.sync.Mutex

/**
 * اللي الربط بيكتبه في الدفتر (مفصول عن `ManagePersonLinks.kt` عشان حد الـ300 سطر). كله بالهللة.
 * [LINK_LOCK] (F11): ربط/فك على نفس العملية من مكانين في نفس اللحظة (قاعدة الحساب + ضغطة المالك) ما يكتبوش مرتين — القفل على مستوى
 * البرنامج لأن فيه أكتر من نسخة من `ManagePersonLinks` (التفاصيل · صفحة الشخص · التسجيل في الخلفية).
 */
internal val LINK_LOCK = Mutex()

/** العمليات المربوطة بشخص في الدفتر من طريق تاني (دين أصله العملية · سداد · نصيب) — من غير روابط [PersonLink]. */
internal suspend fun ledgerLinkedIn(deps: PersonLinksDeps, ids: List<Id>): Set<Id> =
    if (ids.isEmpty()) emptySet()
    else (deps.obligations.listByTransactionIds(ids).mapNotNull { it.originTransactionId } +
        deps.settlements.listByTransactionIds(ids).map { it.transactionId } +
        deps.allocations.listByTransactionIds(ids).map { it.transactionId }).filter { it in ids }.toSet()

/** نصيب الشخص من الشراء ([amount] ≤ اللي فاضل من المبلغ بعد أي نصيب تاني). */
internal suspend fun allocationOf(deps: PersonLinksDeps, t: Transaction, personId: Id, meaning: PersonMeaning, amount: Halalas): PersonAllocation {
    val already = sumMoney(deps.allocations.listByTransactionIds(listOf(t.id)).map { it.amountMinor })
    val total = addMoney(already, amount)
    if (total > t.amountMinor) throw PersonLinkError(uiText(TextKey.LEDGER_ALLOCATIONS_EXCEED, formatMoney(total), formatMoney(t.amountMinor)))
    val kind = if (meaning == PersonMeaning.LENT) AllocationKind.RECEIVABLE else AllocationKind.GIFT
    return PersonAllocation(deps.ids.next("alloc"), t.id, personId, kind, amount, t.currency)
}

/**
 * «سداد» على ديونه المفتوحة بنفس العملة، الأقدم الأول (الدين القديم من غير عملية أقدم حاجة). [receivable] = اللي ليك عنده؛ غيره = **سلفة
 * عليك له بس** ([ObligationKind.LOAN_PAYABLE] — F12: الأمانة اللي عندك له مش دين بيتسدد بـ«سددتله»). بيرجّع السدادات واللي فضل.
 * مراجعة r051: من غير الديون اللي اتعملت **بعد** العملية (نفس شرط `openDebtsFor` في السؤال والقاعدة) — فلوس يناير ما بتسددش سلفة مارس.
 */
internal suspend fun settleOldestOf(deps: PersonLinksDeps, personId: Id, t: Transaction, receivable: Boolean): Pair<List<Settlement>, Halalas> {
    val wanted = if (receivable) ObligationKind.RECEIVABLE else ObligationKind.LOAN_PAYABLE
    val all = deps.obligations.listByPerson(personId).filter { it.currency == t.currency && it.kind == wanted }
    if (all.isEmpty()) return emptyList<Settlement>() to t.amountMinor
    val dates = deps.txns.findByIds(all.mapNotNull { it.originTransactionId }).associate { it.id to it.occurredAt }
    val day = t.occurredAt.take(10)
    val obligations = all.filter { o -> o.originTransactionId?.let(dates::get)?.let { it.take(10) <= day } ?: true }
    if (obligations.isEmpty()) return emptyList<Settlement>() to t.amountMinor
    val rows = deps.settlements.listByObligations(obligations.map { it.id })
    var left = t.amountMinor
    val out = mutableListOf<Settlement>()
    for (o in obligations.sortedWith(compareBy<Obligation>({ o -> o.originTransactionId?.let(dates::get).orEmpty() }, { it.id }))) {
        if (left <= 0L) break
        val remaining = remainingOfObligation(o, rows)
        if (remaining <= 0L) continue
        val part = minOf(left, remaining)
        out += Settlement(deps.ids.next("stl"), t.id, o.id, part)
        left = subtractMoney(left, part)
    }
    return out to left
}

/**
 * النوع اللي الربط غيّره بيرجع زي ما كان («فك الربط» · «تراجع» · مسح الدين — F1) — **بس لو العملية لسه بالنوع اللي الربط حطّه** (R5):
 * لو النوع اتغير بعد الربط، اللي اتختار بعده هو اللي بيفضل.
 */
internal suspend fun revertLinkKind(txns: TransactionRepository, link: PersonLink, now: String?) {
    val kind = link.kindBefore ?: return
    val t = txns.findByIds(listOf(link.transactionId)).firstOrNull() ?: return
    if (t.economicKind != kindForMeaning(link.meaning, true) && t.economicKind != kindForMeaning(link.meaning, false)) return
    txns.update(
        link.transactionId,
        TransactionPatch(
            economicKind = kind, economicKindConfirmed = link.kindConfirmedBefore, reviewState = link.reviewBefore,
            suggestedKind = link.suggestedBefore, updatedAt = now,
        ),
    )
}

/**
 * A2/F3: المالك فك ربط العملية أو غيّر معناها بإيده ⇒ قواعد كل حساب مربوط بشخص بيطابق طرفها («سجّل تحويلاته لوحده» · «مستحقات دايمًا»)
 * بتتمنع عليها للأبد ([app.masroufy.core.TransferParty.autoSkip]).
 */
internal suspend fun rememberSkip(deps: PersonLinksDeps, transactionId: Id) {
    markRulesOff(deps.txns, transactionId, deps.clock.nowIso())
    val t = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: return
    val ref = transferPartyOf(t) ?: return
    val ruled = deps.parties.listAll().filter {
        it.verdict == TransferVerdict.PERSON && (it.autoMeaning != null || it.dues) && partyMatches(it, ref) && transactionId !in it.autoSkip
    }
    for (p in ruled) deps.parties.save(p.copy(autoSkip = p.autoSkip + transactionId))
}

/**
 * A2/F3 (R5 · N2): العلامة على العملية نفسها (Transaction.personRulesOff) — ما بتضيعش لو الحساب ما كانش عليه قاعدة وقت الفك، ولا لو قراره
 * اتنسى أو اتعاد بعدين (القرار الجديد بيتبني من غير autoSkip).
 */
internal suspend fun markRulesOff(txns: TransactionRepository, transactionId: Id, now: String?) {
    val t = txns.findByIds(listOf(transactionId)).firstOrNull() ?: return
    if (t.personRulesOff != true) txns.update(transactionId, TransactionPatch(personRulesOff = true, updatedAt = now))
}

/**
 * N3 · R3: حركة بين فلوسك — تحويل بين محافظك (سحب كاش · «ده حسابي» ورِجله · تحويل داخلي مؤكد) أو نص زوج «رجعت» — أي معنى مع شخص عليها
 * كان هيعدّ نفس الفلوس مرتين (الرِجل التانية لسه بتزوّد المحفظة التانية). [hasOwnLeg] = العملية ليها رِجل في محفظة تانية.
 */
internal fun ownMoneyMove(t: Transaction, hasOwnLeg: Boolean): Boolean =
    hasOwnLeg || t.id.endsWith(OWN_LEG_SUFFIX) || t.transferToWalletId != null || t.reversalOfId != null || t.reversedById != null ||
        (t.economicKind == EconomicKind.INTERNAL_TRANSFER && t.economicKindConfirmed)

/** أصول رِجل «ده حسابي» في [all] (الرِجل معرّفها = الأصل + OWN_LEG_SUFFIX). */
internal fun ownLegBases(all: List<Transaction>): Set<Id> = all.filter { it.id.endsWith(OWN_LEG_SUFFIX) }.map { it.id.removeSuffix(OWN_LEG_SUFFIX) }.toSet()
