package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.ParsedRow
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsRow
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OVERRIDES §112-3: التحويل لحسابك التاني ⇒ رِجل الداخل تتسجل لوحدها، ورسالة البنك التاني لما توصل بتندمج فيها (±يوم)، و«معك الآن»
 * ما بيقلش. كل الأسامي والأرقام مخترعة · المبالغ بالوحدة الصغرى.
 */
class OwnTransferLegEffectTest {
    private val a = Wallet("w-a", "بنك وهمي أ", Currency.SAR, "bank", 100_000, "2026-01-01", accountLast4 = "1111")
    private val b = Wallet("w-b", "بنك وهمي ب", Currency.SAR, "bank", 20_000, "2026-01-01", accountLast4 = "2222")
    private val outBody = "حوالة بين حساباتك\nمن:1111\nالى:2222\nمبلغ:SAR 500.00"
    private val inBody = "حوالة واردة\nالى:2222\nمبلغ:SAR 500.00"

    private fun txn(id: String, wallet: String, dir: Direction, day: String, kind: EconomicKind, confirmed: Boolean) = Transaction(
        id = id, occurredAt = day, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = dir, amountMinor = 50_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = if (confirmed) ReviewState.CONFIRMED else ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = wallet,
    )

    private fun ctx(wallet: Wallet, t: Transaction, body: String, kind: SmsKind, ref: String): RecordContext {
        val row = ParsedRow(1, t.occurredAt, t.amountMinor, t.observedDirection, "", ref, "SMS", body, body)
        val sms = SmsRow(1, t.occurredAt, t.amountMinor, t.observedDirection, "", ref, "SMS", body, body, kind, wallet.accountLast4)
        val request = ImportRequest(fileName = "sms", content = body, accountIdentity = wallet.name, sourceType = ImportSourceType.SMS, walletId = wallet.id, parsedRows = listOf(row))
        val line = ImportPreviewLine(row = row, state = MatchingState.NEW, reason = "", categoryReason = "", selectedByDefault = true)
        val ids = object : app.masroufy.port.IdGenerator {
            var n = 0
            override fun next(prefix: String) = "$prefix-$ref-${n++}"
        }
        return RecordContext(request, "batch-$ref", "2026-10-08T00:00:00Z", false, emptyMap(), mutableListOf(RecordedLine(line, sms, t)), mutableListOf(), ids)
    }

    private suspend fun recordOut(txns: MemoryTransactionRepository, effect: OwnTransferLegEffect, day: String = "2026-10-07") {
        val out = txn("t-out", a.id, Direction.OUT, day, EconomicKind.INTERNAL_TRANSFER, true)
        val c = ctx(a, out, outBody, SmsKind.OWN_TRANSFER, "r-out")
        effect.prepare(c)
        txns.saveMany(c.lines.map { it.transaction } + c.extra.map { it.first })
    }

    private suspend fun recordIn(txns: MemoryTransactionRepository, effect: OwnTransferLegEffect, day: String) {
        val inc = txn("t-in", b.id, Direction.IN, day, EconomicKind.UNCLASSIFIED, false)
        val c = ctx(b, inc, inBody, SmsKind.OTHER, "r-in")
        effect.prepare(c)
        txns.saveMany(c.lines.map { it.transaction } + c.extra.map { it.first })
    }

    private suspend fun all(txns: MemoryTransactionRepository) = txns.listByDateRange("0000-01-01", "9999-12-31")

    @Test fun anOutLegAloneGetsAnAutomaticInLegOnTheOtherAccount() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        recordOut(txns, OwnTransferLegEffect(MemoryWalletRepository(listOf(a, b)), txns))
        val leg = all(txns).single { it.walletId == b.id }
        assertEquals("t-out$OWN_LEG_SUFFIX", leg.id, "تلقائية ومربوطة بالصادر")
        assertEquals(Triple(Direction.IN, 50_000L, EconomicKind.INTERNAL_TRANSFER), Triple(leg.observedDirection, leg.amountMinor, leg.economicKind))
        assertTrue(leg.economicKindConfirmed)
    }

    @Test fun outThenTheOtherBanksMessageNextDayIsOnePair() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val effect = OwnTransferLegEffect(MemoryWalletRepository(listOf(a, b)), txns)
        recordOut(txns, effect)
        recordIn(txns, effect, "2026-10-08")
        val list = all(txns)
        assertEquals(2, list.size, "رجلين بس — رسالة البنك التاني اندمجت في التلقائية")
        val inLeg = list.single { it.walletId == b.id }
        assertEquals("t-in", inLeg.id, "الرسالة الحقيقية هي اللي فضلت")
        assertEquals(EconomicKind.INTERNAL_TRANSFER to true, inLeg.economicKind to inLeg.economicKindConfirmed)
    }

    @Test fun theOtherBanksMessageFirstMeansNoAutomaticLeg() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val effect = OwnTransferLegEffect(MemoryWalletRepository(listOf(a, b)), txns)
        recordIn(txns, effect, "2026-10-06")
        recordOut(txns, effect, "2026-10-07")
        assertEquals(1, all(txns).count { it.walletId == b.id }, "يوم قبل = نفس التحويل (§112-3 ±يوم)")
    }

    @Test fun twoDaysApartAreNotTheSameTransfer() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val effect = OwnTransferLegEffect(MemoryWalletRepository(listOf(a, b)), txns)
        recordOut(txns, effect, "2026-10-07")
        recordIn(txns, effect, "2026-10-09")
        assertEquals(3, all(txns).size, "يومين بُعد ⇒ مش هي")
    }

    @Test fun theHeroTotalDoesNotDropForAnOwnTransfer() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val wallets = MemoryWalletRepository(listOf(a, b))
        val before = LoadWithYouNow(wallets, txns, Currency.SAR).load("2026-10-10").totalMinor
        recordOut(txns, OwnTransferLegEffect(wallets, txns))
        val after = LoadWithYouNow(wallets, txns, Currency.SAR).load("2026-10-10")
        assertEquals(120_000L, before)
        assertEquals(before, after.totalMinor, "«معك الآن» زي ما هو")
        assertEquals(50_000L to 70_000L, after.wallets.single { it.wallet.id == a.id }.balanceMinor to after.wallets.single { it.wallet.id == b.id }.balanceMinor)
    }

    @Test fun theFullSmsLaneStillRecordsExactlyTwoLegs() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a.copy(openingBalanceMinor = 0), b.copy(openingBalanceMinor = 0)))
        val world = SmsWorld(listOf(space)).enable("AlRajhiBank")
        world.receive(sms("own", "حوالة بين حساباتك\nمن:1111\nالى:2222\nمبلغ:SAR 500.00\n26/10/07 11:15", sender = "AlRajhiBank"))
        val effects = smsRecordEffects(space.spaceId, space.wallets, world.inbox, space.categories, txns = space.txns)
        val lane = SmsLane.of(space.spaceId, space.importDeps(effects), ManageSmsInbox(world.inbox, space.parse), space.wallets)
        AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(lane))).run()
        val list = space.all()
        assertEquals(2, list.size, "صادر وداخل — من غير رِجل تلقائية زيادة: $list")
        assertEquals(setOf(a.id to Direction.OUT, b.id to Direction.IN), list.map { it.walletId to it.observedDirection }.toSet())
    }

    @Test fun theZoneDecisionUsesTheSameOneDayWindow() {
        val out = txn("t-out", a.id, Direction.OUT, "2026-10-07", EconomicKind.INTERNAL_TRANSFER, true)
        val nextDay = txn("t-in", b.id, Direction.IN, "2026-10-08", EconomicKind.UNCLASSIFIED, false)
        assertTrue(ownLegAlreadyThere(listOf(nextDay), out, b.id))
        assertTrue(!ownLegAlreadyThere(listOf(nextDay.copy(occurredAt = "2026-10-09")), out, b.id))
    }
}
