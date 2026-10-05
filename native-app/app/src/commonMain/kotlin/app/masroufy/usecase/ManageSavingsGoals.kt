package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.GoalContribution
import app.masroufy.core.GoalProgress
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.SavingsGoal
import app.masroufy.core.SavingsGoalError
import app.masroufy.core.TextKey
import app.masroufy.core.checkSavingsGoal
import app.masroufy.core.goalProgress
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.manualSavedMinor
import app.masroufy.core.uiText
import app.masroufy.core.walletBalancesOn
import app.masroufy.port.Clock
import app.masroufy.port.GoalContributionRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.SavingsGoalRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * «خطة الادخار» (المساعد المالي §68): إنشاء · تعديل · أرشفة · تسجيل إيداع يدوي · ملخص الخطط.
 * الخطة على الحساب، والمحفظة المربوطة جوه بلد ([GoalLedger]) — رصيدها النهارده = المدّخر (اختيار Claude: رصيدها كله).
 */

/** محافظ وعمليات بلد — عشان رصيد المحفظة المربوطة. */
data class GoalLedger(val spaceId: String, val wallets: WalletRepository, val txns: TransactionRepository)

data class GoalInput(
    val name: String,
    val targetMinor: Halalas,
    val currency: Currency,
    val startDate: IsoDate,
    val targetDate: IsoDate,
    val linkedWalletId: Id? = null,
    val linkedSpaceId: String? = null,
)

data class ManageSavingsGoalsDeps(
    val goals: SavingsGoalRepository,
    val contributions: GoalContributionRepository,
    val ids: IdGenerator,
    val clock: Clock,
    /** بلاد الحساب — عشان نتأكد إن المحفظة المربوطة موجودة وبعملة الخطة. */
    val ledgers: List<GoalLedger> = emptyList(),
)

class ManageSavingsGoals(private val deps: ManageSavingsGoalsDeps) {
    private suspend fun find(id: Id): SavingsGoal = deps.goals.listAll().firstOrNull { it.id == id } ?: throw SavingsGoalError(uiText(TextKey.GOAL_NOT_FOUND))

    private suspend fun checkWallet(input: GoalInput) {
        val walletId = input.linkedWalletId ?: return
        val ledger = deps.ledgers.firstOrNull { it.spaceId == input.linkedSpaceId } ?: throw SavingsGoalError(uiText(TextKey.GOAL_WALLET_NOT_FOUND))
        val wallet = ledger.wallets.findById(walletId) ?: throw SavingsGoalError(uiText(TextKey.GOAL_WALLET_NOT_FOUND))
        if (wallet.currency != input.currency) throw SavingsGoalError(uiText(TextKey.GOAL_WALLET_CURRENCY))
    }

    suspend fun create(input: GoalInput): SavingsGoal {
        checkWallet(input)
        val now = deps.clock.nowIso()
        val goal = checkSavingsGoal(
            SavingsGoal(deps.ids.next("goal"), input.name, input.targetMinor, input.currency, input.startDate, input.targetDate, input.linkedWalletId, input.linkedSpaceId, false, now, now),
        )
        deps.goals.save(goal)
        return goal
    }

    /** التعديل بيحتفظ بتاريخ الإنشاء والأرشفة. التحويل من يدوي لمربوط بيسيب الإيداعات متخزنة (ما بتتحسبش وهي مربوطة). */
    suspend fun edit(id: Id, input: GoalInput): SavingsGoal {
        val old = find(id)
        checkWallet(input)
        val goal = checkSavingsGoal(
            old.copy(
                name = input.name, targetMinor = input.targetMinor, currency = input.currency, startDate = input.startDate, targetDate = input.targetDate,
                linkedWalletId = input.linkedWalletId, linkedSpaceId = input.linkedSpaceId, updatedAt = deps.clock.nowIso(),
            ),
        )
        deps.goals.save(goal)
        return goal
    }

    /** أرشفة (أو رجوع منها) — مفيش مسح: الخطة فيها إيداعات اتسجلت. */
    suspend fun archive(id: Id, archived: Boolean = true): SavingsGoal {
        val goal = find(id).copy(archived = archived, updatedAt = deps.clock.nowIso())
        deps.goals.save(goal)
        return goal
    }

    /**
     * النجمة ⭐ (رد المالك، صفحة الضبط 2026-10-05): الخطة اللي habitVsGoal بيقيس عليها. **خطة واحدة بس في الحساب** — النجمة على
     * خطة بتشيلها من أي خطة تانية في نفس الكتابة. [starred] false ⇒ بتتشال من الخطة دي بس. الخطة المؤرشفة ما تاخدش نجمة
     * (ولو اتأرشفت وهي عليها نجمة، النجمة بتفضل بس المساعد بيتجاهلها لحد ما ترجع — `goalForHabit`).
     */
    suspend fun star(id: Id, starred: Boolean = true): SavingsGoal {
        val all = deps.goals.listAll()
        val goal = all.firstOrNull { it.id == id } ?: throw SavingsGoalError(uiText(TextKey.GOAL_NOT_FOUND))
        if (starred && goal.archived) throw SavingsGoalError(uiText(TextKey.GOAL_ARCHIVED))
        val now = deps.clock.nowIso()
        val updated = goal.copy(starred = starred, updatedAt = now)
        val others = if (!starred) emptyList() else all.filter { it.id != id && it.starred }.map { it.copy(starred = false, updatedAt = now) }
        deps.goals.saveMany(listOf(updated) + others)
        return updated
    }

    /** إيداع يدوي (الخطة اليدوية بس، ومش مؤرشفة). موجب دايمًا. */
    suspend fun recordContribution(goalId: Id, date: IsoDate, amountMinor: Halalas, note: String? = null): GoalContribution {
        val goal = find(goalId)
        if (goal.archived) throw SavingsGoalError(uiText(TextKey.GOAL_ARCHIVED))
        if (!goal.manual) throw SavingsGoalError(uiText(TextKey.GOAL_CONTRIBUTION_LINKED))
        if (!isValidIsoDate(date)) throw SavingsGoalError(uiText(TextKey.GOAL_CONTRIBUTION_DATE))
        if (amountMinor <= 0 || amountMinor > MAX_SAFE_HALALAS) throw SavingsGoalError(uiText(TextKey.GOAL_CONTRIBUTION_POSITIVE))
        val c = GoalContribution(deps.ids.next("goalc"), goalId, date, amountMinor, deps.clock.nowIso(), note?.trim()?.takeIf { it.isNotEmpty() })
        deps.contributions.save(c)
        return c
    }

    suspend fun removeContribution(id: Id) {
        if (deps.contributions.listAll().none { it.id == id }) throw SavingsGoalError(uiText(TextKey.GOAL_CONTRIBUTION_NOT_FOUND))
        deps.contributions.remove(id)
    }
}

data class LoadGoalsOverviewDeps(
    val goals: SavingsGoalRepository,
    val contributions: GoalContributionRepository,
    val ledgers: List<GoalLedger> = emptyList(),
)

/** ملخص الخطط النهارده: لكل خطة المدّخر والمفروض وقدام/ورا والمطلوب في الشهر ([GoalProgress]). أي رصيد مش معروف ⇒ «غير متاح». */
class LoadGoalsOverview(private val deps: LoadGoalsOverviewDeps) {
    suspend fun load(today: IsoDate, includeArchived: Boolean = false): List<GoalProgress> {
        val contributions = deps.contributions.listAll()
        return deps.goals.listAll().filter { includeArchived || !it.archived }.sortedWith(compareBy<SavingsGoal> { it.targetDate }.thenBy { it.id }).map { g ->
            if (g.manual) {
                goalProgress(g, manualSavedMinor(contributions, g.id, today), manualSavedMinor(contributions, g.id, g.startDate), today)
            } else {
                goalProgress(g, linkedBalance(g, today), linkedBalance(g, minOf(today, g.startDate)), today)
            }
        }
    }

    /** رصيد المحفظة المربوطة آخر اليوم — null لو البلد أو المحفظة مش موجودة، أو عملتها غير عملة الخطة، أو لسه ما اتفتحتش يومها. */
    private suspend fun linkedBalance(g: SavingsGoal, date: IsoDate): Halalas? {
        val ledger = deps.ledgers.firstOrNull { it.spaceId == g.linkedSpaceId } ?: return null
        val wallet = ledger.wallets.findById(g.linkedWalletId ?: return null) ?: return null
        if (wallet.currency != g.currency || wallet.openingAt > date) return null
        val txns = ledger.txns.listByDateRange(wallet.openingAt, date)
        return walletBalancesOn(listOf(wallet), txns, date)[wallet.id]
    }
}
