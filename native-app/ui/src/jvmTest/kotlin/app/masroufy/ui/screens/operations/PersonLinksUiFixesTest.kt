package app.masroufy.ui.screens.operations

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.DuesCategories
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonBalance
import app.masroufy.core.Texts
import app.masroufy.core.seedCategoryId
import app.masroufy.ui.screens.people.NEEDS_PERSON_SHOWN
import app.masroufy.ui.screens.people.needsShown
import app.masroufy.usecase.PersonObligationRow
import app.masroufy.usecase.PersonRow
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** مراجعة r05 (F5 · F7 · F8 · F10) — منطق الشاشة من غير رسم. كل الأسماء والأرقام مخترعة. */
class PersonLinksUiFixesTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun cat(id: String, parent: String?, name: String, group: String?, order: Int) =
        Category(id, parent, name, "x", "#000000", "#ffffff", true, order, groupKey = group)

    private val transfers = seedCategoryId("تحويلات")
    private val toPeople = seedCategoryId("تحويلات", "تحويل لأشخاص")
    private val tree = listOf(
        cat(seedCategoryId("مطاعم"), null, "مطاعم", "daily", 1),
        cat(transfers, null, "تحويلات", "movement", 2),
        cat(toPeople, transfers, "تحويل لأشخاص", null, 3),
        cat(DuesCategories.ROOT, null, "المستحقات", null, 900),
        cat(seedCategoryId("العناية الشخصية"), null, "العناية الشخصية", "daily", 4),
    )

    @Test fun f5TheSeededPersonCategoriesAreAlwaysOfferedEvenForOutgoing() {
        val out = pickerChoices(tree, spendOnly = true, currentId = null).map { it.id }
        assertTrue(toPeople in out, "«تحويل لأشخاص» فرعي ومجموعته حركة — لازم يظهر عشان «مع مين؟»")
        assertTrue(DuesCategories.ROOT in out)
        assertTrue(transfers !in out, "«تحويلات» نفسه مش مصروف ومش فلوس مع شخص")
        val incoming = pickerChoices(tree, spendOnly = false, currentId = null).map { it.id }
        assertEquals(incoming.distinct(), incoming, "مفيش تصنيف متكرر")
        assertTrue(toPeople in incoming)
    }

    @Test fun f7TheLinksOwnDebtIsNotShownTwiceOnTheCard() {
        val p = Person("p-1", "شخص تجريبي")
        val own = PersonObligationRow(Obligation("obl-9", p.id, "t1", ObligationKind.RECEIVABLE, 4_000, Currency.SAR), 4_000)
        val legacy = PersonObligationRow(Obligation("o-old", p.id, "t1", ObligationKind.RECEIVABLE, 1_000, Currency.SAR), 1_000)
        val rows = listOf(PersonRow(p, PersonBalance(p.id, 5_000, 0, 0), listOf(own, legacy)))
        assertEquals(listOf(1_000L), existingPersonLinks(rows, "t1", skipObligation = "obl-9").map { it.amountMinor })
        assertEquals(2, existingPersonLinks(rows, "t1").size)
    }

    @Test fun f8ShowMoreOpensEveryRow() {
        val rows = (1..12).toList()
        assertEquals(NEEDS_PERSON_SHOWN, needsShown(rows, expanded = false).size)
        assertEquals(rows, needsShown(rows, expanded = true), "السطر السادس وما بعده بيتوصلله بضغطة")
    }

    @Test fun f10ANewPersonIsAddedOnceEvenWhenTheFirstLinkFails() = runBlocking {
        var created = 0
        var kept: String? = null
        val create: suspend () -> String = { created++; if (created > 1) error("الاسم موجود") else "p-new" }
        var attempts = 0
        assertFailsWith<IllegalStateException> {
            withPersonOnce(kept, create, { kept = it }) { attempts++; error("العملية دي عليها دين") }
        }
        assertEquals("p-new", kept, "الشخص اتحفظ في اللوحة رغم إن الربط فشل")
        val out = withPersonOnce(kept, create, { kept = it }) { p -> attempts++; "linked:$p" }
        assertEquals("linked:p-new", out)
        assertEquals(1, created, "المحاولة التانية ما ضافتش الشخص تاني")
        assertEquals(2, attempts)
    }
}
