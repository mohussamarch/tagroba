package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.COMPANY_INCOMING_KINDS
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.INCOMING_FROM_PERSON_KINDS
import app.masroufy.core.Id
import app.masroufy.core.PARTY_NOTE_MAX
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.TransferVerdict
import app.masroufy.core.UiKey
import app.masroufy.core.applyTransferVerdict
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransferPartyRepository
import app.masroufy.port.UnitOfWork

/**
 * الردود الجديدة على «مَن هذا؟» (طلب المالك 2026-10-16 — «محتاج ارد باوبشن اكتر من ال3 … وليه لما اقول ليس هذا بتختفي؟»):
 * - **«شركة أو جهة»** ([markCompany]) ⇒ الطرف جهة (مش شخص ولا «متجر جديد») ونوع المبلغ ده من [COMPANY_INCOMING_KINDS].
 * - **«بيحوّلي مستحقات دايمًا»** ([markDues]) ⇒ الطرف شخص + قاعدة: كل وارد من الحساب بيسدّد اللي على الشخص **بنفس طريق «ده سداد؟»**
 *   (`AnswerTransferAsks.answerDebtRepayment`). مفيش حاجة عليه ⇒ ما بنخترعش دين: المبلغ ده بيتسأل نوعه مرة ([DuesStart.choices]).
 *   القاعدة بتتقفل من صفحة الشخص ([stopDues]).
 * - **«حاجة تانية (أكتبها)»** ([markLabel]) ⇒ اسم حر بيتفتكر للحساب وبيتحط على كل وارد منه (`applyTransferVerdict`).
 * - **«تجاهل الحساب ده»** ([ignore]) ⇒ زي «مش ده» القديم، وبيرجع من «حسابات متجاهَلة» ([ignored] · [restore]).
 * («جهة شغلي (راتب)» = `IncomeAnswers.markSalary` زي ما هو.) **كل رد ليه تراجع** ([undo]) بيرجّع القرار والعمليات زي ما كانت بالظبط.
 */
data class TransferPartyAnswersDeps(
    val txns: TransactionRepository,
    val parties: TransferPartyRepository,
    val transfers: ManageTransfers,
    val asks: AnswerTransferAsks,
    val kinds: SetEconomicKind,
    val ledger: PersonLedgerRepos,
    val uow: UnitOfWork,
    val clock: Clock,
    /**
     * A2 (OVERRIDES §98): «مستحقات دايمًا» بيسجّل السداد كربط بالشخص (`PersonRules.dues`) ⇒ «فك الربط» على العملية والمالك لو فكّه ما بيرجعش.
     * null (اختبارات قديمة) ⇒ نفس طريق «ده سداد؟» القديم.
     */
    val rules: PersonRules? = null,
    /** R4/N4: «حاجة تانية» ما بتغيّرش نوع عملية مربوطة بشخص (المفتوحة ⇒ «فك الربط الأول»). null ⇒ مفيش فحص. */
    val personLinks: app.masroufy.port.PersonLinkRepository? = null,
)

/** تراجع عن رد واحد: القرار قبله (null = ما كانش فيه) · العمليات قبله · العمليات اللي اتسجل عليها سداد. */
data class PartyUndo(val key: String, val before: TransferParty?, val transactions: List<Transaction>, val settledIds: List<Id> = emptyList())

/** نتيجة «مستحقات دايمًا»: [settled] = المبلغ ده اتسجل سداد · [choices] غير null = مفيش سداد ⇒ «المبلغ ده إيه؟» مرة. */
data class DuesStart(val undo: PartyUndo, val settled: Boolean, val settledCount: Int, val choices: List<EconomicKind>?)

class TransferPartyAnswerError(message: String) : IllegalArgumentException(message)

class TransferPartyAnswers(private val deps: TransferPartyAnswersDeps) {
    private suspend fun everything(): List<Transaction> = deps.txns.listByDateRange("0000-01-01", "9999-12-31")

    private suspend fun decisionOf(key: String): TransferParty? = deps.parties.listAll().firstOrNull { it.key == key }

    private suspend fun load(id: Id): Transaction =
        deps.txns.findByIds(listOf(id)).firstOrNull() ?: throw TransferPartyAnswerError(uiText(UiKey.EMPLOYER_ERR_TXN))

    private fun decision(party: TransferPartyRef, verdict: TransferVerdict, now: String, kind: EconomicKind? = null, note: String? = null, personId: Id? = null, dues: Boolean = false) =
        TransferParty(party.key, party.label, party.last4, verdict, personId, now, kind = kind, note = note, dues = dues)

    /** «شركة أو جهة»: الجهة بتتفتكر، والمبلغ ده بالنوع المختار (مؤكد). */
    suspend fun markCompany(party: TransferPartyRef, transactionId: Id, kind: EconomicKind): PartyUndo {
        if (kind !in COMPANY_INCOMING_KINDS) throw TransferPartyAnswerError(uiText(UiKey.EMPLOYER_ERR_KIND))
        val before = decisionOf(party.key)
        val t = load(transactionId)
        deps.uow.run {
            deps.parties.save(decision(party, TransferVerdict.COMPANY, deps.clock.nowIso(), kind = kind))
            deps.kinds.setOne(transactionId, kind)
        }
        return PartyUndo(party.key, before, listOf(t))
    }

    /** «حاجة تانية»: [label] بيتفتكر للحساب وبيتحط على المبلغ ده وكل وارد منه نوعه لسه ما اتأكدش (القديم كمان). */
    suspend fun markLabel(party: TransferPartyRef, transactionId: Id, label: String, kind: EconomicKind?): PartyUndo {
        val text = label.trim()
        if (text.isEmpty() || text.length > PARTY_NOTE_MAX) throw TransferPartyAnswerError(uiText(UiKey.TRANSFER_PARTY_LABEL_ERR, PARTY_NOTE_MAX.toString()))
        if (kind != null && kind !in COMPANY_INCOMING_KINDS && kind !in IncomeAnswers.OTHER_KINDS) throw TransferPartyAnswerError(uiText(UiKey.EMPLOYER_ERR_KIND))
        val before = decisionOf(party.key)
        val now = deps.clock.nowIso()
        val d = decision(party, TransferVerdict.LABEL, now, kind = kind, note = text)
        val mine = everything().filter { transferPartyOf(it)?.key == party.key || it.id == transactionId }
        val personLinked = deps.personLinks?.listByTransactionIds(mine.map { it.id })?.map { it.transactionId }?.toSet().orEmpty()
        if (transactionId in personLinked) throw TransferPartyAnswerError(uiText(UiKey.PLINK_ERR_UNLINK_FIRST))
        // المبلغ اللي المالك فاتحه بياخد الاسم حتى لو نوعه كان متأكد (هو اللي اختار دلوقتي)
        val changed = mine.filter { it.id !in personLinked }.mapNotNull { t ->
            val base = if (t.id == transactionId) t.copy(economicKindConfirmed = false) else t
            applyTransferVerdict(base, d, now).takeIf { it != base && it != t }
        }
        deps.uow.run {
            deps.parties.save(d)
            if (changed.isNotEmpty()) deps.txns.saveMany(changed)
        }
        val ids = changed.map { it.id }.toSet()
        return PartyUndo(party.key, before, mine.filter { it.id in ids })
    }

    /**
     * «بيحوّلي مستحقات دايمًا»: الطرف = [personId] + القاعدة، وبعدين كل وارد منه عليه سؤال «ده سداد؟» بيتجاوب «أيوه» (الأقدم الأول).
     * المبلغ ده ما اتسددش بيه حاجة ⇒ [DuesStart.choices] (من غير «تحصيل دين» — ما بنخترعش دين).
     */
    suspend fun markDues(party: TransferPartyRef, transactionId: Id, personId: Id): DuesStart {
        val before = decisionOf(party.key)
        val mine = everything().filter { transferPartyOf(it)?.key == party.key || it.id == transactionId }
        deps.transfers.markPerson(party, personId)
        val saved = decisionOf(party.key) ?: decision(party, TransferVerdict.PERSON, deps.clock.nowIso(), personId = personId)
        deps.parties.save(saved.copy(dues = true))
        val settled = settleAll(mine.filter { it.observedDirection == Direction.IN })
        val undo = PartyUndo(party.key, before, mine, settled)
        val here = transactionId in settled
        return DuesStart(undo, here, settled.size, if (here) null else INCOMING_FROM_PERSON_KINDS - EconomicKind.DEBT_COLLECTED)
    }

    /** «المبلغ ده إيه؟» بعد «مستحقات دايمًا» لما مفيش حاجة تتسدد. */
    suspend fun answerDuesKind(transactionId: Id, kind: EconomicKind) {
        if (kind !in INCOMING_FROM_PERSON_KINDS || kind == EconomicKind.DEBT_COLLECTED) throw TransferPartyAnswerError(uiText(UiKey.EMPLOYER_ERR_KIND))
        deps.kinds.setOne(transactionId, kind)
    }

    /** «تجاهل الحساب ده». */
    suspend fun ignore(party: TransferPartyRef): PartyUndo {
        val before = decisionOf(party.key)
        deps.transfers.dismiss(party)
        return PartyUndo(party.key, before, emptyList())
    }

    /** «تراجع»: السداد اللي الرد سجّله بيتشال · العمليات زي ما كانت · القرار القديم (أو مفيش). */
    suspend fun undo(u: PartyUndo) {
        deps.rules?.let { r -> u.settledIds.forEach { r.undo(it) } }
        val writes = if (u.settledIds.isEmpty() || deps.rules != null) null else deps.ledger.askWrites(deps.ledger.linksOf(u.settledIds).values, withLoans = false)
        deps.uow.run {
            writes?.let { deps.ledger.remove(it) }
            if (u.transactions.isNotEmpty()) deps.txns.saveMany(u.transactions.map { it.copy(updatedAt = deps.clock.nowIso()) })
            val b = u.before
            if (b == null) deps.parties.remove(u.key) else deps.parties.save(b)
        }
    }

    /** «حسابات متجاهَلة» (إعداد رسائل البنك). */
    suspend fun ignored(): List<TransferParty> = deps.parties.listAll().filter { it.verdict == TransferVerdict.DISMISSED }.sortedByDescending { it.decidedAt }

    /** «رجّعه»: الحساب بيتسأل عنه تاني لما يوصل منه تحويل. */
    suspend fun restore(key: String) {
        if (decisionOf(key)?.verdict == TransferVerdict.DISMISSED) deps.transfers.forget(key)
    }

    /** قواعد «مستحقات دايمًا» للشخص (صفحة الشخص). */
    suspend fun duesOf(personId: Id): List<TransferParty> =
        deps.parties.listAll().filter { it.verdict == TransferVerdict.PERSON && it.personId == personId && it.dues }

    /** «وقّف القاعدة»: الطرف بيفضل الشخص ده، والتحويلات الجاية بتتسأل عادي. */
    suspend fun stopDues(key: String) {
        val d = decisionOf(key) ?: return
        if (d.dues) deps.parties.save(d.copy(dues = false))
    }

    /** المبلغ ده من حساب عليه القاعدة وعليه «ده سداد؟» ⇒ بيتسجل سداد. true لو اتسجل. */
    suspend fun applyDues(transactionId: Id): Boolean {
        val t = load(transactionId)
        val key = transferPartyOf(t)?.key ?: return false
        if (decisionOf(key)?.dues != true) return false
        return settleAll(listOf(t)).isNotEmpty()
    }

    /** كل القواعد على كل الوارد (زون التحويلات) — عدد اللي اتسجل سداد. */
    suspend fun sweepDues(): Int {
        val keys = deps.parties.listAll().filter { it.dues && it.verdict == TransferVerdict.PERSON }.map { it.key }.toSet()
        if (keys.isEmpty()) return 0
        return settleAll(everything().filter { it.observedDirection == Direction.IN && transferPartyOf(it)?.key in keys }).size
    }

    private suspend fun settleAll(incoming: List<Transaction>): List<Id> =
        deps.rules?.let { r -> incoming.sortedWith(compareBy({ it.occurredAt }, { it.id })).filter { r.dues(it) }.map { it.id } } ?: incoming
            .sortedWith(compareBy({ it.occurredAt }, { it.id }))
            .mapNotNull { t ->
                val pending = runCatching { deps.asks.askOf(t.id) }.getOrNull() == AskKind.DEBT_REPAYMENT
                if (!pending) return@mapNotNull null
                // الأكبر من المفتوح أو المرفوض ⇒ بيفضل سؤال عادي (ما بنكتبش حاجة مش متأكدين منها)
                runCatching { deps.asks.answerDebtRepayment(t.id, yes = true) }.getOrNull()?.let { t.id }
            }
}
