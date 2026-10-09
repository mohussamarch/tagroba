package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.CategorizationSource
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.SchemaId
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.SmsShape
import app.masroufy.core.TextKey
import app.masroufy.core.jsTrim
import app.masroufy.core.rememberMerchant
import app.masroufy.core.smsRowsJson
import app.masroufy.core.toParsedRow
import app.masroufy.core.uiText
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
 * الجولة الرابعة: الجديد اللي اتفهم من كلمات عامة بس (`SmsShape.KeywordFallback`) ما بيتسجلش في الخلفية — بيفضل في `ready`
 * جاهز ومعاه سببه ([SmsReviewLine.confirmReason])، و«سجّل الكل» هنا هو التأكيد.
 * **§77-A «وضع التعلّم»** (S1): الشكل الواضح بيتسجل لوحده **بس لو المالك أكّد قبل كده رسالة بنفس الشكل من نفس المرسل** (`SmsLearning.kt`)؛
 * و«سجّل الكل» (تسجيل المالك) بيعلّم أشكال الرسايل اللي اتسجلت فعلًا (الجديد والشبيه اللي اختاره) — مش المكرر ولا اللي اتشال ولا الكلمات العامة.
 */

/**
 * قفل واحد للبرنامج كله على **تسجيل رسايل البنك** (الخلفية + «سجّل الكل»): منع التكرار في الاستيراد «اقرا وبعدين اكتب»، فتسجيلين
 * في نفس اللحظة كانوا ممكن يشوفوا نفس الرسالة جديدة ويسجلوها مرتين. مش قابل للدخول مرتين (`Mutex`) ⇒ جوه القفل بننادي [ReviewSmsInbox.recordLocked].
 */
internal val SMS_RECORD_LOCK = Mutex()

data class ReviewSmsInboxDeps(
    val inbox: ManageSmsInbox,
    val importer: ImportStatement,
    val merchants: MerchantRepository,
    val categories: CategoryRepository,
    val ids: IdGenerator,
    /** فشله ما يوقفش الحفظ على الجهاز. */
    val contribute: (suspend (MerchantContribution, Id) -> Unit)? = null,
    /**
     * S1 (§77-A · §75-2): أشكال الرسايل اللي اتعلّمت وردود «ده راتبك؟» للبلد دي. الشاشة لازم تدّيها (عشان «سجّل الكل» يعلّم والسبب
     * يبان)؛ null = زي قبل وضع التعلّم (ملفات المرجع). التسجيل التلقائي (`AutoRecordSms`) بيدّي بتاعه دايمًا.
     */
    val learning: SmsLearning? = null,
)

class ReviewSmsInbox(private val deps: ReviewSmsInboxDeps) {
    private class Session(
        val request: ImportRequest,
        val preview: ImportPreview,
        val messageByLine: Map<Int, String>,
        val shapeByLine: Map<Int, SmsShape>,
        val senderByLine: Map<Int, String>,
        val learning: SmsLearning?,
    )

    private var session: Session? = null

    val available: Boolean get() = deps.inbox.available

    private suspend fun build(
        inbox: InboxView, target: SmsReviewTarget, learning: SmsLearning? = deps.learning, only: ((InboxItem) -> Boolean)? = null,
    ): SmsReview {
        val rows = mutableListOf<SmsRow>()
        val messageByLine = mutableMapOf<Int, String>()
        val shapeByLine = mutableMapOf<Int, SmsShape>()
        val senderByLine = mutableMapOf<Int, String>()
        val waitByLine = mutableMapOf<Int, TextKey>()
        val questionByLine = mutableMapOf<Int, AskKind>()
        val failed = mutableListOf<SmsFailed>()
        val snapshot = learning?.let(::LearnSnapshot)
        for (item in inbox.items) {
            if (only != null && !only(item)) continue
            when (val parsed = item.parsed) {
                is SmsParseResult.Ok -> {
                    val row = parsed.row
                    val sender = smsSenderKey(item.sender)
                    rows += row
                    messageByLine[row.lineNumber] = item.id
                    senderByLine[row.lineNumber] = sender
                    // الجولة السادسة: حساب تاني ⇒ تستنى. الجولة السابعة: الاسترداد (§75-6) والسحب (§75-4) بيستنوا. S1: «ده راتبك؟» (§75-2)
                    // وبعدها الشكل اللي لسه ما اتأكدش (§77-A — `SmsLearning.kt`)
                    val question = snapshot?.salaryQuestion(row, sender)?.also { questionByLine[row.lineNumber] = it }
                    val wait = if (row.shape.clear) learnWaitOf(row, target, sender, question, snapshot) else TextKey.SMS_WAIT_UNKNOWN_SHAPE
                    shapeByLine[row.lineNumber] = if (wait != null) SmsShape.KeywordFallback else row.shape
                    if (wait != null) waitByLine[row.lineNumber] = wait
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
            smsRows = rows.associateBy { it.lineNumber },
        )
        val preview = deps.importer.preview(request)
        session = Session(request, preview, messageByLine, shapeByLine, senderByLine, learning)
        val lines = preview.lines.map { line ->
            val number = line.row.lineNumber
            val shape = shapeByLine[number] ?: SmsShape.KeywordFallback
            val wait = if (shape.clear) null else waitByLine[number] ?: TextKey.SMS_WAIT_UNKNOWN_SHAPE
            SmsReviewLine(
                messageId = messageByLine.getValue(number),
                lineNumber = number,
                date = line.row.date,
                merchant = jsTrim(line.row.merchantName),
                amountMinor = line.row.amountMinor,
                direction = line.row.direction,
                categoryId = line.categoryId?.takeIf { it.isNotEmpty() },
                remembered = line.categorySource == CategorizationSource.VERIFIED_MERCHANT,
                state = line.state,
                reason = line.reason,
                shape = shape,
                confirmReason = wait?.let { uiText(it) },
                kind = request.smsRows[number]?.kind ?: SmsKind.OTHER,
                waitReason = wait,
                question = questionByLine[number],
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

    /**
     * رسايل [target] بس ([only] — مرسل واحد، ولكل محفظة رسايلها بآخر 4 أرقام §75-11) بالتعلّم اللي [learning] بيقوله (التسجيل التلقائي
     * بيدّي بتاعه دايمًا — §77-A ما ينفعش يتقفل من الشاشة).
     */
    internal suspend fun loadFor(target: SmsReviewTarget, learning: SmsLearning?, only: (InboxItem) -> Boolean): SmsReview =
        build(deps.inbox.refresh(), target, learning, only)

    /** الصندوق بقارئ البلد دي (من غير بناء معاينة). */
    internal suspend fun inboxView(): InboxView = deps.inbox.refresh()

    suspend fun enable(senders: List<String>, target: SmsReviewTarget): SmsReview = build(deps.inbox.enable(senders), target)

    suspend fun disable(target: SmsReviewTarget): SmsReview = build(deps.inbox.disable(), target)

    /** «شيل»: الرسالة بتتشال من الصندوق من غير تسجيل — **وما بتعلّمش شكلها** (§77-A). */
    suspend fun dismiss(messageIds: List<String>, target: SmsReviewTarget): SmsReview = build(deps.inbox.dismiss(messageIds), target)

    /**
     * «سجّل الكل»: الجديد كله + الشبيه اللي المستخدم اختاره. التصنيف اللي اختاره المستخدم بيتحفظ مؤكد.
     * اللي اتسجل والمكرر بيتشالوا من الصندوق؛ الباقي (المرفوض والشبيه اللي ما اتختارش) بيفضل. §77-A: أشكال اللي اتسجل بتتعلّم.
     */
    suspend fun recordAll(categories: Map<Int, Id>, includeSimilar: List<Int>): Int =
        SMS_RECORD_LOCK.withLock { recordLocked(categories, includeSimilar).recorded }

    /**
     * «سجّل الكل» من غير القفل — للي ماسك [SMS_RECORD_LOCK] بالفعل (`AutoRecordSms`). [clearOnly] = التسجيل التلقائي (§72): الجديد
     * اللي **شكله معروف** بس ([SmsShape.clear] — و§77-A: اتعلّم) — والباقي بيفضل في الصندوق مستني تأكيد المالك.
     * المكرر بيتشال في الحالتين (نفس الرسالة بالظبط اتسجلت قبل كده — مرجع `SMS:<بصمة>`). تسجيل المالك (مش [clearOnly]) بيعلّم الأشكال.
     */
    internal suspend fun recordLocked(categories: Map<Int, Id>, includeSimilar: List<Int>, clearOnly: Boolean = false): SmsRecordOutcome {
        val current = session ?: return SmsRecordOutcome(0, 0, null)
        val allowed = includeSimilar.toSet()
        fun clear(line: Int) = current.shapeByLine[line]?.clear == true
        val selection = current.preview.lines
            .filter { it.state == MatchingState.NEW || (it.state == MatchingState.SIMILAR && it.row.lineNumber in allowed) }
            .map { it.row.lineNumber }
            .filter { !clearOnly || clear(it) }
        var recorded = 0
        var batchId: Id? = null
        if (selection.isNotEmpty()) {
            // عقد C0: التسجيل التلقائي مش تسجيل المالك (الآثار اللي بتفرّق بينهم بتبص على `byOwner`)
            val batch = deps.importer.commit(current.request.copy(byOwner = !clearOnly), current.preview, selection, categories)
            if (current.preview.previousBatch?.id != batch.id) {
                recorded = selection.size
                batchId = batch.id
            }
        }
        val lines = if (recorded > 0) selection else emptyList()
        // §77-A: المالك سجّل ⇒ أشكال اللي **اتسجل فعلًا** بتتعلّم (قبل الشيل — لو الشيل وقع، التعلّم ما بيضيعش)
        if (!clearOnly) current.learning?.let { learnRecorded(current, lines, it) }
        val duplicates = current.preview.lines.filter { it.state == MatchingState.DUPLICATE }.map { it.row.lineNumber }
        val done = lines + duplicates
        // بعد الحفظ بس: لو الشيل وقع، الرسالة بتفضل وبتطلع «مكررة» المرة الجاية (مش بتتسجل تاني)
        deps.inbox.imported(done.map { InboxLine(current.messageByLine.getValue(it), it) }, done)
        session = null
        return SmsRecordOutcome(recorded, duplicates.size, batchId)
    }

    /** الشكل الواضح بس (الكلمات العامة مالهاش بصمة — اختيار Claude §77-A)، لكل مرسل. */
    private suspend fun learnRecorded(current: Session, lines: List<Int>, learning: SmsLearning) {
        val bySender = LinkedHashMap<String, MutableSet<String>>()
        for (number in lines) {
            val row = current.request.smsRows[number] ?: continue
            val key = row.learnKey?.takeIf { row.shape.clear } ?: continue
            bySender.getOrPut(current.senderByLine.getValue(number)) { mutableSetOf() } += key
        }
        for ((sender, keys) in bySender) learning.learn(sender, keys)
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
