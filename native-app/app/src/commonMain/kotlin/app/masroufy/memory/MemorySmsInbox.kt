package app.masroufy.memory

import app.masroufy.core.SmsParseResult
import app.masroufy.port.BankSmsParser
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

    // ── §77-A وضع التعلّم · §75-2 «ده راتبك؟» (على الجهاز بس — هنا في الذاكرة) ──

    private val learned = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()
    private val salary = mutableMapOf<String, MutableMap<String, Boolean>>()

    override suspend fun learnedShapes(spaceId: String): Map<String, Set<String>> =
        learned[spaceId].orEmpty().mapValues { it.value.toSet() }.filterValues { it.isNotEmpty() }

    override suspend fun learnShapes(spaceId: String, sender: String, keys: Set<String>) {
        if (keys.isEmpty()) return
        learned.getOrPut(spaceId) { mutableMapOf() }.getOrPut(smsSenderKey(sender)) { mutableSetOf() } += keys
    }

    override suspend fun forgetShapes(spaceId: String, sender: String) {
        learned[spaceId]?.remove(smsSenderKey(sender))
    }

    override suspend fun salaryAnswer(spaceId: String, sender: String): Boolean? = salary[spaceId]?.get(smsSenderKey(sender))

    override suspend fun setSalaryAnswer(spaceId: String, sender: String, answer: Boolean?) {
        val map = salary.getOrPut(spaceId) { mutableMapOf() }
        if (answer == null) map.remove(smsSenderKey(sender)) else map[smsSenderKey(sender)] = answer
    }

    /**
     * للاختبار (S1 — §77-A): المالك «أكّد قبل كده» رسالة بنفس شكل كل رسالة من [messages] (من مرسلها في [spaceId]) ⇒ الرسايل الجاية بنفس
     * الشكل بتتسجل لوحدها. الرسالة اللي شكلها مش واضح (مالهاش بصمة) ما بتعلّمش حاجة. بيرجّع عدد البصمات اللي اتعلّمت.
     */
    fun preLearn(spaceId: String, parse: BankSmsParser, vararg messages: QueuedSms): Int {
        var count = 0
        for (message in messages) {
            val key = (parse(message.message(), 1) as? SmsParseResult.Ok)?.row?.learnKey ?: continue
            learned.getOrPut(spaceId) { mutableMapOf() }.getOrPut(smsSenderKey(message.sender)) { mutableSetOf() } += key
            count++
        }
        return count
    }

    /** للاختبار: رسالة وصلت (زي الاستقبال في الخلفية). */
    fun receive(message: QueuedSms) {
        if (queue.none { it.id == message.id }) queue = queue + message
    }
}
