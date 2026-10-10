package app.masroufy.wiring

import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistTab
import app.masroufy.core.AssistantReply
import app.masroufy.core.CardState
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Space
import app.masroufy.core.Wallet
import app.masroufy.core.emptyProfile
import app.masroufy.memory.MemoryAccount
import app.masroufy.usecase.LoadOnlineFeeds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * المساعد متجمّع فوق نفس مستودعات البلد (الذاكرة هنا، فايربيز على الجوال): بيفتح · بيفهم «قهوة 15» · بيسأل «بتصرف عادةً منين؟» مرة · بيسجّل
 * بـ`AddTransaction` العادية · و«معك الآن» بينقص بنفس المبلغ. بيانات وهمية بس.
 */
class AssistantWiringTest {
    private val saudi = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
    private val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-02-01T00:00:00.000Z")
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2026-01-01")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 20_000, "2026-01-01")
    private val food = Category("c-food", null, "مطاعم", "utensils", "#A36A21", "#E0B070", active = true, order = 1, groupKey = "food")
    private val saRepos = memorySpaceRepositories(listOf(bank, cash), listOf(food), profile = emptyProfile().copy(payday = 28))
    private val egRepos = memorySpaceRepositories()
    private val env = memoryEnv()
    private val session = object : SessionLinks {
        override val account = MemoryAccount()
        override fun spaces() = listOf(saudi to saRepos, egypt to egRepos)
        override fun switchSpace(spaceId: String): Boolean = true
    }
    private val graph = SpaceGraph(saudi, saRepos, env, session, LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis))

    @Test fun contextComesFromTheDeviceAndTheSession() = runBlocking<Unit> {
        val ctx = graph.ask.context(AssistTab.PEOPLE)
        assertEquals("2026-10-09", ctx.today)
        assertEquals(9, ctx.hour)
        assertEquals(saudi, ctx.space)
        assertEquals(AssistTab.PEOPLE, ctx.tab)
        assertEquals(2, ctx.spaceCount, "التحويل بين البلدين محتاج يعرف إن فيه بلد تانية")
    }

    @Test fun typedExpenseAsksTheMainWalletOnceThenRecordsThroughAddTransaction() = runBlocking<Unit> {
        val suite = graph.ask.suite()
        val ctx = graph.ask.context(AssistTab.HOME)
        val start = suite.chat.open(ctx)
        assertTrue(start.messages.isEmpty())
        assertNotNull(start.start, "محادثة جديدة ⇒ «أمور لم تُنجزها بعد»")

        val asked = suite.chat.send("قهوة 15", ctx)
        val pick = asked.messages.last()
        assertEquals(AssistMessageKind.WALLET_PICK, pick.kind, "مفيش محفظة أساسية ⇒ «بتصرف عادةً منين؟»")
        assertEquals(listOf(bank.id, cash.id), pick.options.map { it.id })

        val carded = suite.chat.pick(pick.id, cash.id, ctx)
        val main = app.masroufy.usecase.MainSpendingWallets(saRepos.assistant.settings, saRepos.wallets, saudi.id, env.clock)
        assertEquals(cash.id, main.get()?.id, "الاختيار بقى الأساسية للسعودية")
        val card = carded.messages.last { it.kind == AssistMessageKind.TXN_CARD }
        assertEquals(CardState.PENDING, card.state)
        assertEquals(1_500L, card.card?.amountMinor)

        val before = graph.shell.withYouNow().totalMinor!!
        val done = suite.chat.confirm(card.id, ctx)
        assertEquals(CardState.DONE, done.messages.first { it.id == card.id }.state)
        assertEquals(before - 1_500L, graph.shell.withYouNow().totalMinor, "نفس الرقم في «معك الآن»")
        assertNull(egRepos.assistant.settings.listAll().firstOrNull(), "مخازن مصر منفصلة في الاختبار")
    }

    /** §78 ٢: إعداد واحد على الحساب لكل بلد — لوحة «+» و«اجعلها الأساسية» وعلامة «الأساسية» والمساعد كلهم بيقروا نفس المكان. */
    @Test fun mainWalletIsOneSettingForTheAddSheetTheWalletScreensAndTheAssistant() = runBlocking<Unit> {
        val d = graph.shell.addWalletDefault()
        assertTrue(d.needsAsk, "لسه ⇒ «بتصرف عادةً منين؟» والحفظ مقفول")
        val access = assertNotNull(graph.more.mainWallet, "تفاصيل المحفظة متوصّلة")
        assertNull(access.current())

        access.set(bank.id)
        assertEquals(bank.id, access.current(), "علامة «الأساسية» في القايمة")
        assertEquals(bank.id, graph.shell.addWalletDefault().wallet?.id, "لوحة «+» بتختارها لوحدها")
        val card = graph.ask.suite().chat.send("قهوة 15", graph.ask.context(AssistTab.HOME)).messages.last()
        assertEquals(AssistMessageKind.TXN_CARD, card.kind, "المساعد ما سألش تاني")
        assertEquals(bank.id, card.card?.walletId)

        val eg = SpaceGraph(egypt, egRepos, env, session, LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis))
        assertTrue(eg.shell.addWalletDefault().needsAsk, "مصر ليها أساسيتها لوحدها")
        graph.shell.setMainWallet(cash.id)
        assertEquals(cash.id, access.current(), "أول صرف من اللوحة بيغيّرها")
    }

    /** «×» في الجرس أو الصفحة (رد المالك ٣): على الحساب — العدّ ونقطة التبويب بيروحوا، و«تراجع» بيرجّعه زي ما كان. */
    @Test fun bellDeleteIsStoredOnTheAccountAndClearsTheTabDot() = runBlocking<Unit> {
        val kind = app.masroufy.core.AlertKind.DUE_OVERDUE
        saRepos.alertInbox.save(
            app.masroufy.port.AlertInboxEntry(
                "due|debt|p-1|2026-10-01|receive", "due|debt|p-1|2026-10-01|receive|due_overdue", kind, app.masroufy.core.DueFlow.RECEIVE, "دين فات ميعاده", "تفاصيل",
                app.masroufy.core.AlertDecision(kind, app.masroufy.core.AlertDelivery.INBOX_ONLY, null, false, emptyList()), env.clock.nowIso(),
            ),
        )
        assertEquals(setOf(app.masroufy.ui.nav.Tab.PEOPLE), graph.shell.bell().dots)
        val dismissals = app.masroufy.ui.screens.home.Dismissals()
        dismissals.dropAndSave("due|debt|p-1|2026-10-01|receive", graph.shell)
        val after = graph.shell.bell()
        assertTrue(after.items.isEmpty() && after.dots.isEmpty() && after.unread == 0)
        assertEquals(1, saRepos.assistant.alertDismissals!!.listAll().size, "العلامة على الحساب")

        dismissals.undoAndSave(graph.shell)
        assertEquals(1, graph.shell.bell().items.size, "«تراجع» رجّعه")
        assertTrue(saRepos.assistant.alertDismissals!!.listAll().isEmpty())
    }

    /** رد المالك §79.2-5: «فاضلي كام؟» بالرقمين — الباقي من سقف الشهر + اللي معاك تقريبًا لحد المرتب (من نفس حسبة الرئيسية). */
    @Test fun remainingAnswersBothTheCapLeftAndRoughlyUntilPayday() = runBlocking<Unit> {
        val period = app.masroufy.core.periodForDate("2026-10-09", 28)
        saRepos.budgets.save(app.masroufy.core.Budget("b-1", period.key, period.start, period.end, 200_000, 80, env.clock.nowIso(), env.clock.nowIso()))
        val suite = graph.ask.suite()
        val view = suite.chat.send("فاضلي كام", graph.ask.context(AssistTab.HOME))
        assertIs<AssistantReply.Text>(view.messages.last().reply)
        val text = view.messages.last().text
        val capLeft = app.masroufy.core.uiText(app.masroufy.core.TextKey.ASSIST_REMAINING_CAP, "X", "Y").substringBefore("X")
        val untilPayday = app.masroufy.core.uiText(app.masroufy.core.AskKey.CHAT_UNTIL_SALARY, "X", "Y").substringBefore("X")
        assertTrue(text.contains(capLeft) && text.contains(untilPayday), text)
    }
}
