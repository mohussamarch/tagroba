package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.core.MatchingState
import app.masroufy.core.UiKey
import app.masroufy.core.uiText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * قرارات المالك §82-9 و§82-10 (2026-10-10): ضريبة الرسوم **جوه** «رسوم بنكية» عملية واحدة · رسالة رسوم لوحدها + نفس الرسوم في رسالة التحويل
 * = **رسوم واحدة** أيًّا كانت اللي وصلت الأول (رسالة الرسوم الأول كانت بتطلع رسومين). كل الرسايل مخترعة.
 */
class SmsFeeTwinTest {
    private val feeOnly = "Debit fees\nReason: Transfer fee\nAmount: SAR 5.75\nFrom: STC Bank wallet\nDate: 2026-03-05 09:10:44"
    private val smallTransfer = "حوالة محلية صادرة\nمصرف:ANB\nمن:1111\nمبلغ:SAR 250\nالى:TEST OTHER\nالرسوم:SAR 5.75\n26/03/05 11:30"

    private suspend fun S2Desk.fees() = all().filter { it.economicKind == EconomicKind.FEE }

    @Test fun vatStaysInsideTheOneBankFee() = runBlocking<Unit> {
        val desk = S2Desk(feeTwins = true)
        desk.receive("t1", S2_SA_TOTAL_DUE)
        desk.recordAll()
        assertEquals(listOf(661L), desk.fees().map { it.amountMinor }, "5.75 رسوم + 0.86 ضريبة = عملية «رسوم بنكية» واحدة")
    }

    /** رسالة الرسوم وصلت الأول واتسجلت ⇒ التحويل لما ييجي ما بيعملش رسوم تانية، وسجل «#fee» بتاعه بيتكتب على عملية الرسوم الموجودة. */
    @Test fun feeMessageFirstThenTransferIsOneFee() = runBlocking<Unit> {
        val desk = S2Desk(feeTwins = true)
        desk.receive("f1", feeOnly)
        assertEquals(1, desk.recordAll())
        desk.receive("t1", S2_SA_FEE_ON_TOP)
        assertEquals(1, desk.recordAll())
        val fee = desk.fees().single()
        assertEquals(575L, fee.amountMinor)
        assertEquals(2, desk.all().size, "التحويل ورسومه — من غير رسوم تانية")
        assertEquals(-100_575L, desk.balance(BANK), "الرصيد = المخصوم فعلًا")
        val claim = desk.space.sources.listByTransactionIds(listOf(fee.id)).single { it.sourceReference?.endsWith("#fee") == true }
        assertEquals(MatchingState.DUPLICATE, claim.matchingState)
        assertEquals(uiText(UiKey.SMS_FEE_TWIN_REASON), claim.reason)
    }

    @Test fun bothInTheSameBatchIsOneFee() = runBlocking<Unit> {
        val desk = S2Desk(feeTwins = true)
        desk.receive("f1", feeOnly)
        desk.receive("t1", S2_SA_FEE_ON_TOP)
        assertEquals(2, desk.recordAll())
        assertEquals(1, desk.fees().size)
        assertEquals(-100_575L, desk.balance(BANK))
    }

    /** رسالة رسوم واحدة وتحويلين بنفس الرسوم في نفس اليوم ⇒ الأولاني ياخدها والتاني رسومه لوحده (ولا رسوم بتضيع). */
    @Test fun oneFeeMessageIsCountedForOneTransferOnly() = runBlocking<Unit> {
        val desk = S2Desk(feeTwins = true)
        desk.receive("f1", feeOnly)
        desk.recordAll()
        desk.receive("t1", S2_SA_FEE_ON_TOP)
        desk.recordAll()
        desk.receive("t2", smallTransfer)
        desk.recordAll()
        assertEquals(listOf(575L, 575L), desk.fees().map { it.amountMinor })
        assertEquals(-(100_000L + 25_000L + 575L + 575L), desk.balance(BANK))
    }

    /** §88: التحويل الأول ⇒ رسالة الرسوم بعده **بتنضم** لرسوم التحويل لوحدها (رسوم واحدة) — مش «شبيهة» مستنية. */
    @Test fun transferFirstThenFeeMessageJoinsItsFee() = runBlocking<Unit> {
        val desk = S2Desk(feeTwins = true)
        desk.receive("t1", S2_SA_FEE_ON_TOP)
        desk.recordAll()
        desk.receive("f1", feeOnly)
        val view = desk.load()
        assertEquals(emptyList(), view.similar.map { it.messageId }, "مش مستنية تأكيد")
        assertEquals(listOf("f1"), view.ready.map { it.messageId })
        desk.screen.recordAll(emptyMap(), emptyList())
        val fee = desk.fees().single()
        assertEquals(575L, fee.amountMinor)
        assertEquals(2, desk.all().size, "التحويل ورسومه — من غير رسوم تانية")
        assertEquals(-100_575L, desk.balance(BANK))
        val joined = desk.space.sources.listByTransactionIds(listOf(fee.id)).single { it.sourceReference?.endsWith("#fee") != true }
        assertEquals(MatchingState.DUPLICATE, joined.matchingState)
        assertEquals(uiText(UiKey.SMS_FEE_TWIN_REASON), joined.reason)
        assertEquals(emptyList(), desk.load().ready + desk.load().similar, "الرسالة اتشالت من الصندوق")
    }

    /** التحويل الأول ثم رسالة الرسوم: التراجع عن دفعة التحويل ⇒ الرسوم بتفضل (رسالتها مصدر تاني)؛ وعن دفعة الرسالة ⇒ رسوم التحويل بتفضل. */
    @Test fun transferFirstUndoEitherBatchKeepsOneFee() = runBlocking<Unit> {
        suspend fun joinedDesk(): S2Desk = S2Desk(feeTwins = true).also { d ->
            d.receive("t1", S2_SA_FEE_ON_TOP)
            d.recordAll()
            d.receive("f1", feeOnly)
            d.recordAll()
        }
        // دفعة التحويل = اللي فيها سجل «#fee» · دفعة رسالة الرسوم = اللي ما فيهاش
        suspend fun S2Desk.batch(transfer: Boolean) = space.batchStore.listRecent(10).first { b ->
            space.sources.listByBatch(b.id).any { r -> r.sourceReference?.endsWith("#fee") == true } == transfer
        }

        val a = joinedDesk()
        a.revert().execute(a.batch(transfer = true).id)
        assertEquals(listOf(575L), a.all().map { it.amountMinor }, "التحويل اتمسح والرسوم فضلت")
        assertEquals(-575L, a.balance(BANK))

        val b = joinedDesk()
        b.revert().execute(b.batch(transfer = false).id)
        assertEquals(listOf(100_000L, 575L), b.all().map { it.amountMinor }.sorted().reversed(), "التحويل ورسومه زي ما كانوا")
        assertEquals(-100_575L, b.balance(BANK))
        assertEquals(1, b.space.sources.listByTransactionIds(listOf(b.fees().single().id)).size)
    }

    /** مش واضح إنها هي ⇒ زي الأول بالظبط: مبلغ مختلف · بعد يومين · رسوم التحويل خدت رسالتها خلاص. */
    @Test fun onlyAClearTwinJoins() = runBlocking<Unit> {
        val other = S2Desk(feeTwins = true)
        other.receive("t1", S2_SA_FEE_ON_TOP)
        other.recordAll()
        other.receive("f1", feeOnly.replace("SAR 5.75", "SAR 6.00"))
        other.recordAll()
        assertEquals(listOf(575L, 600L), other.fees().map { it.amountMinor }.sorted(), "مبلغ تاني = رسوم تانية")

        val late = S2Desk(feeTwins = true)
        late.receive("t1", S2_SA_FEE_ON_TOP)
        late.recordAll()
        late.receive("f1", feeOnly.replace("2026-03-05", "2026-03-07"), at = "2026-03-07T09:00:00Z")
        late.recordAll()
        assertEquals(2, late.fees().size, "بعد يومين = برّه النافذة")

        val twice = S2Desk(feeTwins = true)
        twice.receive("t1", S2_SA_FEE_ON_TOP)
        twice.recordAll()
        twice.receive("f1", feeOnly)
        twice.recordAll()
        twice.receive("f2", feeOnly.replace("09:10:44", "09:40:12"))
        assertEquals(emptyList(), twice.load().ready.map { it.messageId }, "رسوم التحويل خدت رسالتها — التانية ما بتنضمش لوحدها")
        assertEquals(1, twice.fees().size)
    }

    /** التراجع عن دفعة التحويل: عملية الرسوم ليها مصدر تاني (رسالة الرسوم) ⇒ بتفضل، والتحويل بيتمسح. */
    @Test fun revertingTheTransferKeepsTheFeeMessage() = runBlocking<Unit> {
        val desk = S2Desk(feeTwins = true)
        desk.receive("f1", feeOnly)
        desk.recordAll()
        desk.receive("t1", S2_SA_FEE_ON_TOP)
        desk.recordAll()
        val transferBatch = desk.space.batchStore.listRecent(10).first { b -> desk.space.sources.listByBatch(b.id).any { it.sourceReference?.endsWith("#fee") == true } }
        desk.revert().execute(transferBatch.id)
        assertEquals(listOf(575L), desk.all().map { it.amountMinor })
        assertTrue(desk.space.sources.listByBatch(transferBatch.id).isEmpty())
        assertEquals(-575L, desk.balance(BANK))
    }
}
