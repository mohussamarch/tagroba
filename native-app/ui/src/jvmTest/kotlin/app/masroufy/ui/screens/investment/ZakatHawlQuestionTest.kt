package app.masroufy.ui.screens.investment

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.HawlState
import app.masroufy.core.OWNED_LONG_AGO
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.Texts
import app.masroufy.core.ZakatCountry
import app.masroufy.core.ZakatHolding
import app.masroufy.core.ZakatMetal
import app.masroufy.core.ZakatPrices
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.assessZakat
import app.masroufy.core.zakatItems
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §121-7: لوحة الوقائع بتسأل «عدّى عليها سنة هجرية؟» للحاجة «من زمان»، والسطر بيقول إنه مستني الإجابة. */
class ZakatHawlQuestionTest {
    @BeforeTest fun egyptian() { Texts.arabicVariant = ArabicVariant.EGYPTIAN }
    @AfterTest fun reset() { Texts.arabicVariant = ArabicVariant.MSA }

    private fun assessment(hawl: Boolean?) = assessZakat(
        ZakatCountry.SA, Currency.SAR, "2026-02-18",
        zakatItems(
            ZakatCountry.SA,
            listOf(ZakatHolding.Metal("a", "دهب وهمي", ZakatMetal.GOLD, 100 * QUANTITY_SCALE, 21, null, ZakatPurpose.SAVING, null, OWNED_LONG_AGO, true, hawl)),
            ZakatPrices(30_000, 350),
        ),
        208_250, HawlState.Complete(true),
    )

    @Test fun unansweredHawlIsAMissingQuestionAndTheTotalIsUnavailable() {
        val a = assessment(null)
        assertNull(a.totalZakatableMinor, "غير متاح لحد الإجابة")
        val q = factQuestions(a, Currency.SAR).single { it.ask == "عدّى عليها سنة هجرية؟" }
        assertTrue(q.missing)
        assertEquals(listOf(FactAnswer.Hawl(true), FactAnswer.Hawl(false)), q.options.map { it.answer })
        assertEquals("ناقصة واقعة: عدّى عليها سنة هجرية؟", missingText(a.items.single()))
    }

    @Test fun answeredHawlShowsTheChoiceAndCounts() {
        val a = assessment(true)
        val q = factQuestions(a, Currency.SAR).single { it.ask == "عدّى عليها سنة هجرية؟" }
        assertEquals(false, q.missing)
        assertEquals(listOf(true, false), q.options.map { it.selected })
        assertTrue((a.totalZakatableMinor ?: 0) > 0)
    }
}
