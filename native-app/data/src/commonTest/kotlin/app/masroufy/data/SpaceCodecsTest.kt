package app.masroufy.data

import app.masroufy.core.ACCOUNT_GROUPS
import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.Currency
import app.masroufy.core.MerchantCategory
import app.masroufy.core.SPACE_GROUPS
import app.masroufy.core.Space
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «حساب لكل بلد» (OVERRIDES §41 · §64): المحوّلات الجديدة، وإن كل مجموعة ليها مكان واحد بس (الحساب أو البلد). */
class SpaceCodecsTest {
    @Test
    fun everyStoredGroupIsEitherAccountLevelOrInsideACountry() {
        val groups = DocumentCodecs.byGroup.keys
        for (g in groups) assertTrue(g in ACCOUNT_GROUPS || g in SPACE_GROUPS, "المجموعة $g مالهاش مكان — لا حساب ولا بلد")
        assertTrue(ACCOUNT_GROUPS.none { it in SPACE_GROUPS }, "مجموعة في المكانين")
        // اللي في النسخة الشاملة ومالوش محوّل = مجموعة اتنست في التخزين
        for (g in BACKUP_GROUPS) assertTrue(g in groups, "$g في النسخة ومالهاش محوّل")
    }

    @Test
    fun spaceAndMerchantCategoryRoundTrip() {
        val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-10-04T10:00:00.000Z")
        val d = SpaceCodecs.spaces.toStore(egypt)
        assertEquals(egypt, SpaceCodecs.spaces.decode(d))
        assertEquals(false, d["archived"], "الأرشفة بتتكتب صريح")
        assertEquals("EGP", d["currency"])
        assertEquals("eg", SpaceCodecs.spaces.id(egypt))
        val m = MerchantCategory("merch-00012", "cat-x")
        assertEquals(m, SpaceCodecs.merchantCategories.decode(SpaceCodecs.merchantCategories.toStore(m)))
        assertEquals("merch-00012", SpaceCodecs.merchantCategories.id(m), "معرّف المستند = معرّف التاجر ⇒ تصنيف واحد للتاجر في البلد")
    }
}
