package app.masroufy.wiring

import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.countryPack
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.port.BankSmsParser
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.port.smsSenderKey
import app.masroufy.ui.screens.imports.SenderReview
import app.masroufy.ui.screens.imports.SmsDeps
import app.masroufy.ui.screens.imports.SmsOverview
import app.masroufy.usecase.AddKind
import app.masroufy.usecase.AddOperationDraft
import app.masroufy.usecase.AddOperationResult
import app.masroufy.usecase.AutoRecordResult
import app.masroufy.usecase.AutoRecordSms
import app.masroufy.usecase.AutoRecordSmsDeps
import app.masroufy.usecase.AutoRecordStatus
import app.masroufy.usecase.EditTransaction
import app.masroufy.usecase.ImportStatement
import app.masroufy.usecase.InboxView
import app.masroufy.usecase.ManageSmsInbox
import app.masroufy.usecase.ReviewSmsInbox
import app.masroufy.usecase.ReviewSmsInboxDeps
import app.masroufy.usecase.RevertImportBatch
import app.masroufy.usecase.SmsLane
import app.masroufy.usecase.SmsReviewTarget

/**
 * رسايل البنك لشاشات «الاستيراد» — تجميع حالات الاستخدام الموجودة بس (مفيش حساب هنا):
 * - التسجيل لوحده والمحفظة لكل بنك = `AutoRecordSms` **بكل البلاد زي الخلفية بالظبط** (`backgroundCycle`).
 * - مراجعة اللي مستني = `ReviewSmsInbox` **لكل بنك على محفظته** ([SenderInbox] بيدّيه رسايل البنك ده بس) — عشان «سجّل الكل» ما يسجّلش رسالة
 *   بنك في محفظة بنك تاني. ⚠️ هدف المراجعة ([targetOf]) نسخة من `AutoRecordSms.walletFor` (private هناك) — لما حالة الاستخدام تدّي
 *   مراجعة البنك بنفسها، السطور دي تتشال.
 */
internal class SmsGraph(
    private val c: AreaContext,
    private val inbox: SmsInboxPort,
    private val parse: BankSmsParser,
    private val transactions: EditTransaction,
    private val batches: RevertImportBatch,
) : SmsDeps {
    private val space = c.space
    private val manage = ManageSmsInbox(inbox, parse)
    private val base = review(manage)

    /** مراجعة كل بنك من آخر [overview] — «سجّل الكل» بيسجّل نفس اللي اتعرض (الجلسة جوه كل `ReviewSmsInbox`). */
    private var last: List<Pair<SenderReview, ReviewSmsInbox>> = emptyList()

    private fun review(m: ManageSmsInbox) =
        ReviewSmsInbox(ReviewSmsInboxDeps(m, ImportStatement(importDeps(c.repos, c.env)), c.repos.merchants, c.repos.categories, c.env.ids))

    /** كل البلاد المفتوحة (رسالة بلد بتترفض من قارئ البلد التانية بالعملة ⇒ بتتسجل في بلدها بس). */
    private fun auto(): AutoRecordSms {
        val spaces = c.session.spaces().ifEmpty { listOf(space to c.repos) }
        val lanes = spaces.mapNotNull { (s, repos) ->
            val reader = countryPack(s.countryCode).smsReader ?: return@mapNotNull null
            SmsLane.of(s.id, importDeps(repos, c.env), ManageSmsInbox(inbox, reader::parse), repos.wallets)
        }
        return AutoRecordSms(AutoRecordSmsDeps(inbox, lanes))
    }

    override suspend fun overview(record: Boolean): SmsOverview {
        val auto = auto()
        val result = if (record) auto.run() else null
        val view = manage.refresh()
        val unmapped = (result?.takeIf { it.status == AutoRecordStatus.RAN }?.unmappedSenders ?: auto.unmappedSenders()).filter { it.spaceId == space.id }
        val wallets = c.shell.addOptions().wallets
        val senderWallets = view.senders.associateWith { sender -> runCatching { auto.walletOf(space.id, sender) }.getOrNull() }
        val reviews = mutableListOf<Pair<SenderReview, ReviewSmsInbox>>()
        for ((sender, walletId) in senderWallets) {
            val wallet = wallets.firstOrNull { it.id == walletId } ?: continue
            val key = smsSenderKey(sender)
            if (view.items.none { smsSenderKey(it.sender) == key }) continue
            val one = review(ManageSmsInbox(SenderInbox(inbox, key), parse))
            reviews += SenderReview(sender, wallet.id, one.load(targetOf(wallet, wallets))) to one
        }
        last = reviews
        return SmsOverview(view, unmapped, reviews.map { it.first }, senderWallets, result?.recordedTransactionIds.orEmpty())
    }

    override suspend fun record(categories: Map<String, Id>, includeSimilar: Set<String>): Int {
        var total = 0
        for ((shown, one) in last) {
            val lines = shown.review.ready + shown.review.similar
            val chosen = lines.mapNotNull { line -> categories[line.messageId]?.let { line.lineNumber to it } }.toMap()
            val similar = shown.review.similar.filter { it.messageId in includeSimilar && it.state == MatchingState.SIMILAR }.map { it.lineNumber }
            if (shown.review.ready.isEmpty() && similar.isEmpty()) continue
            total += one.recordAll(chosen, similar)
        }
        last = emptyList()
        return total
    }

    override suspend fun dismiss(messageIds: List<String>) {
        manage.dismiss(messageIds)
    }

    override suspend fun chooseWallet(sender: String, walletId: Id?): AutoRecordResult {
        val auto = auto()
        auto.chooseWallet(space.id, sender, walletId)
        return auto.run()
    }

    override suspend fun enable(senders: List<String>): InboxView = manage.enable(senders)

    override suspend fun disable(): InboxView = manage.disable()

    override suspend fun remember(merchant: String, categoryId: Id, direction: Direction): Boolean = base.remember(merchant, categoryId, direction)

    /**
     * اللي اتسجل من رسايل البنك النهارده: دفعات الرسايل الأخيرة (`RevertImportBatch.history`) ⇒ عملياتها (`plan` — قراية بس) ⇒ تفاصيلها.
     * ⚠️ مفيش حالة استخدام «اللي اتسجل من الرسايل النهارده» — التجميع ده مكانها لحد ما تتعمل.
     */
    override suspend fun recordedToday(): List<Transaction> {
        val today = c.env.today()
        val yesterday = dayNumberToIso(toDayNumber(parseIsoDate(today)) - 1)
        val recent = batches.history(30).filter { b ->
            b.sourceType == ImportSourceType.SMS && b.state == ImportBatchState.COMMITTED && b.importedAt.take(10) >= yesterday
        }
        val out = LinkedHashMap<Id, Transaction>()
        for (b in recent) {
            val plan = runCatching { batches.plan(b.id) }.getOrNull() ?: continue
            for (o in plan.outcomes) {
                val t = runCatching { transactions.load(o.transactionId).transaction }.getOrNull() ?: continue
                if (t.occurredAt == today || b.importedAt.take(10) == today) out[t.id] = t
            }
        }
        return out.values.sortedByDescending { it.createdAt }
    }

    override suspend fun recordByHand(messageId: String, sender: String, amountText: String): AddOperationResult {
        val walletId = runCatching { auto().walletOf(space.id, sender) }.getOrNull()
        val result = c.shell.addOperation(AddOperationDraft(AddKind.OUT, amountText, walletId))
        if (result is AddOperationResult.Saved) manage.dismiss(listOf(messageId))
        return result
    }
}

/** نفس هدف التشغيلة (`AutoRecordSms.walletFor`): المحفظة وعملتها وآخر ٤ أرقامها + آخر ٤ أرقام حسابات المالك التانية في البلد (الجولة السادسة). */
internal fun targetOf(wallet: Wallet, all: List<Wallet>): SmsReviewTarget {
    fun last4(value: String?) = value?.filter { it in '0'..'9' }?.takeLast(4)?.takeIf { it.length == 4 }
    val others = all.filter { it.id != wallet.id }.mapNotNull { last4(it.accountLast4) }.toSet()
    return SmsReviewTarget(wallet.id, wallet.name, wallet.currency, last4(wallet.accountLast4), others)
}

/** الصندوق نفسه برسايل مرسل واحد بس ([key] بعد `smsSenderKey`) — الشيل والتفعيل بيروحوا للصندوق الحقيقي. */
internal class SenderInbox(private val inbox: SmsInboxPort, private val key: String) : SmsInboxPort {
    override val available: Boolean get() = inbox.available

    private fun only(state: SmsInboxState): SmsInboxState {
        val mine = state.messages.filter { smsSenderKey(it.sender) == key }
        return state.copy(messages = mine, count = mine.size)
    }

    override suspend fun sync() = only(inbox.sync())

    override suspend fun enable(senders: List<String>) = only(inbox.enable(senders))

    override suspend fun disable() = only(inbox.disable())

    override suspend fun acknowledge(ids: List<String>) = only(inbox.acknowledge(ids))

    override suspend fun senderWallets(spaceId: String) = inbox.senderWallets(spaceId)

    override suspend fun setSenderWallet(spaceId: String, sender: String, walletId: String?) = inbox.setSenderWallet(spaceId, sender, walletId)
}
