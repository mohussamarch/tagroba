package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.uiText
import app.masroufy.memory.MemoryMerchantRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * الجولة السادسة (المراجعة العدائية التانية — WA01): البنك (المرسل) مربوط بمحفظة، والمالك عنده حساب **تاني** في نفس البنك مسجّل بمحفظة
 * تانية. رسالة بتقول «From: **8840» (الحساب التاني) كانت بتتسجل لوحدها في محفظة البنك المربوط (2951) لمجرد إن المرسل نفسه.
 * دلوقتي: أرقام الحساب في الرسالة = حساب محفظة تانية للمالك ⇒ **ما بتتسجلش لوحدها** وبتستنى ومعاها سببها. كل الأسامي والأرقام مخترعة.
 */
class AutoRecordSmsAccountTest {
    private val bankA = Wallet("sa-bank", "بنك وهمي أ", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "2951")
    private val bankB = Wallet("sa-bank-b", "بنك وهمي ب", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "8840")

    private fun transfer(from: String, amount: String) = "Debit Transfer Internal\nAmount: SAR $amount\nTo: SAHAR SAMPLE\nFrom: **$from\nOn: 2026-10-07 11:15"

    @Test fun aMessageAboutTheOwnersOtherAccountWaitsInsteadOfLandingInTheMappedWallet() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, bankA, bankB))
        val world = SmsWorld(listOf(space)).enable()
        world.auto().chooseWallet("sa", "TESTBANK", bankA.id)
        world.receive(sms("wa01", transfer("8840", "640.00")), sms("own", transfer("2951", "710.00")), sms("cafe", CAFE))

        val r = world.auto().run()
        assertEquals(2, r.recorded, "حساب المحفظة نفسها + الرسالة اللي مفيهاش رقم حساب")
        assertEquals(listOf("wa01"), r.waiting)
        assertEquals(listOf("wa01"), world.queued(), "المستنية فضلت في الصندوق — ما ضاعتش")
        assertTrue(space.all().all { it.walletId == bankA.id })
        assertEquals(setOf(71_000L, 2_500L), space.all().map { it.amountMinor }.toSet())

        // الشاشة بتعرضها جاهزة ومعاها السبب (حساب تاني) — المالك يقرر
        val screen = ReviewSmsInbox(
            ReviewSmsInboxDeps(ManageSmsInbox(world.inbox, space.parse), ImportStatement(space.importDeps()), MemoryMerchantRepository(), space.categories, space.ids),
        )
        val target = SmsReviewTarget(bankA.id, bankA.name, accountLast4 = "2951", otherAccountsLast4 = setOf("8840"))
        val line = screen.load(target).ready.single()
        assertEquals("wa01", line.messageId)
        assertEquals(uiText(TextKey.SMS_WAIT_OTHER_ACCOUNT), line.confirmReason)
    }
}
