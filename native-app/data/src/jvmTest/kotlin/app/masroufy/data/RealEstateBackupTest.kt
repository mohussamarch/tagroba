package app.masroufy.data

import app.masroufy.core.Asset
import app.masroufy.core.Currency
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
 * علامة العقار (OVERRIDES §69.7) في التخزين والنسخة الشاملة: النوع المتخزن بيفضل "other" (التطبيق الحالي بيرفض غيره —
 * `src/domain/checkFullBackup.ts` سطر 71)، والعلامة **بتتكتب لو true بس** ⇒ مستند الأصل القديم ونسخته هي هي بالحرف. بيانات مخترعة.
 */
class RealEstateBackupTest {
    private val gold = Asset("asset-1", "ذهب وهمي", "gold", "جرام", Currency.SAR, false, feedSymbol = "GOLD_24K_GRAM")
    private val flat = Asset("asset-2", "شقة وهمية", "other", "وحدة", Currency.SAR, false, realEstate = true)

    @Test
    fun `المستند القديم بيتقري ويتكتب هو هو بالحرف`() {
        // مستند بشكل التطبيق الحالي بالظبط (نفس الترتيب والأنواع)
        val old = linkedMapOf<String, Any?>("id" to "asset-3", "name" to "أصل وهمي", "kind" to "other", "unitLabel" to "وحدة", "currency" to "SAR", "archived" to false)
        val read = AssetProjectCodecs.assets.decode(old)
        assertFalse(read.realEstate)
        val again = AssetProjectCodecs.assets.toStore(read)
        assertEquals(old.toList(), again.toList(), "نفس الحقول بنفس الترتيب ونفس القيم")
        assertTrue("realEstate" in AssetProjectCodecs.assets.omittedFields(read), "العلامة مش مكتوبة ⇒ الحفظ بـmerge بيمسحها لو كانت")
    }

    @Test
    fun `العلامة بتتكتب وبتتقري والنوع المتخزن أخرى`() {
        val doc = AssetProjectCodecs.assets.toStore(flat)
        assertEquals("other", doc["kind"])
        assertEquals(true, doc["realEstate"])
        assertEquals(flat, AssetProjectCodecs.assets.decode(doc))
        assertEquals(flat.copy(realEstate = false), AssetProjectCodecs.assets.decode(doc + ("realEstate" to false)))
    }

    @Test
    fun `النسخة الشاملة القديمة هي هي والعلامة بتسافر وبتتفحص`() = runBlocking<Unit> {
        val at = "2026-10-05T12:00:00.000Z"
        val before = FullBackup(MemoryFullBackup(account(gold))).create(at).toJsonText()
        assertFalse("realEstate" in before, "أصل من غير العلامة ⇒ مفيش حقل جديد في الملف")
        assertEquals(before, FullBackup(MemoryFullBackup(account(gold))).create(at).toJsonText(), "نفس الملف بالحرف (ونفس البصمة)")

        val file = FullBackup(MemoryFullBackup(account(gold, flat))).create(at)
        assertTrue("\"realEstate\":true" in file.toJsonText().replace(" ", ""))
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        restore.apply(restore.plan(file.toJsonText()).file)
        assertEquals(listOf(gold, flat), target.read().getValue("assets").map { AssetProjectCodecs.assets.decode(it) })

        for ((field, bad) in listOf("realEstate" to "yes", "kind" to "gold")) {
            val broken = account(gold, flat).also { it.getValue("assets")[1] = it.getValue("assets")[1] + (field to bad) }
            assertFailsWith<IllegalArgumentException>(field) { FullBackup(MemoryFullBackup(broken)).create(at) }
        }
    }

    private fun account(vararg assets: Asset) = emptyBackupData().also { data ->
        for (a in assets) data.getValue("assets") += AssetProjectCodecs.assets.toStore(a)
    }
}
