package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** أسماء البذور بالفصحى والأسماء البديلة وخطة تغيير الأسماء (OVERRIDES §66) — على شجرة صغيرة مخترعة. */
class CategoryNamesTest {
    private val raw = RawCategoryTree(
        listOf(
            RawGroup("transport", listOf(RawMain("السيارة", "car", 200.0, 50.0, 40.0, subs = listOf(RawSub("وقود", "fuel"), RawSub("مواقف وسايس", "square-parking", "hasCar"))))),
            RawGroup("home", listOf(RawMain("اتصالات", "phone", 180.0, 50.0, 40.0, subs = listOf(RawSub("جوال", "phone"))))),
            RawGroup(
                "personal",
                listOf(RawMain("مصاريف الشغل", "briefcase", 30.0, 50.0, 40.0, requires = "business", subs = listOf(RawSub("أدوات وبرامج للشغل", "wrench")))),
            ),
        ),
        mapOf("PARKING" to listOf("السيارة", "مواقف وسايس")),
    )

    private fun cat(id: String, parent: String?, name: String) = Category(id, parent, name, "tag", "#111111", "#EEEEEE", true, 0)

    @Test fun saudiSeedGetsMsaNamesWithTheSameIds() {
        val old = buildCategoryTree(raw)
        val msa = buildCountryCategoryTree(raw, SAUDI_PACK)
        assertEquals(old.categories.map { it.id }, msa.categories.map { it.id })
        assertEquals(listOf("السيارة", "وقود", "مواقف", "اتصالات", "جوال", "مصاريف الدوام", "أدوات وبرامج الدوام"), msa.categories.map { it.name })
        assertEquals(old.wordOverrides, msa.wordOverrides, "كلمة PARKING على نفس التصنيف")
        for (pair in listOf("مواقف وسايس" to "مواقف", "مصاريف الشغل" to "مصاريف الدوام", "أدوات وبرامج للشغل" to "أدوات وبرامج الدوام")) {
            assertEquals(msa.aliases[normalizeText(pair.first)], msa.aliases[normalizeText(pair.second)], pair.toString())
        }
        assertEquals("كاش", SAUDI_PACK.cashWalletName)
    }

    @Test fun egyptSeedKeepsTheEgyptianNames() {
        assertTrue(EGYPT_PACK.seedNames.isEmpty())
        assertEquals("كاش", EGYPT_PACK.cashWalletName)
        val names = buildCountryCategoryTree(raw, EGYPT_PACK).categories.map { it.name }
        assertEquals(listOf("السيارة", "وقود", "باركنج", "سايس", "اتصالات", "موبايل", "مصاريف الشغل", "أدوات وبرامج للشغل"), names)
    }

    @Test fun matchingAcceptsTheOldAndTheNewName() {
        assertTrue(sameCategoryName("مصروف البيت", "مصروف المنزل"))
        assertTrue(sameCategoryName(" مصروف  المنزل ", "مصروف البيت"))
        assertTrue(sameCategoryName("مواقف", "مواقف وسايس"))
        assertTrue(sameCategoryName("سحب نقدي", "سحب نقدي"))
        assertFalse(sameCategoryName("مواقف", "وقود"))
        assertFalse(sameCategoryName(null, "مواقف"))
        // الكود اللي بيطابق بالاسم ما اتأثرش بالأسماء اللي ما اتغيرتش
        assertTrue(isNonExpenseSourceCategory("سحب نقدي") && isNonExpenseSourceCategory("محافظ رقمية"))
        assertFalse(isNonExpenseSourceCategory("مصروف المنزل"))
        assertEquals(EconomicKind.INTERNAL_TRANSFER, suggestEconomicKind(SuggestionInput(Direction.OUT, categoryName = "سحب نقدي")).kind)
        assertEquals(EconomicKind.PURCHASE, suggestEconomicKind(SuggestionInput(Direction.OUT, categoryName = "مواقف")).kind)
        assertEquals(EconomicKind.PURCHASE, suggestEconomicKind(SuggestionInput(Direction.OUT, categoryName = "مواقف وسايس")).kind)
    }

    @Test fun planListsOnlySeedNamesInTheirPlace() {
        val categories = listOf(
            cat("c-car", null, "السيارة"),
            cat("c-park", "c-car", "مواقف وسايس"),
            cat("c-work", null, "مصاريف الشغل"),
            cat("c-tools", "c-work", "أدوات وبرامج للشغل"),
            // اسم بذرة بس المستخدم حطه تحت أساسي تاني ⇒ ما يتلمسش
            cat("c-mine", null, "تصنيفي"),
            cat("c-mine-park", "c-mine", "مواقف وسايس"),
            // الاسم الجديد موجود عند أخ ⇒ ما يتغيرش (كان هيعمل اسمين متطابقين)
            cat("c-family", null, "الأسرة والأطفال"),
            cat("c-house", "c-family", "مصروف البيت"),
            cat("c-house2", "c-family", "مصروف المنزل"),
        )
        val plan = planCategoryRename(categories)
        assertEquals(
            listOf(
                CategoryRename("c-park", "مواقف وسايس", "مواقف"),
                CategoryRename("c-work", "مصاريف الشغل", "مصاريف الدوام"),
                CategoryRename("c-tools", "أدوات وبرامج للشغل", "أدوات وبرامج الدوام"),
            ),
            plan,
        )
    }

    @Test fun planFindsASubWhoseMainWasAlreadyRenamed() {
        val categories = listOf(cat("c-work", null, "مصاريف الدوام"), cat("c-tools", "c-work", "أدوات وبرامج للشغل"))
        assertEquals(listOf(CategoryRename("c-tools", "أدوات وبرامج للشغل", "أدوات وبرامج الدوام")), planCategoryRename(categories))
    }

    /** «النقد» اسم محفظة النقد في بذور السعودية بالفصحى ⇒ البحث بيه بيلاقي عمليات النقد زي «كاش». */
    @Test fun searchingTheMsaCashWordFindsCashTransactions() {
        val cash = Transaction(
            id = "t-1", occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE,
            economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = 1_000, currency = Currency.SAR, categoryConfirmed = false,
            excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = true, createdAt = "x", updatedAt = "x",
        )
        for (word in listOf("كاش", "النقد", "نقد")) {
            assertEquals(listOf("cash"), searchTransactions(listOf(SearchableTransaction(cash)), parseQuery(word)).single().matchedFields, word)
        }
    }

    @Test fun planIsEmptyForAnAccountWithTheNewNames() {
        assertTrue(planCategoryRename(buildCountryCategoryTree(raw, SAUDI_PACK).categories).isEmpty())
        assertTrue(planCategoryRename(emptyList()).isEmpty())
    }
}
