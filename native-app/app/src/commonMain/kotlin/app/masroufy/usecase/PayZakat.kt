package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.ZakatCategory
import app.masroufy.core.ZakatError
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatPayment
import app.masroufy.core.ZakatYear
import app.masroufy.core.ZakatYearStatus
import app.masroufy.core.assertHalalas
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.uiText
import app.masroufy.core.zakatYearStatus
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.port.ZakatPaymentRepository
import app.masroufy.port.ZakatYearRepository

/**
 * دفع الزكاة (OVERRIDES §62 — طلب المالك): لكل سنة سطور عليها خانة «اتدفع» أو «دفعت الكل»، والدفعة **بتتربط بعملية
 * حقيقية من الكشف** (أو أكتر — دفع على مرات) زي ربط الأقساط. العملية بتاخد تصنيف «زكاة» كـ**مصروف عادي** (§62-ب) ⇒ نوعها
 * «شراء / فاتورة» (اختيار Claude — أقرب نوع لـ«مصروف عادي» من غير أثر على الأشخاص). نفس العملية **ما تتربطش بحاجتين** (`DueLinks`).
 * دفع كاش من غير عملية مسموح (بيتسجل بالمبلغ والتاريخ بس).
 */
data class PayZakatDeps(
    val years: ZakatYearRepository,
    val payments: ZakatPaymentRepository,
    val txns: TransactionRepository,
    val roscaEntries: RoscaEntryRepository,
    val installmentPayments: InstallmentPaymentRepository,
    val plans: InstallmentPlanRepository,
    val categories: CategoryRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
    /** عشان عملية اتسجلت نقطة في حدث ما تبقاش دفعة زكاة كمان (§64). */
    val eventLinks: app.masroufy.port.EventLinkRepository? = null,
    /** رجول التحويل لنفسك (§64) — الرجل ما تبقاش دفعة زكاة. */
    val spaceLegs: app.masroufy.port.SpaceTransferLegs? = null,
)

/** عملية من الكشف + الجزء اللي منها زكاة (null = العملية كلها). */
data class ZakatPaymentSource(val transactionId: Id, val amountMinor: Halalas? = null)

class PayZakat(private val deps: PayZakatDeps) {
    private val links = DueLinks(deps.txns, deps.roscaEntries, deps.installmentPayments, deps.plans, deps.categories, deps.clock, deps.payments, deps.eventLinks, deps.spaceLegs)

    private suspend fun closedYear(yearId: Id): ZakatYear {
        val year = deps.years.listAll().firstOrNull { it.id == yearId } ?: throw ZakatError(uiText(TextKey.ZAKAT_YEAR_NOT_FOUND))
        if (!year.closed) throw ZakatError(uiText(TextKey.ZAKAT_YEAR_NOT_CLOSED))
        return year
    }

    /** السطور المختارة — `null` = «دفعت الكل» (كل سطر عليه مطلوب). */
    private fun chosenLines(year: ZakatYear, lines: List<ZakatLineKind>?): List<ZakatLineKind> {
        if (year.dueMinor == 0L) throw ZakatError(uiText(TextKey.ZAKAT_NOTHING_DUE))
        val payable = year.lines.filter { it.dueMinor > 0 }.map { it.kind }
        val chosen = lines ?: payable
        if (chosen.isEmpty() || chosen.toSet().size != chosen.size || !payable.containsAll(chosen)) throw ZakatError(uiText(TextKey.ZAKAT_LINES_INVALID))
        return ZakatLineKind.entries.filter { it in chosen }
    }

    /** المطلوب · اللي اتدفع · الباقي · «صدقة زيادة» — والسطور بخاناتها. */
    suspend fun status(yearId: Id): ZakatYearStatus {
        val year = closedYear(yearId)
        return zakatYearStatus(year, deps.payments.listByYear(yearId))
    }

    suspend fun payments(yearId: Id): List<ZakatPayment> = deps.payments.listByYear(yearId)

    /** دفعة على سطر أو أكتر من عملية أو أكتر — كل عملية دفعة لوحدها بنفس السطور، والكل بيتكتب مع بعض أو ما يتكتبش. */
    suspend fun pay(yearId: Id, lines: List<ZakatLineKind>?, sources: List<ZakatPaymentSource>): List<ZakatPayment> {
        val year = closedYear(yearId)
        val chosen = chosenLines(year, lines)
        if (sources.isEmpty()) throw ZakatError(uiText(TextKey.ZAKAT_LINES_INVALID))
        if (sources.map { it.transactionId }.toSet().size != sources.size) throw ZakatError(uiText(TextKey.ZAKAT_TXN_TWICE))
        val owner = uiText(TextKey.ZAKAT_PAYMENT_NAME, year.dueAt)
        val created = sources.map { s ->
            val (txn, amount) = links.check(s.transactionId, Direction.OUT, year.currency, owner, s.amountMinor)
            ZakatPayment(deps.ids.next("zakatpay"), year.id, txn.id, amount, chosen, txn.occurredAt, deps.clock.nowIso())
        }
        deps.uow.run {
            deps.payments.saveMany(created)
            for (p in created) links.markKind(p.transactionId!!, EconomicKind.PURCHASE, ZakatCategory.ID, ZakatCategory.defaults())
        }
        return created
    }

    /** دفع كاش من غير عملية في الكشف. */
    suspend fun payCash(yearId: Id, lines: List<ZakatLineKind>?, amountMinor: Halalas, paidAt: IsoDate): ZakatPayment {
        val year = closedYear(yearId)
        val chosen = chosenLines(year, lines)
        assertHalalas(amountMinor)
        if (amountMinor <= 0) throw ZakatError(uiText(TextKey.DUE_AMOUNT_POSITIVE))
        if (!isValidIsoDate(paidAt)) throw ZakatError(uiText(TextKey.ZAKAT_DATE_INVALID))
        val payment = ZakatPayment(deps.ids.next("zakatpay"), year.id, null, amountMinor, chosen, paidAt, deps.clock.nowIso())
        deps.payments.saveMany(listOf(payment))
        return payment
    }

    /** فك دفعة: العملية بترجع «لسه ما اتحددش» وتتسأل تاني (زي فك القسط) — ما بنخمّنش نوعها القديم. */
    suspend fun unlink(yearId: Id, paymentId: Id) {
        val payment = deps.payments.listByYear(yearId).firstOrNull { it.id == paymentId } ?: throw ZakatError(uiText(TextKey.ZAKAT_PAYMENT_NOT_FOUND))
        deps.uow.run {
            deps.payments.deleteMany(listOf(payment.id))
            payment.transactionId?.let { links.clearKind(it) }
        }
    }
}
