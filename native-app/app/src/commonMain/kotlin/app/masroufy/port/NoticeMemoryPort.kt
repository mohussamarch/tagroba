package app.masroufy.port

/**
 * PLAN-0.6 §13 (ج): آخر حالة اتبعت عنها إشعار، لكل حالة — عشان نفس الحالة ما يتبعتلهاش إشعار تاني لحد ما تتغير. على الجهاز ده بس
 * (مش بيانات حساب ومش بيتزامن). القيم معرّفات ثابتة أو أوقات — عمرها ما بتبقى نص رسالة.
 * التنفيذ: `SharedPreferences` على أندرويد (`AndroidNoticeMemory` في `:device`) · الذاكرة للاختبار (`MemoryNoticeMemory`).
 */
interface NoticeMemoryPort {
    fun get(key: String): String?

    /** null = تتمسح (الحالة خلصت). */
    fun set(key: String, value: String?)
}
