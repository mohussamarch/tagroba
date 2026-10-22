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
import app.masroufy.usecase.SmsHistoryQuestion
import app.masroufy.usecase.SmsHistoryReport
import app.masroufy.usecase.SmsReview
import app.masroufy.usecase.UnmappedSender

/**
 * منطقة «الاستيراد» (`SCREENS.md` §2.4): حالات الاستخدام اللي شاشاتها محتاجاها **بس** — التنفيذ في `:wiring` (`ImportsGraph`).
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

    /** فترات «اقرأ فترة» الجاهزة: من أول الشهر المالي · آخر 7 أيام · آخر 30 يومًا. */
    suspend fun smsRanges(): List<SmsRange>

    /** النهارده **بتوقيت البلد** (§88 — مش الجوال): «النهاردة؟» في الرسالة الملصوقة (§82-14) وآخر يوم في «اقرأ فترة». */
    fun smsToday(): IsoDate
}

/** صندوق رسايل البنك والتسجيل لوحده (OVERRIDES §72) — كله من `AutoRecordSms` · `ReviewSmsInbox` · `ManageSmsInbox`. */
interface SmsDeps {
    /**
     * الصورة كلها: الصندوق · البنوك اللي مالهاش محفظة · مراجعة رسايل كل بنك ليه محفظة. [record] = يسجّل الجديد المفهوم الأول
     * (نفس تشغيلة الخلفية — لو القراية شغالة) ويرجّع اللي اتسجل في [SmsOverview.fresh] للمعة.
     */
    suspend fun overview(record: Boolean): SmsOverview

    /**
     * r041c — «اسحب لتحت» في «العمليات»: التسجيل لوحده على الرسايل **الجديدة بس** (من غير مراجعة كل بنك اللي `overview` بيبنيها) ⇒
     * العمليات اللي اتسجلت دلوقتي (للمعة). برّه خيط الواجهة.
     */
    suspend fun recordNew(): List<Id> = overview(record = true).fresh

    /**
     * r041c — «إعداد القراءة»: الصندوق والمرسلين ومحفظة كل بنك وعدد المستني محفظة **بس** ([SmsOverview.reviews] فاضية) — من غير معاينة
     * ولا استنا تشغيلة الخلفية. برّه خيط الواجهة.
     */
    suspend fun settings(): SmsOverview = overview(record = false)

    /** r041c: آخر [settings] اتحسبت (في الذاكرة) — الصفحة بتعرضها على طول وهي بتجيب الجديدة. null = لسه. */
    fun cachedSettings(): SmsOverview? = null

    /** «سجّل الكل»: الجاهز في كل بنك + الشبيه اللي المالك اختاره ([includeSimilar] بمعرّف الرسالة) بالتصنيف اللي اختاره ([categories]). */
    suspend fun record(categories: Map<String, Id>, includeSimilar: Set<String>): Int

    /** «احذفها»: الرسايل دي تتشال من الصندوق من غير تسجيل. */
    suspend fun dismiss(messageIds: List<String>)

    /** محفظة بنك (مرة واحدة لكل بنك) ثم تشغيلة بتسجّل المستني. [walletId] = null ⇒ يشيل الربط. */
    suspend fun chooseWallet(sender: String, walletId: Id?): AutoRecordResult

    /**
     * OVERRIDES §91-1 — «الحساب ده بتاعك؟» ([WaitLineUi.askLast4]): [mine] ⇒ الأرقام على المحفظة [walletId] اللي اختارها · لأ ⇒ «مش بتاعي» على محفظة
     * الرسالة. وبعدها تشغيلة بتسجّل المستني. الافتراضي: مفيش.
     * r041 A3: [messageId] = الرسالة اللي المالك رد عليها — بتتسجل على طول وشكلها بيتعلّم (الرد تأكيد صريح ليها).
     */
    suspend fun answerAccount(walletId: Id, last4: String, mine: Boolean, messageId: String? = null, replace: Boolean = false): AutoRecordResult? = null

    /** r041b P10 — «محفظة جديدة لهذا الحساب»: بنك محفظة الرسالة [fromWalletId] بالأرقام دي ورصيدها «غير متاح»، وبعدها نفس التسجيل. */
    suspend fun accountNewWallet(fromWalletId: Id, last4: String, messageId: String? = null): Pair<app.masroufy.core.Wallet, AutoRecordResult>? = null

    /** r041 A10: كارت «اسمح بقراءة رسائل البنك» على الجهاز ده (`SmsInboxPort.handoffCard`). الافتراضي: مفيش. */
    suspend fun handoffCard(): String? = null

    suspend fun setHandoffCard(value: String?) {}

    /** r06: «آخر مرة قرا لوحده» في الخلفية — null = لسه ما قراش (الشاشة بتقول كده، ما بتخترعش وقت). */
    suspend fun lastBackgroundRead(): app.masroufy.core.LastReadWhen? = null

    /**
     * r06-1a3 «فيه رسايل فاتته»: عدد رسايل [inbox] (من مسح الشاشة نفسه — مفيش مسح تاني) اللي وصلت بعد آخر قراية في الخلفية ولسه ما اتقرتش.
     * 0 = تمام، أو الجهاز ما بيعرفش (مفيش سجل خلفية).
     */
    suspend fun missedWhileAway(inbox: InboxView): Int = 0

    /** r041 A8: القراءة شغالة على الجهاز ده (مفعّلة والإذن موجود) — للرئيسية الفاضية. الافتراضي: لأ. */
    suspend fun readingOn(): Boolean = false

    /** تفعيل القراية بالمرسلين دول (الإذن قبلها من `LocalPermissions`). */
    suspend fun enable(senders: List<String>): InboxView

    /** إيقاف القراية — اللي اتسجل قبل كده زي ما هو. */
    suspend fun disable(): InboxView

    /** «تم — «المحل» سيُصنَّف دائمًا»: تصنيف المحل بيتحفظ (من غير سؤال «نفتكره؟» — OVERRIDES §76 (16)). */
    suspend fun remember(merchant: String, categoryId: Id, direction: Direction): Boolean

    /** اللي اتسجل من رسايل البنك النهارده، الأحدث الأول. */
    suspend fun recordedToday(): List<Transaction>

    /** رسالة ما اتفهمتش ⇒ تتسجل صرف بالمبلغ اللي المالك كتبه في محفظة بنكها، وبعدها تتشال من الصندوق. */
    suspend fun recordByHand(messageId: String, sender: String, amountText: String): AddOperationResult

    // ── §83 «اقرأ اللي فات؟» (`SmsHistory`) — الافتراضي: مفيش ──

    /** السؤال على البلد دي دلوقتي، أو null. */
    suspend fun historyQuestion(): SmsHistoryQuestion? = null

    /** «لأ، من دلوقتي بس». */
    suspend fun historyDecline(senders: List<String>) = Unit

    /** «أيوه» من أول يوم [from] (null = من أقدم رسالة). */
    suspend fun historyStart(senders: List<String>, from: IsoDate?, progress: (app.masroufy.usecase.SmsRunProgress) -> Unit = {}): SmsHistoryReport? = null

    /** قايمة «أكّد رسالة واحدة من كل شكل» دلوقتي. */
    suspend fun historyReport(): SmsHistoryReport? = null

    /** «أكّد الأشكال». r051: [progress] بعد كل صفحة من التسجيل (من خيط الشغل — الشاشة بتحطه في حالتها بس). */
    suspend fun confirmShapes(messageIds: List<String>, progress: (app.masroufy.usecase.SmsRunProgress) -> Unit = {}): SmsHistoryReport? = null

    // ── r05 — قايمة «بانتظار تأكيدك» و«ادفع بعدين» (كله بره خيط الشاشة — `SmsScreenExtras`). الافتراضي: مفيش ──

    /** 4b: نفس [overview] من صفحة أقدم في الصندوق ([skip] = يتخطّى أحدث [skip] رسالة مستنية) — والصندوق بيرجع لأحدث صفحة بعدها. */
    suspend fun overviewPage(record: Boolean, skip: Int): SmsOverview = overview(record)

    /**
     * 4a · 3b · 3c: المالك قرر على رسايل بعينها ⇒ بتتسجل (بالتصنيف اللي اختاره [categories]، و[isSalary] لرسالة «ده راتبك؟») وشكلها بيتعلّم،
     * وبعدها تشغيلة بتسجّل باقي رسايل **نفس الشكل** على طول.
     */
    suspend fun confirmMessages(ids: List<String>, categories: Map<String, Id> = emptyMap(), isSalary: Boolean? = null): SmsConfirmed =
        SmsConfirmed(0, 0, emptyList())
}

/** r05: [recorded] = اللي المالك أكّده · [sameShape] = اللي اتسجل معاه بنفس الشكل · [transactionIds] = عمليات المؤكَّد. */
data class SmsConfirmed(val recorded: Int, val sameShape: Int, val transactionIds: List<Id>)

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
