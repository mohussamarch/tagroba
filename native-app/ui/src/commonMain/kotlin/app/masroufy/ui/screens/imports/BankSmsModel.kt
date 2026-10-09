package app.masroufy.ui.screens.imports

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.SmsParseResult
import app.masroufy.core.Transaction
import app.masroufy.core.jsTrim
import app.masroufy.port.smsSenderKey
import app.masroufy.usecase.SmsReviewLine

/**
 * حالة شاشة «رسائل البنك» (`BankSms` + `SmsWaiting`) من نتايج حالات الاستخدام — **من غير حساب فلوس**: المبالغ زي ما الرسالة قالتها
 * (بالوحدة الصغرى)، والعدّ عدّ رسايل بس.
 */
enum class SmsStatus {
    /** القراءة شغالة والإذن موجود. */
    READING,

    /** المالك وقّف القراءة. */
    OFF,

    /** الإذن اتسحب من إعدادات الجوال. */
    PERMISSION,

    /** الجهاز ما بيقراش رسايل (الآيفون) ⇒ «الصق رسالة». */
    UNAVAILABLE,
}

/** بنك (مرسل) رسايله مفهومة بس مالوش محفظة — المالك يختار مرة واحدة. */
data class BankWaitingUi(val sender: String, val count: Int, val samples: List<SampleUi>)

data class SampleUi(val merchant: String, val amountMinor: Halalas, val direction: Direction)

/** رسالة مفهومة مستنية تأكيدك ([reason] = ليه ما اتسجلتش لوحدها) أو شبه عملية موجودة ([conflict] = نفس المرجع بتفاصيل تانية — ما بتتسجلش). */
data class WaitLineUi(
    val messageId: String,
    val sender: String,
    val merchant: String,
    val date: IsoDate,
    val amountMinor: Halalas,
    val direction: Direction,
    val categoryId: Id?,
    val reason: String?,
    val conflict: Boolean = false,
)

/** رسالة ما اتفهمتش: سببها ونصها (لو موجود). [foreign] = بعملة أجنبية (§75-12 — السؤال عن المبلغ المحلي لسه ما اتبناش). */
data class FailedUi(val messageId: String, val sender: String, val date: String, val reason: String, val body: String?, val foreign: Boolean)

/** عملية اتسجلت من رسالة: محتاجة تأكيد تصنيف مقترح ([RecordedNeed.CONFIRM]) أو تصنيف لمتجر جديد ([RecordedNeed.CATEGORIZE]). */
enum class RecordedNeed { NONE, CONFIRM, CATEGORIZE }

data class RecordedUi(
    val id: Id,
    val merchant: String,
    val categoryId: Id?,
    val amountMinor: Halalas,
    val currency: Currency,
    val direction: Direction,
    val need: RecordedNeed,
    /** اتسجلت دلوقتي ⇒ لمعة نعناعي (§71). */
    val sheen: Boolean,
)

data class BankSmsUi(
    val status: SmsStatus,
    val banks: List<BankWaitingUi> = emptyList(),
    val confirm: List<WaitLineUi> = emptyList(),
    val similar: List<WaitLineUi> = emptyList(),
    val failed: List<FailedUi> = emptyList(),
    val recorded: List<RecordedUi> = emptyList(),
) {
    /** عدد الرسايل المستنية قرارك. */
    val waitingCount: Int get() = banks.sumOf { it.count } + confirm.size + similar.size + failed.size

    /** الحالة «فاضي»: مفيش مستني ولا حاجة اتسجلت النهارده. */
    val isEmpty: Boolean get() = waitingCount == 0 && recorded.isEmpty()

    /** القايمة والمستني بيظهروا (عادي · الإذن مسحوب) — الآيفون والمتوقف ليهم سطرهم بس. */
    val live: Boolean get() = status == SmsStatus.READING || status == SmsStatus.PERMISSION
}

/** [overview] = null ⇒ الجهاز ما بيقراش رسايل. [fresh] = اللي اتسجل في الفتحة دي (لمعة). */
fun bankSmsUi(overview: SmsOverview?, recordedToday: List<Transaction>, fresh: Set<Id>): BankSmsUi {
    if (overview == null) return BankSmsUi(SmsStatus.UNAVAILABLE)
    val inbox = overview.inbox
    val status = when {
        !inbox.enabled -> SmsStatus.OFF
        !inbox.permission -> SmsStatus.PERMISSION
        else -> SmsStatus.READING
    }
    val banks = overview.unmapped.map { u ->
        val mine = inbox.items.filter { smsSenderKey(it.sender) == smsSenderKey(u.sender) }
        val samples = mine.mapNotNull { (it.parsed as? SmsParseResult.Ok)?.row }.map { SampleUi(jsTrim(it.merchantName), it.amountMinor, it.direction) }
        BankWaitingUi(mine.firstOrNull()?.sender ?: u.sender, u.messages, samples)
    }
    val confirm = overview.reviews.flatMap { r -> r.review.ready.map { it.toUi(r.sender, it.confirmReason) } }
    val similar = overview.reviews.flatMap { r -> r.review.similar.map { it.toUi(r.sender, it.reason, conflict = it.state == MatchingState.CONFLICT) } }
    val bodies = inbox.messages.associate { it.id to it.body }
    val failed = inbox.items.mapNotNull { item ->
        val rejected = item.parsed as? SmsParseResult.Rejected ?: return@mapNotNull null
        FailedUi(item.id, item.sender, item.receivedAt.take(10), rejected.reason, bodies[item.id], foreign = rejected.foreign != null || rejected.foreignUnread != null)
    }
    val recorded = recordedToday.map { it.toRecorded(it.id in fresh) }
    return BankSmsUi(status, banks, confirm, similar, failed, recorded)
}

private fun SmsReviewLine.toUi(sender: String, reason: String?, conflict: Boolean = false) =
    WaitLineUi(messageId, sender, merchant, date, amountMinor, direction, categoryId, reason, conflict)

private fun Transaction.toRecorded(sheen: Boolean) = RecordedUi(
    id = id,
    merchant = jsTrim(rawMerchantName ?: rawDescription ?: ""),
    categoryId = categoryId,
    amountMinor = amountMinor,
    currency = currency,
    direction = observedDirection,
    need = when {
        categoryConfirmed -> RecordedNeed.NONE
        categoryId != null -> RecordedNeed.CONFIRM
        else -> RecordedNeed.CATEGORIZE
    },
    sheen = sheen,
)

/** التصنيفات اللي ينفع تتختار (الظاهرة بس — المخفي ما بيظهرش في الاختيار، §28). */
fun pickableCategories(all: List<Category>): List<Category> = all.filter { it.active }
