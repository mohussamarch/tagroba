package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsRow
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.transferPartyOf
import app.masroufy.port.SmsInboxPort

/**
 * **§77-A «وضع التعلّم»** (قرار المالك 2026-10-09): رسالة البنك بتتسجل لوحدها **بس بعد ما المالك يأكد أول رسالة من نفس الشكل** من نفس
 * المرسل في نفس البلد. أي شكل جديد (أو شكل اتغيّرت فيه كلمة) بيستنى تأكيد مرة. + **§75-2 «ده راتبك؟»**: رسالة راتب من غير اسم الجهة
 * بتتسأل مرة لكل مرسل في البلد، وبعد الرد ما بتتسألش تاني.
 * التخزين على الجهاز لصاحب الصندوق (`SmsInboxPort.learnedShapes` · `salaryAnswer`) — مش في فايربيز ولا النسخة الشاملة (زي تعلّم
 * التنبيهات §61)، فالجوال الجديد بيتعلّم كل شكل تاني مرة.
 */
class SmsLearning(val inbox: SmsInboxPort, val spaceId: String) {
    suspend fun learned(): Map<String, Set<String>> = inbox.learnedShapes(spaceId)

    suspend fun learn(sender: String, keys: Set<String>) = inbox.learnShapes(spaceId, sender, keys)

    suspend fun salaryAnswer(sender: String): Boolean? = inbox.salaryAnswer(spaceId, sender)

    /** §75-2: رد المالك على «ده راتبك؟» لرسايل [sender] (null = يتسأل تاني). */
    suspend fun setSalaryAnswer(sender: String, answer: Boolean?) = inbox.setSalaryAnswer(spaceId, sender, answer)
}

/** لقطة واحدة من [SmsLearning] لمعاينة واحدة (بنقرا التخزين مرة لكل مرسل بس). */
internal class LearnSnapshot(private val learning: SmsLearning) {
    private var learned: Map<String, Set<String>>? = null
    private val answers = mutableMapOf<String, Boolean?>()

    suspend fun isLearned(sender: String, key: String?): Boolean {
        val all = learned ?: learning.learned().also { learned = it }
        return key != null && key in all[sender].orEmpty()
    }

    /** §75-2: السؤال «ده راتبك؟» على الصف ده — رسالة راتب داخلة **من غير جهة** ([smsRowParty] = null) ولسه ما اتسألش عن المرسل. */
    suspend fun salaryQuestion(row: SmsRow, sender: String): AskKind? {
        if (row.kind != SmsKind.SALARY || row.direction != Direction.IN || smsRowParty(row) != null) return null
        val answer = if (sender in answers) answers[sender] else learning.salaryAnswer(sender).also { answers[sender] = it }
        return if (answer == null) AskKind.IS_SALARY else null
    }
}

/**
 * ليه صف **شكله واضح** يستنى بعد وضع التعلّم، أو null: الأسباب القديمة الأول (حساب تاني · استرداد · سحب … — `SmsWaitReasons.kt`) وهي
 * بتفضل حتى بعد التعلّم · بعدها سؤال «ده راتبك؟» · بعدها **شكل لسه ما اتأكدش** ([TextKey.SMS_WAIT_NEW_SHAPE]).
 */
internal suspend fun learnWaitOf(row: SmsRow, target: SmsReviewTarget, sender: String, question: AskKind?, snapshot: LearnSnapshot?): TextKey? =
    waitReasonOf(row, target)
        ?: if (question == AskKind.IS_SALARY) TextKey.SMS_WAIT_IS_SALARY
        else if (snapshot != null && !snapshot.isLearned(sender, row.learnKey)) TextKey.SMS_WAIT_NEW_SHAPE
        else null

/**
 * الطرف التاني في رسالة البنك **زي ما هيبان على العملية** (`transferPartyOf` على عملية بنفس الوصف والاتجاه — نفس اللي
 * `ImportStatement` بيبنيه) — عشان سؤال الراتب يبقى على نفس قاعدة «مين اللي حوّل» (§64).
 */
internal fun smsRowParty(row: SmsRow): TransferPartyRef? = transferPartyOf(
    Transaction(
        id = "probe", occurredAt = row.date, datePrecision = "day", sourceOrder = row.lineNumber, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = row.direction, amountMinor = row.amountMinor, currency = Currency.SAR,
        categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false,
        createdAt = "", updatedAt = "", rawDescription = row.description, rawMerchantName = row.merchantName,
    ),
)
