package app.masroufy.memory

import app.masroufy.core.SupportMessage
import app.masroufy.port.SupportOutboxPort

/** صندوق «ابعتلنا» في الذاكرة — للاختبار (CLAUDE.md #6) وللنسخ اللي مالهاش ملف. */
class MemorySupportOutbox : SupportOutboxPort {
    private val items = ArrayList<SupportMessage>()

    override fun add(message: SupportMessage) {
        items.add(message)
    }

    override fun all(): List<SupportMessage> = items.toList()
}
