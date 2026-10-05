package app.masroufy.device

import app.masroufy.core.AlertKind
import app.masroufy.core.KindStats
import app.masroufy.core.UsualHours
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals

/** تعلّم محرك التنبيهات على **محاكي الآيفون** (OVERRIDES §61 — على الجوال بس، جلسة 18). نفس اختبار أندرويد، ببيانات وهمية. */
class IosAlertLearningTest {
    @Test fun engagementIsKeptPerAccountOnThePhone() = runBlocking<Unit> {
        val uid = "kt-a-${kotlin.random.Random.nextLong()}"
        val a = IosAlertInteractionStore(uid)
        val b = IosAlertInteractionStore("kt-b-${kotlin.random.Random.nextLong()}")
        assertEquals(emptyMap(), a.load(), "حساب جديد على الجوال: لسه بنتعلم")
        a.save(AlertKind.DUE_SOON, KindStats(6, 4))
        a.save(AlertKind.ZAKAT_TODAY, KindStats(1, 0))
        assertEquals(mapOf(AlertKind.DUE_SOON to KindStats(6, 4), AlertKind.ZAKAT_TODAY to KindStats(1, 0)), IosAlertInteractionStore(uid).load(), "بيفضل بعد ما التطبيق يتقفل")
        assertEquals(emptyMap(), b.load(), "كل حساب ليه تعلّمه")
        NSUserDefaults.standardUserDefaults.setObject("كلام بايظ", AlertLearningFormat.statsKey(uid))
        assertEquals(emptyMap(), a.load(), "قيمة بايظة = من الأول، مش وقع")
    }

    @Test fun usualHoursAreKeptPerAccountOnThePhone() = runBlocking<Unit> {
        val uid = "kt-a-${kotlin.random.Random.nextLong()}"
        val a = IosUsualHoursStore(uid)
        assertEquals(UsualHours(), a.load())
        val hours = UsualHours().recordOpen(9).recordOpen(9).recordOpen(21)
        a.save(hours)
        assertEquals(hours, IosUsualHoursStore(uid).load())
        assertEquals(UsualHours(), IosUsualHoursStore("kt-b-${kotlin.random.Random.nextLong()}").load())
    }
}
