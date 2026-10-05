package app.masroufy.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.masroufy.core.AlertKind
import app.masroufy.core.KindStats
import app.masroufy.core.UsualHours
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** تعلّم محرك التنبيهات على أندرويد (OVERRIDES §61 — على الجوال بس، جلسة 18) — على المحاكي، بيانات وهمية. */
@RunWith(AndroidJUnit4::class)
class AlertLearningOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun engagementIsKeptPerAccountOnThePhone() = runBlocking<Unit> {
        val uid = "kt-a-" + System.nanoTime()
        val a = AndroidAlertInteractionStore(context, uid)
        val b = AndroidAlertInteractionStore(context, "kt-b-" + System.nanoTime())
        assertEquals(emptyMap(), a.load(), "حساب جديد على الجوال: لسه بنتعلم")
        a.save(AlertKind.DUE_SOON, KindStats(6, 4))
        a.save(AlertKind.ZAKAT_TODAY, KindStats(1, 0))
        assertEquals(mapOf(AlertKind.DUE_SOON to KindStats(6, 4), AlertKind.ZAKAT_TODAY to KindStats(1, 0)), AndroidAlertInteractionStore(context, uid).load())
        assertEquals(emptyMap(), b.load(), "كل حساب ليه تعلّمه")
        context.getSharedPreferences("alert-learning", android.content.Context.MODE_PRIVATE).edit().putString(AlertLearningFormat.statsKey(uid), "كلام بايظ").commit()
        assertEquals(emptyMap(), a.load(), "قيمة بايظة = من الأول، مش وقع")
    }

    @Test fun usualHoursAreKeptPerAccountOnThePhone() = runBlocking<Unit> {
        val uid = "kt-a-" + System.nanoTime()
        val a = AndroidUsualHoursStore(context, uid)
        assertEquals(UsualHours(), a.load())
        val hours = UsualHours().recordOpen(9).recordOpen(9).recordOpen(21)
        a.save(hours)
        assertEquals(hours, AndroidUsualHoursStore(context, uid).load())
        assertEquals(UsualHours(), AndroidUsualHoursStore(context, "kt-b-" + System.nanoTime()).load())
    }
}
