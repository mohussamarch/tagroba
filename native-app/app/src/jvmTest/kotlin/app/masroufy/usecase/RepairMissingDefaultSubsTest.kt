package app.masroufy.usecase

import app.masroufy.core.EGYPT_PACK
import app.masroufy.core.SAUDI_PACK
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.seed.BundledSeeds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** r06-darahim: الفرعيات الجديدة بتوصل للحسابات القديمة — مرة واحدة، ومن غير ما ترجّع اللي المالك قفله. */
class RepairMissingDefaultSubsTest {
    private val newSubs = listOf("مغسلة وكوي", "هوايات", "عصير")

    @Test fun newSubsAreInTheBundledTreeUnderTheRightMains() = runBlocking<Unit> {
        for (pack in listOf(SAUDI_PACK, EGYPT_PACK)) {
            val cats = BundledSeeds.load(pack).categories
            val byId = cats.associateBy { it.id }
            val parentOf = newSubs.associateWith { n -> cats.single { it.name == n }.parentId?.let { byId.getValue(it).name } }
            assertEquals("المنزل", parentOf["مغسلة وكوي"])
            assertEquals("ترفيه", parentOf["هوايات"])
            assertEquals("مطاعم وقهوة", parentOf["عصير"])
        }
    }

    @Test fun oldAccountGetsTheMissingSubsOnceAndKeepsDisabledOnes() = runBlocking<Unit> {
        val full = BundledSeeds.load(SAUDI_PACK).categories
        val juice = full.single { it.name == "عصير" }
        // حساب قديم: من غير الفرعيات الجديدة، وفرعي «هوايات» المالك قفله
        val old = full.filter { it.name !in newSubs } + full.single { it.name == "هوايات" }.copy(active = false)
        val repo = MemoryCategoryRepository(old)
        val repair = RepairMissingDefaultSubs(repo, BundledSeeds, SAUDI_PACK)

        assertEquals(2, repair.run())
        assertEquals(0, repair.run(), "التانية ما بتضيفش حاجة")
        val after = repo.listAll()
        assertEquals(full.size, after.size)
        assertTrue(after.single { it.name == "هوايات" }.active.not(), "المقفول يفضل مقفول")
        val added = after.single { it.id == juice.id }
        assertTrue(added.order > old.maxOf { it.order }, "بيظهر آخر إخواته")
    }

    @Test fun renamedSiblingWithSameNameIsNotDuplicated() {
        val full = kotlinx.coroutines.runBlocking { BundledSeeds.load(SAUDI_PACK).categories }
        val juice = full.single { it.name == "عصير" }
        val mine = full.filter { it.name != "عصير" } + juice.copy(id = "cat-custom-juice")
        assertTrue(missingDefaultSubs(mine, full).none { it.name == "عصير" })
        assertTrue(missingDefaultSubs(emptyList(), full).isEmpty(), "مفيش أب ⇒ مفيش فرعي")
    }
}
