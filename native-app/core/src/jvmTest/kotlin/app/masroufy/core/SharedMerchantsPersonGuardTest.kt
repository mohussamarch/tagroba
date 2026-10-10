package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OVERRIDES §80 · CLAUDE.md «الأمان» البند 6: اسم شخص أو طرف تحويل عمره ما يترفع للقايمة المشتركة. كل الأسامي مخترعة. */
class SharedMerchantsPersonGuardTest {
    private val people = listOf("سامي التجريبي", "TEST PERSON", "عبد الودود", "****4321 SAMPLE OTHER")

    @Test fun aPersonNameIsNeverAMerchant() {
        assertTrue(namesAPerson("سامي التجريبي", people), "نفس الاسم")
        assertTrue(namesAPerson("  test   person ", people), "نفس الاسم بحروف ومسافات مختلفة")
        assertTrue(namesAPerson("STC PAY TEST PERSON", people), "اسم الشخص جوه وصف العملية")
        assertTrue(namesAPerson("سامي", people), "الاسم الأول لوحده جزء من اسم شخص")
        assertTrue(namesAPerson("عبدالودود", people), "من غير مسافة")
        assertTrue(namesAPerson("تحويل الى عبدالودود", people), "من غير مسافة جوه وصف")
        assertTrue(namesAPerson("SAMPLE OTHER", people), "طرف تحويل — آخر 4 أرقام والنجوم مش جزء من الاسم")
        assertTrue(namesAPerson("سامى التجريبى", people), "الياء والألف المقصورة بعد التوحيد")
    }

    @Test fun realMerchantsStillShare() {
        assertFalse(namesAPerson("TEST COFFEE HOUSE", people))
        assertFalse(namesAPerson("مطعم الوادي", people))
        assertFalse(namesAPerson("PERSONAL CARE STORE", people), "كلمة كاملة بس — مش جزء من كلمة")
        assertFalse(namesAPerson("", people))
        assertFalse(namesAPerson("TEST COFFEE HOUSE", listOf("", "-", "1234")), "أسامي فاضية أو أرقام بس ما بتمنعش حاجة")
    }

    @Test fun theContributionIsDroppedForAPersonName() {
        val tree = setOf("cat-food")
        fun share(name: String) = contributionFor(EconomicKind.PURCHASE, Direction.OUT, name, "cat-food", tree, null, people)
        assertNull(share("TEST PERSON"))
        assertNull(share("Transfer to TEST PERSON"))
        assertNull(share("SAMPLE OTHER"))
        val shop = assertNotNull(share("Test Coffee House"))
        assertEquals("TEST COFFEE HOUSE", shop.normalizedName)
        assertFalse(shop.confirmed)
    }
}
