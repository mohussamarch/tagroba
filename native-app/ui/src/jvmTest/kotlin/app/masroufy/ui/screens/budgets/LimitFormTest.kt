package app.masroufy.ui.screens.budgets

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Language
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.uiText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** لوحة السقف (`BudgetLimitSheet`): الخانات والتحقق قبل `SetBudget` — الكتابة بالأرقام العربي، والنسبة ٧٠/٨٠/٩٠ أو «أخرى» من ١ لـ١٠٠. */
class LimitFormTest {
    @BeforeTest fun texts() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun anEmptyLimitStartsWithTheAlertOnAtEighty() {
        val d = LimitDraft.from(LimitCurrent(null, null, false), Currency.SAR)
        assertEquals("", d.limitText)
        assertTrue(d.alertOn)
        assertEquals(80, d.preset)
    }

    @Test fun anExistingLimitFillsTheFieldsWithoutDecimalsWhenWhole() {
        val d = LimitDraft.from(LimitCurrent(120_000, 90, notify = true), Currency.SAR)
        assertEquals("1200", d.limitText)
        assertEquals(90, d.preset)
        assertEquals("", d.customText)
        val custom = LimitDraft.from(LimitCurrent(120_050, 75, notify = true), Currency.SAR)
        assertEquals("1200.50", custom.limitText)
        assertNull(custom.preset)
        assertEquals("75", custom.customText, "نسبة مش من الثلاثة ⇒ في «أخرى»")
        assertFalse(LimitDraft.from(LimitCurrent(120_000, 80, notify = false), Currency.SAR).alertOn)
    }

    @Test fun arabicDigitsAreReadToHalalas() {
        val ok = assertIs<LimitCheck.Ok>(checkLimit(LimitDraft("١٬٢٠٠٫٥٠", alertOn = true, preset = 70, customText = ""), Currency.SAR))
        assertEquals(120_050L, ok.limitMinor)
        assertEquals(70, ok.thresholdPercent)
        assertTrue(ok.notify)
    }

    @Test fun zeroOrBlankLimitIsRefusedWithTheUseCaseMessage() {
        val bad = assertIs<LimitCheck.Bad>(checkLimit(LimitDraft("0", alertOn = false, preset = 80, customText = ""), Currency.SAR))
        assertEquals(uiText(TextKey.BUDGET_LIMIT_POSITIVE), bad.limitError)
        assertNull(bad.percentError)
        assertIs<LimitCheck.Bad>(checkLimit(LimitDraft("", alertOn = false, preset = 80, customText = ""), Currency.SAR))
    }

    @Test fun customPercentMustBeAWholeNumberFromOneToHundred() {
        assertEquals(55, customPercent("٥٥"))
        assertNull(customPercent("120"))
        assertNull(customPercent("0"))
        assertNull(customPercent("7.5"))
        val bad = assertIs<LimitCheck.Bad>(checkLimit(LimitDraft("500", alertOn = true, preset = null, customText = "120"), Currency.SAR))
        assertEquals(uiText(TextKey.BUDGET_THRESHOLD_RANGE), bad.percentError)
        // التنبيه مقفول ⇒ النسبة الغلط ما بتمنعش الحفظ، والتنبيه بيتحفظ مقفول
        val off = assertIs<LimitCheck.Ok>(checkLimit(LimitDraft("500", alertOn = false, preset = 80, customText = ""), Currency.SAR))
        assertFalse(off.notify)
    }
}
