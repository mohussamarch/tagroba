package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * مكافأة نهاية الخدمة (رد المالك §64-٧): **السؤال اتشال** من قفل الشغل، وبقت اختيار على الإيداع نفسه من الشركة المؤكدة —
 * نوع جديد `end_of_service` (اختيار Claude) **مؤكد**، فـ«مرتب لوحده» عمره ما يكتب فوقه. أسماء ومبالغ مخترعة.
 */
class EndOfServiceTest {
    private var seq = 0
    private val star = "شركة النجمة الوهمية"
    private val moon = "شركة القمر الوهمية"

    private fun deposit(company: String, date: String, amount: Long = 1_000_000, dir: Direction = Direction.IN) = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = "PAYROLL-PA1234:ملاحظة | سامي-INMAINM1234567RJ-$company | x", sourceOperationType = "حواالت سريع الواردة",
    )

    private val txns = MemoryTransactionRepository()
    private val sources = MemoryIncomeSourceRepository()
    private val profiles = MemoryProfileRepository()
    private val clock = FixedClock("2026-10-05T10:00:00.000Z")
    private val manage = ManageIncomeSources(
        ManageIncomeSourcesDeps(sources, ManageProfile(ManageProfileDeps(profiles, app.masroufy.memory.MemoryAccount(), clock, sources)), MemoryUnitOfWork(listOf(sources)), app.masroufy.memory.SequentialIdGenerator(), clock),
    )
    private val signals = IncomeSourceSignals(
        IncomeSignalsDeps(sources, txns, MemoryAllocationRepository(), profiles, MemoryUnitOfWork(listOf(sources, txns)), clock, MemoryTransferPartyRepository()),
    )

    private suspend fun kind(id: String) = txns.findByIds(listOf(id)).single().let { Triple(it.economicKind, it.economicKindConfirmed, it.reviewState) }

    /** وظيفة في «النجمة» اتأكد إنها بتحوّل المرتب، واتقفلت آخر سبتمبر. */
    private suspend fun confirmedCompanyThatEnded(): Pair<Transaction, Transaction> {
        val job = manage.add(IncomeSourceInput("مصدر وهمي", "2026-01-01", expectedDayOfMonth = 27))
        val salary = deposit(star, "2026-01-27")
        val benefit = deposit(star, "2026-10-03", 9_000_000)
        txns.saveMany(listOf(salary))
        signals.answerPayer(signals.payerQuestions().single(), yes = true)
        manage.close(job.id, "2026-09-30")
        // المكافأة بتيجي بعد القفل ⇒ «مرتب» لوحده (رد المالك §64-١) لحد ما المستخدم يعلّمها
        txns.saveMany(listOf(benefit))
        assertEquals(1, signals.applyKnownPayers())
        assertEquals(Triple(EconomicKind.SALARY, true, ReviewState.CONFIRMED), kind(benefit.id))
        return salary to benefit
    }

    @Test fun closingAJobAsksNothingAndADepositFromTheCompanyCanBeMarkedEndOfService() = runBlocking<Unit> {
        val (_, benefit) = confirmedCompanyThatEnded()
        signals.markEndOfService(benefit.id)
        assertEquals(Triple(EconomicKind.END_OF_SERVICE, true, ReviewState.CONFIRMED), kind(benefit.id))
        // «مرتب لوحده» عمره ما يكتب فوق اختيار المستخدم
        assertEquals(0, signals.applyKnownPayers())
        assertEquals(EconomicKind.END_OF_SERVICE, kind(benefit.id).first)
        assertEquals(uiText(TextKey.KIND_END_OF_SERVICE), app.masroufy.core.ruleFor(kind(benefit.id).first).label)
        // الرجوع: بيرجع «مرتب» مؤكد (قاعدة الشركة المؤكدة)
        signals.markEndOfService(benefit.id, yes = false)
        assertEquals(Triple(EconomicKind.SALARY, true, ReviewState.CONFIRMED), kind(benefit.id))
        assertFailsWith<IncomeSourceError>("مش متعلّم") { signals.markEndOfService(benefit.id, yes = false) }
    }

    @Test fun onlyAnIncomingDepositFromAConfirmedCompanyCanBeMarked() = runBlocking<Unit> {
        confirmedCompanyThatEnded()
        val other = deposit(moon, "2026-10-03")
        val out = deposit(star, "2026-10-04", dir = Direction.OUT)
        txns.saveMany(listOf(other, out))
        val notCompany = assertFailsWith<IncomeSourceError> { signals.markEndOfService(other.id) }
        assertEquals(uiText(TextKey.INCOME_EOS_NOT_FROM_COMPANY), notCompany.message)
        assertEquals(uiText(TextKey.INCOME_EOS_NEEDS_IN), assertFailsWith<IncomeSourceError> { signals.markEndOfService(out.id) }.message)
        assertEquals(uiText(TextKey.INCOME_EOS_TXN_NOT_FOUND), assertFailsWith<IncomeSourceError> { signals.markEndOfService("مش موجودة") }.message)
        assertEquals(EconomicKind.UNCLASSIFIED to EconomicKind.UNCLASSIFIED, kind(other.id).first to kind(out.id).first, "ولا حاجة اتغيرت")
    }

    @Test fun aDeclinedPayerIsNotAConfirmedCompany() = runBlocking<Unit> {
        manage.add(IncomeSourceInput("مصدر وهمي", "2026-01-01"))
        val first = deposit(star, "2026-01-27")
        txns.saveMany(listOf(first))
        signals.answerPayer(signals.payerQuestions().single(), yes = false)
        assertFailsWith<IncomeSourceError> { signals.markEndOfService(first.id) }
    }

    @Test fun theGeneralKindPickerRejectsEndOfServiceOnOutgoingMoney() = runBlocking<Unit> {
        val out = deposit(star, "2026-10-04", dir = Direction.OUT)
        val incoming = deposit(moon, "2026-10-04")
        txns.saveMany(listOf(out, incoming))
        val set = SetEconomicKind(SetEconomicKindDeps(txns, MemoryCategoryRepository(), MemoryUnitOfWork(listOf(txns)), clock))
        assertFailsWith<IllegalStateException> { set.setOne(out.id, EconomicKind.END_OF_SERVICE) }
        set.setOne(incoming.id, EconomicKind.END_OF_SERVICE)
        assertEquals(EconomicKind.END_OF_SERVICE to true, kind(incoming.id).let { it.first to it.second })
    }
}
