package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * قايمة البنود الاختيارية (OVERRIDES §68 — رد المالك على صفحة الضبط: الهدايا والصالون/الحلاق) على **شجرة التطبيق الحقيقية**
 * للسعودية ومصر + تصنيفات «هدايا › نقوط» الثابتة: كل معرّف في القايمة موجود في الشجرتين، فلو الشجرة اتغيرت الاختبار بيقع بدل ما
 * البند يخرج من القايمة في صمت.
 */
class AdvisorDiscretionaryRealTreeTest {
    private fun tree(pack: CountryPack) = buildCountryCategoryTree(RealSeeds.tree, pack).categories + GiftCategories.defaults()

    @Test fun everyListedIdExistsInTheSaudiAndEgyptTrees() {
        for (pack in listOf(SAUDI_PACK, EGYPT_PACK)) {
            val categories = tree(pack)
            val byId = categories.associateBy { it.id }
            val parentOf = categories.associate { it.id to it.parentId }
            for (id in DISCRETIONARY_CATEGORY_IDS + NOT_DISCRETIONARY_CATEGORY_IDS) assertTrue(id in byId, "${pack.code}: $id مش في الشجرة")
            // الحلاق والصالون فرعين تحت «العناية الشخصية» — بالاسم في الشجرة
            assertEquals("حلاقة", byId.getValue("cat-العنايه-الشخصيه--حلاقه").name, pack.code)
            assertEquals("تجميل", byId.getValue("cat-العنايه-الشخصيه--تجميل").name, pack.code)
            val chosen = categories.filter { isDiscretionary(it.id, parentOf) }.map { it.id }.toSet()
            assertTrue(GiftCategories.ROOT in chosen && "cat-العنايه-الشخصيه--حلاقه" in chosen && "cat-العنايه-الشخصيه--تجميل" in chosen, pack.code)
            assertFalse(GiftCategories.EVENT_GIFTS in chosen, "${pack.code}: النقوط مش اختيارية")
            // باقي العناية الشخصية (منتجات عناية · ملابس · أحذية) والأساسي نفسه برا
            val personal = categories.filter { it.parentId == "cat-العنايه-الشخصيه" }.map { it.id }.toSet()
            assertEquals(
                setOf("cat-العنايه-الشخصيه--حلاقه", "cat-العنايه-الشخصيه--تجميل", "cat-العنايه-الشخصيه--عطور", "cat-العنايه-الشخصيه--اكسسوارات"),
                personal.filter { it in chosen }.toSet(), pack.code,
            )
            assertFalse("cat-العنايه-الشخصيه" in chosen)
        }
    }
}
