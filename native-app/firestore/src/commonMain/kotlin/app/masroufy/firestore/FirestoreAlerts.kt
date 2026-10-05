package app.masroufy.firestore

import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertGroupSetting
import app.masroufy.core.NotificationReceipt
import app.masroufy.data.AlertCodecs
import app.masroufy.data.receiptDocId
import app.masroufy.data.skippingUnreadable
import app.masroufy.port.AlertInboxEntry
import app.masroufy.port.AlertInboxStore
import app.masroufy.port.AlertReceiptStore
import app.masroufy.port.AlertSettingsStore

/**
 * محرك التنبيهات على فايربيز (OVERRIDES §61 — رد المالك (١)، جلسة 18) — **على مستوى الحساب** (`users/{uid}`) وبتتزامن بين الأجهزة:
 * قفل مجموعة على جوال بيقفلها على التاني، وسطر الصفحة واحد على الجهازين، والتنبيه اللي اتبعت على جوال ما يتبعتش تاني على التاني.
 * **التعلّم (ساعاتك وتفاعلك) مش هنا** — على الجوال بس (`device`). المستند اللي ما يتقريش (نسخة أحدث من التطبيق كتبت نوع جديد) بيتخطّى.
 */
class FirestoreAlertSettings(private val space: FirestoreSpace) : AlertSettingsStore {
    private val codec = AlertCodecs.alertSettings
    private val readable = codec.skippingUnreadable()

    override suspend fun disabledGroups(): Set<AlertGroup> = space.select(readable).filterNotNull().filter { !it.enabled }.map { it.group }.toSet()

    override suspend fun setGroupEnabled(group: AlertGroup, enabled: Boolean) = space.saveAll(codec, listOf(AlertGroupSetting(group, enabled)))
}

class FirestoreAlertReceipts(private val space: FirestoreSpace) : AlertReceiptStore {
    private val codec = AlertCodecs.alertReceipts

    override suspend fun listAll(): List<NotificationReceipt> = space.select(codec.skippingUnreadable()).filterNotNull()

    override suspend fun saveMany(receipts: List<NotificationReceipt>) = space.saveAll(codec, receipts)
}

class FirestoreAlertInbox(private val space: FirestoreSpace) : AlertInboxStore {
    private val codec = AlertCodecs.alertInbox

    override suspend fun listAll(): List<AlertInboxEntry> = space.select(codec.skippingUnreadable()).filterNotNull()

    override suspend fun save(entry: AlertInboxEntry) = space.saveAll(codec, listOf(entry))

    override suspend fun remove(threadKeys: List<String>) = space.deleteAll(codec.group, threadKeys.map(::receiptDocId))
}
