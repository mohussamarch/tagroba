package app.masroufy.usecase

import app.masroufy.core.AlertKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.LocalMoment
import app.masroufy.core.RecurringItem
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.normalizeText
import app.masroufy.core.paysSubscription
import app.masroufy.core.periodForDate
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.port.RecurringRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * §75-7 (قرار المالك 2026-10-08): **خصم الاشتراك بيتطابق لوحده بالمحل والمبلغ، والميعاد الجاي بيتحرك** — من رسالة أو كشف، ولحاق في دورة
 * الخلفية للي اتسجل قبل كده أو بالإيد. السماحية ±٥٪ و±٧ أيام (اختيار Claude — نفس اقتراح الاشتراكات). «TEST STREAM» اشتراك وهمي.
 */
class SubscriptionChargeTest {
    private val stream = RecurringItem("r-1", "TEST STREAM", "name:" + normalizeText("TEST STREAM"), "subscription", 1, 4_900, Currency.SAR, "2026-10-05", active = true, confirmed = true)

    private fun charge(id: String, date: String, minor: Long = 4_900, shop: String = "TEST STREAM", kind: EconomicKind = EconomicKind.UNCLASSIFIED) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = false,
        observedDirection = Direction.OUT, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", rawMerchantName = shop, walletId = MW_BANK,
    )

    private fun line(date: String, amount: String) = "$date,$amount,0.00,,TEST STREAM,,,شراء"

    private suspend fun nextDue(items: RecurringRepository) = items.listAll().single().nextDueAt

    @Test fun anSmsChargeMovesTheNextDueDate() = runBlocking<Unit> {
        val items = MemoryRecurringRepository(listOf(stream))
        val w = MatchingWorld(effects = listOf(SubscriptionChargeEffect(items)))
        w.receive(matchingPurchaseSms("49", "26/10/04", shop = "TEST STREAM"), at = "2026-10-04T09:00:00Z")
        assertEquals(1, w.recordSms())
        assertEquals("2026-11-05", nextDue(items))
    }

    @Test fun aStatementChargeMovesItToo() = runBlocking<Unit> {
        val items = MemoryRecurringRepository(listOf(stream))
        val w = MatchingWorld(effects = listOf(SubscriptionChargeEffect(items)))
        w.import(w.statement(line("2026-10-04", "49.00")))
        assertEquals("2026-11-05", nextDue(items))
    }

    @Test fun amountAndDateOutsideTheBandDoNotMatch() = runBlocking<Unit> {
        for ((date, amount) in listOf("2026-10-04" to "55.00", "2026-09-20" to "49.00", "2026-10-13" to "49.00", "2026-10-04" to "51.46")) {
            val items = MemoryRecurringRepository(listOf(stream))
            val w = MatchingWorld(effects = listOf(SubscriptionChargeEffect(items)))
            w.import(w.statement(line(date, amount)))
            assertEquals("2026-10-05", nextDue(items), "$date $amount")
        }
        // الحد نفسه جوه: +٥٪ بالظبط (51.45) و٧ أيام بالظبط
        for ((date, amount) in listOf("2026-10-04" to "51.45", "2026-10-12" to "46.55", "2026-09-28" to "49.00")) {
            val items = MemoryRecurringRepository(listOf(stream))
            val w = MatchingWorld(effects = listOf(SubscriptionChargeEffect(items)))
            w.import(w.statement(line(date, amount)))
            assertEquals("2026-11-05", nextDue(items), "$date $amount")
        }
    }

    @Test fun aDuplicateChargeAdvancesOnce() = runBlocking<Unit> {
        val items = MemoryRecurringRepository(listOf(stream))
        // من غير دمج (null): الرسالة والكشف بقوا عمليتين — الاشتراك برضه بيتحرك مرة واحدة
        val w = MatchingWorld(window = null, effects = listOf(SubscriptionChargeEffect(items)))
        w.receive(matchingPurchaseSms("49", "26/10/04", shop = "TEST STREAM"), at = "2026-10-04T09:00:00Z")
        w.recordSms()
        w.import(w.statement(line("2026-10-05", "49.00")))
        assertEquals(2, w.all().size)
        assertEquals("2026-11-05", nextDue(items))
        // وكذا خصم في نفس الدفعة
        val again = MemoryRecurringRepository(listOf(stream))
        val w2 = MatchingWorld(effects = listOf(SubscriptionChargeEffect(again)))
        w2.import(w2.statement(line("2026-10-04", "49.00"), "2026-10-04,49.00,0.00,,TEST STREAM,,,شراء مكرر"))
        assertEquals("2026-11-05", nextDue(again))
    }

    @Test fun inactiveOrUnconfirmedItemsAreUntouched() = runBlocking<Unit> {
        // جنبه اشتراك تاني متأكد وشغال (محل تاني) — عشان الفحص يتعمل على كل اشتراك لوحده مش على القايمة كلها
        val music = RecurringItem("r-2", "TEST MUSIC", "name:" + normalizeText("TEST MUSIC"), "subscription", 1, 2_000, Currency.SAR, "2026-10-05", active = true, confirmed = true)
        for (item in listOf(stream.copy(active = false), stream.copy(confirmed = false))) {
            assertFalse(paysSubscription(item, charge("t", "2026-10-04")))
            val items = MemoryRecurringRepository(listOf(item, music))
            val w = MatchingWorld(effects = listOf(SubscriptionChargeEffect(items)))
            w.import(w.statement(line("2026-10-04", "49.00")))
            assertEquals(listOf(item, music), items.listAll())
            val txns = MemoryTransactionRepository(listOf(charge("t-1", "2026-10-04")))
            assertTrue(MatchSubscriptions(MatchSubscriptionsDeps(items, txns)).catchUp("2026-10-08").isEmpty())
            assertEquals(listOf(item, music), items.listAll())
        }
    }

    @Test fun onlyAPlainPurchaseAtTheSameMerchantPays() {
        assertTrue(paysSubscription(stream, charge("t", "2026-10-04")))
        assertFalse(paysSubscription(stream, charge("t", "2026-10-04", shop = "TEST OTHER")))
        assertFalse(paysSubscription(stream, charge("t", "2026-10-04").copy(observedDirection = Direction.IN)))
        assertFalse(paysSubscription(stream, charge("t", "2026-10-04").copy(currency = Currency.EGP)))
        assertFalse(paysSubscription(stream, charge("t", "2026-10-04").copy(transferToWalletId = "w-cash")))
        assertFalse(paysSubscription(stream, charge("t", "2026-10-04", kind = EconomicKind.LOAN_GRANTED)))
        assertTrue(paysSubscription(stream, charge("t", "2026-10-04", kind = EconomicKind.PURCHASE)))
    }

    @Test fun catchUpAdvancesEarlierChargesAndIsIdempotent() = runBlocking<Unit> {
        val items = MemoryRecurringRepository(listOf(stream.copy(nextDueAt = "2026-08-05")))
        val txns = MemoryTransactionRepository(listOf(charge("t-aug", "2026-08-04"), charge("t-sep", "2026-09-05"), charge("t-oct", "2026-10-03")))
        val match = MatchSubscriptions(MatchSubscriptionsDeps(items, txns))
        assertEquals(listOf("t-aug", "t-sep", "t-oct"), match.catchUp("2026-10-08").map { it.transactionId })
        assertEquals("2026-11-05", nextDue(items))
        assertTrue(match.catchUp("2026-10-08").isEmpty(), "مرة تانية = ولا حاجة")
        assertTrue(match.catchUp("2026-10-20").isEmpty())
        assertEquals("2026-11-05", nextDue(items))
        // كل ٣ شهور: الميعاد بيتحرك دورة كاملة
        val quarterly = MemoryRecurringRepository(listOf(stream.copy(cycleMonths = 3)))
        MatchSubscriptions(MatchSubscriptionsDeps(quarterly, MemoryTransactionRepository(listOf(charge("t", "2026-10-06"))))).catchUp("2026-10-08")
        assertEquals("2027-01-05", nextDue(quarterly))
    }

    @Test fun aChargedItemIsNoLongerOverdueAfterTheBackgroundCycle() = runBlocking<Unit> {
        val items = MemoryRecurringRepository(listOf(stream))
        // اتسجل بالإيد (مفيش أثر وقت التسجيل) — اللحاق في الخلفية هو اللي بيحرّك
        val txns = MemoryTransactionRepository(listOf(charge("t-hand", "2026-10-04")))
        val dues = LoadDues(
            LoadDuesDeps(
                MemoryRoscaRepository(), MemoryRoscaEntryRepository(), MemoryInstallmentPlanRepository(), MemoryInstallmentPaymentRepository(), MemoryDebtTermsRepository(),
                MemoryPersonRepository(), MemoryObligationRepository(), MemorySettlementRepository(), items, txns,
            ),
        )
        val gather = GatherAlerts(GatherAlertsDeps(dues))
        val input = AlertGatherInput("2026-10-08", periodForDate("2026-10-08", 28), Currency.SAR)
        assertTrue(gather.gather(input).any { it.kind == AlertKind.DUE_OVERDUE && "r-1" in it.threadKey }, "قبل اللحاق: «عدّى ميعاده»")

        val result = RunBackgroundCycle(BackgroundCycleDeps(subscriptions = listOf(MatchSubscriptions(MatchSubscriptionsDeps(items, txns))))).run(LocalMoment("2026-10-08", 12))
        assertFalse(result.subscriptionsFailed)
        assertEquals("2026-11-05", nextDue(items))
        assertTrue(gather.gather(input).none { it.kind == AlertKind.DUE_OVERDUE }, "اتدفع ⇒ مش متأخر")
    }

    /** مراجعة S4: التراجع عن الدفعة اللي جابت الخصم بيرجّع الميعاد — الدورة دي ما اتدفعتش تاني. */
    @Test fun revertingTheChargeBatchRestoresTheNextDueDate() = runBlocking<Unit> {
        val items = MemoryRecurringRepository(listOf(stream))
        val w = MatchingWorld(effects = listOf(SubscriptionChargeEffect(items)))
        val batch = w.import(w.statement(line("2026-10-04", "49.00")))
        assertEquals("2026-11-05", nextDue(items))
        w.revert(listOf(SubscriptionChargeUndo(items, w.txns))).execute(batch.id)
        assertEquals(0, w.all().size, "الخصم اتمسح")
        assertEquals("2026-10-05", nextDue(items), "مفيش حاجة دفعت دورة أكتوبر")
        // خصمين لدورتين ورا بعض في نفس الدفعة ⇒ الاتنين بيرجعوا
        val two = MemoryRecurringRepository(listOf(stream))
        val w2 = MatchingWorld(effects = listOf(SubscriptionChargeEffect(two)))
        val both = w2.import(w2.statement(line("2026-10-04", "49.00"), line("2026-11-04", "49.00")))
        assertEquals("2026-12-05", nextDue(two))
        w2.revert(listOf(SubscriptionChargeUndo(two, w2.txns))).execute(both.id)
        assertEquals("2026-10-05", nextDue(two))
    }

    @Test fun revertKeepsTheDateWhenAnotherChargeStillPaysOrTheOwnerMovedIt() = runBlocking<Unit> {
        // من غير دمج: الرسالة دفعت (حرّكت الميعاد) والكشف خصم مكرر ما حرّكش ⇒ التراجع عن الكشف ما بيرجّعش (الرسالة لسه دافعة)
        val items = MemoryRecurringRepository(listOf(stream))
        val w = MatchingWorld(window = null, effects = listOf(SubscriptionChargeEffect(items)))
        w.receive(matchingPurchaseSms("49", "26/10/04", shop = "TEST STREAM"), at = "2026-10-04T09:00:00Z")
        w.recordSms()
        val smsBatch = w.sources.listByTransactionIds(w.all().map { it.id }).single().batchId
        val statement = w.import(w.statement(line("2026-10-05", "49.00")))
        val undo = listOf(SubscriptionChargeUndo(items, w.txns))
        w.revert(undo).execute(statement.id)
        assertEquals("2026-11-05", nextDue(items), "خصم الرسالة لسه دافع دورة أكتوبر")
        // وبعدها التراجع عن الرسالة نفسها ⇒ مفيش حاجة دافعة ⇒ يرجع
        w.revert(undo).execute(smsBatch)
        assertEquals("2026-10-05", nextDue(items))

        // المالك غيّر الميعاد بإيده بعد الخصم ⇒ التراجع ما بيلمسوش
        val moved = MemoryRecurringRepository(listOf(stream))
        val w2 = MatchingWorld(effects = listOf(SubscriptionChargeEffect(moved)))
        val batch = w2.import(w2.statement(line("2026-10-04", "49.00")))
        moved.save(moved.listAll().single().copy(nextDueAt = "2026-11-20"))
        w2.revert(listOf(SubscriptionChargeUndo(moved, w2.txns))).execute(batch.id)
        assertEquals("2026-11-20", nextDue(moved))
    }

    @Test fun aFailingCountryIsReportedAndTheOthersStillCatchUp() = runBlocking<Unit> {
        val broken = object : RecurringRepository {
            override suspend fun listAll(): List<RecurringItem> = throw IllegalStateException("store down")

            override suspend fun save(item: RecurringItem) = Unit
        }
        val items = MemoryRecurringRepository(listOf(stream))
        val txns = MemoryTransactionRepository(listOf(charge("t", "2026-10-04")))
        val lanes = listOf(MatchSubscriptions(MatchSubscriptionsDeps(broken, txns)), MatchSubscriptions(MatchSubscriptionsDeps(items, txns)))
        assertTrue(RunBackgroundCycle(BackgroundCycleDeps(subscriptions = lanes)).run(LocalMoment("2026-10-08", 12)).subscriptionsFailed)
        assertEquals("2026-11-05", nextDue(items))
        // وأثر وقت التسجيل لو وقع ما بيرجّعش الاستيراد (اللحاق بيكمّل بعدين)
        val w = MatchingWorld(effects = listOf(SubscriptionChargeEffect(broken)))
        w.import(w.statement(line("2026-10-04", "49.00")))
        assertEquals(1, w.all().size)
    }
}
