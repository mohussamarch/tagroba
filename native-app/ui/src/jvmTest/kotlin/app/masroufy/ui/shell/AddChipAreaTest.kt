package app.masroufy.ui.shell

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * r041 A1 — لوحة «+»: مكان الشرايح طوله ثابت في كل التبويبات (3 صفوف = 136)، فالتبويبات و«بصوتك» ما يتنقلوش لما تبدّل صرف/دخل/تحويل.
 * (مفيش اختبار واجهة Compose في المشروع — الضغط على مكان التبويب القديم بعد تبديل التبويبات بيتجرب على المحاكي.)
 */
class AddChipAreaTest {
    @Test fun theChipAreaIsThreeChipRowsOnEveryTab() {
        assertEquals(136.dp, ADD_CHIP_AREA)
        assertEquals(40.dp, chipAreaHeight(1))
        assertEquals(88.dp, chipAreaHeight(2), "the old minimum, now fixed at three rows")
    }
}
