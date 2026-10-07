package app.masroufy.memory

import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState

/** صندوق رسايل وهمي للاختبار — نقل `memorySmsInbox`: مفيش صلاحية جهاز ولا رسايل بنك حقيقية. */
class MemorySmsInbox(messages: List<QueuedSms> = emptyList(), override val available: Boolean = false) : SmsInboxPort {
    private var enabled = false
    private var senders = emptyList<String>()
    private var queue = messages.toList()

    private fun state() = SmsInboxState(
        enabled = enabled,
        permission = enabled,
        more = false,
        count = queue.size,
        senders = senders.toList(),
        messages = queue.toList(),
    )

    override suspend fun sync(): SmsInboxState = state()

    override suspend fun enable(senders: List<String>): SmsInboxState {
        enabled = true
        this.senders = senders.toList()
        return state()
    }

    override suspend fun disable(): SmsInboxState {
        enabled = false
        return state()
    }

    override suspend fun acknowledge(ids: List<String>): SmsInboxState {
        queue = queue.filter { it.id !in ids }
        return state()
    }

    private val targets = mutableMapOf<String, String>()

    override suspend fun autoTarget(spaceId: String): String? = targets[spaceId]

    override suspend fun setAutoTarget(spaceId: String, walletId: String?) {
        if (walletId == null) targets.remove(spaceId) else targets[spaceId] = walletId
    }

    /** للاختبار: رسالة وصلت (زي الاستقبال في الخلفية). */
    fun receive(message: QueuedSms) {
        if (queue.none { it.id == message.id }) queue = queue + message
    }
}
