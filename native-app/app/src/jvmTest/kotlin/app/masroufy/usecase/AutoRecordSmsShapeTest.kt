package app.masroufy.usecase

import app.masroufy.core.AlertKind
import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsShape
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.smsConfirmCandidate
import app.masroufy.core.uiText
import app.masroufy.memory.MemoryMerchantRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الجولة الرابعة — **«المفهومة» = شكل معروف** (OVERRIDES §72): التسجيل التلقائي بيسجّل الصف اللي شكله معروف (قالب بنك أو عنوان
 * البنك المركزي) ومن مرسل المالك فعّله بس. اللي اتفهم من كلمات عامة بيستنى تأكيدك (بسببه، وبيتحسب في التنبيه)، والأجنبي بيستنى (§75-12).
 * الشاشة بتعرض المستني جاهز و«سجّل الكل» بيأكده. كل الرسايل مخترعة (`SmsAutoFixture.kt`).
 */
class AutoRecordSmsShapeTest {
    private fun screenOf(world: SmsWorld, space: SmsSpace = world.spaces.single()) = ReviewSmsInbox(
        ReviewSmsInboxDeps(ManageSmsInbox(world.inbox, space.parse), ImportStatement(space.importDeps()), MemoryMerchantRepository(), space.categories, space.ids),
    )

    @Test fun knownShapeIsRecordedWhileKeywordOnlyAndForeignWait() = runBlocking<Unit> {
        // الرسايل نفسها: الأولى شكل معروف · التانية بتتقري بس من كلمات عامة · التالتة أجنبية مرفوضة ومعاها تفاصيلها
        assertEquals(SmsShape.KnownShape("alrajhi", "purchase"), assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", SENT_AT, CAFE), 1)).row.shape)
        assertEquals(SmsShape.KeywordFallback, assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", SENT_AT, KEYWORD_ONLY), 1)).row.shape)
        assertNotNull(assertIs<SmsParseResult.Rejected>(parseBankSms(BankSmsMessage("TESTBANK", SENT_AT, FOREIGN), 1)).foreign, "§75-12: مستنية ومعاها المبلغ الأجنبي")

        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", KEYWORD_ONLY), sms("m3", FOREIGN))
        val r = world.auto().run()
        assertEquals(1, r.recorded, "الشكل المعروف بس")
        assertEquals(listOf("m2", "m3"), r.waiting)
        assertEquals(listOf("m2"), r.unknownShape, "سبب انتظار الكلمات العامة")
        assertEquals(listOf("m2", "m3"), world.queued(), "المستني فضل في الصندوق — ما ضاعش")
        assertEquals(listOf(2_500L), world.spaces.single().all().map { it.amountMinor })

        repeat(2) { assertEquals(0, world.auto().run().recorded, "التشغيلات الجاية كمان ما بتسجلهاش") }
        assertEquals(listOf("m2", "m3"), world.queued())
    }

    @Test fun keywordOnlyCountsInTheConfirmAlertEvenBeforeTheRun() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", KEYWORD_ONLY))
        val waiting = world.auto().waiting()
        assertEquals(listOf("m2"), waiting.messageIds, "الشراء المعروف هيتسجل لوحده ⇒ مش مستني؛ الكلمات العامة مستنية")
        assertEquals(listOf("m2"), waiting.unknownShape)
        val alert = assertNotNull(smsConfirmCandidate(waiting.messageIds))
        assertEquals(AlertKind.SMS_CONFIRM, alert.kind)
        assertTrue(alert.title.contains("1"), alert.title)
        assertTrue(world.spaces.single().all().isEmpty(), "الحساب ما بيكتبش")
    }

    @Test fun theScreenOffersItPrefilledWithItsReasonAndOneTapRecordsIt() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", KEYWORD_ONLY))
        world.auto().run()
        val space = world.spaces.single()
        val screen = screenOf(world)
        val line = screen.load(SmsReviewTarget(BANK.id, BANK.name)).ready.single()
        assertEquals("m2", line.messageId)
        assertEquals(Triple(3_000L, Direction.OUT, "TEST SHOP"), Triple(line.amountMinor, line.direction, line.merchant), "جاهزة متعبّية")
        assertEquals(SmsShape.KeywordFallback, line.shape)
        assertEquals(uiText(TextKey.SMS_WAIT_UNKNOWN_SHAPE), line.confirmReason)
        assertEquals(1, screen.recordAll(emptyMap(), emptyList()), "ضغطة واحدة = تأكيد")
        assertTrue(world.queued().isEmpty())
        assertEquals(setOf(2_500L, 3_000L), space.all().map { it.amountMinor }.toSet())
        assertTrue(world.auto().waiting().messageIds.isEmpty())
    }

    @Test fun aClearLineOnTheScreenHasNoWaitReason() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE))
        val line = screenOf(world).load(SmsReviewTarget(BANK.id, BANK.name)).ready.single()
        assertTrue(line.shape.clear)
        assertNull(line.confirmReason)
    }

    /** حساب بنك واحد (المحفظة معروفة لأي مرسل): الرسالة المعروفة من مرسل مش مفعّل برضه ما بتتسجلش — بتستنى. */
    @Test fun aSenderTheOwnerDidNotEnableWaitsEvenWithOneBankAccount() = runBlocking<Unit> {
        val world = SmsWorld().enable() // TESTBANK بس · محفظة بنك واحدة
        world.receive(sms("m1", CAFE, sender = "OTHERBANK"), sms("m2", MART))
        val r = world.auto().run()
        assertEquals(1, r.recorded)
        assertEquals(listOf("m1"), r.waiting)
        assertEquals(listOf(4_000L), world.spaces.single().all().map { it.amountMinor })
        assertEquals(listOf("m1"), world.queued(), "ما ضاعتش")
    }

    @Test fun aSenderTheOwnerDidNotEnableIsNeverRecordedAutomatically() = runBlocking<Unit> {
        // حسابين بنك: المرسل المفعّل مربوط، والتاني لو اتعامل كمفعّل كان هيطلع «محتاج تختار محفظته»
        val world = SmsWorld(listOf(SmsSpace(wallets = listOf(BANK, BANK2)))).enable() // TESTBANK بس
        world.receive(sms("m1", CAFE, sender = "OTHERBANK"), sms("m2", MART))
        val auto = world.auto()
        auto.chooseWallet("sa", "TESTBANK", BANK.id)
        val r = auto.run()
        assertEquals(1, r.recorded, "رسالة TESTBANK بس")
        assertEquals(listOf("m1"), r.waiting, "شكل معروف بس من مرسل مش مفعّل ⇒ تستنى")
        assertTrue(r.unmappedSenders.isEmpty() && auto.unmappedSenders().isEmpty(), "مرسل مش مفعّل مش سؤال محفظة")
        assertTrue(r.unknownShape.isEmpty())
        assertEquals(listOf(4_000L), world.spaces.single().all().map { it.amountMinor })
    }

    @Test fun egyptRecordsAKnownSentenceAndKeepsAnInventedOneWaiting() = runBlocking<Unit> {
        assertEquals(SmsShape.KeywordFallback, assertIs<SmsParseResult.Ok>(parseEgyptBankSms(BankSmsMessage("TESTBANK", SENT_AT, EG_KEYWORD_ONLY), 1)).row.shape)
        val egBank = Wallet("w-eg", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-01-01")
        val eg = SmsSpace("eg", listOf(egBank), parse = ::parseEgyptBankSms)
        val world = SmsWorld(listOf(SmsSpace(), eg)).enable()
        world.receive(sms("m1", EG_CARD), sms("m2", EG_KEYWORD_ONLY))
        val r = world.auto().run()
        assertEquals(1, r.recorded)
        assertEquals(listOf("m2"), r.waiting)
        assertEquals(listOf("m2"), r.unknownShape)
        assertEquals(listOf(4_125L), eg.all().map { it.amountMinor })
    }

    /** وقع بعد ما سجّل المعروف وقبل الشيل: الشاشة بتشوف المعروف «مكرر» والكلمات العامة «جديدة» من نفس المحتوى — والتأكيد بيسجلها. */
    @Test fun aCrashAfterRecordingTheClearOneStillLetsTheScreenConfirmTheRest() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", KEYWORD_ONLY))
        world.inbox.failAcks = 1
        assertFailsWith<IllegalStateException> { world.auto().run() }
        assertEquals(listOf("m1", "m2"), world.queued())
        val space = world.spaces.single()
        val screen = screenOf(world)
        val view = screen.load(SmsReviewTarget(BANK.id, BANK.name))
        assertEquals(listOf("m1"), view.duplicates.map { it.messageId })
        assertEquals(listOf("m2"), view.ready.map { it.messageId })
        assertEquals(1, screen.recordAll(emptyMap(), emptyList()))
        assertTrue(world.queued().isEmpty())
        assertEquals(setOf(2_500L, 3_000L), space.all().map { it.amountMinor }.toSet())
    }
}
