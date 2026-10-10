package app.masroufy.ui.shell.ask

import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistTab
import app.masroufy.core.ScreenLink
import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.SheetRoute
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.budgets.CategoriesRoute
import app.masroufy.ui.screens.budgets.CategoryBudgetRoute
import app.masroufy.ui.screens.budgets.RulesRoute
import app.masroufy.ui.screens.budgets.SavingsGoalsRoute
import app.masroufy.ui.screens.dues.DuesDebtsRoute
import app.masroufy.ui.screens.dues.InstallmentDetailRoute
import app.masroufy.ui.screens.dues.InstallmentsRoute
import app.masroufy.ui.screens.dues.RoscaDetailRoute
import app.masroufy.ui.screens.dues.RoscasRoute
import app.masroufy.ui.screens.dues.SubscriptionDetailRoute
import app.masroufy.ui.screens.dues.SubscriptionsRoute
import app.masroufy.ui.screens.home.CalendarRoute
import app.masroufy.ui.screens.home.NotificationsRoute
import app.masroufy.ui.screens.imports.BankSmsRoute
import app.masroufy.ui.screens.imports.BankSmsSettingsRoute
import app.masroufy.ui.screens.imports.ImportBatchesRoute
import app.masroufy.ui.screens.imports.SmsPasteRoute
import app.masroufy.ui.screens.imports.StatementImportRoute
import app.masroufy.ui.screens.investment.AdvisorRoute
import app.masroufy.ui.screens.investment.AssetDetailRoute
import app.masroufy.ui.screens.investment.InheritanceCalculatorRoute
import app.masroufy.ui.screens.investment.InheritanceSavedRoute
import app.masroufy.ui.screens.investment.RetirementCalculatorRoute
import app.masroufy.ui.screens.investment.SavingsCalculatorRoute
import app.masroufy.ui.screens.investment.ZakatPayRoute
import app.masroufy.ui.screens.investment.ZakatRoute
import app.masroufy.ui.screens.more.AccountRoute
import app.masroufy.ui.screens.more.AppSettingsRoute
import app.masroufy.ui.screens.more.BackupRoute
import app.masroufy.ui.screens.more.IncomeSourcesRoute
import app.masroufy.ui.screens.more.MoreRoute
import app.masroufy.ui.screens.more.NotificationSettingsRoute
import app.masroufy.ui.screens.more.SpacesRoute
import app.masroufy.ui.screens.more.WalletDetailRoute
import app.masroufy.ui.screens.more.WalletsRoute
import app.masroufy.ui.screens.onboarding.ProfileQuestionRoute
import app.masroufy.ui.screens.operations.OperationDetailRoute
import app.masroufy.ui.screens.operations.OperationFiltersRoute
import app.masroufy.ui.screens.operations.ReviewQueueRoute
import app.masroufy.ui.screens.operations.SpaceTransferRoute
import app.masroufy.ui.screens.operations.TransfersRoute
import app.masroufy.ui.screens.people.AddPersonSheetRoute
import app.masroufy.ui.screens.people.EventDetailRoute
import app.masroufy.ui.screens.people.EventPrepRoute
import app.masroufy.ui.screens.people.EventsRoute
import app.masroufy.ui.screens.people.OwedToYouRoute
import app.masroufy.ui.screens.people.PersonProfileRoute
import app.masroufy.ui.screens.people.ProjectDetailRoute
import app.masroufy.ui.screens.people.ProjectsRoute
import app.masroufy.ui.screens.people.YouOweRoute

/** وين زرار الشاشة في رد المساعد بيودّي: شاشة داخلية · لوحة · تبويب · لوحة «+». */
sealed interface AssistTarget {
    data class Push(val route: Route) : AssistTarget

    data class Open(val sheet: SheetRoute) : AssistTarget

    data class SwitchTab(val tab: Tab) : AssistTarget

    data object AddOperation : AssistTarget
}

/** التبويب اللي المساعد اتفتح منه بلغة المحرك (المزيد مالوش تبويب في الشريط). */
fun assistTabOf(tab: Tab): AssistTab = when (tab) {
    Tab.HOME -> AssistTab.HOME
    Tab.OPERATIONS -> AssistTab.OPERATIONS
    Tab.PEOPLE -> AssistTab.PEOPLE
    Tab.INVESTMENT -> AssistTab.INVESTMENT
}

/**
 * رابط المحرك ([ScreenLink] — الشاشة ومعرّفاتها) ⇒ مكانها في التطبيق. معرّف ناقص ⇒ الشاشة الأم (مش شاشة فاضية). الميزانيات والمستحقات خانات
 * جوه «العمليات» ⇒ التبويب نفسه. ده ربط أسامي بس (مفيش حساب).
 */
fun targetOf(link: ScreenLink): AssistTarget {
    val a = link.args
    fun push(r: Route) = AssistTarget.Push(r)
    fun tab(t: Tab) = AssistTarget.SwitchTab(t)
    return when (link.screen) {
        AssistScreen.HOME, AssistScreen.CASH_DETAILS, AssistScreen.PROFILE_QUESTION -> if (link.screen == AssistScreen.PROFILE_QUESTION) push(ProfileQuestionRoute) else tab(Tab.HOME)
        AssistScreen.NOTIFICATIONS -> push(NotificationsRoute)
        AssistScreen.NOTIFICATION_SETTINGS -> push(NotificationSettingsRoute)
        AssistScreen.CALENDAR -> push(CalendarRoute)
        AssistScreen.OPERATIONS, AssistScreen.BUDGETS, AssistScreen.DUES, AssistScreen.MERCHANT -> tab(Tab.OPERATIONS)
        AssistScreen.OPERATION_FILTERS -> push(OperationFiltersRoute)
        AssistScreen.REVIEW_QUEUE -> push(ReviewQueueRoute)
        AssistScreen.ADD_OPERATION -> AssistTarget.AddOperation
        AssistScreen.CATEGORY_BUDGET -> a["categoryId"]?.let { push(CategoryBudgetRoute(it)) } ?: tab(Tab.OPERATIONS)
        AssistScreen.DEBTS, AssistScreen.DEBT_DETAIL -> push(DuesDebtsRoute())
        AssistScreen.OWED_TO_YOU -> push(OwedToYouRoute)
        AssistScreen.YOU_OWE -> push(YouOweRoute)
        AssistScreen.ROSCAS -> push(RoscasRoute)
        AssistScreen.ROSCA_DETAIL -> push(a["roscaId"]?.let(::RoscaDetailRoute) ?: RoscasRoute)
        AssistScreen.INSTALLMENTS -> push(InstallmentsRoute)
        AssistScreen.INSTALLMENT_DETAIL -> push(a["planId"]?.let(::InstallmentDetailRoute) ?: InstallmentsRoute)
        AssistScreen.SUBSCRIPTIONS -> push(SubscriptionsRoute)
        AssistScreen.SUBSCRIPTION_DETAIL -> push(a["recurringId"]?.let(::SubscriptionDetailRoute) ?: SubscriptionsRoute)
        AssistScreen.TRANSFERS -> push(TransfersRoute)
        AssistScreen.SPACE_TRANSFER -> push(SpaceTransferRoute)
        AssistScreen.PROJECTS -> push(ProjectsRoute)
        AssistScreen.PROJECT_DETAIL -> push(a["projectId"]?.let(::ProjectDetailRoute) ?: ProjectsRoute)
        AssistScreen.BANK_SMS -> push(BankSmsRoute)
        AssistScreen.BANK_SMS_SETTINGS -> push(BankSmsSettingsRoute)
        AssistScreen.SMS_PASTE -> push(SmsPasteRoute)
        AssistScreen.STATEMENT_IMPORT -> push(StatementImportRoute)
        AssistScreen.IMPORT_BATCHES -> push(ImportBatchesRoute)
        AssistScreen.PEOPLE -> tab(Tab.PEOPLE)
        AssistScreen.PERSON_PROFILE -> a["personId"]?.let { push(PersonProfileRoute(it)) } ?: tab(Tab.PEOPLE)
        AssistScreen.ADD_PERSON -> AssistTarget.Open(AddPersonSheetRoute)
        AssistScreen.EVENTS -> push(EventsRoute)
        AssistScreen.EVENT_DETAIL, AssistScreen.NUQOOT -> push(a["eventId"]?.let(::EventDetailRoute) ?: EventsRoute)
        AssistScreen.EVENT_PREP -> push(a["eventId"]?.let(::EventPrepRoute) ?: EventsRoute)
        AssistScreen.INVESTMENT, AssistScreen.SAVINGS_GROWTH -> tab(Tab.INVESTMENT)
        AssistScreen.ASSET_DETAIL -> a["assetId"]?.let { push(AssetDetailRoute(it)) } ?: tab(Tab.INVESTMENT)
        AssistScreen.ZAKAT -> push(ZakatRoute)
        AssistScreen.ZAKAT_PAY -> push(a["yearId"]?.let(::ZakatPayRoute) ?: ZakatRoute)
        AssistScreen.SAVINGS_CALCULATOR -> push(SavingsCalculatorRoute)
        AssistScreen.RETIREMENT_CALCULATOR -> push(RetirementCalculatorRoute)
        AssistScreen.INHERITANCE_CALCULATOR -> push(InheritanceCalculatorRoute)
        AssistScreen.INHERITANCE_SAVED -> push(InheritanceSavedRoute)
        AssistScreen.ADVISOR -> push(AdvisorRoute)
        AssistScreen.SAVINGS_GOALS, AssistScreen.GOAL_DETAIL -> push(SavingsGoalsRoute)
        AssistScreen.MORE -> push(MoreRoute)
        AssistScreen.ACCOUNT -> push(AccountRoute)
        AssistScreen.INCOME_SOURCES, AssistScreen.JOB_CHANGE -> push(IncomeSourcesRoute)
        AssistScreen.SPACES, AssistScreen.SPACE_SWITCHER -> push(SpacesRoute)
        AssistScreen.WALLETS -> push(WalletsRoute)
        AssistScreen.WALLET_DETAIL -> push(a["walletId"]?.let(::WalletDetailRoute) ?: WalletsRoute)
        AssistScreen.CATEGORIES -> push(CategoriesRoute)
        AssistScreen.RULES -> push(RulesRoute)
        AssistScreen.BACKUP -> push(BackupRoute)
        AssistScreen.LOCK, AssistScreen.LANGUAGE, AssistScreen.ISLAMIC_CONTENT -> push(AppSettingsRoute)
        AssistScreen.OPERATION_DETAIL -> a["transactionId"]?.let { push(OperationDetailRoute(it)) } ?: tab(Tab.OPERATIONS)
    }
}
