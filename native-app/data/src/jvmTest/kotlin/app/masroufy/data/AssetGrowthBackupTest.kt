package app.masroufy.data

import app.masroufy.core.Asset
import app.masroufy.core.Currency
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.emptyBackupData
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * حقول «هتوصل لكام؟» على الأصل (OVERRIDES §69.6) في التخزين (فايربيز والذاكرة بنفس المحوّل) والنسخة الشاملة —
 * **بتتكتب لو موجودة بس** ⇒ مستند التطبيق الحالي ونسخه هي هي. بيانات مخترعة.
 */
class AssetGrowthBackupTest {
    private val plain = Asset("asset-1", "ذهب وهمي", "gold", "جرام", Currency.SAR, false, feedSymbol = "GOLD_24K_GRAM")
    private val flat = Asset(
        "asset-2", "شقة وهمية", "other", "وحدة", Currency.SAR, false,
        valuation = RealEstateValuation.AREA, areaSqm = 240 * QUANTITY_SCALE, pricePerSqmMinor = 4_000_000, pricePerSqmAsOf = "2026-10-01",
        monthlyRentMinor = 1_500_000, rentIncreaseBp = 500, vacantMonthsPerYear = 1, expectedRateBp = 174,
    )
    private val oldShape = setOf("id", "name", "kind", "unitLabel", "currency", "feedSymbol", "archived")
    private val growthFields = setOf("valuation", "areaSqm", "pricePerSqmMinor", "pricePerSqmAsOf", "monthlyRentMinor", "rentIncreaseBp", "vacantMonthsPerYear", "expectedRateBp")

    @Test
    fun `الأصل من غير الحقول بيتكتب بنفس شكله القديم بالظبط`() {
        val doc = AssetProjectCodecs.assets.toStore(plain)
        assertEquals(oldShape, doc.keys)
        assertEquals(plain, AssetProjectCodecs.assets.decode(doc))
        // مستند قديم (من التطبيق الحالي) ⇒ الحقول الجديدة فاضية
        val old = mapOf("id" to "asset-1", "name" to "ذهب وهمي", "kind" to "gold", "unitLabel" to "جرام", "currency" to "SAR", "archived" to false, "feedSymbol" to "GOLD_24K_GRAM")
        assertEquals(plain, AssetProjectCodecs.assets.decode(old))
        assertTrue(growthFields.all { it in AssetProjectCodecs.assets.omittedFields(plain) }, "شيل الحقل بيمسحه من المستند (merge)")
    }

    @Test
    fun `العقار بيتكتب ويتقري بكل حقوله`() {
        val doc = AssetProjectCodecs.assets.toStore(flat)
        assertTrue(doc.keys.containsAll(growthFields))
        assertEquals("area", doc["valuation"])
        assertEquals(500L, doc["rentIncreaseBp"], "الأرقام الصحيحة بتتخزن Long")
        assertEquals(flat, AssetProjectCodecs.assets.decode(doc))
        assertEquals(flat.copy(valuation = RealEstateValuation.WHOLE), AssetProjectCodecs.assets.decode(doc + ("valuation" to "whole")))
        assertFailsWith<IllegalArgumentException> { AssetProjectCodecs.assets.decode(doc + ("valuation" to "house")) }
    }

    @Test
    fun `النسخة الشاملة القديمة هي هي والجديدة بتسافر وبتتفحص`() = runBlocking<Unit> {
        val oldText = FullBackup(MemoryFullBackup(account(plain))).create("2026-10-05T12:00:00.000Z").toJsonText()
        assertTrue(growthFields.none { "\"$it\"" in oldText }, "أصل من غير الحقول ⇒ ولا حقل جديد في الملف")
        val file = FullBackup(MemoryFullBackup(account(plain, flat))).create("2026-10-05T12:00:00.000Z")
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        restore.apply(restore.plan(file.toJsonText()).file)
        assertEquals(listOf(plain, flat), target.read().getValue("assets").map { AssetProjectCodecs.assets.decode(it) })
        for ((field, bad) in listOf("valuation" to "house", "vacantMonthsPerYear" to 13L, "areaSqm" to 0L, "expectedRateBp" to 200_000L, "pricePerSqmMinor" to -1L)) {
            val broken = account(plain, flat).also { it.getValue("assets")[1] = it.getValue("assets")[1] + (field to bad) }
            assertFailsWith<IllegalArgumentException>(field) { FullBackup(MemoryFullBackup(broken)).create("2026-10-05T12:00:00.000Z") }
        }
        assertFalse(oldText.isEmpty())
    }

    private fun account(vararg assets: Asset) = emptyBackupData().also { data ->
        for (a in assets) data.getValue("assets") += AssetProjectCodecs.assets.toStore(a)
    }
}
