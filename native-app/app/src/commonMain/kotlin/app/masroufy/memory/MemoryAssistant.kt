package app.masroufy.memory

import app.masroufy.core.AlertDismissal
import app.masroufy.core.AssistConversation
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistTopic
import app.masroufy.core.ForgottenMark
import app.masroufy.core.UnknownQuestion
import app.masroufy.core.UserSetting
import app.masroufy.port.AlertDismissalStore
import app.masroufy.port.AssistantConversationStore
import app.masroufy.port.AssistantForgottenStore
import app.masroufy.port.AssistantMessageStore
import app.masroufy.port.AssistantTopicStore
import app.masroufy.port.AssistantUnknownStore
import app.masroufy.port.UserSettingsStore

/** خريطة بمفتاح — أساس كل مخازن المساعد في الذاكرة (للاختبار — CLAUDE.md #6). */
open class MemoryKeyed<T>(seed: List<T>, private val keyOf: (T) -> String) {
    protected val items = LinkedHashMap<String, T>().apply { seed.forEach { put(keyOf(it), it) } }

    suspend fun listAll(): List<T> = items.values.toList()

    protected fun put(item: T) {
        items[keyOf(item)] = item
    }

    protected fun drop(keys: List<String>) = keys.forEach { items.remove(it) }
}

class MemoryConversationStore(seed: List<AssistConversation> = emptyList()) :
    MemoryKeyed<AssistConversation>(seed, { it.id }), AssistantConversationStore {
    override suspend fun save(conversation: AssistConversation) = put(conversation)

    override suspend fun remove(ids: List<String>) = drop(ids)
}

class MemoryMessageStore(seed: List<AssistMessage> = emptyList()) : MemoryKeyed<AssistMessage>(seed, { it.id }), AssistantMessageStore {
    override suspend fun listByConversation(conversationId: String): List<AssistMessage> = items.values.filter { it.conversationId == conversationId }

    override suspend fun save(message: AssistMessage) = put(message)

    override suspend fun removeByConversations(conversationIds: List<String>) =
        drop(items.values.filter { it.conversationId in conversationIds }.map { it.id })
}

class MemoryTopicStore(seed: List<AssistTopic> = emptyList()) : MemoryKeyed<AssistTopic>(seed, { it.key }), AssistantTopicStore {
    override suspend fun save(topic: AssistTopic) = put(topic)

    override suspend fun remove(keys: List<String>) = drop(keys)
}

class MemoryForgottenStore(seed: List<ForgottenMark> = emptyList()) : MemoryKeyed<ForgottenMark>(seed, { it.id }), AssistantForgottenStore {
    override suspend fun save(mark: ForgottenMark) = put(mark)

    override suspend fun remove(ids: List<String>) = drop(ids)
}

class MemoryUnknownStore(seed: List<UnknownQuestion> = emptyList()) : MemoryKeyed<UnknownQuestion>(seed, { it.id }), AssistantUnknownStore {
    override suspend fun save(question: UnknownQuestion) = put(question)

    override suspend fun remove(ids: List<String>) = drop(ids)
}

class MemoryUserSettingsStore(seed: List<UserSetting> = emptyList()) : MemoryKeyed<UserSetting>(seed, { it.key }), UserSettingsStore {
    override suspend fun save(setting: UserSetting) = put(setting)

    override suspend fun remove(keys: List<String>) = drop(keys)
}

class MemoryAlertDismissalStore(seed: List<AlertDismissal> = emptyList()) :
    MemoryKeyed<AlertDismissal>(seed, { it.threadKey }), AlertDismissalStore {
    override suspend fun save(dismissal: AlertDismissal) = put(dismissal)

    override suspend fun remove(threadKeys: List<String>) = drop(threadKeys)
}

/** كل مخازن المساعد في الذاكرة مرة واحدة (التجميع في الاختبار — `memorySpaceRepositories`). */
fun memoryAssistantStores(): app.masroufy.usecase.AssistantStores = app.masroufy.usecase.AssistantStores(
    MemoryConversationStore(), MemoryMessageStore(), MemoryTopicStore(), MemoryForgottenStore(), MemoryUnknownStore(), MemoryUserSettingsStore(),
    MemoryAlertDismissalStore(),
)
