package app.masroufy.port

import app.masroufy.core.AssistConversation
import app.masroufy.core.AssistPrefs
import app.masroufy.core.AssistTopic
import app.masroufy.core.Dismissal
import app.masroufy.core.ForgottenFact
import app.masroufy.core.MainSpendingWallet
import app.masroufy.core.UnknownQuestion

/**
 * مخازن المساعد «مصروفي» (OVERRIDES §78) — **كلها على مستوى الحساب** (`users/{uid}/…`) وبتتزامن وبتدخل النسخة الشاملة (قرار المالك:
 * «الذاكرة والأسئلة في حساب المالك وتتزامن»، والرد ٤ على فرع التصميم: السجل كمان). `listAll` مسموح: الأعداد صغيرة بطبيعتها (آخر ١٠٠
 * محادثة · مواضيع · أسئلة). نسخ الذاكرة (`MemoryAssistant.kt`) للاختبار، وفايربيز (`FirestoreAssistant.kt`) للتشغيل.
 */
interface AssistantConversationStore {
    suspend fun listAll(): List<AssistConversation>

    suspend fun save(conversation: AssistConversation)

    suspend fun remove(id: String)
}

interface AssistantTopicStore {
    suspend fun listAll(): List<AssistTopic>

    suspend fun save(topic: AssistTopic)

    suspend fun remove(keys: List<String>)
}

/** مستند واحد للحساب: مفتاح «يتعلّم من أسئلتي». null = لسه ما اتغيرش ⇒ شغال. */
interface AssistantPrefsStore {
    suspend fun load(): AssistPrefs?

    suspend fun save(prefs: AssistPrefs)
}

interface AssistantForgottenStore {
    suspend fun listAll(): List<ForgottenFact>

    suspend fun saveMany(facts: List<ForgottenFact>)

    suspend fun clear()
}

interface AssistantUnknownStore {
    suspend fun listAll(): List<UnknownQuestion>

    suspend fun save(question: UnknownQuestion)
}

/** المحفظة الأساسية لكل بلد (رد المالك ٢ — 2026-10-09). مستند لكل بلد بمعرّف البلد. */
interface MainSpendingWalletStore {
    suspend fun listAll(): List<MainSpendingWallet>

    suspend fun save(main: MainSpendingWallet)

    suspend fun remove(spaceId: String)
}

/** المسح بـ«×»: إشعارات الجرس ([alertDismissals]) وكروت «أمور لم تُنجزها بعد» — نفس الشكل، مجموعتين منفصلتين. */
interface DismissalStore {
    suspend fun listAll(): List<Dismissal>

    suspend fun save(dismissal: Dismissal)

    suspend fun remove(keys: List<String>)
}
