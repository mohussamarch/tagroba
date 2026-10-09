package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.INCOMING_FROM_PERSON_KINDS
import app.masroufy.core.Id
import app.masroufy.core.ObligationKind
import app.masroufy.core.RepaymentPlan
import app.masroufy.core.ReviewState
import app.masroufy.core.Settlement
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.formatMoney
import app.masroufy.core.openDebtsFor
import app.masroufy.core.planRepayment
import app.masroufy.core.sumMoney
import app.masroufy.core.transferAskOf
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransferPartyRepository
import app.masroufy.port.UnitOfWork
import kotlin.coroutines.cancellation.CancellationException

/**
 * الإجابة على أسئلة التحويل مع شخص مربوط (قرارات المالك §75-5 و§75-9 — الأسئلة نفسها في `core/TransferAsks.kt`):
 * - «سلفة ولا دعم؟»: **سلفة** ⇒ «قرض ممنوح» مؤكد + دين ليك على الشخص بالمبلغ (نفس طريق شاشة الأشخاص `linkToPerson`) ·
 *   **دعم** ⇒ «دعم / هدية» مؤكد (مصروف — زي ما كان بيتحط لوحده قبل §75-5).
 * - «ده سداد؟»: **أيوه** ⇒ «تحصيل دين» (الوارد) أو «سداد دين عليك» (الصادر) مؤكد + تسوية على الديون المفتوحة **بالأقدم الأول**
 *   (`ManagePeople.settle`). المبلغ أكبر من المفتوح ⇒ **مرفوض بنفس رسالة الزيادة ومفيش ولا كتابة**. **لأ** ⇒ مفيش كتابة، والسؤال
 *   اللي بعده: الصادر «سلفة ولا دعم؟» · الوارد اختيارات §39.1 من غير «تحصيل دين».
 * الإجابة ممكن تتعاد بأمان لو اتقطعت في النص: الدين ما بيتعملش مرتين، والتسوية بمعرّف ثابت من العملية.
 * الشاشات مستنية تصميم المالك — دي الدوال اللي هتنادِيها.
 */
data class AnswerTransferAsksDeps(
    val txns: TransactionRepository,
    val parties: TransferPartyRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    /** اسم الشخص في نص السؤال. */
    val persons: PersonRepository,
    /** السلفة والسداد من نفس طريق شاشة الأشخاص (نفس الفحوص ونفس الأشكال المتخزنة اللي التطبيق القديم بيقراها). */
    val people: ManagePeople,
    val uow: UnitOfWork,
    val clock: Clock,
)

enum class LoanOrSupport { LOAN, SUPPORT }

/** السؤال زي ما الشاشة هتعرضه. [openDebtMinor] = المفتوح مع الشخص بعملة العملية (لسؤال السداد بس). */
data class TransferQuestion(
    val kind: AskKind,
    val transaction: Transaction,
    val personId: Id,
    val title: String,
    val body: String,
    val openDebtMinor: Halalas? = null,
)

/** نتيجة «ده سداد؟». */
sealed class DebtAnswer {
    /** «أيوه»: العملية بعد التأكيد والتسويات اللي اتكتبت. */
    data class Recorded(val transaction: Transaction, val settlements: List<Settlement>) : DebtAnswer()

    /** «لأ» على الصادر ⇒ اسأل «سلفة ولا دعم؟». */
    object ThenAskLoanOrSupport : DebtAnswer()

    /** «لأ» على الوارد ⇒ اختار نوعه من دول (§39.1) بـ`SetEconomicKind.setOne`. */
    data class ThenChooseIncomingKind(val choices: List<EconomicKind>) : DebtAnswer()
}

/** إجابة لكذا عملية مرة واحدة: [skipped] = مالهاش السؤال ده دلوقتي · [refused] = اترفضت وسببها (زي الزيادة عن الدين). */
data class TransferAskBulkResult(val answered: List<Id>, val skipped: List<Id>, val refused: Map<Id, String>)

open class TransferAskError(message: String) : IllegalStateException(message)

/** العملية مالهاش السؤال ده دلوقتي (اتجاوب، أو الطرف مش «شخص»، أو اتجاهها غلط). */
class TransferAskNotPending : TransferAskError(uiText(TextKey.ASK_TRANSFER_NOT_PENDING))

class AnswerTransferAsks(private val deps: AnswerTransferAsksDeps) {
    private class Pending(val t: Transaction, val party: TransferParty, val personId: Id, val debts: PersonLedger.Debts) {
        fun ask(debtRuledOut: Boolean = false): AskKind? = transferAskOf(t, party, debts.obligations, debts.settlements, debtRuledOut)
    }

    /** العملية وطرفها لو «شخص» — null لو مالهاش طرف شخص. */
    private suspend fun load(txnId: Id): Pending? {
        val t = deps.txns.findByIds(listOf(txnId)).firstOrNull() ?: throw TransferAskError(uiText(TextKey.TXN_NOT_FOUND))
        val key = transferPartyOf(t)?.key ?: return null
        val party = personParties(deps.parties.listAll())[key] ?: return null
        val personId = party.personId ?: return null
        return Pending(t, party, personId, PersonLedger(deps.obligations, deps.settlements).of(personId))
    }

    /** السؤال اللي على العملية دلوقتي (أو null) — نفس اللي بيتعد في «محتاجة تأكيد». */
    suspend fun askOf(txnId: Id): AskKind? = load(txnId)?.ask()

    /** السؤال بنصه للشاشة، أو null. */
    suspend fun question(txnId: Id): TransferQuestion? {
        val p = load(txnId) ?: return null
        val kind = p.ask() ?: return null
        val name = deps.persons.listAll().firstOrNull { it.id == p.personId }?.name ?: p.party.label
        val amount = formatMoney(p.t.amountMinor, p.t.currency)
        if (kind == AskKind.LOAN_OR_SUPPORT) {
            return TransferQuestion(kind, p.t, p.personId, uiText(TextKey.ASK_LOAN_OR_SUPPORT), uiText(TextKey.ASK_LOAN_OR_SUPPORT_BODY, amount, name))
        }
        val open = sumMoney(openDebtsFor(p.t, p.personId, p.debts.obligations, p.debts.settlements).map { it.remainingMinor })
        val incoming = p.t.observedDirection == Direction.IN
        val title = if (incoming) TextKey.ASK_DEBT_COLLECTED else TextKey.ASK_DEBT_REPAID
        val body = if (incoming) TextKey.ASK_DEBT_COLLECTED_BODY else TextKey.ASK_DEBT_REPAID_BODY
        return TransferQuestion(kind, p.t, p.personId, uiText(title), uiText(body, amount, name, formatMoney(open, p.t.currency)), open)
    }

    /**
     * «سلفة ولا دعم؟» — على الصادر لشخص اللي نوعه لسه ما اتأكدش (حتى لو عليه سؤال «سداد؟»: المالك ممكن يجاوب «لأ» ويختار على طول).
     * بترجّع العملية بعد التأكيد.
     */
    suspend fun answerLoanOrSupport(txnId: Id, answer: LoanOrSupport): Transaction {
        val p = load(txnId) ?: throw TransferAskNotPending()
        if (p.ask(debtRuledOut = true) != AskKind.LOAN_OR_SUPPORT) throw TransferAskNotPending()
        val kind = if (answer == LoanOrSupport.LOAN) EconomicKind.LOAN_GRANTED else EconomicKind.SUPPORT_GIFT
        return deps.uow.run {
            // سلفة ⇒ دين ليك على الشخص — مرة واحدة بس للعملية (إعادة الإجابة بعد انقطاع ما بتعملش دين تاني)
            if (answer == LoanOrSupport.LOAN &&
                deps.obligations.listByTransactionIds(listOf(p.t.id)).none { it.personId == p.personId && it.kind == ObligationKind.RECEIVABLE }
            ) {
                deps.people.linkToPerson(p.t.id, p.personId, ObligationKind.RECEIVABLE, p.t.amountMinor)
            }
            confirmKind(p.t, kind)
        }
    }

    /** «ده سداد السلفة؟» / «ده سداد دين عليك؟». */
    suspend fun answerDebtRepayment(txnId: Id, yes: Boolean): DebtAnswer {
        val p = load(txnId) ?: throw TransferAskNotPending()
        if (p.ask() != AskKind.DEBT_REPAYMENT) throw TransferAskNotPending()
        if (!yes) {
            return if (p.t.observedDirection == Direction.OUT) DebtAnswer.ThenAskLoanOrSupport
            else DebtAnswer.ThenChooseIncomingKind(INCOMING_FROM_PERSON_KINDS - EconomicKind.DEBT_COLLECTED)
        }
        val origins = p.debts.obligations.mapNotNull { it.originTransactionId }
        val dates = if (origins.isEmpty()) emptyMap() else deps.txns.findByIds(origins).associate { it.id to it.occurredAt }
        val parts = when (val plan = planRepayment(p.t.amountMinor, openDebtsFor(p.t, p.personId, p.debts.obligations, p.debts.settlements, dates))) {
            is RepaymentPlan.Refused -> throw TransferAskError(plan.reason) // قبل أي كتابة
            is RepaymentPlan.Settle -> plan.parts
        }
        val kind = if (p.t.observedDirection == Direction.IN) EconomicKind.DEBT_COLLECTED else EconomicKind.DEBT_REPAID
        return deps.uow.run {
            val written = parts.map { (obligation, amount) -> deps.people.settle(obligation.id, p.personId, amount, p.t.id, requestIdOf(p.t.id)) }
            DebtAnswer.Recorded(confirmKind(p.t, kind), written)
        }
    }

    /** «سلفة» أو «دعم» لكذا تحويل مرة واحدة (زي تحويلات قديمة اتسألت كلها لما الطرف اتربط بشخص). */
    suspend fun answerLoanOrSupportAll(txnIds: List<Id>, answer: LoanOrSupport): TransferAskBulkResult =
        bulk(txnIds) { answerLoanOrSupport(it, answer) }

    /** «أيوه، سداد» لكذا عملية — **الأقدم الأول**، عشان أقدم سداد يقفل أقدم دين. اللي يزيد عن الباقي بيترفض لوحده. */
    suspend fun confirmRepaymentAll(txnIds: List<Id>): TransferAskBulkResult {
        val ordered = deps.txns.findByIds(txnIds.distinct()).sortedWith(compareBy({ it.occurredAt }, { it.sourceOrder }, { it.id })).map { it.id }
        return bulk(ordered + (txnIds.distinct() - ordered.toSet())) { answerDebtRepayment(it, true) }
    }

    private suspend fun bulk(txnIds: List<Id>, one: suspend (Id) -> Unit): TransferAskBulkResult {
        val answered = mutableListOf<Id>()
        val skipped = mutableListOf<Id>()
        val refused = LinkedHashMap<Id, String>()
        for (id in txnIds.distinct()) {
            try {
                one(id)
                answered += id
            } catch (e: CancellationException) {
                throw e
            } catch (_: TransferAskNotPending) {
                skipped += id
            } catch (e: IllegalStateException) {
                refused[id] = e.message.orEmpty()
            }
        }
        return TransferAskBulkResult(answered, skipped, refused)
    }

    private suspend fun confirmKind(t: Transaction, kind: EconomicKind): Transaction {
        val now = deps.clock.nowIso()
        deps.txns.update(t.id, TransactionPatch(economicKind = kind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = now))
        return t.copy(economicKind = kind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = now)
    }

    /**
     * معرّف طلب ثابت من العملية (`stl-<الدين>-<ده>`) — نفس الإجابة مرتين = نفس التسوية. المعرّف اللي فيه حروف مش مسموحة في معرّف
     * الطلب (أو طويل) بياخد بصمة ثابتة منه بدل ما الحروف تتبدل — عشان عمليتين مختلفتين عمرهم ما ياخدوا نفس التسوية.
     */
    private fun requestIdOf(txnId: Id): String =
        if (SAFE_TXN_ID.matches(txnId)) "ask-$txnId" else "ask-h" + fingerprint(txnId) + "-" + txnId.length

    private companion object {
        val SAFE_TXN_ID = Regex("^[A-Za-z0-9_-]{1,90}$")

        /** FNV-1a 64 على حروف المعرّف — بصمة مش فلوس. */
        fun fingerprint(text: String): String {
            var hash = -0x340d631b7bdddcdbL
            for (c in text) {
                hash = hash xor c.code.toLong()
                hash *= 0x100000001b3L
            }
            return hash.toULong().toString(16)
        }
    }
}
