package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** تجهيزات الحدث الجاي (OVERRIDES §65) — القلب النقي. كل الأسامي والمبالغ مخترعة. */
class EventPrepTest {
    private val today = "2026-10-04"
    private val wedding = LifeEvent("ev-1", "فرح وهمي", "x", LifeEventKind.WEDDING, "2026-12-01", mine = true, createdAt = "c")

    private fun txn(id: String, amount: Long, currency: Currency = Currency.SAR) = Transaction(
        id = id, occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE,
        economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = amount, currency = currency, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private fun spend(t: String, prep: String?, share: Int = EVENT_SHARE_WHOLE, role: EventRole = EventRole.SPEND, event: String = "ev-1") =
        EventLink(eventLinkId(t), event, t, role, if (role.isGift) "p-1" else null, "c", share, prep)

    @Test fun suggestionsByKindWithoutAmountsAndNoneForCondolencePastOrStarted() {
        val names = prepSuggestions(wedding, emptyList(), today)
        assertTrue("القاعة" in names && names.size == prepSuggestionKeys(LifeEventKind.WEDDING).size)
        for (kind in LifeEventKind.entries) {
            val expected = kind != LifeEventKind.CONDOLENCE
            assertEquals(expected, prepSuggestionKeys(kind).isNotEmpty(), kind.wire)
            assertEquals(expected, prepSuggestions(wedding.copy(kind = kind), emptyList(), today).isNotEmpty(), kind.wire)
        }
        assertTrue(prepSuggestions(wedding.copy(date = "2026-10-03"), emptyList(), today).isEmpty(), "حدث عدّى")
        assertTrue(prepSuggestions(wedding.copy(archived = true), emptyList(), today).isEmpty())
        val started = listOf(PrepItem("a", "ev-1", "حاجة", null, 1, false, "c"))
        assertTrue(prepSuggestions(wedding, started, today).isEmpty(), "عنده بنود ⇒ مفيش اقتراح")
        assertTrue(eventNeedsPrep(wedding.copy(date = today), today), "النهارده محسوب")
        assertTrue(!prepAllowed(wedding.copy(kind = LifeEventKind.CONDOLENCE)))
    }

    @Test fun namesAndAmountsAreChecked() {
        val siblings = listOf(PrepItem("a", "ev-1", "القاعة", null, 1, false, "c"))
        assertEquals("التصوير", checkPrepName("  التصوير ", siblings))
        assertFailsWith<PrepError> { checkPrepName("القاعه", siblings) }
        assertEquals("القاعة", checkPrepName("القاعة", siblings, selfId = "a"))
        assertFailsWith<PrepError> { checkPrepName(" ", siblings) }
        assertFailsWith<PrepError> { checkPrepName("ا".repeat(PREP_NAME_MAX + 1), siblings) }
        checkPrepPlanned(null)
        checkPrepPlanned(1)
        assertFailsWith<PrepError> { checkPrepPlanned(0) }
        assertFailsWith<PrepError> { checkPrepPlanned(-5) }
    }

    @Test fun spentPerItemUsesTheStoredShareAndKeepsTheRestSeparate() {
        val items = listOf(
            PrepItem("i1", "ev-1", "القاعة", 300_000, 1, false, "c"),
            PrepItem("i2", "ev-1", "اللبس", null, 2, false, "c"),
            PrepItem("i3", "ev-1", "الدعوات", 50_000, 3, true, "c"),
            PrepItem("other", "ev-9", "بند حدث تاني", 1, 1, false, "c"),
        )
        val txns = listOf(txn("t1", 100_000), txn("t2", 20_000), txn("t3", 7_000), txn("t4", 1_000, Currency.EGP), txn("t5", 9_999), txn("t6", 3_000))
        val links = listOf(
            spend("t1", "i1", share = 50), spend("t2", "i3"), spend("t3", null), spend("t4", "i1"),
            spend("t5", null, role = EventRole.GIFT_OUT), spend("t6", "gone"),
        )
        val s = summarizePrep(wedding, Currency.SAR, items, links, txns)
        assertEquals(listOf("i1" to 50_000L, "i2" to 0L, "i3" to 20_000L), s.items.map { it.item.id to it.spentMinor })
        assertEquals(350_000L, s.plannedTotalMinor, "اللي ليه مبلغ بس")
        assertEquals(1, s.unpricedCount)
        assertEquals(80_000L, s.spentMinor, "نصيب الحدث مش العملية كلها، والنقطة مش مصروف")
        assertEquals(10_000L, s.unassignedSpentMinor, "من غير بند + بند اتشال")
        assertEquals(1, s.otherCurrencyCount)
        assertEquals(2, s.remainingCount)
        assertEquals(summarizeEvent(wedding, links, txns).totals.first { it.currency == Currency.SAR }.spentMinor, s.spentMinor, "نفس رقم ملخص الحدث")
        val unpriced = summarizePrep(wedding, Currency.SAR, items.map { it.copy(plannedMinor = null) }, links, txns)
        assertNull(unpriced.plannedTotalMinor, "ولا بند ليه مبلغ ⇒ غير متاح مش صفر")
    }

    @Test fun onlySpendingFromTheSameEventGoesOnAnItem() {
        val item = PrepItem("i1", "ev-1", "القاعة", null, 1, false, "c")
        checkPrepAssignment(spend("t1", null), item)
        checkPrepAssignment(spend("t1", "i1"), null)
        assertFailsWith<PrepError> { checkPrepAssignment(spend("t1", null, role = EventRole.GIFT_OUT), item) }
        assertFailsWith<PrepError> { checkPrepAssignment(spend("t1", null, event = "ev-2"), item) }
    }
}
