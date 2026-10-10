package app.masroufy.wiring

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Space
import app.masroufy.core.SpaceError
import app.masroufy.core.Texts
import app.masroufy.core.activeSpaceOf
import app.masroufy.core.countryPack
import app.masroufy.core.defaultSpace
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryActiveSpaceStore
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryReferenceSeed
import app.masroufy.memory.MemorySpaceRegistry
import app.masroufy.port.SpaceRegistry
import app.masroufy.port.SpaceSeedTargets
import app.masroufy.ui.screens.more.NewWallet
import app.masroufy.ui.screens.onboarding.countryOptions
import app.masroufy.usecase.AddKind
import app.masroufy.usecase.AddOperationDraft
import app.masroufy.usecase.AddOperationResult
import app.masroufy.usecase.LoadOnlineFeeds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * «أضف بلدًا» على الجوال (رد المالك §88-8 — «اضافة دولة زي مصر غير متاح لسه»): التجميع كله على الذاكرة بجلسة بنفس سلوك
 * `AccountSession` (البلاد المفتوحة من السجل · التبديل · إعادة الفتح بعد أي تغيير). الشجرة **المضمّنة الحقيقية** (`BundledSeeds`).
 * بيانات وهمية بس.
 */
class SpacesAdminWiringTest {
    /** جلسة في الذاكرة: السعودية دايمًا مفتوحة، والباقي من السجل (مش المؤرشفة). مستودعات كل بلد بتفضل (الأرشفة ما بتمسحش). */
    private inner class Session(val registry: SpaceRegistry = MemorySpaceRegistry()) {
        val active = MemoryActiveSpaceStore()
        val stores = linkedMapOf(DEFAULT_SPACE_ID to memorySpaceRepositories())
        var open: Map<String, Space> = mapOf(DEFAULT_SPACE_ID to defaultSpace())
        var activeId = DEFAULT_SPACE_ID
        var reopens = 0
        var reopenFails = 0
        var reopenHangs = false

        private fun repos(id: String) = stores.getOrPut(id) { memorySpaceRepositories() }

        suspend fun reopen() {
            if (reopenHangs) awaitCancellation()
            if (reopenFails > 0) { reopenFails--; error("انقطع النت") }
            reopens++
            val reg = registry.listAll()
            open = mapOf(DEFAULT_SPACE_ID to defaultSpace()) + reg.filter { !it.archived }.associateBy { it.id }
            activeId = activeSpaceOf(active.read(), reg.filter { it.id in open }).id
        }

        val links = object : SessionLinks {
            override val account = MemoryAccount()

            override fun spaces() = open.values.map { it to repos(it.id) }

            override fun switchSpace(spaceId: String): Boolean {
                if (spaceId !in open) return false
                active.write(spaceId)
                activeId = spaceId
                return true
            }

            override fun spaceAdmin() = SpaceAdminLinks(
                registry,
                active,
                targets = { s -> repos(s.id).let { SpaceSeedTargets(it.categories, it.rules, MemoryReferenceSeed(it.categories, it.rules, MemoryMerchantRepository())) } },
                reopen = ::reopen,
            )
        }

        /** اللي الجوال بيبنيه لكل حالة «جاهز» (`AppContainer.ready`): التجميع للبلد الشغالة. */
        fun graph() = SpaceGraph(open.getValue(activeId), repos(activeId), env, links, feeds)
    }

    private val env = memoryEnv()
    private val feeds = LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis)

    @AfterTest fun msaBack() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun moreAndOnboardingShareOneAdminAndEgyptIsUnlocked() = runBlocking<Unit> {
        val graph = Session().graph()
        val admin = assertNotNull(graph.more.spacesAdmin, "«البلدان» متوصّلة")
        assertSame(admin, graph.onboarding.spacesAdmin, "«أول تشغيل» بنفس الطريق")
        val egypt = countryOptions(listOf(defaultSpace()), canCreate = graph.onboarding.spacesAdmin != null).single { it.id == "EG" }
        assertFalse(egypt.off, "مصر مش مقفولة في كارت البلد")
        // من غير إدارة بلاد في الجلسة (المعاينة) ⇒ «غير متاح بعد» زي الأول
        val bare = SpaceGraph(defaultSpace(), memorySpaceRepositories(), env, object : SessionLinks {
            override val account = MemoryAccount()
            override fun spaces() = emptyList<Pair<Space, SpaceRepositories>>()
            override fun switchSpace(spaceId: String) = false
        }, feeds)
        assertEquals(null, bare.more.spacesAdmin)
    }

    @Test fun egyptIsCreatedWithTheBundledTreeAndOpensInTheSameSession() = runBlocking<Unit> {
        val s = Session()
        val graph = s.graph()
        val eg = graph.more.spacesAdmin!!.create("EG")
        assertEquals("eg", eg.id)
        assertEquals(Currency.EGP, eg.currency)
        assertEquals("Africa/Cairo", countryPack(eg.countryCode).timeZone)
        assertEquals(1, s.reopens, "الجلسة اتحدثت بعد الإنشاء")
        // نفس الجلسة: «البلدان» بتشوفها والتبديل لها شغال من غير إعادة تشغيل
        assertEquals(listOf(DEFAULT_SPACE_ID, "eg"), graph.more.spaces().map { it.space.id })
        assertTrue(graph.shell.switchSpace("eg"))
        val names = s.stores.getValue("eg").categories.listAll().map { it.name }
        assertTrue("باركنج" in names && "سايس" in names && "موبايل" in names, "شجرة مصر = السعودية + فروقها")
        assertTrue("مواقف وسايس" !in names)
        assertTrue(s.stores.getValue("eg").rules.listAll().isNotEmpty(), "قواعد التصنيف العامة اتزرعت")
        assertTrue(s.stores.getValue(DEFAULT_SPACE_ID).categories.listAll().isEmpty(), "السعودية ما اتلمستش")
        assertTrue(s.stores.getValue("eg").merchants.listAll().isEmpty(), "التجار ما بيتزرعوش في البلد")
    }

    @Test fun retryingAfterAnInterruptedCreateSeedsOnce() = runBlocking<Unit> {
        val s = Session()
        val admin = s.graph().more.spacesAdmin!!
        // السجل اتكتب والجلسة ما لحقتش تفتحها (النت قطع) ⇒ «أضف» تاني بيفتحها بس (مش «عندك حساب مصر قبل كده»)
        s.reopenFails = 1
        assertFailsWith<IllegalStateException> { admin.create("EG") }
        assertEquals(listOf("eg"), s.registry.listAll().map { it.id })
        val seeded = s.stores.getValue("eg").categories.listAll().size
        assertEquals("eg", admin.create("EG").id)
        assertEquals(seeded, s.stores.getValue("eg").categories.listAll().size, "التصنيفات ما اتكررتش")
        assertTrue("eg" in s.open)
        // مرة تالتة ⇒ نفس البلد، من غير كتابة جديدة
        admin.create("eg")
        assertEquals(seeded, s.stores.getValue("eg").categories.listAll().size)
        assertEquals(1, s.registry.listAll().size)
        assertFailsWith<SpaceError>("السعودية موجودة من الأول") { admin.create("SA") }
    }

    @Test fun aCreateCutBeforeTheRegistryIsCompletedNotDuplicated() = runBlocking<Unit> {
        val base = MemorySpaceRegistry()
        var fail = true
        val s = Session(object : SpaceRegistry by base {
            override suspend fun addIfMissing(space: Space): Boolean {
                if (fail) { fail = false; error("انقطع") }
                return base.addIfMissing(space)
            }
        })
        val admin = s.graph().more.spacesAdmin!!
        assertFailsWith<IllegalStateException> { admin.create("EG") }
        assertEquals(setOf(DEFAULT_SPACE_ID), s.open.keys, "نص بلد ما تتفتحش")
        val seeded = s.stores.getValue("eg").categories.listAll().size
        admin.create("EG")
        assertEquals(seeded, s.stores.getValue("eg").categories.listAll().size)
        assertTrue("eg" in s.open)
    }

    @Test fun noInternetTimesOutInsteadOfHanging() = runBlocking<Unit> {
        val s = Session()
        s.reopenHangs = true
        val admin = SessionSpacesAdmin(s.links.spaceAdmin()!!, env.clock, timeoutMs = 50)
        assertFailsWith<SpaceAdminTimeout> { admin.create("EG") }
        s.reopenHangs = false
        assertEquals("eg", admin.create("EG").id, "المحاولة التانية بتكمّل")
        assertTrue("eg" in s.open)
    }

    @Test fun archivingClosesTheCountryAndBringingItBackReopensIt() = runBlocking<Unit> {
        val s = Session()
        val graph = s.graph()
        val admin = graph.more.spacesAdmin!!
        admin.create("EG")
        assertTrue(graph.shell.switchSpace("eg"))
        admin.archive("eg")
        assertEquals(DEFAULT_SPACE_ID, s.activeId, "أرشفة الشغالة ⇒ السعودية")
        assertEquals(setOf(DEFAULT_SPACE_ID), s.open.keys)
        assertEquals(listOf("eg"), admin.archived().map { it.id })
        assertEquals(listOf(DEFAULT_SPACE_ID), graph.more.spaces().map { it.space.id })
        assertFalse(graph.shell.switchSpace("eg"), "المؤرشفة ما بيتبدّلش لها")
        assertFailsWith<SpaceError>("بلد واحدة = حساب واحد — المؤرشفة بترجع، ما بتتعملش تاني") { admin.create("EG") }
        admin.unarchive("eg")
        assertTrue(admin.archived().isEmpty())
        assertTrue(graph.shell.switchSpace("eg"))
        assertEquals("eg", s.graph().space.id)
        assertFailsWith<SpaceError> { admin.archive(DEFAULT_SPACE_ID) }
    }

    /** التبديل بيفتح بيانات البلد: كاش ومصروف في مصر بالجنيه ⇒ رجوع للسعودية (ما شافتش حاجة) ⇒ مصر تاني (البيانات زي ما هي). */
    @Test fun switchingOpensEachCountryWithItsOwnData() = runBlocking<Unit> {
        val s = Session()
        s.graph().more.spacesAdmin!!.create("EG")
        assertTrue(s.links.switchSpace("eg"))
        val eg = s.graph()
        assertEquals(Currency.EGP, eg.space.currency)
        assertEquals(null, eg.more.walletEditor.add(NewWallet("cash", "كاش", null, 50_000, "2026-10-01")))
        val options = eg.shell.addOptions()
        assertEquals(Currency.EGP, options.currency)
        val spent = eg.shell.addOperation(AddOperationDraft(AddKind.OUT, "25", options.wallets.single().id, categoryId = options.categories.first().id))
        assertIs<AddOperationResult.Saved>(spent)
        assertEquals(2_500L, spent.transaction.amountMinor)
        assertEquals(Currency.EGP, spent.transaction.currency)
        assertEquals(47_500L, eg.shell.withYouNow().totalMinor)

        assertTrue(s.links.switchSpace(DEFAULT_SPACE_ID))
        val sa = s.graph()
        assertEquals(DEFAULT_SPACE_ID, sa.space.id)
        assertTrue(sa.shell.addOptions().wallets.isEmpty(), "محافظ مصر ما بتبانش في السعودية")
        assertTrue(s.stores.getValue(DEFAULT_SPACE_ID).transactions.listByDateRange("2026-01-01", "2026-12-31").isEmpty())

        assertTrue(s.links.switchSpace("eg"))
        assertEquals(47_500L, s.graph().shell.withYouNow().totalMinor, "مصر رجعت ببياناتها")
    }
}
