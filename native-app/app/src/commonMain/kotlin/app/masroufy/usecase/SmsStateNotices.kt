package app.masroufy.usecase

import app.masroufy.core.SmsStateNotice
import app.masroufy.core.hashContent
import app.masroufy.core.isoInstantMillis
import app.masroufy.core.missedSms
import app.masroufy.core.smsStateNotice
import app.masroufy.port.DeviceNotifier
import app.masroufy.port.NoticeMemoryPort
import app.masroufy.port.NoticeOutcome
import app.masroufy.port.SmsInboxState

/**
 * [inboxState] = حالة الصندوق **بعد** خطوة التسجيل (null = الجهاز مالوش رسايل) · [lastRead] = آخر قراية من أي نوع
 * (`BackgroundReadPort.lastAnyRead` — نفس اللي كارت «فيه رسايل فاتته» بيستعمله).
 */
class SmsStateNoticesDeps(
    val inboxState: suspend () -> SmsInboxState?,
    val lastRead: () -> Long?,
    val memory: NoticeMemoryPort,
    val notifier: DeviceNotifier,
)

/**
 * PLAN-0.6 §13 (ج) — إشعارات حالة القراية في الخلفية: خطوة صغيرة لوحدها في [RunBackgroundCycle] (مش محرك التنبيهات — ده محتاج كل
 * المرشحين). **التلات حالات بس** ([SmsStateNotice]) — العملية اللي اتسجلت لوحدها ما لهاش إشعار خالص (المالك 2026-10-22).
 * - **مفيش تكرار لنفس الحالة:** آخر حالة اتبعت عنها بتتفتكر ([NoticeMemoryPort]) — بيتبعت تاني بس لما الحالة تتغير (رسالة شكل جديد
 *   ما اتبعتش عنها · الإذن رجع واتسحب تاني · رسالة فاتته أحدث من آخر واحدة اتبعت عنها). الحالة لما بتخلص بتتمسح.
 * - **إذن الإشعارات:** مقفول ⇒ ولا حاجة بتتبعت **ولا بتتفتكر** (أول ما يتسمح بيتبعت).
 * - «فاتته»: بس لو خطوة الرسايل في الدورة دي **ما قرتش** (فشلت) — لو قرت، اللي وصل اتقرا (والمسجّل لوحده ما لهوش إشعار).
 */
class SmsStateNotices(private val deps: SmsStateNoticesDeps) {
    suspend fun check(sms: AutoRecordResult?, smsFailed: Boolean): List<SmsStateNotice> {
        val state = deps.inboxState() ?: return emptyList()
        if (!state.enabled) {
            // القراية مقفولة بإيد المالك ⇒ مفيش حالة تستاهل، ولما تتفتح تاني الحالات بتبدأ من الأول
            SmsStateNotice.entries.forEach { deps.memory.set(memoryKey(it), null) }
            return emptyList()
        }
        val due = mutableListOf<Pair<SmsStateNotice, String>>()
        if (!state.permission) {
            if (deps.memory.get(memoryKey(SmsStateNotice.PERMISSION_OFF)) != REVOKED) due += SmsStateNotice.PERMISSION_OFF to REVOKED
            // من غير إذن الصندوق ما بيتقريش ⇒ باقي الحالات مش معروفة
            return send(due)
        }
        deps.memory.set(memoryKey(SmsStateNotice.PERMISSION_OFF), null)
        if (sms != null && sms.status == AutoRecordStatus.RAN) newShape(sms.newShape)?.let { due += SmsStateNotice.NEW_SHAPE to it }
        if (smsFailed) missed(state)?.let { due += SmsStateNotice.MISSED to it } else if (sms != null) deps.memory.set(memoryKey(SmsStateNotice.MISSED), null)
        return send(due)
    }

    /** رسالة شكل جديد ما اتبعتش عنها قبل كده ⇒ القيمة الجديدة للذاكرة (بصمات المعرّفات). null = مفيش جديد. */
    private fun newShape(ids: List<String>): String? {
        val key = memoryKey(SmsStateNotice.NEW_SHAPE)
        val now = ids.map(::hashContent).toSortedSet()
        if (now.isEmpty()) {
            deps.memory.set(key, null)
            return null
        }
        val before = deps.memory.get(key)?.split(SEP)?.toSet().orEmpty()
        val value = now.joinToString(SEP)
        if ((now - before).isNotEmpty()) return value
        deps.memory.set(key, value)
        return null
    }

    /** نفس حساب الكارت ([missedSms]) ⇒ وقت أحدث رسالة فاتته (لو أحدث من آخر واحدة اتبعت عنها). null = مفيش أو اتبعت. */
    private fun missed(state: SmsInboxState): String? {
        val key = memoryKey(SmsStateNotice.MISSED)
        val last = deps.lastRead()
        val arrivals = state.messages.map { isoInstantMillis(it.receivedAt) }
        if (missedSms(last, arrivals, state.count).n == 0) {
            deps.memory.set(key, null)
            return null
        }
        val newest = arrivals.filterNotNull().filter { last == null || it > last }.maxOrNull() ?: 0L
        val before = deps.memory.get(key)?.toLongOrNull()
        return if (before == null || newest > before) newest.toString() else null
    }

    private suspend fun send(due: List<Pair<SmsStateNotice, String>>): List<SmsStateNotice> {
        if (due.isEmpty()) return emptyList()
        val notifier = deps.notifier
        if (!notifier.available || !notifier.permitted()) return emptyList()
        val sent = mutableListOf<SmsStateNotice>()
        for ((kind, value) in due) {
            when (notifier.post(kind.tag, smsStateNotice(kind), null)) {
                NoticeOutcome.SHOWN, NoticeOutcome.SCHEDULED -> {
                    deps.memory.set(memoryKey(kind), value)
                    sent += kind
                }
                NoticeOutcome.BLOCKED, NoticeOutcome.UNAVAILABLE -> Unit
            }
        }
        return sent
    }

    private companion object {
        const val REVOKED = "revoked"
        const val SEP = ","

        fun memoryKey(kind: SmsStateNotice) = "sms-notice-" + kind.name.lowercase()
    }
}
