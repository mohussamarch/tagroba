package app.masroufy.wiring.budgets

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Language
import app.masroufy.core.ReviewState
import app.masroufy.core.Space
import app.masroufy.core.Texts
import app.masroufy.core.Transaction
import app.masroufy.core.UserProfile
import app.masroufy.core.Wallet
import app.masroufy.core.emptyProfile
import app.masroufy.memory.MemoryAccount
import app.masroufy.usecase.LoadOnlineFeeds
import app.masroufy.wiring.SessionLinks
import app.masroufy.wiring.SpaceGraph
import app.masroufy.wiring.SpaceRepositories
import app.masroufy.wiring.memoryEnv
import app.masroufy.wiring.memorySpaceRepositories

/**
 * بيانات وهمية لاختبارات منطقة «الميزانيات والخطط والتصنيفات والقواعد» (من غير أي رقم من بيانات المالك — المستودع عام).
 * النهارده 2026-10-09 ويوم القبض 28 ⇒ الشهر المالي 2026-09-28 … 2026-10-27 واسمه «أكتوبر» (الشهر اللي بيخلص فيه — OVERRIDES §76).
 */
internal const val TODAY = "2026-10-09"

internal val SAUDI = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
internal val EGYPT = Space("eg", "مصر", "EG", Currency.EGP, "2026-02-01T00:00:00.000Z")

internal val FOOD = Category("c-food", null, "مطاعم وقهوة", "utensils", "#C76A2A", "#F0A06A", active = true, order = 1, groupKey = "food")
internal val COFFEE = Category("c-coffee", "c-food", "قهوة ومشروبات", "coffee", "#D8834A", "#F4B88A", active = true, order = 1)
internal val GROCERY = Category("c-groc", null, "بقالة وسوبرماركت", "shopping-basket", "#277A45", "#6FCB8E", active = true, order = 2, groupKey = "food")
internal val CAR = Category("c-car", null, "السيارة", "car", "#2E6DB8", "#7FB0EE", active = true, order = 3, groupKey = "transport", requires = "hasCar")

internal val BANK = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 500_000, "2026-01-01")

internal fun profile(payday: Int = 28): UserProfile = emptyProfile().copy(displayName = "تجربة", payday = payday)

private var seq = 0

/** مصروف شراء مؤكد بتصنيفه (أو بنوع تاني لاختبار «غير متاح»). */
internal fun spend(
    date: String,
    amountMinor: Halalas,
    categoryId: String?,
    kind: EconomicKind = EconomicKind.PURCHASE,
    merchant: String? = null,
    /** نوع «من غير تصنيف» **مؤكد** ⇒ حالة الاستخدام ما بتخمّنش نوعه ⇒ المصروف «غير متاح». */
    kindConfirmed: Boolean = kind != EconomicKind.UNCLASSIFIED,
): Transaction {
    seq += 1
    return Transaction(
        id = "t-$seq", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind,
        economicKindConfirmed = kindConfirmed, observedDirection = Direction.OUT, amountMinor = amountMinor,
        currency = Currency.SAR, categoryConfirmed = categoryId != null, excludedFromBudget = false,
        reviewState = if (kind == EconomicKind.UNCLASSIFIED) ReviewState.NEEDS_REVIEW else ReviewState.CONFIRMED,
        isCashTagged = false, createdAt = "${date}T08:00:00.000Z", updatedAt = "${date}T08:00:00.000Z",
        categoryId = categoryId, walletId = BANK.id, rawMerchantName = merchant,
    )
}

/** بلد واحدة بمستودعات الذاكرة + التجميع كله (`SpaceGraph`) — المنطقة بتاخد حالات استخدامها من [SpaceGraph.budgets]. */
internal class BudgetsWorld(
    categories: List<Category> = listOf(FOOD, COFFEE, GROCERY),
    transactions: List<Transaction> = emptyList(),
    wallets: List<Wallet> = listOf(BANK),
    profile: UserProfile? = profile(),
    space: Space = SAUDI,
) {
    val repos: SpaceRepositories = memorySpaceRepositories(wallets, categories, transactions, profile)
    private val env = memoryEnv(TODAY)
    private val session = object : SessionLinks {
        override val account = MemoryAccount()
        override fun spaces() = listOf(space to repos)
        override fun switchSpace(spaceId: String): Boolean = true
    }
    val graph = SpaceGraph(space, repos, env, session, LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis))
    val deps get() = graph.budgets
}

/** كل اختبار بيبدأ بالفصحى (السعودية) — والمصري بيتجرب صراحة. */
internal fun resetTexts(variant: ArabicVariant = ArabicVariant.MSA) {
    Texts.language = Language.AR
    Texts.arabicVariant = variant
}
