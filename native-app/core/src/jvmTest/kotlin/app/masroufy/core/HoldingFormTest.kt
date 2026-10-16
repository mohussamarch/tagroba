package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «ضيف حاجة» — من الخانات المكتوبة لـ`NewHolding` بأعداد صحيحة بس (مواصفة r05 §2.6). */
class HoldingFormTest {
    private fun ok(form: HoldingFormText, currency: Currency = Currency.SAR): NewHolding =
        assertIs<HoldingFormCheck.Ok>(checkHoldingForm(form, currency, "الاسم الافتراضي")).holding

    @Test fun everyTypeHasItsRequiredField() {
        for (type in HoldingType.entries) {
            val check = checkHoldingForm(HoldingFormText(type), Currency.SAR, "x")
            val missing = assertIs<HoldingFormCheck.Missing>(check, type.wire)
            val expected = when {
                type.metal -> HoldingField.GRAMS
                type == HoldingType.STK -> HoldingField.QTY
                else -> HoldingField.VALUE
            }
            assertEquals(expected, missing.field, type.wire)
        }
    }

    @Test fun gramsBecomeQuantityAndPriceBecomesHalalasWithArabicDigits() {
        val h = ok(HoldingFormText(HoldingType.GOLD).with(HoldingField.GRAMS, "١٢٠٫٥").with(HoldingField.PRICE, "٢٬٣٤٥٫٥"))
        assertEquals(12_050_000_000L, h.quantity) // 120.5 جرام × 10^8
        assertEquals(234_550L, h.priceMinor)
        assertEquals(21, h.karat)
        assertNull(h.fineness)
        assertEquals(HoldingPurpose.INVEST, h.purpose)
        assertEquals("الاسم الافتراضي", h.name)
    }

    @Test fun jewelleryKeepsMakingAndSwitchesPurposeToPersonal() {
        val form = HoldingFormText(HoldingType.GOLD).withForm(MetalForm.JEWEL).copy(karat = 18)
            .with(HoldingField.GRAMS, "10").with(HoldingField.PRICE, "4000").with(HoldingField.MAKING, "350.25")
        val h = ok(form)
        assertTrue(h.jewellery)
        assertEquals(HoldingPurpose.PERSONAL, h.purpose)
        assertEquals(35_025L, h.makingMinor)
        assertEquals(18, h.karat)
        // الرجوع للسبايك ⇒ الغرض الافتراضي والمصنعية مش خانة
        val bars = ok(form.withForm(MetalForm.BARS))
        assertEquals(HoldingPurpose.INVEST, bars.purpose)
        assertNull(bars.makingMinor)
    }

    @Test fun silverUsesFineness() {
        val h = ok(HoldingFormText(HoldingType.SILVER).copy(karat = 925).with(HoldingField.GRAMS, "50"))
        assertEquals(925, h.fineness)
        assertNull(h.karat)
        assertEquals(HoldingPurpose.RESERVE, h.purpose)
        assertNull(h.priceMinor) // مش مكتوب ⇒ null، مش صفر
    }

    @Test fun propertyValueAreaPlaceAndRentOnlyWhenRented() {
        val base = HoldingFormText(HoldingType.FLAT).with(HoldingField.VALUE, "750,000").with(HoldingField.AREA, "120")
            .with(HoldingField.PLACE, "  حي مخترع  ").with(HoldingField.RENT, "3000")
        val notRented = ok(base)
        assertEquals(75_000_000L, notRented.valueMinor)
        assertEquals(120L * QUANTITY_SCALE, notRented.areaSqm)
        assertEquals("حي مخترع", notRented.place)
        assertNull(notRented.monthlyRentMinor)
        assertEquals(QUANTITY_SCALE, notRented.quantity)
        assertEquals(300_000L, ok(base.copy(rented = true)).monthlyRentMinor)
        assertEquals(HoldingPurpose.TRADE, ok(HoldingFormText(HoldingType.SHOP).with(HoldingField.VALUE, "1")).purpose)
        assertEquals(listOf(HoldingField.VALUE, HoldingField.AREA, HoldingField.PLACE), fieldsFor(HoldingType.LAND, rented = true))
    }

    @Test fun sharesAndCompanyStake() {
        val stk = ok(HoldingFormText(HoldingType.STK).with(HoldingField.NAME, "شركة مخترعة").with(HoldingField.QTY, "40").with(HoldingField.PRICE, "1000"))
        assertEquals(40L * QUANTITY_SCALE, stk.quantity)
        assertEquals("شركة مخترعة", stk.name)
        assertEquals(100_000L, stk.priceMinor)
        val co = ok(HoldingFormText(HoldingType.CO).with(HoldingField.SHARE, "12.5").with(HoldingField.VALUE, "90000"), Currency.EGP)
        assertEquals(1250, co.sharePercentBp)
        assertEquals(9_000_000L, co.valueMinor)
        assertIs<HoldingFormCheck.Invalid>(checkHoldingForm(HoldingFormText(HoldingType.CO).with(HoldingField.SHARE, "120").with(HoldingField.VALUE, "1"), Currency.SAR, "x"))
    }

    @Test fun badInputIsNeverSilentlyRounded() {
        // أكتر من خانتين عشريتين في المبلغ المطلوب ⇒ «اكتب القيمة» مش تقريب
        assertIs<HoldingFormCheck.Missing>(checkHoldingForm(HoldingFormText(HoldingType.CAR).with(HoldingField.VALUE, "10.555"), Currency.SAR, "x"))
        assertIs<HoldingFormCheck.Missing>(checkHoldingForm(HoldingFormText(HoldingType.CAR).with(HoldingField.VALUE, "0"), Currency.SAR, "x"))
        assertIs<HoldingFormCheck.Missing>(checkHoldingForm(HoldingFormText(HoldingType.GOLD).with(HoldingField.GRAMS, "abc"), Currency.SAR, "x"))
        val bad = checkHoldingForm(HoldingFormText(HoldingType.GOLD).with(HoldingField.GRAMS, "5").with(HoldingField.PRICE, "x"), Currency.SAR, "x")
        assertEquals(HoldingField.PRICE, assertIs<HoldingFormCheck.Invalid>(bad).field)
    }
}
