package app.masroufy.data

import app.masroufy.core.Bequest
import app.masroufy.core.DistantBranch
import app.masroufy.core.EstateItem
import app.masroufy.core.EstateOwner
import app.masroufy.core.HeirKind
import app.masroufy.core.INHERITANCE_SCENARIOS_GROUP
import app.masroufy.core.InheritanceCase
import app.masroufy.core.InheritanceScenario
import app.masroufy.core.PredeceasedChild
import app.masroufy.core.SpecialCircumstance
import app.masroufy.core.emptyBackupData
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** حسابات الورث المحفوظة (§69.4) في التخزين والنسخة الشاملة — بيانات مخترعة. */
class InheritanceScenarioBackupTest {
    private val simple = InheritanceScenario(
        "inherit-1", "تركتي الوهمية", EstateOwner.MINE, null,
        InheritanceCase("SA", mapOf(HeirKind.WIFE to 1, HeirKind.SON to 2), listOf(EstateItem("شقة وهمية", 50_000_000))),
        "2026-10-05T10:00:00.000Z", "2026-10-05T10:00:00.000Z",
    )
    private val full = simple.copy(
        id = "inherit-2", estateOf = EstateOwner.OTHER, personId = "p-1",
        input = InheritanceCase(
            countryCode = "EG",
            heirs = mapOf(HeirKind.SON to 1, HeirKind.DAUGHTER_SON to 2, HeirKind.FULL_SISTER_DAUGHTER to 1),
            items = listOf(EstateItem("أرض وهمية", 1_000_000), EstateItem("دهب وهمي", 0)),
            funeralMinor = 10_000, debtsMinor = 20_000, bequest = Bequest(30_000, toHeir = true, heirsConsent = false),
            predeceasedChildren = listOf(PredeceasedChild(false, 1, 1, 5_000)),
            distantRelatives = true,
            special = setOf(SpecialCircumstance.TAKHARUJ),
            names = mapOf(HeirKind.SON to listOf("سامي وهمي")),
            distantBranches = listOf(DistantBranch(HeirKind.DAUGHTER, 2, 0), DistantBranch(HeirKind.FULL_SISTER, 0, 1)),
        ),
    )

    private fun roundTrip(value: InheritanceScenario): Doc {
        val d = InheritanceCodecs.scenarios.toStore(value)
        assertEquals(value, InheritanceCodecs.scenarios.decode(d))
        return d
    }

    @Test
    fun `الحسبة بتتكتب وتتقري والفاضي ما بيتكتبش`() {
        val d = roundTrip(simple)
        assertEquals(
            setOf("id", "name", "estateOf", "countryCode", "heirs", "items", "funeralMinor", "debtsMinor", "createdAt", "updatedAt"), d.keys,
            "من غير وصية ولا أسامي ولا ذوي أرحام",
        )
        assertEquals(listOf(mapOf("kind" to "wife", "count" to 1L), mapOf("kind" to "son", "count" to 2L)), d["heirs"])
        assertEquals(listOf(mapOf("name" to "شقة وهمية", "valueMinor" to 50_000_000L)), d["items"])
        val f = roundTrip(full)
        for (k in listOf("personId", "bequest", "predeceased", "distantRelatives", "distantBranches", "special", "names")) assertTrue(k in f, k)
        assertEquals(listOf(mapOf("parent" to "daughter", "sons" to 2L, "daughters" to 0L), mapOf("parent" to "full_sister", "sons" to 0L, "daughters" to 1L)), f["distantBranches"])
        assertEquals(listOf(mapOf("kind" to "son", "values" to listOf("سامي وهمي"))), f["names"])
        // العدد صفر ما بيتكتبش
        assertEquals(
            listOf(mapOf("kind" to "son", "count" to 1L)),
            InheritanceCodecs.scenarios.toStore(simple.copy(input = simple.input.copy(heirs = mapOf(HeirKind.SON to 1, HeirKind.WIFE to 0))))["heirs"],
        )
        // الحفظ بـmerge: الوصية اللي اتشالت بتتمسح من المستند صراحة
        assertTrue("bequest" in InheritanceCodecs.scenarios.omittedFields(simple))
        assertEquals(57, DocumentCodecs.byGroup.size)
    }

    @Test
    fun `رقم الحساب الطويل في الأسامي بيتقص`() {
        val withNumber = simple.copy(name = "حساب 1234567890123", input = simple.input.copy(items = listOf(EstateItem("حساب وهمي 9876543210987", 1))))
        val d = InheritanceCodecs.scenarios.toStore(withNumber)
        assertEquals("حساب ****0123", d["name"])
        assertEquals("حساب وهمي ****0987", (d["items"] as List<*>).filterIsInstance<Map<*, *>>().single()["name"])
    }

    private fun account(with: Boolean) = emptyBackupData().also {
        if (with) {
            it.getValue(INHERITANCE_SCENARIOS_GROUP) += InheritanceCodecs.scenarios.toStore(simple)
            it.getValue(INHERITANCE_SCENARIOS_GROUP) += InheritanceCodecs.scenarios.toStore(full)
        }
    }

    @Test
    fun `الحسابات بتسافر في النسخة الشاملة وبترجع بنفس البصمة`() = runBlocking<Unit> {
        val source = account(with = true)
        val file = FullBackup(MemoryFullBackup(source)).create("2026-10-05T12:00:00.000Z")
        val text = file.toJsonText()
        assertTrue("\"$INHERITANCE_SCENARIOS_GROUP\"" in text)
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val outcome = restore.apply(restore.plan(text).file)
        assertEquals(2, outcome.added[INHERITANCE_SCENARIOS_GROUP])
        assertEquals(source.getValue(INHERITANCE_SCENARIOS_GROUP), target.read().getValue(INHERITANCE_SCENARIOS_GROUP))
        assertEquals(listOf(simple, full), target.read().getValue(INHERITANCE_SCENARIOS_GROUP).map { InheritanceCodecs.scenarios.decode(it) })
        assertEquals(file.checksum, FullBackup(target).create("2026-10-05T12:00:00.000Z").checksum)
    }

    @Test
    fun `من غير حسابات المجموعة ما بتتكتبش والمكسور بيترفض`() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(account(with = false))).create("2026-10-05T12:00:00.000Z").toJsonText()
        assertFalse("\"$INHERITANCE_SCENARIOS_GROUP\"" in text, "ملف الإصدار 2/3 من غير حسابات ورث هو هو")
        fun rejects(why: String, change: (Map<String, Any?>) -> Map<String, Any?>) {
            val data = account(with = true).also { it.getValue(INHERITANCE_SCENARIOS_GROUP)[1] = change(it.getValue(INHERITANCE_SCENARIOS_GROUP)[1]) }
            assertFailsWith<IllegalArgumentException>(why) { runBlocking { FullBackup(MemoryFullBackup(data)).create("2026-10-05T12:00:00.000Z") } }
        }
        rejects("نوع وارث مش معروف") { it + ("heirs" to listOf(mapOf("kind" to "cousin_of_cousin", "count" to 1L))) }
        rejects("عدد فوق 100") { it + ("heirs" to listOf(mapOf("kind" to "son", "count" to 101L))) }
        rejects("الورثة خريطة مش قايمة") { it + ("heirs" to mapOf("son" to 1L)) }
        rejects("اسم مش نص") { it + ("names" to listOf(mapOf("kind" to "son", "values" to listOf(5L)))) }
        rejects("قيمة بالسالب") { it + ("items" to listOf(mapOf("name" to "x", "valueMinor" to -1L))) }
        rejects("حاجة من غير اسم") { it + ("items" to listOf(mapOf("valueMinor" to 1L))) }
        rejects("تركة مين مش معروفة") { it + ("estateOf" to "both") }
        rejects("تركتي بشخص") { it + ("estateOf" to "mine") }
        rejects("شخص أولاده مش من ذوي الأرحام") { it + ("distantBranches" to listOf(mapOf("parent" to "mother", "sons" to 1L, "daughters" to 0L))) }
        rejects("ظرف مش معروف") { it + ("special" to listOf("war")) }
        rejects("وصية ناقصة") { it + ("bequest" to mapOf("amountMinor" to 1L)) }
        rejects("الديون عشري") { it + ("debtsMinor" to 1.5) }
        rejects("من غير ورثة") { it - "heirs" }
    }
}
