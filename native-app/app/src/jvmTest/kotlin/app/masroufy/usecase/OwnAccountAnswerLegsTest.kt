package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Wallet
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * إجابة المالك 2026-10-15 (OVERRIDES §91-5): تحويل «إلى: اسم» وأرقامه زي محفظتك التانية ⇒ رِجل خارجة بس وسؤال «ده حسابك؟» (زي ما هو).
 * «أيوه» ⇒ تحويل بين محافظك برجلين (خارج من دي وداخل في دي، «تحويل داخلي» = شكل محايد) · «لأ» ⇒ يفضل خارج. كل الأسامي والأرقام مخترعة.
 */
class OwnAccountAnswerLegsTest {
    private val a = Wallet("w-a", "بنك وهمي أ", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "1111")
    private val b = Wallet("w-b", "بنك وهمي ب", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "2222")
    private val body = "حوالة داخلية صادرة\nمن:1111\nإلى:SAMPLE PERSON\nإلى:2222\nبـSR 500\n26/10/07 09:35"

    private suspend fun recorded(): Pair<SmsSpace, ManageTransfers> {
        val space = SmsSpace(wallets = listOf(CASH, a, b))
        val w = SmsWorld(listOf(space)).enable()
        w.auto().chooseWallet("sa", "TESTBANK", a.id)
        w.receive(sms("t1", body))
        assertEquals(1, w.auto().run().recorded)
        val sent = space.all().single()
        assertEquals(Triple(a.id, Direction.OUT, EconomicKind.UNCLASSIFIED), Triple(sent.walletId, sent.observedDirection, sent.economicKind), "one outgoing leg + the question")
        val deps = ManageTransfersDeps(
            space.txnStore, space.parties, MemoryPersonRepository(), MemoryUnitOfWork(listOf(space.txnStore, space.parties)),
            FixedClock("2026-10-08T09:00:00.000Z"), wallets = space.wallets,
        )
        return space to ManageTransfers(deps)
    }

    private fun net(list: List<app.masroufy.core.Transaction>, walletId: String) =
        list.filter { it.walletId == walletId }.sumOf { if (it.observedDirection == Direction.IN) it.amountMinor else -it.amountMinor }

    @Test fun yesMakesAnOwnWalletTransferWithBothLegs() = runBlocking<Unit> {
        val (space, zone) = recorded()
        val party = transferPartyOf(space.all().single())!!
        assertEquals(2, zone.markOwnAccount(party), "the leg flipped + the other wallet's leg")
        val all = space.all()
        assertEquals(2, all.size)
        assertTrue(all.all { it.economicKind == EconomicKind.INTERNAL_TRANSFER && it.economicKindConfirmed }, "neutral look: internal transfer, not spending")
        assertEquals(-50_000L to 50_000L, net(all, a.id) to net(all, b.id))
        // نفس الرد تاني ما بيعملش رِجل تالتة
        zone.markOwnAccount(party)
        assertEquals(2, space.all().size)
        // الفك بيشيل الرِّجل اللي اتعملت والعملية بترجع تتسأل
        zone.forget(party.key)
        assertEquals(listOf(EconomicKind.UNCLASSIFIED), space.all().map { it.economicKind })
    }

    @Test fun noKeepsItOutgoing() = runBlocking<Unit> {
        val (space, zone) = recorded()
        val sent = space.all().single()
        zone.dismiss(transferPartyOf(sent)!!)
        val after = space.all().single()
        assertEquals(sent, after, "still one outgoing leg from wallet a")
        assertEquals(-50_000L to 0L, net(space.all(), a.id) to net(space.all(), b.id))
    }
}
