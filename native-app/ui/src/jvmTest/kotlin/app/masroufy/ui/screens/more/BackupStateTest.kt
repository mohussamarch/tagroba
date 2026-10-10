package app.masroufy.ui.screens.more

import app.masroufy.core.UiKey
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Language
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t
import app.masroufy.usecase.BackupPlanLine
import app.masroufy.usecase.BackupProfilePlan
import app.masroufy.usecase.FullBackupFile
import app.masroufy.usecase.FullBackupPlan
import app.masroufy.usecase.SpaceRestorePlan
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** النسخة والاسترجاع: الأعداد من خطة الدمج بالظبط، والتصدير من تاريخ لتاريخ شامل الطرفين. */
class BackupStateTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test
    fun exportRangeCountsBothEnds() {
        val r = exportRange("2026-09-28", "2026-10-07", "2026-10-09")
        assertTrue(r.ok)
        assertEquals(10, r.days)
        assertEquals(t(UiKey.BAK_RANGE_LINE, fullDate("2026-09-28")!!, fullDate("2026-10-07")!!, t(UiKey.BAK_DAY_FEW, sentenceNumber(10))), r.line)
        assertEquals(1, exportRange("2026-10-09", "2026-10-09", "2026-10-09").days)
    }

    @Test
    fun exportRangeRejectsBadInput() {
        val future = exportRange("2026-10-01", "2026-10-10", "2026-10-09")
        assertFalse(future.ok)
        assertEquals(t(UiKey.BAK_RANGE_BAD_DATE), future.line)
        assertEquals(t(UiKey.BAK_RANGE_BAD_DATE), exportRange("2026-13-01", "2026-10-01", "2026-10-09").line)
        val reversed = exportRange("2026-10-05", "2026-10-01", "2026-10-09")
        assertFalse(reversed.ok)
        assertEquals(t(UiKey.BAK_RANGE_BAD_ORDER), reversed.line)
        assertEquals(0, reversed.days)
    }

    @Test
    fun fileNamesCarryTheDates() {
        assertEquals("masroufy-backup-2026-10-09.json", backupFileName("2026-10-09"))
        assertEquals("masroufy-2026-09-28_2026-10-07.csv", exportFileName("2026-09-28", "2026-10-07"))
    }

    private val file = FullBackupFile("2026-10-09T10:00:00Z", emptyMap(), null, false, "", emptyMap())
    private val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-05-12")

    private fun plan(profile: BackupProfilePlan, spaces: List<SpaceRestorePlan> = emptyList(), warnings: List<String> = emptyList()) = FullBackupPlan(
        file,
        listOf(
            BackupPlanLine("transactions", "العمليات", 40, 32, 8, null),
            BackupPlanLine("goals", "الأهداف", 0, 0, 0, null),
            BackupPlanLine("wallets", "المحافظ", 2, 0, 2, null),
        ),
        profile, totalToAdd = 32 + spaces.sumOf { it.totalToAdd }, warnings = warnings, spaces = spaces,
    )

    @Test
    fun planViewHidesEmptyGroupsAndKeepsTheUseCaseCounts() {
        val v = planView(plan(BackupProfilePlan(incoming = true, toAdd = false)))
        assertEquals(32, v.totalToAdd)
        assertEquals(42, v.incoming)
        assertEquals(UiKey.RST_PROFILE_KEEP, v.profileLine)
        val root = v.spaces.single()
        assertEquals(t(UiKey.RST_ROOT), root.title)
        assertNull(root.isNew)
        assertEquals(listOf("العمليات", "المحافظ"), root.rows.map { it.label }, "المجموعة اللي مفيهاش حاجة جاية بتستخبى")
        assertEquals(PlanRow("العمليات", 40, 32, 8), root.rows[0])
        assertEquals(32, root.toAdd)
    }

    @Test
    fun planViewListsOtherCountriesAndWarnings() {
        val eg = SpaceRestorePlan(egypt, "eg", isNew = true, lines = listOf(BackupPlanLine("transactions", "العمليات", 5, 5, 0, null)), totalToAdd = 5)
        val v = planView(plan(BackupProfilePlan(incoming = true, toAdd = true), listOf(eg), listOf("ملاحظة من الملف")))
        assertEquals(UiKey.RST_PROFILE_ADD, v.profileLine)
        assertEquals(2, v.spaces.size)
        assertEquals(t(TextKey.COUNTRY_EG), v.spaces[1].title)
        assertEquals(true, v.spaces[1].isNew)
        assertEquals(47, v.incoming)
        assertEquals(37, v.totalToAdd)
        assertEquals(listOf("ملاحظة من الملف"), v.notes)
        assertEquals(UiKey.RST_PROFILE_NONE, planView(plan(BackupProfilePlan(incoming = false, toAdd = false))).profileLine)
    }

    @Test
    fun planSpaceLineBeforeAndAfterRestore() {
        val s = PlanSpace("x", null, emptyList(), 32)
        assertEquals(t(UiKey.RST_WILL_ADD_N, t(UiKey.RST_REC_MANY, sentenceNumber(32))), planSpaceLine(s, done = false))
        assertEquals(t(UiKey.RST_ADDED_N, t(UiKey.RST_REC_MANY, sentenceNumber(32))), planSpaceLine(s, done = true))
        assertEquals(t(UiKey.RST_ALL_THERE), planSpaceLine(s.copy(toAdd = 0), done = false))
        assertEquals(t(UiKey.RST_WILL_ADD_N, t(UiKey.RST_REC_ONE)), planSpaceLine(s.copy(toAdd = 1), done = false))
    }

    @Test
    fun egyptianWording() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("كله موجود عندك", planSpaceLine(PlanSpace("x", null, emptyList(), 0), false))
        assertTrue(planSpaceLine(PlanSpace("x", null, emptyList(), 5), false).startsWith("هيتضاف"))
        Texts.arabicVariant = ArabicVariant.MSA
        assertTrue(planSpaceLine(PlanSpace("x", null, emptyList(), 5), false).startsWith("سيُضاف"))
    }
}
