package app.masroufy.memory

import app.masroufy.core.SupportMessage
import app.masroufy.port.PendingSupport
import app.masroufy.port.SupportOutboxPort

/** صندوق «ابعتلنا» في الذاكرة — للاختبار (CLAUDE.md #6) وللنسخ اللي مالهاش ملف. */
class MemorySupportOutbox : SupportOutboxPort {
    private val items = ArrayList<SupportMessage>()
    private val waiting = ArrayList<PendingSupport>()

    override fun add(message: SupportMessage, id: String) {
        items.add(message)
        waiting.add(PendingSupport(id, message))
    }

    override fun all(): List<SupportMessage> = items.toList()

    override fun pending(): List<PendingSupport> = waiting.toList()

    override fun sent(id: String) {
        waiting.removeAll { it.id == id }
    }
}
