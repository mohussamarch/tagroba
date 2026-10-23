package app.masroufy.port

import app.masroufy.core.SupportMessage

/**
 * صندوق «ابعتلنا» على الجهاز (OVERRIDES ❓ «ابعتلنا — الوجهة»): بيحفظ الرسايل **محليًا بس**. مفيش إرسال لأي مكان لحد ما المالك يحسم الوجهة.
 * TODO(owner decision): الوجهة المقترحة = مستودع جيت هاب **خاص** عبر مهمة آلية (scheduled Action) — المالك لازم يعمل المستودع. لا تبعت من هنا.
 * دوال عادية (مش `suspend`) — ملف صغير. التنفيذ: `FileSupportOutbox` في `:device` · `MemorySupportOutbox` للاختبار.
 */
interface SupportOutboxPort {
    fun add(message: SupportMessage)

    /** من الأقدم للأحدث. */
    fun all(): List<SupportMessage>
}
