package app.masroufy.usecase

import app.masroufy.core.Id

data class AutoRecordResult(
    val status: AutoRecordStatus,
    val recorded: Int = 0,
    /** اتسجلت قبل كده (من الشاشة أو تشغيلة وقعت قبل الشيل) — اتشالت من الصندوق من غير تسجيل. */
    val duplicates: Int = 0,
    /** معرّفات الرسايل اللي مستنية قرار المالك، بترتيب الصندوق (الأقدم الأول). */
    val waiting: List<String> = emptyList(),
    /** العمليات اللي اتسجلت دلوقتي — الشاشة بتعمل عليها اللمعة النعناعي (§71). */
    val recordedTransactionIds: List<Id> = emptyList(),
    /** البنوك (المرسلين) اللي محتاجة المالك يختار محفظتها ⇒ رسايلها مستنية. */
    val unmappedSenders: List<UnmappedSender> = emptyList(),
    /** جزء من [waiting]: اتفهمت **من كلمات عامة بس** (`TextKey.SMS_WAIT_UNKNOWN_SHAPE`). بترتيب الصندوق. */
    val unknownShape: List<String> = emptyList(),
    /** جزء من [waiting] (§77-A): **شكل واضح لسه ما اتأكدش** من المرسل ده (`TextKey.SMS_WAIT_NEW_SHAPE`). بترتيب الصندوق. */
    val newShape: List<String> = emptyList(),
)

/** حد أمان لتقليب صفحات الصندوق في تشغيلة واحدة (مراجعة 0.3). */
internal const val MAX_PAGE_PASSES = 1_000
