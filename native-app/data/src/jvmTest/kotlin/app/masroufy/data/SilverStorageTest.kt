package app.masroufy.data

import app.masroufy.core.Asset
import app.masroufy.core.Currency
import app.masroufy.core.NEW_APP_BACKUP_GROUPS
import app.masroufy.core.Space
import app.masroufy.core.backupChecksum
import app.masroufy.core.canonicalBackup
import app.masroufy.core.emptyBackupData
import app.masroufy.core.exportedBackupData
import app.masroufy.core.exportedSpaceData
import app.masroufy.core.spaceBackupHeader
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.memory.MemorySpaceRegistry
import app.masroufy.memory.MemorySpacesBackup
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.AssetRepository
import app.masroufy.usecase.AssetGrowthInput
import app.masroufy.usecase.FullBackup
import app.masroufy.usecase.ManageAssetGrowth
import app.masroufy.usecase.ManageAssetGrowthDeps
import app.masroufy.usecase.ManageAssets
import app.masroufy.usecase.ManageAssetsDeps
import app.masroufy.usecase.NewAsset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** مستودع أصول على المحوّل نفسه، والحفظ بـmerge زي فايربيز (`FirestoreSpace`: الحقول الفاضية بتتمسح، والغريبة بتفضل). */
internal class StoredAssets(vararg seed: Doc) : AssetRepository {
    val docs = LinkedHashMap<String, Doc>().apply { seed.forEach { put(it["id"] as String, it) } }

    override suspend fun listAll(): List<Asset> = docs.values.map { AssetProjectCodecs.assets.decode(it) }

    override suspend fun save(asset: Asset) {
        val old = docs[asset.id].orEmpty() - AssetProjectCodecs.assets.omittedFields(asset)
        docs[asset.id] = LinkedHashMap(old).apply { putAll(AssetProjectCodecs.assets.toStore(asset)) }
    }
}

/**
 * الفضة (OVERRIDES §69.9): في كوتلن نوع لوحده "silver"، **ومتخزنة "other" + `silver: true`** عشان النسخة الشاملة في التطبيق
 * الحالي ما تقفش (`src/domain/checkFullBackup.ts` سطر 71). العلامة بتتكتب لو true بس ⇒ مستند أي أصل تاني ونسخته هي هي. بيانات مخترعة.
 */
class SilverStorageTest {
    private val gold = Asset("asset-1", "ذهب وهمي", "gold", "جرام", Currency.SAR, false, feedSymbol = "GOLD_24K_GRAM")
    private val bar = Asset("asset-2", "سبيكة فضة وهمية", "silver", "جرام", Currency.SAR, false)
    private val legacy = linkedMapOf<String, Any?>("id" to "asset-3", "name" to "فضة قديمة وهمية", "kind" to "silver", "unitLabel" to "جرام", "currency" to "SAR", "archived" to false)

    @Test
    fun `الفضة بتتكتب أخرى مع العلامة وبتتقري فضة`() {
        val doc = AssetProjectCodecs.assets.toStore(bar)
        assertEquals("other", doc["kind"])
        assertEquals(true, doc["silver"])
        assertEquals(bar, AssetProjectCodecs.assets.decode(doc), "رحلة ذهاب وعودة")
        assertFalse("silver" in AssetProjectCodecs.assets.omittedFields(bar))
        // العلامة false أو على نوع تاني ما بتعملش فضة (والنسخة الشاملة بترفض التانية)
        assertEquals("other", AssetProjectCodecs.assets.decode(doc + ("silver" to false)).kind)
        assertEquals("gold", AssetProjectCodecs.assets.decode(doc + ("kind" to "gold")).kind)
        assertFailsWith<DocumentError> { AssetProjectCodecs.assets.decode(doc + ("silver" to "yes")) }
    }

    @Test
    fun `المستندات القديمة من غير فضة هي هي بالحرف`() {
        for (kind in listOf("gold", "stock", "fund", "digital", "other")) {
            val old = LinkedHashMap(legacy).apply { put("kind", kind) }
            val read = AssetProjectCodecs.assets.decode(old)
            assertEquals(kind, read.kind)
            assertEquals(old.toList(), AssetProjectCodecs.assets.toStore(read).toList(), kind)
            assertTrue("silver" in AssetProjectCodecs.assets.omittedFields(read), "العلامة مش مكتوبة ⇒ الحفظ بـmerge بيمسحها لو كانت")
        }
    }

    @Test
    fun `المستند القديم بنوع فضة بيتقري فضة وأول حفظ بيكتبه بالشكل الجديد`() = runBlocking<Unit> {
        val read = AssetProjectCodecs.assets.decode(legacy)
        assertEquals("silver", read.kind)
        val repo = StoredAssets(legacy, AssetProjectCodecs.assets.toStore(gold))
        val clock = FixedClock("2026-10-06T09:00:00.000Z")
        val manage = ManageAssets(ManageAssetsDeps(repo, MemoryAssetLotRepository(), MemoryAssetSaleRepository(), MemoryAssetPriceRepository(), SequentialIdGenerator(), clock))
        assertEquals("silver", repo.listAll().first { it.id == "asset-3" }.kind, "قبل الحفظ: المتخزن لسه «silver» وبيتقري فضة")
        manage.archiveAsset("asset-3", true)
        assertEquals(LinkedHashMap(legacy).apply { put("kind", "other"); put("archived", true); put("silver", true) }.toList(), repo.docs.getValue("asset-3").toList())
        assertEquals("silver", repo.listAll().first { it.id == "asset-3" }.kind)
        assertEquals(AssetProjectCodecs.assets.toStore(gold), repo.docs.getValue("asset-1"), "الدهب ما اتلمسش")
    }

    // القرار: الإضافة وكل تعديل (أرشفة · ربط بالأسعار · حقول التوقّع) بيكتبوا الشكل الجديد دايمًا — التحويل في المحوّل بس
    @Test
    fun `الإضافة والتعديل بيكتبوا الشكل الجديد دايمًا`() = runBlocking<Unit> {
        val repo = StoredAssets()
        val clock = FixedClock("2026-10-06T09:00:00.000Z")
        val lots = MemoryAssetLotRepository()
        val sales = MemoryAssetSaleRepository()
        val prices = MemoryAssetPriceRepository()
        val manage = ManageAssets(ManageAssetsDeps(repo, lots, sales, prices, SequentialIdGenerator(), clock))
        val added = manage.addAsset(NewAsset("سبيكة وهمية", "silver"))
        assertEquals("silver" to "جرام", added.kind to added.unitLabel)
        fun stored() = repo.docs.getValue(added.id)
        assertEquals("other" to true, stored()["kind"] to stored()["silver"])
        manage.linkToFeed(added.id, "SILVER_GRAM")
        ManageAssetGrowth(ManageAssetGrowthDeps(repo, lots, sales, prices, clock)).setProfile(added.id, AssetGrowthInput(expectedRateBp = 250))
        manage.archiveAsset(added.id, true)
        assertEquals(Triple("other", true, 250L), Triple(stored()["kind"], stored()["silver"], stored()["expectedRateBp"]))
        assertEquals("silver", repo.listAll().single().kind)
    }

    @Test
    fun `النسخة الشاملة من غير فضة هي هي والفضة القديمة بتتصدّر بالشكل الجديد`() = runBlocking<Unit> {
        val at = "2026-10-06T12:00:00.000Z"
        val plain = account(AssetProjectCodecs.assets.toStore(gold))
        val before = FullBackup(MemoryFullBackup(plain)).create(at)
        assertSame(plain.getValue("assets")[0], before.data.getValue("assets")[0], "مفيش فضة ⇒ نفس السطر بالظبط")
        assertFalse("silver" in before.toJsonText())
        val signed = backupChecksum(canonicalBackup(linkedMapOf("data" to exportedBackupData(plain), "profile" to null)))
        assertEquals(signed, before.checksum, "نفس البصمة اللي التطبيق الحالي بيحسبها")

        val file = FullBackup(MemoryFullBackup(account(AssetProjectCodecs.assets.toStore(gold), legacy))).create(at)
        val row = file.data.getValue("assets").single { it["id"] == "asset-3" }
        assertEquals("other" to true, row["kind"] to row["silver"], "الملف ما فيهوش «silver» ⇒ التطبيق الحالي بيقبله")
        assertFalse("\"kind\":\"silver\"" in file.toJsonText().replace(" ", ""))
    }

    @Test
    fun `ملف فيه فضة قديمة بيتقبل وبيترجع بالشكل الجديد`() = runBlocking<Unit> {
        val data = account(AssetProjectCodecs.assets.toStore(gold), legacy)
        // ملف إصدار 2 مكتوب بالفضة القديمة (زي ما كوتلن كان بيصدّر قبل جلسة 28) — بصمته صح
        val base = FullBackup(MemoryFullBackup(account(AssetProjectCodecs.assets.toStore(gold)))).create("2026-10-06T12:00:00.000Z")
        val old = base.copy(data = data, counts = base.counts + ("assets" to 2L), checksum = backupChecksum(canonicalBackup(linkedMapOf("data" to exportedBackupData(data), "profile" to null))))
        assertTrue("\"kind\":\"silver\"" in old.toJsonText().replace(" ", ""))
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val plan = restore.plan(old.toJsonText())
        restore.apply(plan.file)
        val restored = target.read().getValue("assets").single { it["id"] == "asset-3" }
        assertEquals("other" to true, restored["kind"] to restored["silver"])
        assertEquals("silver", AssetProjectCodecs.assets.decode(restored).kind)
    }

    // بلد تانية (الإصدار 3): الفضة القديمة في مساحة مصر بتتصدّر بالشكل الجديد، وملف إصدار 3 فيه «silver» بيترجع بالشكل الجديد
    @Test
    fun `مساحة مصر — التصدير والاسترجاع بالشكل الجديد`() = runBlocking<Unit> {
        val at = "2026-10-06T12:00:00.000Z"
        val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-10-04T10:00:00.000Z")
        val egRow = LinkedHashMap(legacy).apply { put("currency", "EGP") }
        val spaces = MemorySpacesBackup(MemorySpaceRegistry(listOf(egypt)), linkedMapOf("eg" to MemoryFullBackup(account(egRow))))
        val created = FullBackup(MemoryFullBackup(), spaces).create(at)
        val entry = created.spaces!!.single()
        assertEquals("other" to true, entry.data.getValue("assets").single().let { it["kind"] to it["silver"] })

        // نفس الملف بس بالفضة القديمة (زي ما كوتلن كان بيصدّر قبل جلسة 28) — البصمة متحسبة زي `signedV3`
        val old = entry.copy(data = LinkedHashMap(entry.data).apply { put("assets", listOf(egRow)) })
        val shown = exportedSpaceData(old.data)
        val written = spaceBackupHeader(egypt).apply { put("data", shown); put("counts", old.counts.filterKeys { it !in NEW_APP_BACKUP_GROUPS || it in shown }) }
        val signed = linkedMapOf("data" to exportedBackupData(created.data), "profile" to created.profile, "spaces" to listOf(written), "spaceTransfers" to created.spaceTransfers)
        val file = created.copy(spaces = listOf(old), checksum = backupChecksum(canonicalBackup(signed)))
        assertTrue("\"kind\":\"silver\"" in file.toJsonText().replace(" ", ""))
        val target = MemorySpacesBackup()
        val restore = FullBackup(MemoryFullBackup(), target)
        restore.apply(restore.plan(file.toJsonText()).file)
        val restored = target.dataOf("eg").read().getValue("assets").single()
        assertEquals("other" to true, restored["kind"] to restored["silver"])
    }

    @Test
    fun `العلامة الغلط بتترفض في النسخة الشاملة`() = runBlocking<Unit> {
        val at = "2026-10-06T12:00:00.000Z"
        val good = AssetProjectCodecs.assets.toStore(bar)
        FullBackup(MemoryFullBackup(account(good))).create(at)
        for ((field, bad) in listOf("silver" to "yes", "kind" to "gold", "realEstate" to true)) {
            assertFailsWith<IllegalArgumentException>(field) { FullBackup(MemoryFullBackup(account(good + (field to bad)))).create(at) }
        }
    }

    private fun account(vararg rows: Doc) = emptyBackupData().also { data -> for (r in rows) data.getValue("assets") += r }
}
