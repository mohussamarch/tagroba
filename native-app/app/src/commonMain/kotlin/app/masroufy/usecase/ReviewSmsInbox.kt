package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.SmsParseResult
import app.masroufy.core.rememberMerchant
import app.masroufy.port.CategoryRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.MerchantRepository
import app.masroufy.port.WalletRepository
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
 * **مراجعة S1:** الشاشة بتوزّع على المحافظ بآخر 4 أرقام الحساب زي الخلفية (§75-11 — `SmsSessionDraft`)، و«ده راتبك؟» لازم يترد قبل التسجيل.
 * الشاشة الحقيقية تتبني من `SmsLane.screen` (نفس التعلّم والآثار بتوع الخلفية).
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
    /**
     * S1 (§77-A · §75-2): أشكال الرسايل اللي اتعلّمت وردود «ده راتبك؟» للبلد دي — **لازم** (مراجعة S1: كان اختياري، والشاشة اللي
     * اتبنت من غيره ما كانتش بتعلّم ولا بتسأل، فولا رسالة كانت هتتسجل لوحدها أبدًا).
     */
    val learning: SmsLearning,
    /** مراجعة S1 (§75-11 على الشاشة): محافظ البلد — الرسالة اللي أرقام حسابها لمحفظة تانية بتتسجل هناك (`SmsRouting.kt`). */
    val wallets: WalletRepository,
    /** فشله ما يوقفش الحفظ على الجهاز. */
    val contribute: (suspend (MerchantContribution, Id) -> Unit)? = null,
)

/**
 * المُنشئ داخلي (مراجعة S1): الشاشة الحقيقية **لازم** تيجي من `SmsLane.screen` — هناك التعلّم والمحافظ وآثار رسايل البنك (`SmsSalaryEffect` ·
 * `OwnAccountByLast4Effect`) مطلوبين، فتجميع ناسي حاجة منهم ما بيعدّيش من غير ما يقع.
 */
class ReviewSmsInbox internal constructor(private val deps: ReviewSmsInboxDeps) {
    private var session: SmsSession? = null

    val available: Boolean get() = deps.inbox.available

    /** [route] = الشاشة (التوزيع بأرقام الحساب — §75-11)؛ التسجيل التلقائي بيوزّع بنفسه (`AutoRecordSms`) وبيدّي [only] لكل محفظة. */
    private suspend fun build(
        inbox: InboxView, target: SmsReviewTarget, only: ((InboxItem) -> Boolean)? = null, route: Boolean = true,
    ): SmsReview {
        val router = if (route) ScreenRouter.load(deps.wallets, deps.learning, target) else null
        val draft = SmsSessionDraft(LearnSnapshot(deps.learning))
        for (item in inbox.items) {
            if (only != null && !only(item)) continue
            when (val parsed = item.parsed) {
                is SmsParseResult.Ok -> {
                    val sender = smsSenderKey(item.sender)
                    draft.add(item.id, sender, parsed.row, router?.targetFor(parsed.row, sender) ?: target)
                }
                is SmsParseResult.Rejected -> draft.failed += SmsFailed(item.id, item.sender, item.receivedAt.take(10), parsed.reason)
            }
        }
        val categories = deps.categories.listAll()
        session = null
        val current = draft.build(deps.importer)
        session = current
        val lines = current?.lines().orEmpty()
        val newestFirst = Comparator<SmsReviewLine> { a, b -> if (a.date != b.date) b.date.compareTo(a.date) else b.lineNumber - a.lineNumber }
        return SmsReview(
            inbox.enabled, inbox.permission, inbox.senders, inbox.more,
            ready = lines.filter { it.state == MatchingState.NEW }.sortedWith(newestFirst),
            similar = lines.filter { it.state == MatchingState.SIMILAR || it.state == MatchingState.CONFLICT }.sortedWith(newestFirst),
            duplicates = lines.filter { it.state == MatchingState.DUPLICATE },
            failed = draft.failed,
            categories = categories,
        )
    }

    suspend fun load(target: SmsReviewTarget): SmsReview = build(deps.inbox.refresh(), target)

    /** رسايل [target] بس ([only] — مرسل واحد، ولكل محفظة رسايلها بآخر 4 أرقام §75-11) — التسجيل التلقائي بيوزّع بنفسه. */
    internal suspend fun loadFor(target: SmsReviewTarget, only: (InboxItem) -> Boolean): SmsReview =
        build(deps.inbox.refresh(), target, only, route = false)

    /** الصندوق بقارئ البلد دي (من غير بناء معاينة). */
    internal suspend fun inboxView(): InboxView = deps.inbox.refresh()

    suspend fun enable(senders: List<String>, target: SmsReviewTarget): SmsReview = build(deps.inbox.enable(senders), target)

    suspend fun disable(target: SmsReviewTarget): SmsReview = build(deps.inbox.disable(), target)

    /** «شيل»: الرسالة بتتشال من الصندوق من غير تسجيل — **وما بتعلّمش شكلها** (§77-A). */
    suspend fun dismiss(messageIds: List<String>, target: SmsReviewTarget): SmsReview = build(deps.inbox.dismiss(messageIds), target)

    /**
     * §75-2 — رد المالك على «ده راتبك؟» على الرسالة [messageId] (المرسل بتاعها في البلد دي؛ null = يتسأل تاني). بعدها الشاشة تتحمّل تاني
     * والرسالة تتسجل بـ«سجّل الكل» — وأيوه ⇒ «راتب» مؤكد (`SmsSalaryEffect`). رسالة مش في الصندوق ⇒ false.
     */
    suspend fun answerSalary(messageId: String, yes: Boolean?): Boolean {
        val item = deps.inbox.refresh().items.firstOrNull { it.id == messageId } ?: return false
        deps.learning.setSalaryAnswer(smsSenderKey(item.sender), yes)
        return true
    }

    /**
     * «سجّل الكل»: الجديد كله + الشبيه اللي المستخدم اختاره — كل رسالة في محفظتها. التصنيف اللي المستخدم اختاره بيتحفظ مؤكد.
     * اللي اتسجل والمكرر بيتشالوا من الصندوق؛ الباقي (المرفوض · الشبيه اللي ما اتختارش · «ده راتبك؟» اللي ما اترّدش) بيفضل.
     * §77-A: أشكال اللي اتسجل بتتعلّم.
     */
    suspend fun recordAll(categories: Map<Int, Id>, includeSimilar: List<Int>): Int =
        SMS_RECORD_LOCK.withLock { recordLocked(categories, includeSimilar).recorded }

    /**
     * «سجّل الكل» من غير القفل — للي ماسك [SMS_RECORD_LOCK] بالفعل (`AutoRecordSms`). [clearOnly] = التسجيل التلقائي (§72): الجديد
     * اللي **شكله معروف** بس ([app.masroufy.core.SmsShape.clear] — و§77-A: اتعلّم) — والباقي بيفضل في الصندوق مستني تأكيد المالك.
     * المكرر بيتشال في الحالتين (نفس الرسالة بالظبط اتسجلت قبل كده — مرجع `SMS:<بصمة>`، في أي محفظة). تسجيل المالك (مش [clearOnly])
     * بيعلّم الأشكال. [salaryAnswer] = رد المالك على «ده راتبك؟» للرسايل اللي عليها السؤال (بيتحفظ لمرسلها الأول)؛ null ⇒ ما بتتسجلش.
     */
    internal suspend fun recordLocked(
        categories: Map<Int, Id>, includeSimilar: List<Int>, clearOnly: Boolean = false, salaryAnswer: Boolean? = null,
    ): SmsRecordOutcome {
        val current = session ?: return SmsRecordOutcome(0, 0, emptyList())
        val allowed = includeSimilar.toSet()
        fun wanted(line: ImportPreviewLine) =
            line.state == MatchingState.NEW || (line.state == MatchingState.SIMILAR && line.row.lineNumber in allowed)
        fun asked(number: Int) = current.questionByLine[number] == AskKind.IS_SALARY
        // §75-2 (مراجعة S1): الرد اللي جه مع التأكيد بيتحفظ **قبل** الحفظ — أثر الراتب بيقراه وهو بيسجّل
        if (!clearOnly && salaryAnswer != null) {
            val senders = current.parts.flatMap { it.preview.lines }.filter(::wanted).map { it.row.lineNumber }.filter(::asked)
                .map { current.senderByLine.getValue(it) }.toSet()
            for (sender in senders) deps.learning.setSalaryAnswer(sender, salaryAnswer)
        }
        var recorded = 0
        var duplicates = 0
        val batches = mutableListOf<Id>()
        for (part in current.parts) {
            val selection = part.preview.lines.filter(::wanted).map { it.row.lineNumber }
                .filter { !clearOnly || current.shapeByLine[it]?.clear == true }
                .filter { salaryAnswer != null || !asked(it) }
            var partRecorded = 0
            if (selection.isNotEmpty()) {
                // عقد C0: التسجيل التلقائي مش تسجيل المالك (الآثار اللي بتفرّق بينهم بتبص على `byOwner`)
                val batch = deps.importer.commit(part.request.copy(byOwner = !clearOnly), part.preview, selection, categories)
                if (part.preview.previousBatch?.id != batch.id) {
                    partRecorded = selection.size
                    batches += batch.id
                }
            }
            val lines = if (partRecorded > 0) selection else emptyList()
            // §77-A: المالك سجّل ⇒ أشكال اللي **اتسجل فعلًا** بتتعلّم (قبل الشيل — لو الشيل وقع، التعلّم ما بيضيعش)
            if (!clearOnly) learnRecorded(current, part, lines)
            val partDuplicates = part.preview.lines.filter { it.state == MatchingState.DUPLICATE }.map { it.row.lineNumber }
            val done = lines + partDuplicates
            // بعد الحفظ بس: لو الشيل وقع، الرسالة بتفضل وبتطلع «مكررة» المرة الجاية (مش بتتسجل تاني — حتى لو راحت محفظة تانية)
            deps.inbox.imported(done.map { InboxLine(current.messageByLine.getValue(it), it) }, done)
            recorded += partRecorded
            duplicates += partDuplicates.size
        }
        session = null
        return SmsRecordOutcome(recorded, duplicates, batches)
    }

    /** الشكل الواضح بس (الكلمات العامة مالهاش بصمة — اختيار Claude §77-A)، لكل مرسل. */
    private suspend fun learnRecorded(current: SmsSession, part: SmsPart, lines: List<Int>) {
        val bySender = LinkedHashMap<String, MutableSet<String>>()
        for (number in lines) {
            val row = part.request.smsRows[number] ?: continue
            val key = row.learnKey?.takeIf { row.shape.clear } ?: continue
            bySender.getOrPut(current.senderByLine.getValue(number)) { mutableSetOf() } += key
        }
        for ((sender, keys) in bySender) deps.learning.learn(sender, keys)
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
