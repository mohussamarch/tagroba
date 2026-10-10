package app.masroufy.ui.screens.imports

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** «إعداد القراءة»: المرسل المعروف بيتعرض ببنكه من الدليل (§74 — المؤكد بس، وفي بلد المساحة)، والمش معروف بيفضل باسمه الخام. */
class SenderBankTest {
    @AfterTest fun reset() = Fx.resetTexts()

    @Test fun knownSendersGetTheirBankUnknownStayRaw() {
        val overview = SmsOverview(Fx.inbox(senders = listOf("AlRajhiBank", "ALWAHA", "CIB")), emptyList(), emptyList(), emptyMap())
        val saudi = smsSettingsUi(overview, emptyList(), "SA").senders
        assertEquals("sa-al-rajhi-bank", saudi[0].bank?.key)
        assertNull(saudi[1].bank, "مرسل مش في الدليل ⇒ اسمه الخام")
        assertNull(saudi[2].bank, "«CIB» مرسل مصري — في السعودية ما يتخمنش")
        assertEquals("eg-cib-egypt", smsSettingsUi(overview, emptyList(), "EG").senders[2].bank?.key)
        assertEquals(listOf("AlRajhiBank", "ALWAHA", "CIB"), saudi.map { it.sender }, "الاسم الخام محفوظ زي ما هو (الربط بالمحفظة بيه)")
    }
}
