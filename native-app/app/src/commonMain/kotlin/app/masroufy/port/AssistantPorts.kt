package app.masroufy.port

import app.masroufy.core.AlertDismissal
import app.masroufy.core.AssistConversation
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistTopic
import app.masroufy.core.ForgottenMark
import app.masroufy.core.UnknownQuestion
import app.masroufy.core.UserSetting

/**
 * مخازن المساعد «مصروفي» (OVERRIDES §78) — **كلها على مستوى الحساب** (`users/{uid}/…`) وبتتزامن وبتدخل النسخة الشاملة (قرار المالك:
 * «الذاكرة والأسئلة في حساب المالك وتتزامن»، والرد ٤ على فرع التصميم: السجل كمان). `listAll` مسموح: الأعداد صغيرة بطبيعتها.
 * نسخ الذاكرة (`MemoryAssistant.kt`) للاختبار، وفايربيز (`FirestoreAssistant.kt`) للتشغيل.
 */
interface AssistantConversationStore {
    suspend fun listAll(): List<AssistConversation>

    suspend fun save(conversation: AssistConversation)

    suspend fun remove(ids: List<String>)
}

/** كل رسالة مستند لوحدها بمعرّف محادثتها (جوالين في نفس الوقت ما بيكتبوش فوق بعض). */
interface AssistantMessageStore {
    suspend fun listByConversation(conversationId: String): List<AssistMessage>

    suspend fun listAll(): List<AssistMessage>

    suspend fun save(message: AssistMessage)

    suspend fun removeByConversations(conversationIds: List<String>)
}

interface AssistantTopicStore {
    suspend fun listAll(): List<AssistTopic>

    suspend fun save(topic: AssistTopic)

    suspend fun remove(keys: List<String>)
}

/** علامات «امسح دي» (`fact:…` · `topic:…` · `card:…`). */
interface AssistantForgottenStore {
    suspend fun listAll(): List<ForgottenMark>

    suspend fun save(mark: ForgottenMark)

    suspend fun remove(ids: List<String>)
}

interface AssistantUnknownStore {
    suspend fun listAll(): List<UnknownQuestion>

    suspend fun save(question: UnknownQuestion)

    suspend fun remove(ids: List<String>)
}

/** إعدادات المستخدم الجديدة (`userSettings/{key}`): المحفظة الأساسية لكل بلد · مفتاح التعلم. مش في `profile/main` (شوف `AssistStorage.kt`). */
interface UserSettingsStore {
    suspend fun listAll(): List<UserSetting>

    suspend fun save(setting: UserSetting)

    suspend fun remove(keys: List<String>)
}

/** إشعارات الجرس الممسوحة بـ«×» (رد المالك ٣). */
interface AlertDismissalStore {
    suspend fun listAll(): List<AlertDismissal>

    suspend fun save(dismissal: AlertDismissal)

    suspend fun remove(threadKeys: List<String>)
}
