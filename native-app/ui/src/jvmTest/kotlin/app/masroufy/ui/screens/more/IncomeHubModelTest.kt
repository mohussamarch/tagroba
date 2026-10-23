package app.masroufy.ui.screens.more

import app.masroufy.core.Currency
import app.masroufy.core.EmployerCountryPay
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.LinkedEmployer
import app.masroufy.core.PayerQuestion
import app.masroufy.core.R06Key
import app.masroufy.core.Space
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** «مصادر الدخل» بعد الدمج (OVERRIDES §112-6). أسماء ومبالغ مخترعة، بالوحدة الصغرى. */
class IncomeHubModelTest {
    private fun src(id: String, kind: IncomeSourceKind, start: String, end: String? = null, cur: Currency = Currency.SAR, expected: Long? = null) =
        IncomeSource(id, "جهة $id", "جهة $id", kind, cur, start, end, expectedDayOfMonth = 28, expectedMinor = expected)

    @Test
    fun hubListsAllSevenKindsWhenThereIsNoData() {
        val tiles = incomeHub(emptyList()).tiles
        assertEquals(7, tiles.size)
        assertEquals(IncomeSourceKind.entries.toSet(), tiles.map { it.kind }.toSet())
        assertEquals(HUB_KINDS, tiles.map { it.kind })
        assertTrue(tiles.all { it.state == TileState.EMPTY && it.sub == t(R06Key.INC_ADD) })
    }

    @Test
    fun tileShowsCurrentNameOrPastOrAdd() {
        val tiles = incomeHub(
            listOf(
                src("a", IncomeSourceKind.JOB, "2026-04-28", expected = 1_250_000),
                src("b", IncomeSourceKind.JOB, "2025-03-01", "2026-04-27"),
                src("c", IncomeSourceKind.CLIENT, "2026-01-01", "2026-06-30"),
                src("d", IncomeSourceKind.RENT, "2026-01-01"),
            ),
        ).tiles.associateBy { it.kind }
        assertEquals(TileState.CURRENT, tiles.getValue(IncomeSourceKind.JOB).state)
        assertEquals("جهة a", tiles.getValue(IncomeSourceKind.JOB).sub)
        assertEquals(TileState.PAST_ONLY, tiles.getValue(IncomeSourceKind.CLIENT).state)
        assertEquals(t(R06Key.INC_BEFORE, "جهة c"), tiles.getValue(IncomeSourceKind.CLIENT).sub)
        assertEquals(TileState.CURRENT, tiles.getValue(IncomeSourceKind.RENT).state, "الإيجار نوع من الأنواع")
        assertEquals(TileState.EMPTY, tiles.getValue(IncomeSourceKind.PENSION).state)
    }

    @Test
    fun kindPageSplitsCurrentFromEndedTimeline() {
        val all = listOf(
            src("now", IncomeSourceKind.JOB, "2026-04-28", expected = 1_250_000),
            src("old1", IncomeSourceKind.JOB, "2023-01-01", "2025-02-28"),
            src("old2", IncomeSourceKind.JOB, "2025-03-01", "2026-04-27"),
            src("rent", IncomeSourceKind.RENT, "2026-01-01"),
        )
        val page = kindPage(IncomeSourceKind.JOB, all)
        assertEquals(listOf("now"), page.now.map { it.id })
        assertEquals(listOf("old2", "old1"), page.past.map { it.id }, "الأحدث فوق")
        assertEquals(listOf(amountLabel(1_250_000, Currency.SAR)), page.now.single().amounts)
        assertTrue(page.now.single().line.contains(payText(all[0])))
        assertEquals(listOf(KindAction.CHANGE, KindAction.ADD_OTHER), page.actions)
        assertTrue(page.employersLink, "«جهات العمل» بقت جوه نوع الشغل")
        assertEquals(t(R06Key.INC_PAST_WORK), page.pastLabel)

        val rent = kindPage(IncomeSourceKind.RENT, all)
        assertEquals(listOf(KindAction.ADD), rent.actions)
        assertFalse(rent.employersLink)
        val empty = kindPage(IncomeSourceKind.PENSION, all)
        assertTrue(empty.now.isEmpty() && empty.past.isEmpty())
        assertEquals(listOf(KindAction.ADD), empty.actions)
    }

    @Test
    fun employerLinkedAcrossCountriesShowsBothAmounts() {
        val sa = src("sa", IncomeSourceKind.JOB, "2026-01-01", expected = 1_250_000)
        val linked = listOf(
            LinkedEmployer("k", "جهة sa", listOf(EmployerCountryPay("default", "sa", Currency.SAR, 1_250_000), EmployerCountryPay("eg", "eg1", Currency.EGP, 900_000))),
        )
        assertEquals(listOf(amountLabel(1_250_000, Currency.SAR), amountLabel(900_000, Currency.EGP)), expectedAmounts(sa, linked))
        assertEquals(listOf(amountLabel(1_250_000, Currency.SAR)), expectedAmounts(sa, emptyList()))
        assertTrue(expectedAmounts(sa.copy(expectedMinor = null), emptyList()).isEmpty(), "ما اتكتبش ⇒ مفيش رقم (مش صفر)")
    }

    @Test
    fun employersMenuIsGoneAndIncomeOpensTheHub() {
        val routes = moreGroups(moreView(null, 28, null, Space(app.masroufy.core.DEFAULT_SPACE_ID, "السعودية", "SA", Currency.SAR, "2025-01-01T00:00:00Z"))).flatMap { g -> g.items.map { it.to } }
        assertFalse(EmployersRoute in routes, "«جهات العمل» ما بقتش في «المزيد»")
        assertTrue(IncomeHubRoute in routes)
    }

    @Test
    fun salaryQuestionIsNotOnTheHub() {
        val types = (IncomeHubView::class.java.declaredFields + KindPageView::class.java.declaredFields + KindTile::class.java.declaredFields).map { it.type }
        assertFalse(types.any { PayerQuestion::class.java.isAssignableFrom(it) })
    }
}
