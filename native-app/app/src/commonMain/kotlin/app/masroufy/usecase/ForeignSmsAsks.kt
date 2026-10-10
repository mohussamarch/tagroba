package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ImportSourceType
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.PendingAsk
import app.masroufy.core.SchemaId
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.assertHalalas
import app.masroufy.core.currencyDecimals
import app.masroufy.core.redactSms
import app.masroufy.core.smsMessageKind
import app.masroufy.core.smsMessageReference
import app.masroufy.core.smsRowsJson
import app.masroufy.core.smsSafeText
import app.masroufy.core.smsSourceReference
import app.masroufy.core.toParsedRow
import app.masroufy.core.uiText
import app.masroufy.core.writtenForeignMinor
import app.masroufy.port.QueuedSms
import app.masroufy.port.smsSenderKey
import kotlinx.coroutines.sync.withLock

/**
 * §75-12 (قرار المالك ✗ — غير المقترح): «شراء بعملة أجنبية ⇒ يتسجل ويسأل عن المبلغ بالعملة المحلية». القارئ بيرفض الرسالة الأجنبية
 * ومعاها كل اللي اتقري (`SmsForeignPending` / `SmsForeignUnread`) فبتفضل في الصندوق — **ولا عملية بتتسجل بمبلغ مخترع قبل الإجابة**
 * (قاعدة 10). هنا:
 * - [list]: الأسئلة (رسالة لكل سؤال، في بلدها بس) ومعاها المبلغ الأجنبي بكسور عملته (أو المكتوب زي ما هو) والمقابل المحلي المكتوب
 *   **اقتراح** بس ([ForeignAsk.localSuggestion]). نوعها بعد §77-D (الأجنبي اللي **رجع** = `RETURNED`).
 * - [answerForeign]: المالك كتب المبلغ المحلي ⇒ عملية واحدة بنفس خط رسايل البنك (منع التكرار · التصنيف · زون التحويلات · آثار وقت
 *   التسجيل — ومنها §77-D للي رجع) بمرجع الرسالة نفسه (`SMS:<البصمة>`) ⇒ الإجابة مرتين أو الوقوع بعد الحفظ وقبل الشيل **ما بيعملش
 *   عملية تانية**. والرسالة بتتشال من الصندوق.
 * - **شبه عملية موجودة** (الكشف فيه نفس المبلغ في نفس اليوم — §72 «شبه عملية موجودة» بيستنى) ⇒ **ما بتتسجلش**:
 *   [ForeignOutcome.LOOKS_LIKE_EXISTING] ومعاها العملية الموجودة، والشاشة بتسأل: «هي نفسها» ⇒ [keepExisting] · «لأ دي عملية تانية» ⇒
 *   [answerForeign] تاني بـ `notTheSame = true`.
 * ⚠️ §75.1 (ب) لسه مفتوح: السؤال اللي ما اتجاوبش يظهر كسطر عملية «مبلغها ناقص» ولا يفضل رسالة مستنية — المتبني هنا: رسالة مستنية.
 */
data class ForeignAsk(
    val messageId: String,
    val spaceId: String,
    val sender: String,
    val date: IsoDate,
    /** كود ISO، أو null لو العملة مش معروفة بالظبط («دولار» لوحده · «دينار»). */
    val currency: String?,
    /** المبلغ الأجنبي بالوحدة الصغرى بتاعة [currency]، أو null لو مش مقروء بالظبط ([writtenAmount] زي ما هو مكتوب). */
    val foreignMinor: Long?,
    val decimals: Int?,
    val writtenAmount: String?,
    val direction: Direction,
    val merchant: String,
    val kind: SmsKind,
    val ownLast4: String?,
    /** المبلغ بعملة البلد **المكتوب في الرسالة** — اقتراح للسؤال، مش مبلغ متسجل. */
    val localSuggestion: Halalas?,
)

enum class ForeignOutcome {
    /** اتسجلت دلوقتي. */
    RECORDED,

    /** اتجاوبت قبل كده (نفس الرسالة) ⇒ نفس العملية، ما اتسجلتش تاني. */
    ALREADY_RECORDED,

    /** شبه عملية موجودة ⇒ **ما اتسجلتش** والرسالة لسه مستنية — [ForeignAnswer.transactionId] = العملية الموجودة. */
    LOOKS_LIKE_EXISTING,
}

/** [transactionId] = العملية اللي اتسجلت أو الموجودة (في [ForeignOutcome.LOOKS_LIKE_EXISTING] — null لو المطابقة ما قالتش هي مين). */
data class ForeignAnswer(val transactionId: Id?, val outcome: ForeignOutcome) {
    val alreadyRecorded: Boolean get() = outcome == ForeignOutcome.ALREADY_RECORDED
}

class ForeignSmsAsks(private val deps: AutoRecordSmsDeps) {
    private class Found(val lane: SmsLane, val ask: ForeignAsk)

    private suspend fun findAll(): List<Found> {
        if (!deps.inbox.available || !deps.inbox.sync().enabled) return emptyList()
        val seen = HashSet<String>()
        val out = mutableListOf<Found>()
        for (lane in deps.lanes) {
            val view = lane.review.inboxView()
            val byId = view.messages.associateBy { it.id }
            for (item in view.items) {
                val parsed = item.parsed as? SmsParseResult.Rejected ?: continue
                val ask = askOf(lane.spaceId, item, parsed, byId[item.id]) ?: continue
                if (seen.add(item.id)) out += Found(lane, ask)
            }
        }
        return out
    }

    private fun askOf(spaceId: String, item: InboxItem, parsed: SmsParseResult.Rejected, queued: QueuedSms?): ForeignAsk? {
        // §77-D: الأجنبي اللي رجع («Purchase … returned» بالدولار) — القارئ المصري ما بيعدّيش الأجنبي على `refineSmsKind`
        fun kindOf(kind: SmsKind, direction: Direction) = queued?.let { smsMessageKind(it.message(), kind, direction) } ?: kind
        parsed.foreign?.let { f ->
            return ForeignAsk(
                item.id, spaceId, item.sender, f.date, f.foreign.currency, f.foreign.amountMinor, f.foreign.decimals, null,
                f.direction, f.merchantName, kindOf(f.kind, f.direction), f.ownLast4, f.localSuggestion,
            )
        }
        val u = parsed.foreignUnread ?: return null
        val minor = u.currency?.let { writtenForeignMinor(u.writtenAmount, it) }
        return ForeignAsk(
            item.id, spaceId, item.sender, u.date, u.currency, minor, u.currency?.let(::currencyDecimals), u.writtenAmount,
            u.direction, u.merchantName, kindOf(u.kind, u.direction), u.ownLast4, u.localSuggestion,
        )
    }

    /** الرسايل الأجنبية المستنية المبلغ المحلي، بترتيب الصندوق. */
    suspend fun list(): List<ForeignAsk> = findAll().map { it.ask }

    /** أسئلة البلد [spaceId] اللي تاريخها لحد [to] — الرسالة مستنية لحد ما تتجاوب مهما قدمت (مش بس في الفترة). */
    suspend fun pendingFor(spaceId: String, to: IsoDate): List<PendingAsk> = list()
        .filter { it.spaceId == spaceId && it.date <= to }
        .map { PendingAsk(AskKind.FOREIGN_LOCAL_AMOUNT, spaceId, messageId = it.messageId, date = it.date) }

    /**
     * المالك كتب المبلغ المحلي [localMinor] (بعملة المحفظة) للرسالة [messageId]. [walletId] = المحفظة اللي اتخصم منها؛ من غيرها: محفظة
     * البنك ده (الربط) أو حساب البنك الوحيد في البلد — زي التسجيل التلقائي، وإلا لازم يختار. [notTheSame] = المالك قال إنها **مش** العملية
     * الموجودة اللي شبهها (بعد [ForeignOutcome.LOOKS_LIKE_EXISTING]) ⇒ بتتسجل.
     */
    suspend fun answerForeign(messageId: String, localMinor: Halalas, walletId: Id? = null, notTheSame: Boolean = false): ForeignAnswer = SMS_RECORD_LOCK.withLock {
        assertHalalas(localMinor)
        if (localMinor <= 0) throw IllegalArgumentException(uiText(TextKey.RETURNS_FOREIGN_AMOUNT_POSITIVE))
        val found = findAll().firstOrNull { it.ask.messageId == messageId } ?: throw IllegalStateException(uiText(TextKey.RETURNS_FOREIGN_NOT_FOUND))
        val lane = found.lane
        val queued = deps.inbox.sync().messages.firstOrNull { it.id == messageId } ?: throw IllegalStateException(uiText(TextKey.RETURNS_FOREIGN_NOT_FOUND))
        val wallet = walletFor(lane, found.ask.sender, walletId) ?: throw IllegalStateException(uiText(TextKey.RETURNS_FOREIGN_WALLET_NEEDED))
        val row = answerRow(queued.message(), found.ask, localMinor)
        val request = ImportRequest(
            fileName = "bank-sms.json", content = smsRowsJson(listOf(row)), accountIdentity = wallet.name, sourceType = ImportSourceType.SMS,
            walletId = wallet.id, schema = SchemaId.SMS, parsedRows = listOf(row.toParsedRow()), currency = wallet.currency,
            smsRows = mapOf(row.lineNumber to row), byOwner = true,
        )
        val preview = lane.importer.preview(request)
        val line = preview.lines.single()
        val matched = line.matchedTransactionId
        when {
            line.state == MatchingState.DUPLICATE || line.state == MatchingState.CONFLICT ->
                // اتجاوبت قبل كده (الشيل وقع، أو مبلغ تاني بنفس الرسالة) ⇒ نفس العملية، ما بتتسجلش تاني
                ForeignAnswer(matched ?: throw IllegalStateException(uiText(TextKey.RETURNS_FOREIGN_NOT_FOUND)), ForeignOutcome.ALREADY_RECORDED)
                    .also { deps.inbox.acknowledge(listOf(messageId)) }
            // §72: «شبه عملية موجودة» بيستنى — الاختيار الصريح في الاستيراد كان بيسجّلها فوق الكشف (العملية مرتين)
            line.state == MatchingState.SIMILAR && !notTheSame -> ForeignAnswer(matched, ForeignOutcome.LOOKS_LIKE_EXISTING)
            else -> {
                val batch = lane.importer.commit(request, preview, listOf(row.lineNumber))
                val id = lane.sources.listByBatch(batch.id).mapNotNull { it.transactionId }.single()
                // بعد الحفظ بس — لو الشيل وقع، الرسالة بتفضل والإجابة الجاية بتلاقي العملية (مكررة)
                deps.inbox.acknowledge(listOf(messageId))
                ForeignAnswer(id, ForeignOutcome.RECORDED)
            }
        }
    }

    /** «هي نفسها العملية الموجودة» (بعد [ForeignOutcome.LOOKS_LIKE_EXISTING]): الرسالة بتتشال من غير ما حاجة تتسجل. */
    suspend fun keepExisting(messageId: String): Unit = SMS_RECORD_LOCK.withLock {
        if (findAll().none { it.ask.messageId == messageId }) throw IllegalStateException(uiText(TextKey.RETURNS_FOREIGN_NOT_FOUND))
        deps.inbox.acknowledge(listOf(messageId))
    }

    private suspend fun walletFor(lane: SmsLane, sender: String, chosen: Id?): Wallet? {
        if (chosen != null) return lane.wallets.findById(chosen) ?: throw IllegalArgumentException(uiText(TextKey.SMS_AUTO_WALLET_UNKNOWN))
        val all = lane.wallets.listAll()
        val mapped = deps.inbox.senderWallets(lane.spaceId)[smsSenderKey(sender)]
        return if (mapped != null) all.firstOrNull { it.id == mapped } else all.filter { it.kind == "bank" }.singleOrNull()
    }

    /**
     * صف الإجابة: نفس مرجع الرسالة ووصفها المحجوب زي القارئين، والمبلغ = المحلي، والأجنبي معاه (`ForeignSmsEffect` بينسخه) ورقم البنك
     * المرجعي ونوعها بعد §77-D (`ReturnedSmsEffect` بيدوّر على الأصلية للأجنبي اللي رجع).
     */
    private fun answerRow(message: BankSmsMessage, ask: ForeignAsk, localMinor: Halalas): SmsRow {
        val text = smsSafeText(message)
        return SmsRow(
            lineNumber = 1, date = ask.date, amountMinor = localMinor, direction = ask.direction, merchantName = redactSms(ask.merchant),
            reference = smsSourceReference(message), sourceName = message.sender, description = text, raw = text, kind = ask.kind,
            ownLast4 = ask.ownLast4, bankReference = smsMessageReference(message),
            foreignCurrency = ask.currency, foreignAmountMinor = ask.foreignMinor.takeIf { ask.currency != null },
        )
    }
}
