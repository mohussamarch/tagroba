package app.masroufy.ui.screens.people

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Project
import app.masroufy.core.ProjectKind
import app.masroufy.core.ProjectRule
import app.masroufy.core.ProjectSummary
import app.masroufy.core.ReviewState
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.TextKey
import app.masroufy.core.projectNetMinor
import app.masroufy.core.subtractMoney
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.text.t
import app.masroufy.usecase.ProjectDetail
import app.masroufy.usecase.ProjectLists
import app.masroufy.usecase.ProjectRow
import app.masroufy.usecase.ProjectTransaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «المشاريع» و«تفاصيل المشروع»: الترتيب والكلام بس — الرقم الكبير جاي من دالة `PeopleMoney.projectHeadline` (نفس اللي في `PeopleGraph`). */
class ProjectsPresenterTest {
    @BeforeTest fun before() = resetTexts()

    @AfterTest fun after() = resetTexts()

    /** نفس حساب `PeopleGraph.projectHeadline` (من `core`) — الاختبار ما بيحسبش بنفسه. */
    private val headline: (ProjectKind, ProjectSummary) -> Long = { k, s ->
        if (k == ProjectKind.WORK) projectNetMinor(s) else subtractMoney(s.spentMinor, s.receivedMinor)
    }

    private fun project(id: String, kind: ProjectKind, deadline: String? = null, archived: Boolean = false) =
        Project(id, "مشروع وهمي $id", "مشروع وهمي $id", archived, "c", kind, deadline)

    private fun summary(spent: Long, received: Long, count: Int, estimated: Int = 0) = ProjectSummary(spent, received, count, estimated, 0)

    @Test
    fun workShowsTheSignedNetAndPersonalShowsWhatItCost() {
        val lists = ProjectLists(
            active = listOf(
                ProjectRow(project("pr-1", ProjectKind.WORK, "2026-10-20"), summary(50_000, 80_000, 3, estimated = 1)),
                ProjectRow(project("pr-2", ProjectKind.WORK), summary(90_000, 10_000, 2)),
                ProjectRow(project("pr-3", ProjectKind.PERSONAL), summary(40_000, 5_000, 2)),
            ),
            archived = listOf(ProjectRow(project("pr-4", ProjectKind.PERSONAL, archived = true), summary(0, 0, 0))),
        )
        val ui = projectsUi(lists, Currency.SAR, TODAY, headline)
        val win = ui.active[0]
        assertEquals(30_000, win.headline!!.line.minor)
        assertEquals(AmountTone.INCOME, win.headline!!.tone)
        assertNotNull(win.approx, "عملية نوعها تقديري ⇒ «تقريبي»")
        val loss = ui.active[1]
        assertEquals(-80_000, loss.headline!!.line.minor, "مشروع الشغل الخسران بيظهر سالب — مفيش إخفاء")
        assertEquals(AmountTone.EXPENSE, loss.headline!!.tone)
        assertTrue(loss.headline!!.colorExpense)
        assertNull(loss.approx)
        val personal = ui.active[2]
        assertEquals(35_000, personal.headline!!.line.minor, "الشخصي = كلّفني (صرفت − جاءك)")
        assertEquals(AmountTone.PLAIN, personal.headline!!.tone)
        assertEquals(t(TextKey.PROJECTS_Q_PERSONAL), personal.question)
        assertEquals(t(TextKey.PROJECTS_NO_DEADLINE), personal.deadline)
        assertEquals(1, ui.archived.size)
    }

    @Test
    fun aProjectWithoutOperationsHasNoNumberAtAll() {
        val ui = projectsUi(ProjectLists(listOf(ProjectRow(project("pr-1", ProjectKind.WORK), summary(0, 0, 0))), emptyList()), Currency.SAR, TODAY, headline)
        val card = ui.active.single()
        assertNull(card.headline, "مفيش عمليات ⇒ «—» مش صفر مؤكد")
        assertEquals(t(TextKey.PROJECTS_NO_OPS), card.line)
    }

    @Test
    fun aPassedDeadlineSaysSo() {
        assertTrue(deadlineText("2026-09-01", TODAY).contains(t(TextKey.PROJECTS_PASSED)))
        assertTrue(!deadlineText("2026-10-20", TODAY).contains(t(TextKey.PROJECTS_PASSED)))
    }

    @Test
    fun detailMapsOperationsRulesAndTheFormulaByKind() {
        val rule = ProjectRule("r-1", "pr-1", "مورد وهمي", RuleMatchMode.STARTS_WITH, "in", true, "c")
        val d = ProjectDetail(
            project("pr-1", ProjectKind.WORK, "2026-11-01"),
            summary(20_000, 50_000, 2),
            listOf(rule),
            listOf(
                ProjectTransaction(txn("t-1", 50_000, Direction.IN, review = ReviewState.NEEDS_REVIEW), "rule"),
                ProjectTransaction(txn("t-2", 20_000), "manual"),
            ),
        )
        val ui = projectDetailUi(d, Currency.SAR, headline(ProjectKind.WORK, d.summary))
        assertEquals(30_000, ui.headline!!.line.minor)
        assertEquals(20_000, ui.out.minor)
        assertEquals(50_000, ui.inn.minor)
        assertEquals(t(TextKey.PROJECT_DETAIL_FORMULA_WORK), ui.formula)
        val (byRule, manual) = ui.ops
        assertTrue(byRule.byRule && byRule.unsure)
        assertEquals(AmountTone.INCOME, byRule.tone)
        assertTrue(!manual.byRule && !manual.unsure)
        assertEquals(AmountTone.EXPENSE, manual.tone)
        val r = ui.rules.single()
        assertEquals(t(TextKey.PROJECT_DETAIL_MODE_STARTS), r.mode)
        assertEquals(t(TextKey.PROJECT_DETAIL_DIR, t(TextKey.PROJECT_DETAIL_DIR_IN)), r.direction)
    }

    @Test
    fun egyptianWordingDiffersFromSaudi() {
        val saudi = t(TextKey.PROJECTS_Q_WORK)
        egyptian()
        assertNotEquals(saudi, t(TextKey.PROJECTS_Q_WORK), "مساحة مصر بالعامية (OVERRIDES §66)")
    }
}
