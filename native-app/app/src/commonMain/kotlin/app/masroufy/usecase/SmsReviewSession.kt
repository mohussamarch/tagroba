package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.CategorizationSource
import app.masroufy.core.Id
import app.masroufy.core.ImportSourceType
import app.masroufy.core.SchemaId
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsRow
import app.masroufy.core.SmsShape
import app.masroufy.core.TextKey
import app.masroufy.core.jsTrim
import app.masroufy.core.smsRowsJson
import app.masroufy.core.toParsedRow
import app.masroufy.core.uiText

/**
 * جلسة شاشة رسايل البنك (`ReviewSmsInbox`) — اتنقلت هنا عشان حد الـ300 سطر. **مراجعة S1 (§75-11):** الجلسة بقت **معاينة لكل محفظة**:
 * الرسالة اللي أرقام حسابها لمحفظة تانية بتتسجل هناك (`SmsRouting.kt` — `ScreenRouter`)، والباقي في المحفظة اللي الشاشة اتفتحت عليها.
 * أرقام السطور فريدة في الصندوق كله (ترتيب الرسالة)، فالتصنيف اللي المالك اختاره لسطر بيوصل لمعاينة محفظته.
 */

/** معاينة محفظة واحدة: طلب الاستيراد ونتيجته. */
internal class SmsPart(val request: ImportRequest, val preview: ImportPreview)

/** جلسة «سجّل الكل»: معاينة لكل محفظة + لكل سطر رسالته ومرسله وشكله وسبب انتظاره وسؤاله. */
internal class SmsSession(
    val parts: List<SmsPart>,
    val messageByLine: Map<Int, String>,
    val shapeByLine: Map<Int, SmsShape>,
    val senderByLine: Map<Int, String>,
    val questionByLine: Map<Int, AskKind>,
    private val waitByLine: Map<Int, TextKey>,
) {
    fun lines(): List<SmsReviewLine> = parts.flatMap { part -> part.preview.lines.map { line -> reviewLine(part, line) } }

    private fun reviewLine(part: SmsPart, line: ImportPreviewLine): SmsReviewLine {
        val number = line.row.lineNumber
        val shape = shapeByLine[number] ?: SmsShape.KeywordFallback
        val wait = if (shape.clear) null else waitByLine[number] ?: TextKey.SMS_WAIT_UNKNOWN_SHAPE
        return SmsReviewLine(
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
            kind = part.request.smsRows[number]?.kind ?: SmsKind.OTHER,
            waitReason = wait,
            question = questionByLine[number],
            walletId = part.request.walletId,
            fee = part.request.smsRows[number]?.let(::smsFeeToRecord),
        )
    }
}

/** بيجمّع صفوف الرسايل لكل محفظة وأسباب انتظارها ([add])، وبعدين معاينة لكل محفظة ([build]). */
internal class SmsSessionDraft(private val snapshot: LearnSnapshot) {
    private val rowsByWallet = LinkedHashMap<Id, Pair<SmsReviewTarget, MutableList<SmsRow>>>()
    private val messageByLine = mutableMapOf<Int, String>()
    private val shapeByLine = mutableMapOf<Int, SmsShape>()
    private val senderByLine = mutableMapOf<Int, String>()
    private val waitByLine = mutableMapOf<Int, TextKey>()
    private val questionByLine = mutableMapOf<Int, AskKind>()
    val failed = mutableListOf<SmsFailed>()

    /**
     * صف رسالة مفهومة رايح [target]. الجولة السادسة: حساب تاني ⇒ تستنى. الجولة السابعة: الاسترداد (§75-6) والسحب (§75-4) بيستنوا. S1: «ده
     * راتبك؟» (§75-2) وبعدها الشكل اللي لسه ما اتأكدش (§77-A — `SmsLearning.kt`).
     */
    suspend fun add(messageId: String, sender: String, row: SmsRow, target: SmsReviewTarget) {
        rowsByWallet.getOrPut(target.walletId) { target to mutableListOf() }.second += row
        messageByLine[row.lineNumber] = messageId
        senderByLine[row.lineNumber] = sender
        val question = snapshot.salaryQuestion(row, sender)?.also { questionByLine[row.lineNumber] = it }
        val wait = if (row.shape.clear) learnWaitOf(row, target, sender, question, snapshot) else TextKey.SMS_WAIT_UNKNOWN_SHAPE
        shapeByLine[row.lineNumber] = if (wait != null) SmsShape.KeywordFallback else row.shape
        if (wait != null) waitByLine[row.lineNumber] = wait
    }

    /** معاينة لكل محفظة (بترتيب أول رسالة ليها) — null لو مفيش ولا رسالة مفهومة. */
    suspend fun build(importer: ImportStatement): SmsSession? {
        if (rowsByWallet.isEmpty()) return null
        val parts = rowsByWallet.values.map { (target, rows) ->
            val request = smsRequest(target, rows)
            SmsPart(request, importer.preview(request))
        }
        return SmsSession(parts, messageByLine, shapeByLine, senderByLine, questionByLine, waitByLine)
    }
}

/** رسايل محفظة واحدة كـ«ملف» استيراد — نفس خط الكشف (منع التكرار · التصنيف · الحفظ). */
private fun smsRequest(target: SmsReviewTarget, rows: List<SmsRow>) = ImportRequest(
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
