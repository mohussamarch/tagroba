package app.masroufy.usecase

import app.masroufy.core.CategorizationSource
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.SchemaId
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.jsTrim
import app.masroufy.core.rememberMerchant
import app.masroufy.core.smsRowsJson
import app.masroufy.core.toParsedRow
import app.masroufy.port.CategoryRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.MerchantRepository
import app.masroufy.port.smsSenderKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * شاشة رسايل البنك — نقل `reviewSmsInbox.ts` (OVERRIDES §36): قايمة واحدة فيها كل عملية بتصنيفها المقترح،
 * وزرار واحد «سجّل الكل». منع التكرار والتصنيف هما نفس خط استيراد الكشف.
 * **§72 (قرار المالك 2026-10-08) لغى «مفيش حاجة بتتسجل من غير ضغطة»:** الجديد بيتسجل لوحده في الخلفية (`AutoRecordSms`) بنفس
 * الكلاس ده، والشاشة بتعرض اللي مستني بس. الاتنين بيسجّلوا تحت [SMS_RECORD_LOCK].
 */

/**
 * قفل واحد للبرنامج كله على **تسجيل رسايل البنك** (الخلفية + «سجّل الكل»): منع التكرار في الاستيراد «اقرا وبعدين اكتب»، فتسجيلين
 * في نفس اللحظة كانوا ممكن يشوفوا نفس الرسالة جديدة ويسجلوها مرتين. مش قابل للدخول مرتين (`Mutex`) ⇒ جوه القفل بننادي [ReviewSmsInbox.recordLocked].
 */
internal val SMS_RECORD_LOCK = Mutex()

/** نتيجة التسجيل لحالات الاستخدام — [batchId] بس لو اتسجل حاجة فعلًا. */
internal class SmsRecordOutcome(val recorded: Int, val duplicates: Int, val batchId: Id?)

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

/**
 * `accountIdentity` = اسم المحفظة — نطاق تفرّد المرجع، زي شاشة الاستيراد. [currency] = عملة المحفظة: من غيرها رسايل QNB مصر
 * كانت هتتسجل بالريال (الاستيراد افتراضيه ريال) — اتكشف في جلسة 31. الافتراضي ريال عشان ملفات المرجع والتطبيق الحالي.
 */
data class SmsReviewTarget(val walletId: Id, val accountIdentity: String, val currency: Currency = Currency.SAR)

/** اللي بيترفع للقايمة المشتركة (OVERRIDES §25) — المصروف بس. */
data class MerchantContribution(val economicKind: EconomicKind, val observedDirection: Direction, val rawMerchantName: String)

data class ReviewSmsInboxDeps(
    val inbox: ManageSmsInbox,
    val importer: ImportStatement,
    val merchants: MerchantRepository,
    val categories: CategoryRepository,
    val ids: IdGenerator,
    /** فشله ما يوقفش الحفظ على الجهاز. */
    val contribute: (suspend (MerchantContribution, Id) -> Unit)? = null,
)

class ReviewSmsInbox(private val deps: ReviewSmsInboxDeps) {
    private class Session(val request: ImportRequest, val preview: ImportPreview, val messageByLine: Map<Int, String>)

    private var session: Session? = null

    val available: Boolean get() = deps.inbox.available

    private suspend fun build(inbox: InboxView, target: SmsReviewTarget, only: ((InboxItem) -> Boolean)? = null): SmsReview {
        val rows = mutableListOf<SmsRow>()
        val messageByLine = mutableMapOf<Int, String>()
        val failed = mutableListOf<SmsFailed>()
        for (item in inbox.items) {
            if (only != null && !only(item)) continue
            when (val parsed = item.parsed) {
                is SmsParseResult.Ok -> {
                    rows += parsed.row
                    messageByLine[parsed.row.lineNumber] = item.id
                }
                is SmsParseResult.Rejected -> failed += SmsFailed(item.id, item.sender, item.receivedAt.take(10), parsed.reason)
            }
        }
        val categories = deps.categories.listAll()
        session = null
        fun review(ready: List<SmsReviewLine>, similar: List<SmsReviewLine>, duplicates: List<SmsReviewLine>) =
            SmsReview(inbox.enabled, inbox.permission, inbox.senders, inbox.more, ready, similar, duplicates, failed, categories)
        if (rows.isEmpty()) return review(emptyList(), emptyList(), emptyList())

        val request = ImportRequest(
            fileName = "bank-sms.json",
            content = smsRowsJson(rows),
            accountIdentity = target.accountIdentity,
            sourceType = ImportSourceType.SMS,
            walletId = target.walletId,
            schema = SchemaId.SMS,
            parsedRows = rows.map { it.toParsedRow() },
            currency = target.currency,
        )
        val preview = deps.importer.preview(request)
        session = Session(request, preview, messageByLine)
        val lines = preview.lines.map { line ->
            SmsReviewLine(
                messageId = messageByLine.getValue(line.row.lineNumber),
                lineNumber = line.row.lineNumber,
                date = line.row.date,
                merchant = jsTrim(line.row.merchantName),
                amountMinor = line.row.amountMinor,
                direction = line.row.direction,
                categoryId = line.categoryId?.takeIf { it.isNotEmpty() },
                remembered = line.categorySource == CategorizationSource.VERIFIED_MERCHANT,
                state = line.state,
                reason = line.reason,
            )
        }
        val newestFirst = Comparator<SmsReviewLine> { a, b -> if (a.date != b.date) b.date.compareTo(a.date) else b.lineNumber - a.lineNumber }
        return review(
            ready = lines.filter { it.state == MatchingState.NEW }.sortedWith(newestFirst),
            similar = lines.filter { it.state == MatchingState.SIMILAR || it.state == MatchingState.CONFLICT }.sortedWith(newestFirst),
            duplicates = lines.filter { it.state == MatchingState.DUPLICATE },
        )
    }

    suspend fun load(target: SmsReviewTarget): SmsReview = build(deps.inbox.refresh(), target)

    /** رسايل مرسل واحد بس (كل بنك ليه محفظته — §72 رد المالك ١). [senderKey] بعد `smsSenderKey`. */
    internal suspend fun loadSender(target: SmsReviewTarget, senderKey: String): SmsReview =
        build(deps.inbox.refresh(), target) { smsSenderKey(it.sender) == senderKey }

    /** الصندوق بقارئ البلد دي (من غير بناء معاينة). */
    internal suspend fun inboxView(): InboxView = deps.inbox.refresh()

    suspend fun enable(senders: List<String>, target: SmsReviewTarget): SmsReview = build(deps.inbox.enable(senders), target)

    suspend fun disable(target: SmsReviewTarget): SmsReview = build(deps.inbox.disable(), target)

    suspend fun dismiss(messageIds: List<String>, target: SmsReviewTarget): SmsReview = build(deps.inbox.dismiss(messageIds), target)

    /**
     * «سجّل الكل»: الجديد كله + الشبيه اللي المستخدم اختاره. التصنيف اللي اختاره المستخدم بيتحفظ مؤكد.
     * اللي اتسجل والمكرر بيتشالوا من الصندوق؛ الباقي (المرفوض والشبيه اللي ما اتختارش) بيفضل.
     */
    suspend fun recordAll(categories: Map<Int, Id>, includeSimilar: List<Int>): Int =
        SMS_RECORD_LOCK.withLock { recordLocked(categories, includeSimilar).recorded }

    /** «سجّل الكل» من غير القفل — للي ماسك [SMS_RECORD_LOCK] بالفعل (`AutoRecordSms`). */
    internal suspend fun recordLocked(categories: Map<Int, Id>, includeSimilar: List<Int>): SmsRecordOutcome {
        val current = session ?: return SmsRecordOutcome(0, 0, null)
        val allowed = includeSimilar.toSet()
        val selection = current.preview.lines
            .filter { it.state == MatchingState.NEW || (it.state == MatchingState.SIMILAR && it.row.lineNumber in allowed) }
            .map { it.row.lineNumber }
        var recorded = 0
        var batchId: Id? = null
        if (selection.isNotEmpty()) {
            val batch = deps.importer.commit(current.request, current.preview, selection, categories)
            if (current.preview.previousBatch?.id != batch.id) {
                recorded = selection.size
                batchId = batch.id
            }
        }
        val lines = if (recorded > 0) selection else emptyList()
        val duplicates = current.preview.lines.filter { it.state == MatchingState.DUPLICATE }.map { it.row.lineNumber }
        val done = lines + duplicates
        // بعد الحفظ بس: لو الشيل وقع، الرسالة بتفضل وبتطلع «مكررة» المرة الجاية (مش بتتسجل تاني)
        deps.inbox.imported(done.map { InboxLine(current.messageByLine.getValue(it), it) }, done)
        session = null
        return SmsRecordOutcome(recorded, duplicates.size, batchId)
    }

    /** «أيوه افتكره»: تصنيف المحل بيتثبت على الجهاز، ويترفع اقتراح للقايمة المشتركة لو مصروف. */
    suspend fun remember(merchantName: String, categoryId: Id, direction: Direction): Boolean {
        val merchant = rememberMerchant(deps.merchants.listAll(), merchantName, categoryId, deps.ids.next("merchant")) ?: return false
        deps.merchants.saveMany(listOf(merchant))
        val contribute = deps.contribute
        if (direction == Direction.OUT && contribute != null) {
            try {
                contribute(MerchantContribution(EconomicKind.PURCHASE, Direction.OUT, merchantName), categoryId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // الاقتراح المشترك اختياري — المحل اتحفظ على الجهاز خلاص
            }
        }
        return true
    }
}
