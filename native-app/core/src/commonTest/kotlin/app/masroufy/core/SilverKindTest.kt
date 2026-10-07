package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * الفضة نوع لوحده في كوتلن بس (رد المالك §69.9): المتخزن "other" + `silver: true` عشان التطبيق الحالي
 * (`src/domain/checkFullBackup.ts` سطر 71 بيقبل gold/stock/fund/digital/other بس). بيانات مخترعة.
 */
class SilverKindTest {
    @Test
    fun storedFormAndBack() {
        assertEquals("other" to true, storedSilverForm(ASSET_KIND_SILVER))
        for (k in listOf("gold", "stock", "fund", "digital", "other")) assertEquals(k to false, storedSilverForm(k))
        assertEquals(ASSET_KIND_SILVER, kindFromStored("other", true))
        assertEquals(ASSET_KIND_SILVER, kindFromStored(ASSET_KIND_SILVER, false), "القديم")
        assertEquals("other", kindFromStored("other", false))
        assertEquals("gold", kindFromStored("gold", true), "العلامة على نوع تاني ما بتغيرش النوع")
    }

    @Test
    fun legacyRowsNormalizedOthersUntouched() {
        val gold = mapOf<String, Any?>("id" to "a-1", "kind" to "gold")
        val plain = mapOf("assets" to listOf(gold), "people" to emptyList())
        assertSame(plain, normalizeLegacySilverAssets(plain), "مفيش فضة قديمة ⇒ نفس الكائن")
        val legacy = linkedMapOf<String, Any?>("id" to "a-2", "kind" to "silver", "unitLabel" to "جرام")
        val out = normalizeLegacySilverAssets(mapOf("assets" to listOf(gold, legacy)))
        assertSame(gold, out.getValue("assets")[0])
        assertEquals(listOf("id" to "a-2", "kind" to "other", "unitLabel" to "جرام", "silver" to true), out.getValue("assets")[1].toList())
    }

    @Test
    fun backupMarkerChecked() {
        val row = mapOf<String, Any?>("kind" to "other", "silver" to true)
        assertNull(checkAssetGrowthRow(row))
        assertNull(checkAssetGrowthRow(row + ("silver" to false)))
        assertNull(checkAssetGrowthRow(mapOf("kind" to "silver")), "القديم من غير علامة مقبول")
        assertEquals("silver", checkAssetGrowthRow(row + ("silver" to "yes")))
        assertEquals("silver", checkAssetGrowthRow(row + ("kind" to "gold")))
        assertEquals("silver", checkAssetGrowthRow(row + ("kind" to "silver")))
        assertEquals("silver", checkAssetGrowthRow(row + ("realEstate" to true)))
    }
}
