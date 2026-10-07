package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** تنبيه «رسايل بنك مستنية تأكيدك» (OVERRIDES §72 · §61). */
class SmsConfirmAlertTest {
    private val noon = LocalMoment("2026-10-07", 12)

    @Test fun nothingWaitingMeansNoCandidate() {
        assertNull(smsConfirmCandidate(emptyList()))
    }

    @Test fun aNewWaitingMessageIsANewTopicAndTheCountIsInsideTheAppOnly() {
        val one = smsConfirmCandidate(listOf("m1"))!!
        val two = smsConfirmCandidate(listOf("m1", "m2"))!!
        assertEquals(AlertKind.SMS_CONFIRM, one.kind)
        assertNotEquals(one.threadKey, two.threadKey, "رسالة جديدة بتستنى ⇒ موضوع جديد ⇒ إشعار جديد مرة واحدة")
        assertEquals(two.threadKey, smsConfirmCandidate(listOf("m0", "m2"))!!.threadKey, "الموضوع = أحدث رسالة مستنية")
        assertFalse("m2" in two.threadKey, "معرّف الرسالة ببصمته")
        forEachTextVariant { lang ->
            val c = smsConfirmCandidate(listOf("a", "b", "c"))!!
            assertTrue(c.title.contains("3"), "[$lang] العدد في الصفحة جوه التطبيق: ${c.title}")
            val notice = systemNoticeFor(AlertKind.SMS_CONFIRM)
            assertTrue(isLockSafe(notice.title) && isLockSafe(notice.body), "[$lang] شاشة القفل من غير أرقام: $notice")
            assertFalse(notice.body.contains("3"))
        }
    }

    @Test fun itNeedsADecisionAndTheEngineDecidesTheTiming() {
        val c = smsConfirmCandidate(listOf("m1"))!!
        val fresh = decideAlert(c, KindStats(), UsualHours(), noon)
        assertEquals(AlertDelivery.SEND_NOW, fresh.delivery, "لسه بنتعلم ساعاتك ⇒ دلوقتي")
        assertTrue(AlertFactor.NEEDS_DECISION in fresh.factors)
        // بعد ما يتعلم إنك بتتجاهله: المحرك بيجمعه في ملخص (لسه إشعار، بس مجمّع) — نفس قاعدة سؤال التحويلات (§61 (٥))
        assertEquals(AlertDelivery.DIGEST, decideAlert(c, KindStats(shown = 10, opened = 0), UsualHours(), noon).delivery)
        assertEquals(AlertGroup.QUESTIONS, AlertKind.SMS_CONFIRM.group)
    }
}
