package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** «حساب لكل بلد» (OVERRIDES §41 · §64) — المنطق النقي. بيشتغل على محاكي الآيفون كمان (commonTest). */
class SpacesTest {
    @Test fun accountAndSpaceGroupsSplitEveryBackupGroupExactlyOnce() {
        assertTrue(ACCOUNT_GROUPS.intersect(SPACE_GROUPS.toSet()).isEmpty(), "مجموعة في الحساب والمساحة مع بعض")
        assertEquals(BACKUP_GROUPS.toSet(), (ACCOUNT_DATA_GROUPS + SPACE_GROUPS).toSet(), "كل مجموعة في النسخة ليها مكان واحد")
        assertEquals(listOf("merchants", "people", "tags", "occasions", "personProfiles", "personRelations"), ACCOUNT_DATA_GROUPS, "المشترك بقرار §41 و§64 + دواير الأشخاص وصلاتهم (جلسة 16) بس")
        for (g in listOf("transactions", "wallets", "categories", "rules", "budgets", "obligations", "settlements", "allocations", "incomeSources", "lifeEvents", "eventLinks", "zakatYears", MERCHANT_CATEGORIES_GROUP)) {
            assertTrue(g in SPACE_GROUPS, "$g لازم يبقى جوه البلد")
        }
        assertTrue(SPACES_GROUP in ACCOUNT_GROUPS && SPACE_TRANSFERS_GROUP in ACCOUNT_GROUPS)
    }

    @Test fun saudiIsBuiltInAndNeverStored() {
        val saudi = defaultSpace()
        assertEquals(DEFAULT_SPACE_ID, saudi.id)
        assertEquals("SA", saudi.countryCode)
        assertEquals(Currency.SAR, saudi.currency)
        assertEquals(listOf(DEFAULT_SPACE_ID), allSpaces(emptyList()).map { it.id })
    }

    @Test fun oneSpacePerCountryAndCurrencyFromThePack() {
        val egypt = newSpaceFor("eg", emptyList(), "2026-10-04T10:00:00.000Z")
        assertEquals("eg", egypt.id)
        assertEquals("EG", egypt.countryCode)
        assertEquals(Currency.EGP, egypt.currency)
        assertFalse(egypt.archived)
        assertFailsWith<SpaceError>("مصر مرتين") { newSpaceFor("EG", listOf(egypt), "x") }
        assertFailsWith<SpaceError>("حتى لو القديمة مؤرشفة") { newSpaceFor("EG", listOf(egypt.copy(archived = true)), "x") }
        assertFailsWith<SpaceError>("السعودية موجودة من الأول") { newSpaceFor("SA", emptyList(), "x") }
        assertFailsWith<SpaceError>("بلد مش في الحزم") { newSpaceFor("XX", emptyList(), "x") }
    }

    @Test fun activeSpaceFallsBackToSaudi() {
        val egypt = newSpaceFor("EG", emptyList(), "2026-10-04T10:00:00.000Z")
        assertEquals("eg", activeSpaceOf("eg", listOf(egypt)).id)
        assertEquals(DEFAULT_SPACE_ID, activeSpaceOf(null, listOf(egypt)).id)
        assertEquals(DEFAULT_SPACE_ID, activeSpaceOf("ae", listOf(egypt)).id, "بلد مش موجودة")
        assertEquals(DEFAULT_SPACE_ID, activeSpaceOf("eg", listOf(egypt.copy(archived = true))).id, "مؤرشفة")
        assertTrue(isSpaceId("eg") && isSpaceId(DEFAULT_SPACE_ID))
        assertFalse(isSpaceId("../x") || isSpaceId("EG") || isSpaceId(null) || isSpaceId(""))
        assertEquals(listOf(DEFAULT_SPACE_ID, "eg"), allSpaces(listOf(egypt)).map { it.id }, "السعودية الأول")
    }

    @Test fun alertThreadsOfOtherCountriesArePrefixedAndSaudiStaysAsItWas() {
        val c = AlertCandidate(AlertKind.DUE_SOON, "due|plan|ip-1|2026-10-10", "عنوان", "تفاصيل")
        val egypt = newSpaceFor("EG", emptyList(), "2026-10-04T10:00:00.000Z")
        val sa = c.inSpace(defaultSpace(), labelled = true)
        assertEquals(c.threadKey, sa.threadKey, "موضوع السعودية زي ما هو")
        assertEquals(c.eventKey, sa.eventKey, "إيصالات «اتبعت» القديمة شغالة")
        assertEquals(defaultSpace().name, sa.spaceLabel)
        val eg = c.inSpace(egypt, labelled = true)
        assertEquals("eg:due|plan|ip-1|2026-10-10", eg.threadKey)
        assertEquals("eg", eg.spaceId)
        assertEquals(egypt.name, eg.spaceLabel)
        assertEquals(null, c.inSpace(egypt, labelled = false).spaceLabel, "بلد واحدة ⇒ من غير اسم")
        assertEquals(c.title to c.body, eg.title to eg.body, "النص هو هو — الاسم حقل لوحده")
    }

    private val raw = RawCategoryTree(
        listOf(
            RawGroup(
                "transport",
                listOf(RawMain("السيارة", "car", 200.0, 50.0, 40.0, subs = listOf(RawSub("وقود", "fuel"), RawSub("مواقف وسايس", "square-parking", "hasCar"), RawSub("مواصلات عامة", "train-front")))),
            ),
            RawGroup("home", listOf(RawMain("اتصالات", "phone", 180.0, 50.0, 40.0, subs = listOf(RawSub("جوال", "phone"), RawSub("إنترنت منزلي", "wifi"))))),
        ),
        mapOf("PARKING" to listOf("السيارة", "مواقف وسايس"), "FUEL" to listOf("السيارة", "وقود")),
    )

    @Test fun egyptTreeIsTheSaudiTreePlusTheListedDelta() {
        val saudi = buildCountryCategoryTree(raw, SAUDI_PACK)
        assertEquals(buildCategoryTree(raw), saudi, "السعودية من غير أي فرق")
        val egypt = buildCountryCategoryTree(raw, EGYPT_PACK)
        fun subsOf(tree: BuiltCategoryTree, main: String): List<Category> {
            val parent = tree.categories.single { it.parentId == null && it.name == main }
            return tree.categories.filter { it.parentId == parent.id }
        }
        assertEquals(listOf("وقود", "باركنج", "سايس", "مواصلات عامة"), subsOf(egypt, "السيارة").map { it.name })
        assertEquals(listOf("موبايل", "إنترنت منزلي"), subsOf(egypt, "اتصالات").map { it.name })
        val parking = egypt.categories.single { it.name == "باركنج" }
        assertEquals("hasCar", parking.requires, "الاسم بس اتغير — الشرط والرمز زي ما هم")
        assertEquals("square-parking", parking.iconKey)
        assertEquals("hasCar", egypt.categories.single { it.name == "سايس" }.requires)
        assertEquals(parking.id, egypt.wordOverrides[normalizeText("PARKING")], "كلمة القاعدة اللي كانت على «مواقف وسايس» بقت على «باركنج»")
        assertEquals(parking.id, egypt.aliases[normalizeText("مواقف وسايس")], "القواعد بالاسم القديم بتوصل للجديد")
        // باقي التصنيفات بنفس المعرّفات بالظبط — الفرق 3 فرعيات بس
        val changed = setOf("باركنج", "سايس", "موبايل")
        assertEquals(
            saudi.categories.filter { it.name != "مواقف وسايس" && it.name != "جوال" }.map { it.id },
            egypt.categories.filter { it.name !in changed }.map { it.id },
        )
    }

    @Test fun aDeltaThatNoLongerFitsTheTreeFailsLoudly() {
        val broken = RawCategoryTree(listOf(RawGroup("home", listOf(RawMain("اتصالات", "phone", 1.0, 1.0, 1.0, subs = listOf(RawSub("جوال", "phone")))))))
        assertFailsWith<SeedError> { applyCategoryDelta(broken, EGYPT_CATEGORY_DELTA) }
    }
}
