package app.masroufy.ui.screens.common

import app.masroufy.core.CalendarItemType
import app.masroufy.ui.screens.common.QuickAct.ADD_EVENT_ON_DAY
import app.masroufy.ui.screens.common.QuickAct.ADD_OCCASION
import app.masroufy.ui.screens.common.QuickAct.ARCHIVE
import app.masroufy.ui.screens.common.QuickAct.DELETE
import app.masroufy.ui.screens.common.QuickAct.EDIT
import app.masroufy.ui.screens.common.QuickAct.MARK_READ
import app.masroufy.ui.screens.common.QuickAct.OPEN
import app.masroufy.ui.screens.common.QuickAct.OPEN_SECTION
import app.masroufy.ui.screens.common.QuickAct.PERSON
import app.masroufy.ui.screens.common.QuickAct.PREP
import app.masroufy.ui.screens.common.QuickAct.SETTLE
import app.masroufy.ui.screens.common.QuickAct.SHOW_DAY
import app.masroufy.ui.screens.common.QuickAct.STAR
import app.masroufy.ui.screens.common.QuickAct.UNARCHIVE
import app.masroufy.ui.screens.common.QuickAct.UNSTAR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** بنود القايمة السريعة لكل نوع (قايمة المالك §6.5-13): الأفعال الموجودة بس، و«امسح» بس لما الشاشة عندها مسح. */
class QuickActionsTest {
    @Test fun debtWithRemainderCanBeSettled() = assertEquals(listOf(OPEN, SETTLE, PERSON), debtQuickActs(5_000L))

    @Test fun settledOrUnknownDebtHasNoSettle() {
        assertEquals(listOf(OPEN, PERSON), debtQuickActs(0L))
        assertEquals(listOf(OPEN, PERSON), debtQuickActs(null))
    }

    @Test fun deleteAppearsOnlyWhenScreenProvidesIt() {
        assertFalse(DELETE in installmentQuickActs())
        assertEquals(listOf(OPEN, EDIT, DELETE), installmentQuickActs(canDelete = true))
        assertEquals(listOf(OPEN, SETTLE, PERSON, DELETE), debtQuickActs(1L, canDelete = true))
        assertEquals(listOf(OPEN, DELETE), openOnlyQuickActs(canDelete = true))
    }

    @Test fun goalStarToggles() {
        assertEquals(listOf(OPEN, STAR), goalQuickActs(starred = false))
        assertEquals(listOf(OPEN, UNSTAR), goalQuickActs(starred = true))
    }

    @Test fun assetArchiveToggles() {
        assertEquals(listOf(OPEN, ARCHIVE), assetQuickActs(archived = false))
        assertEquals(listOf(OPEN, UNARCHIVE), assetQuickActs(archived = true))
    }

    @Test fun archivedEventHasNoPrep() {
        assertEquals(listOf(OPEN, PREP), eventQuickActs(archived = false))
        assertEquals(listOf(OPEN), eventQuickActs(archived = true))
    }

    @Test fun personOffersNewOccasion() = assertEquals(listOf(OPEN, ADD_OCCASION), personQuickActs())

    @Test fun calendarDayAddsEventOnlyWhenSheetExists() {
        assertEquals(listOf(SHOW_DAY, ADD_EVENT_ON_DAY), calendarDayQuickActs(canAddEvent = true))
        assertEquals(listOf(SHOW_DAY), calendarDayQuickActs(canAddEvent = false))
    }

    @Test fun calendarItemsByType() {
        assertEquals(listOf(OPEN, SETTLE, OPEN_SECTION), calendarItemQuickActs(CalendarItemType.DEBT))
        assertEquals(listOf(OPEN, PREP, OPEN_SECTION), calendarItemQuickActs(CalendarItemType.EVENT))
        assertEquals(listOf(OPEN, OPEN_SECTION), calendarItemQuickActs(CalendarItemType.INSTALLMENT))
        assertEquals(listOf(OPEN_SECTION), calendarItemQuickActs(CalendarItemType.PAYDAY))
        CalendarItemType.entries.forEach { assertTrue(OPEN_SECTION in calendarItemQuickActs(it), "$it") }
    }

    @Test fun notificationMarkReadOnlyWhenUnread() {
        assertEquals(listOf(OPEN, MARK_READ), notificationQuickActs(unread = true, canOpen = true))
        assertEquals(listOf(OPEN), notificationQuickActs(unread = false, canOpen = true))
        assertEquals(emptyList(), notificationQuickActs(unread = false, canOpen = false))
    }
}
