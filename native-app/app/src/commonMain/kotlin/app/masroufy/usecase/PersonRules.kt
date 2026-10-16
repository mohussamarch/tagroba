package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.PersonMeaning
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.meaningForAuto
import app.masroufy.core.openDebtsFor
import app.masroufy.core.partyMatches
import app.masroufy.core.skips
import app.masroufy.core.sumMoney
import app.masroufy.core.transferAskOf
import app.masroufy.core.transferPartyOf
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * قواعد الحسابات المربوطة بشخص **لحظة التسجيل** (رد المالك 2026-10-16 — OVERRIDES §98 · A2): «بيحوّلي مستحقات دايمًا» (`TransferParty.dues`)
 * و«سجّل تحويلاته لوحده» (`TransferParty.autoMeaning`) بيشتغلوا أول ما رسالة البنك تتسجل — في دورة الخلفية والتسجيل التلقائي وتأكيد المالك
 * ([PersonRulesEffect]) — مش لما صفحة تتفتح. الاتنين بيعدّوا من نفس الربط ([ManagePersonLinks.link] بـ`auto`) ⇒ «فك الربط» على العملية
 * بيفضل موجود، والعملية اللي المالك فك ربطها أو غيّر معناها (`autoSkip`) عمر القاعدة ما ترجع عليها.
 * «مستحقات دايمًا» = سداد اللي الشخص عليه بس لو سؤال «ده سداد؟» قايم والمبلغ مش أكبر من المفتوح (نفس شرط زون التحويلات — ما بنخترعش دين).
 */
class PersonRules internal constructor(private val deps: PersonLinksDeps, private val owner: ManagePersonLinks) {
    /** بعد ما [ids] اتسجلت: كل عملية بقاعدة حسابها (المستحقات الأول). بيرجّع عدد اللي اتربط. */
    suspend fun onRecorded(ids: List<Id>): Int {
        if (ids.isEmpty()) return 0
        val ruled = ruledParties()
        if (ruled.isEmpty()) return 0
        val txns = deps.txns.findByIds(ids.distinct()).filter { !it.id.endsWith(OWN_LEG_SUFFIX) }
        return txns.sortedWith(compareBy({ it.occurredAt }, { it.id })).count { t -> applyOne(t, ruled) }
    }

    /** «مستحقات دايمًا» على عملية واحدة (تفاصيل العملية · زون التحويلات · رد «مستحقات دايمًا» نفسه). true لو اتسجلت سداد. */
    suspend fun dues(t: Transaction): Boolean {
        val ref = transferPartyOf(t) ?: return false
        val party = ruledParties().firstOrNull { it.dues && partyMatches(it, ref) } ?: return false
        if (party.skips(t.id) || owner.linkedAmong(listOf(t.id)).isNotEmpty()) return false
        return duesNow(t, party)
    }

    /** «تراجع» عن سداد سجّلته القاعدة جوه رد «مستحقات دايمًا» (القرار نفسه بيرجع، فمفيش منع للعملية). */
    suspend fun undo(transactionId: Id) {
        owner.unlink(transactionId, remember = false)
    }

    private suspend fun ruledParties(): List<TransferParty> =
        deps.parties.listAll().filter { it.verdict == TransferVerdict.PERSON && it.personId != null && (it.dues || it.autoMeaning != null) }

    private suspend fun applyOne(t: Transaction, ruled: List<TransferParty>): Boolean {
        val ref = transferPartyOf(t) ?: return false
        val mine = ruled.filter { partyMatches(it, ref) }
        if (mine.isEmpty() || mine.any { it.skips(t.id) }) return false
        if (owner.linkedAmong(listOf(t.id)).isNotEmpty()) return false
        mine.firstOrNull { it.dues }?.let { if (duesNow(t, it)) return true }
        val auto = mine.firstOrNull { it.autoMeaning != null && t.occurredAt.take(10) >= it.autoSince.orEmpty().take(10) } ?: return false
        val meaning = meaningForAuto(auto.autoMeaning ?: return false, t.observedDirection)
        return runCatching { owner.link(t.id, auto.personId ?: return false, meaning, auto = true) }.isSuccess
    }

    private suspend fun duesNow(t: Transaction, party: TransferParty): Boolean {
        val personId = party.personId ?: return false
        if (t.observedDirection != Direction.IN) return false
        val obligations = deps.obligations.listByPerson(personId)
        val settlements = deps.settlements.listByObligations(obligations.map { it.id })
        val dates = deps.txns.findByIds(obligations.mapNotNull { it.originTransactionId }).associate { it.id to it.occurredAt }
        if (transferAskOf(t, party, obligations, settlements, originDates = dates) != AskKind.DEBT_REPAYMENT) return false
        // الأكبر من المفتوح ⇒ بيفضل سؤال عادي (ما بنكتبش حاجة مش متأكدين منها)
        if (t.amountMinor > sumMoney(openDebtsFor(t, personId, obligations, settlements, dates).map { it.remainingMinor })) return false
        return runCatching { owner.link(t.id, personId, PersonMeaning.COLLECTED, auto = true) }.isSuccess
    }
}

/** أثر وقت التسجيل لرسايل البنك (A2): بعد ما الدفعة تتقفل ⇒ قواعد الحسابات المربوطة على اللي اتسجل. فشلها ما بيوقفش التسجيل. */
class PersonRulesEffect(private val rules: PersonRules) : RecordEffect {
    override suspend fun afterCommit(ctx: RecordContext) {
        rules.onRecorded(ctx.lines.map { it.transaction.id } + ctx.extra.map { it.first.id })
    }
}

/**
 * فهرس طرف التحويل لكل عملية (السرعة — صفحة الشخص): أول مرة بيقرا الكل ويفهم طرف كل عملية مرة واحدة، وبعد كده **الجديد بس**
 * (`listCreatedAfter`). نص البنك ما بيتغيرش بعد التسجيل، فالطرف ثابت؛ العملية الممسوحة بتقع لما تتقري بمعرّفها.
 */
internal class PartyTxnIndex(private val txns: TransactionRepository) {
    private val byKey = HashMap<String, MutableSet<Id>>()
    private val byLast4 = HashMap<String, MutableSet<Id>>()
    private var cursor: String? = null
    private val lock = Mutex()

    /** معرّفات العمليات اللي طرفها حساب من [parties] (بالمفتاح · أو بآخر 4 أرقام للحساب المكتوب بالإيد). */
    suspend fun idsFor(parties: List<TransferParty>): Set<Id> = lock.withLock {
        val fresh = cursor?.let { txns.listCreatedAfter(it) } ?: txns.listByDateRange(ManagePersonLinks.EARLIEST, ManagePersonLinks.LATEST)
        for (t in fresh) {
            if (t.id.endsWith(OWN_LEG_SUFFIX)) continue
            val ref = transferPartyOf(t) ?: continue
            byKey.getOrPut(ref.key) { HashSet() }.add(t.id)
            ref.last4?.let { byLast4.getOrPut(it) { HashSet() }.add(t.id) }
        }
        fresh.maxOfOrNull { it.createdAt }?.let { m -> if (cursor == null || m > cursor.orEmpty()) cursor = m }
        if (cursor == null) cursor = ""
        parties.flatMap { p ->
            byKey[p.key].orEmpty() + (if (p.key.startsWith("#") && p.last4 != null) byLast4[p.last4].orEmpty() else emptySet())
        }.toSet()
    }
}
