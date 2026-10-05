package app.masroufy.memory

import app.masroufy.core.NotificationReceipt
import app.masroufy.port.NotificationReceiptRepository

/** إيصالات التنبيه في الذاكرة — نقل `memoryNotificationRepository.ts`. المفتاح هو `eventKey` نفسه. */
class MemoryNotificationReceiptRepository(seed: List<NotificationReceipt> = emptyList()) : NotificationReceiptRepository {
    private val items = LinkedHashMap<String, NotificationReceipt>()

    init {
        for (r in seed) items[r.eventKey] = r
    }

    override suspend fun listAll(): List<NotificationReceipt> = items.values.toList()

    override suspend fun saveMany(receipts: List<NotificationReceipt>) {
        for (r in receipts) items[r.eventKey] = r
    }

    override suspend fun deleteMany(eventKeys: List<String>) {
        for (key in eventKeys) items.remove(key)
    }
}
