package app.masroufy.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.masroufy.ui.overlay.ChoiceSlot
import app.masroufy.ui.overlay.OverlayHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * «الضغط برّه بيلغي الاختيار» (المالك 2026-10-15 — HANDOVER §6.5 بند 11): مفتوح ⇒ ضغطة برّه ⇒ مقفول · اللي ما اتأكدش بيرجع ·
 * الكلام المكتوب بيفضل (الكيبورد بس اللي بيتقفل) · زرار الرجوع بيقفل الاختيار قبل اللوحة اللي تحته.
 */
class OpenChoicesTest {
    private val panel = Rect(0f, 400f, 360f, 700f)
    private val trigger = Rect(0f, 340f, 360f, 388f)
    private val inPanel = Offset(100f, 500f)
    private val onTrigger = Offset(100f, 360f)
    private val outside = Offset(100f, 100f)

    /** شاشة صغيرة زي «من إمتى؟» في رسايل البنك: قيمة متأكدة + اختيار ما اتأكدش لسه. */
    private class FromPicker {
        var open = false
        var confirmed = "opening"
        var pending = "opening"
        fun pick(v: String) {
            pending = v
        }
        fun cancel() {
            open = false
            pending = confirmed
        }
    }

    private fun tap(c: OpenChoices, p: Offset): Boolean {
        c.pressed()
        return c.tapped(p)
    }

    @Test fun anOutsideTapClosesTheOpenChoice() {
        val choices = OpenChoices()
        var open = true
        val choice = choices.open(onClose = { open = false })
        choices.area(choice, "panel") { panel }
        tap(choices, outside)
        assertFalse(open, "ضغطة برّه ⇒ اتقفل")
    }

    @Test fun tapsInsideThePanelOrOnItsOwnButtonKeepItOpen() {
        val choices = OpenChoices()
        var closes = 0
        val choice = choices.open(onClose = { closes++ })
        choices.area(choice, "panel") { panel }
        choices.area(choice, "button") { trigger }
        tap(choices, inPanel)
        tap(choices, onTrigger)
        assertEquals(0, closes, "جوه اللوحة أو على زرارها (اللي بيقفلها بنفسه) ⇒ مش «برّه»")
    }

    @Test fun cancellingDiscardsTheUnconfirmedValue() {
        val choices = OpenChoices()
        val screen = FromPicker().apply { open = true }
        val choice = choices.open(onClose = screen::cancel)
        choices.area(choice, "panel") { panel }
        screen.pick("other")
        tap(choices, outside)
        assertFalse(screen.open)
        assertEquals("opening", screen.pending, "اللي اتختار من غير «ابدأ» راح")
        assertEquals("opening", screen.confirmed, "ومفيش حاجة اتحفظت")
    }

    @Test fun typedTextStaysAndOnlyTheKeyboardCloses() {
        val choices = OpenChoices()
        var name = ""
        choices.keeper("field") { Rect(0f, 200f, 360f, 248f) }
        name += "قسط"
        assertFalse(tap(choices, Offset(50f, 220f)), "الضغط جوه الخانة ما بيقفلش الكيبورد")
        assertTrue(tap(choices, outside), "برّه الخانة ⇒ الكيبورد يتقفل")
        assertEquals("قسط", name, "الكلام زي ما هو")
    }

    @Test fun aSheetWithTypedTextStaysOpenOnAnOutsideTap() {
        val guard = FormGuard()
        assertTrue(guard.outsideTap(), "لوحة ما اتكتبش فيها ⇒ الضغط برّه بيقفلها زي الأول")
        guard.onTyped()
        assertFalse(guard.outsideTap(), "اتكتب فيها ⇒ تفضل")
        assertEquals(1, guard.nudges, "وتهتز عشان المالك يعرف إنها لسه مفتوحة")
        guard.reset()
        assertTrue(guard.outsideTap())
    }

    @Test fun theGestureThatOpenedAChoiceDoesNotCloseItOnRelease() {
        val choices = OpenChoices()
        var open = false
        choices.pressed()
        // ضغط مطوّل فتح وضع التعديل والصباع لسه نازل
        open = true
        val choice = choices.open(onClose = { open = false })
        choices.area(choice, "stage") { panel }
        choices.tapped(outside)
        assertTrue(open, "رفع الصباع بعد الضغط المطوّل ما بيقفلش اللي لسه فاتح")
        tap(choices, outside)
        assertFalse(open, "الضغطة اللي بعدها برّه بتقفله")
    }

    @Test fun aChoiceUnderASheetIgnoresTapsInsideThatSheet() {
        val choices = OpenChoices()
        var open = true
        var covered = true
        val choice = choices.open(onClose = { open = false })
        choices.area(choice, "panel") { panel }
        choice.covered = { covered }
        tap(choices, outside)
        assertTrue(open, "فيه لوحة فوقه ⇒ الضغط جواها مش «برّه»")
        covered = false
        tap(choices, outside)
        assertFalse(open)
    }

    @Test fun scrollingClosesEditModesButNotPanelsInsideTheContent() {
        val choices = OpenChoices()
        var mode = true
        var panelOpen = true
        val edit = choices.open(onClose = { mode = false }, closeOnScroll = true)
        choices.area(edit, "stage") { Rect(0f, 0f, 360f, 300f) }
        val inline = choices.open(onClose = { panelOpen = false })
        choices.area(inline, "panel") { panel }
        choices.pressed()
        choices.dragged(Offset(100f, 350f))
        assertFalse(mode, "وضع التعديل بيتقفل بالتمرير (§84)")
        assertTrue(panelOpen, "اللوحة اللي جوه المحتوى بتتحرك معاه ومش بتتقفل")
    }

    @Test fun aDragThatStartsInsideKeepsTheChoiceOpen() {
        val choices = OpenChoices()
        var mode = true
        val edit = choices.open(onClose = { mode = false }, closeOnScroll = true)
        choices.area(edit, "stage") { panel }
        choices.pressed()
        choices.dragged(inPanel)
        assertTrue(mode, "السحب جوه المسرح = ترتيب الأشخاص")
    }

    @Test fun aRemovedAreaNoLongerCountsAsInside() {
        val choices = OpenChoices()
        var open = true
        val choice = choices.open(onClose = { open = false })
        choices.area(choice, "panel") { null }
        tap(choices, inPanel)
        assertFalse(open, "منطقة اتشالت من الشاشة ⇒ مفيش «جوه»")
    }

    @Test fun aClosedChoiceIsForgotten() {
        val choices = OpenChoices()
        var closes = 0
        val choice = choices.open(onClose = { closes++ })
        choices.closed(choice)
        tap(choices, outside)
        assertEquals(0, closes)
        assertEquals(0, choices.openCount)
    }

    @Test fun backClosesTheChoiceBeforeTheSheetUnderIt() {
        val host = OverlayHost()
        var sheetOpen = true
        val sheet = host.newEntry().also { it.onBack = { sheetOpen = false } }
        host.entries += sheet
        val choices = OpenChoices()
        var open = true
        val slot = ChoiceSlot(choices.prepare({ open = false }, closeOnScroll = false), host)
        slot.onRemembered()
        assertTrue(host.handleBack())
        assertFalse(open, "الرجوع بيقفل الاختيار الأول")
        assertTrue(sheetOpen, "واللوحة اللي هو جواها لسه مفتوحة")
        slot.onForgotten()
        assertTrue(host.handleBack())
        assertFalse(sheetOpen)
    }

    @Test fun aRealSheetOpenedLaterCoversTheChoiceButAnotherChoiceDoesNot() {
        val host = OverlayHost()
        val choices = OpenChoices()
        val first = ChoiceSlot(choices.prepare({}, closeOnScroll = false), host).also { it.onRemembered() }
        val second = ChoiceSlot(choices.prepare({}, closeOnScroll = false), host).also { it.onRemembered() }
        assertFalse(first.choice.covered(), "اختيار تاني فوقه مش لوحة")
        val sheet = host.newEntry()
        host.entries += sheet
        assertTrue(first.choice.covered(), "لوحة اتفتحت بعده ⇒ متغطي")
        host.entries -= sheet
        assertFalse(second.choice.covered())
    }
}
