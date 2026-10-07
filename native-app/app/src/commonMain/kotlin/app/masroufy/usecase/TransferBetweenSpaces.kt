package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ReviewState
import app.masroufy.core.Space
import app.masroufy.core.SpaceLeg
import app.masroufy.core.SpaceTransfer
import app.masroufy.core.SpaceTransferError
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.asSpaceTransferLeg
import app.masroufy.core.asUnlinkedLeg
import app.masroufy.core.assertHalalas
import app.masroufy.core.checkSpaceTransfer
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.jsTrim
import app.masroufy.core.spaceTransferId
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
import app.masroufy.port.Clock
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.ProjectLinkRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SpaceLegWrite
import app.masroufy.port.SpaceTransferLegs
import app.masroufy.port.SpaceTransferRepository
import app.masroufy.port.SpaceTransferWriter
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository
import app.masroufy.port.ZakatPaymentRepository

/**
 * تحويل لنفسك بين بلدين (رد المالك §64 — «زوج مربوط»): ربط عمليتين موجودين من الكشفين · تسجيل تحويل جديد (عمليتين يدوي) · الفك.
 * الرجلين «تحويل داخلي» مؤكد ⇒ **لا مصروف ولا دخل** في أي بلد، وكل رجل بعملتها زي ما هي. الكتابة **ذرّية** ([SpaceTransferWriter]).
 * الرجل ما تبقاش متربطة بأي حاجة تانية (مستحقات · زكاة · نقطة · مشروع · شخص) والعكس — اختيار Claude §64.
 */

/** كل اللي بيربط عملية بحاجة تانية جوه البلد — الرجل المتربطة بأي واحد منهم مرفوضة. */
data class TransactionLinkRepos(
    val roscaEntries: RoscaEntryRepository,
    val installmentPayments: InstallmentPaymentRepository,
    val installmentPlans: InstallmentPlanRepository,
    val zakatPayments: ZakatPaymentRepository,
    val eventLinks: EventLinkRepository,
    val projectLinks: ProjectLinkRepository,
    val allocations: AllocationRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
) {
    suspend fun linked(ids: List<Id>): Set<Id> {
        val wanted = ids.toSet()
        return (
            roscaEntries.listByTransactionIds(ids).map { it.transactionId } + installmentPayments.listByTransactionIds(ids).map { it.transactionId } +
                installmentPlans.listAll().mapNotNull { it.receivedTransactionId } + zakatPayments.listByTransactionIds(ids).mapNotNull { it.transactionId } +
                eventLinks.listByTransactionIds(ids).map { it.transactionId } + projectLinks.listAll().map { it.transactionId } +
                allocations.listByTransactionIds(ids).map { it.transactionId } + obligations.listByTransactionIds(ids).mapNotNull { it.originTransactionId } +
                settlements.listByTransactionIds(ids).map { it.transactionId }
            ).filter { it in wanted }.toSet()
    }
}

/** مستودعات بلد واحدة. */
data class SpaceBook(val space: Space, val transactions: TransactionRepository, val wallets: WalletRepository, val links: TransactionLinkRepos)

data class TransferBetweenSpacesDeps(
    val pairs: SpaceTransferRepository,
    val writer: SpaceTransferWriter,
    /** `null` = البلد مش موجودة أو مؤرشفة. */
    val book: suspend (spaceId: String) -> SpaceBook?,
    val ids: IdGenerator,
    val clock: Clock,
)

/** تحويل جديد ما وصلش في أي كشف (كاش مثلًا): مبلغ طالع بعملة البلد الأولى ومبلغ داخل بعملة التانية — الاتنين زي ما حصلوا فعلًا. */
data class NewSpaceTransfer(
    val fromSpaceId: String,
    val fromWalletId: Id,
    val fromAmountMinor: Halalas,
    val toSpaceId: String,
    val toWalletId: Id,
    val toAmountMinor: Halalas,
    val occurredAt: String,
    val note: String? = null,
)

class TransferBetweenSpaces(private val deps: TransferBetweenSpacesDeps) {
    suspend fun list(): List<SpaceTransfer> = deps.pairs.listAll().sortedByDescending { it.createdAt }

    /** ربط عمليتين موجودين: الطالعة من بلد والداخلة في التانية. */
    suspend fun linkExisting(fromSpaceId: String, fromTransactionId: Id, toSpaceId: String, toTransactionId: Id, note: String? = null): SpaceTransfer {
        val from = book(fromSpaceId)
        val to = book(toSpaceId)
        val fromTx = from.transactions.findByIds(listOf(fromTransactionId)).firstOrNull() ?: fail(TextKey.SPACE_TRANSFER_TXN_NOT_FOUND)
        val toTx = to.transactions.findByIds(listOf(toTransactionId)).firstOrNull() ?: fail(TextKey.SPACE_TRANSFER_TXN_NOT_FOUND)
        return write(from, fromTx, to, toTx, cleanNote(note))
    }

    /** تسجيل تحويل جديد: عمليتين يدوي (طالعة وداخلة) + الزوج — مع بعض أو ولا حاجة. */
    suspend fun recordNew(input: NewSpaceTransfer): SpaceTransfer {
        if (!isValidIsoDate(input.occurredAt)) throw SpaceTransferError(uiText(TextKey.SPACE_TRANSFER_DATE))
        for (amount in listOf(input.fromAmountMinor, input.toAmountMinor)) {
            if (amount <= 0) fail(TextKey.SPACE_TRANSFER_AMOUNT)
            assertHalalas(amount)
        }
        val from = book(input.fromSpaceId)
        val to = book(input.toSpaceId)
        val now = deps.clock.nowIso()
        val fromWallet = from.wallets.findById(input.fromWalletId) ?: fail(TextKey.SPACE_TRANSFER_WALLET)
        val toWallet = to.wallets.findById(input.toWalletId) ?: fail(TextKey.SPACE_TRANSFER_WALLET)
        fun manual(wallet: app.masroufy.core.Wallet, direction: Direction, amount: Halalas) = Transaction(
            id = deps.ids.next("txn"), occurredAt = input.occurredAt, datePrecision = "day", sourceOrder = 9_000_000,
            economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, observedDirection = direction, amountMinor = amount,
            currency = wallet.currency, walletId = wallet.id, categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.CONFIRMED,
            isCashTagged = wallet.kind == "cash", rawMerchantName = uiText(TextKey.KIND_INTERNAL_TRANSFER), createdAt = now, updatedAt = now,
        )
        return write(from, manual(fromWallet, Direction.OUT, input.fromAmountMinor), to, manual(toWallet, Direction.IN, input.toAmountMinor), cleanNote(input.note))
    }

    /** الفك: الزوج بيتمسح والرجلين بيرجعوا «لسه ما اتحددش» ويتسألوا تاني — **العمليات ما بتتمسحش**. */
    suspend fun unlink(pairId: Id): SpaceTransfer {
        val pair = deps.pairs.listAll().firstOrNull { it.id == pairId } ?: fail(TextKey.SPACE_TRANSFER_NOT_FOUND)
        deps.writer.unlink(pair, survivingLegs(pair))
        return pair
    }

    /** رجول البلد دي — للي بيربط عملية بحاجة تانية، وللتراجع عن دفعة استيراد. */
    fun legsIn(spaceId: String): SpaceTransferLegs = object : SpaceTransferLegs {
        override suspend fun pairsOf(transactionIds: List<Id>): List<SpaceTransfer> {
            val wanted = transactionIds.toSet()
            return deps.pairs.listAll().filter { p -> wanted.any { p.involves(spaceId, it) } }
        }

        override suspend fun legsAmong(transactionIds: List<Id>): Set<Id> {
            val pairs = deps.pairs.listAll()
            return transactionIds.filter { id -> pairs.any { it.involves(spaceId, id) } }.toSet()
        }

        override suspend fun detach(pairs: List<SpaceTransfer>) {
            for (p in pairs) deps.writer.unlink(p, survivingLegs(p))
        }
    }

    private suspend fun write(from: SpaceBook, fromTx: Transaction, to: SpaceBook, toTx: Transaction, note: String?): SpaceTransfer {
        val fromLeg = SpaceLeg(from.space, fromTx, fromTx.walletId?.let { from.wallets.findById(it) })
        val toLeg = SpaceLeg(to.space, toTx, toTx.walletId?.let { to.wallets.findById(it) })
        val linked = from.links.linked(listOf(fromTx.id)).map { from.space.id to it } + to.links.linked(listOf(toTx.id)).map { to.space.id to it }
        checkSpaceTransfer(fromLeg, toLeg, deps.pairs.listAll(), linked.toSet())
        val now = deps.clock.nowIso()
        val pair = SpaceTransfer(
            spaceTransferId(from.space.id, fromTx.id), from.space.id, fromTx.id, fromTx.amountMinor, fromTx.currency,
            to.space.id, toTx.id, toTx.amountMinor, toTx.currency, now, note,
        )
        deps.writer.link(pair, listOf(SpaceLegWrite(from.space.id, asSpaceTransferLeg(fromTx, now)), SpaceLegWrite(to.space.id, asSpaceTransferLeg(toTx, now))))
        return pair
    }

    /** الرجول اللي لسه موجودة (بلد مؤرشفة أو عملية اتمسحت ⇒ بتتساب) بعد الفك. */
    private suspend fun survivingLegs(pair: SpaceTransfer): List<SpaceLegWrite> {
        val now = deps.clock.nowIso()
        return listOf(pair.fromSpaceId to pair.fromTransactionId, pair.toSpaceId to pair.toTransactionId).mapNotNull { (spaceId, txId) ->
            val tx = deps.book(spaceId)?.transactions?.findByIds(listOf(txId))?.firstOrNull() ?: return@mapNotNull null
            SpaceLegWrite(spaceId, asUnlinkedLeg(tx, now))
        }
    }

    private suspend fun book(spaceId: String): SpaceBook = deps.book(spaceId) ?: throw SpaceTransferError(uiText(TextKey.SPACE_NOT_FOUND))

    private fun cleanNote(note: String?): String? {
        val n = note?.let(::jsTrim)?.takeIf { it.isNotEmpty() } ?: return null
        if (n.length > 1000) throw SpaceTransferError(uiText(TextKey.SPACE_TRANSFER_NOTE_LONG))
        return n
    }

    private fun fail(key: TextKey): Nothing = throw SpaceTransferError(uiText(key))
}
