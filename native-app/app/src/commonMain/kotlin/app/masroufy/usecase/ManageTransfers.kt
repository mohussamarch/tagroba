package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.INCOMING_FROM_PERSON_KINDS
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.ReviewState
import app.masroufy.core.SuspiciousParty
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.TransferVerdict
import app.masroufy.core.addMoney
import app.masroufy.core.applyTransferVerdict
import app.masroufy.core.suspiciousTransferParties
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.PersonRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransferPartyRepository
import app.masroufy.port.UnitOfWork

/**
 * «زون التحويلات» (OVERRIDES §39 · §39.1 · §42 · §60): التحويلات متجمعة بالطرف التاني، والأسئلة عن الأطراف اللي التحويلات
 * معاها كترت، والقرار عليها. الشاشة نفسها مستنية تصميم المالك (§55).
 */
data class ManageTransfersDeps(
    val txns: TransactionRepository,
    val parties: TransferPartyRepository,
    val people: PersonRepository,
    val uow: UnitOfWork,
    val clock: Clock,
    /**
     * دفتر الأشخاص: «ده حسابي التاني» بيشيل اللي إجابات «سلفة ولا دعم؟» و«ده سداد؟» كتبته على تحويلات الطرف (الفلوس راحت لحسابك،
     * فمفيش حد مديون بيها). **التشغيل الحقيقي لازم يدّيه**؛ null = سلوك ما قبل الإصلاح (الدين بيفضل).
     */
    val ledger: PersonLedgerRepos? = null,
)

/** سطر في الزون: طرف بعملة واحدة (المبالغ ما بتتجمعش بين عملتين). */
data class TransferZoneRow(
    val party: TransferPartyRef,
    val decision: TransferParty?,
    val currency: Currency,
    val count: Int,
    val incomingMinor: Halalas,
    val outgoingMinor: Halalas,
    val lastAt: IsoDate,
)

/**
 * [unidentified]: تحويلات مالهاش طرف يتعرف (الاسم ضاع من ملف البنك أو مش موجود) — بتتعد بس، ما بنخمّنش طرفها.
 * [incomingChoices]: الأنواع اللي بتتعرض لما فلوس تيجي من شخص (§42).
 */
data class TransferZone(
    val rows: List<TransferZoneRow>,
    val questions: List<SuspiciousParty>,
    val unidentified: Int,
    val incomingChoices: List<EconomicKind> = INCOMING_FROM_PERSON_KINDS,
)

class TransferZoneError(message: String) : IllegalArgumentException(message)

class ManageTransfers(private val deps: ManageTransfersDeps) {
    private suspend fun everything(): List<Transaction> = deps.txns.listByDateRange(EARLIEST, LATEST)

    suspend fun zone(): TransferZone {
        val all = everything()
        val decided = deps.parties.listAll().associateBy { it.key }
        val groups = LinkedHashMap<Pair<String, Currency>, MutableList<Pair<TransferPartyRef, Transaction>>>()
        var unidentified = 0
        for (t in all) {
            val party = transferPartyOf(t)
            if (party == null) {
                if (app.masroufy.core.isTransferLike(t)) unidentified++
                continue
            }
            groups.getOrPut(party.key to t.currency) { mutableListOf() } += party to t
        }
        val rows = groups.map { (key, list) ->
            var incoming = 0L
            var outgoing = 0L
            for ((_, t) in list) if (t.observedDirection == Direction.IN) incoming = addMoney(incoming, t.amountMinor) else outgoing = addMoney(outgoing, t.amountMinor)
            TransferZoneRow(list.first().first, decided[key.first], key.second, list.size, incoming, outgoing, list.maxOf { it.second.occurredAt })
        }.sortedWith(compareByDescending<TransferZoneRow> { it.count }.thenBy { it.party.key })
        return TransferZone(rows, suspiciousTransferParties(all, decided.keys), unidentified)
    }

    /**
     * «ده حسابي التاني» ⇒ كل التحويلات معاه (القديم كمان — قرار المالك §39 (ج)) تحويل داخلي. بيرجّع عدد العمليات اللي اتغيرت.
     * ومع [ManageTransfersDeps.ledger]: اللي إجابات السؤال كتبته على التحويلات دي (دين «سلفة» ونصيبه، وتسويات «أيوه، سداد») بيتشال
     * في نفس الخطوة. السلفة اللي عليها سداد اتسجل **من مكان تاني** ⇒ مرفوض بسببه ومفيش ولا كتابة (ما بنمسحش حاجة المالك كتبها).
     * الربط اللي المالك عمله بنفسه من شاشة الأشخاص ما بيتلمسش (زي ما كان).
     */
    suspend fun markOwnAccount(party: TransferPartyRef): Int = decide(party, TransferVerdict.OWN_ACCOUNT, null)

    /**
     * «ده شخص» ⇒ كل تحويل معاه نوعه لسه ما اتأكدش **بيتسأل** (القديم كمان): الصادر «سلفة ولا دعم؟» كل مرة (قرار المالك §75-5 —
     * كان «دعم» لوحده) أو «ده سداد؟» لو ليه عندك دين (§75-9)، والوارد عن نوعه (§39.1) أو «ده سداد السلفة؟». الأسئلة في
     * `TransferAskSource` والإجابة في `AnswerTransferAsks`. اللي إنت أكدته (حتى «دعم» اتحط لوحده قبل §75-5) ما بيتلمسش.
     */
    suspend fun markPerson(party: TransferPartyRef, personId: Id): Int {
        if (deps.people.listAll().none { it.id == personId }) throw TransferZoneError(uiText(TextKey.TRANSFER_PERSON_NOT_FOUND))
        return decide(party, TransferVerdict.PERSON, personId)
    }

    /** «مش ده» ⇒ ما يتسألش عنه تاني، والعمليات ما بتتغيرش. */
    suspend fun dismiss(party: TransferPartyRef) {
        deps.parties.save(TransferParty(party.key, party.label, party.last4, TransferVerdict.DISMISSED, null, deps.clock.nowIso()))
    }

    /**
     * فك القرار: الطرف بيرجع يتسأل، والعمليات اللي القرار حط نوعها («تحويل داخلي» من «حسابي التاني») بترجع «لسه ما اتحددش»
     * وتتسأل تاني — ما بنخمّنش نوعها القديم. بيرجّع عدد العمليات اللي رجعت.
     * «شخص» بعد §75-5 **ما بيحطش نوع** لوحده — «سلفة» و«دعم» إجابات المالك نفسه ⇒ الفك ما بيرجّعش حاجة (الاتنين بيفضلوا زي بعض).
     * «دعم» اللي كان بيتحط لوحده قبل §75-5 ما بيتفرقش في التخزين عن إجابة المالك ⇒ بيفضل كمان (سؤال مفتوح للمالك).
     */
    suspend fun forget(key: String): Int {
        val party = deps.parties.listAll().firstOrNull { it.key == key } ?: return 0
        val setKind = when (party.verdict) {
            TransferVerdict.OWN_ACCOUNT -> EconomicKind.INTERNAL_TRANSFER
            TransferVerdict.PERSON, TransferVerdict.DISMISSED -> null
        }
        val now = deps.clock.nowIso()
        val reverted = everything().filter { t -> setKind != null && t.economicKind == setKind && transferPartyOf(t)?.key == key }
            .map { it.copy(economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, reviewState = ReviewState.NEEDS_REVIEW, updatedAt = now) }
        deps.uow.run {
            deps.parties.remove(key)
            if (reverted.isNotEmpty()) deps.txns.saveMany(reverted)
        }
        return reverted.size
    }

    private suspend fun decide(party: TransferPartyRef, verdict: TransferVerdict, personId: Id?): Int {
        val now = deps.clock.nowIso()
        val decision = TransferParty(party.key, party.label, party.last4, verdict, personId, now)
        val mine = everything().filter { transferPartyOf(it)?.key == party.key }
        val changed = mine.mapNotNull { t -> applyTransferVerdict(t, decision, now).takeIf { it != t } }
        // «حسابي التاني»: اللي الأسئلة كتبته على **كل** تحويلات الطرف (مش اللي اتغير بس — عشان الإعادة بعد انقطاع تكمّل الشيل)
        val ledger = deps.ledger?.takeIf { verdict == TransferVerdict.OWN_ACCOUNT }
        val stale = ledger?.let { it.askWrites(it.linksOf(mine.map { t -> t.id }).values, withLoans = true) }
        if (stale != null && stale.blocking.isNotEmpty()) throw TransferZoneError(uiText(TextKey.ASK_LOAN_HAS_REPAYMENT))
        deps.uow.run {
            deps.parties.save(decision)
            if (changed.isNotEmpty()) deps.txns.saveMany(changed)
            if (stale != null) ledger?.remove(stale)
        }
        return changed.size
    }

    private companion object {
        const val EARLIEST = "0000-01-01"
        const val LATEST = "9999-12-31"
    }
}
