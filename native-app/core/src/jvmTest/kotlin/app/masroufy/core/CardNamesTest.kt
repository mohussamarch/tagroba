package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** r06-darahim (§12-4): تنضيف اسم الكارت للعرض — أسامي مخترعة. */
class CardNamesTest {
    @Test fun dropsKnownCityAndCountryCode() {
        assertEquals("Test Diner", cleanCardName("TEST DINER RIYADH SA"))
        assertEquals("Sample Shop", cleanCardName("sample shop riyadh sa".uppercase()))
        assertEquals("Demo Cafe", cleanCardName("DEMO CAFE  AL KHOBAR  SA"))
        assertEquals("Fake Bakery", cleanCardName("FAKE BAKERY JEDDAH"))
        assertEquals("Mock Store", cleanCardName("MOCK STORE CAIRO EG"))
        assertEquals("Trial Mall", cleanCardName("TRIAL MALL DUBAI AE"))
    }

    @Test fun keepsWhatIsNotACity() {
        assertEquals("Some Shop Sa", cleanCardName("SOME SHOP SA"), "من غير مدينة معروفة الكود ما بيتشالش")
        assertEquals("Riyadh", cleanCardName("RIYADH"), "الاسم ما يتشالش كله")
        assertEquals("Riyadh Sa", cleanCardName("RIYADH SA"))
        assertEquals("Test Riyadh Gallery", cleanCardName("TEST RIYADH GALLERY"), "المدينة في النص مش في الآخر")
    }

    @Test fun titleCasesOnlyAllCapsLatin() {
        assertEquals("Mixed Case Name", cleanCardName("Mixed Case Name"))
        assertEquals("مطعم تجربة", cleanCardName("مطعم تجربة الرياض"))
        assertEquals("7eleven Test", cleanCardName("7ELEVEN TEST"))
        assertEquals("Shop.com", cleanCardName("SHOP.COM"))
        assertEquals("", cleanCardName("   "))
    }

    @Test fun displayTitleUsesTheCleanNameAndKeepsTheRaw() {
        val t = Transaction(
            id = "t", occurredAt = "2026-10-07", datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
            observedDirection = Direction.OUT, amountMinor = 1_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", rawMerchantName = "TEST DINER RIYADH SA",
        )
        assertEquals("Test Diner", displayTitleOf(t))
        assertEquals("TEST DINER RIYADH SA", t.rawMerchantName)
        val m = rememberMerchant(emptyList(), t.rawMerchantName!!, "cat-x", "m-1")!!
        assertEquals("Test Diner", m.displayName)
        assertEquals(normalizeText("TEST DINER RIYADH SA"), m.normalizedName, "المطابقة على الأصلي")
    }
}
