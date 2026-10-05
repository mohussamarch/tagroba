package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * أسماء البذور بالفصحى (OVERRIDES §66) على **شجرة التطبيق الحقيقية** وملفات المراجع المرفقة معاه:
 * الحساب السعودي الجديد بالأسماء الجديدة، وحساب بالأسماء القديمة (زي حساب المالك قبل موافقته) — الاتنين بيشتغلوا.
 */
class SeedNamesRealTreeTest {
    private val oldTree by lazy { buildCategoryTree(RealSeeds.tree) }
    private val saudi by lazy { buildCountryCategoryTree(RealSeeds.tree, SAUDI_PACK) }

    @Test fun everyMsaSeedNameFindsItsPlaceInTheRealTree() {
        val oldById = oldTree.categories.associateBy { it.id }
        val renamed = saudi.categories.filter { it.name != oldById.getValue(it.id).name }
        assertEquals(SAUDI_MSA_SEED_NAMES.size, renamed.size, "كل اسم لقى مكانه: ${renamed.map { it.name }}")
        assertEquals(SAUDI_MSA_SEED_NAMES.map { it.to }.sorted(), renamed.map { it.name }.sorted())
        assertEquals(oldTree.categories.map { it.id }, saudi.categories.map { it.id }, "المعرّفات زي ما هي بالظبط")
        assertEquals(oldTree.categories.map { it.copy(name = "") }, saudi.categories.map { it.copy(name = "") }, "الاسم بس اللي اتغير")
        assertEquals(oldTree.wordOverrides, saudi.wordOverrides)
        for (change in SAUDI_MSA_SEED_NAMES) {
            val id = saudi.aliases[normalizeText(change.to)]
            assertTrue(id != null && id == saudi.aliases[normalizeText(change.from)], "«${change.from}» و«${change.to}» بيوصلوا لنفس التصنيف")
        }
    }

    /** نفس ملفات المراجع (القواعد والتجار بأسمائها القديمة) ⇒ نفس المعرّفات بالظبط على الشجرة القديمة والجديدة. */
    @Test fun referencesWithOldNamesLoadTheSameOnTheMsaTree() {
        val before = loadReferences(RealSeeds.rules, RealSeeds.merchants, oldTree.categories, oldTree)
        val after = loadReferences(RealSeeds.rules, RealSeeds.merchants, saudi.categories, saudi)
        assertEquals(before, after)
        assertTrue(before.rules.isNotEmpty() && before.merchants.isNotEmpty())
    }

    @Test fun planOnTheOldTreeRenamesExactlyTheSeedNamesAndEndsAtTheMsaTree() {
        val plan = planCategoryRename(oldTree.categories)
        assertEquals(SAUDI_MSA_SEED_NAMES.size, plan.size, plan.toString())
        val renamed = oldTree.categories.map { c -> plan.firstOrNull { it.id == c.id }?.let { c.copy(name = it.newName) } ?: c }
        assertEquals(saudi.categories, renamed, "بعد التنفيذ = شجرة الحساب السعودي الجديد بالحرف")
        assertTrue(planCategoryRename(saudi.categories).isEmpty(), "حساب بالأسماء الجديدة ⇒ مفيش حاجة تتغير")
    }

    @Test fun egyptTreeIsUnchanged() {
        val egypt = buildCountryCategoryTree(RealSeeds.tree, EGYPT_PACK)
        val before = buildCategoryTree(applyCategoryDelta(RealSeeds.tree, EGYPT_CATEGORY_DELTA))
        assertEquals(before.categories, egypt.categories, "مصر بنفس أسمائها ومعرّفاتها قبل الفصحى")
        val names = egypt.categories.map { it.name }
        assertTrue("مصاريف الشغل" in names && "دورات أونلاين" in names && "باركنج" in names, "مصر بالمصري")
    }
}
