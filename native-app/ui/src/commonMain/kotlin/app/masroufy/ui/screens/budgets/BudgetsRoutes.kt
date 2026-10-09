package app.masroufy.ui.screens.budgets

import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.Slots
import app.masroufy.usecase.LoadBudgetScreen
import app.masroufy.usecase.LoadCalendar
import app.masroufy.usecase.LoadGoalsOverview
import app.masroufy.usecase.LoadLeftover
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.ManageCategories
import app.masroufy.usecase.ManageProfile
import app.masroufy.usecase.ManageReservations
import app.masroufy.usecase.ManageRules
import app.masroufy.usecase.ManageSavingsGoals
import app.masroufy.usecase.ReviewHistory
import app.masroufy.usecase.SetBudget

/**
 * منطقة «الميزانيات والخطط والتصنيفات والقواعد» — **منطقة تاسعة** (قرار مكتوب في ARCHITECTURE §31.32): Budgets (خانة جوه مبدّل
 * «العمليات» — [Slots.BUDGETS]) · CategoryBudget · BudgetLimitSheet · SavingsGoals · GoalDetail · GoalEditSheet · Categories ·
 * CategoryEditSheet · Rules. اسم كل شاشة = اسم لوحتها في النموذج (`SCREENS.md`). اللوحات الخاصة بشاشة واحدة (`BudgetLimitSheet` ·
 * `GoalDetail` · `GoalEditSheet` · `CategoryEditSheet`) جوه شاشتها بـ`Sheet(visible, …)`.
 * **الدخول من مناطق تانية:** «العمليات» بترسم [Slots.BUDGETS] · «المزيد» بيفتح [CategoriesRoute] و[RulesRoute] · «الاستثمار» والمستشار
 * والمساعد بيفتحوا [SavingsGoalsRoute] · المساعد بيفتح [CategoryBudgetRoute].
 */
interface BudgetsDeps {
    /** يوم المرتب و«المستحقات في الحد» (`duesInBudget`) ومعلومات ظهور التصنيفات المشروطة. */
    val profile: ManageProfile
    val budgetScreen: LoadBudgetScreen
    val setBudget: SetBudget

    /** «مواعيد قادمة» في الميزانيات — `LoadCalendar.items(النهارده، آخر الفترة، النهارده)`. */
    val calendar: LoadCalendar

    /** «احسبه من فلوسي» — `countUpcomingItem` · `uncount`. */
    val reservations: ManageReservations

    /** «المتبقي تقريبًا بعد المحجوز». */
    val leftover: LoadLeftover

    /** عمليات التصنيف في «ميزانية تصنيف». */
    val transactions: LoadTransactionsScreen
    val goals: ManageSavingsGoals
    val goalsOverview: LoadGoalsOverview
    val categories: ManageCategories
    val rules: ManageRules

    /** «طبّق القواعد على السابق» بمعاينة الأول — `preview` · `applyCategories`. */
    val history: ReviewHistory
}

/** ميزانية تصنيف واحد (`CategoryBudget`) — من أي صف في «الميزانيات» ومن المساعد. */
data class CategoryBudgetRoute(val categoryId: String) : Route {
    override val name = "CategoryBudget"
}

/** خطط الادخار (`SavingsGoals`) — من «الاستثمار» والمستشار والمساعد. */
object SavingsGoalsRoute : Route {
    override val name = "SavingsGoals"
}

/** التصنيفات (`Categories`) — من «المزيد» ومن «أضف فرعيًا» في ميزانية التصنيف. */
object CategoriesRoute : Route {
    override val name = "Categories"
}

/** القواعد والتجار (`Rules`) — من «المزيد» ومن آخر «التصنيفات». */
object RulesRoute : Route {
    override val name = "Rules"
}

fun RouteRegistry.registerBudgets() {
    slot(Slots.BUDGETS) { BudgetsPanel() }
    screen<CategoryBudgetRoute> { r -> CategoryBudgetScreen(r.categoryId) }
    screen<SavingsGoalsRoute> { SavingsGoalsScreen() }
    screen<CategoriesRoute> { CategoriesScreen() }
    screen<RulesRoute> { RulesScreen() }
}

/**
 * روابط لشاشات مناطق تانية لسه ما اتعرّفتش في الأساس (مسارها بتاع منطقتها) — لحد الدمج النهائي بتفتح «قيد البناء» بصراحة
 * (`PendingScreen`)، ووقت الدمج بتتبدّل بمسار المنطقة الحقيقي. **مش متسجلة هنا عمدًا.**
 */
internal object ReviewQueueLink : Route {
    override val name = "ReviewQueue"
}

internal data class OperationDetailLink(val transactionId: String) : Route {
    override val name = "OperationDetail"
}
