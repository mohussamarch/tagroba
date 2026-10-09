package app.masroufy.ui.screens.operations

import androidx.compose.ui.graphics.Color
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Liquidity
import app.masroufy.core.Merchant
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.Wallet
import app.masroufy.core.isTransferLike
import app.masroufy.core.merchantIndex
import app.masroufy.core.normalizeText
import app.masroufy.core.ruleFor
import app.masroufy.core.transferPartyOf
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.text.t

/** نوع العملية من ناحية الربط: شراء (دين عليه · هدية له) · حوالة طالعة · حوالة داخلة · تحويل بين محافظك (من غير ربط). */
enum class LinkFlow { PURCHASE, TRANSFER_OUT, TRANSFER_IN, NONE }

/** حالة التصنيف في التفاصيل: مقترح (يحتاج تأكيدك) · مؤكد · بلا تصنيف. */
enum class CategoryStatus { SUGGESTED, CONFIRMED, NONE }

data class CategoryField(val name: String?, val status: CategoryStatus, val categoryId: Id?)

/** سطر في كارت الحقول: العنوان الصغير · القيمة · شارة اختيارية · سطر شرح اختياري. */
data class DetailField(val label: String, val value: String, val chip: FieldChip? = null, val hint: String? = null)

data class FieldChip(val text: String, val ink: Color, val background: Color)

data class DetailView(
    val id: Id,
    val title: String,
    val sub: String,
    val icon: Lucide,
    val color: Color,
    val amountMinor: Halalas,
    val currency: Currency,
    val tone: AmountTone,
    /** اسم التاجر زي ما جه (لصفحة التاجر) — للشراء بس. */
    val merchantName: String?,
    /** التاجر المحفوظ (لو موجود) — اختيار تصنيف بإيدك بيتثبّت عليه. */
    val merchant: Merchant?,
    /** فرق عدّ الكاش «غير مسجّلة» — ⚠️ مفيش منطق عدّ الكاش في كوتلن لسه ⇒ `false` دايمًا. */
    val unrecorded: Boolean,
    val category: CategoryField?,
    val fields: List<DetailField>,
    val flow: LinkFlow,
    /** الطرف التاني في حوالة داخلة لسه ما اتقررش ⇒ كارت «مَن هذا؟» (`TransferParty`). */
    val askParty: TransferPartyRef?,
    val transaction: Transaction,
)

fun linkFlowOf(tx: Transaction): LinkFlow = when {
    ruleFor(tx.economicKind).liquidity == Liquidity.INTERNAL -> LinkFlow.NONE
    tx.observedDirection == Direction.IN -> LinkFlow.TRANSFER_IN
    isTransferLike(tx) -> LinkFlow.TRANSFER_OUT
    else -> LinkFlow.PURCHASE
}

fun categoryFieldOf(tx: Transaction, categories: List<Category>): CategoryField? {
    if (ruleFor(tx.economicKind).liquidity == Liquidity.INTERNAL) return null
    val cat = tx.categoryId?.let { id -> categories.firstOrNull { it.id == id } }
    val status = when {
        cat == null -> CategoryStatus.NONE
        tx.categoryConfirmed || tx.reviewState == ReviewState.CONFIRMED -> CategoryStatus.CONFIRMED
        else -> CategoryStatus.SUGGESTED
    }
    return CategoryField(cat?.name, status, cat?.id)
}

/**
 * [decidedParties] مفاتيح الأطراف اللي ليها قرار (`ManageTransfers.zone().rows[].decision`) — الطرف اللي ليه قرار ما بيتسألش تاني.
 * [merchants] التجار المحفوظين (`ManageRules.listMerchants`) — المطابقة بالاسم المطبّع والأسماء البديلة (`merchantIndex` من `core`).
 */
fun detailView(
    tx: Transaction,
    categories: List<Category>,
    wallets: List<Wallet>,
    merchants: List<Merchant>,
    decidedParties: Set<String>,
    today: IsoDate,
): DetailView {
    val known = tx.rawMerchantName?.let { merchantIndex(merchants)[normalizeText(it)] }
    val ctx = RowContext(categories, known?.let { mapOf(tx.id to listOf(it.displayName)) }.orEmpty(), wallets)
    val flow = linkFlowOf(tx)
    val rule = ruleFor(tx.economicKind)
    val direction = t(if (tx.observedDirection == Direction.IN) TextKey.OPERATION_DETAIL_IN else TextKey.OPERATION_DETAIL_OUT)
    val walletName = ctx.wallet(tx.walletId)?.name ?: t(TextKey.NOT_AVAILABLE)
    val kindField = if (tx.economicKind == EconomicKind.UNCLASSIFIED) {
        DetailField(t(TextKey.OPERATION_DETAIL_KIND), t(TextKey.OPERATION_DETAIL_KIND_WAITING), FieldChip(t(TextKey.OPERATION_DETAIL_WAITING_CHIP), ChipInk.amber, ChipInk.amberBg))
    } else {
        DetailField(t(TextKey.OPERATION_DETAIL_KIND), rule.label, hint = if (tx.economicKindConfirmed) null else t(TextKey.OPERATION_DETAIL_KIND_ESTIMATED))
    }
    val party = if (flow == LinkFlow.TRANSFER_IN) transferPartyOf(tx)?.takeIf { it.key !in decidedParties } else null
    return DetailView(
        id = tx.id,
        title = titleOf(tx, ctx),
        sub = t(TextKey.OPERATION_DETAIL_SUB, dayLabel(tx.occurredAt, today), direction),
        icon = iconOf(tx, ctx),
        color = colorOf(tx, ctx),
        amountMinor = tx.amountMinor,
        currency = tx.currency,
        tone = toneOf(tx),
        merchantName = tx.rawMerchantName?.takeIf { flow == LinkFlow.PURCHASE && it.isNotBlank() },
        merchant = known,
        unrecorded = false,
        category = categoryFieldOf(tx, categories),
        fields = listOf(DetailField(t(TextKey.OPERATION_DETAIL_WALLET), walletName), kindField),
        flow = flow,
        askParty = party?.takeIf { tx.economicKind == EconomicKind.UNCLASSIFIED || !tx.economicKindConfirmed },
        transaction = tx,
    )
}

/** الرسالة بعد ما تختار تصنيف بإيدك: «تم — «المحل» سيُصنَّف «التصنيف» دائمًا» لو التاجر محفوظ (اتثبّت عليه)، وإلا «صُنّفت». */
fun categoryToast(merchantRemembered: Boolean, title: String, category: String): String =
    if (merchantRemembered) t(TextKey.OPERATION_DETAIL_REMEMBERED, title, category) else t(TextKey.OPERATION_DETAIL_CATEGORIZED, category)
