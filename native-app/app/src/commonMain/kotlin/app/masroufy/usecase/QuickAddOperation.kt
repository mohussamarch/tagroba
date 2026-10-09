package app.masroufy.usecase

import app.masroufy.core.CATEGORY_GROUPS
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.arabicCompare
import app.masroufy.core.factsFromProfile
import app.masroufy.core.presentCategories
import app.masroufy.core.tryParseMoney
import app.masroufy.core.uiText
import app.masroufy.port.CategoryRepository
import app.masroufy.port.ProfileRepository
import app.masroufy.port.WalletRepository

/**
 * لوحة «+» ⇒ «عملية جديدة» (`BottomBar` في النموذج — `AddSheet`/`AddOperation`): صرف · دخل · تحويل بين محافظي، المبلغ، التصنيف (أو نوع الدخل)،
 * ومن أين/إلى أين — والحفظ عن طريق [AddTransaction] (نفس قواعده: النوع مؤكد، والتحويل الداخلي بطرفين، والكاش بيتوسم لوحده).
 * - **التصنيفات السريعة:** الأساسية الظاهرة (مش فرعية ولا مخفية بشرط من ملفك) من مجموعات الصرف، بترتيبها، أول [MAX_QUICK_CATEGORIES].
 * - **أنواع الدخل:** راتب · عمل حر · دعم أو هدية · بيع شخصي (اختيار Claude — النموذج كان فيه «أخرى» ومفيش نوع «أخرى» بيتحفظ: `UNCLASSIFIED`
 *   بيترفض في `AddTransaction`).
 * - المبلغ بيتقري بـ`tryParseMoney` (الأرقام العربي والفواصل) — الشاشة ما بتحوّلش أرقام.
 */
enum class AddKind { OUT, IN, MOVE }

data class AddOperationOptions(
    val currency: Currency,
    val wallets: List<Wallet>,
    val categories: List<Category>,
    val incomeKinds: List<EconomicKind>,
)

data class AddOperationDraft(
    val kind: AddKind,
    val amountText: String,
    val walletId: Id?,
    val toWalletId: Id? = null,
    val categoryId: Id? = null,
    val incomeKind: EconomicKind? = null,
    val name: String = "",
)

sealed interface AddOperationResult {
    data class Saved(val transaction: Transaction) : AddOperationResult

    /** الرسالة بلغة المستخدم — جنب الخانة. */
    data class Invalid(val message: String) : AddOperationResult
}

const val MAX_QUICK_CATEGORIES = 8

val QUICK_INCOME_KINDS = listOf(EconomicKind.SALARY, EconomicKind.FREELANCE, EconomicKind.SUPPORT_GIFT, EconomicKind.PERSONAL_SALE)

class QuickAddOperation(
    private val wallets: WalletRepository,
    private val categories: CategoryRepository,
    private val profile: ProfileRepository,
    private val add: AddTransaction,
    private val currency: Currency,
    private val today: () -> IsoDate,
) {
    suspend fun options(): AddOperationOptions {
        val spendGroups = CATEGORY_GROUPS.filter { it.kind == "spend" }.map { it.key }.toSet()
        val shown = presentCategories(categories.listAll(), factsFromProfile(profile.load()))
            .filter { it.parentId == null && it.active && (it.groupKey == null || it.groupKey in spendGroups) }
            .sortedWith { a, b -> if (a.order != b.order) a.order.compareTo(b.order) else arabicCompare(a.name, b.name) }
            .take(MAX_QUICK_CATEGORIES)
        return AddOperationOptions(currency, wallets.listAll(), shown, QUICK_INCOME_KINDS)
    }

    suspend fun save(draft: AddOperationDraft): AddOperationResult {
        val minor = tryParseMoney(draft.amountText, currency)
        if (minor == null || minor <= 0) return AddOperationResult.Invalid(uiText(TextKey.ADD_AMOUNT_INVALID))
        val walletId = draft.walletId ?: return AddOperationResult.Invalid(uiText(TextKey.ADD_NO_WALLET))
        val kind = when (draft.kind) {
            AddKind.OUT -> EconomicKind.PURCHASE
            AddKind.IN -> draft.incomeKind ?: return AddOperationResult.Invalid(uiText(TextKey.TXN_KIND_REQUIRED))
            AddKind.MOVE -> EconomicKind.INTERNAL_TRANSFER
        }
        return try {
            val txn = add.add(
                NewTransactionInput(
                    amountMinor = minor,
                    occurredAt = today(),
                    walletId = walletId,
                    transferToWalletId = if (draft.kind == AddKind.MOVE) draft.toWalletId else null,
                    economicKind = kind,
                    merchantName = draft.name,
                    categoryId = if (draft.kind == AddKind.OUT) draft.categoryId else null,
                ),
            )
            AddOperationResult.Saved(txn)
        } catch (e: IllegalArgumentException) {
            AddOperationResult.Invalid(e.message ?: uiText(TextKey.ADD_AMOUNT_INVALID))
        }
    }
}
