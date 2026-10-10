package app.masroufy.ui.screens.imports

import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.usecase.AddOperationResult
import app.masroufy.usecase.AutoRecordResult
import app.masroufy.usecase.EditTransaction
import app.masroufy.usecase.ImportStatement
import app.masroufy.usecase.InboxView
import app.masroufy.usecase.ManageCategories
import app.masroufy.usecase.ReadBankSms
import app.masroufy.usecase.ReadPdfStatement
import app.masroufy.usecase.ResumeStagedBatch
import app.masroufy.usecase.RevertImportBatch
import app.masroufy.usecase.SmsReview
import app.masroufy.usecase.UnmappedSender

/**
 * منطقة «الاستيراد» (`SCREENS.md` §٢.٤): حالات الاستخدام اللي شاشاتها محتاجاها **بس** — التنفيذ في `:wiring` (`ImportsGraph`).
 * **ممنوع** مستودع هنا (CLAUDE.md #4): الشاشة بتنادي حالة استخدام، وما بتحسبش مبلغ.
 */
interface ImportsDeps {
    /** رسايل البنك على الجهاز ده — null = الجهاز ما بيقراش رسايل (الآيفون) أو البلد مالهاش قارئ ⇒ حالة «غير متاح» و«الصق رسالة». */
    val sms: SmsDeps?

    /** قراية رسالة ملصوقة أو فترة من الجوال — null = البلد مالهاش قارئ رسايل. `available` = قراية الفترة ممكنة (أندرويد). */
    val readSms: ReadBankSms?

    /** قراية كشف PDF — null = الجهاز مالوش قارئ PDF. */
    val pdf: ReadPdfStatement?

    /** خط الاستيراد الواحد: معاينة من غير كتابة ⇒ تأكيد ذري (الكشف والرسايل الملصوقة). */
    val importer: ImportStatement

    /** سجل الدفعات + معاينة الإرجاع وتنفيذه. */
    val batches: RevertImportBatch

    /** الدفعات اللي اتقطعت في النص وتنضيفها. */
    val staged: ResumeStagedBatch

    /** تفاصيل عملية وتصنيفها (الطرف التاني في «قارن» · صفوف الإرجاع · تصنيف عملية اتسجلت من رسالة). */
    val transactions: EditTransaction

    /** كل التصنيفات (حتى المخفية — عشان اسم ولون تصنيف قديم يبان؛ الاختيار من الظاهر بس). */
    val categories: ManageCategories

    /** محافظ البلد الشغالة (`QuickAddOperation.options`). */
    suspend fun wallets(): List<Wallet>

    /** أول [rows] سطر من ملف CSV بعناوينه (تحديد الأعمدة يدويًا) — null = الملف مش CSV مقروء. */
    fun csvTable(content: String, rows: Int = 4): CsvTable?

    /** فترات «اقرأ فترة» الجاهزة: من أول الشهر المالي · آخر ٧ أيام · آخر ٣٠ يومًا. */
    suspend fun smsRanges(): List<SmsRange>
}

/** صندوق رسايل البنك والتسجيل لوحده (OVERRIDES §72) — كله من `AutoRecordSms` · `ReviewSmsInbox` · `ManageSmsInbox`. */
interface SmsDeps {
    /**
     * الصورة كلها: الصندوق · البنوك اللي مالهاش محفظة · مراجعة رسايل كل بنك ليه محفظة. [record] = يسجّل الجديد المفهوم الأول
     * (نفس تشغيلة الخلفية — لو القراية شغالة) ويرجّع اللي اتسجل في [SmsOverview.fresh] للمعة.
     */
    suspend fun overview(record: Boolean): SmsOverview

    /** «سجّل الكل»: الجاهز في كل بنك + الشبيه اللي المالك اختاره ([includeSimilar] بمعرّف الرسالة) بالتصنيف اللي اختاره ([categories]). */
    suspend fun record(categories: Map<String, Id>, includeSimilar: Set<String>): Int

    /** «احذفها»: الرسايل دي تتشال من الصندوق من غير تسجيل. */
    suspend fun dismiss(messageIds: List<String>)

    /** محفظة بنك (مرة واحدة لكل بنك) ثم تشغيلة بتسجّل المستني. [walletId] = null ⇒ يشيل الربط. */
    suspend fun chooseWallet(sender: String, walletId: Id?): AutoRecordResult

    /** تفعيل القراية بالمرسلين دول (الإذن قبلها من `LocalPermissions`). */
    suspend fun enable(senders: List<String>): InboxView

    /** إيقاف القراية — اللي اتسجل قبل كده زي ما هو. */
    suspend fun disable(): InboxView

    /** «تم — «المحل» سيُصنَّف دائمًا»: تصنيف المحل بيتحفظ (من غير سؤال «نفتكره؟» — OVERRIDES §76 (١٦)). */
    suspend fun remember(merchant: String, categoryId: Id, direction: Direction): Boolean

    /** اللي اتسجل من رسايل البنك النهارده، الأحدث الأول. */
    suspend fun recordedToday(): List<Transaction>

    /** رسالة ما اتفهمتش ⇒ تتسجل صرف بالمبلغ اللي المالك كتبه في محفظة بنكها، وبعدها تتشال من الصندوق. */
    suspend fun recordByHand(messageId: String, sender: String, amountText: String): AddOperationResult
}

/**
 * صورة رسايل البنك. [senderWallets] = المحفظة اللي رسايل كل مرسل مفعّل بتتسجل فيها (null = مستنية المالك يختار) بنفس كتابة المرسل في
 * الصندوق. [fresh] = العمليات اللي اتسجلت لوحدها في الفتحة دي.
 */
data class SmsOverview(
    val inbox: InboxView,
    val unmapped: List<UnmappedSender>,
    val reviews: List<SenderReview>,
    val senderWallets: Map<String, Id?>,
    val fresh: List<Id> = emptyList(),
)

/** مراجعة رسايل بنك واحد على محفظته (`ReviewSmsInbox.load`). */
data class SenderReview(val sender: String, val walletId: Id, val review: SmsReview)

/** أول سطور ملف CSV: [header] = أول سطر · [rows] = اللي بعده · [lines] = عدد سطور البيانات كلها. */
data class CsvTable(val header: List<String>, val rows: List<List<String>>, val lines: Int)

enum class SmsRangeKind { MONTH, WEEK, DAYS30 }

/** فترة قراية الرسايل (من · لحد — ISO). */
data class SmsRange(val kind: SmsRangeKind, val from: IsoDate, val to: IsoDate)
