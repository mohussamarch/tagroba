package app.masroufy.memory

import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.KindStats
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.UsualHours
import app.masroufy.port.AlertInboxEntry
import app.masroufy.port.AlertInboxStore
import app.masroufy.port.AlertInteractionStore
import app.masroufy.port.AlertReceiptStore
import app.masroufy.port.AlertSettingsStore
import app.masroufy.port.UsualHoursStore

/** مخازن محرك التنبيهات في الذاكرة (§61) — للاختبار، ولحد ما تخزين الجوال يتبني. */
class MemoryAlertSettings(disabled: Set<AlertGroup> = emptySet()) : AlertSettingsStore {
    private val off = disabled.toMutableSet()

    override suspend fun disabledGroups(): Set<AlertGroup> = off.toSet()

    override suspend fun setGroupEnabled(group: AlertGroup, enabled: Boolean) {
        if (enabled) off -= group else off += group
    }
}

class MemoryAlertInteractions(seed: Map<AlertKind, KindStats> = emptyMap()) : AlertInteractionStore {
    private val items = LinkedHashMap(seed)

    override suspend fun load(): Map<AlertKind, KindStats> = items.toMap()

    override suspend fun save(kind: AlertKind, stats: KindStats) {
        items[kind] = stats
    }
}

class MemoryUsualHours(private var hours: UsualHours = UsualHours()) : UsualHoursStore {
    override suspend fun load(): UsualHours = hours

    override suspend fun save(hours: UsualHours) {
        this.hours = hours
    }
}

class MemoryAlertReceipts : AlertReceiptStore {
    private val items = LinkedHashMap<String, NotificationReceipt>()

    override suspend fun listAll(): List<NotificationReceipt> = items.values.toList()

    override suspend fun saveMany(receipts: List<NotificationReceipt>) {
        for (r in receipts) items[r.eventKey] = r
    }
}

class MemoryAlertInbox : AlertInboxStore {
    private val items = LinkedHashMap<String, AlertInboxEntry>()

    override suspend fun listAll(): List<AlertInboxEntry> = items.values.toList()

    override suspend fun save(entry: AlertInboxEntry) {
        items[entry.threadKey] = entry
    }

    override suspend fun remove(threadKeys: List<String>) {
        for (k in threadKeys) items.remove(k)
    }
}
