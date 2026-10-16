package app.masroufy.usecase

import app.masroufy.core.SmsParseResult
import app.masroufy.core.TextKey
import app.masroufy.core.jsTrim
import app.masroufy.core.uiText
import app.masroufy.port.BankSmsParser
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState

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
) {
    val available: Boolean get() = port.available

    /** [ids] (r041c) = الرسايل دي بس اللي بتتفهم وتبقى في [InboxView.items] — بنفس رقم سطرها في الصفحة؛ null = كلها. */
    private fun prepare(state: SmsInboxState, ids: Set<String>? = null) = InboxView(
        enabled = state.enabled,
        permission = state.permission,
        more = state.more,
        count = state.count,
        senders = state.senders,
        messages = state.messages,
        items = state.messages.mapIndexedNotNull { index, message ->
            if (ids != null && message.id !in ids) return@mapIndexedNotNull null
            InboxItem(message.id, message.sender, message.receivedAt, parse(message.message(), index + 1))
        },
    )

    suspend fun refresh(): InboxView = prepare(port.sync())

    /** r041c: نفس [refresh] بس بيفهم الرسايل [ids] بس (null = كلها) — التسجيل لوحده بيقرا الجديد من غير ما يعيد فهم الصفحة كلها. */
    suspend fun refresh(ids: Set<String>?): InboxView = prepare(port.sync(), ids)

    suspend fun enable(senders: List<String>): InboxView {
        val clean = senders.map(::jsTrim).filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty() || clean.size > 10 || clean.any { it.length > 50 }) {
            throw IllegalArgumentException(uiText(TextKey.SMS_SENDER_REQUIRED_MANY))
        }
        port.enable(clean)
        return prepare(port.sync())
    }

    suspend fun disable(): InboxView = prepare(port.disable())

    suspend fun dismiss(ids: List<String>): InboxView = prepare(port.acknowledge(ids))

    /** المؤكد بس اللي بيتشال — الملغي وغير المختار وغير المدعوم بيفضل. */
    suspend fun imported(items: List<InboxLine>, confirmed: List<Int>) {
        val lines = confirmed.toSet()
        val ids = items.filter { it.lineNumber in lines }.map { it.id }
        if (ids.isNotEmpty()) port.acknowledge(ids)
    }
}
