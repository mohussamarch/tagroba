package app.masroufy.ui.screens.dues

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.ObligationKind
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «الديون» (`DuesDebts`): `listWithBalances` + المجاميع + مواعيد الديون ⇒ مجموعات «لك» و«عليك» و«أمانات» منفصلة (من غير مقاصة)،
 * بالترتيب، مع البحث. مجموع المجموعة جاي من `DuesTotals` زي ما هو، ومع البحث بيختفي (مفيش حالة استخدام بتجمع نتيجة البحث).
 */
class DuesDebtsModelTest {
    private val fahd = personRow("p1", "فهد", obligation("o1", "p1", ObligationKind.RECEIVABLE, 150_000) to 120_000)
    private val omar = personRow(
        "p2", "عمر",
        obligation("o2", "p2", ObligationKind.LOAN_PAYABLE, 200_000) to 200_000,
        obligation("o3", "p2", ObligationKind.CUSTODY_PAYABLE, 30_000) to 30_000,
    )
    private val laila = personRow("p3", "ليلى", obligation("o4", "p3", ObligationKind.RECEIVABLE, 45_000, opening = true) to 45_000)
    private val people = listOf(fahd, omar, laila)
    private val sums = totals(receivable = 165_000, loans = 200_000, custody = 30_000)
    private val dues = listOf(
        debtDue("o1", "2026-11-15", 120_000, receive = true),
        debtDue("o4", "2026-10-01", 45_000, receive = true),
        // ميعادين لنفس الدين ⇒ الأقرب بيكسب
        debtDue("o2", "2027-03-01", 100_000, receive = false),
        debtDue("o2", "2026-12-01", 100_000, receive = false),
    )

    @BeforeTest fun arabic() = useArabic()

    @AfterTest fun reset() = resetTexts()

    @Test fun groupsStaySeparateAndOverdueComesFirst() {
        val ui = duesDebtsUi(people, sums, dues, TODAY, Currency.SAR, DebtSide.ALL, "")
        assertEquals(listOf("for", "on", "custody"), ui.groups.map { it.key })
        val forYou = ui.groups[0]
        assertEquals(165_000L, forYou.totalMinor, "المجموع من `DuesTotals` زي ما هو")
        assertEquals(listOf("o4", "o1"), forYou.rows.map { it.obligationId }, "اللي فات موعده الأول")
        val late = forYou.rows[0]
        assertEquals(Chip.OVERDUE, late.chip)
        assertEquals("فات موعدها منذ " + sentenceNumber(8) + " أيام", late.chipText)
        assertEquals("دين قديم من قبل التطبيق", late.reason)
        assertEquals("موعدها " + dayMonth("2026-10-01"), late.dueText)
        assertEquals("موعدها " + dayMonth("2026-12-01"), ui.groups[1].rows.single().dueText, "أقرب ميعاد للدين")
        assertEquals(30_000L, ui.groups[2].totalMinor, "الأمانات لوحدها")
        assertEquals("بلا موعد", ui.groups[2].rows.single().dueText)
        assertNull(ui.groups[2].rows.single().chipText, "من غير موعد ⇒ مفيش إشارة («منتظر منذ» مش متاح)")
        assertFalse(ui.empty)
    }

    @Test fun sidesCountPeopleAndCarryTheTotals() {
        val ui = duesDebtsUi(people, sums, dues, TODAY, Currency.SAR, DebtSide.ALL, "")
        val (forYou, onYou) = ui.sides
        assertEquals("عند شخصين", forYou.count)
        assertEquals(165_000L, forYou.totalMinor)
        assertEquals("لشخص واحد", onYou.count, "الأمانة مش دين ⇒ عمر بيتعد مرة واحدة")
        assertEquals(200_000L, onYou.totalMinor)
        val none = duesDebtsUi(listOf(fahd), totals(receivable = 120_000), emptyList(), TODAY, Currency.SAR, DebtSide.ALL, "")
        assertEquals("لا أحد", none.sides[1].count, "صفر ⇒ «لا أحد» مش «٠ أشخاص»")
    }

    @Test fun aSideFilterShowsOnlyThatSide() {
        val forYou = duesDebtsUi(people, sums, dues, TODAY, Currency.SAR, DebtSide.FOR_YOU, "")
        assertEquals(listOf("for"), forYou.groups.map { it.key })
        val onYou = duesDebtsUi(people, sums, dues, TODAY, Currency.SAR, DebtSide.ON_YOU, "")
        assertEquals(listOf("on", "custody"), onYou.groups.map { it.key })
    }

    @Test fun searchMatchesNamesAndHidesGroupTotals() {
        val ui = duesDebtsUi(people, sums, dues, TODAY, Currency.SAR, DebtSide.ALL, " فهد ")
        assertEquals(listOf("o1"), ui.groups.flatMap { it.rows }.map { it.obligationId })
        assertNull(ui.groups.single().totalMinor, "مجموع نتيجة البحث مش محسوب ⇒ ما بيظهرش")
        val miss = duesDebtsUi(people, sums, dues, TODAY, Currency.SAR, DebtSide.FOR_YOU, "زياد")
        assertTrue(miss.groups.isEmpty())
        assertEquals("لا أحد باسم «زياد»", miss.noHitsTitle)
        assertEquals("البحث داخل «لك» فقط.", miss.noHitsBody)
    }

    @Test fun noDebtsAtAllIsTheEmptyState() {
        val ui = duesDebtsUi(emptyList(), totals(), emptyList(), TODAY, Currency.SAR, DebtSide.ALL, "")
        assertTrue(ui.empty)
        assertEquals("لا شيء هنا", ui.noHitsTitle)
    }

    @Test fun egyptWording() {
        useArabic(ArabicVariant.EGYPTIAN)
        val ui = duesDebtsUi(people, sums, dues, TODAY, Currency.SAR, DebtSide.ALL, "")
        assertEquals("ليك", ui.groups[0].title)
        assertEquals("ميعادها فات من " + sentenceNumber(8) + " أيام", ui.groups[0].rows[0].chipText)
        assertEquals("من غير ميعاد", ui.groups[2].rows.single().dueText)
        val miss = duesDebtsUi(people, sums, dues, TODAY, Currency.SAR, DebtSide.ALL, "زياد")
        assertEquals("مفيش حد اسمه «زياد»", miss.noHitsTitle)
        assertEquals("جرّب حتة من الاسم.", miss.noHitsBody)
    }
}
