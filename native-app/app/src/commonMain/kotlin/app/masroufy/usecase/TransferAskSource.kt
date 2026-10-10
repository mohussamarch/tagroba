package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Obligation
import app.masroufy.core.PendingAsk
import app.masroufy.core.Settlement
import app.masroufy.core.TransferParty
import app.masroufy.core.LEGACY_AUTO_SUPPORT_UNTIL
import app.masroufy.core.TransferVerdict
import app.masroufy.core.isLegacySupportBefore
import app.masroufy.core.legacySupportAsk
import app.masroufy.core.transferAskOf
import app.masroufy.core.transferPartyOf
import app.masroufy.port.AllocationRepository
import app.masroufy.port.AskSource
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransferPartyRepository

/**
 * أسئلة التحويلات مع الأشخاص (§75-5 «سلفة ولا دعم؟» · §75-9 «ده سداد؟») في عدّ «محتاجة تأكيد» والتذكير الأسبوعي (§75-15).
 * القراية **محدودة بالأيام** ([pending] بياخد نافذة) — العمليات اللي في النافذة بس، والديون مرة واحدة لكل شخص، وروابط العمليات
 * في الدفتر 3 قرايات للكل (العملية اللي المالك ربطها بنفسه — §30 — مالهاش سؤال).
 * مفيش إشعار جوال جديد: السؤال جوه التطبيق على العملية (`AnswerTransferAsks`).
 * «لأ، مش سداد» ما بتتخزنش ⇒ العملية دي بتتعد هنا بسؤال «ده سداد؟» لحد ما نوعها يتأكد (العدد نفسه ما بيتغيرش: سؤال واحد لكل عملية).
 */
data class TransferAskSourceDeps(
    val spaceId: String,
    val txns: TransactionRepository,
    val parties: TransferPartyRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val allocations: AllocationRepository,
)

class TransferAskSource(private val deps: TransferAskSourceDeps) : AskSource {
    override suspend fun pending(from: IsoDate, to: IsoDate): List<PendingAsk> {
        val people = personParties(deps.parties.listAll())
        if (people.isEmpty()) return emptyList()
        val candidates = deps.txns.listByDateRange(from, to).mapNotNull { t ->
            if (t.economicKindConfirmed) null else transferPartyOf(t)?.let { people[it.key] }?.let { t to it }
        }
        // §82-13: «دعم» اتسجل لوحده قبل «سلفة ولا دعم؟» — **مهما كان تاريخه** (سؤال لمرة واحدة، مش من النافذة): من غير تاريخ في السؤال
        // عشان العدّ (`CountNeedsConfirmation`) ما يشيلوش بالنافذة
        val legacy = deps.txns.listByDateRange(EARLIEST, LEGACY_AUTO_SUPPORT_UNTIL.take(10)).mapNotNull { t ->
            transferPartyOf(t)?.let { people[it.key] }?.takeIf { isLegacySupportBefore(t, it) == true }?.let { t to it }
        }
        if (candidates.isEmpty() && legacy.isEmpty()) return emptyList()
        val debtsOf = PersonLedger(deps.obligations, deps.settlements, deps.txns)
        val links = PersonLedgerRepos(deps.obligations, deps.settlements, deps.allocations).linksOf((candidates + legacy).map { it.first.id }.distinct())
        val out = mutableListOf<PendingAsk>()
        for ((t, party) in candidates) {
            val debts = debtsOf.of(party.personId ?: continue)
            val kind = transferAskOf(t, party, debts.obligations, debts.settlements, links = links.getValue(t.id), originDates = debts.originDates)
                ?: continue
            out += PendingAsk(kind, deps.spaceId, transactionId = t.id, date = t.occurredAt)
        }
        for ((t, party) in legacy) {
            val debts = debtsOf.of(party.personId ?: continue)
            val kind = legacySupportAsk(t, party, debts.settlements, links.getValue(t.id)) ?: continue
            out += PendingAsk(kind, deps.spaceId, transactionId = t.id, date = null)
        }
        return out
    }

    private companion object {
        const val EARLIEST = "0000-01-01"
    }
}

/** الأطراف اللي اتقرر إنها «شخص» ومعاها الشخص — بمفتاح الطرف. */
internal fun personParties(all: List<TransferParty>): Map<String, TransferParty> =
    all.filter { it.verdict == TransferVerdict.PERSON && it.personId != null }.associateBy { it.key }

/**
 * ديون شخص وتسوياتها وتاريخ عملية كل دين — قراية واحدة لكل شخص في نفس الطلب. التاريخ عشان الدين اللي اتعمل **بعد** التحويل
 * ما يتسددش بيه (`openDebtsFor`).
 */
internal class PersonLedger(
    private val obligations: ObligationRepository,
    private val settlements: SettlementRepository,
    private val txns: TransactionRepository,
) {
    /** [originDates]: معرّف عملية الدين ⇒ تاريخها (الدين القديم من غير عملية — §27 — مالوش). */
    class Debts(val obligations: List<Obligation>, val settlements: List<Settlement>, val originDates: Map<Id, IsoDate>)

    private val cache = HashMap<Id, Debts>()

    suspend fun of(personId: Id): Debts {
        cache[personId]?.let { return it }
        val mine = obligations.listByPerson(personId)
        val origins = mine.mapNotNull { it.originTransactionId }.distinct()
        val debts = Debts(
            mine,
            if (mine.isEmpty()) emptyList() else settlements.listByObligations(mine.map { it.id }),
            if (origins.isEmpty()) emptyMap() else txns.findByIds(origins).associate { it.id to it.occurredAt },
        )
        cache[personId] = debts
        return debts
    }
}
