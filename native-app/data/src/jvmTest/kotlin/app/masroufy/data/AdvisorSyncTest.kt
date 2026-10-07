package app.masroufy.data

import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertGroupSetting
import app.masroufy.core.CalendarItemType
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.LocalMoment
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.buildPeriod
import app.masroufy.core.emptyProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryOccasionRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryPrepItemRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryProjectRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryReservationRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUsualHours
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.port.AlertInboxEntry
import app.masroufy.port.AlertInboxStore
import app.masroufy.port.AlertReceiptStore
import app.masroufy.port.AlertSettingsStore
import app.masroufy.usecase.AdvisorSignals
import app.masroufy.usecase.AdvisorSignalsDeps
import app.masroufy.usecase.AlertEngineDeps
import app.masroufy.usecase.AlertGatherInput
import app.masroufy.usecase.LoadCalendar
import app.masroufy.usecase.LoadCalendarDeps
import app.masroufy.usecase.LoadDues
import app.masroufy.usecase.LoadDuesDeps
import app.masroufy.usecase.LoadLeftover
import app.masroufy.usecase.LoadLeftoverDeps
import app.masroufy.usecase.ManageReservations
import app.masroufy.usecase.RunAlertEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * المساعد المالي على جوالين (OVERRIDES §68 فوق §61 جلسة 18): المساعد بيقرا **نفس الإيصالات المتزامنة** اللي المحرك بيكتبها ⇒
 * التنبيه اللي اتبعت على جوال ما يتبعتش على التاني، و«النقص كبر 25%» بيتحسب من أكبر نقص اتبعت **من أي جوال**.
 * «السحابة» مستندات في الذاكرة بالمحوّلات نفسها (زي `AlertSyncTest`). كل الأسماء والمبالغ مخترعة.
 */
class AdvisorSyncTest {
    private class Cloud {
        val docs = HashMap<String, LinkedHashMap<String, Map<String, Any?>>>()

        fun <T> save(codec: DocCodec<T>, value: T) {
            val group = docs.getOrPut(codec.group) { LinkedHashMap() }
            val id = codec.id(value)
            group[id] = (group[id].orEmpty() - codec.omittedFields(value)) + codec.toStore(value)
        }

        fun <T : Any> list(codec: DocCodec<T>): List<T> = docs[codec.group].orEmpty().values.mapNotNull { codec.skippingUnreadable().decode(it) }
    }

    private class CloudSettings(private val cloud: Cloud) : AlertSettingsStore {
        override suspend fun disabledGroups() = cloud.list(AlertCodecs.alertSettings).filter { !it.enabled }.map { it.group }.toSet()

        override suspend fun setGroupEnabled(group: AlertGroup, enabled: Boolean) = cloud.save(AlertCodecs.alertSettings, AlertGroupSetting(group, enabled))
    }

    private class CloudReceipts(private val cloud: Cloud) : AlertReceiptStore {
        override suspend fun listAll() = cloud.list(AlertCodecs.alertReceipts)

        override suspend fun saveMany(receipts: List<NotificationReceipt>) = receipts.forEach { cloud.save(AlertCodecs.alertReceipts, it) }
    }

    private class CloudInbox(private val cloud: Cloud) : AlertInboxStore {
        override suspend fun listAll() = cloud.list(AlertCodecs.alertInbox)

        override suspend fun save(entry: AlertInboxEntry) = cloud.save(AlertCodecs.alertInbox, entry)

        override suspend fun remove(threadKeys: List<String>) {
            cloud.docs[AlertCodecs.alertInbox.group]?.keys?.removeAll(threadKeys.map(::receiptDocId).toSet())
        }
    }

    // بيانات الحساب (على فايربيز بتتزامن هي كمان): بنك فيه 4,100 وحدثين جايين قبل الراتب
    private val today = "2026-10-04"
    private val period = buildPeriod(2026, 9, 28)
    private val txns = MemoryTransactionRepository()
    private val wallets = MemoryWalletRepository(listOf(Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 410_000, "2026-01-01")))
    private val reservations = MemoryReservationRepository()
    private val profile = MemoryProfileRepository(emptyProfile().copy(payday = 28))
    private val incomes = MemoryIncomeSourceRepository(listOf(IncomeSource("s-1", "شركة وهمية", "n", IncomeSourceKind.JOB, Currency.SAR, "2025-01-01")))
    private val people = MemoryPersonRepository()
    private val calendar = LoadCalendar(
        LoadCalendarDeps(
            LoadDues(
                LoadDuesDeps(
                    MemoryRoscaRepository(), MemoryRoscaEntryRepository(), MemoryInstallmentPlanRepository(), MemoryInstallmentPaymentRepository(),
                    MemoryDebtTermsRepository(), people, MemoryObligationRepository(), MemorySettlementRepository(), MemoryRecurringRepository(), txns,
                ),
            ),
            MemoryProjectRepository(),
            MemoryLifeEventRepository(
                listOf(
                    LifeEvent("ev-1", "فرح صاحب وهمي", "x", LifeEventKind.WEDDING, "2026-10-15", mine = false, createdAt = "c"),
                    LifeEvent("ev-2", "سفر وهمي", "y", LifeEventKind.OTHER, "2026-10-20", mine = false, createdAt = "c"),
                ),
            ),
            MemoryPrepItemRepository(), MemoryOccasionRepository(), people, profile, reservations, "SA", Currency.SAR, incomeSources = incomes,
        ),
    )
    private val clock = FixedClock("2026-10-04T09:00:00.000Z")
    private val cloud = Cloud()

    /** جوال: المتزامن من السحابة (الإيصالات للمحرك **وللمساعد**)، والتعلّم في ذاكرته هو. */
    private inner class Phone {
        val engine = RunAlertEngine(AlertEngineDeps(CloudSettings(cloud), MemoryAlertInteractions(), MemoryUsualHours(), CloudReceipts(cloud), CloudInbox(cloud), clock))
        val advisor = AdvisorSignals(
            AdvisorSignalsDeps(
                txns, MemoryAllocationRepository(), MemoryCategoryRepository(), profile,
                leftover = LoadLeftover(LoadLeftoverDeps(calendar, wallets, txns, reservations, profile, Currency.SAR, incomes)),
                receipts = CloudReceipts(cloud),
            ),
        )

        suspend fun run(hour: Int) = engine.run(advisor.alertCandidates(AlertGatherInput(today, period, Currency.SAR)), LocalMoment(today, hour))
    }

    private suspend fun spend(id: String, amount: Long) = txns.saveMany(
        listOf(
            Transaction(
                id = id, occurredAt = today, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
                observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
                reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-bank",
            ),
        ),
    )

    @Test
    fun advisorAlertSentOnPhoneAIsNotResentOnPhoneB() = runBlocking<Unit> {
        val a = Phone()
        val b = Phone()
        val counting = ManageReservations(calendar, reservations, clock)
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-1", "2026-10-15", today, 300_000)
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-2", "2026-10-20", today, 220_000)

        assertEquals(1, a.run(9).posts.size, "الجوال الأول بعت «حجوزاتك أكثر مما معك» (ينقصك 1,100)")
        assertTrue(b.run(10).posts.isEmpty(), "الجوال التاني شاف الإيصال المتزامن ⇒ ما بيبعتش")

        spend("t-1", 27_499) // النقص 1,374.99 — أقل من +25%
        assertTrue(b.run(11).posts.isEmpty(), "زاد أقل من 25% عن اللي اتبعت من الجوال الأول")
        spend("t-2", 1) // 1,375.00 = +25%
        assertEquals(1, b.run(12).posts.size, "كبر بوضوح ⇒ الجوال التاني بعته")
        assertTrue(a.run(13).posts.isEmpty(), "والأول ما بيعيدوش")
        val line = CloudInbox(cloud).listAll().single()
        assertTrue(line.body.endsWith("ينقصك 1,375.00 ر.س"), "سطر واحد في الصفحة على الجوالين بالنقص الجديد")
        assertEquals(2, cloud.list(AlertCodecs.alertReceipts).size, "إيصالين بس في السحابة: النقص الأول والنقص اللي كبر")
    }
}
