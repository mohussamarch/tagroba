package app.masroufy.ui.screens.home

import app.masroufy.core.ArabicVariant
import app.masroufy.core.CashSummary
import app.masroufy.core.Direction
import app.masroufy.core.Texts
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.screens.home.HomeTestData.bank
import app.masroufy.ui.screens.home.HomeTestData.cash
import app.masroufy.ui.screens.home.HomeTestData.tx
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * لوحة الكاش (`CashDetails` جوه `Main`): من `LoadCashSummary` لحالة اللوحة — مفيش محفظة كاش ⇒ «غير متاح» مش صفر · الأرقام زي ما جت بالهللة ·
 * الصفوف الأحدث بطول ثابت · لون الصف من اتجاه الحركة (داخل الكاش أخضر، خارج منه أحمر) · اسم الصف التاجر ⇒ الملاحظة ⇒ نوعها.
 */
class CashDetailsModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun summary(rows: Int = 2) = CashSummary(
        wallet = cash,
        balanceMinor = 90_000,
        inSinceOpeningMinor = 150_000,
        outSinceOpeningMinor = 80_000,
        spentInPeriodMinor = 110_000,
        periodTransactions = listOf(
            tx("atm", "2026-10-05", 50_000, wallet = bank.id, to = cash.id, note = "سحب من الصراف"),
            tx("cafe", "2026-10-04", 2_500, wallet = cash.id, merchant = "مقهى وهمي"),
        ) + (1..rows - 2).map { tx("x$it", "2026-10-0${(it % 3) + 1}", 1_000L * it, wallet = cash.id) },
    )

    @Test fun noCashWalletIsNotAvailableNotZero() {
        assertNull(cashViewOf(null))
    }

    @Test fun amountsComeAsTheyAreFromTheUseCase() {
        val v = cashViewOf(summary())!!
        assertEquals(90_000, v.balanceMinor)
        assertEquals(110_000, v.spentInPeriodMinor)
        assertEquals("منذ رصيد البداية في 1 سبتمبر", v.sinceLine)
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("من رصيد البداية في 1 سبتمبر", cashViewOf(summary())!!.sinceLine)
    }

    @Test fun rowsAreTheNewestFiveWithToneFromTheDirection() {
        val v = cashViewOf(summary(rows = 8))!!
        assertEquals(CASH_ROWS, v.rows.size, "اللوحة طولها ثابت ⇒ أحدث 5 بس")
        assertEquals(listOf("atm", "cafe"), v.rows.take(2).map { it.id })
        assertEquals(AmountTone.INCOME, v.rows[0].tone, "سحب من البنك للكاش = داخل الكاش")
        assertEquals(AmountTone.EXPENSE, v.rows[1].tone, "دفع كاش = خارج منه")
        assertEquals("سحب من الصراف", v.rows[0].title, "مفيش تاجر ⇒ الملاحظة")
        assertEquals("مقهى وهمي", v.rows[1].title)
    }

    @Test fun moneyComingIntoTheCashWalletItselfIsIncome() {
        val s = summary().copy(periodTransactions = listOf(tx("in", "2026-10-06", 30_000, direction = Direction.IN, wallet = cash.id)))
        assertEquals(AmountTone.INCOME, cashViewOf(s)!!.rows.single().tone)
    }
}
