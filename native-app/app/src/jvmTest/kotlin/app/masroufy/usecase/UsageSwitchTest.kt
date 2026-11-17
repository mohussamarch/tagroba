package app.masroufy.usecase

import app.masroufy.core.UsageEvent
import app.masroufy.core.UsageProperty
import app.masroufy.core.UsageValue
import app.masroufy.core.UsageEvents
import app.masroufy.port.AnalyticsPort
import app.masroufy.port.UsageSwitchPort
import app.masroufy.problems.Usage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OVERRIDES §121-17: مفتاح «بيانات الاستخدام» لوحده — مقفول ⇒ ولا حدث بيتبعت؛ ومش متاح ⇒ المفتاح ما بيتحركش. */
class UsageSwitchTest {
    private class Switch(override val available: Boolean, var state: Boolean = true) : UsageSwitchPort {
        override fun isOn() = state
        override fun setOn(on: Boolean) { state = on }
    }

    private class Recorder : AnalyticsPort {
        val events = ArrayList<UsageEvent>()
        override fun log(event: UsageEvent) { events += event }
        override fun set(property: UsageProperty, value: UsageValue) {}
    }

    @AfterTest fun reset() {
        Usage.port = null
        Usage.allowed = { true }
    }

    @Test fun switchOffStopsCollectionAndOnResumesIt() {
        val sw = Switch(available = true)
        val rec = Recorder()
        Usage.port = rec
        Usage.allowed = sw::isOn
        Usage.log(UsageEvents.backgroundGaveUp())
        assertEquals(1, rec.events.size)
        ManageUsageAnalytics(sw).setOn(false)
        assertFalse(Usage.active)
        Usage.log(UsageEvents.backgroundGaveUp())
        assertEquals(1, rec.events.size, "مقفول ⇒ ولا حدث")
        ManageUsageAnalytics(sw).setOn(true)
        Usage.log(UsageEvents.backgroundGaveUp())
        assertEquals(2, rec.events.size)
    }

    @Test fun defaultIsOnLikeCrashReports() {
        Usage.port = Recorder()
        assertTrue(Usage.active)
    }

    @Test fun unavailableBuildReadsOffAndIgnoresTheSwitch() {
        val sw = Switch(available = false, state = true)
        val use = ManageUsageAnalytics(sw)
        assertFalse(use.isOn())
        use.setOn(false)
        assertTrue(sw.state, "النسخة اللي مالهاش بيانات استخدام ما بتكتبش المفتاح")
    }

    @Test fun noPortMeansInactiveWhateverTheSwitch() {
        Usage.allowed = { true }
        assertFalse(Usage.active)
    }
}
