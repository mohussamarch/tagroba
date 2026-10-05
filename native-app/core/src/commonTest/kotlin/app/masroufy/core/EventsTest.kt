package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «الأحداث» — القلب النقي (OVERRIDES §64). كل الأسامي والمبالغ مخترعة. */
class EventsTest {
    // النص المتوقع هنا = نص التطبيق الحالي = النسخة المصرية (OVERRIDES §66)
    @BeforeTest
    fun egyptianText() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
    }

    @AfterTest
    fun defaultText() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun txn(id: String, dir: Direction, amount: Long, currency: Currency = Currency.SAR) = Transaction(
        id = id, occurredAt = "2026-05-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private val myWedding = LifeEvent("ev-1", "فرح وهمي", normalizeText("فرح وهمي"), LifeEventKind.WEDDING, "2026-05-01", mine = true, createdAt = "c")
    private val brotherWedding = LifeEvent("ev-2", "فرح أخ وهمي", normalizeText("فرح أخ وهمي"), LifeEventKind.WEDDING, "2026-08-01", mine = false, hostPersonId = "p-2", createdAt = "c")

    private fun link(t: String, ev: String, role: EventRole, person: String? = null) = EventLink(eventLinkId(t), ev, t, role, person, "c")

    @Test fun eventNameIsCleanedLimitedAndUnique() {
        val checked = checkEventName("  فرح   وهمي  ", emptyList())
        assertEquals("فرح وهمي", checked.name)
        assertFailsWith<EventError> { checkEventName("فرح  وهمي", listOf(myWedding)) }
        assertEquals("فرح وهمي", checkEventName("فرح وهمي", listOf(myWedding), selfId = "ev-1").name, "تعديل نفس الحدث مسموح")
        assertFailsWith<EventError> { checkEventName("   ", emptyList()) }
        assertFailsWith<EventError> { checkEventName("ا".repeat(EVENT_NAME_MAX + 1), emptyList()) }
        assertFailsWith<EventError> { checkEventFields("2026-02-30", mine = false, hostPersonId = null) }
        assertFailsWith<EventError> { checkEventFields("2026-05-01", mine = true, hostPersonId = "p-1") }
        checkEventFields("2026-05-01", mine = false, hostPersonId = "p-1")
    }

    @Test fun linkRulesDirectionPersonOwnershipAndOneEventPerTransaction() {
        val spend = txn("t-out", Direction.OUT, 500_000)
        val gift = txn("t-in", Direction.IN, 200_000)
        checkEventLink(myWedding, EventRole.SPEND, null, spend, emptyList())
        checkEventLink(myWedding, EventRole.GIFT_IN, "p-1", gift, emptyList())
        checkEventLink(brotherWedding, EventRole.GIFT_OUT, "p-2", spend, emptyList())
        assertFailsWith<EventError>("المصروف لازم طالع") { checkEventLink(myWedding, EventRole.SPEND, null, gift, emptyList()) }
        assertFailsWith<EventError>("النقطة الجاية لازم داخلة") { checkEventLink(myWedding, EventRole.GIFT_IN, "p-1", spend, emptyList()) }
        assertFailsWith<EventError>("النقطة من غير شخص") { checkEventLink(myWedding, EventRole.GIFT_IN, null, gift, emptyList()) }
        assertFailsWith<EventError>("النقطة الطالعة من غير شخص") { checkEventLink(brotherWedding, EventRole.GIFT_OUT, null, spend, emptyList()) }
        assertFailsWith<EventError>("نقوط جاتلك في حدث حد تاني") { checkEventLink(brotherWedding, EventRole.GIFT_IN, "p-1", gift, emptyList()) }
        val e = assertFailsWith<EventError> { checkEventLink(brotherWedding, EventRole.SPEND, null, spend, listOf(link("t-out", "ev-1", EventRole.SPEND))) }
        assertEquals(uiText(TextKey.EVENT_TXN_ALREADY_LINKED), e.message)
        assertFailsWith<EventError>("ولا مرتين في نفس الحدث") { checkEventLink(myWedding, EventRole.SPEND, null, spend, listOf(link("t-out", "ev-1", EventRole.SPEND))) }
        assertEquals(eventLinkId("t-out"), eventLinkId("t-out"), "المعرّف من العملية بس ⇒ ربط واحد على مستوى التخزين")
    }

    @Test fun giftsNeverReduceSpendingAndThereIsNoNet() {
        val txns = listOf(txn("s-1", Direction.OUT, 3_000_000), txn("s-2", Direction.OUT, 1_500_000))
        val spendOnly = listOf(link("s-1", "ev-1", EventRole.SPEND), link("s-2", "ev-1", EventRole.SPEND))
        val before = summarizeEvent(myWedding, spendOnly, txns)
        assertEquals(4_500_000, before.totals.single().spentMinor)
        val gifts = listOf(txn("g-1", Direction.IN, 200_000), txn("g-2", Direction.IN, 100_000))
        val after = summarizeEvent(myWedding, spendOnly + listOf(link("g-1", "ev-1", EventRole.GIFT_IN, "p-1"), link("g-2", "ev-1", EventRole.GIFT_IN, "p-3")), txns + gifts)
        val total = after.totals.single()
        assertEquals(4_500_000, total.spentMinor, "🔒 المصروف هو هو بعد النقوط (قرار المالك §64)")
        assertEquals(300_000, total.giftsInMinor, "النقوط رقم لوحده للمعلومية")
        assertEquals(2, after.giftInCount)
        assertEquals(2, after.spendCount)
        // مفيش أي حقل في الملخص بيساوي المصروف − النقوط
        assertTrue(listOf(total.spentMinor, total.giftsInMinor, total.giftsOutMinor).none { it == 4_200_000L })
    }

    @Test fun giftsInAreHiddenForSomeoneElsesEventButYourGiftsShow() {
        val txns = listOf(txn("o-1", Direction.OUT, 100_000), txn("i-1", Direction.IN, 50_000))
        // نقطة داخلة متسجلة في حدث حد تاني (من نسخة قديمة مثلًا) ⇒ برضه ما بتظهرش
        val links = listOf(link("o-1", "ev-2", EventRole.GIFT_OUT, "p-2"), link("i-1", "ev-2", EventRole.GIFT_IN, "p-2"))
        val s = summarizeEvent(brotherWedding, links, txns)
        assertNull(s.totals.single().giftsInMinor)
        assertNull(s.giftInCount)
        assertEquals(100_000, s.totals.single().giftsOutMinor)
        assertEquals(0, s.totals.single().spentMinor)
    }

    @Test fun currenciesAreNeverSummedTogether() {
        val txns = listOf(txn("s-1", Direction.OUT, 100_000, Currency.SAR), txn("s-2", Direction.OUT, 700_000, Currency.EGP))
        val s = summarizeEvent(myWedding, listOf(link("s-1", "ev-1", EventRole.SPEND), link("s-2", "ev-1", EventRole.SPEND)), txns)
        assertEquals(listOf(Currency.EGP to 700_000L, Currency.SAR to 100_000L), s.totals.map { it.currency to it.spentMinor })
    }

    @Test fun personBadgesShowWhoGaveWhatAtWhichEvent() {
        val txns = listOf(txn("g-1", Direction.IN, 200_000), txn("g-2", Direction.IN, 50_000), txn("o-1", Direction.OUT, 100_000), txn("s-1", Direction.OUT, 900_000))
        val links = listOf(
            link("g-1", "ev-1", EventRole.GIFT_IN, "p-2"), link("g-2", "ev-1", EventRole.GIFT_IN, "p-2"),
            link("o-1", "ev-2", EventRole.GIFT_OUT, "p-2"), link("s-1", "ev-1", EventRole.SPEND, "p-2"),
        )
        val badges = personGiftBadges("p-2", listOf(myWedding, brotherWedding), links, txns)
        assertEquals(listOf(Direction.OUT to 100_000L, Direction.IN to 250_000L), badges.map { it.direction to it.amountMinor }, "الأحدث الأول، والمصروف مش بادج")
        assertEquals("نقّطك ${formatMoney(250_000, Currency.SAR)} في «فرح وهمي»", giftBadgeText(badges[1]))
        assertTrue(formatMoney(250_000, Currency.SAR).contains("2,500"))
        assertTrue(giftBadgeText(badges[0]).startsWith("نقّطته"))
        assertTrue(personGiftBadges("p-9", listOf(myWedding), links, txns).isEmpty())
    }

    @Test fun eventShareIsIntegerMathRoundedHalfUpOnTheMinorUnit() {
        assertEquals(3_000_000, eventShareMinor(3_000_000, EVENT_SHARE_WHOLE), "100 = العملية كلها بالظبط")
        assertEquals(1_200_000, eventShareMinor(3_000_000, 40))
        assertEquals(33, eventShareMinor(101, 33), "33.33 ⇒ 33")
        assertEquals(1, eventShareMinor(1, 50), "0.5 هللة ⇒ 1 (النص لفوق)")
        assertEquals(1, eventShareMinor(149, 1), "1.49 ⇒ 1")
        assertEquals(2, eventShareMinor(150, 1), "1.50 ⇒ 2")
        assertEquals(66, eventShareMinor(99, 67), "66.33 ⇒ 66")
        assertEquals(50, eventShareMinor(99, 50), "49.5 ⇒ 50")
        assertEquals(EVENT_SHARE_WHOLE, link("x", "ev-1", EventRole.SPEND).sharePercent, "الربط من غير نسبة = العملية كلها")
        checkEventShare(EventRole.SPEND, 1)
        checkEventShare(EventRole.SPEND, 100)
        assertFailsWith<EventError> { checkEventShare(EventRole.SPEND, 0) }
        assertFailsWith<EventError> { checkEventShare(EventRole.SPEND, 101) }
        assertFailsWith<EventError> { checkEventShare(EventRole.GIFT_IN, 50) }
        assertFailsWith<EventError> { checkEventShare(EventRole.GIFT_OUT, 99) }
        assertFailsWith<EventError> { checkEventLink(myWedding, EventRole.SPEND, null, txn("t", Direction.OUT, 100), emptyList(), sharePercent = 0) }
    }

    @Test fun spendingUsesTheStoredShareAndGiftsStayWhole() {
        val txns = listOf(txn("s-1", Direction.OUT, 1_000_001), txn("s-2", Direction.OUT, 200_000), txn("g-1", Direction.IN, 300_000))
        val links = listOf(
            link("s-1", "ev-1", EventRole.SPEND).copy(sharePercent = 25),
            link("s-2", "ev-1", EventRole.SPEND),
            link("g-1", "ev-1", EventRole.GIFT_IN, "p-1"),
        )
        val total = summarizeEvent(myWedding, links, txns).totals.single()
        assertEquals(250_000 + 200_000, total.spentMinor, "25% من 10,000.01 = 2,500.0025 ⇒ 2,500.00 + العملية التانية كلها")
        assertEquals(300_000, total.giftsInMinor, "النقطة العملية كلها")
    }
}
