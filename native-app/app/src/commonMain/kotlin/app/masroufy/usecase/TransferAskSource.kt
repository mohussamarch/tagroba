package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Obligation
import app.masroufy.core.PendingAsk
import app.masroufy.core.Settlement
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.transferAskOf
import app.masroufy.core.transferPartyOf
import app.masroufy.port.AskSource
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransferPartyRepository

/**
 * أسئلة التحويلات مع الأشخاص (§75-5 «سلفة ولا دعم؟» · §75-9 «ده سداد؟») في عدّ «محتاجة تأكيد» والتذكير الأسبوعي (§75-15).
 * القراية **محدودة بالأيام** ([pending] بياخد نافذة) — العمليات اللي في النافذة بس، والديون مرة واحدة لكل شخص.
 * مفيش إشعار جوال جديد: السؤال جوه التطبيق على العملية (`AnswerTransferAsks`).
 */
data class TransferAskSourceDeps(
    val spaceId: String,
    val txns: TransactionRepository,
    val parties: TransferPartyRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
)

class TransferAskSource(private val deps: TransferAskSourceDeps) : AskSource {
    override suspend fun pending(from: IsoDate, to: IsoDate): List<PendingAsk> {
        val people = personParties(deps.parties.listAll())
        if (people.isEmpty()) return emptyList()
        val ledger = PersonLedger(deps.obligations, deps.settlements)
        val out = mutableListOf<PendingAsk>()
        for (t in deps.txns.listByDateRange(from, to)) {
            if (t.economicKindConfirmed) continue
            val party = transferPartyOf(t)?.let { people[it.key] } ?: continue
            val debts = ledger.of(party.personId ?: continue)
            val kind = transferAskOf(t, party, debts.obligations, debts.settlements) ?: continue
            out += PendingAsk(kind, deps.spaceId, transactionId = t.id, date = t.occurredAt)
        }
        return out
    }
}

/** الأطراف اللي اتقرر إنها «شخص» ومعاها الشخص — بمفتاح الطرف. */
internal fun personParties(all: List<TransferParty>): Map<String, TransferParty> =
    all.filter { it.verdict == TransferVerdict.PERSON && it.personId != null }.associateBy { it.key }

/** ديون شخص وتسوياتها — قراية واحدة لكل شخص في نفس الطلب. */
internal class PersonLedger(private val obligations: ObligationRepository, private val settlements: SettlementRepository) {
    class Debts(val obligations: List<Obligation>, val settlements: List<Settlement>)

    private val cache = HashMap<Id, Debts>()

    suspend fun of(personId: Id): Debts {
        cache[personId]?.let { return it }
        val mine = obligations.listByPerson(personId)
        val debts = Debts(mine, if (mine.isEmpty()) emptyList() else settlements.listByObligations(mine.map { it.id }))
        cache[personId] = debts
        return debts
    }
}
