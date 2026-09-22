package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonAllocation
import app.masroufy.core.PersonBalance
import app.masroufy.core.Settlement
import app.masroufy.core.computePersonBalance
import app.masroufy.core.formatMoney
import app.masroufy.core.jsTrim
import app.masroufy.core.remainingOfObligation
import app.masroufy.port.AllocationRepository
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SettlementWriter
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * ManagePeople — نقل `managePeople.ts`: الأشخاص والديون والأمانات.
 * منطق الحساب كله في `core/Ledger.kt` — الملف ده بيربطه بالتخزين وما بيعيدش حساب.
 * spec/02: «اعرض منفصلين: لك عنده وله عندك… **لا تقاص تلقائي** بين لك وعليك».
 */

data class PersonObligationRow(val obligation: Obligation, val remainingMinor: Halalas)

data class PersonRow(
    val person: Person,
    val balance: PersonBalance,
    val obligations: List<PersonObligationRow>,
)

data class ManagePeopleDeps(
    val people: PersonRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val settlementWriter: SettlementWriter,
    val allocations: AllocationRepository,
    val txns: TransactionRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
)

data class LinkResult(val obligation: Obligation?, val allocation: PersonAllocation)

private val REQUEST_ID = Regex("^[a-zA-Z0-9_-]{1,100}$")

class ManagePeople(private val deps: ManagePeopleDeps) {
    /** كل الأشخاص بأرصدتهم — القراية محدودة بعدد الأشخاص مش بالعمليات. */
    suspend fun listWithBalances(): List<PersonRow> {
        val rows = deps.people.listAll().map { person ->
            val obligations = deps.obligations.listByPerson(person.id)
            val settlements = deps.settlements.listByObligations(obligations.map { it.id })
            PersonRow(
                person = person,
                balance = computePersonBalance(person.id, obligations, settlements),
                obligations = obligations
                    .map { PersonObligationRow(it, remainingOfObligation(it, settlements)) }
                    // المسدَّد بالكامل ما بيتعرضش في القايمة النشطة
                    .filter { it.remainingMinor > 0 },
            )
        }

        // الأكتر رصيدًا الأول، والمؤرشف آخر واحد
        fun total(r: PersonRow) = r.balance.receivableMinor + r.balance.payableLoanMinor + r.balance.payableCustodyMinor
        return rows.sortedWith(compareBy<PersonRow> { it.person.archived }.thenByDescending { total(it) })
    }

    suspend fun addPerson(name: String): Person {
        val trimmed = jsTrim(name)
        if (trimmed.isEmpty()) throw IllegalStateException("اكتب اسم الشخص")
        // spec/04: حد اسم الشخص 80 حرف، معلن ومتحقَّق منه
        if (trimmed.length > 80) throw IllegalStateException("الاسم أطول من 80 حرف")

        if (deps.people.listAll().any { jsTrim(it.name) == trimmed }) {
            throw IllegalStateException("فيه شخص اسمه «$trimmed» موجود قبل كده")
        }

        val person = Person(id = deps.ids.next("person"), name = trimmed, archived = false)
        deps.people.save(person)
        return person
    }

    /** أرشفة — **مفيش حذف** (spec/03: «لا حذف للحساب ذي سجل»). */
    suspend fun archivePerson(personId: Id, archived: Boolean) {
        val person = deps.people.listAll().find { it.id == personId } ?: throw IllegalStateException("الشخص ده مش موجود")
        deps.people.save(person.copy(archived = archived))
    }

    /**
     * بينشئ التزامًا من عملية: دفعت عن حد أو سلّفت أو استلمت قرض/أمانة.
     * spec/02: «مجموع التخصيصات للشخص **لا يتجاوز قيمة الشراء**»،
     * والهدية تخصيص بلا التزام (spec/06).
     */
    suspend fun linkToPerson(
        transactionId: Id,
        personId: Id,
        kind: ObligationKind,
        amountMinor: Halalas,
        asGift: Boolean = false,
    ): LinkResult {
        if (amountMinor <= 0) throw IllegalStateException("المبلغ لازم يكون أكبر من صفر")

        val transaction = deps.txns.findByIds(listOf(transactionId)).firstOrNull()
            ?: throw IllegalStateException("العملية دي مش موجودة")

        val already = deps.allocations.listByTransactionIds(listOf(transactionId)).fold(0L) { sum, a -> sum + a.amountMinor }
        if (already + amountMinor > transaction.amountMinor) {
            throw IllegalStateException(
                "مجموع التخصيصات (${formatMoney(already + amountMinor)}) أكبر من قيمة العملية (${formatMoney(transaction.amountMinor)})",
            )
        }

        return deps.uow.run {
            val allocation = PersonAllocation(
                id = deps.ids.next("alloc"),
                transactionId = transactionId,
                personId = personId,
                allocationKind = if (asGift) AllocationKind.GIFT else AllocationKind.RECEIVABLE,
                amountMinor = amountMinor,
                currency = transaction.currency,
            )
            deps.allocations.saveMany(listOf(allocation))

            if (asGift) return@run LinkResult(obligation = null, allocation = allocation)

            val obligation = Obligation(
                id = deps.ids.next("obl"),
                personId = personId,
                originTransactionId = transactionId,
                kind = kind,
                originalMinor = amountMinor,
                currency = transaction.currency,
            )
            deps.obligations.saveMany(listOf(obligation))
            LinkResult(obligation = obligation, allocation = allocation)
        }
    }

    /** بيسوّي التزامًا — الزيادة بتترفض بتفسير، ونفس معرّف الطلب مرتين = نفس التسوية. */
    suspend fun settle(
        obligationId: Id,
        personId: Id,
        amountMinor: Halalas,
        transactionId: Id? = null,
        requestId: String? = null,
    ): Settlement {
        if (requestId != null && !REQUEST_ID.matches(requestId)) throw IllegalStateException("معرّف طلب التسوية غير سليم")
        return deps.settlementWriter.settle(
            Settlement(
                id = if (requestId != null) "stl-$obligationId-$requestId" else deps.ids.next("stl"),
                transactionId = transactionId ?: "",
                obligationId = obligationId,
                amountMinor = amountMinor,
            ),
            personId,
        )
    }

    /**
     * دين قديم من غير عملية — OVERRIDES §27. الالتزام بـ`originTransactionId = null`
     * وبيتسدد عادي، و**مفيش عملية وهمية**: مش بيدخل المصروف ولا الدخل ولا رصيد الكاش.
     */
    suspend fun addOpeningDebt(personId: Id, kind: ObligationKind, amountMinor: Halalas): Obligation {
        if (amountMinor <= 0 || amountMinor > MAX_SAFE_HALALAS) throw IllegalStateException("المبلغ لازم يكون أكبر من صفر")
        if (deps.people.listAll().none { it.id == personId }) throw IllegalStateException("الشخص ده مش موجود")
        val obligation = Obligation(
            id = deps.ids.next("obl"),
            personId = personId,
            originTransactionId = null,
            kind = kind,
            originalMinor = amountMinor,
            currency = Currency.SAR,
        )
        deps.obligations.saveMany(listOf(obligation))
        return obligation
    }
}
