package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Currency
import app.masroufy.core.SmsParseResult
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * مراجعة S1 — التوزيع على المحافظ بآخر 4 أرقام (§75-11) في **الشاشة** وفي التسجيل التلقائي، و«مرة واحدة بس مهما حصل» (§72) لما الرسالة
 * تروح محفظة تانية بعد وقعة، والتوزيع بأرقام **الحساب** بس ولمحافظ نفس البنك بس، والشاشة بتتبني من البلد نفسها. كل الأسامي والأرقام مخترعة.
 */
class SmsRoutingReviewTest {
    private val a = Wallet("w-a", "بنك وهمي أ", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "1111")
    private val b = Wallet("w-b", "بنك وهمي ب", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "2222")
    private val c = Wallet("w-c", "بنك وهمي ج", Currency.SAR, "bank", 0, "2026-01-01")

    private fun buy(amount: String, merchant: String, extra: String) = "PoS Purchase\nAmount: SAR $amount\n${extra}At: $merchant\nOn: 2026-10-07 10:00"

    private val toB = buy("200.00", "TEST GROCER", "Account: **2222\n")

    private fun keyOf(body: String) = (parseBankSms(BankSmsMessage("TESTBANK", SENT_AT, body), 1) as SmsParseResult.Ok).row.learnKey!!

    /** الشكوى ١: الشاشة المفتوحة على محفظة أ كانت بتسجّل رسالة حساب ب في أ («حساب تاني» وبعدين «سجّل الكل») وتعلّم شكلها. */
    @Test fun theScreenRecordsEachMessageInTheWalletOfItsAccountDigits() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, b))
        val w = SmsWorld(listOf(space), learnOnReceive = false).enable()
        w.auto().chooseWallet("sa", "TESTBANK", a.id)
        w.receive(sms("toB", toB), sms("cafe", CAFE))
        val screen = space.screen(w.inbox)
        val ready = screen.load(targetOf(a, space.wallets.listAll(), movesCash = false)).ready.associateBy { it.messageId }
        assertEquals(b.id to TextKey.SMS_WAIT_NEW_SHAPE, ready.getValue("toB").let { it.walletId to it.waitReason }, "مش «حساب تاني»")
        assertEquals(a.id, ready.getValue("cafe").walletId)
        assertEquals(2, screen.recordAll(emptyMap(), emptyList()))
        assertEquals(mapOf(20_000L to b.id, 2_500L to a.id), space.all().associate { it.amountMinor to it.walletId })
        assertTrue(keyOf(toB) in w.memory.learnedShapes("sa").getValue("testbank"), "المالك سجّلها ⇒ شكلها اتعلّم")
        assertTrue(w.queued().isEmpty())
    }

    /** الشكوى ٢: اتحفظت في محفظة والجهاز وقع قبل الشيل، والمالك ربط البنك بمحفظة تانية ⇒ المحاولة الجاية كانت بتسجلها تاني. */
    @Test fun aMessageSavedBeforeACrashIsADuplicateEvenWhenItIsRoutedToAnotherWallet() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, c))
        val w = SmsWorld(listOf(space)).enable()
        w.auto().chooseWallet("sa", "TESTBANK", a.id)
        w.receive(sms("m1", CAFE))
        w.inbox.failAcks = 1
        assertFailsWith<IllegalStateException> { w.auto().run() }
        assertEquals(listOf("m1"), w.queued(), "اتحفظت بس ما اتشالتش")
        w.auto().chooseWallet("sa", "TESTBANK", c.id) // المحاولة الجاية رايحة محفظة تانية
        val retry = w.auto().run()
        assertEquals(0 to 1, retry.recorded to retry.duplicates)
        assertEquals(listOf(2_500L to a.id), space.all().map { it.amountMinor to it.walletId }, "مرة واحدة بس")
        assertTrue(w.queued().isEmpty(), "المكررة اتشالت")
    }

    /** نفس الوقعة من الشاشة: المالك سجّل من الشاشة على أ، وقعت قبل الشيل، والخلفية بعدها شايفة الرسالة رايحة ب بأرقام الحساب. */
    @Test fun aScreenSaveBeforeACrashIsNotRecordedAgainByTheBackgroundInAnotherWallet() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, c))
        val w = SmsWorld(listOf(space)).enable()
        w.auto().chooseWallet("sa", "TESTBANK", a.id)
        val body = buy("200.00", "TEST GROCER", "Account: **2222\n")
        w.receive(sms("m1", body))
        val screen = space.screen(w.inbox)
        screen.load(targetOf(a, space.wallets.listAll(), movesCash = false))
        w.inbox.failAcks = 1
        assertFailsWith<IllegalStateException> { screen.recordAll(emptyMap(), emptyList()) }
        assertEquals(a.id, space.all().single().walletId, "الأرقام 2222 مش لمحفظة لسه ⇒ محفظة الشاشة")
        space.wallets.save(c.copy(accountLast4 = "2222")) // المالك كتب أرقام حساب ج بعدها
        val retry = w.auto().run()
        assertEquals(0 to 1, retry.recorded to retry.duplicates)
        assertEquals(1, space.all().size)
        assertTrue(w.queued().isEmpty())
    }

    /**
     * الشكوى ٤: التوزيع بأرقام **الحساب** بس، ولمحافظ نفس البنك بس (مربوطة بيه أو مش مربوطة ببنك تاني). كارت بنك تاني آخره زي حساب في محفظة
     * تانية كان بيتسجل لوحده في المحفظة الغلط ويغلب اختيار المالك.
     */
    @Test fun onlyAccountDigitsRouteAndOnlyAmongTheSendersOwnWallets() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, b, c))
        val w = SmsWorld(listOf(space)).enable("TESTBANK", "OTHERBANK")
        w.auto().chooseWallet("sa", "TESTBANK", a.id)
        w.auto().chooseWallet("sa", "OTHERBANK", c.id)
        w.receive(
            sms("card", buy("55.00", "TEST KIOSK", "Card: *2222\n"), sender = "OTHERBANK"),
            sms("mappedElsewhere", buy("66.00", "TEST KIOSK", "Account: **1111\n"), sender = "OTHERBANK"),
            sms("free", buy("77.00", "TEST KIOSK", "Account: **2222\n"), sender = "OTHERBANK"),
            sms("own", buy("88.00", "TEST KIOSK", ""), sender = "OTHERBANK"),
        )
        val r = w.auto().run()
        val where = space.all().associate { it.amountMinor to it.walletId }
        assertEquals(mapOf(7_700L to b.id, 8_800L to c.id), where, "ب مش مربوطة ببنك تاني ⇒ حسابه · من غير أرقام ⇒ محفظة البنك")
        // رقم كارت · حساب مربوط ببنك تاني ⇒ محفظة البنك نفسه وبتستنى «حساب تاني» (ما بتتسجلش في أي حتة لوحدها)
        assertEquals(listOf("card", "mappedElsewhere"), r.waiting)
        val target = targetOf(c, space.wallets.listAll(), movesCash = false)
        val ready = space.screen(w.inbox).load(target).ready.associateBy { it.messageId }
        for (id in listOf("card", "mappedElsewhere")) {
            assertEquals(c.id to TextKey.SMS_WAIT_OTHER_ACCOUNT, ready.getValue(id).let { it.walletId to it.waitReason }, id)
        }
    }

    /** الشكوى ٨: البلد من غير آثار S1 ما بتتبنيش، والشاشة اللي من البلد بتعلّم (ما بقاش فيه شاشة من غير تعلّم). */
    @Test fun theLaneRequiresTheS1EffectsAndItsScreenLearns() = runBlocking<Unit> {
        val space = SmsSpace()
        val w = SmsWorld(listOf(space), learnOnReceive = false).enable()
        assertFailsWith<IllegalArgumentException> { SmsLane.of("sa", space.importDeps(), ManageSmsInbox(w.inbox, space.parse), space.wallets) }
        assertFailsWith<IllegalArgumentException> {
            SmsLane.of("sa", space.importDeps(listOf(SmsSalaryEffect(w.inbox, "sa"))), ManageSmsInbox(w.inbox, space.parse), space.wallets)
        }
        w.receive(sms("m1", CAFE))
        val screen = space.lane(w.inbox).screen()
        screen.load(SmsReviewTarget(BANK.id, BANK.name))
        assertEquals(1, screen.recordAll(emptyMap(), emptyList()))
        w.receive(sms("m2", MART))
        assertEquals(1, w.auto().run().recorded, "الشاشة علّمت الشكل ⇒ الجاية لوحدها")
    }
}
