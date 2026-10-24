package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** fix055: نصوص `TextsR06Fix055.kt` — «مَن هذا؟» للخارج بـ«إلى» في اللهجتين · جمع سطر البيعات اللي مش محسوبة (§117-2). */
class Fix055TextsTest {
    @Test fun outgoingWhoIsThisSaysToInBothDialects() {
        val out = partyKindTitleKey(outgoing = true)
        for (texts in listOf(MSA_R06_FIX055_TEXTS, EGYPTIAN_R06_FIX055_TEXTS)) {
            val line = texts.getValue(out as R06Key)
            assertTrue("إلى {0}" in line, line)
            assertFalse(line.contains("من {0}"), line)
        }
        assertEquals(UiKey.TRANSFER_PARTY_KIND_TITLE, partyKindTitleKey(outgoing = false), "الداخل زي ما هو «من X»")
    }

    @Test fun skippedSalesLineUsesArabicPlurals() {
        assertEquals(R06Key.FX55_GAIN_SKIPPED_ONE, gainSkippedKey(1))
        assertEquals(R06Key.FX55_GAIN_SKIPPED_TWO, gainSkippedKey(2))
        assertEquals(R06Key.FX55_GAIN_SKIPPED_FEW, gainSkippedKey(3))
        assertEquals(R06Key.FX55_GAIN_SKIPPED_FEW, gainSkippedKey(10))
        assertEquals(R06Key.FX55_GAIN_SKIPPED_MANY, gainSkippedKey(11))
        assertEquals("بيعة واحدة مش محسوبة (التكلفة مش متسجلة)", EGYPTIAN_R06_FIX055_TEXTS.getValue(R06Key.FX55_GAIN_SKIPPED_ONE))
        for (texts in listOf(MSA_R06_FIX055_TEXTS, EGYPTIAN_R06_FIX055_TEXTS, ENGLISH_R06_FIX055_TEXTS)) {
            assertEquals(R06Key.entries.filter { it.name.startsWith("FX55_") }.toSet(), texts.keys, "كل المفاتيح في كل لغة")
        }
    }
}
