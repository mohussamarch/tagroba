package app.masroufy.port

import app.masroufy.core.AlertDecision
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.DueFlow
import app.masroufy.core.KindStats
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.UsualHours

/**
 * محرك التنبيهات (OVERRIDES §61 — رد المالك (١)، اتبنى في جلسة 18):
 * - **بيتزامن بين الأجهزة** (فايربيز على مستوى الحساب — `FirestoreAlerts.kt`): الإعدادات ([AlertSettingsStore]) والصفحة
 *   ([AlertInboxStore]) والإيصالات ([AlertReceiptStore]) — قفل مجموعة على جوال بيقفلها على التاني، والتنبيه اللي اتبعت على جوال
 *   ما يتبعتش تاني على التاني.
 * - **على الجوال بس، مش بيتزامن** (قرار المالك): التعلم — تفاعلك مع كل نوع ([AlertInteractionStore]) والساعات اللي بتفتح فيها
 *   ([UsualHoursStore]) — في موديول الجهاز (`AndroidAlertLearning.kt` · `IosAlertLearning.kt`).
 * نسخ الذاكرة (`MemoryAlerts.kt`) للاختبار.
 */

/** المجموعات اللي المستخدم قفلها. **المحرك عمره ما بيكتب هنا** — المستخدم بس. */
interface AlertSettingsStore {
    suspend fun disabledGroups(): Set<AlertGroup>

    suspend fun setGroupEnabled(group: AlertGroup, enabled: Boolean)
}

/** تفاعلك مع كل نوع (اتعرض/اتفتح). على الجوال بس. */
interface AlertInteractionStore {
    suspend fun load(): Map<AlertKind, KindStats>

    suspend fun save(kind: AlertKind, stats: KindStats)
}

/** عدد فتحات التطبيق في كل ساعة. على الجوال بس. */
interface UsualHoursStore {
    suspend fun load(): UsualHours

    suspend fun save(hours: UsualHours)
}

/**
 * إيصالات اللي اتبعت — نفس فكرة `NotificationReceipt` (المفتاح = `eventKey`): **نفس التنبيه ما يتبعتش مرتين**.
 * منفصل عن `NotificationReceiptRepository` (اللي بيتزامن) عشان ما يتربطش بيه بالغلط.
 */
interface AlertReceiptStore {
    suspend fun listAll(): List<NotificationReceipt>

    suspend fun saveMany(receipts: List<NotificationReceipt>)
}

/**
 * سطر في صفحة الإشعارات. واحد لكل **موضوع** ([threadKey]) — الدرجة الأعلى بتحل محل الأقل.
 * [title]/[body] التفاصيل (فيها مبالغ وأسامي) — للصفحة جوه التطبيق بس. [decision] = ليه اتبعت كده (بيتكتب كنص وقت العرض).
 */
data class AlertInboxEntry(
    val threadKey: String,
    val eventKey: String,
    val kind: AlertKind,
    val flow: DueFlow,
    val title: String,
    val body: String,
    val decision: AlertDecision,
    val createdAt: String,
    val openedAt: String? = null,
    /** اسم البلد جوه الصفحة (§64) — `null` = بلد واحدة أو تنبيه على مستوى الحساب. **مش في نص شاشة القفل.** */
    val spaceLabel: String? = null,
)

interface AlertInboxStore {
    suspend fun listAll(): List<AlertInboxEntry>

    /** بيحل محل أي سطر بنفس الموضوع. */
    suspend fun save(entry: AlertInboxEntry)

    suspend fun remove(threadKeys: List<String>)
}
