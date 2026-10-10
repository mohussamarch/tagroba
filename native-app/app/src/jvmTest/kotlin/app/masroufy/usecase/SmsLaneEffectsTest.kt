package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** الدامج: خط رسايل البنك بآثار كل الشرايح بترتيب العقد ([smsRecordEffects]) في الخلفية. كل الرسايل والأسامي مخترعة. */
class SmsLaneEffectsTest {
    private val atm = "ATM Withdrawal\nCard: *7739\nAmount: SAR 500.00\nAt: TEST ATM RIYADH\nOn: 2026-10-07 10:00"

    @Test fun theBackgroundLaneWithAllEffectsMovesCashAndSplitsTheFee() = runBlocking<Unit> {
        val space = SmsSpace()
        val world = SmsWorld(listOf(space)).enable()
        world.receive(sms("atm", atm), sms("due", S2_SA_TOTAL_DUE))
        val effects = smsRecordEffects(space.spaceId, space.wallets, world.inbox, space.categories)
        val lane = SmsLane.of(space.spaceId, space.importDeps(effects), ManageSmsInbox(world.inbox, space.parse), space.wallets)
        val auto = AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(lane)))
        val run = auto.run()
        assertEquals(1, run.recorded, "السحب لوحده")
        assertEquals(listOf("due"), run.waiting, "التحويل لطرف لسه مالوش قرار بيستنى المالك (§60)")
        assertEquals(1, auto.confirm(listOf("due")), "المالك أكّد")

        val all = space.all()
        assertEquals(CASH.id, all.single { it.amountMinor == 50_000L }.transferToWalletId, "السحب اتنقل للكاش")
        assertEquals(100_000L, all.single { it.amountMinor == 100_000L }.amountMinor, "التحويل بالأساسي")
        assertEquals(listOf(661L), all.filter { it.economicKind == EconomicKind.FEE }.map { it.amountMinor }, "الرسوم عملية لوحدها")
    }
}
