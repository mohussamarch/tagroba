package app.masroufy.ui.shell.ask

import app.masroufy.core.AskKey
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistSpeaker
import app.masroufy.core.AssistTab
import app.masroufy.core.CardState
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.RecurringItem
import app.masroufy.core.ReviewState
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.emptyProfile
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.memory.memoryAssistantStores
import app.masroufy.ui.app.AskDeps
import app.masroufy.usecase.AddTransaction
import app.masroufy.usecase.AddTransactionDeps
import app.masroufy.usecase.AssistContext
import app.masroufy.usecase.AssistLexiconSource
import app.masroufy.usecase.AssistantDeps
import app.masroufy.usecase.AssistantSources
import app.masroufy.usecase.AssistantSuite
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * شاشة المساعد فوق المحرك الحقيقي (مستودعات الذاكرة — بيانات مخترعة): الشاشة بتعرض اللي المحرك رجّعه بس — الكروت والاختيارات والبداية
 * و«×» و«تراجع» والسجل و«اللي اتعلمته عنك». النهارده 2026-10-10، الراتب 28، فاتورة كهربا ميعادها 2026-10-03 ما اتسجلتش.
 */
class AskPresenterTest {
    private val space = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 500_000, "2026-01-01")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 50_000, "2026-01-01")
    private val coffee = Category("c-coffee", null, "قهوة", "coffee", "#000", "#fff", true, 1)
    private val elec = RecurringItem("r-elec", "فاتورة الكهرباء", "name:كهرباء", "bill", 1, 38_000, Currency.SAR, "2026-10-03", true, true)
    private val clock = FixedClock("2026-10-10T09:00:00.000Z")
    private val ids = SequentialIdGenerator()
    private val wallets = MemoryWalletRepository(listOf(bank, cash))
    private val txns = MemoryTransactionRepository(emptyList())
    private val stores = memoryAssistantStores()
    private val categories = MemoryCategoryRepository(listOf(coffee))
    private val deps = AssistantDeps(
        stores = stores,
        sources = AssistantSources(wallets, txns, MemoryProfileRepository(emptyProfile().copy(payday = 28, displayName = "محمد"))),
        lexicon = AssistLexiconSource(categories = categories, wallets = wallets, recurring = MemoryRecurringRepository(listOf(elec))),
        ids = ids, clock = clock, add = AddTransaction(AddTransactionDeps(txns, wallets, ids, clock)),
    )
    private val ask = object : AskDeps {
        val suite = AssistantSuite(deps)
        override suspend fun suite() = suite
        override suspend fun context(tab: AssistTab) = AssistContext("2026-10-10T09:00:00.000Z", "2026-10-10", 12, space, tab)
    }
    private val p = AskPresenter(ask) { AssistTab.HOME }

    @Test fun startCardsComeFromTheEngineAndEveryCardCloses() = runBlocking<Unit> {
        p.open()
        val v = assertNotNull(p.view)
        assertTrue(v.greeting.contains("محمد"), "التحية من المحرك بالاسم")
        val bill = v.start!!.items.single()
        assertTrue(bill.text.contains("فاتورة الكهرباء"))

        p.closeStart(bill)
        assertTrue(p.view!!.start!!.items.isEmpty(), "«×» قفل الكارت حتى لو الفاتورة ما اتسجلتش")
        assertEquals(uiText(TextKey.ASSIST_NOTHING_WAITING), p.view!!.startNote, "كلهم اتقفلوا ⇒ «مفيش حاجة مستنياك دلوقتي»")
        assertIs<AskUndo.StartCard>(p.undo)
        assertTrue(stores.forgotten.listAll().any { it.factKey == "card:${bill.key}" }, "متخزن على الحساب")

        p.runUndo()
        assertEquals(listOf(bill.key), p.view!!.start!!.items.map { it.key }, "«تراجع» رجّعه")
        assertNull(p.undo)
    }

    @Test fun typedExpenseAsksTheWalletOnceThenTheCardSavesThroughAddTransaction() = runBlocking<Unit> {
        p.open()
        assertTrue(p.send("قهوة 15"))
        val pick = p.messages.last()
        assertEquals(AssistMessageKind.WALLET_PICK, pick.kind)
        assertEquals(listOf("بنك وهمي", "الكاش"), pick.options.map { it.label })

        p.pick(pick.id, cash.id)
        val card = p.messages.last { it.kind == AssistMessageKind.TXN_CARD }
        assertEquals(1_500L, card.card!!.amountMinor)
        assertEquals("الكاش", card.card!!.walletName)

        p.edit(card.id)
        assertEquals(uiText(AskKey.CHAT_EDIT_HOW), p.messages.last().text, "«عدّل» ⇒ «تعدّل إيه؟» والكارت لسه مستني")
        p.confirm(card.id)
        assertEquals(CardState.DONE, p.messages.first { it.id == card.id }.state)
        assertEquals(listOf(1_500L), all().map { it.amountMinor })
        assertFalse(p.busy)
    }

    @Test fun easternDigitsInTheChatBecomeWesternBeforeTheEngineReadsThem() = runBlocking<Unit> {
        p.open()
        assertTrue(p.send("قهوة ١٥"))
        assertEquals("قهوة 15", p.messages.last { it.from == AssistSpeaker.ME }.text, "اللي اتكتب بيتحفظ بـ0-9 (OVERRIDES §79 — L5)")
        p.pick(p.messages.last().id, cash.id)
        assertEquals(1_500L, p.messages.last { it.kind == AssistMessageKind.TXN_CARD }.card!!.amountMinor, "۱۵ فارسي أو ١٥ عربي = 15")
    }

    @Test fun similarSameDayCardLetsTheOwnerKeepTheExistingOne() = runBlocking<Unit> {
        txns.saveMany(listOf(sameDay("t-sms", 2_500)))
        stores.settings.save(app.masroufy.core.UserSetting.MainWallet(space.id, bank.id, app.masroufy.core.MainWalletSource.ADD_SHEET, clock.nowIso()))
        p.open()
        p.send("قهوة 25")
        val card = p.messages.last { it.kind == AssistMessageKind.TXN_CARD }
        assertEquals("t-sms", card.card!!.similarTransactionId)
        p.keep(card.id)
        assertEquals(CardState.CANCELLED, p.messages.first { it.id == card.id }.state)
        val reply = p.messages.last()
        assertEquals(uiText(AskKey.CHAT_SIMILAR_KEPT), reply.text)
        assertEquals(AssistScreen.OPERATION_DETAIL, reply.links.single().screen)
        assertEquals(1, all().size, "ما اتسجلش تاني")
    }

    @Test fun newConversationGoesToHistoryAndDeleteWaitsForUndo() = runBlocking<Unit> {
        p.open()
        assertFalse(p.newConversation(), "فاضية ⇒ الزرار معطّل")
        p.send("كم صرفت هذا الشهر")
        assertTrue(p.newConversation())
        assertTrue(p.view!!.messages.isEmpty())
        p.showHistory()
        val row = p.history.single()
        assertEquals("كم صرفت هذا الشهر", row.conversation.title)

        p.openPast(row.conversation.id)
        assertEquals(2, p.messages.size, "سؤالك ورده")
        p.backToCurrent()
        assertTrue(p.messages.isEmpty())

        p.deleteConversation(row)
        assertTrue(p.history.isEmpty(), "اتشالت من القايمة")
        p.runUndo()
        p.showHistory()
        assertEquals(1, p.history.size, "«تراجع» قبل الـ٤ ثواني ⇒ ما اتمسحتش")

        p.deleteConversation(p.history.single())
        p.expireUndo()
        assertTrue(stores.conversations.listAll().none { it.messageCount > 0 }, "بعد الوقت اتمسحت فعلًا")
    }

    @Test fun memoryPanelLearningSwitchAndUnknownQuestionsWithUndo() = runBlocking<Unit> {
        p.open()
        p.setLearning(false)
        assertFalse(p.memory!!.learningOn)
        p.send("زقزق بلبل طار")
        p.showHistory()
        assertEquals(1, p.unknownCount, "السؤال اللي ما اتفهمش بيتحفظ حتى والتعلّم مقفول (§79.2-2)")
        val q = p.unknown.single()

        p.removeUnknown(q)
        assertEquals(0, p.unknownCount)
        assertIs<AskUndo.Unknown>(p.undo)
        p.runUndo()
        assertEquals(1, p.unknownCount)
        assertTrue(p.unknownText().contains("زقزق"))
    }

    private suspend fun all() = txns.listByDateRange("2000-01-01", "2100-01-01")

    private fun sameDay(id: String, minor: Long) = Transaction(
        id = id, occurredAt = "2026-10-10", datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "2026-10-10T08:00:00.000Z", updatedAt = "2026-10-10T08:00:00.000Z",
        walletId = bank.id,
    )
}
