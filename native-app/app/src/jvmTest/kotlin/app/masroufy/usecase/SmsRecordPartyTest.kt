package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.transferPartyOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** smoke 0.5.4 ملاحظة 1 — «سجّلها وحدِّد الطرف» على حوالة صادرة بشكل جديد: العملية بترجع للشاشة وطرفها مقروء. رسالة مخترعة. */
class SmsRecordPartyTest {
    private val out = "حوالة محلية صادرة\nمصرف:ANB\nمن:0000\nمبلغ:SAR 300\nالى:QA PERSON Q\nالى:7788\nالرسوم:SAR 0.58\nفي:26/10/07 23:30"

    @Test fun recordingANewShapeOutgoingTransferReturnsItWithItsParty() = runBlocking<Unit> {
        val w = SmsWorld(learnOnReceive = false).enable()
        val space = w.spaces.single()
        w.receive(sms("p1", out))
        assertEquals(listOf("p1"), w.auto().run().waiting, "new shape ⇒ waits")
        val ids = mutableListOf<String>()
        assertEquals(1, w.auto().confirm(listOf("p1"), recordedIds = ids))
        val all = space.all()
        val txn = all.single { it.id in ids && it.amountMinor == 30_000L }
        assertEquals(Direction.OUT, txn.observedDirection)
        val party = assertNotNull(transferPartyOf(txn), "party")
        assertEquals("QA PERSON Q" to "7788", party.label to party.last4)
    }
}
