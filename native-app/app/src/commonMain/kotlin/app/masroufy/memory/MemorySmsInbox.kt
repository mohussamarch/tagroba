package app.masroufy.memory

import app.masroufy.core.SmsParseResult
import app.masroufy.core.isoInstantMillis
import app.masroufy.port.BankSmsParser
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.port.SmsReadMarks
import app.masroufy.port.smsSenderKey

/**
 * صندوق رسايل وهمي للاختبار — نقل `memorySmsInbox`: مفيش صلاحية جهاز ولا رسايل بنك حقيقية.
 * §83: فيه كمان **صندوق رسايل الجوال نفسه** ([deliver] — زي `Telephony.Sms.Inbox`) ومؤشر قراية زي الجهاز (`SmsInboxStore`): أول تفعيل بيبدأ
 * من [now] · المزامنة بتسحب من الجوال كل رسالة من المرسلين المفعّلين من المؤشر ولسه ما اتقريتش ([perSync] في المرة — زي الـ500، والباقي
 * `more`) · [readFrom] بيرجّع المؤشر · الرسالة اللي اتقرت مرة (اتسجلت أو اتشالت) ما بتدخلش تاني · الصندوق بيعرض أحدث [window] رسالة.
 */
class MemorySmsInbox(
    messages: List<QueuedSms> = emptyList(),
    override val available: Boolean = false,
    private val now: String = "2026-10-07T12:00:00Z",
    private val window: Int = Int.MAX_VALUE,
    private val perSync: Int = Int.MAX_VALUE,
) : SmsInboxPort {
    private var enabled = false
    private var senders = emptyList<String>()
    private var queue = messages.toList()
    private val phone = mutableListOf<QueuedSms>()
    private val seen = messages.map { it.id }.toMutableSet()
    private var cursor: String? = null
    private var floor: String? = null
    private var more = false
    private var skip = 0

    private fun millis(iso: String) = isoInstantMillis(iso) ?: Long.MIN_VALUE

    private fun state(): SmsInboxState {
        // الجهاز بيعرض أحدث رسايل الصندوق (من الأقدم للأحدث) — من غير حد: الصندوق زي ما هو بترتيب دخوله
        val shown = if (skip == 0 && queue.size <= window) queue.toList()
        else queue.sortedWith(compareBy({ millis(it.receivedAt) }, { it.id })).dropLast(skip).takeLast(window)
        return SmsInboxState(
            enabled = enabled, permission = enabled, more = more, count = queue.size, senders = senders.toList(), messages = shown,
            oldestQueuedAt = queue.minByOrNull { millis(it.receivedAt) }?.receivedAt,
        )
    }

    override suspend fun sync(): SmsInboxState {
        more = enabled && pull()
        return state()
    }

    /** رسايل الجوال من المؤشر (زي `AndroidSmsInbox.catchUp`) — [perSync] في المرة. `true` = لسه فيه. */
    private fun pull(): Boolean {
        val from = cursor ?: return false
        val keys = senders.map(::smsSenderKey).toSet()
        val due = phone.filter { smsSenderKey(it.sender) in keys && millis(it.receivedAt) >= millis(from) && it.id !in seen }
            .sortedWith(compareBy({ millis(it.receivedAt) }, { it.id }))
        for (message in due.take(perSync)) {
            seen += message.id
            queue = queue + message
            if (millis(message.receivedAt) > millis(cursor!!)) cursor = message.receivedAt
        }
        return due.size > perSync
    }

    override suspend fun enable(senders: List<String>): SmsInboxState {
        enabled = true
        this.senders = senders.toList()
        // أول تفعيل بيبدأ من دلوقتي — والتفعيل بعد إيقاف بيكمّل من المؤشر (الفترة اللي كان واقف فيها بتتقري — §83)
        if (cursor == null) {
            cursor = now
            floor = now
        }
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

    override suspend fun page(skip: Int) {
        this.skip = maxOf(0, skip)
    }

    // r041 A10: كارت «اسمح بقراءة رسائل البنك» على الجهاز ده
    private var handoff: String? = null

    override suspend fun handoffCard(): String? = handoff

    override suspend fun setHandoffCard(value: String?) {
        handoff = value
    }

    override suspend fun oldestMessageAt(senders: List<String>): String? {
        val keys = senders.map(::smsSenderKey).toSet()
        return phone.filter { smsSenderKey(it.sender) in keys }.minByOrNull { millis(it.receivedAt) }?.receivedAt
    }

    override suspend fun readMarks(): SmsReadMarks? {
        val point = cursor ?: return null
        return if (enabled) SmsReadMarks(point, floor ?: point) else null
    }

    override suspend fun readFrom(point: String) {
        val at = cursor ?: return
        if (millis(point) < millis(at)) cursor = point
        if (millis(point) < millis(floor ?: at)) floor = point
    }

    /** للاختبار (§83): رسايل وصلت **الجوال** (مش الصندوق لسه) — المزامنة بتسحبها لو من مرسل مفعّل ومن المؤشر وبعده. */
    fun deliver(vararg messages: QueuedSms) {
        phone += messages
    }

    /**
     * للاختبار (§83): البرنامج اتمسح واتثبت تاني [at] — الصندوق والمؤشر والأشكال اللي اتعلّمت ومحفظة كل بنك بيضيعوا (كلهم على الجهاز بس)، وصندوق
     * رسايل الجوال زي ما هو.
     */
    fun reinstall(at: String = now): MemorySmsInbox = MemorySmsInbox(available = available, now = at, window = window, perSync = perSync).also { it.phone += phone }

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
        seen += message.id
        if (queue.none { it.id == message.id }) queue = queue + message
    }
}
