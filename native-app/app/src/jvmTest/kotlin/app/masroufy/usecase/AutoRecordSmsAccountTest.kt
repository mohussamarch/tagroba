package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.uiText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * الجولة السادسة (المراجعة العدائية التانية — WA01): البنك (المرسل) مربوط بمحفظة، والمالك عنده حساب **تاني** في نفس البنك مسجّل بمحفظة
 * تانية. رسالة بتقول «From: **8840» (الحساب التاني) كانت بتتسجل لوحدها في محفظة البنك المربوط (2951) لمجرد إن المرسل نفسه، وبعدها
 * بقت **بتستنى**. **§75-11 (قرار المالك — S1):** «بنك واحد بحسابين ⇒ التفرقة بآخر ٤ أرقام» ⇒ بتتسجل لوحدها **في المحفظة اللي أرقامها في
 * الرسالة**. والانتظار بسبب «حساب تاني» فضل بس لما المحفظة دي ما تنفعش (عملة تانية). كل الأسامي والأرقام مخترعة.
 */
class AutoRecordSmsAccountTest {
    private val bankA = Wallet("sa-bank", "بنك وهمي أ", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "2951")
    private val bankB = Wallet("sa-bank-b", "بنك وهمي ب", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "8840")

    private fun transfer(from: String, amount: String) = "Debit Transfer Internal\nAmount: SAR $amount\nTo: SAHAR SAMPLE\nFrom: **$from\nOn: 2026-10-07 11:15"

    @Test fun aMessageAboutTheOwnersOtherAccountIsRecordedInThatAccountsWallet() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, bankA, bankB))
        val world = SmsWorld(listOf(space)).enable()
        world.auto().chooseWallet("sa", "TESTBANK", bankA.id)
        world.receive(sms("wa01", transfer("8840", "640.00")), sms("own", transfer("2951", "710.00")), sms("cafe", CAFE))

        val r = world.auto().run()
        assertEquals(3, r.recorded, "كل رسالة في محفظتها")
        assertTrue(r.waiting.isEmpty())
        assertEquals(mapOf(64_000L to bankB.id, 71_000L to bankA.id, 2_500L to bankA.id), space.all().associate { it.amountMinor to it.walletId })
    }

    @Test fun anOtherAccountInAnotherCurrencyStillWaitsWithItsReason() = runBlocking<Unit> {
        val usd = bankB.copy(currency = Currency.USD)
        val space = SmsSpace(wallets = listOf(CASH, bankA, usd))
        val world = SmsWorld(listOf(space)).enable()
        world.auto().chooseWallet("sa", "TESTBANK", bankA.id)
        world.receive(sms("wa01", transfer("8840", "640.00")))
        val r = world.auto().run()
        assertEquals(0, r.recorded, "محفظة الأرقام بعملة تانية ⇒ ما تنفعش")
        assertEquals(listOf("wa01"), r.waiting)
        assertEquals(listOf("wa01"), world.queued(), "المستنية فضلت في الصندوق — ما ضاعتش")

        // الشاشة بتعرضها جاهزة ومعاها السبب (حساب تاني) — المالك يقرر
        val target = SmsReviewTarget(bankA.id, bankA.name, accountLast4 = "2951", otherAccountsLast4 = setOf("8840"))
        val line = space.screen(world.inbox).load(target).ready.single()
        assertEquals("wa01", line.messageId)
        assertEquals(uiText(TextKey.SMS_WAIT_OTHER_ACCOUNT), line.confirmReason)
    }
}
