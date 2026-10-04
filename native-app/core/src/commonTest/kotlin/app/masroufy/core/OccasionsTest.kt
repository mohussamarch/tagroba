package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** مناسبات الشخص + تنبيهها (OVERRIDES §64). كل الأسامي والمبالغ مخترعة. */
class OccasionsTest {
    private fun occ(month: Int, day: Int, year: Int? = null, yearly: Boolean = true, lead: Int? = null, person: String? = "p-1", kind: OccasionKind = OccasionKind.BIRTHDAY) =
        Occasion("o-$month-$day", person, kind, null, month, day, year, yearly, lead, null, "c")

    @Test fun validationCoversDatesYearAndLead() {
        checkOccasion(occ(2, 29))
        checkOccasion(occ(2, 29, year = 1996))
        assertFailsWith<OccasionError>("29 فبراير في سنة مش كبيسة") { checkOccasion(occ(2, 29, year = 1995)) }
        assertFailsWith<OccasionError> { checkOccasion(occ(2, 30)) }
        assertFailsWith<OccasionError> { checkOccasion(occ(13, 1)) }
        assertFailsWith<OccasionError> { checkOccasion(occ(4, 31)) }
        assertFailsWith<OccasionError>("المرة الواحدة محتاجة سنة") { checkOccasion(occ(6, 1, yearly = false)) }
        assertFailsWith<OccasionError> { checkOccasion(occ(6, 1, lead = 0)) }
        assertFailsWith<OccasionError> { checkOccasion(occ(6, 1, lead = OCCASION_LEAD_MAX + 1)) }
        assertEquals(OCCASION_LEAD_MAX, checkOccasion(occ(6, 1, lead = OCCASION_LEAD_MAX)).leadDays)
        assertEquals("تخرّج", checkOccasion(occ(6, 1).copy(label = "  تخرّج  ")).label)
        assertNull(checkOccasion(occ(6, 1).copy(label = "   ")).label)
    }

    @Test fun nextOccurrenceForYearlyAndOneTime() {
        assertEquals("2026-12-01", nextOccurrence(occ(12, 1), "2026-10-04"))
        assertEquals("2026-10-04", nextOccurrence(occ(10, 4), "2026-10-04"), "النهارده محسوب")
        assertEquals("2027-10-03", nextOccurrence(occ(10, 3), "2026-10-04"), "عدّت ⇒ السنة الجاية")
        assertEquals("2027-03-01", nextOccurrence(occ(3, 1, year = 2027), "2026-10-04"), "مش قبل سنتها")
        assertEquals("2028-03-01", nextOccurrence(occ(3, 1, year = 2028), "2026-10-04"), "مش قبل سنتها — حتى لو بعدها بسنتين")
        assertEquals("2026-11-20", nextOccurrence(occ(11, 20, year = 2026, yearly = false), "2026-10-04"))
        assertNull(nextOccurrence(occ(9, 20, year = 2026, yearly = false), "2026-10-04"), "مرة واحدة وعدّت ⇒ مفيش")
        assertNull(nextOccurrence(occ(10, 4, year = 2025, yearly = false), "2026-10-04"))
    }

    @Test fun feb29FallsOnFeb28InNonLeapYears() {
        val leap = occ(2, 29)
        assertEquals("2027-02-28", nextOccurrence(leap, "2026-10-04"))
        assertEquals("2028-02-29", nextOccurrence(leap, "2027-03-01"))
        assertEquals("2029-02-28", nextOccurrence(leap, "2028-03-01"))
        assertEquals("2100-02-28", occasionDateIn(leap, 2100), "2100 مش كبيسة")
    }

    @Test fun ownEventReminderIsYearlyWithTheChosenLead() {
        val wedding = LifeEvent("ev-1", "فرح وهمي", "فرح وهمي", LifeEventKind.WEDDING, "2024-04-12", mine = true, createdAt = "c")
        val o = ownEventOccasion(wedding, 14, "now")
        assertEquals(listOf(null, OccasionKind.WEDDING_ANNIVERSARY, 4, 12, 2024, true, 14, "ev-1"), listOf(o.personId, o.kind, o.month, o.day, o.year, o.yearly, o.leadDays, o.sourceEventId))
        assertEquals(o.id, ownEventOccasion(wedding, 3, "later", o).id)
        assertEquals("now", ownEventOccasion(wedding, 3, "later", o).createdAt)
        assertFailsWith<OccasionError> { ownEventOccasion(wedding.copy(mine = false, hostPersonId = "p-1"), 14, "now") }
        assertFailsWith<OccasionError> { ownEventOccasion(wedding, 0, "now") }
        assertEquals(OccasionKind.OTHER, ownEventOccasion(wedding.copy(kind = LifeEventKind.BIRTH), 7, "now").kind)
    }

    @Test fun alertWindowIsLeadDaysOrAWeekThenToday() {
        val today = "2026-10-04"
        assertEquals(AlertKind.OCCASION_SOON, occasionAlertCandidate(occ(10, 11), today, "سامي الوهمي", null)?.kind, "أسبوع بالظبط")
        assertNull(occasionAlertCandidate(occ(10, 12), today, "سامي الوهمي", null), "8 أيام ⇒ لسه")
        assertEquals(AlertKind.OCCASION_SOON, occasionAlertCandidate(occ(10, 24, lead = 20), today, "سامي الوهمي", null)?.kind)
        assertNull(occasionAlertCandidate(occ(10, 7, lead = 2), today, "سامي الوهمي", null), "مدته يومين")
        val day = occasionAlertCandidate(occ(10, 4), today, "سامي الوهمي", null)!!
        assertEquals(AlertKind.OCCASION_TODAY, day.kind)
        assertNull(occasionAlertCandidate(occ(10, 3, year = 2026, yearly = false), today, "سامي الوهمي", null), "مفيش «عدّى»")
        val soon = occasionAlertCandidate(occ(10, 8), today, "سامي الوهمي", null)!!
        assertEquals(soon.threadKey, occasionAlertCandidate(occ(10, 8), "2026-10-08", "سامي الوهمي", null)!!.threadKey, "نفس الموضوع لحد يومها")
        assertTrue(soon.threadKey != occasionAlertCandidate(occ(10, 8), "2027-10-05", "سامي الوهمي", null)!!.threadKey, "السنة الجاية موضوع جديد")
    }

    @Test fun inAppLineHasNameAndReciprocityButLockScreenNeverDoes() {
        val badges = listOf(
            GiftBadge("ev-2", "فرح أخوه الوهمي", LifeEventKind.WEDDING, "2026-08-01", Direction.OUT, 100_000, Currency.SAR),
            GiftBadge("ev-1", "فرحي الوهمي", LifeEventKind.WEDDING, "2025-05-01", Direction.IN, 200_000, Currency.SAR),
            GiftBadge("ev-0", "خطوبة قديمة وهمية", LifeEventKind.ENGAGEMENT, "2020-01-01", Direction.IN, 50_000, Currency.SAR),
        )
        val c = occasionAlertCandidate(occ(10, 6), "2026-10-04", "سامي الوهمي", null, badges)!!
        assertTrue(c.title.contains("سامي الوهمي"), c.title)
        assertTrue(c.body.contains("نقّطك ${formatMoney(200_000, Currency.SAR)} في «فرحي الوهمي»"), c.body)
        assertTrue(c.body.contains("نقّطته"), c.body)
        assertFalse(c.body.contains("خطوبة قديمة"), "آخر مرة في كل اتجاه بس")
        try {
            for (lang in Language.entries) {
                Texts.language = lang
                for (kind in listOf(AlertKind.OCCASION_SOON, AlertKind.OCCASION_TODAY)) {
                    val n = systemNoticeFor(kind)
                    for (text in listOf(n.title, n.body)) {
                        assertTrue(isLockSafe(text), "[$lang] $kind: $text")
                        assertFalse(text.contains("سامي") || text.contains("فرح") || text.contains("2,000"), "[$lang] $kind: $text")
                    }
                }
            }
        } finally {
            Texts.language = Language.AR
        }
        assertEquals("عندك مناسبة قريبة", systemNoticeFor(AlertKind.OCCASION_SOON).body)
    }

    @Test fun ownEventAlertUsesTheEventName() {
        val wedding = LifeEvent("ev-1", "فرحي الوهمي", "فرحي الوهمي", LifeEventKind.WEDDING, "2024-10-14", mine = true, createdAt = "c")
        val c = occasionAlertCandidate(ownEventOccasion(wedding, 14, "now"), "2026-10-04", null, wedding.name)!!
        assertEquals("ذكرى «فرحي الوهمي»: كمان 10 يوم", c.title)
        assertTrue(c.body.contains("هدية"), c.body)
        assertEquals("عيد ميلاد سامي الوهمي", occasionTitle(occ(1, 1), "سامي الوهمي", null))
        assertEquals("عيد ميلادك", occasionTitle(occ(1, 1, person = null), null, null))
    }
}
