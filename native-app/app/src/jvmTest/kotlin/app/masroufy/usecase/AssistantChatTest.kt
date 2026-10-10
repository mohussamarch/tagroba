package app.masroufy.usecase

import app.masroufy.core.AlertDismissal
import app.masroufy.core.ArabicVariant
import app.masroufy.core.AssistChoiceKind
import app.masroufy.core.AssistIntent
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistSpeaker
import app.masroufy.core.CardState
import app.masroufy.core.ChipRef
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Language
import app.masroufy.core.MainWalletSource
import app.masroufy.core.StartItemKind
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.billAlertThread
import app.masroufy.core.uiText
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** المحادثة على بيانات مخترعة: الإضافة بالكتابة والمحفظة الأساسية (رد المالك ٢) · التعديل والإلغاء · التقسيم · عمر المحادثة (رد المالك ١) · البداية · الاقتراحات · «مش فاهم». */
class AssistantChatTest {
    @BeforeTest fun egyptianTexts() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
    }

    private val w = AssistantWorld()
    private val ctx = w.ctx()

    private fun ChatView.last(kind: AssistMessageKind): AssistMessage = messages.last { it.kind == kind }

    @Test fun firstQuickAddAsksTheMainWalletOnceThenRemembersIt() = runBlocking<Unit> {
        val v = w.chat.send("قهوة ١٥", ctx)
        val ask = v.last(AssistMessageKind.WALLET_PICK)
        assertEquals(uiText(TextKey.ASSIST_ASK_MAIN_WALLET), ask.text)
        assertEquals(listOf("w-bank", "w-cash"), ask.options.map { it.id }, "حساب البنك والكاش")
        val picked = w.chat.pick(ask.id, "w-bank", ctx)
        assertEquals("w-bank", MainSpendingWallets(w.stores.settings, w.wallets, DEFAULT_SPACE_ID, w.clock).get()?.id)
        val card = picked.last(AssistMessageKind.TXN_CARD).card!!
        assertEquals(1_500L, card.amountMinor)
        assertEquals("w-bank", card.walletId)
        assertEquals(w.coffeeId, card.categoryId)
        val done = w.chat.confirm(picked.last(AssistMessageKind.TXN_CARD).id, ctx)
        val saved = done.last(AssistMessageKind.TXN_CARD)
        assertEquals(CardState.DONE, saved.state)
        val tx = w.txns.findByIds(listOf(saved.card!!.transactionId!!)).single()
        assertEquals(1_500L, tx.amountMinor)
        assertEquals("w-bank", tx.walletId)
        // التانية: من غير سؤال
        val second = w.chat.send("قهوة 20", ctx)
        assertEquals("w-bank", second.last(AssistMessageKind.TXN_CARD).card!!.walletId)
        assertEquals(1, second.messages.count { it.kind == AssistMessageKind.WALLET_PICK })
    }

    @Test fun walletNamedInTheTextWinsOverTheMainWallet() = runBlocking<Unit> {
        MainSpendingWallets(w.stores.settings, w.wallets, DEFAULT_SPACE_ID, w.clock).set("w-bank", MainWalletSource.WALLET_DETAIL)
        val v = w.chat.send("بقالة 50 كاش", ctx)
        assertEquals("w-cash", v.last(AssistMessageKind.TXN_CARD).card!!.walletId)
    }

    @Test fun editThenCancelWritesNothing() = runBlocking<Unit> {
        MainSpendingWallets(w.stores.settings, w.wallets, DEFAULT_SPACE_ID, w.clock).set("w-bank", MainWalletSource.CHAT)
        val before = w.txns.listByDateRange("2000-01-01", "2100-01-01").size
        w.chat.send("قهوة 15", ctx)
        val edited = w.chat.send("لا خليها ٢٠", ctx)
        val cards = edited.messages.filter { it.kind == AssistMessageKind.TXN_CARD }
        assertEquals(CardState.DROPPED, cards.first().state)
        assertEquals(2_000L, cards.last().card!!.amountMinor)
        val cancelled = w.chat.send("لا", ctx)
        assertEquals(CardState.CANCELLED, cancelled.last(AssistMessageKind.TXN_CARD).state)
        assertEquals(before, w.txns.listByDateRange("2000-01-01", "2100-01-01").size)
    }

    @Test fun foreignCurrencyAndFutureDayGetAnHonestQuestionNoCard() = runBlocking<Unit> {
        listOf("قهوة 15 دولار", "قهوة 15 بكرة").forEach { t ->
            val v = w.chat.send(t, ctx)
            assertTrue(v.messages.none { it.kind == AssistMessageKind.TXN_CARD || it.kind == AssistMessageKind.WALLET_PICK }, t)
        }
    }

    @Test fun splitWithAKnownPersonLinksTheirShare() = runBlocking<Unit> {
        MainSpendingWallets(w.stores.settings, w.wallets, DEFAULT_SPACE_ID, w.clock).set("w-bank", MainWalletSource.CHAT)
        val v = w.chat.send("قسّم 300 مع أحمد", ctx)
        val card = v.last(AssistMessageKind.SPLIT_CARD)
        assertEquals(listOf(15_000L, 15_000L), card.split!!.shares.map { it.amountMinor })
        assertEquals("p-ahmed", card.split!!.shares[1].personId)
        w.chat.confirm(card.id, ctx)
        assertEquals(15_000L, w.obligations.listByPerson("p-ahmed").single().originalMinor)
    }

    @Test fun splitWithANewNameAsksBeforeAdding() = runBlocking<Unit> {
        MainSpendingWallets(w.stores.settings, w.wallets, DEFAULT_SPACE_ID, w.clock).set("w-bank", MainWalletSource.CHAT)
        val v = w.chat.send("قسّم 200 مع ياسر", ctx)
        val ask = v.last(AssistMessageKind.CHOICE)
        assertEquals(AssistChoiceKind.ADD_PERSON, ask.choice)
        val after = w.chat.pick(ask.id, OPT_YES, ctx)
        assertTrue(w.personRepo.listAll().any { it.name == "ياسر" })
        assertEquals(listOf(10_000L, 10_000L), after.last(AssistMessageKind.SPLIT_CARD).split!!.shares.map { it.amountMinor })
    }

    /** رد المالك ١: المحادثة بتخلص بعد ساعة من غير رسايل؛ «محادثة جديدة» بتبدأ واحدة والقديمة في السجل. */
    @Test fun conversationEndsAfterAnIdleHour() = runBlocking<Unit> {
        val first = w.chat.send("كم صرفت هذا الشهر؟", w.ctx("2026-10-10T09:00:00.000Z"))
        assertEquals(first.conversation!!.id, w.chat.open(w.ctx("2026-10-10T09:59:00.000Z")).conversation?.id)
        val later = w.chat.open(w.ctx("2026-10-10T10:01:00.000Z"))
        assertNull(later.conversation)
        assertTrue(later.messages.isEmpty())
        assertEquals(listOf(first.conversation!!.id), AssistantHistory(w.deps).list("2026-10-10").map { it.conversation.id })
        val fresh = w.chat.newConversation(w.ctx("2026-10-10T10:02:00.000Z"))
        assertEquals(0, fresh.conversation!!.messageCount)
        assertEquals(1, AssistantHistory(w.deps).list("2026-10-10").size, "الفاضية مش في السجل")
        val typed = w.chat.send("محادثة جديدة", w.ctx("2026-10-10T10:03:00.000Z"))
        assertEquals(0, typed.conversation!!.messageCount)
    }

    /** «أمور لم تُنجزها بعد»: الفاتورة اللي عدّى يومها + التصنيف عند حد التنبيه؛ الإشعار الممسوح من الجرس أو «×» بيخفيه. */
    @Test fun startItemsRespectDeletedNotificationsAndClosedCards() = runBlocking<Unit> {
        val start = w.chat.open(ctx).start!!
        assertEquals(listOf(StartItemKind.BILL_UNRECORDED, StartItemKind.CATEGORY_AT_THRESHOLD), start.items.map { it.kind })
        w.stores.alertDismissals!!.save(AlertDismissal(billAlertThread("r-elec", "2026-10-03", DEFAULT_SPACE_ID), ctx.nowIso))
        assertEquals(listOf(StartItemKind.CATEGORY_AT_THRESHOLD), w.chat.open(ctx).start!!.items.map { it.kind })
        ManageStartCards(w.stores.forgotten, w.clock).close(start.items[1].key)
        val none = w.chat.open(ctx)
        assertTrue(none.start!!.items.isEmpty())
        assertEquals(uiText(TextKey.ASSIST_NOTHING_WAITING), none.startNote)
    }

    /** موضوع اتسأل مرتين ⇒ تحت «بتسأل عنها كتير» بالترتيب (الأكتر الأول)؛ بعد أول رسالة ⇒ أسئلة متابعة. */
    @Test fun frequentTopicsBecomeOpeningChips() = runBlocking<Unit> {
        repeat(2) { w.chat.send("كم صرفت هذا الشهر؟", ctx) }
        repeat(3) { w.chat.send("فاضلي كام؟", ctx) }
        val bar = w.chat.newConversation(ctx).chips
        assertEquals(uiText(TextKey.ASSIST_CHIPS_FREQUENT), bar.label)
        assertEquals(listOf(AssistIntent.REMAINING.wire, AssistIntent.SPEND_TOTAL.wire), bar.chips.filter { it.learned }.map { it.ref.topic })
        assertTrue(bar.chips.size <= 6)
        val after = w.chat.ask(ChipRef.of(AssistIntent.SPEND_TOTAL), ctx)
        assertEquals(uiText(TextKey.ASSIST_CHIPS_FOLLOW), after.chips.label)
        assertEquals(AssistIntent.SPEND_BIGGEST.wire, after.chips.chips.first().ref.topic)
    }

    /** رد المالك §79.2-2: المفتاح بيوقف المواضيع والاقتراحات بس — السؤال اللي ما اتفهمش **بيفضل يتحفظ** عشان نعلّمه. */
    @Test fun learningOffCountsNothingButStillSavesUnknownQuestions() = runBlocking<Unit> {
        AssistantMemory(w.deps).setLearning(false)
        repeat(3) { w.chat.send("فاضلي كام؟", ctx) }
        w.chat.send("كوكو واوا", ctx)
        assertTrue(w.stores.topics.listAll().isEmpty())
        assertEquals(listOf("كوكو واوا"), w.stores.unknown.listAll().map { it.text })
        assertTrue(w.chat.newConversation(ctx).chips.chips.none { it.learned })
    }

    /** «مش فاهم» عمره ما بيدّعي إجابة: جملة صريحة + لحد ٣ شاشات، والسؤال بيتحفظ (نفس الشكل الموحّد = سطر واحد بعدّاد). */
    @Test fun unknownNeverPretends() = runBlocking<Unit> {
        val v = w.chat.send("عايز أعرف أخبار السوق", ctx)
        val reply = v.messages.last()
        assertEquals(AssistMessageKind.LINKS, reply.kind)
        assertTrue(reply.links.size in 1..3, "${reply.links}")
        assertTrue(reply.text.none(Char::isDigit))
        w.chat.send("عايز اعرف اخبار السوق", ctx)
        assertEquals(2, w.stores.unknown.listAll().single().askCount)
    }

    /** رد المالك ٣: الإشعار الممسوح المحرك ما بيرجّعوش طول ما موضوعه شغال، ولما الموضوع يخلص العلامة بتتشال. */
    @Test fun alertEngineDoesNotBringBackADeletedNotification() = runBlocking<Unit> {
        val inbox = app.masroufy.memory.MemoryAlertInbox()
        val dismissals = app.masroufy.memory.MemoryAlertDismissalStore()
        val engine = RunAlertEngine(
            AlertEngineDeps(
                app.masroufy.memory.MemoryAlertSettings(), app.masroufy.memory.MemoryAlertInteractions(), app.masroufy.memory.MemoryUsualHours(),
                app.masroufy.memory.MemoryAlertReceipts(), inbox, w.clock, dismissals,
            ),
        )
        val c = app.masroufy.core.AlertCandidate(app.masroufy.core.AlertKind.entries.first { !it.needsServer }, "budget|x", "عنوان", "تفاصيل")
        engine.run(listOf(c), app.masroufy.core.LocalMoment("2026-10-10", 14))
        assertEquals(listOf("budget|x"), inbox.listAll().map { it.threadKey })
        ManageAlertDismissals(dismissals, inbox, w.clock).dismiss("budget|x")
        engine.run(listOf(c), app.masroufy.core.LocalMoment("2026-10-10", 15))
        assertTrue(inbox.listAll().isEmpty())
        engine.run(emptyList(), app.masroufy.core.LocalMoment("2026-10-11", 15))
        assertTrue(dismissals.listAll().isEmpty())
    }

    @Test fun deletedBellNotificationCanBeUndone() = runBlocking<Unit> {
        val inbox = app.masroufy.memory.MemoryAlertInbox()
        val entry = app.masroufy.port.AlertInboxEntry(
            "t-1", "e-1", app.masroufy.core.AlertKind.entries.first(), app.masroufy.core.DueFlow.PAY, "عنوان", "تفاصيل",
            app.masroufy.core.AlertDecision(app.masroufy.core.AlertKind.entries.first(), app.masroufy.core.AlertDelivery.entries.first(), null, false, emptyList()), ctx.nowIso,
        )
        inbox.save(entry)
        val manage = ManageAlertDismissals(w.stores.alertDismissals!!, inbox, w.clock)
        assertTrue(manage.hasUnread())
        val gone = manage.dismiss("t-1")
        assertTrue(inbox.listAll().isEmpty())
        assertTrue("t-1" in manage.dismissedThreads())
        manage.undo(gone)
        assertNotNull(inbox.listAll().singleOrNull())
        assertTrue(manage.dismissedThreads().isEmpty())
    }
}
