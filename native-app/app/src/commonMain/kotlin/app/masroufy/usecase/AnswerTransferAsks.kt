package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.INCOMING_FROM_PERSON_KINDS
import app.masroufy.core.Id
import app.masroufy.core.LEGACY_AUTO_SUPPORT_UNTIL
import app.masroufy.core.ObligationKind
import app.masroufy.core.RepaymentPlan
import app.masroufy.core.ReviewState
import app.masroufy.core.Settlement
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransactionLedgerLinks
import app.masroufy.core.TransferParty
import app.masroufy.core.UiKey
import app.masroufy.core.formatMoney
import app.masroufy.core.isLegacySupportBefore
import app.masroufy.core.legacySupportAsk
import app.masroufy.core.openDebtsFor
import app.masroufy.core.planRepayment
import app.masroufy.core.sumMoney
import app.masroufy.core.transferAskOf
import app.masroufy.core.transferAskRequestId
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
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
 *   (`ManagePeople.settle`) — الديون اللي اتعملت يوم التحويل أو قبله بس (الدين اللي بعده ما بيتسددش بفلوس سابقاه). المبلغ أكبر
 *   من المفتوح ⇒ **مرفوض بنفس رسالة الزيادة ومفيش ولا كتابة**. **لأ** ⇒ مفيش كتابة، والسؤال اللي بعده: الصادر «سلفة ولا دعم؟» ·
 *   الوارد اختيارات §39.1 من غير «تحصيل دين». «لأ» **ما بتتخزنش**: الشاشة بتمرر `debtRuledOut` في نفس الخطوة، ولو المالك قفل
 *   قبل ما يختار، «ده سداد؟» بيرجع المرة الجاية.
 * - العملية اللي المالك ربطها بالدفتر بنفسه (تسوية أو نصيب أو دين منها — §30، في التطبيق القديم أو الجديد) **مالهاش سؤال**.
 * الإجابة ممكن تتعاد بأمان لو اتقطعت في النص (وحدة عمل فايربيز مش ذرّية): الدين والتسوية بمعرّفات ثابتة من العملية
 * (`transferAskRequestId`)، والإجابة التانية بتشيل اللي إجابة مقطوعة مختلفة كتبته.
 * الشاشات مستنية تصميم المالك — دي الدوال اللي هتنادِيها.
 */
data class AnswerTransferAsksDeps(
    val txns: TransactionRepository,
    val parties: TransferPartyRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    /** نصيب الأشخاص من العمليات — العملية اللي ليها نصيب من شاشة الأشخاص اتجاوبت. */
    val allocations: AllocationRepository,
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

    /** «لأ» على الصادر ⇒ اسأل «سلفة ولا دعم؟» (`question(id, debtRuledOut = true)`). */
    object ThenAskLoanOrSupport : DebtAnswer()

    /** «لأ» على الوارد ⇒ اختار نوعه من دول (§39.1) بـ`SetEconomicKind.setOne`. */
    data class ThenChooseIncomingKind(val choices: List<EconomicKind>) : DebtAnswer()

    /** §82-19 «غلطة في المبلغ» ⇒ ولا كتابة: الشاشة تفتح تعديل مبلغ العملية (§32) والسؤال بيرجع بعدها. */
    data class FixAmount(val transaction: Transaction) : DebtAnswer()
}

/**
 * §82-19: «أيوه، سداد» على صادر **أكبر** من المفتوح ⇒ مش رفض وخلاص: «دفعت أكتر من اللي عليك» بالاختيارات (`answerOverpay`). الرسالة
 * نفس رسالة الزيادة القديمة ⇒ اللي ما بيسألش (زي «أيوه للكل») بيعرضها زي ما هي.
 */
class TransferAskOverpaid(message: String, val overpay: app.masroufy.core.Overpay) : TransferAskError(message)

/** إجابة لكذا عملية مرة واحدة: [skipped] = مالهاش السؤال ده دلوقتي · [refused] = اترفضت وسببها (زي الزيادة عن الدين). */
data class TransferAskBulkResult(val answered: List<Id>, val skipped: List<Id>, val refused: Map<Id, String>)

open class TransferAskError(message: String) : IllegalStateException(message)

/** العملية مالهاش السؤال ده دلوقتي (اتجاوب، أو الطرف مش «شخص»، أو اتجاهها غلط، أو المالك ربطها بالدفتر بنفسه). */
class TransferAskNotPending : TransferAskError(uiText(TextKey.ASK_TRANSFER_NOT_PENDING))

class AnswerTransferAsks(private val deps: AnswerTransferAsksDeps) {
    private val ledger = PersonLedgerRepos(deps.obligations, deps.settlements, deps.allocations)

    private class Pending(
        val t: Transaction,
        val party: TransferParty,
        val personId: Id,
        val debts: PersonLedger.Debts,
        val links: TransactionLedgerLinks,
    ) {
        /** §82-13: «دعم» اتسجل لوحده قبل القرار بياخد «سلفة ولا دعم؟» كمان (`legacySupportAsk`) — ولا حاجة بتتغيّر لحد الإجابة. */
        fun ask(debtRuledOut: Boolean = false): AskKind? =
            transferAskOf(t, party, debts.obligations, debts.settlements, debtRuledOut, links, debts.originDates)
                ?: legacySupportAsk(t, party, debts.settlements, links)

        val legacy: Boolean get() = isLegacySupportBefore(t, party) == true

        /** الديون المفتوحة اللي العملية ممكن تسددها — من غير اللي اتعمل بعدها. */
        fun openDebts() = openDebtsFor(t, personId, debts.obligations, debts.settlements, debts.originDates)
    }

    /** العملية وطرفها لو «شخص» — null لو مالهاش طرف شخص. */
    private suspend fun load(txnId: Id): Pending? {
        val t = deps.txns.findByIds(listOf(txnId)).firstOrNull() ?: throw TransferAskError(uiText(TextKey.TXN_NOT_FOUND))
        val key = transferPartyOf(t)?.key ?: return null
        val party = personParties(deps.parties.listAll())[key] ?: return null
        val personId = party.personId ?: return null
        val links = ledger.linksOf(listOf(t.id)).getValue(t.id)
        return Pending(t, party, personId, PersonLedger(deps.obligations, deps.settlements, deps.txns).of(personId), links)
    }

    /** السؤال اللي على العملية دلوقتي (أو null) — نفس اللي بيتعد في «محتاجة تأكيد». [debtRuledOut] = المالك لسه قايل «لأ، مش سداد». */
    suspend fun askOf(txnId: Id, debtRuledOut: Boolean = false): AskKind? = load(txnId)?.ask(debtRuledOut)

    /** السؤال بنصه للشاشة، أو null. بعد «لأ، مش سداد» على الصادر: [debtRuledOut] = true ⇒ «سلفة ولا دعم؟». */
    suspend fun question(txnId: Id, debtRuledOut: Boolean = false): TransferQuestion? {
        val p = load(txnId) ?: return null
        val kind = p.ask(debtRuledOut) ?: return null
        val name = deps.persons.listAll().firstOrNull { it.id == p.personId }?.name ?: p.party.label
        val amount = formatMoney(p.t.amountMinor, p.t.currency)
        if (kind == AskKind.LOAN_OR_SUPPORT) {
            val body = if (p.legacy) uiText(UiKey.ASK_LEGACY_SUPPORT_BODY, amount, name) else uiText(TextKey.ASK_LOAN_OR_SUPPORT_BODY, amount, name)
            return TransferQuestion(kind, p.t, p.personId, uiText(TextKey.ASK_LOAN_OR_SUPPORT), body)
        }
        val open = sumMoney(p.openDebts().map { it.remainingMinor })
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
        val loan = answer == LoanOrSupport.LOAN
        // اللي إجابة مقطوعة كتبته وبطل صح: تسويات «سداد» دايمًا، والسلفة لو الإجابة «دعم» (السلفة بتتكتب تاني بنفس المعرّف)
        val stale = ledger.askWrites(listOf(p.links), withLoans = !loan)
        if (stale.blocking.isNotEmpty()) throw TransferAskError(uiText(TextKey.ASK_LOAN_HAS_REPAYMENT))
        return deps.uow.run {
            ledger.remove(stale)
            // سلفة ⇒ دين ليك على الشخص بمعرّف ثابت من العملية: الإعادة بعد انقطاع بتكتب نفس الدين، مش دين تاني
            if (loan) deps.people.linkToPerson(p.t.id, p.personId, ObligationKind.RECEIVABLE, p.t.amountMinor, requestId = transferAskRequestId(p.t.id))
            confirmKind(p.t, if (loan) EconomicKind.LOAN_GRANTED else EconomicKind.SUPPORT_GIFT)
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
        val debts = p.openDebts()
        val parts = when (val plan = planRepayment(p.t.amountMinor, debts)) {
            // قبل أي كتابة — والصادر الأكبر من المفتوح ⇒ سؤال «دفعت أكتر» (§82-19)
            is RepaymentPlan.Refused -> throw overpayOf(p, debts)?.let { TransferAskOverpaid(plan.reason, it) } ?: TransferAskError(plan.reason)
            is RepaymentPlan.Settle -> plan.parts
        }
        val request = transferAskRequestId(p.t.id)
        // نفس معرّف `ManagePeople.settle` — التسوية المقطوعة اللي زي الخطة بالظبط بتفضل، واللي مختلفة (أو سلفة مقطوعة) بتتشال
        val planned = parts.associate { (obligation, amount) -> "stl-${obligation.id}-$request" to amount }
        val stale = ledger.askWrites(listOf(p.links), withLoans = true) { planned[it.id] != it.amountMinor }
        if (stale.blocking.isNotEmpty()) throw TransferAskError(uiText(TextKey.ASK_LOAN_HAS_REPAYMENT))
        val kind = if (p.t.observedDirection == Direction.IN) EconomicKind.DEBT_COLLECTED else EconomicKind.DEBT_REPAID
        return deps.uow.run {
            ledger.remove(stale)
            val written = parts.map { (obligation, amount) -> deps.people.settle(obligation.id, p.personId, amount, p.t.id, request) }
            DebtAnswer.Recorded(confirmKind(p.t, kind), written)
        }
    }

    /** الصادر أكبر من المفتوح مع الشخص (الديون اللي التحويل ممكن يسددها) ⇒ الزيادة، وإلا null. */
    private fun overpayOf(p: Pending, debts: List<app.masroufy.core.OpenDebt>): app.masroufy.core.Overpay? =
        if (p.t.observedDirection != Direction.OUT) null else app.masroufy.core.overpayOf(p.t.amountMinor, sumMoney(debts.map { it.remainingMinor }))

    /**
     * §82-19 — رد «دفعت أكتر من اللي عليك» على صادر لشخص أكبر من ديونك المفتوحة ليه: الديون بتتقفل كلها (**الأقدم الأول**، نفس معرّفات
     * «أيوه، سداد» الثابتة) والعملية «سداد دين عليك» مؤكد، والزيادة نصيب الشخص من العملية بنفس طريق شاشة الأشخاص (`linkToPerson` بمعرّف
     * السؤال الثابت ⇒ الإعادة بعد انقطاع بتكتب نفس المستندات): «أمانة ليك عنده» ⇒ دين ليك معلّم أمانة · «سلفة جديدة ليه» ⇒ دين ليك ·
     * «دعم» ⇒ نصيب «هدية» من غير دين، و**بيتحسب مصروف** في شهر العملية زي أي هدية (§88 — `givenAwayShareOf`؛ السداد نفسه مش مصروف) ·
     * «غلطة في المبلغ» ⇒ [DebtAnswer.FixAmount] من غير ولا كتابة.
     */
    suspend fun answerOverpay(txnId: Id, choice: app.masroufy.core.OverpayChoice): DebtAnswer {
        val p = load(txnId) ?: throw TransferAskNotPending()
        if (p.ask() != AskKind.DEBT_REPAYMENT) throw TransferAskNotPending()
        val debts = p.openDebts()
        val over = overpayOf(p, debts) ?: throw TransferAskError(uiText(app.masroufy.core.UiKey.OVERPAY_NOT_OVER))
        if (choice == app.masroufy.core.OverpayChoice.WRONG_AMOUNT) return DebtAnswer.FixAmount(p.t)
        val request = transferAskRequestId(p.t.id)
        val planned = debts.associate { "stl-${it.obligation.id}-$request" to it.remainingMinor }
        val stale = ledger.askWrites(listOf(p.links), withLoans = true) { planned[it.id] != it.amountMinor }
        if (stale.blocking.isNotEmpty()) throw TransferAskError(uiText(TextKey.ASK_LOAN_HAS_REPAYMENT))
        return deps.uow.run {
            ledger.remove(stale)
            val written = debts.map { deps.people.settle(it.obligation.id, p.personId, it.remainingMinor, p.t.id, request) }
            deps.people.linkToPerson(
                p.t.id, p.personId, ObligationKind.RECEIVABLE, over.surplusMinor, asGift = choice == app.masroufy.core.OverpayChoice.SUPPORT,
                requestId = request, custodyHeld = choice == app.masroufy.core.OverpayChoice.HELD_FOR_YOU,
                countedAsSpend = choice == app.masroufy.core.OverpayChoice.SUPPORT,
            )
            DebtAnswer.Recorded(confirmKind(p.t, EconomicKind.DEBT_REPAID), written)
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
        // §82-13: الإجابة لازم تبان «بعد القرار» حتى لو ساعة الجوال متأخرة — وإلا «دعم» اللي المالك أكّده يرجع يتسأل (`legacySupportAsk`)
        val now = maxOf(deps.clock.nowIso(), LEGACY_AUTO_SUPPORT_UNTIL)
        deps.txns.update(t.id, TransactionPatch(economicKind = kind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = now))
        return t.copy(economicKind = kind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = now)
    }
}
