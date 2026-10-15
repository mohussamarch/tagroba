package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * sms-fix (HANDOVER §6.5-3) — أرقام مخترعة بس (مش حسابات المالك ولا مبالغه): A = 2222 (المحفظة) · B = 1111 (حسابه التاني) · 3333 / 7777 مش
 * لأي محفظة.
 */
class SmsAccountLegsTest {
    private fun row(kind: SmsKind, direction: Direction, raw: String, date: IsoDate = "2026-03-03") =
        SmsRow(1, date, 50_000, direction, "", "SMS:test", "", "", raw, kind = kind)

    private val bToA = row(SmsKind.OWN_TRANSFER, Direction.OUT, "حوالة بين حساباتك\nمن:1111\nالى:2222\nمبلغ:SAR 500.00")

    @Test
    fun ownTransferIntoThisWalletIsIncomingNotTheShapeDirection() {
        assertEquals(SmsLegsOf("1111", "2222"), smsLegsOf(bToA.raw))
        assertEquals(SmsLegVerdict.Into("2222"), smsLegVerdict(bToA, setOf("2222")))
        val legs = smsLegRows(bToA, smsLegVerdict(bToA, setOf("2222")))
        assertEquals(listOf(Direction.IN), legs.map { it.direction })
        assertEquals("2222", legs.single().accountLast4)
    }

    @Test
    fun ownTransferBetweenTwoWalletsHasBothLegs() {
        val v = smsLegVerdict(bToA, setOf("1111", "2222"))
        assertEquals(SmsLegVerdict.Between("1111", "2222"), v)
        val legs = smsLegRows(bToA, v)
        assertEquals(listOf(Direction.OUT to "1111", Direction.IN to "2222"), legs.map { it.direction to it.accountLast4 })
        assertEquals("SMS:test$IN_LEG_SUFFIX", legs[1].reference)
    }

    @Test
    fun outOfThisWalletStaysOutgoing() {
        val aToB = row(SmsKind.OWN_TRANSFER, Direction.OUT, "حوالة بين حساباتك\nمن:2222\nالى:1111")
        assertEquals(SmsLegVerdict.OutOf("2222"), smsLegVerdict(aToB, setOf("2222")))
    }

    @Test
    fun otherAccountIncomingNeverLandsInTheOnlyBankWallet() {
        val toOther = row(SmsKind.TRANSFER_IN, Direction.IN, "حوالة داخلية واردة\nمن:7777\nالى:3333")
        assertEquals(SmsLegVerdict.Unknown("3333"), smsLegVerdict(toOther, setOf("2222")))
        // المحفظة لسه مالهاش أرقام خالص ⇒ برضه سؤال، مش تسجيل
        assertEquals(SmsLegVerdict.Unknown("1111"), smsLegVerdict(bToA, emptySet()))
    }

    @Test
    fun nonTransferAndNoAccountLinesKeepOldBehaviour() {
        assertEquals(SmsLegVerdict.Keep, smsLegVerdict(row(SmsKind.PURCHASE, Direction.OUT, "شراء\nمن:1111"), setOf("2222")))
        assertEquals(SmsLegVerdict.Keep, smsLegVerdict(row(SmsKind.TRANSFER_OUT, Direction.OUT, "حوالة صادرة\nمبلغ:SAR 10"), setOf("2222")))
    }

    @Test
    fun cardHoldIsNotAPurchase() {
        val purchase = "شراء\nبطاقة:4321;مدى\nمبلغ:SAR 25.00\nلدى:SAMPLE SHOP\nفي:26-03-03 10:00"
        assertTrue(parseBankSms(BankSmsMessage("AlRajhiBank", "2026-03-03T10:00:00", purchase), 1) is SmsParseResult.Ok)
        val hold = parseBankSms(BankSmsMessage("AlRajhiBank", "2026-03-03T10:00:00", "شراء TEMP\nبطاقة:4321;مدى\nمبلغ:SAR 25.00\nلدى:SAMPLE SHOP\nفي:26-03-03 10:00"), 1)
        assertTrue(hold is SmsParseResult.Rejected, "TEMP hold must not be a purchase: $hold")
    }

    @Test
    fun lateCardPostingGetsTheWideWindowOnly() {
        val sms = CrossSourceRow(1, "2026-03-03", 9_900, Direction.OUT, fromSms = true, reference = "SMS:x", state = MatchingState.NEW)
        val statement = CrossSourceExisting("t-st", "2026-03-07", 9_900, Direction.OUT, fromSms = false, fromStatement = true)
        assertTrue(matchCrossSource(listOf(sms), listOf(statement), CROSS_SOURCE_WINDOW_DAYS).isEmpty())
        val wide = matchCrossSource(listOf(sms.copy(wide = true)), listOf(statement), CROSS_SOURCE_WINDOW_DAYS)
        assertEquals(CrossSourceVerdict.Merge("t-st"), wide[1])
        assertTrue(lateCardPosting("شراء دولي\nمبلغ:USD 26.40") && lateCardPosting("PoS", foreign = true) && !lateCardPosting("شراء\nلدى:SAMPLE SHOP"))
    }

    @Test
    fun repairTargetAndStatementDuplicatesForTheExactBugPattern() {
        // قبل الإصلاح: B ⇒ A اتسجل خارج من A، والكشف فيه نفس الحركة داخل ⇒ بعد الإصلاح: داخل، والزوج للمراجعة (اتجاه عكس)
        assertEquals("2222" to Direction.IN, smsRepairTarget(bToA, setOf("2222"), "2222"))
        assertNull(smsRepairTarget(bToA, setOf("1111", "2222"), "9999"))
        val moves = listOf(
            LedgerMove("t-sms", "wA", "2026-03-03", 50_000, Direction.OUT, fromSms = true, fromStatement = false, ownTransfer = true),
            LedgerMove("t-st", "wA", "2026-03-04", 50_000, Direction.IN, fromSms = false, fromStatement = true),
            LedgerMove("t-late", "wA", "2026-03-20", 50_000, Direction.OUT, fromSms = true, fromStatement = false),
        )
        val pairs = smsStatementDuplicates(moves, mapOf("wA" to "2026-03-10"))
        assertEquals(listOf(SmsStatementPair("t-sms", "t-st", oppositeDirection = true)), pairs)
        // ٤ أيام لشراء دولي — بس لو معلّم واسع
        val intl = listOf(
            LedgerMove("s", "wA", "2026-03-01", 2_640, Direction.OUT, fromSms = true, fromStatement = false, wide = true),
            LedgerMove("st", "wA", "2026-03-05", 2_640, Direction.OUT, fromSms = false, fromStatement = true),
        )
        assertEquals(1, smsStatementDuplicates(intl, mapOf("wA" to "2026-03-10")).size)
        assertTrue(smsStatementDuplicates(intl.map { it.copy(wide = false) }, mapOf("wA" to "2026-03-10")).isEmpty())
    }
}

private fun SmsLegsOf(from: String?, to: String?) = SmsLegs(from, to)
