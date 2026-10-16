package app.masroufy.ui.screens.imports

import app.masroufy.core.Direction
import app.masroufy.core.UiKey
import app.masroufy.usecase.SmsHistoryReport
import app.masroufy.usecase.SmsShapeGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** r041b P3 (فحص المحاكي r041 — اللقطات 33–35 · re-08): رسايل البنك المستنية «أكّد الأشكال» بتبان في الرئيسية. بيانات مخترعة. */
class SmsHomeCardTest {
    private fun shape(id: String, count: Int) =
        SmsShapeGroup("sa", "TESTBANK", id, "2026-10-16", 4_550L, Direction.OUT, "TEST SHOP", count)

    private fun report(vararg shapes: SmsShapeGroup) = SmsHistoryReport(recorded = 0, duplicates = 0, shapes = shapes.toList(), waiting = 2, queued = 2)

    @Test fun countsEveryMessageWaitingForItsShapeConfirmation() {
        assertEquals(2, smsShapeWaiting(report(shape("m1", 1), shape("m2", 1))))
        assertEquals(7, smsShapeWaiting(report(shape("m1", 5), shape("m2", 2))))
        assertEquals(0, smsShapeWaiting(report()), "nothing waits ⇒ no card")
        assertEquals(0, smsShapeWaiting(null), "no report (iPhone · read failed) ⇒ no card, not a confirmed zero")
    }

    @Test fun yesToPastMessagesOpensTheConfirmScreenOnlyWhenSomethingWaits() {
        assertTrue(openConfirmAfterHistory(report(shape("m1", 1))))
        assertFalse(openConfirmAfterHistory(report()))
        assertFalse(openConfirmAfterHistory(null))
    }

    @Test fun quietMonthTextFollowsTheReadingState() {
        assertEquals(UiKey.HOME_EMPTY_BODY_READING, homeEmptyBodyKey(readable = true, readingOn = true))
        assertEquals(UiKey.HOME_EMPTY_BODY, homeEmptyBodyKey(readable = true, readingOn = false))
        assertEquals(UiKey.HOME_EMPTY_BODY_NO_SMS, homeEmptyBodyKey(readable = false, readingOn = false))
    }
}
