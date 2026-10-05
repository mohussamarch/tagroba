package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.InheritanceCase
import app.masroufy.core.InheritanceResult
import app.masroufy.core.calculateInheritance
import app.masroufy.core.computePosition
import app.masroufy.core.walletBalancesOn
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetPriceRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.AssetSaleRepository
import app.masroufy.port.Clock
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * «هات أملاكي من مصروفي» (§69) — بيقرا من بيانات المساحة الشغالة بعملتها. لو مش متوفر (تركة شخص تاني بس) ⇒ null.
 */
data class EstateHoldingsDeps(
    val wallets: WalletRepository,
    val txns: TransactionRepository,
    val assets: AssetRepository,
    val lots: AssetLotRepository,
    val sales: AssetSaleRepository,
    val prices: AssetPriceRepository,
)

/** [countryCode] بلد المساحة الشغالة ⇒ القانون تلقائيًا (مش اختيار). [currency] عملتها. */
data class CalculateInheritanceDeps(
    val countryCode: String,
    val currency: Currency,
    val clock: Clock,
    val holdings: EstateHoldingsDeps? = null,
)

/** منين الحاجة اتجابت. */
enum class EstateItemSource { WALLET, ASSET }

/**
 * حاجة مقترحة للتركة. [valueMinor] null = مش معروف (أصل من غير سعر) — **مش صفر** (القاعدة #10)، والمستخدم لازم يكتبه قبل الحساب.
 * كل رقم بيتعدّل في الشاشة قبل ما يتحوّل لـ`EstateItem`.
 */
data class EstateItemDraft(val name: String, val valueMinor: Halalas?, val source: EstateItemSource, val sourceId: Id)

/**
 * **حاسبة الورث** (OVERRIDES §69): من غير تخزين في النسخة دي (حفظ السيناريو سؤال مفتوح للمالك — HANDOVER).
 * - [calculate]: القانون من بلد المساحة الشغالة دايمًا ([CalculateInheritanceDeps.countryCode] بيغلب على اللي في المسألة).
 * - [prefillMyEstate]: لتركة المستخدم **نفسه** بس — أرصدة المحافظ (بعملة المساحة، رصيد النهارده من سلسلة الرصيد) + الأصول
 *   (دهب · فضة · أسهم · صناديق · …) بقيمتها بأسعار النهارده (`computePosition`، نفس قراية الزكاة). الرصيد صفر أو بالسالب ما بيتجابش
 *   (السالب دين — المستخدم يكتبه في الديون — اختيار Claude). الأصل المؤرشف أو اللي اتباع كله ما بيتجابش.
 */
class CalculateInheritance(private val deps: CalculateInheritanceDeps) {
    fun calculate(case: InheritanceCase): InheritanceResult = calculateInheritance(case.copy(countryCode = deps.countryCode))

    suspend fun prefillMyEstate(): List<EstateItemDraft> {
        val h = deps.holdings ?: return emptyList()
        val today = deps.clock.nowIso().take(10)
        val out = mutableListOf<EstateItemDraft>()
        val wallets = h.wallets.listAll().filter { it.currency == deps.currency }
        val from = wallets.minOfOrNull { it.openingAt }
        val txns = if (from == null || from > today) emptyList() else h.txns.listByDateRange(from, today).filter { it.currency == deps.currency }
        val balances = walletBalancesOn(wallets, txns, today)
        for (w in wallets) {
            val balance = balances[w.id] ?: continue
            if (balance > 0) out += EstateItemDraft(w.name, balance, EstateItemSource.WALLET, w.id)
        }
        val lots = h.lots.listAll().filter { it.purchasedAt <= today }.groupBy { it.assetId }
        val sales = h.sales.listAll().filter { it.soldAt <= today }.groupBy { it.assetId }
        val prices = h.prices.listAll().associateBy { it.assetId }
        for (a in h.assets.listAll()) {
            if (a.archived || a.currency != deps.currency) continue
            val position = computePosition(a.id, lots[a.id].orEmpty(), sales[a.id].orEmpty(), prices[a.id], today)
            if (position.heldQuantity <= 0) continue
            out += EstateItemDraft(a.name, position.marketValueMinor, EstateItemSource.ASSET, a.id)
        }
        return out
    }
}
