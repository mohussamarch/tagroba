package app.masroufy.usecase

import app.masroufy.core.Budget
import app.masroufy.core.Category
import app.masroufy.core.CategoryBudget
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.Merchant
import app.masroufy.core.Person
import app.masroufy.core.RecurringItem
import app.masroufy.core.ReviewState
import app.masroufy.core.Space
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.emptyProfile
import app.masroufy.core.normalizeText
import app.masroufy.core.periodForDate
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryAlertDismissalStore
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryConversationStore
import app.masroufy.memory.MemoryForgottenStore
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryMessageStore
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTopicStore
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryUnknownStore
import app.masroufy.memory.MemoryUserSettingsStore
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.Clock

/**
 * عالم اختبار المساعد — **كله مخترع** (المستودع عام): حساب سعودي، النهارده 2026-10-10، الراتب يوم 28 ⇒ الشهر الحالي 2026-09-28 … 2026-10-27.
 * قهوة في «مقهى المرسى» ٤ مرات (٨٥ ر.س من سقف ١٠٠ ⇒ عند حد التنبيه ٨٠٪) · بقالة كاش ٣٠٠ · راتب ١٠٬٠٠٠ · فاتورة كهرباء ميعادها عدّى.
 */
class AssistantWorld(salaried: Boolean = true, now: String = "2026-10-10T09:00:00.000Z") {
    val today = now.take(10)
    val categories: List<Category> = listOf(
        Category("c-food", null, "مطاعم ومقاهي", "food", "#000", "#fff", true, 1),
        Category("c-coffee", "c-food", "قهوة", "coffee", "#000", "#fff", true, 2),
        Category("c-groceries", null, "بقالة", "cart", "#000", "#fff", true, 3),
        Category("c-transport", null, "مواصلات", "car", "#000", "#fff", true, 4),
    )
    val coffeeId = "c-coffee"
    val groceryId = "c-groceries"
    val period = periodForDate(today, 28)

    val bank = Wallet("w-bank", "الراجحي", Currency.SAR, "bank", 500_000, "2026-01-01")
    val cash = Wallet("w-cash", "كاش", Currency.SAR, "cash", 50_000, "2026-01-01")
    val marsa = Merchant("m-marsa", "مقهى المرسى", normalizeText("مقهى المرسى"), aliases = listOf(normalizeText("Al Marsa")), verifiedCategoryId = coffeeId)
    val people = listOf(Person("p-ahmed", "أحمد"), Person("p-sara", "سارة"), Person("p-khaled", "خالد"))
    val elec = RecurringItem("r-elec", "فاتورة الكهرباء", "name:كهرباء", "bill", 1, 38_000, Currency.SAR, "2026-10-03", true, true)

    fun txn(id: String, date: String, minor: Long, dir: Direction = Direction.OUT, kind: EconomicKind = EconomicKind.PURCHASE, category: String? = null, merchant: String? = null, wallet: String = bank.id, confirmed: Boolean = true) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = confirmed, observedDirection = dir,
        amountMinor = minor, currency = Currency.SAR, categoryConfirmed = category != null, excludedFromBudget = false, reviewState = ReviewState.CONFIRMED,
        isCashTagged = wallet == cash.id, createdAt = "2026-10-01T00:00:00.000Z", updatedAt = "2026-10-01T00:00:00.000Z", walletId = wallet, categoryId = category, merchantId = merchant,
    )

    val txns = MemoryTransactionRepository(
        listOf(
            txn("t-salary", "2026-09-28", 1_000_000, Direction.IN, EconomicKind.SALARY),
            txn("t-c1", "2026-09-30", 2_500, category = coffeeId, merchant = marsa.id),
            txn("t-c2", "2026-10-02", 2_500, category = coffeeId, merchant = marsa.id),
            txn("t-c3", "2026-10-05", 2_500, category = coffeeId, merchant = marsa.id),
            txn("t-c4", "2026-10-08", 1_000, category = coffeeId, merchant = marsa.id),
            txn("t-groc", "2026-10-06", 30_000, category = groceryId, wallet = cash.id),
            txn("t-old", "2026-09-10", 1_500, category = coffeeId, merchant = marsa.id),
        ),
    )
    val catRepo = MemoryCategoryRepository(categories)
    val allocations = MemoryAllocationRepository()
    val budgets = MemoryBudgetRepository(
        listOf(Budget("b-1", period.key, period.start, period.end, 200_000, 80, "2026-09-28T00:00:00.000Z", "2026-09-28T00:00:00.000Z")),
        listOf(CategoryBudget("cb-coffee", "b-1", coffeeId, 10_000, true, 80)),
    )
    val wallets = MemoryWalletRepository(listOf(bank, cash))
    val merchants = MemoryMerchantRepository(listOf(marsa))
    val personRepo = MemoryPersonRepository(people)
    val recurring = MemoryRecurringRepository(listOf(elec))
    val clock: Clock = FixedClock(now)
    val profiles = MemoryProfileRepository(emptyProfile().copy(payday = 28, displayName = "محمد"))
    val incomeRepo = MemoryIncomeSourceRepository(
        if (salaried) listOf(IncomeSource("i-job", "شركة الأفق", normalizeText("شركة الأفق"), IncomeSourceKind.JOB, Currency.SAR, "2025-01-01", expectedDayOfMonth = 28, expectedMinor = 1_000_000))
        else listOf(IncomeSource("i-rent", "إيجار الشقة", normalizeText("إيجار الشقة"), IncomeSourceKind.RENT, Currency.SAR, "2025-01-01", expectedDayOfMonth = 5)),
    )
    private val profileUse = ManageProfile(ManageProfileDeps(profiles, MemoryAccount(), clock, incomeRepo))
    val obligations = MemoryObligationRepository()
    val settlements = MemorySettlementRepository()
    val ids = SequentialIdGenerator()

    val home = LoadHomeScreen(LoadHomeScreenDeps(txns, catRepo, allocations, budgets))
    val money = LoadMoneySummary(LoadMoneySummaryDeps(txns, catRepo, allocations))
    val budget = LoadBudgetScreen(LoadBudgetScreenDeps(txns, catRepo, allocations, budgets))
    val cashSummary = LoadCashSummary(LoadCashSummaryDeps(wallets, txns, allocations, catRepo))
    val categorySpend = LoadCategorySpend(LoadCategorySpendDeps(txns, catRepo, allocations))
    val sources = AssistantSources(
        wallets = wallets, txns = txns, profile = profiles, home = home, money = money,
        history = LoadHomeHistory(LoadHomeHistoryDeps(txns, allocations, catRepo)), budget = budget, cash = cashSummary,
        categorySpend = categorySpend, lastAt = FindLastAtMerchant(txns, wallets),
        personAcross = PersonAcrossSpaces(listOf(PersonSpaceBook(Space(DEFAULT_SPACE_ID, "السعودية", "SA", Currency.SAR, "2025-01-01"), obligations, settlements))),
        incomeSources = ManageIncomeSources(ManageIncomeSourcesDeps(incomeRepo, profileUse, MemoryUnitOfWork(listOf(incomeRepo)), ids, clock)),
    )
    val stores = AssistantStores(
        MemoryConversationStore(), MemoryMessageStore(), MemoryTopicStore(), MemoryForgottenStore(), MemoryUnknownStore(), MemoryUserSettingsStore(),
        MemoryAlertDismissalStore(),
    )
    val people4 = ManagePeople(
        ManagePeopleDeps(personRepo, obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, txns, PassthroughUnitOfWork(), ids, clock),
    )
    val deps = AssistantDeps(
        stores, sources, AssistLexiconSource(catRepo, merchants, personRepo, wallets, recurring = recurring), ids, clock,
        add = AddTransaction(AddTransactionDeps(txns, wallets, ids, clock)), people = people4, allocations = allocations,
    )
    val chat = AssistantChat(deps)
    val space = Space(DEFAULT_SPACE_ID, "السعودية", "SA", Currency.SAR, "2025-01-01")

    fun ctx(nowIso: String = "${today}T09:00:00.000Z", hour: Int = 12) = AssistContext(nowIso, nowIso.take(10), hour, space)
}
