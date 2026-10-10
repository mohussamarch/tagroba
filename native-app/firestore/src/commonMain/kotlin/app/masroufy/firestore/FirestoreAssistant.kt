package app.masroufy.firestore

import app.masroufy.core.AlertDismissal
import app.masroufy.core.AssistConversation
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistTopic
import app.masroufy.core.ForgottenMark
import app.masroufy.core.UnknownQuestion
import app.masroufy.core.UserSetting
import app.masroufy.core.hashContent
import app.masroufy.data.AssistantCodecs
import app.masroufy.data.skippingUnreadable
import app.masroufy.port.AlertDismissalStore
import app.masroufy.port.AssistantConversationStore
import app.masroufy.port.AssistantForgottenStore
import app.masroufy.port.AssistantMessageStore
import app.masroufy.port.AssistantTopicStore
import app.masroufy.port.AssistantUnknownStore
import app.masroufy.port.UserSettingsStore

/**
 * المساعد «مصروفي» على فايربيز (OVERRIDES §78 + رد المالك ٤): **على مستوى الحساب** (`users/{uid}/…`) — القاعدة العامة
 * `users/{uid}/{document=**}` بتغطيه ⇒ مفيش نشر قواعد. رسايل المحادثة بتتقرا بشرط واحد (`conversationId ==`) ⇒ مفيش فهرس مركّب.
 * المستند اللي ما يتقريش (نسخة أحدث كتبت نوع جديد) بيتخطّى.
 */
class FirestoreUserSettings(private val space: FirestoreSpace) : UserSettingsStore {
    private val codec = AssistantCodecs.userSettings

    override suspend fun listAll(): List<UserSetting> = space.select(codec.skippingUnreadable()).filterNotNull()

    override suspend fun save(setting: UserSetting) = space.saveAll(codec, listOf(setting))

    override suspend fun remove(keys: List<String>) = space.deleteAll(codec.group, keys)
}

class FirestoreAssistantConversations(private val space: FirestoreSpace) : AssistantConversationStore {
    private val codec = AssistantCodecs.conversations

    override suspend fun listAll(): List<AssistConversation> = space.select(codec.skippingUnreadable()).filterNotNull()

    override suspend fun save(conversation: AssistConversation) = space.saveAll(codec, listOf(conversation))

    override suspend fun remove(ids: List<String>) = space.deleteAll(codec.group, ids)
}

class FirestoreAssistantMessages(private val space: FirestoreSpace) : AssistantMessageStore {
    private val codec = AssistantCodecs.messages

    override suspend fun listByConversation(conversationId: String): List<AssistMessage> =
        space.select(codec.skippingUnreadable(), DocQuery(listOf(Cond.Eq("conversationId", conversationId)))).filterNotNull()

    override suspend fun listAll(): List<AssistMessage> = space.select(codec.skippingUnreadable()).filterNotNull()

    override suspend fun save(message: AssistMessage) = space.saveAll(codec, listOf(message))

    override suspend fun removeByConversations(conversationIds: List<String>) {
        val ids = conversationIds.flatMap { c -> listByConversation(c).map { it.id } }
        if (ids.isNotEmpty()) space.deleteAll(codec.group, ids)
    }
}

class FirestoreAssistantTopics(private val space: FirestoreSpace) : AssistantTopicStore {
    private val codec = AssistantCodecs.topics

    override suspend fun listAll(): List<AssistTopic> = space.select(codec.skippingUnreadable()).filterNotNull()

    override suspend fun save(topic: AssistTopic) = space.saveAll(codec, listOf(topic))

    override suspend fun remove(keys: List<String>) = space.deleteAll(codec.group, keys)
}

class FirestoreAssistantForgotten(private val space: FirestoreSpace) : AssistantForgottenStore {
    private val codec = AssistantCodecs.forgotten

    override suspend fun listAll(): List<ForgottenMark> = space.select(codec.skippingUnreadable()).filterNotNull()

    override suspend fun save(mark: ForgottenMark) = space.saveAll(codec, listOf(mark))

    override suspend fun remove(ids: List<String>) = space.deleteAll(codec.group, ids)
}

class FirestoreAssistantUnknown(private val space: FirestoreSpace) : AssistantUnknownStore {
    private val codec = AssistantCodecs.unknown

    override suspend fun listAll(): List<UnknownQuestion> = space.select(codec.skippingUnreadable()).filterNotNull()

    override suspend fun save(question: UnknownQuestion) = space.saveAll(codec, listOf(question))

    override suspend fun remove(ids: List<String>) = space.deleteAll(codec.group, ids)
}

/** إشعارات الجرس الممسوحة (رد المالك ٣). معرّف المستند = بصمة الموضوع (فيه «|» و«:»). */
class FirestoreAlertDismissals(private val space: FirestoreSpace) : AlertDismissalStore {
    private val codec = AssistantCodecs.alertDismissals

    override suspend fun listAll(): List<AlertDismissal> = space.select(codec.skippingUnreadable()).filterNotNull()

    override suspend fun save(dismissal: AlertDismissal) = space.saveAll(codec, listOf(dismissal))

    override suspend fun remove(threadKeys: List<String>) = space.deleteAll(codec.group, threadKeys.map(::hashContent))
}
