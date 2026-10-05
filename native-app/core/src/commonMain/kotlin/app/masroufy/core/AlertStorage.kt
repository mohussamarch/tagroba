package app.masroufy.core

/**
 * تخزين محرك التنبيهات (OVERRIDES §61 — رد المالك (١)): **على مستوى الحساب** (`users/{uid}`) مش جوه بلد — المحرك واحد لكل البلاد
 * ومواضيع البلاد التانية متعلّمة ببلدها (`inSpace`)، فمكان واحد بيكفي ومفيش تكرار.
 * - [ALERT_SETTINGS_GROUP] المجموعات المقفولة — **بتتزامن وفي النسخة الشاملة** (اختيار المستخدم، زي ملفه).
 * - [ALERT_INBOX_GROUP] صفحة الإشعارات و[ALERT_RECEIPTS_GROUP] إيصالات «اتبعت» — **بتتزامن ومش في النسخة** (بتتولد من البيانات تاني،
 *   ورجوع إيصالات قديمة من نسخة كان هيكتم تنبيهات صح — اختيار Claude، المالك يقدر يغيّره).
 * - **التعلّم** (ساعاتك وتفاعلك مع كل نوع) **على الجوال بس** ومش هنا خالص (قرار المالك) — موديول الجهاز.
 */
const val ALERT_SETTINGS_GROUP = "alertSettings"
const val ALERT_INBOX_GROUP = "alertInbox"
const val ALERT_RECEIPTS_GROUP = "alertReceipts"

/** اللي بيتزامن ومش في النسخة الشاملة — على مستوى الحساب. */
val ALERT_SYNC_ONLY_GROUPS: List<String> = listOf(ALERT_INBOX_GROUP, ALERT_RECEIPTS_GROUP)

/** مجموعة تنبيهات مقفولة أو مفتوحة — مستند لكل مجموعة (المعرّف = اسمها)، فقفلها على جوال بيقفلها على التاني. */
data class AlertGroupSetting(val group: AlertGroup, val enabled: Boolean)
