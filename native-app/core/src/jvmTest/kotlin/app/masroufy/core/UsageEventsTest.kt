package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** بيانات الاستخدام (OVERRIDES §114): القيم اللي بتاخد نص بتقبل شكل ثابت بس — أي حاجة شبه بيانات بتطلع «other». */
class UsageEventsTest {
    @Test fun senderIsTheCatalogIdOrOther() {
        assertEquals("AlRajhiBank", UsageValue.sender("alrajhibank").text)
        assertEquals("other", UsageValue.sender("0551234567").text)
        assertEquals("other", UsageValue.sender("Ahmed Ali").text)
        assertEquals("other", UsageValue.sender("").text)
    }

    @Test fun screenIsACodeNameOrOther() {
        assertEquals("Backup", UsageValue.screen("Backup").text)
        assertEquals("other", UsageValue.screen("محمد").text)
        assertEquals("other", UsageValue.screen("Person 12 SAR 150.00").text)
        assertEquals("other", UsageValue.screen("A".repeat(41)).text)
    }

    @Test fun ruleIdIsBuiltInOnly() {
        assertEquals("rule-0042", UsageValue.ruleId("rule-0042").text)
        assertEquals("other", UsageValue.ruleId("r-8f3a91c2").text)
        assertEquals("other", UsageValue.ruleId("rule-0042 Starbucks").text)
    }

    @Test fun countsAreSmallAndBucketsFixed() {
        assertEquals(999L, UsageValue.count(150_000).number)
        assertEquals(0L, UsageValue.count(-3).number)
        assertEquals(listOf("0", "1", "2-3", "2-3", "4+"), listOf(0, 1, 2, 3, 9).map { UsageValue.accountsBucket(it).text })
    }

    @Test fun everyEventCarriesOnlyCatalogParams() {
        val events = listOf(
            UsageEvents.onboardingStep(UsagePhase.REACHED, UsagePhase.COMPLETED),
            UsageEvents.permission(UsagePermission.SMS, true),
            UsageEvents.screenOpened("Home"),
            UsageEvents.categorySet(UsageCategoryBefore.AUTO),
            UsageEvents.builtInRuleOverridden("rule-0001"),
            UsageEvents.smsParse("AlRajhiBank", 10, 2),
            UsageEvents.smsNewShapeWaiting("+966500000000", 1),
            UsageEvents.backgroundGaveUp(), UsageEvents.plusDismissed(), UsageEvents.faqNoResult(), UsageEvents.faqNotAnswered(),
        )
        val code = Regex("[a-z0-9_+-]{1,40}|[A-Za-z][A-Za-z0-9_]{0,39}")
        for (e in events) for ((_, v) in e.params) {
            val text = v.text
            assertTrue(text == null || code.matches(text), "قيمة مش ثابتة: $text في ${e.name}")
            assertTrue(v.number == null || v.number!! in 0..UsageValue.MAX_COUNT.toLong())
        }
        assertEquals("other", events[6].params[UsageParam.SENDER]?.text)
        // أسماء Firebase: حروف صغيرة و_ بس، لحد 40
        val wire = Regex("[a-z][a-z0-9_]{0,39}")
        assertTrue(UsageEventName.entries.all { wire.matches(it.wire) } && UsageParam.entries.all { wire.matches(it.wire) })
        assertTrue(UsageProperty.entries.all { Regex("[a-z][a-z0-9_]{0,23}").matches(it.wire) })
    }
}
