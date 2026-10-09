package app.masroufy.ui.screens.home

import app.masroufy.core.AlertKind
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Texts
import app.masroufy.core.systemNoticeFor
import app.masroufy.ui.screens.home.HomeTestData.inbox
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * صفحة الإشعارات: «بانتظار قرارك» (الأنواع اللي محتاجة قرار) ثم «جديد» · الممسوح بـ«×» ما يبانش · نقطة «جديد» من قراية الجرس ·
 * «مقفولة» · إمتى · فين بيودّي · صندوق شاشة القفل من غير أرقام. و«×» نفسه: مسح ⇒ تراجع ⇒ انتهاء الأربع ثواني.
 */
class NotificationsModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val list = listOf(
        inbox("sms", AlertKind.SMS_CONFIRM),
        inbox("budget", AlertKind.BUDGET_EXCEEDED, createdAt = "2026-10-08T09:00:00.000Z"),
        inbox("party", AlertKind.TRANSFER_QUESTION, createdAt = "2026-10-05T09:00:00.000Z"),
        inbox("zakat", AlertKind.ZAKAT_SOON, muted = true, spaceLabel = "مصر"),
    )

    @Test fun decisionsComeFirstThenTheRest() {
        val s = notificationSections(list, unread = setOf("sms"), gone = emptySet(), today = "2026-10-09")
        assertEquals(listOf("بانتظار قرارك", "جديد"), s.map { it.title })
        assertEquals(listOf("sms", "party"), s[0].rows.map { it.threadKey })
        assertEquals(listOf("budget", "zakat"), s[1].rows.map { it.threadKey })
        assertTrue(s[0].rows[0].unread)
        assertFalse(s[0].rows[1].unread)
        val zakat = s[1].rows[1]
        assertTrue(zakat.muted)
        assertEquals("مصر", zakat.spaceLabel)
        assertEquals("سبب sms", s[0].rows[0].why, "«لماذا الآن» جاية من المنطق")
    }

    @Test fun deletedRowsDisappearAndAnEmptySectionIsDropped() {
        val s = notificationSections(list, emptySet(), gone = setOf("sms", "party"), today = "2026-10-09")
        assertEquals(listOf("جديد"), s.map { it.title })
        assertTrue(notificationSections(emptyList(), emptySet(), emptySet(), "2026-10-09").isEmpty(), "فاضي ⇒ الشاشة الفاضية")
    }

    @Test fun whenTextAndTargets() {
        assertEquals("اليوم", whenText("2026-10-09T08:00:00.000Z", "2026-10-09"))
        assertEquals("أمس", whenText("2026-10-08T08:00:00.000Z", "2026-10-09"))
        assertEquals("قبل يومين", whenText("2026-10-07T08:00:00.000Z", "2026-10-09"))
        assertEquals("قبل ٤ أيام", whenText("2026-10-05T08:00:00.000Z", "2026-10-09"))
        assertEquals("قبل ١٧ يومًا", whenText("2026-09-22T08:00:00.000Z", "2026-10-09"))
        assertEquals("", whenText("bad", "2026-10-09"))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("من ١٧ يوم", whenText("2026-09-22T08:00:00.000Z", "2026-10-09"))
        assertEquals(NotifTarget.BANK_SMS, targetOf(AlertKind.SMS_CONFIRM))
        assertEquals(NotifTarget.REVIEW, targetOf(AlertKind.TRANSFER_QUESTION))
        assertEquals(NotifTarget.PEOPLE, targetOf(AlertKind.OCCASION_SOON))
        assertEquals(NotifTarget.PROFILE, targetOf(AlertKind.PROFILE_INCOMPLETE))
        assertEquals(NotifIcon.DEBT, iconOf(AlertKind.DUE_OVERDUE))
    }

    @Test fun lockPreviewUsesTheGenericNoticeOfTheFirstVisibleRow() {
        assertEquals(systemNoticeFor(AlertKind.SMS_CONFIRM), lockPreviewOf(list, gone = emptySet()))
        assertEquals(systemNoticeFor(AlertKind.BUDGET_EXCEEDED), lockPreviewOf(list, gone = setOf("sms")))
        assertNull(lockPreviewOf(listOf(inbox("z", AlertKind.ZAKAT_SOON, muted = true)), emptySet()), "المقفول ما بيوصلش شاشة القفل")
        val notice = lockPreviewOf(list, emptySet())!!
        assertFalse(notice.body.any { it.isDigit() }, "شاشة القفل من غير أرقام")
    }

    @Test fun deleteUndoAndExpiry() {
        val d = Dismissals()
        d.drop("a")
        assertTrue(d.isGone("a"))
        assertEquals("a", d.undo)
        d.restore()
        assertFalse(d.isGone("a"), "«تراجع» بيرجّعه")
        assertNull(d.undo)
        d.drop("b")
        d.drop("c")
        assertEquals("c", d.undo, "التراجع لآخر واحد بس")
        d.expire("b")
        assertEquals("c", d.undo, "انتهاء واحد قديم ما بيلغيش تراجع الأحدث")
        d.expire("c")
        assertNull(d.undo)
        assertEquals(setOf("b", "c"), d.goneKeys)
        assertTrue(Dismissals.of("sa") === Dismissals.of("sa"), "نفس الحالة في الصفحة والجرس")
    }
}
