package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ملخص شاشة الأشخاص (جلسة 16) — دالة نقية، أسماء ومبالغ مخترعة. بيشتغل على محاكي الآيفون كمان (commonTest). */
class PeopleOverviewTest {
    private val today = "2026-10-05"
    private val sa = DEFAULT_SPACE_ID

    private fun balance(pid: Id, space: String, currency: Currency, receivable: Long = 0, loan: Long = 0, custody: Long = 0) =
        PersonSpaceBalance(pid, space, if (space == sa) "السعودية" else "مصر", currency, receivable, loan, custody)

    private fun birthday(id: Id, pid: Id, month: Int, day: Int) = Occasion(id, pid, OccasionKind.BIRTHDAY, month = month, day = day, yearly = true, createdAt = "c")

    private fun input(
        people: List<Person>,
        balances: List<PersonSpaceBalance> = emptyList(),
        occasions: List<Occasion> = emptyList(),
        profiles: List<PersonProfile> = emptyList(),
        relations: List<PersonRelation> = emptyList(),
        badges: List<Pair<Id, SpaceGiftBadge>> = emptyList(),
    ) = PeopleOverviewInput(today, people, profiles, relations, occasions, balances, badges)

    @Test fun balancesAreNeverSummedAcrossCurrenciesOrCountries() {
        val people = listOf(Person("p-1", "سامي الوهمي"), Person("p-2", "خالد الوهمي"))
        val o = peopleOverview(
            input(
                people,
                listOf(
                    balance("p-1", sa, Currency.SAR, receivable = 60_000),
                    balance("p-1", "eg", Currency.EGP, receivable = 250_000, loan = 30_000, custody = 10_000),
                    balance("p-1", "eg", Currency.SAR, receivable = 5_000),
                    balance("p-2", sa, Currency.SAR, receivable = 20_000),
                ),
            ),
        )
        val sami = o.rows.single { it.person.id == "p-1" }
        assertEquals(
            listOf(
                PersonBalanceLine(sa, "السعودية", Currency.SAR, BalanceDirection.OWED_TO_YOU, 60_000),
                PersonBalanceLine("eg", "مصر", Currency.EGP, BalanceDirection.OWED_TO_YOU, 250_000),
                PersonBalanceLine("eg", "مصر", Currency.EGP, BalanceDirection.YOU_OWE, 40_000, custodyMinor = 10_000),
                PersonBalanceLine("eg", "مصر", Currency.SAR, BalanceDirection.OWED_TO_YOU, 5_000),
            ),
            sami.balances,
            "سطر لكل بلد وعملة واتجاه — لا تقاص ولا جمع",
        )
        assertEquals(
            listOf(
                PeopleTotal(sa, "السعودية", Currency.SAR, 80_000, 0),
                PeopleTotal("eg", "مصر", Currency.EGP, 250_000, 40_000),
                PeopleTotal("eg", "مصر", Currency.SAR, 5_000, 0),
            ),
            o.totals,
            "الريال في مصر ما بيتجمعش مع ريال السعودية، والجنيه لوحده",
        )
    }

    @Test fun outlineFollowsTheFixedPriority() {
        val people = (1..6).map { Person("p-$it", "شخص $it") }
        val o = peopleOverview(
            input(
                people,
                listOf(
                    balance("p-1", sa, Currency.SAR, receivable = 1_000, loan = 500),
                    balance("p-2", sa, Currency.SAR, receivable = 1_000),
                    balance("p-6", "eg", Currency.EGP, custody = 700),
                ),
                listOf(
                    birthday("o-1", "p-1", 10, 6), birthday("o-2", "p-2", 10, 6),
                    birthday("o-3", "p-3", 11, 4), // بعد 30 يوم بالظبط ⇒ قريبة
                    birthday("o-4", "p-4", 11, 5), // بعد 31 يوم ⇒ لأ
                ),
            ),
        )
        val state = o.rows.associate { it.person.id to it.state }
        assertEquals(PersonOutlineState.YOU_OWE, state["p-1"], "عليك قبل لك وقبل المناسبة")
        assertEquals(PersonOutlineState.OWED_TO_YOU, state["p-2"], "لك قبل المناسبة")
        assertEquals(PersonOutlineState.OCCASION_SOON, state["p-3"])
        assertEquals(PersonOutlineState.NONE, state["p-4"])
        assertEquals(PersonOutlineState.NONE, state["p-5"])
        assertEquals(PersonOutlineState.YOU_OWE, state["p-6"], "الأمانة اللي عندك = عليك")
        assertEquals(30, o.rows.single { it.person.id == "p-3" }.nextOccasion!!.daysAway)
    }

    @Test fun sectionsAndCountsKeepBothDirectionsWithoutNetting() {
        val people = (1..5).map { Person("p-$it", "شخص $it") }
        val o = peopleOverview(
            input(
                people,
                listOf(balance("p-1", sa, Currency.SAR, receivable = 1_000, loan = 1_000), balance("p-2", sa, Currency.SAR, loan = 300)),
                listOf(birthday("o-3", "p-3", 10, 20), birthday("o-4", "p-4", 10, 9), birthday("o-1", "p-1", 10, 6)),
            ),
        )
        val bySection = o.sections.associate { it.section to it.personIds }
        assertEquals(listOf("p-1"), bySection[PeopleSection.OWED_TO_YOU])
        assertEquals(listOf("p-1", "p-2"), bySection[PeopleSection.YOU_OWE], "اللي ليه وعليه في القسمين — من غير تقاص")
        assertEquals(listOf("p-4", "p-3"), bySection[PeopleSection.OCCASIONS_SOON], "بالأقرب، واللي عليه رصيد مش هنا")
        assertEquals(listOf("p-5"), bySection[PeopleSection.NO_BALANCE])
        assertEquals(listOf(1, 2, 2, 1), o.sections.map { it.count })
        assertEquals(listOf(PeopleSection.OWED_TO_YOU, PeopleSection.YOU_OWE, PeopleSection.OCCASIONS_SOON, PeopleSection.NO_BALANCE), o.sections.map { it.section })
    }

    @Test fun archivedPeopleStayOnlyWhileMoneyIsOpenAndLeaveTheRings() {
        val people = listOf(Person("p-1", "مؤرشف عليه فلوس", archived = true), Person("p-2", "مؤرشف من غير فلوس", archived = true), Person("p-3", "نشط"))
        val o = peopleOverview(
            input(
                people,
                listOf(balance("p-1", sa, Currency.SAR, receivable = 900)),
                listOf(birthday("o-1", "p-1", 10, 6), birthday("o-2", "p-2", 10, 6)),
                profiles = listOf(PersonProfile("p-1", PersonCircle.FAMILY, null, "c"), PersonProfile("p-3", PersonCircle.WORK, "زميلي", "c")),
            ),
        )
        assertEquals(setOf("p-1", "p-3"), o.rows.map { it.person.id }.toSet())
        assertNull(o.rows.single { it.person.id == "p-1" }.nextOccasion, "مناسبات المؤرشف ما بتتحسبش")
        assertEquals(emptyList(), o.rings.getValue(PersonCircle.FAMILY), "المؤرشف برا الحلقات")
        assertEquals(listOf("p-3"), o.rings.getValue(PersonCircle.WORK))
        assertEquals("زميلي", o.rows.single { it.person.id == "p-3" }.relationLabel)
        assertEquals(PeopleTotal(sa, "السعودية", Currency.SAR, 900, 0), o.totals.single(), "فلوس المؤرشف في الإجمالي")
    }

    @Test fun nextOccasionBadgesRingsAndRelationsComeTogether() {
        val people = listOf(Person("p-1", "أم وهمية"), Person("p-2", "بنت وهمية"), Person("p-3", "جار وهمي"))
        val badge = GiftBadge("ev-1", "فرح وهمي", LifeEventKind.WEDDING, "2026-05-01", Direction.IN, 200_000, Currency.SAR)
        val o = peopleOverview(
            input(
                people,
                occasions = listOf(birthday("o-late", "p-1", 12, 1), birthday("o-near", "p-1", 10, 15), Occasion("o-mine", null, OccasionKind.BIRTHDAY, month = 10, day = 6, yearly = true, createdAt = "c")),
                profiles = listOf(PersonProfile("p-1", PersonCircle.FAMILY, "أمي", "c"), PersonProfile("p-2", PersonCircle.FAMILY, null, "c")),
                relations = listOf(newPersonRelation("p-2", "p-1", "أمها", people, "c")),
                badges = listOf("p-1" to SpaceGiftBadge(sa, "السعودية", badge)),
            ),
        )
        val mom = o.rows.single { it.person.id == "p-1" }
        assertEquals("o-near", mom.nextOccasion!!.occasion.id)
        assertEquals("2026-10-15", mom.nextOccasion!!.date)
        assertEquals(listOf(badge), mom.giftBadges.map { it.badge })
        assertEquals(listOf("p-1", "p-2"), o.rings.getValue(PersonCircle.FAMILY))
        assertFalse(o.rings.containsKey(PersonCircle.OTHER), "من غير دايرة ⇒ في القايمة بس، مش في الدواير (رد المالك §67)")
        assertTrue(o.rows.any { it.person.id == "p-3" && it.circle == PersonCircle.OTHER }, "بس موجود في القايمة")
        assertEquals(listOf("p-1" to "p-2"), o.relations.map { it.personAId to it.personBId })
        assertTrue(o.rows.none { it.nextOccasion?.occasion?.id == "o-mine" }, "مناسبتك إنت مش على حد")
        assertTrue(o.totals.isEmpty(), "مفيش أرصدة ⇒ مفيش إجمالي (مش صفر)")
    }
}
