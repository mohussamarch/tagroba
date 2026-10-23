package app.masroufy.usecase

import app.masroufy.core.SmsParseResult
import app.masroufy.core.TextKey
import app.masroufy.core.isoInstantMillis
import app.masroufy.core.jsTrim
import app.masroufy.core.uiText
import app.masroufy.port.BankSmsParser
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.port.smsSenderKey

/** ManageSmsInbox — نقل `manageSmsInbox.ts`: تفعيل الصندوق وقفله وتحديثه، وشيل اللي خلص. */

data class InboxItem(val id: String, val sender: String, val receivedAt: String, val parsed: SmsParseResult)

data class InboxView(
    val enabled: Boolean,
    val permission: Boolean,
    val more: Boolean,
    val count: Int,
    val senders: List<String>,
    val messages: List<QueuedSms>,
    val items: List<InboxItem>,
)

data class InboxLine(val id: String, val lineNumber: Int)

class ManageSmsInbox(
    /** الصندوق نفسه — `SmsLane` بيبني منه تعلّم البلد (§77-A) عشان الشاشة والخلفية يقروا نفس التخزين. */
    internal val port: SmsInboxPort,
    private val parse: BankSmsParser,
    /**
     * OVERRIDES §112-5: قرّاية البلاد التانية. رسالة القارئ ده رفضها وقارئ بلد تانية فاهمها = رسالة البلد دي ⇒ ما بتظهرش هنا (بتتسجل
     * هناك — التسجيل لوحده بيلف على كل البلاد). فاضية = البلد دي بس (زي الأول).
     */
    private val elsewhere: List<BankSmsParser> = emptyList(),
) {
    val available: Boolean get() = port.available

    /**
     * fix053 (بلاغ المالك — تعديل OVERRIDES §112-5): بلد كل مرسل بيتقرر **مرة** ويتفتكر — مرسل رسايله المفهومة على شكل بلد تانية (ومفيش
     * ولا رسالة منه بيفهمها قارئ البلد دي) = مرسل البلد التانية ⇒ ولا رسالة منه تظهر هنا (حتى «ما اتفهمتش» زي رسايل الرمز) ولا تتعد في
     * «مستنية» ولا كارت «فاتتك» ولا العدّاد. مش موجود = لسه ما اتقررش.
     */
    private val senderHere = HashMap<String, Boolean>()

    private fun otherCountry(message: QueuedSms, parsed: SmsParseResult, line: Int): Boolean =
        parsed is SmsParseResult.Rejected && (parsed.otherCountry || elsewhere.any { it(message.message(), line) is SmsParseResult.Ok })

    /**
     * [ids] (r041c) = الرسايل دي بس اللي بتتفهم وتبقى في [InboxView.items] — بنفس رقم سطرها في الصفحة؛ null = كلها.
     * [behind] = رسايل البلد دي اللي ورا الصفحة (null = مش محسوب ⇒ كل اللي ورا الصفحة).
     */
    private fun prepare(state: SmsInboxState, ids: Set<String>? = null, behind: Int? = null): InboxView {
        if (elsewhere.isEmpty()) {
            return InboxView(state.enabled, state.permission, state.more, state.count, state.senders, state.messages, itemsOf(state, ids, emptySet()))
        }
        // OVERRIDES §112-5 (قرار المالك 2026-10-23): رسالة على شكل البلد التانية بتتقري وتتسجل هناك (التسجيل لوحده بيلف على كل البلاد) —
        // هنا ما بتظهرش «ما اتفهمتش» ولا بتتعد. القديم كمان: الصندوق بيتفهم من الأول كل مرة، فمفيش صفوف متخزنة تتنضف
        val parsedAll = HashMap<String, SmsParseResult>()
        val hidden = HashSet<String>()
        val okHere = HashSet<String>()
        val foreignSeen = HashSet<String>()
        state.messages.forEachIndexed { index, message ->
            val key = smsSenderKey(message.sender)
            if (senderHere[key] == false) return@forEachIndexed
            if (ids != null && message.id !in ids && senderHere[key] == true) return@forEachIndexed
            val parsed = parse(message.message(), index + 1)
            parsedAll[message.id] = parsed
            when {
                parsed is SmsParseResult.Ok -> okHere += key
                otherCountry(message, parsed, index + 1) -> {
                    hidden += message.id
                    foreignSeen += key
                }
            }
        }
        for (key in foreignSeen) if (key !in okHere && senderHere[key] != true) senderHere[key] = false
        for (key in okHere) senderHere[key] = true
        state.messages.forEach { if (senderHere[smsSenderKey(it.sender)] == false) hidden += it.id }
        val kept = state.messages.filter { it.id !in hidden }
        val count = kept.size + (behind ?: (state.count - state.messages.size).coerceAtLeast(0))
        return InboxView(state.enabled, state.permission, state.more, count, state.senders, kept, itemsOf(state, ids, hidden, parsedAll))
    }

    private fun itemsOf(state: SmsInboxState, ids: Set<String>?, hidden: Set<String>, parsed: Map<String, SmsParseResult> = emptyMap()) =
        state.messages.mapIndexedNotNull { index, message ->
            if (message.id in hidden || (ids != null && message.id !in ids)) return@mapIndexedNotNull null
            InboxItem(message.id, message.sender, message.receivedAt, parsed[message.id] ?: parse(message.message(), index + 1))
        }

    /**
     * fix053: كام رسالة **من البلد دي** ورا الصفحة [page] — الصندوق صفحة صفحة (`window`)، وبلد المرسل اللي مش في الصفحة بيتقرر مرة (أول رسالة
     * منه بتبيّن بلدها). الصندوق كله في الصفحة أو مفيش بلاد تانية ⇒ null (العدّ زي ما هو).
     */
    private suspend fun behindPage(page: SmsInboxState): Int? {
        if (elsewhere.isEmpty() || page.count <= page.messages.size) return null
        val onPage = page.messages.mapTo(HashSet()) { it.id }
        val seen = HashSet<String>()
        var n = 0
        var before: QueuedSms? = null
        for (pass in 0 until MAX_COUNT_PAGES) {
            val box = port.window(before)
            val fresh = box.messages.filter { seen.add(it.id) }
            if (fresh.isEmpty()) break
            for (m in fresh) {
                if (m.id in onPage) continue
                val key = smsSenderKey(m.sender)
                if (key !in senderHere) {
                    val parsed = parse(m.message(), 1)
                    if (parsed is SmsParseResult.Ok) senderHere[key] = true else if (otherCountry(m, parsed, 1)) senderHere[key] = false
                }
                if (senderHere[key] != false) n++
            }
            before = box.messages.minWithOrNull(compareBy({ isoInstantMillis(it.receivedAt) ?: Long.MIN_VALUE }, { it.id }))
        }
        return n
    }

    /** الصفحة بعد فصل البلد التانية + [InboxView.count] = رسايل البلد دي بس في الصندوق كله (مش عدّ الصندوق كله). */
    private suspend fun scoped(state: SmsInboxState): InboxView {
        val page = prepare(state)
        val behind = behindPage(state) ?: return page
        return page.copy(count = page.messages.size + behind)
    }

    suspend fun refresh(): InboxView = scoped(port.sync())

    /** r041c: نفس [refresh] بس بيفهم الرسايل [ids] بس (null = كلها) — التسجيل لوحده بيقرا الجديد من غير ما يعيد فهم الصفحة كلها. */
    suspend fun refresh(ids: Set<String>?): InboxView = prepare(port.sync(), ids)

    /**
     * r051: صفحة اتقرت خلاص (`SmsInboxPort.window`) بقارئ البلد دي — من غير قراية تانية للصندوق. التشغيلة بتقرا الصفحة مرة وبتفهمها مرة لكل
     * بلد، وكل محفظة بتتعاين منها (كانت كل محفظة بتقرا الصندوق وتفهم الصفحة من الأول).
     */
    internal fun view(state: SmsInboxState, ids: Set<String>?): InboxView = prepare(state, ids)

    /** fix053: المرسل ده اتعرف إنه من بلد تانية (رسايله على شكلها) — «اقرأ اللي فات؟» ما يسألش عنه هنا. */
    fun isOtherCountrySender(sender: String): Boolean = senderHere[smsSenderKey(sender)] == false

    suspend fun enable(senders: List<String>): InboxView {
        val clean = senders.map(::jsTrim).filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty() || clean.size > app.masroufy.core.MAX_SMS_SENDERS || clean.any { it.length > 50 }) {
            throw IllegalArgumentException(uiText(TextKey.SMS_SENDER_REQUIRED_MANY))
        }
        port.enable(clean)
        return scoped(port.sync())
    }

    suspend fun disable(): InboxView = scoped(port.disable())

    suspend fun dismiss(ids: List<String>): InboxView = scoped(port.acknowledge(ids))

    /** المؤكد بس اللي بيتشال — الملغي وغير المختار وغير المدعوم بيفضل. */
    suspend fun imported(items: List<InboxLine>, confirmed: List<Int>) {
        val lines = confirmed.toSet()
        val ids = items.filter { it.lineNumber in lines }.map { it.id }
        if (ids.isNotEmpty()) port.acknowledge(ids)
    }

    private companion object {
        /** حد أمان للف على الصندوق في العدّ (200 في الصفحة ⇒ 200 ألف رسالة). */
        const val MAX_COUNT_PAGES = 1_000
    }
}
