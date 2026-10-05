package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * العقار نوع لوحده (رد المالك §69.7 «أيوه، نوع عقار») — **في كوتلن بس**: النوع المتخزن "other" + علامة `realEstate`، عشان التطبيق الحالي
 * بيرفض أي نوع مش في قايمته وهو بيعمل النسخة الشاملة (`src/domain/checkFullBackup.ts` سطر 71). الأصول مخترعة.
 */
class RealEstateKindTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val other = Asset("a-1", "أصل وهمي", "other", "وحدة", Currency.SAR, false)
    private val marked = other.copy(realEstate = true)
    private val valued = other.copy(valuation = RealEstateValuation.WHOLE)

    @Test
    fun theMarkerOrAValuationMakeItRealEstate() {
        assertFalse(other.isRealEstate)
        assertTrue(marked.isRealEstate)
        assertTrue(valued.isRealEstate, "الأصل اللي عليه طريقة قيمة (§69.6، قبل العلامة) عقار")
        assertTrue(other.copy(valuation = RealEstateValuation.AREA).isRealEstate)
        assertEquals(listOf("other", ASSET_KIND_REAL_ESTATE, ASSET_KIND_REAL_ESTATE), listOf(other, marked, valued).map { it.displayKind })
        assertEquals("gold", other.copy(kind = "gold").displayKind)
        // المتخزن: العقار ⇒ "other" + العلامة · غيره زي ما هو
        assertEquals("other" to true, storedAssetKind(ASSET_KIND_REAL_ESTATE))
        assertEquals("gold" to false, storedAssetKind("gold"))
        assertEquals("other" to false, storedAssetKind("other"))
        assertEquals("وحدة", ASSET_UNIT_DEFAULTS.getValue(ASSET_KIND_REAL_ESTATE))
    }

    @Test
    fun theLabelInEachLanguage() {
        assertEquals("عقار", ASSET_KIND_LABELS[marked.displayKind])
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("عقار", ASSET_KIND_LABELS[ASSET_KIND_REAL_ESTATE])
        assertEquals("أصل آخر", ASSET_KIND_LABELS[other.displayKind], "«أخرى» زي ما هي")
        Texts.language = Language.EN
        assertEquals("Real estate", ASSET_KIND_LABELS[ASSET_KIND_REAL_ESTATE])
    }

    // «هتوصل لكام»: العقار بالعلامة لوحدها بياخد معدل العقار، وقيمته من غير شراء = سعره كله (زي «القيمة كلها»)
    @Test
    fun growthTreatsTheMarkerLikeAValuation() {
        assertEquals(GrowthClass.REAL_ESTATE, growthClassOf(marked, Currency.SAR))
        assertNull(growthClassOf(other, Currency.SAR))
        val priced = computePosition("a-1", emptyList(), price = AssetPrice("a-1", 90_000_000, "2026-10-01", "manual"), today = "2026-10-05")
        assertEquals(90_000_000L, currentValueOf(marked, priced, lotsRecorded = false))
        assertNull(currentValueOf(other, priced, lotsRecorded = false), "«أخرى» من غير شراء ⇒ غير متاح زي ما كان")
    }

    @Test
    fun theBackupCheckAcceptsOnlyABooleanOnOther() {
        val row = mapOf<String, Any?>("kind" to "other", "realEstate" to true)
        assertNull(checkAssetGrowthRow(row))
        assertNull(checkAssetGrowthRow(row + ("realEstate" to false)))
        assertNull(checkAssetGrowthRow(mapOf("kind" to "gold")), "المستند القديم من غير العلامة سليم")
        assertEquals("realEstate", checkAssetGrowthRow(row + ("realEstate" to "yes")))
        assertEquals("realEstate", checkAssetGrowthRow(row + ("kind" to "gold")))
    }
}
