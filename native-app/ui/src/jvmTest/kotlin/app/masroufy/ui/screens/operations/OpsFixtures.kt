package app.masroufy.ui.screens.operations

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.periodForDate
import app.masroufy.usecase.TransactionsScreenData

/** بيانات وهمية لاختبارات حالات شاشات «العمليات» (مش بيانات حقيقية — المستودع عام). */
internal object Fx {
    const val TODAY: IsoDate = "2026-10-09"
    val food = Category("c-food", null, "مطاعم وقهوة", "utensils", "#A36A21", "#E0B070", active = true, order = 1, groupKey = "food")
    val shop = Category("c-shop", null, "بقالة وسوبرماركت", "shopping-basket", "#3866A7", "#9DB7E0", active = true, order = 2, groupKey = "food")
    val hidden = Category("c-old", null, "قديم مخفي", "tag", "#637570", "#9AA8A3", active = false, order = 9, groupKey = "food")
    val child = Category("c-coffee", "c-food", "قهوة", "coffee", "#A36A21", "#E0B070", active = true, order = 3)
    val bank = Wallet("w-bank", "حساب الراتب", Currency.SAR, "bank", 100_000, "2026-01-01")
    val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 20_000, "2026-01-01")

    fun tx(
        id: String,
        date: IsoDate = TODAY,
        amount: Halalas = 4_200,
        direction: Direction = Direction.OUT,
        kind: EconomicKind = EconomicKind.PURCHASE,
        category: String? = food.id,
        wallet: String? = bank.id,
        merchant: String? = "مطعم الريف",
        review: ReviewState = ReviewState.CONFIRMED,
        confirmedKind: Boolean = true,
        confirmedCategory: Boolean = true,
        description: String? = null,
        toWallet: String? = null,
        order: Int = 0,
    ) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = order, economicKind = kind, economicKindConfirmed = confirmedKind,
        observedDirection = direction, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = confirmedCategory, excludedFromBudget = false,
        reviewState = review, isCashTagged = false, createdAt = "2026-10-09T08:00:00.000Z", updatedAt = "2026-10-09T08:00:00.000Z",
        categoryId = category, walletId = wallet, rawMerchantName = merchant, rawDescription = description, transferToWalletId = toWallet,
    )

    fun screen(
        transactions: List<Transaction>,
        income: Halalas? = 1_250_000,
        expense: Halalas? = 665_000,
        estimated: Int = 0,
        needsReview: Int = 0,
        merchants: Map<String, List<String>> = emptyMap(),
        tags: Map<String, List<String>> = emptyMap(),
        today: IsoDate = TODAY,
    ) = TransactionsScreenData(
        period = periodForDate(today, 28),
        periodRange = "",
        transactions = transactions,
        categories = listOf(food, shop, hidden, child),
        tagNamesByTransaction = tags,
        merchantNamesByTransaction = merchants,
        incomeMinor = income,
        expenseMinor = expense,
        remainingMinor = null,
        savingsRatePercent = null,
        unclassifiedCount = needsReview,
        estimatedCount = estimated,
        needsReviewCount = needsReview,
        totalCount = transactions.size,
    )
}
