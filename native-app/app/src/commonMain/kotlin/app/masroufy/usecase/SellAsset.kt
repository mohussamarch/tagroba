package app.masroufy.usecase

import app.masroufy.core.AssetError
import app.masroufy.core.AssetSale
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.R06Key
import app.masroufy.core.SaleOutcome
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.parseIsoDate
import app.masroufy.core.saleOutcome
import app.masroufy.core.subtractMoney
import app.masroufy.core.toDayNumber
import app.masroufy.core.uiText
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.AssetSaleRepository
import app.masroufy.port.Clock
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * SellAsset — «بيع أصل» بقرار المالك OVERRIDES §112-4: **«الفلوس راحت فين؟» إجباري**، ويتسجل مكسب أو خسارة.
 *
 * - البنك: تقدر تربطه بالتحويل اللي وصل ([matchingCredits]) ⇒ **مفيش حركة فلوس جديدة** — العملية نفسها بتبقى «بيع أصل».
 * - من غير ربط (بنك · كاش · محفظة تانية): بتتعمل عملية داخلة «بيع أصل» على المحفظة دي بـ**الصافي** (الحصيلة − الرسوم — اللي وصل فعلًا).
 * - «بيع أصل» مش دخل (spec/02) — النوع ده ما بيدخلش الدخل ولا المصروف، بس بيرفع رصيد المحفظة.
 * - المكسب = الحصيلة − الرسوم − حصة التكلفة بالمتوسط المرجّح (`saleOutcome` في core — قاعدة التقريب مكتوبة هناك).
 */
enum class SellWalletKind { BANK, CASH, OTHER }

data class SellDestination(val wallet: Wallet, val kind: SellWalletKind)

data class SellAssetInput(
    val sale: SaleInput,
    /** إجباري — المحفظة اللي الفلوس دخلتها. */
    val toWalletId: Id?,
    /** التحويل اللي وصل (اختياري — بنك بس). */
    val linkTransactionId: Id? = null,
)

data class SoldAsset(val sale: AssetSale, val outcome: SaleOutcome, val transactionId: Id?, val created: Boolean)

data class SellAssetDeps(
    val manage: ManageAssets,
    val assets: AssetRepository,
    val lots: AssetLotRepository,
    val sales: AssetSaleRepository,
    val wallets: WalletRepository,
    val txns: TransactionRepository,
    val add: AddTransaction,
    val clock: Clock,
)

/** التحويل «القريب»: نفس المحفظة والعملة · داخل · ±[MATCH_DAYS] يوم من يوم البيع · المبلغ في حدود 10% من الصافي (أعداد صحيحة). */
const val MATCH_DAYS = 7
private const val MATCH_LIMIT = 5

class SellAsset(private val deps: SellAssetDeps) {
    private suspend fun assetOf(id: Id) = deps.assets.listAll().find { it.id == id } ?: throw AssetError(uiText(app.masroufy.core.TextKey.ASSET_NOT_FOUND))

    /** مربعات «الفلوس راحت فين؟»: كل محافظ البلد دي بعملة الأصل — البنوك الأول، وبعدين الكاش، وبعدين الباقي. */
    suspend fun destinations(assetId: Id): List<SellDestination> {
        val currency = assetOf(assetId).currency
        return deps.wallets.listAll().filter { it.currency == currency }
            .map { SellDestination(it, kindOf(it)) }
            .sortedBy { it.kind.ordinal }
    }

    /** المكسب أو الخسارة قبل الحفظ (المعاينة) — نفس الحساب اللي بيتحفظ. */
    suspend fun preview(input: SaleInput): SaleOutcome {
        val draft = AssetSale("", input.assetId, input.soldAt, input.quantity, input.grossProceedsMinor, input.feeMinor ?: 0)
        val known = assetOf(input.assetId).costKnown != false
        return saleOutcome(deps.lots.listByAsset(input.assetId), deps.sales.listByAsset(input.assetId), draft).copy(costKnown = known)
    }

    /** «اربطه بالتحويل اللي وصل (لو وصل)»: داخل على المحفظة دي، قريب من يوم البيع ومن الصافي، ومش مربوط بشراء أو بيع تاني. */
    suspend fun matchingCredits(walletId: Id, soldAt: IsoDate, grossMinor: Halalas?, feeMinor: Halalas? = null): List<Transaction> {
        if (!isValidIsoDate(soldAt)) return emptyList()
        val netMinor = grossMinor?.let { g -> (g - (feeMinor ?: 0)).takeIf { it > 0 } }
        val wallet = deps.wallets.findById(walletId) ?: return emptyList()
        if (kindOf(wallet) != SellWalletKind.BANK) return emptyList()
        val day = toDayNumber(parseIsoDate(soldAt))
        val used = usedTransactionIds()
        return deps.txns.listByDateRange(dayNumberToIso(day - MATCH_DAYS), dayNumberToIso(day + MATCH_DAYS))
            .filter { isCandidate(it, wallet, used) && (netMinor == null || near(it.amountMinor, netMinor)) }
            .sortedWith(
                compareBy<Transaction> { if (netMinor == null) 0L else kotlin.math.abs(it.amountMinor - netMinor) }
                    .thenBy { kotlin.math.abs(toDayNumber(parseIsoDate(it.occurredAt.take(10))) - day) },
            )
            .take(MATCH_LIMIT)
    }

    suspend fun sell(input: SellAssetInput): SoldAsset {
        val toId = input.toWalletId?.takeIf { it.isNotEmpty() } ?: throw AssetError(uiText(R06Key.SELL_ERR_WALLET))
        val asset = assetOf(input.sale.assetId)
        val wallet = deps.wallets.findById(toId) ?: throw AssetError(uiText(R06Key.SELL_ERR_WALLET))
        if (wallet.currency != asset.currency) throw AssetError(uiText(R06Key.SELL_ERR_CURRENCY))
        // الفحص كله (الكمية · الرسوم · البيع أكتر من المملوك) قبل أي كتابة
        val outcome = preview(input.sale)
        val fee = input.sale.feeMinor ?: 0
        if (fee > input.sale.grossProceedsMinor) {
            throw AssetError(
                uiText(app.masroufy.core.TextKey.ASSET_FEES_OVER_PROCEEDS, app.masroufy.core.formatMoney(fee), app.masroufy.core.formatMoney(input.sale.grossProceedsMinor)),
            )
        }
        val linkId = input.linkTransactionId?.takeIf { it.isNotEmpty() }
        var created: Id? = null
        val txnId: Id? = if (linkId != null) {
            val t = deps.txns.findByIds(listOf(linkId)).firstOrNull()
            if (t == null || kindOf(wallet) != SellWalletKind.BANK || !isCandidate(t, wallet, usedTransactionIds())) {
                throw AssetError(uiText(R06Key.SELL_ERR_LINK))
            }
            t.id
        } else {
            val net = subtractMoney(input.sale.grossProceedsMinor, fee)
            if (net <= 0) null else deps.add.add(
                NewTransactionInput(
                    amountMinor = net, occurredAt = input.sale.soldAt, walletId = wallet.id,
                    economicKind = EconomicKind.ASSET_SELL, merchantName = asset.name,
                ),
            ).id.also { created = it }
        }
        val recorded = try {
            deps.manage.recordSale(input.sale.copy(transactionId = txnId), toWalletId = wallet.id, gainMinor = outcome.gainMinor.takeIf { outcome.costKnown })
        } catch (e: Exception) {
            created?.let { deps.txns.deleteMany(listOf(it)) }
            throw e
        }
        if (linkId != null) {
            // التحويل المربوط بقى «بيع أصل» (مش دخل) — نفس الفلوس، مفيش حركة جديدة
            deps.txns.update(
                linkId,
                TransactionPatch(economicKind = EconomicKind.ASSET_SELL, economicKindConfirmed = true, updatedAt = deps.clock.nowIso()),
            )
        }
        return SoldAsset(recorded.sale, outcome, txnId, created != null)
    }

    private fun isCandidate(t: Transaction, wallet: Wallet, used: Set<Id>): Boolean =
        t.walletId == wallet.id && t.currency == wallet.currency && t.observedDirection == Direction.IN &&
            t.transferToWalletId == null && t.economicKind != EconomicKind.INTERNAL_TRANSFER &&
            t.reversalOfId == null && t.reversedById == null && t.linkedExpenseId == null && t.id !in used

    private suspend fun usedTransactionIds(): Set<Id> =
        (deps.lots.listAll().mapNotNull { it.transactionId } + deps.sales.listAll().mapNotNull { it.transactionId }).toSet()

    companion object {
        fun kindOf(w: Wallet): SellWalletKind = when (w.kind) {
            "bank" -> SellWalletKind.BANK
            "cash" -> SellWalletKind.CASH
            else -> SellWalletKind.OTHER
        }

        /** |المبلغ − الصافي| × 10 ≤ الصافي (يعني 10%) — أعداد صحيحة من غير كسور. */
        fun near(amountMinor: Halalas, netMinor: Halalas): Boolean = kotlin.math.abs(amountMinor - netMinor) * 10 <= netMinor
    }
}
