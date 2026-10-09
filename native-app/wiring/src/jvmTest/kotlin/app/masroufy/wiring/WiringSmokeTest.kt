package app.masroufy.wiring

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.LocalMoment
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.emptyProfile
import app.masroufy.core.Wallet
import app.masroufy.core.smsConfirmCandidate
import app.masroufy.core.uiText
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryDeviceNotifier
import app.masroufy.memory.MemoryHttpText
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.ui.nav.Tab
import app.masroufy.usecase.AddKind
import app.masroufy.usecase.AddOperationDraft
import app.masroufy.usecase.AddOperationResult
import app.masroufy.usecase.AutoRecordStatus
import app.masroufy.usecase.FEEDS_BASE_URL
import app.masroufy.usecase.FeedState
import app.masroufy.usecase.LoadOnlineFeeds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * التجميع كله على مستودعات الذاكرة (نفس اللي الجوال بيعمله فوق فايربيز): الهيكل والمناطق بتتبني، وحالات الاستخدام بتشتغل من آخرها لآخرها —
 * «معك الآن» · لوحة «+» · البلاد والتبديل · الجرس والنقط · التقويم · الأسعار من النت · دورة الخلفية. بيانات وهمية بس.
 */
class WiringSmokeTest {
    private val saudi = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
    private val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-02-01T00:00:00.000Z")
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2026-01-01")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 20_000, "2026-01-01")
    private val food = Category("c-food", null, "مطاعم", "utensils", "#A36A21", "#E0B070", active = true, order = 1, groupKey = "food")
    private val saRepos = memorySpaceRepositories(listOf(bank, cash), listOf(food), profile = null)
    private val egRepos = memorySpaceRepositories()
    private val http = MemoryHttpText().also {
        it[FEEDS_BASE_URL + "prices.json"] = """{"generatedAt":"2026-10-08T08:00:00Z","baseCurrency":"SAR","prices":{"GOLD_24K_GRAM":{"name":"ذهب","unit":"جرام","pricePerUnitMinor":40000,"asOf":"2026-10-08","source":"مصدر وهمي"}}}"""
    }
    private val env = memoryEnv(http = http)
    private val switched = mutableListOf<String>()
    private val session = object : SessionLinks {
        override val account = MemoryAccount()
        override fun spaces() = listOf(saudi to saRepos, egypt to egRepos)
        override fun switchSpace(spaceId: String): Boolean = switched.add(spaceId)
    }
    private val feeds = LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis)
    private val graph = SpaceGraph(saudi, saRepos, env, session, feeds)

    @Test fun everyAreaIsWired() {
        listOf(graph.shell, graph.home, graph.operations, graph.imports, graph.people, graph.dues, graph.investment, graph.more, graph.onboarding)
            .forEach { assertNotNull(it) }
        assertEquals(saudi, graph.space)
    }

    @Test fun withYouNowAndQuickAddGoThroughTheUseCases() = runBlocking<Unit> {
        val shell = graph.shell
        assertEquals(120_000L, shell.withYouNow().totalMinor)
        val options = shell.addOptions()
        assertEquals(listOf("مطاعم"), options.categories.map { it.name })
        assertEquals(2, options.wallets.size)

        val spent = shell.addOperation(AddOperationDraft(AddKind.OUT, "42.50", bank.id, categoryId = food.id, name = food.name))
        assertIs<AddOperationResult.Saved>(spent)
        assertEquals(4_250L, spent.transaction.amountMinor, "المبلغ اتقرى بالهللة في حالة الاستخدام")
        assertEquals(115_750L, shell.withYouNow().totalMinor)

        val bad = shell.addOperation(AddOperationDraft(AddKind.OUT, "abc", bank.id, categoryId = food.id))
        assertEquals(AddOperationResult.Invalid(uiText(TextKey.ADD_AMOUNT_INVALID)), bad)

        // تحويل بين محافظي: المجموع ما بيتغيرش، والكاش زاد
        assertIs<AddOperationResult.Saved>(shell.addOperation(AddOperationDraft(AddKind.MOVE, "١٠٠", bank.id, toWalletId = cash.id)))
        val now = shell.withYouNow()
        assertEquals(115_750L, now.totalMinor)
        assertEquals(30_000L, now.cashMinor)
    }

    @Test fun spacesAndSwitching() = runBlocking<Unit> {
        val choices = graph.shell.spaces()
        assertEquals(listOf("sa", "eg"), choices.map { it.space.id })
        assertTrue(choices.first().active)
        assertEquals(120_000L, choices.first().withYouNowMinor)
        assertNull(choices.last().withYouNowMinor, "بلد من غير محافظ ⇒ غير متاح")
        assertTrue(graph.shell.switchSpace("eg"))
        assertEquals(listOf("eg"), switched)
    }

    @Test fun bellCountsUnreadAndDotsTheTabsUntilMarkedRead() = runBlocking<Unit> {
        val shell = graph.shell as ShellGraph
        assertEquals(0, shell.bell().unread)
        shell.engine.run(listOfNotNull(smsConfirmCandidate(listOf("sms-1"))), LocalMoment("2026-10-09", 9))
        val bell = shell.bell()
        assertEquals(1, bell.unread)
        assertEquals(setOf(Tab.OPERATIONS), bell.dots, "رسايل البنك ⇒ نقطة على العمليات")
        shell.markAllRead()
        val read = shell.bell()
        assertEquals(0, read.unread)
        assertTrue(read.dots.isEmpty())
        assertFalse(read.items.single().unread)
    }

    @Test fun homeCalendarAndInvestmentFeedsRun() = runBlocking<Unit> {
        saRepos.profile.save(emptyProfile().copy(displayName = "تجربة", payday = 28))
        val items = graph.home.calendar.items("2026-10-09", "2026-11-09", "2026-10-09")
        assertTrue(items.any { it.type == app.masroufy.core.CalendarItemType.PAYDAY }, "يوم الراتب من ملفك")
        val feed = assertIs<FeedState.Ready<app.masroufy.core.PriceFeed>>(graph.investment.feeds.prices())
        assertEquals(0, graph.investment.syncPrices.sync(feed.feed).updated.size, "مفيش أصول مربوطة")
        assertEquals("تجربة", graph.shell.me().displayName)
        assertNull(graph.shell.me().profilePercent, "نسبة الملف لسه مالهاش حسبة ⇒ مش بنخترع رقم")
    }

    @Test fun backgroundCycleWiresTheSmsLanesPerCountry() = runBlocking<Unit> {
        val cycle = backgroundCycle(session.spaces(), MemorySmsInbox(), MemoryDeviceNotifier(), env)
        val result = cycle.run(LocalMoment("2026-10-09", 9))
        assertEquals(AutoRecordStatus.OFF, result.sms?.status, "الصندوق مش متاح (زي الآيفون) ⇒ ولا حاجة")
        assertFalse(result.smsFailed)
        assertNull(result.alerts, "المحرك مش متوصل في الخلفية لسه (BackgroundWiring)")
    }
}
