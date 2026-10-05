package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.CategoryRename
import app.masroufy.memory.MemoryCategoryRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** تغيير أسماء تصنيفات موجودة للفصحى (OVERRIDES §66) — على مستودع الذاكرة بس، بأسماء مخترعة. **مفيش تشغيل على فايربيز.** */
class RenameSeedCategoriesTest {
    private fun cat(id: String, parent: String?, name: String, order: Int) = Category(id, parent, name, "tag", "#111111", "#EEEEEE", true, order)

    private val before = listOf(
        cat("cat-السيارة", null, "السيارة", 1),
        cat("cat-السيارة--مواقف-وسايس", "cat-السيارة", "مواقف وسايس", 2),
        cat("cat-السيارة--وقود", "cat-السيارة", "وقود", 3),
        cat("cat-تعليم-وتدريب", null, "تعليم وتدريب", 4),
        cat("cat-تعليم-وتدريب--دورات-أونلاين", "cat-تعليم-وتدريب", "دورات أونلاين", 5),
        cat("cat-mine", null, "تصنيف وهمي", 6),
    )

    @Test fun planWritesNothingAndApplyRenamesOnlyTheNames() = runBlocking<Unit> {
        val repo = MemoryCategoryRepository(before)
        val use = RenameSeedCategories(repo)
        val plan = use.plan()
        assertEquals(
            listOf(
                CategoryRename("cat-السيارة--مواقف-وسايس", "مواقف وسايس", "مواقف"),
                CategoryRename("cat-السيارة--وقود", "وقود", "بنزين"),
                CategoryRename("cat-تعليم-وتدريب--دورات-أونلاين", "دورات أونلاين", "دورات إلكترونية"),
            ),
            plan,
        )
        assertEquals(before, repo.listAll(), "الخطة بس ⇒ ولا كتابة")

        val result = use.apply(plan)
        assertEquals(plan, result.applied)
        assertTrue(result.skipped.isEmpty())
        val after = repo.listAll()
        assertEquals(before.map { it.id }, after.map { it.id }, "المعرّفات زي ما هي")
        assertEquals(before.map { it.copy(name = "") }, after.map { it.copy(name = "") }, "الاسم بس اللي اتغير")
        assertEquals(listOf("السيارة", "مواقف", "بنزين", "تعليم وتدريب", "دورات إلكترونية", "تصنيف وهمي"), after.map { it.name })
        assertTrue(use.plan().isEmpty(), "بعد التنفيذ مفيش حاجة تانية")
    }

    @Test fun aLineThatChangedAfterThePlanIsSkipped() = runBlocking<Unit> {
        val repo = MemoryCategoryRepository(before)
        val use = RenameSeedCategories(repo)
        val plan = use.plan()
        // المستخدم غيّر الاسم بنفسه بعد ما الخطة اتعرضت ⇒ ما نكتبش فوقه
        repo.save(before[1].copy(name = "جراج وهمي"))
        val result = use.apply(plan + CategoryRename("cat-مش-موجود", "قديم", "جديد"))
        assertEquals(listOf(plan[1], plan[2]), result.applied)
        assertEquals(listOf(plan[0], CategoryRename("cat-مش-موجود", "قديم", "جديد")), result.skipped)
        assertEquals("جراج وهمي", repo.listAll().single { it.id == before[1].id }.name)
    }
}
