package app.masroufy.wiring

import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.countryPack
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.port.BankSmsParser
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.smsSenderKey
import app.masroufy.ui.screens.imports.SenderReview
import app.masroufy.ui.screens.imports.SmsDeps
import app.masroufy.ui.screens.imports.SmsOverview
import app.masroufy.usecase.AddKind
import app.masroufy.usecase.AnswerSmsAccount
import app.masroufy.usecase.AddOperationDraft
import app.masroufy.usecase.AddOperationResult
import app.masroufy.usecase.AutoRecordResult
import app.masroufy.usecase.AutoRecordSms
import app.masroufy.usecase.AutoRecordSmsDeps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import app.masroufy.usecase.EditTransaction
import app.masroufy.usecase.InboxView
import app.masroufy.usecase.ManageSmsInbox
import app.masroufy.usecase.ManageWallets
import app.masroufy.usecase.ReviewSmsInbox
import app.masroufy.usecase.RevertImportBatch
import app.masroufy.usecase.SmsLane
import app.masroufy.usecase.SmsReviewTarget
import app.masroufy.usecase.SmsRunMemo
import app.masroufy.usecase.SmsRunProgress
import kotlinx.coroutines.withContext

/**
 * رسايل البنك لشاشات «الاستيراد» — تجميع حالات الاستخدام الموجودة بس (مفيش حساب هنا):
 * - التسجيل لوحده والمحفظة لكل بنك = `AutoRecordSms` **بكل البلاد زي الخلفية بالظبط** (`backgroundCycle`).
 * - مراجعة اللي مستني = `ReviewSmsInbox` **لكل بنك على محفظته** ([SenderInbox] بيدّيه رسايل البنك ده بس) — عشان «سجّل الكل» ما يسجّلش رسالة
 *   بنك في محفظة بنك تاني. ⚠️ هدف المراجعة ([targetOf]) نسخة من `AutoRecordSms.walletFor` (private هناك) — لما حالة الاستخدام تدّي
 *   مراجعة البنك بنفسها، السطور دي تتشال.
 * - r041c: **كل** نداء هنا برّه خيط الواجهة (`DeviceEnv.work`) — قراية الصندوق وفهم الرسايل ومطابقتها وتسجيلها كانت على خيط الواجهة فالبرنامج
 *   كله بيعلّق في «اسحب لتحت» و«إعداد القراءة» بتفضل بيضا على جوال فيه آلاف الرسايل.
 */
internal class SmsGraph(
    private val c: AreaContext,
    private val inbox: SmsInboxPort,
    private val parse: BankSmsParser,
    private val transactions: EditTransaction,
    private val batches: RevertImportBatch,
    /** r05 (4d): شغل الزيادات الجديدة بره خيط الشاشة. */
    private val io: CoroutineContext = Dispatchers.Default,
) : SmsDeps {
    private val space = c.space
    private val manage = ManageSmsInbox(inbox, parse)
    private val base = review(manage)

    /** مراجعة كل بنك من آخر [overview] — «سجّل الكل» بيسجّل نفس اللي اتعرض (الجلسة جوه كل `ReviewSmsInbox`). */
    private var last: List<Pair<SenderReview, ReviewSmsInbox>> = emptyList()

    /** r041c: اللي «اسحب لتحت» عاينه قبل كده ⇒ السحبة الجاية بتقرا الجديد بس (`AutoRecordSms.runNew`). */
    private val memo = SmsRunMemo()

    /** r041c: آخر «إعداد القراءة» ([settings]) — الصفحة بتعرضها على طول وهي بتجيب الجديدة. */
    private var settingsCache: SmsOverview? = null

    private suspend fun <T> work(block: suspend () -> T): T = withContext(c.env.work) { block() }

    // decisions-77 (مراجعة S1): الشاشة لازم تيجي من `SmsLane.screen` — نفس التعلّم والآثار والمحافظ بتوع الخلفية.
    private fun review(m: ManageSmsInbox): ReviewSmsInbox =
        SmsLane.of(space.id, smsImportDeps(space.id, c.repos, c.env, inbox), m, c.repos.wallets, countryPack(space.countryCode).timeZone).screen()

    private fun auto(): AutoRecordSms = autoRecordSms(c, inbox)

    private val extras = SmsScreenExtras(c, { auto() }, io, memo)

    /**
     * r05 (4b): صفحة أقدم من الصندوق، وبعدها الصندوق بيرجع لأحدث صفحة. r051: التشغيلة ما بتقراش صفحة الشاشة (بتمشي بالمؤشر)، و[record]
     * بيسجّل **الجديد بس** (`runNew`) — مش لفة كاملة ماسكة القفل مع كل فتح وتحميل (كانت 28 ثانية على 3,600 رسالة والضغطات مستنياها).
     */
    override suspend fun overviewPage(record: Boolean, skip: Int): SmsOverview = withContext(io) {
        if (skip <= 0) return@withContext overview(record)
        val fresh = if (record) auto().runNew(memo).recordedTransactionIds else emptyList()
        inbox.page(skip)
        try {
            overview(record = false).copy(fresh = fresh)
        } finally {
            inbox.page(0)
        }
    }

    override suspend fun confirmMessages(ids: List<String>, categories: Map<String, Id>, isSalary: Boolean?) = extras.confirm(ids, categories, isSalary)


    override suspend fun overview(record: Boolean): SmsOverview = work {
        val auto = auto()
        // r051: الجديد بس (أو لفة كاملة صفحة صفحة لو حاجة اتغيرت من آخر مرة) · البنوك اللي مالهاش محفظة من غير معاينة ولا قفل
        val result = if (record) auto.runNew(memo) else null
        val view = manage.refresh()
        val unmapped = auto.unmappedQuick().filter { it.spaceId == space.id }
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
        SmsOverview(view, unmapped, reviews.map { it.first }, senderWallets, result?.recordedTransactionIds.orEmpty())
    }

    override suspend fun recordNew(): List<Id> = work { auto().runNew(memo).recordedTransactionIds }

    override suspend fun settings(): SmsOverview = work {
        val auto = auto()
        val view = manage.refresh()
        val unmapped = auto.unmappedQuick().filter { it.spaceId == space.id }
        val senderWallets = view.senders.associateWith { sender -> runCatching { auto.walletOf(space.id, sender) }.getOrNull() }
        SmsOverview(view, unmapped, emptyList(), senderWallets).also { settingsCache = it }
    }

    override fun cachedSettings(): SmsOverview? = settingsCache

    override suspend fun record(categories: Map<String, Id>, includeSimilar: Set<String>): Int = work {
        var total = 0
        for ((shown, one) in last) {
            val lines = shown.review.ready + shown.review.similar
            val chosen = lines.mapNotNull { line -> categories[line.messageId]?.let { line.lineNumber to it } }.toMap()
            val similar = shown.review.similar.filter { it.messageId in includeSimilar && it.state == MatchingState.SIMILAR }.map { it.lineNumber }
            if (shown.review.ready.isEmpty() && similar.isEmpty()) continue
            total += one.recordAll(chosen, similar)
        }
        last = emptyList()
        total
    }

    override suspend fun dismiss(messageIds: List<String>) {
        work { manage.dismiss(messageIds) }
    }

    override suspend fun chooseWallet(sender: String, walletId: Id?): AutoRecordResult = work {
        val auto = auto()
        auto.chooseWallet(space.id, sender, walletId)
        auto.run(memo = memo)
    }

    override suspend fun answerAccount(walletId: Id, last4: String, mine: Boolean, messageId: String?, replace: Boolean): AutoRecordResult =
        work { answering().answerAndRecord(auto(), walletId, last4, mine, messageId, replace) }

    override suspend fun accountNewWallet(fromWalletId: Id, last4: String, messageId: String?): Pair<Wallet, AutoRecordResult> =
        work { answering().newWalletAndRecord(auto(), fromWalletId, last4, messageId) }

    /** r041b P10: «محفظة جديدة لهذا الحساب» بتتعمل من نفس طريق «أضف محفظة» (`ManageWallets` — عملة البلد ويومها). */
    private fun answering() = AnswerSmsAccount(
        c.repos.wallets,
        ManageWallets(c.repos.wallets, c.env.ids, c.env.clock, space.currency, countryPack(space.countryCode).timeZone),
    )

    override suspend fun handoffCard(): String? = work { inbox.handoffCard() }

    override suspend fun setHandoffCard(value: String?) = work { inbox.setHandoffCard(value) }

    override suspend fun lastBackgroundRead(): app.masroufy.core.LastReadWhen? = work {
        val port = c.env.backgroundRead ?: return@work null
        port.last()?.let { app.masroufy.core.lastReadWhen(it, c.env.nowMillis(), port.zoneOffsetMillis(it)) }
    }

    override suspend fun missedWhileAway(inbox: app.masroufy.usecase.InboxView): Int = work {
        val port = c.env.backgroundRead ?: return@work 0
        app.masroufy.core.missedWhileAway(port.last(), inbox.messages.map { app.masroufy.core.isoInstantMillis(it.receivedAt) })
    }

    override suspend fun readingOn(): Boolean = work { manage.refresh().let { it.enabled && it.permission } }

    override suspend fun enable(senders: List<String>): InboxView = work { manage.enable(senders) }

    override suspend fun disable(): InboxView = work { manage.disable() }

    override suspend fun remember(merchant: String, categoryId: Id, direction: Direction): Boolean = work { base.remember(merchant, categoryId, direction) }

    /**
     * اللي اتسجل من رسايل البنك النهارده: دفعات الرسايل الأخيرة (`RevertImportBatch.history`) ⇒ عملياتها (`plan` — قراية بس) ⇒ تفاصيلها.
     * ⚠️ مفيش حالة استخدام «اللي اتسجل من الرسايل النهارده» — التجميع ده مكانها لحد ما تتعمل.
     */
    override suspend fun recordedToday(): List<Transaction> = work {
        val today = c.env.today()
        val yesterday = dayNumberToIso(toDayNumber(parseIsoDate(today)) - 1)
        val recent = batches.history(30).filter { b ->
            b.sourceType == ImportSourceType.SMS && b.state == ImportBatchState.COMMITTED && b.importedAt.take(10) >= yesterday
        }
        // r051: العمليات بقراية واحدة (`findByIds`) — كانت `load` لكل عملية (تفاصيل ووسوم) ⇒ بعد تأكيد 2,500 رسالة الشاشة فضلت هيكل ثواني
        val batchOf = LinkedHashMap<Id, ImportBatch>()
        for (b in recent) {
            val plan = runCatching { batches.plan(b.id) }.getOrNull() ?: continue
            for (o in plan.outcomes) batchOf.getOrPut(o.transactionId) { b }
        }
        val found = runCatching { c.repos.transactions.findByIds(batchOf.keys.toList()) }.getOrDefault(emptyList())
        found.filter { t -> t.occurredAt == today || batchOf[t.id]?.importedAt?.take(10) == today }.sortedByDescending { it.createdAt }
    }

    // §83 «اقرأ اللي فات؟» — بنفس صندوق وحارات التسجيل لوحده (كل البلاد). `SmsHistory` نفسه بيشتغل على `DeviceEnv.work`.
    private fun history() = smsHistory(c.session.spaces().ifEmpty { listOf(c.space to c.repos) }, inbox, c.env)

    override suspend fun historyQuestion() = work { history()?.question(space.id) }

    override suspend fun historyDecline(senders: List<String>) {
        work { history()?.decline(space.id, senders) }
    }

    override suspend fun historyStart(senders: List<String>, from: IsoDate?, progress: (SmsRunProgress) -> Unit) =
        work { history()?.start(space.id, senders, from, progress, memo) }

    override suspend fun historyReport() = work { history()?.report() }

    override suspend fun confirmShapes(messageIds: List<String>, progress: (SmsRunProgress) -> Unit) =
        work { history()?.confirmShapes(messageIds, progress, memo) }

    override suspend fun recordByHand(messageId: String, sender: String, amountText: String): AddOperationResult = work {
        val walletId = runCatching { auto().walletOf(space.id, sender) }.getOrNull()
        val result = c.shell.addOperation(AddOperationDraft(AddKind.OUT, amountText, walletId))
        if (result is AddOperationResult.Saved) manage.dismiss(listOf(messageId))
        result
    }
}

/**
 * التسجيل لوحده لكل البلاد المفتوحة (رسالة بلد بتترفض من قارئ البلد التانية بالعملة ⇒ بتتسجل في بلدها بس) — شاشة رسايل البنك والمساعد
 * («رسايل مستنية» في «أمور لم تُنجزها بعد») بنفس الحارات.
 */
internal fun autoRecordSms(c: AreaContext, inbox: SmsInboxPort): AutoRecordSms {
    val spaces = c.session.spaces().ifEmpty { listOf(c.space to c.repos) }
    val lanes = spaces.mapNotNull { (s, repos) -> smsLaneOf(s, repos, c.env, inbox) }
    return AutoRecordSms(AutoRecordSmsDeps(inbox, lanes, work = c.env.work))
}

/** نفس هدف التشغيلة (`AutoRecordSms.walletFor`): المحفظة وعملتها وآخر ٤ أرقامها + آخر ٤ أرقام حسابات المالك التانية في البلد (الجولة السادسة). */
internal fun targetOf(wallet: Wallet, all: List<Wallet>): SmsReviewTarget {
    fun last4(value: String?) = value?.filter { it in '0'..'9' }?.takeLast(4)?.takeIf { it.length == 4 }
    val others = all.filter { it.id != wallet.id }.mapNotNull { last4(it.accountLast4) }.toSet()
    return SmsReviewTarget(wallet.id, wallet.name, wallet.currency, last4(wallet.accountLast4), others, notMineLast4 = all.flatMap { it.notMineLast4 }.toSet())
}
