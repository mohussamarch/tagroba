package app.masroufy.memory

import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.port.smsSenderKey

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

    private val wallets = mutableMapOf<String, MutableMap<String, String>>()

    override suspend fun senderWallets(spaceId: String): Map<String, String> = wallets[spaceId].orEmpty().toMap()

    override suspend fun setSenderWallet(spaceId: String, sender: String, walletId: String?) {
        val map = wallets.getOrPut(spaceId) { mutableMapOf() }
        if (walletId == null) map.remove(smsSenderKey(sender)) else map[smsSenderKey(sender)] = walletId
    }

    /** للاختبار: رسالة وصلت (زي الاستقبال في الخلفية). */
    fun receive(message: QueuedSms) {
        if (queue.none { it.id == message.id }) queue = queue + message
    }
}
