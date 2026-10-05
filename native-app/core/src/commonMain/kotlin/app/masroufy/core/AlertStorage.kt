package app.masroufy.core

/**
 * تخزين محرك التنبيهات (OVERRIDES §61 — رد المالك (١)): **على مستوى الحساب** (`users/{uid}`) مش جوه بلد — المحرك واحد لكل البلاد
 * ومواضيع البلاد التانية متعلّمة ببلدها (`inSpace`)، فمكان واحد بيكفي ومفيش تكرار.
 * - [ALERT_SETTINGS_GROUP] المجموعات المقفولة — **بتتزامن وفي النسخة الشاملة** (اختيار المستخدم، زي ملفه).
 * - [ALERT_INBOX_GROUP] صفحة الإشعارات **بقرايتها** (`openedAt`) — **بتتزامن وفي النسخة الشاملة** (اختيار المالك §69: «وصفحة الإشعارات
 *   كمان»). بتتكتب في الملف **بس لو فيها حاجة** ⇒ ملف الإصدار 2 من غيرها هو هو. سطر بنوع مش معروف (من نسخة أحدث) بيتخطّى في الاسترجاع.
 * - [ALERT_RECEIPTS_GROUP] إيصالات «اتبعت» — **بتتزامن ومش في النسخة** (بتتولد تاني، ورجوع إيصالات قديمة كان هيكتم تنبيهات صح).
 * - **التعلّم** (ساعاتك وتفاعلك مع كل نوع) **على الجوال بس** ومش هنا خالص (قرار المالك) — موديول الجهاز.
 */
const val ALERT_SETTINGS_GROUP = "alertSettings"
const val ALERT_INBOX_GROUP = "alertInbox"
const val ALERT_RECEIPTS_GROUP = "alertReceipts"

/** اللي بيتزامن ومش في النسخة الشاملة — على مستوى الحساب. */
val ALERT_SYNC_ONLY_GROUPS: List<String> = listOf(ALERT_RECEIPTS_GROUP)

/** مجموعة تنبيهات مقفولة أو مفتوحة — مستند لكل مجموعة (المعرّف = اسمها)، فقفلها على جوال بيقفلها على التاني. */
data class AlertGroupSetting(val group: AlertGroup, val enabled: Boolean)

/**
 * سطر صفحة الإشعارات من النسخة **يترجع؟** نفس شروط قارئ السطر (`AlertCodecs.alertInbox`): النوع · الاتجاه · طريقة التوصيل · أسباب القرار
 * كلها قيم معروفة، وساعة التوصيل من 0 لـ23. سطر من نسخة أحدث من التطبيق (نوع جديد مثلًا) ⇒ **بيتخطّى** بدل ما النسخة كلها تترفض
 * (نفس سلوك الصفحة نفسها مع السطر المجهول — جلسة 18).
 */
fun isRestorableInboxRow(row: BackupRow): Boolean {
    if (AlertKind.fromWire(jsString(row["kind"])) == null) return false
    if (DueFlow.entries.none { it.wire == row["flow"] }) return false
    if (AlertDelivery.entries.none { it.wire == row["delivery"] }) return false
    val factors = row["factors"] as? List<*> ?: return false
    if (factors.any { f -> AlertFactor.entries.none { it.name.lowercase() == f } }) return false
    if (row["deliverAtDate"] != null) {
        val hour = numberOf(row["deliverAtHour"]) ?: return false
        if (hour % 1.0 != 0.0 || hour < 0 || hour > 23) return false
    }
    return true
}
