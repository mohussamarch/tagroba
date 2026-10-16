package app.masroufy.wiring

import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.port.smsSenderKey

/** الصندوق نفسه برسايل مرسل واحد بس ([key] بعد `smsSenderKey`) — الشيل والتفعيل بيروحوا للصندوق الحقيقي. (اتنقل من `SmsGraph.kt` — r041c) */
internal class SenderInbox(private val inbox: SmsInboxPort, private val key: String) : SmsInboxPort {
    override val available: Boolean get() = inbox.available

    private fun only(state: SmsInboxState): SmsInboxState {
        val mine = state.messages.filter { smsSenderKey(it.sender) == key }
        return state.copy(messages = mine, count = mine.size)
    }

    override suspend fun sync() = only(inbox.sync())

    override suspend fun page(skip: Int) = inbox.page(skip)

    override suspend fun window(before: app.masroufy.port.QueuedSms?) = only(inbox.window(before))

    override suspend fun enable(senders: List<String>) = only(inbox.enable(senders))

    override suspend fun disable() = only(inbox.disable())

    override suspend fun acknowledge(ids: List<String>) = only(inbox.acknowledge(ids))

    override suspend fun senderWallets(spaceId: String) = inbox.senderWallets(spaceId)

    override suspend fun setSenderWallet(spaceId: String, sender: String, walletId: String?) = inbox.setSenderWallet(spaceId, sender, walletId)

    // §77-A (decisions-77): التعلّم وردّ «ده راتبك؟» بيروحوا للصندوق الحقيقي زي ما هم (مش بيتفلتروا بالمرسل).
    override suspend fun learnedShapes(spaceId: String) = inbox.learnedShapes(spaceId)

    override suspend fun learnShapes(spaceId: String, sender: String, keys: Set<String>) = inbox.learnShapes(spaceId, sender, keys)

    override suspend fun forgetShapes(spaceId: String, sender: String) = inbox.forgetShapes(spaceId, sender)

    override suspend fun salaryAnswer(spaceId: String, sender: String) = inbox.salaryAnswer(spaceId, sender)

    override suspend fun setSalaryAnswer(spaceId: String, sender: String, answer: Boolean?) = inbox.setSalaryAnswer(spaceId, sender, answer)

    // §83: مؤشر القراية للصندوق كله (مش للمرسل ده بس)
    override suspend fun oldestMessageAt(senders: List<String>) = inbox.oldestMessageAt(senders)

    override suspend fun readMarks() = inbox.readMarks()

    override suspend fun readFrom(point: String) = inbox.readFrom(point)
}
