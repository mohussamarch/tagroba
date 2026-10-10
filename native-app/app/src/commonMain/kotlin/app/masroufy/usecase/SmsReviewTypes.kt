package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Category
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.SmsFee
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsShape
import app.masroufy.core.TextKey

/** أنواع شاشة رسايل البنك (`ReviewSmsInbox`) — اتنقلت هنا عشان حد الـ300 سطر (S1). */

/** نتيجة التسجيل لحالات الاستخدام — [batchIds] الدفعات اللي اتسجل فيها حاجة فعلًا (دفعة لكل محفظة — مراجعة S1). */
internal class SmsRecordOutcome(val recorded: Int, val duplicates: Int, val batchIds: List<Id>)

data class SmsReviewLine(
    val messageId: String,
    val lineNumber: Int,
    val date: String,
    val merchant: String,
    val amountMinor: Halalas,
    val direction: Direction,
    val categoryId: Id?,
    /** المستخدم افتكر المحل ده قبل كده — مفيش سؤال «نفتكره؟» تاني. */
    val remembered: Boolean,
    val state: MatchingState,
    val reason: String,
    /** القارئ فهمها إزاي (الجولة الرابعة) — مش واضحة ([SmsShape.clear] = false) ⇒ ما بتتسجلش لوحدها في الخلفية. */
    val shape: SmsShape = SmsShape.KeywordFallback,
    /**
     * سبب إنها **مستنية تأكيدك** رغم إنها جديدة: اتفهمت من كلمات عامة بس (`TextKey.SMS_WAIT_UNKNOWN_SHAPE`)، أو شكل لسه ما أكدتهوش
     * (§77-A — `TextKey.SMS_WAIT_NEW_SHAPE`)، أو سبب تاني ([waitReason])؛ null = هتتسجل لوحدها. الشاشة بتعرضها جاهزة (متعبّية)
     * و«سجّل الكل» بيسجلها — ضغطة المالك هي التأكيد.
     */
    val confirmReason: String? = null,
    /** عقد C0: نوع العملية زي ما الرسالة بتقوله (دليل — [SmsKind]). */
    val kind: SmsKind = SmsKind.OTHER,
    /** S1: مفتاح [confirmReason] (عشان العدّ يفرّق «شكل جديد» عن «كلمات عامة» من غير ما يقارن نصوص). */
    val waitReason: TextKey? = null,
    /**
     * S1 (§75-2): سؤال جوه التطبيق على الرسالة دي — «ده راتبك؟» ([AskKind.IS_SALARY]) — أو null. مراجعة S1: الرسالة اللي عليها السؤال
     * **ما بتتسجلش** بـ«سجّل الكل» لحد ما يترد عليه (`ReviewSmsInbox.answerSalary` · `AutoRecordSms.confirm(…, isSalary)`).
     */
    val question: AskKind? = null,
    /** مراجعة S1 (§75-11): المحفظة اللي الرسالة دي هتتسجل فيها — ممكن تبقى غير محفظة الشاشة (أرقام حسابها لمحفظة تانية). */
    val walletId: Id? = null,
    /**
     * S2 (§77-B): الرسوم اللي هتتسجل عملية «رسوم بنكية» لوحدها جنب السطر ده (`smsFeeToRecord` — نفس اللي `SmsFeeEffect` بيكتبه)، أو null.
     * [SmsFee.includedInAmount] = [amountMinor] فيه الرسوم (الأصلية هتتسجل المبلغ − الرسوم). الشاشة بتعرضها قبل «سجّل الكل».
     */
    val fee: SmsFee? = null,
)

data class SmsFailed(val messageId: String, val sender: String, val date: String, val reason: String)

data class SmsReview(
    val enabled: Boolean,
    val permission: Boolean,
    val senders: List<String>,
    val more: Boolean,
    /** جديدة — بتتسجل بـ«سجّل الكل». */
    val ready: List<SmsReviewLine>,
    /** شبه عملية موجودة — ما بتتسجلش إلا لو المستخدم اختارها. التعارض ما بيتسجلش أبدًا. */
    val similar: List<SmsReviewLine>,
    /** موجودة فعلًا — بتتشال من القايمة مع «سجّل الكل». */
    val duplicates: List<SmsReviewLine>,
    val failed: List<SmsFailed>,
    /** كل التصنيفات (حتى المخفية) عشان اسم ولون تصنيف قديم يبان؛ الاختيار من الظاهر بس. */
    val categories: List<Category>,
)

/** اللي بيترفع للقايمة المشتركة (OVERRIDES §25) — المصروف بس. */
data class MerchantContribution(val economicKind: EconomicKind, val observedDirection: Direction, val rawMerchantName: String)
