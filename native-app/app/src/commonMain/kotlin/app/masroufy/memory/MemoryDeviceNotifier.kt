package app.masroufy.memory

import app.masroufy.core.LocalMoment
import app.masroufy.core.SystemNotice
import app.masroufy.port.DeviceNotifier
import app.masroufy.port.NoticeOutcome

/** إشعارات الجوال في الذاكرة للاختبار (OVERRIDES §72): بيحفظ اللي «اتبعت» بس. [allowed] = المستخدم سمح بالإشعارات. */
class MemoryDeviceNotifier(var allowed: Boolean = true, override val available: Boolean = true) : DeviceNotifier {
    data class Posted(val tag: String, val notice: SystemNotice, val at: LocalMoment?)

    val posted = mutableListOf<Posted>()

    override suspend fun permitted(): Boolean = allowed

    override suspend fun post(tag: String, notice: SystemNotice, at: LocalMoment?): NoticeOutcome {
        if (!available) return NoticeOutcome.UNAVAILABLE
        if (!allowed) return NoticeOutcome.BLOCKED
        posted.removeAll { it.tag == tag }
        posted += Posted(tag, notice, at)
        return if (at == null) NoticeOutcome.SHOWN else NoticeOutcome.SCHEDULED
    }
}
