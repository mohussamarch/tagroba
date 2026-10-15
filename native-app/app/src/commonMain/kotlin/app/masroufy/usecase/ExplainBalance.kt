package app.masroufy.usecase

import app.masroufy.core.BalanceExplanation
import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.Id
import app.masroufy.core.ImportSourceType
import app.masroufy.core.IsoDate
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.TxnSources
import app.masroufy.core.SmsStatementPair
import app.masroufy.core.explainWalletBalance
import app.masroufy.core.withRematchPairs
import app.masroufy.core.isSmsReference
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * «افهم رصيدك» (HANDOVER §6.5 بند 3): تفكيك رصيد محفظة (`explainWalletBalance` — نفس قاعدة «معك الآن») + «حاجات تستاهل تتراجع».
 * **قراية بس.** مصدر كل عملية من سجلات مصدرها: نوع دفعتها (رسالة ولا كشف)، ولو الدفعة مش موجودة (بيانات قديمة) ⇒ شكل المرجع
 * (`SMS:` = رسالة) — نفس قاعدة الدمج (`CrossSourcePreview.kt`). مفيش سجلات ⇒ يدوي.
 */
data class ExplainBalanceDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val batches: ImportBatchRepository,
    val wallets: WalletRepository,
    val windowDays: Int = CROSS_SOURCE_WINDOW_DAYS,
    /** علامة «تعديل رصيد» (فرع «حدّث الرصيد» بيحددها وقت الدمج) — الافتراضي ولا عملية. */
    val isAdjustment: (Transaction) -> Boolean = { false },
    /** أزواج «صلّح رسايل البنك القديمة» (`RepairOldSms.duplicates`) — اللي بعكس الاتجاه بتنضاف لـ«راجع المكرر». null = مش متوصل. */
    val rematch: (suspend () -> List<SmsStatementPair>)? = null,
)

/** أول يوم ممكن — عمليات قبل رصيد البداية بتتعرض (مش بتتحسب)، فالقراية من الأول (زي «طبّق القواعد على كل حاجة» — 1970-01-01). */
internal const val EARLIEST_DAY = "1970-01-01"

class ExplainBalance(private val deps: ExplainBalanceDeps) {
    /** null = المحفظة مش موجودة. */
    suspend fun explain(walletId: Id, asOf: IsoDate): BalanceExplanation? {
        val wallets = deps.wallets.listAll()
        val wallet = wallets.firstOrNull { it.id == walletId } ?: return null
        val all = deps.txns.listByDateRange(EARLIEST_DAY, asOf)
        val touching = all.filter { it.walletId == walletId || it.transferToWalletId == walletId || (it.walletId == null && it.transferToWalletId == null) }
        val own = touching.filter { it.walletId == walletId }.map { it.id }
        val sources = sourcesOf(deps.sources, deps.batches, own)
        val e = explainWalletBalance(wallet, wallets, touching, sources, asOf, deps.windowDays, deps.isAdjustment)
        val pairs = deps.rematch?.let { f -> runCatching { f() }.getOrNull() }.orEmpty()
        return if (pairs.isEmpty()) e else withRematchPairs(e, pairs, touching.associateBy { it.id })
    }
}

/** مصادر العمليات [ids] (كشف و/أو رسالة) من سجلاتها. عملية مالهاش سجلات مش في الخريطة (= يدوي). */
internal suspend fun sourcesOf(sources: SourceRecordRepository, batches: ImportBatchRepository, ids: List<Id>): Map<Id, TxnSources> {
    if (ids.isEmpty()) return emptyMap()
    val records = sources.listByTransactionIds(ids)
    val kinds = records.map { it.batchId }.distinct().associateWith { batches.findById(it)?.sourceType }
    val out = HashMap<Id, TxnSources>()
    for (r in records) {
        val id = r.transactionId ?: continue
        val sms = isSmsRecord(r, kinds[r.batchId])
        val now = out[id] ?: TxnSources(statement = false, sms = false)
        out[id] = if (sms) now.copy(sms = true) else now.copy(statement = true)
    }
    return out
}

/** السجل من رسالة؟ نوع دفعته، ولو الدفعة مش موجودة ⇒ شكل المرجع. */
internal fun isSmsRecord(record: SourceRecord, batchType: ImportSourceType?): Boolean =
    batchType?.let { it == ImportSourceType.SMS } ?: isSmsReference(record.sourceReference)
