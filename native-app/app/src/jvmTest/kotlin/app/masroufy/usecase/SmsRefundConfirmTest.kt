package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.core.ReviewState
import app.masroufy.core.SchemaId
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsParseResult
import app.masroufy.core.TextKey
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.parseBankSms
import app.masroufy.core.smsRowsJson
import app.masroufy.core.toParsedRow
import app.masroufy.core.uiText
import app.masroufy.core.withEstimatedKinds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * الاسترداد من محل (§75-6 — `RefundConfirmEffect`): بيستنى ومعاه «استرداد» مقترح، ولما المالك يأكده بيتسجل «استرداد» مؤكد بينقّص المصروف.
 * التسجيل التلقائي عمره ما بيسجّله. كل الرسايل والأسامي مخترعة.
 */
class SmsRefundConfirmTest {
    private val refund = "Notification: Refund\nTransaction: TEST OPTICS\nCard: ***7739\nAmount: 89.00 SAR\nDate: 2026-10-07 16:02"
    private val purchase = "شراء\nبـSR 200\nلدى:TEST OPTICS\n26/10/07"

    @Test fun aRefundWaitsWithTheSuggestion() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("rf", refund, SENT_AT)
        val line = desk.load().ready.single()
        assertEquals(SmsKind.REFUND, line.kind)
        assertEquals(Direction.IN, line.direction)
        assertEquals(uiText(TextKey.SMS_WAIT_REFUND), line.confirmReason)
        assertFalse(line.shape.clear, "مش بيتسجل لوحده")
    }

    @Test fun theOwnersConfirmationRecordsARefundThatReducesExpense() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("buy", purchase, SENT_AT)
        desk.receive("rf", refund, SENT_AT)
        assertEquals(2, desk.recordAll(), "«سجّل الكل» = تأكيد المالك")
        val r = desk.all().single { it.observedDirection == Direction.IN }
        assertEquals(EconomicKind.REFUND_RECEIVED, r.economicKind)
        assertTrue(r.economicKindConfirmed)
        assertEquals(ReviewState.CONFIRMED, r.reviewState)
        assertEquals(8_900L, r.amountMinor)
        // زي الشاشات: الشراء اللي لسه ما اتحددش بيتحسب بنوع تقديري (§18)، والاسترداد بينقّص المصروف (§42) — مش دخل
        val names = desk.space.categories.listAll().associate { it.id to it.name }
        val totals = computePeriodTotals(withEstimatedKinds(desk.all(), names).transactions, emptyList())
        assertEquals(20_000L - 8_900L, totals.personalExpenseMinor)
        assertEquals(0L, totals.incomeMinor)
        assertEquals(-8_900L, computePeriodTotals(listOf(r), emptyList()).personalExpenseMinor)
    }

    @Test fun anAutomaticRunNeverRecordsARefund() = runBlocking<Unit> {
        val space = SmsSpace()
        val world = SmsWorld(listOf(space)).enable()
        world.receive(sms("rf", refund), sms("cafe", CAFE))
        val effects = listOf(CashWithdrawalEffect(space.wallets), SmsFeeEffect(space.categories), RefundConfirmEffect())
        val lane = SmsLane.of(space.spaceId, space.importDeps().copy(effects = space.smsEffects(world.inbox) + effects), ManageSmsInbox(world.inbox, space.parse), space.wallets)
        val r = AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(lane))).run()
        assertTrue("rf" in r.waiting)
        assertTrue(space.all().none { it.observedDirection == Direction.IN }, "الاسترداد فضل مستني")
    }

    /** حتى لو سطر استرداد وصل للتسجيل من غير المالك (قاعدة الانتظار اتغيرت في يوم) ⇒ ما بيتأكدش «استرداد» لوحده. */
    @Test fun onlyTheOwnersRecordingConfirmsARefund() = runBlocking<Unit> {
        for (byOwner in listOf(false, true)) {
            val space = SmsSpace()
            val row = assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", SENT_AT, refund), 1)).row
            val importer = ImportStatement(space.importDeps().copy(effects = listOf(RefundConfirmEffect())))
            val request = ImportRequest(
                "bank-sms.json", smsRowsJson(listOf(row)), BANK.name, ImportSourceType.SMS, BANK.id, SchemaId.SMS,
                parsedRows = listOf(row.toParsedRow()), smsRows = mapOf(row.lineNumber to row), byOwner = byOwner,
            )
            importer.commit(request, importer.preview(request))
            val t = space.all().single()
            assertEquals(if (byOwner) EconomicKind.REFUND_RECEIVED else EconomicKind.UNCLASSIFIED, t.economicKind)
            assertEquals(byOwner, t.economicKindConfirmed)
        }
    }
}
