package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.AlertDelivery
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.KindStats
import app.masroufy.core.LocalMoment
import app.masroufy.core.Period
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.ZakatYear
import app.masroufy.core.digestNotice
import app.masroufy.core.isLockSafe
import app.masroufy.core.muted
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInbox
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAlertReceipts
import app.masroufy.memory.MemoryAlertSettings
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryUsualHours
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.memory.MemoryZakatYearRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** محرك التنبيهات من البيانات للصفحة (OVERRIDES §61) على مستودعات الذاكرة — كل الأسامي والمبالغ مخترعة. */
class AlertEngineFlowTest {
    private var seq = 0
    private val planName = "تمويل الوهم"

    private fun txn(date: String, dir: Direction = Direction.OUT, amount: Long = 10_000, desc: String? = null, op: String? = null) = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = desc, sourceOperationType = op,
    )

    // 5 تحويلات صادرة لنفس الطرف في سبتمبر ⇒ سؤال في «زون التحويلات» (الاسم بحروف العرض المقلوبة زي كشف الراجحي)
    private val transfers = (1..5).map { txn("2026-09-0$it", desc = "ﻲﻣﺎﺳW-/TOACCT/12345678901234567TO:ملاحظة", op = "عملية تحويل داخلية") }
    private val payTxn = txn("2026-10-11", amount = 50_000)
    private val txns = MemoryTransactionRepository(transfers + payTxn)
    private val plan = InstallmentPlan("ip-1", planName, "جهة وهمية", InstallmentKind.PURCHASE_PLAN, Currency.SAR, 500_000, 500_000, 50_000, 1, "2026-10-10")
    private val payments = MemoryInstallmentPaymentRepository()
    private val parties = MemoryTransferPartyRepository()
    private val clock = FixedClock("2026-10-01T10:00:00.000Z")

    private val dues = LoadDues(
        LoadDuesDeps(
            MemoryRoscaRepository(), MemoryRoscaEntryRepository(), MemoryInstallmentPlanRepository(listOf(plan)), payments, MemoryDebtTermsRepository(),
            MemoryPersonRepository(), MemoryObligationRepository(), MemorySettlementRepository(), MemoryRecurringRepository(), txns,
        ),
    )
    private val zakatYears = MemoryZakatYearRepository(listOf(ZakatYear("2026-10-15", "2025-10-26", "2026-10-15", Currency.SAR, "x")))
    private val gather = GatherAlerts(
        GatherAlertsDeps(
            dues,
            ManageTransfers(ManageTransfersDeps(txns, parties, MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي"))), MemoryUnitOfWork(listOf(txns, parties)), clock)),
            zakatYears, MemoryZakatPaymentRepository(), MemoryProfileRepository(),
        ),
    )

    private val settings = MemoryAlertSettings()
    private val interactions = MemoryAlertInteractions()
    private val hours = MemoryUsualHours()
    private val inbox = MemoryAlertInbox()
    private val engine = RunAlertEngine(AlertEngineDeps(settings, interactions, hours, MemoryAlertReceipts(), inbox, clock))
    private val period = Period("2026-09", "2026-09-28", "2026-10-27", 30)

    private suspend fun candidates(day: String, profile: Int? = null) = gather.gather(AlertGatherInput(day, period, Currency.SAR, profileCompletionPercent = profile))

    private suspend fun runDay(day: String, hour: Int = 14) = engine.run(candidates(day), LocalMoment(day, hour))

    private fun AlertRun.dueKeys() = posts.flatMap { it.eventKeys }.filter { it.startsWith("due|") }

    private suspend fun dueEntries() = inbox.listAll().filter { it.kind.group == AlertGroup.DUES }

    @Test fun installmentEscalatesSoonTodayOverdueOncePerStep() = runBlocking<Unit> {
        assertEquals(emptyList(), runDay("2026-10-06").dueKeys(), "4 أيام قبل ⇒ لسه")
        val soon = runDay("2026-10-07")
        assertEquals(1, soon.dueKeys().size)
        assertEquals(listOf(AlertKind.DUE_SOON), dueEntries().map { it.kind })
        assertEquals(emptyList(), runDay("2026-10-08").dueKeys(), "نفس الدرجة ما بتتبعتش تاني")
        assertEquals(1, runDay("2026-10-10").dueKeys().size)
        assertEquals(listOf(AlertKind.DUE_TODAY), dueEntries().map { it.kind }, "النهارده حل محل قرّب — سطر واحد للموضوع")
        val overdue = runDay("2026-10-11")
        assertEquals(1, overdue.dueKeys().size)
        assertEquals(listOf(AlertKind.DUE_OVERDUE), overdue.inAppWindows.filter { it.kind.group == AlertGroup.DUES }.map { it.kind }, "المتأخر: نافذة كمان")
        assertEquals(emptyList(), runDay("2026-10-12").dueKeys())
        assertEquals(listOf(AlertKind.DUE_OVERDUE), dueEntries().map { it.kind })
    }

    @Test fun alertDisappearsWhenTheInstallmentIsPaid() = runBlocking<Unit> {
        runDay("2026-10-11")
        assertEquals(1, dueEntries().size)
        payments.saveMany(listOf(InstallmentPayment("pay-1", "ip-1", payTxn.id, 50_000)))
        val after = runDay("2026-10-12")
        assertTrue(dueEntries().isEmpty(), "القسط اتربط بدفعة ⇒ التنبيه اختفى")
        assertTrue(after.resolved.single().startsWith("due|installment|ip-1|2026-10-10"))
    }

    @Test fun sameAlertIsNeverSentTwiceEvenIfGatheredTwice() = runBlocking<Unit> {
        val list = candidates("2026-10-10")
        val first = engine.run(list + list, LocalMoment("2026-10-10", 9))
        val keys = first.posts.flatMap { it.eventKeys }
        assertEquals(keys.toSet().size, keys.size)
        assertTrue(keys.isNotEmpty())
        assertEquals(emptyList(), engine.run(list, LocalMoment("2026-10-10", 20)).posts)
    }

    @Test fun groupTurnedOffStaysOnThePageOnlyAndIsNeverReEnabled() = runBlocking<Unit> {
        runDay("2026-10-07")
        assertTrue(dueEntries().isNotEmpty())
        engine.setGroupEnabled(AlertGroup.DUES, false)
        val statsBefore = interactions.load()[AlertKind.DUE_TODAY]
        for ((day, kind) in listOf("2026-10-08" to AlertKind.DUE_SOON, "2026-10-10" to AlertKind.DUE_TODAY, "2026-10-11" to AlertKind.DUE_OVERDUE, "2026-10-15" to AlertKind.DUE_OVERDUE)) {
            val run = runDay(day)
            assertEquals(emptyList(), run.dueKeys(), "$day: مفيش شريط للمجموعة المقفولة")
            assertTrue(run.inAppWindows.none { it.kind.group == AlertGroup.DUES }, "$day: ولا نافذة")
            assertEquals(listOf(kind), dueEntries().map { it.kind }, "$day: السطر في الصفحة (رد المالك §61) — سطر واحد للموضوع")
        }
        val view = engine.inbox().single { it.entry.kind.group == AlertGroup.DUES }
        assertTrue(view.muted, "الصفحة تقدر تقول إنها مقفولة")
        assertTrue(view.reason.contains(uiText(TextKey.ALERT_FACTOR_GROUP_OFF)), view.reason)
        assertEquals(statsBefore, interactions.load()[AlertKind.DUE_TODAY], "المقفول ما بيتعدّش «اتعرض»")
        assertEquals(setOf(AlertGroup.DUES), settings.disabledGroups(), "المحرك ما فتحهاش")
        assertTrue(inbox.listAll().any { it.kind.group == AlertGroup.ZAKAT && !it.decision.muted }, "باقي المجموعات شغالة")
        // اتفتحت تاني ⇒ الدرجة اللي ما اتبعتتش (المتأخر) بتتبعت عادي — مفيش إيصال كان اتكتب وهي مقفولة
        engine.setGroupEnabled(AlertGroup.DUES, true)
        val back = runDay("2026-10-16")
        assertEquals(1, back.dueKeys().size)
        assertFalse(engine.inbox().single { it.entry.kind.group == AlertGroup.DUES }.muted)
        // اتحل وهي مقفولة ⇒ بيختفي برضه
        engine.setGroupEnabled(AlertGroup.DUES, false)
        payments.saveMany(listOf(InstallmentPayment("pay-1", "ip-1", payTxn.id, 50_000)))
        runDay("2026-10-17")
        assertTrue(dueEntries().isEmpty())
    }

    @Test fun ignoredTypeGoesToTheDigestWhileAnOpenedTypeStaysImmediate() = runBlocking<Unit> {
        interactions.save(AlertKind.DUE_SOON, KindStats(shown = 10, opened = 0))
        interactions.save(AlertKind.ZAKAT_SOON, KindStats(shown = 10, opened = 9))
        val run = runDay("2026-10-07")
        val digest = run.posts.single { it.notice == digestNotice() }
        assertTrue(digest.eventKeys.any { it.startsWith("due|") }, "القسط اللي بتتجاهله اتجمع في الملخص")
        val zakatPost = run.posts.single { p -> p.eventKeys.any { it.startsWith("zakat|") } }
        assertEquals(null, zakatPost.at, "الزكاة اللي بتفتحها اتبعتت على طول")
        val dueEntry = dueEntries().single()
        assertEquals(AlertDelivery.DIGEST, dueEntry.decision.delivery)
        engine.opened(dueEntry.threadKey)
        engine.opened(dueEntry.threadKey)
        assertEquals(KindStats(11, 1), interactions.load()[AlertKind.DUE_SOON], "اتعرض مرة زيادة واتفتح مرة واحدة بس")
    }

    @Test fun usualHoursLearnedFromAppOpensDelayTheMediumAlert() = runBlocking<Unit> {
        repeat(3) { engine.appOpened(LocalMoment("2026-10-01", 21)) }
        repeat(3) { engine.appOpened(LocalMoment("2026-10-02", 8)) }
        val run = runDay("2026-10-07", hour = 14)
        val duePost = run.posts.single { p -> p.eventKeys.any { it.startsWith("due|") } }
        assertEquals(LocalMoment("2026-10-07", 21), duePost.at)
        val reason = engine.inbox().single { it.entry.kind == AlertKind.DUE_SOON }.reason
        assertTrue(reason.contains("21"), reason)
    }

    @Test fun inboxCarriesReasonsAndTheBarNeverCarriesDetails() = runBlocking<Unit> {
        val run = engine.run(candidates("2026-10-10", profile = 40), LocalMoment("2026-10-10", 14))
        val views = engine.inbox()
        val kinds = views.map { it.entry.kind }.toSet()
        assertTrue(setOf(AlertKind.DUE_TODAY, AlertKind.TRANSFER_QUESTION, AlertKind.ZAKAT_SOON, AlertKind.PROFILE_INCOMPLETE).all { it in kinds }, "$kinds")
        for (v in views) {
            assertTrue(v.reason.isNotBlank() && v.group.isNotBlank(), v.entry.threadKey)
        }
        assertTrue(views.single { it.entry.kind == AlertKind.DUE_TODAY }.entry.title.contains(planName))
        for (p in run.posts) {
            assertTrue(isLockSafe(p.notice.title) && isLockSafe(p.notice.body), p.notice.toString())
            assertFalse(p.notice.body.contains(planName) || p.notice.body.contains("سامي"), p.notice.toString())
        }
        assertTrue(run.posts.none { p -> p.eventKeys.any { it.startsWith("profile|") } }, "كارت الملف في الصفحة بس")
    }

    @Test fun serverOnlyKindsAreNeitherGatheredNorSent() = runBlocking<Unit> {
        val gathered = candidates("2026-10-11", profile = 10)
        assertTrue(gathered.none { it.kind.needsServer })
        val fake = AlertCandidate(AlertKind.NEW_DEVICE_LOGIN, "login|x", "x", "x")
        val run = engine.run(gathered + fake, LocalMoment("2026-10-11", 14))
        assertTrue(run.posts.none { "login|x|new_device_login" in it.eventKeys })
        assertTrue(inbox.listAll().none { it.kind.needsServer })
    }

    @Test fun answeredTransferQuestionDisappears() = runBlocking<Unit> {
        runDay("2026-10-07")
        assertEquals(1, inbox.listAll().count { it.kind == AlertKind.TRANSFER_QUESTION })
        val m = ManageTransfers(ManageTransfersDeps(txns, parties, MemoryPersonRepository(), MemoryUnitOfWork(listOf(txns, parties)), clock))
        m.dismiss(m.zone().questions.single().party)
        runDay("2026-10-08")
        assertEquals(0, inbox.listAll().count { it.kind == AlertKind.TRANSFER_QUESTION })
    }
}
