package app.masroufy.memory

import app.masroufy.core.AssistConversation
import app.masroufy.core.AssistPrefs
import app.masroufy.core.AssistTopic
import app.masroufy.core.Dismissal
import app.masroufy.core.ForgottenFact
import app.masroufy.core.MainSpendingWallet
import app.masroufy.core.UnknownQuestion
import app.masroufy.port.AssistantConversationStore
import app.masroufy.port.AssistantForgottenStore
import app.masroufy.port.AssistantPrefsStore
import app.masroufy.port.AssistantTopicStore
import app.masroufy.port.AssistantUnknownStore
import app.masroufy.port.DismissalStore
import app.masroufy.port.MainSpendingWalletStore

/** مخازن المساعد في الذاكرة — للاختبار (CLAUDE.md #6). */
class MemoryConversationStore(seed: List<AssistConversation> = emptyList()) : AssistantConversationStore {
    private val items = LinkedHashMap<String, AssistConversation>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<AssistConversation> = items.values.toList()

    override suspend fun save(conversation: AssistConversation) {
        items[conversation.id] = conversation
    }

    override suspend fun remove(id: String) {
        items.remove(id)
    }
}

class MemoryTopicStore(seed: List<AssistTopic> = emptyList()) : AssistantTopicStore {
    private val items = LinkedHashMap<String, AssistTopic>().apply { seed.forEach { put(it.key, it) } }

    override suspend fun listAll(): List<AssistTopic> = items.values.toList()

    override suspend fun save(topic: AssistTopic) {
        items[topic.key] = topic
    }

    override suspend fun remove(keys: List<String>) {
        keys.forEach { items.remove(it) }
    }
}

class MemoryPrefsStore(private var prefs: AssistPrefs? = null) : AssistantPrefsStore {
    override suspend fun load(): AssistPrefs? = prefs

    override suspend fun save(prefs: AssistPrefs) {
        this.prefs = prefs
    }
}

class MemoryForgottenStore(seed: List<ForgottenFact> = emptyList()) : AssistantForgottenStore {
    private val items = LinkedHashMap<String, ForgottenFact>().apply { seed.forEach { put(it.key, it) } }

    override suspend fun listAll(): List<ForgottenFact> = items.values.toList()

    override suspend fun saveMany(facts: List<ForgottenFact>) {
        facts.forEach { items[it.key] = it }
    }

    override suspend fun clear() {
        items.clear()
    }
}

class MemoryUnknownStore(seed: List<UnknownQuestion> = emptyList()) : AssistantUnknownStore {
    private val items = LinkedHashMap<String, UnknownQuestion>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<UnknownQuestion> = items.values.toList()

    override suspend fun save(question: UnknownQuestion) {
        items[question.id] = question
    }
}

class MemoryMainWalletStore(seed: List<MainSpendingWallet> = emptyList()) : MainSpendingWalletStore {
    private val items = LinkedHashMap<String, MainSpendingWallet>().apply { seed.forEach { put(it.spaceId, it) } }

    override suspend fun listAll(): List<MainSpendingWallet> = items.values.toList()

    override suspend fun save(main: MainSpendingWallet) {
        items[main.spaceId] = main
    }

    override suspend fun remove(spaceId: String) {
        items.remove(spaceId)
    }
}

class MemoryDismissalStore(seed: List<Dismissal> = emptyList()) : DismissalStore {
    private val items = LinkedHashMap<String, Dismissal>().apply { seed.forEach { put(it.key, it) } }

    override suspend fun listAll(): List<Dismissal> = items.values.toList()

    override suspend fun save(dismissal: Dismissal) {
        items[dismissal.key] = dismissal
    }

    override suspend fun remove(keys: List<String>) {
        keys.forEach { items.remove(it) }
    }
}
