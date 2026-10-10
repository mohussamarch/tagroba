package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Merchant
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.merchantIndex
import app.masroufy.core.normalizeText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.text.t
import app.masroufy.usecase.TransactionsScreenData

/** عملية للتاجر في «عملياته»: التاريخ · المحفظة · المبلغ. */
data class MerchantTxn(val id: Id, val date: String, val wallet: String, val amountMinor: Halalas, val currency: Currency, val tone: AmountTone)

data class MerchantView(
    val name: String,
    /** `null` = المحل لسه مش محفوظ (الاسم من العملية بس) ⇒ التصنيف والاسم والأسماء البديلة ما بيتحفظوش من هنا. */
    val merchantId: Id?,
    val normalizedName: String,
    val category: Category?,
    /** مؤكد = تصنيفه المثبّت في البلد دي. غير كده = مقترح (من قاعدة عامة أو من بلدك التانية). */
    val confirmed: Boolean,
    /** الاقتراح جاي من تصنيفه في بلدك التانية (§64-٢). */
    val fromOtherCountry: Boolean,
    val aliases: List<String>,
    val periodLabel: String,
    val txns: List<MerchantTxn>,
)

/** التاجر المحفوظ اللي اسمه أو اسمه البديل = [rawName] (نفس مطابقة الكشف — `merchantIndex`). */
fun findMerchant(merchants: List<Merchant>, rawName: String): Merchant? = merchantIndex(merchants)[normalizeText(rawName)]

/**
 * صفحة التاجر من التجار المحفوظين (`ManageRules.listMerchants`) وعمليات الفترة (`LoadTransactionsScreen`) — عمليات الفترة دي **بس**
 * (مفيش حالة استخدام لكل عمليات التاجر ولا مجاميعه لسه ⇒ الكارتين «غير متاح»).
 */
fun merchantView(
    rawName: String,
    merchants: List<Merchant>,
    categories: List<Category>,
    data: TransactionsScreenData?,
    wallets: List<Wallet>,
    today: IsoDate,
): MerchantView {
    val m = findMerchant(merchants, rawName)
    val key = normalizeText(rawName)
    val names = (listOfNotNull(m?.normalizedName, key) + m?.aliases.orEmpty()).toSet()
    val categoryId = m?.verifiedCategoryId ?: m?.suggestedCategoryId
    val txns = data?.transactions.orEmpty()
        .filter { tx -> tx.rawMerchantName?.let { normalizeText(it) in names } == true }
        .sortedByDescending { it.occurredAt }
        .map { tx -> MerchantTxn(tx.id, dayLabel(tx.occurredAt, today), wallets.firstOrNull { it.id == tx.walletId }?.name ?: t(TextKey.NOT_AVAILABLE), tx.amountMinor, tx.currency, toneOf(tx)) }
    return MerchantView(
        name = m?.displayName ?: rawName.trim(),
        merchantId = m?.id,
        normalizedName = m?.normalizedName ?: key,
        category = categoryId?.let { id -> categories.firstOrNull { it.id == id } },
        confirmed = m?.verifiedCategoryId != null,
        fromOtherCountry = m?.verifiedCategoryId == null && m?.suggestedCategoryId != null,
        aliases = m?.aliases.orEmpty(),
        periodLabel = data?.period?.let(::periodLabel).orEmpty(),
        txns = txns,
    )
}

/** الاسم البديل موجود أصلًا (الاسم نفسه أو بديل قديم) ⇒ ما نكتبوش تاني. */
fun aliasExists(view: MerchantView, raw: String): Boolean {
    val a = normalizeText(raw)
    return a == view.normalizedName || view.aliases.any { normalizeText(it) == a }
}

/** سطر الشرح تحت التصنيف. */
fun merchantCategoryHint(view: MerchantView, country: String): String = when {
    view.merchantId == null -> t(UiKey.MERCHANT_PROFILE_NOT_SAVED)
    view.confirmed -> t(UiKey.MERCHANT_PROFILE_HINT_SAVED, country)
    view.fromOtherCountry -> t(UiKey.MERCHANT_PROFILE_HINT_OTHER_COUNTRY)
    else -> t(UiKey.MERCHANT_PROFILE_HINT_SUGGESTED)
}
