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
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.Wallet
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.PassthroughUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** OVERRIDES §120-5 · §120-8 — فلوس داخلة من حسابك التاني. كل الأسامي والأرقام مخترعة · المبالغ بالوحدة الصغرى. */
class OwnIncomingLegTest {
    private val a = Wallet("w-a", "بنك وهمي أ", Currency.SAR, "bank", 100_000, "2026-01-01", accountLast4 = "1111")
    private val b = Wallet("w-b", "بنك وهمي ب", Currency.SAR, "bank", 20_000, "2026-01-01", accountLast4 = "2222")
    private val ownIn = "حوالة بين حساباتك\nمن:1111\nالى:2222\nمبلغ:SAR 500.00"
    private val namedIn = "حوالة محلية واردة\nمن:TEST PERSON\nبـSR 500\nإلى:2222\n26/10/07"
    private val plainOut = "حوالة صادرة\nمبلغ:SAR 500.00"

    private fun txn(id: String, wallet: String, dir: Direction, day: String, kind: EconomicKind, confirmed: Boolean, raw: String? = null) = Transaction(
        id = id, occurredAt = day, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = dir, amountMinor = 50_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = if (confirmed) ReviewState.CONFIRMED else ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        walletId = wallet, rawDescription = raw,
    )

    private suspend fun record(txns: MemoryTransactionRepository, effect: OwnTransferLegEffect, wallet: Wallet, t: Transaction, body: String, kind: SmsKind) {
        val ref = "r-${t.id}"
        val row = ParsedRow(1, t.occurredAt, t.amountMinor, t.observedDirection, "", ref, "SMS", body, body)
        val sms = SmsRow(1, t.occurredAt, t.amountMinor, t.observedDirection, "", ref, "SMS", body, body, kind, wallet.accountLast4)
        val request = ImportRequest(fileName = "sms", content = body, accountIdentity = wallet.name, sourceType = ImportSourceType.SMS, walletId = wallet.id, parsedRows = listOf(row))
        val line = ImportPreviewLine(row = row, state = MatchingState.NEW, reason = "", categoryReason = "", selectedByDefault = true)
        val ids = object : app.masroufy.port.IdGenerator {
            var n = 0
            override fun next(prefix: String) = "$prefix-$ref-${n++}"
        }
        val c = RecordContext(request, "batch-$ref", "2026-10-08T00:00:00Z", false, emptyMap(), mutableListOf(RecordedLine(line, sms, t)), mutableListOf(), ids)
        effect.prepare(c)
        txns.saveMany(c.lines.map { it.transaction } + c.extra.map { it.first })
    }

    private suspend fun all(txns: MemoryTransactionRepository) = txns.listByDateRange("0000-01-01", "9999-12-31")

    @Test fun incomingFromYourOtherAccountDebitsItAndTheLaterDebitMessageMerges() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val effect = OwnTransferLegEffect(MemoryWalletRepository(listOf(a, b)), txns)
        record(txns, effect, b, txn("t-in", b.id, Direction.IN, "2026-10-07", EconomicKind.INTERNAL_TRANSFER, true, ownIn), ownIn, SmsKind.OWN_TRANSFER)
        val leg = all(txns).single { it.walletId == a.id }
        assertEquals("t-in$OWN_LEG_SUFFIX" to Direction.OUT, leg.id to leg.observedDirection, "رِجل خارجة تلقائية من الحساب التاني")
        // رسالة الخصم من البنك التاني وصلت بعدين ⇒ بتندمج (مفيش رجلين)
        record(txns, effect, a, txn("t-out", a.id, Direction.OUT, "2026-10-08", EconomicKind.UNCLASSIFIED, false), plainOut, SmsKind.OTHER)
        val list = all(txns)
        assertEquals(2, list.size, "داخل وخارج بس: $list")
        assertEquals("t-out", list.single { it.walletId == a.id }.id)
    }

    @Test fun noLegWhenTheDebitIsAlreadyThere() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        txns.saveMany(listOf(txn("t-out", a.id, Direction.OUT, "2026-10-06", EconomicKind.INTERNAL_TRANSFER, true)))
        val effect = OwnTransferLegEffect(MemoryWalletRepository(listOf(a, b)), txns)
        record(txns, effect, b, txn("t-in", b.id, Direction.IN, "2026-10-07", EconomicKind.INTERNAL_TRANSFER, true, ownIn), ownIn, SmsKind.OWN_TRANSFER)
        assertEquals(2, all(txns).size)
    }

    @Test fun aPartyWithoutDigitsUsesTheAccountTheOwnerPicked() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val t = txn("t-in", b.id, Direction.IN, "2026-10-07", EconomicKind.INTERNAL_TRANSFER, true, namedIn)
        val party = assertNotNull(transferPartyOf(t), "الطرف بالاسم")
        assertEquals(null, party.last4)
        val parties = MemoryTransferPartyRepository(listOf(TransferParty(party.key, party.label, null, TransferVerdict.OWN_ACCOUNT, walletId = a.id)))
        record(txns, OwnTransferLegEffect(MemoryWalletRepository(listOf(a, b)), txns, parties), b, t, namedIn, SmsKind.TRANSFER_IN)
        assertEquals(Direction.OUT, all(txns).single { it.walletId == a.id }.observedDirection, "§120-8: الرِجل على الحساب اللي اتختار")
    }

    @Test fun markOwnAccountSavesThePickedAccountForAPartyWithoutDigits() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val t = txn("t-in", b.id, Direction.IN, "2026-10-07", EconomicKind.UNCLASSIFIED, false, namedIn)
        txns.saveMany(listOf(t))
        val parties = MemoryTransferPartyRepository()
        val manage = ManageTransfers(
            ManageTransfersDeps(txns, parties, app.masroufy.memory.MemoryPersonRepository(), PassthroughUnitOfWork(), FixedClock("2026-10-08T00:00:00Z"),
                wallets = MemoryWalletRepository(listOf(a, b)), work = kotlin.coroutines.EmptyCoroutineContext),
        )
        val ref = assertNotNull(transferPartyOf(t))
        assertTrue(manage.needsAccountChoice(ref))
        assertTrue(a.id in manage.accountChoices(ref).map { it.id })
        manage.markOwnAccount(ref, a.id)
        assertEquals(a.id, parties.listAll().single().walletId, "الإجابة اتحفظت للطرف")
        val list = all(txns)
        assertEquals(EconomicKind.INTERNAL_TRANSFER, list.single { it.id == "t-in" }.economicKind)
        assertEquals(Direction.OUT, list.single { it.walletId == a.id }.observedDirection, "والقديم خد رِجله")
    }

    @Test fun theManualDebitActionCreatesOneLegOnly() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        txns.saveMany(listOf(txn("t-in", b.id, Direction.IN, "2026-10-07", EconomicKind.UNCLASSIFIED, false, namedIn)))
        val use = AddOwnLeg(txns, MemoryWalletRepository(listOf(a, b)), PassthroughUnitOfWork(), FixedClock("2026-10-08T00:00:00Z"))
        assertEquals(listOf(a.id), use.choices("t-in").map { it.id })
        assertEquals("t-in$OWN_LEG_SUFFIX", use.add("t-in", a.id))
        assertTrue(use.choices("t-in").isEmpty(), "اتعملت ⇒ الفعل بيختفي")
        assertFailsWith<TransferZoneError> { use.add("t-in", a.id) }
        val list = all(txns)
        assertEquals(2, list.size)
        assertEquals(EconomicKind.INTERNAL_TRANSFER to true, list.single { it.id == "t-in" }.let { it.economicKind to it.economicKindConfirmed })
        // رسالة الخصم وصلت بعدين ⇒ بتندمج في الرِجل اليدوية
        val effect = OwnTransferLegEffect(MemoryWalletRepository(listOf(a, b)), txns)
        record(txns, effect, a, txn("t-out", a.id, Direction.OUT, "2026-10-07", EconomicKind.UNCLASSIFIED, false), plainOut, SmsKind.OTHER)
        assertEquals(2, all(txns).size, "مفيش رجلين")
    }

    @Test fun outgoingToYourOtherAccountWithoutTheCreditMessageAddsTheIncomingLeg() = runBlocking<Unit> {
        // §121-10: المراية — خارج من أ ورسالة الدخول على ب ما وصلتش ⇒ «ضيفها لحسابي التاني»
        val cash = Wallet("w-cash", "كاش", Currency.SAR, "cash", 0, "2026-01-01")
        val txns = MemoryTransactionRepository()
        txns.saveMany(listOf(txn("t-out", a.id, Direction.OUT, "2026-10-07", EconomicKind.UNCLASSIFIED, false, plainOut)))
        val use = AddOwnLeg(txns, MemoryWalletRepository(listOf(a, b, cash)), PassthroughUnitOfWork(), FixedClock("2026-10-08T00:00:00Z"))
        assertEquals(listOf(b.id), use.choices("t-out").map { it.id }, "مش الكاش ولا نفس المحفظة")
        assertEquals("t-out$OWN_LEG_SUFFIX", use.add("t-out", b.id))
        val leg = all(txns).single { it.id == "t-out$OWN_LEG_SUFFIX" }
        assertEquals(Triple(b.id, Direction.IN, 50_000L), Triple(leg.walletId, leg.observedDirection, leg.amountMinor))
        assertEquals(EconomicKind.INTERNAL_TRANSFER to true, all(txns).single { it.id == "t-out" }.let { it.economicKind to it.economicKindConfirmed })
        assertTrue(use.choices("t-out").isEmpty(), "اتعملت ⇒ الفعل بيختفي")
        assertFailsWith<TransferZoneError> { use.add("t-out", b.id) }
        // رسالة الدخول وصلت بعدين ⇒ بتندمج في الرِجل اليدوية
        record(txns, OwnTransferLegEffect(MemoryWalletRepository(listOf(a, b)), txns), b, txn("t-late", b.id, Direction.IN, "2026-10-08", EconomicKind.UNCLASSIFIED, false), namedIn, SmsKind.TRANSFER_IN)
        assertEquals(2, all(txns).size, "مفيش رجلين")
    }

    @Test fun outgoingWhoseCreditIsAlreadyOnTheOtherAccountHasNoAction() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        txns.saveMany(listOf(
            txn("t-out", a.id, Direction.OUT, "2026-10-07", EconomicKind.INTERNAL_TRANSFER, true, plainOut),
            txn("t-in", b.id, Direction.IN, "2026-10-07", EconomicKind.INTERNAL_TRANSFER, true, namedIn),
        ))
        val use = AddOwnLeg(txns, MemoryWalletRepository(listOf(a, b)), PassthroughUnitOfWork(), FixedClock("2026-10-08T00:00:00Z"))
        assertTrue(use.choices("t-out").isEmpty())
        assertFailsWith<TransferZoneError> { use.add("t-out", b.id) }
    }
}
