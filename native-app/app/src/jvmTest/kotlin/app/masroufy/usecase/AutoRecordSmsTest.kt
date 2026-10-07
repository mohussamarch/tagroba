package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.ReviewState
import app.masroufy.core.Wallet
import app.masroufy.core.parseEgyptBankSms
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** رسايل البنك بتتسجل لوحدها (OVERRIDES §72) على مستودعات الذاكرة — كل الرسايل مخترعة (`SmsAutoFixture.kt`). */
class AutoRecordSmsTest {
    @Test fun understoodNewMessagesAreRecordedAndOnlyTheUnclearOnesWait() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", MART), sms("m3", UNCLEAR))
        val result = world.auto().run()
        assertEquals(AutoRecordStatus.RAN, result.status)
        assertEquals(2, result.recorded)
        assertEquals(listOf("m3"), result.waiting, "اللي ما اتفهمش بس بيستنى")
        assertEquals(listOf("m3"), world.queued(), "المسجّل اتشال من الصندوق والمرفوض فضل")
        val txns = world.spaces.single().all()
        assertEquals(txns.map { it.id }.toSet(), result.recordedTransactionIds.toSet(), "معرّفات اللمعة = اللي اتسجل فعلًا")
        assertEquals(setOf(2_500L, 4_000L), txns.map { it.amountMinor }.toSet())
        assertTrue(txns.all { it.walletId == BANK.id }, "أول محفظة بنك (زي الشاشة §36) — مش النقد اللي قبلها في القايمة")
    }

    @Test fun autoRecordIsNotAConfirmation() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", MART))
        world.auto().run()
        val txns = world.spaces.single().all().associateBy { it.amountMinor }
        val cafe = txns.getValue(2_500)
        assertEquals("cat-food", cafe.categoryId, "التصنيف من القاعدة")
        assertFalse(cafe.categoryConfirmed, "§36: اللي جه من قاعدة بيفضل مقترح")
        assertEquals(ReviewState.SUGGESTED, cafe.reviewState)
        val mart = txns.getValue(4_000)
        assertNull(mart.categoryId, "رد المالك ١: المحل اللي مالوش تصنيف بيتسجل «غير مصنف»")
        assertEquals(ReviewState.NEEDS_REVIEW, mart.reviewState)
    }

    @Test fun similarWaitsAndDuplicatesAreAcknowledgedWithoutRecording() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE))
        world.auto().run()
        world.receive(sms("m2", CAFE_TWIN))
        val second = world.auto().run()
        assertEquals(0, second.recorded, "شبه عملية موجودة ما بيتسجلش لوحده")
        assertEquals(listOf("m2"), second.waiting)
        assertEquals(1, world.spaces.single().all().size)
    }

    @Test fun runningAgainAndAgainRecordsEachMessageOnce() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", MART))
        repeat(3) { world.auto().run() }
        world.receive(sms("m1", CAFE)) // نفس الرسالة رجعت للصندوق (المزامنة لقطتها تاني)
        val again = world.auto().run()
        assertEquals(0, again.recorded)
        assertEquals(1, again.duplicates, "رجعت «مكررة» واتشالت")
        assertEquals(2, world.spaces.single().all().size)
        assertTrue(world.queued().isEmpty())
    }

    @Test fun twoRunsAtTheSameTimeRecordOnce() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", MART))
        val a = world.auto()
        val b = world.auto()
        coroutineScope {
            launch { a.run() }
            launch { b.run() }
        }
        assertEquals(2, world.spaces.single().all().size, "القفل: التانية بتستنى الأولى وبعدين تلاقي كله متسجل")
    }

    @Test fun screenAndBackgroundTogetherNeverDoubleRecord() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE))
        val space = world.spaces.single()
        val screen = ReviewSmsInbox(ReviewSmsInboxDeps(ManageSmsInbox(world.inbox, space.parse), ImportStatement(space.importDeps()), app.masroufy.memory.MemoryMerchantRepository(), space.categories, space.ids))
        screen.load(SmsReviewTarget(BANK.id, BANK.name))
        world.auto().run() // الخلفية سجّلت والشاشة لسه مفتوحة على نفس الرسالة
        runCatching { screen.recordAll(emptyMap(), emptyList()) }
        assertEquals(1, space.all().size)
    }

    @Test fun crashAfterSavingBeforeAcknowledgeDoesNotRecordTwice() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE))
        world.inbox.failAcks = 1
        assertFailsWith<IllegalStateException> { world.auto().run() }
        assertEquals(1, world.spaces.single().all().size, "اتحفظت")
        assertEquals(listOf("m1"), world.queued(), "بس ما اتشالتش من الصندوق")
        val retry = world.auto().run()
        assertEquals(0 to 1, retry.recorded to retry.duplicates)
        assertEquals(1, world.spaces.single().all().size, "ما اتسجلتش تاني")
        assertTrue(world.queued().isEmpty())
    }

    @Test fun crashInsideTheSaveLosesNothing() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", MART))
        val space = world.spaces.single()
        space.txns.failSaves = 1
        assertFailsWith<IllegalStateException> { world.auto().run() }
        assertTrue(space.all().isEmpty() && space.sources.listByAccountIdentity(BANK.name).isEmpty(), "وحدة العمل رجّعت كله")
        assertEquals(listOf("m1", "m2"), world.queued(), "الرسايل لسه في الصندوق")
        assertEquals(2, world.auto().run().recorded)
        assertEquals(2, space.all().size)
    }

    @Test fun storedWalletWinsAndAMissingWalletMeansWaiting() = runBlocking<Unit> {
        val other = Wallet("w-bank2", "بنك وهمي تاني", Currency.SAR, "bank", 0, "2026-01-01")
        val world = SmsWorld(listOf(SmsSpace(wallets = listOf(BANK, other)))).enable()
        val auto = world.auto()
        auto.chooseWallet("sa", other.id)
        assertEquals(other.id, auto.walletOf("sa"))
        assertFailsWith<IllegalArgumentException> { auto.chooseWallet("sa", "w-nope") }
        assertFailsWith<IllegalArgumentException> { auto.chooseWallet("eg", other.id) }
        world.receive(sms("m1", CAFE))
        auto.run()
        assertEquals(other.id, world.spaces.single().all().single().walletId)

        val noBank = SmsWorld(listOf(SmsSpace(wallets = listOf(CASH)))).enable()
        noBank.receive(sms("m1", CAFE))
        val r = noBank.auto().run()
        assertEquals(0, r.recorded, "مفيش محفظة بنك ⇒ ما بنخمّنش (النقد مش مكان رسايل البنك)")
        assertEquals(listOf("sa"), r.spacesWithoutWallet)
        assertEquals(listOf("m1"), r.waiting)
    }

    @Test fun offWhenReadingIsOffAndNothingWaits() = runBlocking<Unit> {
        val world = SmsWorld()
        world.receive(sms("m1", UNCLEAR))
        val r = world.auto().run()
        assertEquals(AutoRecordStatus.OFF, r.status)
        assertTrue(world.auto().waiting().messageIds.isEmpty(), "القراية مقفولة ⇒ مفيش «مستنية»")
    }

    @Test fun eachCountryRecordsItsOwnMessagesInItsCurrency() = runBlocking<Unit> {
        val egBank = Wallet("w-eg", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-01-01")
        val sa = SmsSpace()
        val eg = SmsSpace("eg", listOf(egBank), parse = ::parseEgyptBankSms)
        val world = SmsWorld(listOf(sa, eg)).enable()
        world.receive(sms("m1", CAFE), sms("m2", EG_CARD))
        val r = world.auto().run()
        assertEquals(2, r.recorded)
        assertTrue(r.waiting.isEmpty(), "رسالة مصر مرفوضة من قارئ السعودية بس اتسجلت في مصر ⇒ مش مستنية")
        assertEquals(Currency.SAR to 2_500L, sa.all().single().let { it.currency to it.amountMinor })
        assertEquals(Currency.EGP to 4_125L, eg.all().single().let { it.currency to it.amountMinor }, "بالجنيه — مش بالريال الافتراضي")
    }

    @Test fun laneRefusesAnImporterWithoutTransferDecisions() {
        assertFailsWith<IllegalArgumentException> {
            SmsLane.of("sa", SmsSpace().importDeps().copy(transferParties = null), ManageSmsInbox(SmsWorld().inbox, SmsSpace().parse), SmsSpace().wallets)
        }
    }
}
