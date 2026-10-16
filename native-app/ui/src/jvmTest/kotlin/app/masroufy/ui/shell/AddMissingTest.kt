package app.masroufy.ui.shell

import app.masroufy.core.UiKey
import app.masroufy.usecase.AddKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * r041b P7 (فحص المحاكي r041 — اللقطات 46 · 67): «احفظ» المعطّل في لوحة «+» بيقول أول حاجة ناقصة، بترتيب اللوحة من فوق لتحت.
 * (الضغطة نفسها بعد قفل الكيبورد بتتجرب على المحاكي — مفيش اختبار واجهة Compose في المشروع.)
 */
class AddMissingTest {
    @Test fun expenseNeedsAmountThenCategoryThenWallet() {
        assertEquals(UiKey.ADD_NEED_AMOUNT, addMissing(AddKind.OUT, " ", null, hasCategory = false, hasIncome = false, to = null))
        assertEquals(UiKey.ADD_NEED_CATEGORY, addMissing(AddKind.OUT, "75", "w1", hasCategory = false, hasIncome = false, to = null))
        assertEquals(UiKey.ADD_NEED_FROM, addMissing(AddKind.OUT, "75", null, hasCategory = true, hasIncome = false, to = null))
        assertNull(addMissing(AddKind.OUT, "75", "w1", hasCategory = true, hasIncome = false, to = null))
    }

    @Test fun incomeNeedsItsKind() {
        assertEquals(UiKey.ADD_NEED_INCOME_KIND, addMissing(AddKind.IN, "300", "w1", hasCategory = true, hasIncome = false, to = null))
        assertNull(addMissing(AddKind.IN, "300", "w1", hasCategory = false, hasIncome = true, to = null))
    }

    @Test fun transferNeedsAnotherWallet() {
        assertEquals(UiKey.ADD_NEED_TO, addMissing(AddKind.MOVE, "50", "w1", hasCategory = false, hasIncome = false, to = null))
        assertEquals(UiKey.ADD_NEED_TO, addMissing(AddKind.MOVE, "50", "w1", hasCategory = false, hasIncome = false, to = "w1"))
        assertNull(addMissing(AddKind.MOVE, "50", "w1", hasCategory = false, hasIncome = false, to = "w2"))
    }
}
