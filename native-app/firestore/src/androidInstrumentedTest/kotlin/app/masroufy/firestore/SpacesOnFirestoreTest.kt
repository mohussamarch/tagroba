package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.ACCOUNT_GROUPS
import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Merchant
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.RawCategoryTree
import app.masroufy.core.RawGroup
import app.masroufy.core.RawMain
import app.masroufy.core.RawSub
import app.masroufy.core.ReviewState
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.SPACE_GROUPS
import app.masroufy.core.Settlement
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.buildCountryCategoryTree
import app.masroufy.core.emptyProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryActiveSpaceStore
import app.masroufy.memory.MemoryAuth
import app.masroufy.port.SeedSource
import app.masroufy.port.SpaceSeedTargets
import app.masroufy.usecase.ManageSpaces
import app.masroufy.usecase.ManageSpacesDeps
import dev.gitlive.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * «حساب لكل بلد» (OVERRIDES §41 · §64) على Firestore Emulator (8088) — حسابات وأسماء وأرقام مخترعة.
 * 🔒 **حارس الجذر:** بيانات التطبيق الحالي (`users/{uid}`) ما يتكتبش عليها **ولا مستند** لما بلد تتعمل ويتكتب فيها.
 */
@RunWith(AndroidJUnit4::class)
class SpacesOnFirestoreTest {
    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(150_000) { block() } }

    private fun txn(id: String, currency: Currency = Currency.SAR, wallet: String = "wallet-bank") = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = 12_345, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "2026-09-10T00:00:00.000Z", updatedAt = "2026-09-10T00:00:00.000Z", walletId = wallet,
    )

    private val raw = RawCategoryTree(
        listOf(
            RawGroup("home", listOf(RawMain("اتصالات", "phone", 180.0, 50.0, 40.0, subs = listOf(RawSub("جوال", "phone"))))),
            RawGroup("transport", listOf(RawMain("السيارة", "car", 200.0, 50.0, 40.0, subs = listOf(RawSub("وقود", "fuel"), RawSub("مواقف وسايس", "square-parking", "hasCar"))))),
        ),
    )

    /** كل مستند في الجذر (مجموعات النسخة + الحساب + المستندات المفردة) من السيرفر: المسار ⇐ المحتوى. */
    private suspend fun rootSnapshot(root: FirestoreSpace): Map<String, Map<String, Any?>> {
        val out = LinkedHashMap<String, Map<String, Any?>>()
        for (group in (BACKUP_GROUPS + ACCOUNT_GROUPS).distinct()) {
            for (d in root.collection(group).get(Source.SERVER).documents) out["$group/${d.id}"] = d.rawData()!!
        }
        for (single in listOf("profile/main", "initialization/references-v1", "concurrency/settlements")) {
            root.db.document("${root.root}/$single").get(Source.SERVER).rawData()?.let { out[single] = it }
        }
        return out
    }

    private fun manageSpaces(uid: String, account: FirestoreSpace) = ManageSpaces(
        ManageSpacesDeps(
            FirestoreSpaceRegistry(account), MemoryActiveSpaceStore(), FixedClock("2026-10-04T10:00:00.000Z"),
            targets = { space ->
                val root = FirestoreSpace.forSpace(account.db, uid, space.id)
                SpaceSeedTargets(FirestoreCategoryRepository(root), FirestoreRuleRepository(root), FirestoreReferenceSeed(root, merchantsAt = account))
            },
            seeds = { pack ->
                val tree = buildCountryCategoryTree(raw, pack)
                SeedSource(tree.categories, listOf(ClassificationRule("rule-0001", 1, "PARKING", RuleMatchMode.CONTAINS, tree.categories.last().id, true)), emptyList())
            },
        ),
    )

    @Test fun creatingACountryAndWritingInItLeavesEveryRootDocumentUnchanged() = run {
        val uid = "kt-guard-" + java.util.UUID.randomUUID()
        val db = Emulator.firestore()
        val account = FirestoreSpace.forAccount(db, uid)
        // بيانات «التطبيق الحالي» في الجذر: ملف · مراجع بعلامتها · تاجر متصنف · شخص ودين متسوّى (عدّاد التسويات) · محفظة وعمليات
        val saudi = FirestoreContainer(FirestoreSpace.forUser(db, uid))
        saudi.profile.save(emptyProfile().copy(displayName = "حساب وهمي", payday = 28))
        app.masroufy.usecase.SeedUserReferences(saudi.categories, saudi.rules, saudi.referenceSeed).seed(
            SeedSource(buildCountryCategoryTree(raw, app.masroufy.core.SAUDI_PACK).categories, emptyList(), listOf(Merchant("merch-00001", "محطة وهمية", "محطة وهمية", verifiedCategoryId = "cat-sa-x"))),
        )
        saudi.wallets.save(Wallet("wallet-bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2025-01-01", "1234"))
        saudi.transactions.saveMany(listOf(txn("t-sa-1"), txn("t-sa-2")))
        saudi.people.save(Person("p-1", "شخص وهمي"))
        saudi.obligations.saveMany(listOf(Obligation("o-1", "p-1", "t-sa-1", ObligationKind.RECEIVABLE, 5_000, Currency.SAR)))
        saudi.settlementWriter.settle(Settlement("st-1", "t-sa-2", "o-1", 1_000), "p-1")
        val before = rootSnapshot(FirestoreSpace.forUser(db, uid))
        assertTrue(before.size >= 9, "الجذر فيه بيانات فعلًا: ${before.keys}")

        // بلد جديدة ⇒ تصنيفاتها وقواعدها في مكانها، وكتابة محفظة وعمليات وتصنيف تاجر وتاجر جديد وشخص جديد من جواها
        val egypt = manageSpaces(uid, account).create("EG")
        val eg = FirestoreContainer(account, FirestoreSpace.forSpace(db, uid, egypt.id), egypt.id)
        eg.wallets.save(Wallet("wallet-bank", "بنك مصري وهمي", Currency.EGP, "bank", 50_000, "2026-01-01", "9876"))
        eg.transactions.saveMany(listOf(txn("t-sa-1", Currency.EGP), txn("t-eg-2", Currency.EGP)))
        eg.merchants.saveMany(listOf(eg.merchants.listAll().single().copy(verifiedCategoryId = eg.categories.listAll().first().id)))
        eg.merchants.saveMany(listOf(Merchant("merch-90000", "كشك وهمي", "كشك وهمي", verifiedCategoryId = eg.categories.listAll().last().id)))
        eg.people.save(Person("p-2", "شخص في البلدين"))
        // دين وتسوية في مصر ⇒ عدّاد التسويات بتاع مصر، مش بتاع السعودية
        eg.obligations.saveMany(listOf(Obligation("o-eg", "p-2", "t-eg-2", ObligationKind.RECEIVABLE, 5_000, Currency.EGP)))
        eg.settlementWriter.settle(Settlement("st-eg-1", "t-sa-1", "o-eg", 1_000), "p-2")

        val after = rootSnapshot(FirestoreSpace.forUser(db, uid))
        for ((path, doc) in before) assertEquals(doc, after[path], "المستند $path في الجذر اتغير")
        // الجديد في الجذر: سجل البلد + التاجر والشخص الجداد (على مستوى الحساب) — بس
        assertEquals(setOf("spaces/eg", "merchants/merch-90000", "people/p-2"), after.keys - before.keys)
        assertEquals("cat-sa-x", after.getValue("merchants/merch-00001")["verifiedCategoryId"], "تصنيف مصر ما وصلش للتاجر المشترك")

        // بيانات مصر في مكانها — حتى العملية اللي ليها نفس معرّف عملية سعودية
        val egRoot = FirestoreSpace.forSpace(db, uid, "eg")
        assertEquals(setOf("t-sa-1", "t-eg-2"), egRoot.collection("transactions").get(Source.SERVER).documents.map { it.id }.toSet())
        assertEquals("EGP", egRoot.collection("transactions").document("t-sa-1").get(Source.SERVER).rawData()!!["currency"])
        assertEquals(listOf("merch-00001", "merch-90000"), egRoot.collection("merchantCategories").get(Source.SERVER).documents.map { it.id }.sorted())
        assertEquals(listOf("باركنج", "سايس"), eg.categories.listAll().filter { it.parentId != null }.map { it.name }.takeLast(2))
        assertEquals("complete", egRoot.db.document("${egRoot.root}/initialization/references-v1").get(Source.SERVER).rawData()!!["state"])
        assertEquals(1L, egRoot.db.document("${egRoot.root}/concurrency/settlements").get(Source.SERVER).rawData()!!["revision"])
    }

    @Test fun rootListenersNeverSeeCountryDocuments() = run {
        val uid = "kt-listen-" + java.util.UUID.randomUUID()
        val db = Emulator.firestore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val account = FirestoreSpace.forAccount(db, uid)
        val saudiRoot = FirestoreSpace.forSpace(db, uid, DEFAULT_SPACE_ID)
        val accountSync = FirestoreSync(account, ACCOUNT_GROUPS).also { it.start(scope) }
        val saudiSync = FirestoreSync(saudiRoot, SPACE_GROUPS).also { it.start(scope) }
        accountSync.awaitComplete()
        saudiSync.awaitComplete()
        val saudi = FirestoreContainer(account, saudiRoot, DEFAULT_SPACE_ID)
        saudi.transactions.saveMany(listOf(txn("t-sa")))

        val egypt = manageSpaces(uid, account).create("EG")
        val eg = FirestoreContainer(account, FirestoreSpace.forSpace(db, uid, egypt.id), egypt.id)
        eg.transactions.saveMany(listOf(txn("t-eg", Currency.EGP)))
        delay(1_500) // وقت للمستمعين لو كانوا هيشوفوها
        assertEquals(listOf("t-sa"), saudi.transactions.listByDateRange("2026-09-01", "2026-09-30").map { it.id }, "السعودية ما بتشوفش عمليات مصر")
        assertEquals(listOf("t-eg"), eg.transactions.listByDateRange("2026-09-01", "2026-09-30").map { it.id })
        assertEquals(1, saudiSync.documentsInMemory, "ذاكرة السعودية فيها عمليتها بس — ولا تصنيف ولا قاعدة من مصر")
        assertEquals(1, accountSync.documentsInMemory, "ذاكرة الحساب فيها سجل البلد بس (مش اللي تحته)")
        accountSync.stop(); saudiSync.stop(); scope.cancel()
    }

    @Test fun sessionOpensEveryCountryAndSwitchesWithoutWriting() = run {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val auth = MemoryAuth(uidPrefix = "kt-spaces-" + java.util.UUID.randomUUID().toString().take(8) + "-")
        val db = Emulator.firestore("session-spaces")
        val store = MemoryActiveSpaceStore()
        val session = AccountSession(auth, db, scope) { store }
        session.start()
        val user = auth.registerWithEmail("spaces@example.com", "secret1")
        val ready = session.state.first { it is AccountSession.State.Ready } as AccountSession.State.Ready
        assertEquals(DEFAULT_SPACE_ID, ready.activeSpaceId)
        ready.repos.transactions.saveMany(listOf(txn("t-sa")))

        manageSpaces(user.uid, ready.account).create("EG")
        session.refreshSpaces()
        val withEgypt = session.state.value as AccountSession.State.Ready
        assertEquals(setOf(DEFAULT_SPACE_ID, "eg"), withEgypt.spaces.keys)
        assertTrue(session.switchSpace("eg"))
        val inEgypt = session.state.value as AccountSession.State.Ready
        assertEquals("eg", inEgypt.activeSpaceId)
        assertEquals("eg", store.read(), "البلد الشغالة اتحفظت على الجهاز")
        inEgypt.repos.transactions.saveMany(listOf(txn("t-eg", Currency.EGP)))
        assertEquals(listOf("t-eg"), inEgypt.repos.transactions.listByDateRange("2026-09-01", "2026-09-30").map { it.id })
        assertTrue(session.switchSpace(DEFAULT_SPACE_ID))
        assertEquals(listOf("t-sa"), (session.state.value as AccountSession.State.Ready).repos.transactions.listByDateRange("2026-09-01", "2026-09-30").map { it.id })
        assertTrue(!session.switchSpace("ae"), "بلد مش موجودة")

        // خرج ⇒ كل المزامنات وقفت واتمسحت (مش بس البلد الشغالة)
        auth.signOut()
        session.state.first { it is AccountSession.State.SignedOut }
        for (s in inEgypt.spaces.values) {
            assertTrue(!s.sync.isRunning, "مستمعين ${s.space.id} لازم يقفوا")
            assertEquals(0, s.sync.documentsInMemory)
        }
        assertTrue(!inEgypt.accountSync.isRunning)
        session.stop()
        scope.cancel()
    }
}
