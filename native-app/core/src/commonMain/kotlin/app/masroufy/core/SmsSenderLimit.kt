package app.masroufy.core

/**
 * OVERRIDES §112-7 (قرار المالك 2026-10-23): حد البنوك اللي بتبعت رسايل — **25 لكل بلد**، والرقم هنا بس (كان 10 مكتوبة في 6 أماكن).
 * قايمة المرسلين على الجهاز واحدة للبلاد كلها (`SmsInboxState.senders`)، فحد القايمة = 25 × عدد البلاد اللي ليها قارئ رسايل.
 */
const val MAX_SMS_SENDERS_PER_COUNTRY = 25

/** حد قايمة المرسلين على الجهاز (كل البلاد مع بعض). */
val MAX_SMS_SENDERS: Int get() = MAX_SMS_SENDERS_PER_COUNTRY * COUNTRY_PACKS.values.count { it.smsReader != null }.coerceAtLeast(1)
