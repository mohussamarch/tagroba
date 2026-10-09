package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.TransferVerdict
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * الأطراف المربوطة في «زون التحويلات» (§60) بتتطبق على تحويلات **رسايل البنك** اللي اتسجلت لوحدها (§72) زي الكشف بالظبط.
 * كل الأسامي والأرقام مخترعة.
 */
class SmsTransferDecisionsTest {
    private suspend fun partyOf(body: String): TransferPartyRef {
        val world = SmsWorld().enable()
        world.receive(sms("probe", body))
        world.auto().run()
        return transferPartyOf(world.spaces.single().all().single())!!
    }

    private fun decision(party: TransferPartyRef, verdict: TransferVerdict, personId: String? = null) =
        TransferParty(party.key, party.label, party.last4, verdict, personId, "2026-10-01T00:00:00.000Z")

    private suspend fun recordWith(decisions: List<TransferParty>, vararg bodies: String): List<Transaction> {
        val space = SmsSpace(parties = decisions)
        val world = SmsWorld(listOf(space)).enable()
        bodies.forEachIndexed { i, b -> world.receive(sms("m$i", b)) }
        assertEquals(bodies.size, world.auto().run().recorded)
        return space.all()
    }

    @Test fun transferToALinkedPersonIsRecordedAgainstThatPersonAutomatically() = runBlocking<Unit> {
        val person = partyOf(TO_PERSON)
        val (out, incoming) = recordWith(listOf(decision(person, TransferVerdict.PERSON, "p-1")), TO_PERSON, FROM_PERSON).sortedBy { it.observedDirection.wire }
            .let { list -> list.single { it.amountMinor == 50_000L } to list.single { it.amountMinor == 100_000L } }
        assertEquals(EconomicKind.UNCLASSIFIED, out.economicKind, "الصادر لشخص بيتسأل «سلفة ولا دعم؟» كل مرة (§75-5) — مش دعم لوحده")
        assertFalse(out.economicKindConfirmed)
        assertEquals(ReviewState.NEEDS_REVIEW, out.reviewState)
        assertEquals(ReviewState.NEEDS_REVIEW, incoming.reviewState, "الوارد منه بيتسأل (§39.1)")
        assertFalse(incoming.economicKindConfirmed)
    }

    @Test fun ownAccountByLastFourBecomesAnInternalTransfer() = runBlocking<Unit> {
        val body = "حوالة داخلية صادرة\nالرصيد: 1500 SAR\nمبلغ:SAR 35.62\nإلى: 9999\n26/10/07 09:35"
        val own = partyOf(body)
        assertEquals("9999", own.last4)
        val t = recordWith(listOf(decision(own, TransferVerdict.OWN_ACCOUNT)), body).single()
        assertEquals(EconomicKind.INTERNAL_TRANSFER, t.economicKind)
        assertEquals(ReviewState.CONFIRMED, t.reviewState)
    }

    @Test fun dismissedPartyChangesNothingAndUndecidedStaysOpen() = runBlocking<Unit> {
        val person = partyOf(TO_PERSON)
        val dismissed = recordWith(listOf(decision(person, TransferVerdict.DISMISSED)), TO_PERSON).single()
        val undecided = recordWith(emptyList(), TO_PERSON).single()
        assertEquals(EconomicKind.UNCLASSIFIED, dismissed.economicKind)
        assertEquals(dismissed.copy(id = undecided.id), undecided.copy(id = undecided.id))
    }

    @Test fun theZoneSeesSmsTransfersAndDecidingThereFixesThem() = runBlocking<Unit> {
        val space = SmsSpace()
        val world = SmsWorld(listOf(space)).enable()
        world.receive(sms("m1", TO_PERSON))
        world.auto().run()
        val people = MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي")))
        val zone = ManageTransfers(ManageTransfersDeps(space.txnStore, space.parties, people, MemoryUnitOfWork(listOf(space.txnStore, space.parties)), FixedClock("2026-10-08T00:00:00.000Z")))
        val row = zone.zone().rows.single()
        assertEquals("TEST PERSON" to Currency.SAR, row.party.label to row.currency)
        val before = space.all().single()
        assertEquals(EconomicKind.UNCLASSIFIED to ReviewState.NEEDS_REVIEW, before.economicKind to before.reviewState)
        // الصادر لشخص ما بقاش بيتحط «دعم» (§75-5): العملية كانت مستنية أصلًا ⇒ مفيش كتابة، والسؤال بيطلع من مصدر الأسئلة
        assertEquals(0, zone.markPerson(row.party, "p-1"), "مستنية زي ما هي — مفيش حاجة تتكتب")
        assertEquals(before, space.all().single())
        val asks = TransferAskSource(
            TransferAskSourceDeps("sa", space.txnStore, space.parties, MemoryObligationRepository(), MemorySettlementRepository(), MemoryAllocationRepository()),
        ).pending("2026-10-01", "2026-10-31")
        assertEquals(listOf(AskKind.LOAN_OR_SUPPORT to before.id), asks.map { it.kind to it.transactionId }, "القرار من الزون بيوصل لتحويل الرسالة اللي اتسجل قبله")
    }
}
