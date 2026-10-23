package app.masroufy.ui.screens.imports

import app.masroufy.core.senderBelongsElsewhere
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «إعداد القراءة»: المرسل المعروف بيتعرض ببنكه من الدليل (§74 — المؤكد بس، وفي بلد المساحة)، والمش معروف بيفضل باسمه الخام. */
class SenderBankTest {
    @AfterTest fun reset() = Fx.resetTexts()

    @Test fun knownSendersGetTheirBankUnknownStayRaw() {
        val overview = SmsOverview(Fx.inbox(senders = listOf("AlRajhiBank", "ALWAHA", "CIB")), emptyList(), emptyList(), emptyMap())
        val saudi = smsSettingsUi(overview, emptyList(), "SA").senders
        assertEquals("sa-al-rajhi-bank", saudi[0].bank?.key)
        assertNull(saudi[1].bank, "مرسل مش في الدليل ⇒ اسمه الخام")
        assertEquals(listOf("AlRajhiBank", "ALWAHA"), saudi.map { it.sender }, "«CIB» مرسل مصري — ما يظهرش في السعودية")
        val egypt = smsSettingsUi(overview, emptyList(), "EG").senders
        assertEquals("eg-cib-egypt", egypt.last().bank?.key)
    }

    /** فحص 0.5.3: `AlRajhiBank` كان ظاهر في مصر ومكتوب «بتتسجل في: البنك الأهلي المصري». */
    @Test fun eachCountryListsOnlyItsOwnKnownSendersButKeepsTheWholeDeviceList() {
        val overview = SmsOverview(
            Fx.inbox(senders = listOf("AlRajhiBank", "CIB", "QA_LOCAL")), emptyList(), emptyList(),
            senderWallets = mapOf("AlRajhiBank" to "eg-bank", "CIB" to "eg-bank"),
        )
        val egypt = smsSettingsUi(overview, emptyList(), "EG")
        assertEquals(listOf("CIB", "QA_LOCAL"), egypt.senders.map { it.sender }, "المرسل السعودي ما يظهرش ولا يتربط في مصر")
        assertEquals(listOf("AlRajhiBank", "CIB", "QA_LOCAL"), egypt.allSenders, "قايمة الجهاز كلها بتتبعت لـenable — السعودي ما يتمسحش")
        assertEquals(listOf("AlRajhiBank", "QA_LOCAL"), smsSettingsUi(overview, emptyList(), "SA").senders.map { it.sender })
    }

    @Test fun senderBelongsElsewhereOnlyForKnownSendersOfTheOtherCountry() {
        assertTrue(senderBelongsElsewhere("AlRajhiBank", "EG"))
        assertTrue(senderBelongsElsewhere("alrajhi-bank", "EG"), "من غير حالة حروف ولا رموز")
        assertFalse(senderBelongsElsewhere("AlRajhiBank", "SA"))
        assertFalse(senderBelongsElsewhere("QA_UNKNOWN", "EG"), "مش في الدليل ⇒ ما نعرفش بلده فبيفضل")
        assertFalse(senderBelongsElsewhere("AlRajhiBank", null))
    }
}
