package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Merchant
import app.masroufy.core.RawCategoryTree
import app.masroufy.core.RawGroup
import app.masroufy.core.RawMain
import app.masroufy.core.RawSub
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.SpaceError
import app.masroufy.core.buildCountryCategoryTree
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryActiveSpaceStore
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryReferenceSeed
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySpaceRegistry
import app.masroufy.port.SeedSource
import app.masroufy.port.SpaceSeedTargets
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** إدارة البلاد (OVERRIDES §41 · §64) على مستودعات الذاكرة — شجرة وأسماء مخترعة. */
class ManageSpacesTest {
    private val raw = RawCategoryTree(
        listOf(
            RawGroup("transport", listOf(RawMain("السيارة", "car", 200.0, 50.0, 40.0, subs = listOf(RawSub("وقود", "fuel"), RawSub("مواقف وسايس", "square-parking", "hasCar"))))),
            RawGroup("home", listOf(RawMain("اتصالات", "phone", 180.0, 50.0, 40.0, subs = listOf(RawSub("جوال", "phone"))))),
        ),
    )

    // السعودية: بيانات موجودة قبل البلاد — ولازم تفضل زي ما هي بالحرف
    private val saudiCategories = MemoryCategoryRepository(listOf(Category("cat-old", null, "تصنيف قديم", "tag", "#111111", "#EEEEEE", true, 1)))
    private val saudiRules = MemoryRuleRepository(listOf(ClassificationRule("rule-0001", 1, "كلمة", RuleMatchMode.CONTAINS, "cat-old", true)))
    private val sharedMerchants = MemoryMerchantRepository(listOf(Merchant("merch-00001", "تاجر وهمي", "تاجر وهمي", verifiedCategoryId = "cat-old")))

    private val registry = MemorySpaceRegistry()
    private val active = MemoryActiveSpaceStore()
    private val created = LinkedHashMap<String, Pair<MemoryCategoryRepository, MemoryRuleRepository>>()
    private var loads = 0

    private val manage = ManageSpaces(
        ManageSpacesDeps(
            registry, active, FixedClock("2026-10-04T10:00:00.000Z"),
            targets = { space ->
                val c = MemoryCategoryRepository()
                val r = MemoryRuleRepository()
                created[space.id] = c to r
                // التجار في المساحة الجديدة = المشتركين + تصنيف البلد — التجهيز ما يكتبش تجار خالص
                SpaceSeedTargets(c, r, MemoryReferenceSeed(c, r, SpaceMerchantRepository(sharedMerchants, MemoryMerchantCategoryRepository(), c)))
            },
            seeds = { pack ->
                loads++
                val tree = buildCountryCategoryTree(raw, pack)
                val parking = tree.aliases.getValue(app.masroufy.core.normalizeText("مواقف وسايس"))
                SeedSource(
                    tree.categories,
                    listOf(ClassificationRule("rule-0001", 1, "PARKING", RuleMatchMode.CONTAINS, parking, true)),
                    listOf(Merchant("merch-09999", "تاجر من الملف", "تاجر من الملف")),
                )
            },
        ),
    )

    @Test fun egyptIsCreatedWithItsOwnTreeAndSaudiIsUntouched() = runBlocking<Unit> {
        val before = Triple(saudiCategories.listAll(), saudiRules.listAll(), sharedMerchants.listAll())
        val egypt = manage.create("EG")
        assertEquals("eg", egypt.id)
        assertEquals(app.masroufy.core.Currency.EGP, egypt.currency)
        val (categories, rules) = created.getValue("eg")
        val names = categories.listAll().map { it.name }
        assertTrue("باركنج" in names && "سايس" in names && "موبايل" in names, names.toString())
        assertTrue("مواقف وسايس" !in names)
        assertEquals(1, rules.listAll().size)
        assertEquals(categories.listAll().single { it.name == "باركنج" }.id, rules.listAll().single().categoryId)
        assertEquals(before, Triple(saudiCategories.listAll(), saudiRules.listAll(), sharedMerchants.listAll()), "السعودية والتجار المشتركين ما اتلمسوش")
        assertEquals(listOf(DEFAULT_SPACE_ID, "eg"), manage.list().map { it.id })
        assertEquals(DEFAULT_SPACE_ID, manage.active().id, "إنشاء بلد ما بيبدّلش لوحده")
    }

    @Test fun oneSpacePerCountry() = runBlocking<Unit> {
        manage.create("EG")
        assertFailsWith<SpaceError> { manage.create("eg") }
        assertFailsWith<SpaceError> { manage.create("SA") }
        assertFailsWith<SpaceError> { manage.create("XX") }
        assertEquals(1, registry.listAll().size)
        assertEquals(1, loads, "المرفوض ما بيقراش ملفات المراجع ولا بيكتب حاجة")
        assertEquals(listOf("eg"), created.keys.toList())
    }

    @Test fun switchingArchivingAndComingBack() = runBlocking<Unit> {
        manage.create("EG")
        assertEquals("eg", manage.switchTo("eg").id)
        assertEquals("eg", manage.active().id)
        assertEquals("eg", active.read())
        // أرشفة الشغالة ⇒ الجهاز يرجع للسعودية، والبلد ما تتمسحش
        manage.archive("eg")
        assertEquals(DEFAULT_SPACE_ID, manage.active().id)
        assertEquals(listOf(DEFAULT_SPACE_ID), manage.list().map { it.id })
        assertEquals(listOf(DEFAULT_SPACE_ID, "eg"), manage.list(includeArchived = true).map { it.id })
        assertFailsWith<SpaceError> { manage.switchTo("eg") }
        assertFailsWith<SpaceError>("مؤرشفة لسه موجودة ⇒ ما تتعملش تاني") { manage.create("EG") }
        manage.unarchive("eg")
        assertEquals("eg", manage.switchTo("eg").id)
        assertFailsWith<SpaceError> { manage.archive(DEFAULT_SPACE_ID) }
        assertFailsWith<SpaceError> { manage.switchTo("ae") }
        assertEquals(DEFAULT_SPACE_ID, manage.switchTo(DEFAULT_SPACE_ID).id)
    }

    @Test fun anInterruptedCreationIsRetriedNotDuplicated() = runBlocking<Unit> {
        // أول محاولة: السجل وقع بعد ما التصنيفات اتكتبت ⇒ البلد مش في القايمة، والمحاولة التانية بتكمّل
        val failing = object : app.masroufy.port.SpaceRegistry by registry {
            var fail = true
            override suspend fun addIfMissing(space: app.masroufy.core.Space): Boolean {
                if (fail) { fail = false; throw IllegalStateException("انقطع") }
                return registry.addIfMissing(space)
            }
        }
        val c = MemoryCategoryRepository()
        val r = MemoryRuleRepository()
        val seed = MemoryReferenceSeed(c, r, MemoryMerchantRepository())
        val m = ManageSpaces(ManageSpacesDeps(failing, active, FixedClock("2026-10-04T10:00:00.000Z"), { SpaceSeedTargets(c, r, seed) }, { pack -> SeedSource(buildCountryCategoryTree(raw, pack).categories, emptyList(), emptyList()) }))
        assertFailsWith<IllegalStateException> { m.create("EG") }
        assertEquals(listOf(DEFAULT_SPACE_ID), m.list().map { it.id }, "نص بلد ما تبانش")
        val count = c.listAll().size
        m.create("EG")
        assertEquals(count, c.listAll().size, "ما اتكررش")
        assertEquals(listOf(DEFAULT_SPACE_ID, "eg"), m.list().map { it.id })
        assertNull(active.read())
    }
}
