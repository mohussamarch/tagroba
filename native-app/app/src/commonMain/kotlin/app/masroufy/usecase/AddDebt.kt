package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DebtTerms
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.PersonMeaning
import app.masroufy.core.R06Key
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.formatMoney
import app.masroufy.core.isPurchaseShaped
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.shiftDays
import app.masroufy.core.shiftMonths
import app.masroufy.core.uiText
import app.masroufy.port.ObligationRepository

/**
 * «ضيف دين» (OVERRIDES §112-2 — الديون الأساسية في «المستحقات»، التصميم `AddDebtSheet.dc.html` من `8575ee4b`). حالة استخدام بتجمع
 * الموجود بس — مفيش كتابة جديدة في الدفتر من هنا:
 * - الشخص: موجود أو جديد ([ManagePeople.addPerson]).
 * - الفلوس اتحركت إزاي: عملية متسجلة ([ManagePersonLinks.link] بمعنى «سلّفته» أو «استلفت منه») · كاش ([PersonAccounts.addOperation] على
 *   محفظة الكاش) · دين قديم من غير حركة فلوس ([ManagePeople.addOpeningDebt] — §27).
 * - الميعاد (اختياري): [ManageInstallments.setDebtTerms] — دفعة واحدة بالمبلغ كله.
 * - السبب (اختياري): على الدين نفسه ([Obligation.note]) وعلى عملية الكاش.
 * أي خطوة بعد الأولى تفشل ⇒ اللي اتكتب قبلها بيترجع (فك الربط · مسح عملية الكاش · مسح الدين القديم · أرشفة الشخص الجديد).
 */
enum class DebtSideIn { FOR_YOU, ON_YOU }

sealed interface DebtWho {
    data class Existing(val personId: Id) : DebtWho
    data class New(val name: String) : DebtWho
}

sealed interface DebtMoney {
    data class Operation(val transactionId: Id) : DebtMoney
    data object Cash : DebtMoney
    data object Old : DebtMoney
}

enum class DebtDueChoice { NONE, WEEK, MONTH, PICK }

data class NewDebt(
    val side: DebtSideIn,
    val who: DebtWho,
    val amountMinor: Halalas,
    val reason: String?,
    val due: DebtDueChoice,
    /** على [DebtDueChoice.PICK] بس. */
    val pickedDate: IsoDate?,
    val money: DebtMoney,
    val today: IsoDate,
    /** عملية البلد الشغالة (الدين القديم بيتخزن بيها). */
    val currency: Currency,
)

data class AddedDebt(val obligation: Obligation, val personId: Id, val personName: String, val dueAt: IsoDate?, val transactionId: Id?)

class AddDebtError(message: String) : IllegalStateException(message)

const val DEBT_REASON_MAX = 80

class AddDebt(
    private val people: ManagePeople,
    private val links: ManagePersonLinks,
    private val installments: ManageInstallments,
    private val obligations: ObligationRepository,
) {
    /** «عملية متسجلة»: أحدث العمليات في الاتجاه الصح (ليك ⇒ خارجة · عليك ⇒ داخلة) ومش مربوطة بحد ولا حركة بين فلوسك. أول [limit]. */
    suspend fun candidates(side: DebtSideIn, limit: Int = 30): List<Transaction> {
        val dir = directionOf(side)
        val everything = links.everything()
        val bases = ownLegBases(everything)
        val fit = everything.filter { it.observedDirection == dir && !it.id.endsWith(OWN_LEG_SUFFIX) && !ownMoneyMove(it, it.id in bases) }
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.sourceOrder })
            .take(limit * 3)
        val linked = links.linkedAmong(fit.map { it.id })
        return fit.filter { it.id !in linked }.take(limit)
    }

    /** محفظة الكاش في البلد (null ⇒ «كاش» مقفول). */
    suspend fun cashWallet(): Wallet? = links.accounts.wallets().firstOrNull { it.kind == "cash" }

    /** الميعاد من الاختيار — null = من غير ميعاد. */
    fun dueDateOf(due: DebtDueChoice, today: IsoDate, picked: IsoDate?): IsoDate? = when (due) {
        DebtDueChoice.NONE -> null
        DebtDueChoice.WEEK -> shiftDays(today, 7)
        DebtDueChoice.MONTH -> shiftMonths(today, 1)
        DebtDueChoice.PICK -> picked
    }

    suspend fun add(input: NewDebt): AddedDebt {
        // الفحص كله قبل أي كتابة
        if (input.amountMinor <= 0 || input.amountMinor > MAX_SAFE_HALALAS) throw AddDebtError(uiText(TextKey.AMOUNT_POSITIVE))
        val reason = input.reason?.trim()?.takeIf { it.isNotEmpty() }
        if (reason != null && reason.length > DEBT_REASON_MAX) throw AddDebtError(uiText(R06Key.AD_ERR_REASON_LONG, DEBT_REASON_MAX.toString()))
        val dueAt = dueDateOf(input.due, input.today, input.pickedDate)
        if (input.due == DebtDueChoice.PICK && (dueAt == null || !isValidIsoDate(dueAt) || dueAt < input.today)) throw AddDebtError(uiText(R06Key.AD_ERR_DATE))
        val meaning = if (input.side == DebtSideIn.FOR_YOU) PersonMeaning.LENT else PersonMeaning.BORROWED
        val cash = if (input.money == DebtMoney.Cash) cashWallet() ?: throw AddDebtError(uiText(R06Key.AD_ERR_NO_CASH)) else null
        (input.money as? DebtMoney.Operation)?.let { checkOperation(it.transactionId, input) }
        if (input.who is DebtWho.New && input.who.name.isBlank()) throw AddDebtError(uiText(R06Key.AD_ERR_PERSON))

        val (personId, personName, created) = when (val w = input.who) {
            is DebtWho.Existing -> {
                val p = people.listWithBalances().firstOrNull { it.person.id == w.personId }?.person ?: throw AddDebtError(uiText(TextKey.PERSON_NOT_FOUND))
                Triple(p.id, p.name, false)
            }
            is DebtWho.New -> people.addPerson(w.name).let { Triple(it.id, it.name, true) }
        }
        var obligation: Obligation? = null
        var txnId: Id? = null
        try {
            when (val m = input.money) {
                is DebtMoney.Operation -> {
                    val out = links.link(m.transactionId, personId, meaning, amountMinor = input.amountMinor)
                    txnId = m.transactionId
                    obligation = obligationOf(personId, out.link.obligationId)
                }
                DebtMoney.Cash -> {
                    val out = links.accounts.addOperation(personId, meaning, input.amountMinor, input.today, cash!!.id, reason)
                    txnId = out.link.transactionId
                    obligation = obligationOf(personId, out.link.obligationId)
                }
                DebtMoney.Old -> {
                    val kind = if (input.side == DebtSideIn.FOR_YOU) ObligationKind.RECEIVABLE else ObligationKind.LOAN_PAYABLE
                    obligation = people.addOpeningDebt(personId, kind, input.amountMinor, input.currency)
                }
            }
            var saved = obligation!!
            val cashOrigin = input.money == DebtMoney.Cash
            if (reason != null || cashOrigin) {
                saved = saved.copy(note = reason ?: saved.note, cashOrigin = cashOrigin || saved.cashOrigin)
                obligations.saveMany(listOf(saved))
            }
            if (dueAt != null) installments.setDebtTerms(DebtTerms(saved.id, personId, firstDueAt = dueAt))
            return AddedDebt(saved, personId, personName, dueAt, txnId)
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            undo(input.money, txnId, obligation, personId, created)
            throw e
        }
    }

    private suspend fun checkOperation(transactionId: Id, input: NewDebt) {
        val t = links.everything().firstOrNull { it.id == transactionId } ?: throw AddDebtError(uiText(TextKey.TXN_NOT_FOUND))
        if (t.observedDirection != directionOf(input.side)) throw AddDebtError(uiText(R06Key.AD_ERR_OP_DIRECTION))
        // الربط بيكتب الدين بمبلغ العملية كله (غير نصيب في شراء) ⇒ المبلغ المكتوب لازم يساويه — مقارنة مش حساب
        if (!isPurchaseShaped(t) && t.amountMinor != input.amountMinor) throw AddDebtError(uiText(R06Key.AD_ERR_OP_AMOUNT, formatMoney(t.amountMinor)))
    }

    private suspend fun obligationOf(personId: Id, id: Id?): Obligation =
        obligations.listByPerson(personId).firstOrNull { it.id == id } ?: throw AddDebtError(uiText(TextKey.DEBT_NOT_FOUND))

    private suspend fun undo(money: DebtMoney, txnId: Id?, obligation: Obligation?, personId: Id, createdPerson: Boolean) {
        runCatching {
            when (money) {
                is DebtMoney.Operation -> if (txnId != null) links.unlink(txnId, remember = false)
                DebtMoney.Cash -> if (txnId != null) links.accounts.undoAdd(txnId)
                DebtMoney.Old -> if (obligation != null) obligations.deleteMany(listOf(obligation.id))
            }
        }
        if (createdPerson) runCatching { people.archivePerson(personId, archived = true) }
    }

    private fun directionOf(side: DebtSideIn) = if (side == DebtSideIn.FOR_YOU) Direction.OUT else Direction.IN
}
