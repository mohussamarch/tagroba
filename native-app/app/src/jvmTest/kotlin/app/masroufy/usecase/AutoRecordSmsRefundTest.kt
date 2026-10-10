package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.SmsShape
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.uiText
import app.masroufy.memory.MemoryMerchantRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * الجولة السابعة (المراجعة العدائية التالتة): **الاسترداد** بشكل معروف كان بيتسجل لوحده دخل، والمالك قرّر في §75-6 (✗ — غير المقترح):
 * «الاسترداد ⇒ يقترح «استرداد» ويستنى تأكيده». و**السحب من الصرّاف** كان بيتسجل صرف عادي من البنك، و§75-4 (النقل لمحفظة الكاش)
 * لسه ما اتبناش. الاتنين دلوقتي **بيستنوا** ومعاهم سببهم، والشاشة بتعرضهم جاهزين (ضغطة «سجّل الكل» = التأكيد). كل الرسايل مخترعة.
 */
class AutoRecordSmsRefundTest {
    private val sar = listOf(
        "rf1" to "Notification: Refund\nTransaction: SAFA OPTICS\nCard: ***7739\nAmount: 89.00 SAR\nDate: 2026-10-07 16:02",
        "rf2" to "استرداد شراء\nبطاقة: 7739*;مدى\nمبلغ: 89.00 ر.س\nمن: SAFA OPTICS\nفي: 26-10-07 16:02",
        "rf3" to "Credit Card Cashback\nCard: *7739\nAmount: SAR 37.20\nOn: 2026-10-07",
        "atm1" to "ATM Withdrawal\nCard: *7739\nAmount: SAR 500.00\nAt: OLAYA ATM RIYADH\nOn: 2026-10-07 10:00",
        "atm2" to "سحب صراف آلي\nبطاقة: *7739\nمبلغ: 500.00 ر.س\nفي: 2026-10-07 10:00",
    )

    @Test fun refundsAndCashWithdrawalsWaitWithTheirReasonWhilePurchasesRecordThemselves() = runBlocking<Unit> {
        val space = SmsSpace()
        val world = SmsWorld(listOf(space)).enable()
        world.receive(*(sar.map { (id, body) -> sms(id, body) } + sms("cafe", CAFE)).toTypedArray())

        val r = world.auto().run()
        assertEquals(1, r.recorded, "الشراء بس")
        assertEquals(sar.map { it.first }, r.waiting)
        assertEquals(sar.map { it.first }, world.queued(), "المستني فضل في الصندوق — ما ضاعش")
        assertEquals(listOf(2_500L), space.all().map { it.amountMinor })

        val screen = space.screen(world.inbox)
        val ready = screen.load(SmsReviewTarget(BANK.id, BANK.name)).ready.associateBy { it.messageId }
        for (id in listOf("rf1", "rf2", "rf3")) assertEquals(uiText(TextKey.SMS_WAIT_REFUND), ready.getValue(id).confirmReason, id)
        for (id in listOf("atm1", "atm2")) assertEquals(uiText(TextKey.SMS_WAIT_CASH_WITHDRAWAL), ready.getValue(id).confirmReason, id)
        assertTrue(ready.values.none { it.shape.clear })

        // «سجّل الكل» = تأكيد المالك ⇒ بيتسجلوا
        assertEquals(sar.size, screen.recordAll(emptyMap(), emptyList()))
    }

    /**
     * §77-D (الشريحة S3): «لقد تم رد …» (التجاري الدولي) و«IPN transfer … returned» (بيت التمويل) بقوا **عملية رجعت** (`SmsKind.RETURNED`)
     * مش استرداد ⇒ ما بيستنوش زي الاسترداد: بيتسجلوا لوحدهم و`ReturnedSmsEffect` بيدوّر على الأصلية ويلغيها أو يخليها «استرداد» مقترح
     * ويسأل (`ReturnedSmsTest`). السحب من فودافون كاش لسه بيستنى.
     */
    @Test fun egyptianRefundsAndCashOutWaitToo() = runBlocking<Unit> {
        val eg = Wallet("eg-bank", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-01-01")
        val space = SmsSpace("eg", wallets = listOf(eg), parse = ::parseEgyptBankSms)
        val world = SmsWorld(listOf(space)).enable()
        world.receive(
            sms("cib", "لقد تم رد EGP75.50 على بطاقتكم الائتمانية المنتهية بـ# 4417 من PROBE BOOKS"),
            sms("kfh", "IPN transfer dated 05/10/2026 with EGP 1,500.00 returned with Ref# 553314. For info call 19888"),
            sms("vf", "تم سحب 500 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 700 جنيه. تاريخ العملية: 2026-10-07 10:00 رقم العملية: 900000613"),
            sms("buy", "Your credit card ending with #4417 was charged for EGP 640.00 at PROBE MART on 07/10/2026"),
        )
        for (id in listOf("cib", "kfh", "vf", "buy")) {
            val row = (space.parse(app.masroufy.core.BankSmsMessage("TESTBANK", SENT_AT, world.memory.sync().messages.first { it.id == id }.body), 1)
                as app.masroufy.core.SmsParseResult.Ok).row
            assertTrue(row.shape is SmsShape.KnownShape, "$id: ${row.shape.wire}")
            if (id == "cib" || id == "kfh") assertEquals(app.masroufy.core.SmsKind.RETURNED, row.kind, id)
        }
        val r = world.auto().run()
        assertEquals(3, r.recorded, "الشراء + العمليتين اللي رجعوا (§77-D)")
        assertEquals(listOf("vf"), r.waiting)
        assertEquals(listOf(7_550L, 64_000L, 150_000L), space.all().map { it.amountMinor }.sorted())
        // خط الرسايل بيضيف أثر «اللي رجع» لوحده (`SmsLane.of`): ما لقيناش الأصلية ⇒ «استرداد» مقترح ويسأل — مش داخل بيتقدّر دخل
        val returns = space.all().filter { it.observedDirection == app.masroufy.core.Direction.IN }
        assertTrue(returns.all { it.suggestedKind == app.masroufy.core.EconomicKind.REFUND_RECEIVED }, "$returns")
        val estimated = app.masroufy.core.withEstimatedKinds(space.all(), emptyMap()).transactions
        assertEquals(0L, app.masroufy.core.computePeriodTotals(estimated, emptyList()).incomeMinor)
    }
}
