package app.masroufy.core

/**
 * فهرس الشاشات اللي المساعد بيوديك ليها (§78-١ «بحث بكلامك في فهرس كل شاشة وميزة»). [board] = اسم اللوحة في النموذج التفاعلي
 * (`design-source/prototype/KOTLIN-MAP.md` — نفس اسم الشاشة في كوتلن)، و[navWire] = معرّف النية في التصميم («nav.zakat» …).
 * [tab] = التبويب اللي الشاشة جواه (لأقرب الشاشات لما الكلام ما يقربش من حاجة). [needsEntity] = شاشة تفاصيل بتتفتح بمعرّف (شخص · محفظة …)
 * — ما بتطلعش من الكلمات لوحدها.
 */
enum class AssistScreen(val board: String, val navWire: String, val label: TextKey, val tab: AssistTab, val needsEntity: Boolean = false) {
    HOME("Main", "nav.home", TextKey.ASSIST_SCREEN_HOME, AssistTab.HOME),
    CASH_DETAILS("CashDetails", "nav.cash_details", TextKey.ASSIST_SCREEN_CASH, AssistTab.HOME),
    NOTIFICATIONS("Notifications", "nav.notifications", TextKey.ASSIST_SCREEN_NOTIFICATIONS, AssistTab.HOME),
    NOTIFICATION_SETTINGS("NotificationSettings", "nav.notification_settings", TextKey.ASSIST_SCREEN_NOTIFICATION_SETTINGS, AssistTab.MORE),
    CALENDAR("Calendar", "nav.calendar", TextKey.ASSIST_SCREEN_CALENDAR, AssistTab.HOME),
    PROFILE_QUESTION("ProfileQuestion", "nav.profile_question", TextKey.ASSIST_SCREEN_PROFILE_QUESTION, AssistTab.HOME),
    OPERATIONS("Operations", "nav.operations", TextKey.ASSIST_SCREEN_OPERATIONS, AssistTab.OPERATIONS),
    OPERATION_FILTERS("OperationFilters", "nav.operation_filters", TextKey.ASSIST_SCREEN_OPERATION_FILTERS, AssistTab.OPERATIONS),
    REVIEW_QUEUE("ReviewQueue", "nav.review_queue", TextKey.ASSIST_SCREEN_REVIEW_QUEUE, AssistTab.OPERATIONS),
    ADD_OPERATION("AddOperation", "nav.add_operation", TextKey.ASSIST_SCREEN_ADD_OPERATION, AssistTab.OPERATIONS),
    BUDGETS("Budgets", "nav.budgets", TextKey.ASSIST_SCREEN_BUDGETS, AssistTab.OPERATIONS),
    CATEGORY_BUDGET("CategoryBudget", "nav.category_budget", TextKey.ASSIST_SCREEN_CATEGORY_BUDGET, AssistTab.OPERATIONS, needsEntity = true),
    DUES("Dues", "nav.dues", TextKey.ASSIST_SCREEN_DUES, AssistTab.OPERATIONS),
    DEBTS("DuesDebts", "nav.debts", TextKey.ASSIST_SCREEN_DEBTS, AssistTab.OPERATIONS),
    OWED_TO_YOU("OwedToYou", "nav.owed_to_you", TextKey.ASSIST_SCREEN_OWED_TO_YOU, AssistTab.PEOPLE),
    YOU_OWE("YouOwe", "nav.you_owe", TextKey.ASSIST_SCREEN_YOU_OWE, AssistTab.PEOPLE),
    ROSCAS("Roscas", "nav.roscas", TextKey.ASSIST_SCREEN_ROSCAS, AssistTab.OPERATIONS),
    ROSCA_DETAIL("RoscaDetail", "nav.roscas", TextKey.ASSIST_SCREEN_ROSCAS, AssistTab.OPERATIONS, needsEntity = true),
    INSTALLMENTS("Installments", "nav.installments", TextKey.ASSIST_SCREEN_INSTALLMENTS, AssistTab.OPERATIONS),
    INSTALLMENT_DETAIL("InstallmentDetail", "nav.installments", TextKey.ASSIST_SCREEN_INSTALLMENTS, AssistTab.OPERATIONS, needsEntity = true),
    SUBSCRIPTIONS("Subscriptions", "nav.subscriptions", TextKey.ASSIST_SCREEN_SUBSCRIPTIONS, AssistTab.OPERATIONS),
    SUBSCRIPTION_DETAIL("SubscriptionDetail", "nav.subscriptions", TextKey.ASSIST_SCREEN_SUBSCRIPTIONS, AssistTab.OPERATIONS, needsEntity = true),
    TRANSFERS("Transfers", "nav.transfers", TextKey.ASSIST_SCREEN_TRANSFERS, AssistTab.OPERATIONS),
    SPACE_TRANSFER("SpaceTransfer", "nav.space_transfer", TextKey.ASSIST_SCREEN_SPACE_TRANSFER, AssistTab.MORE),
    PROJECTS("Projects", "nav.projects", TextKey.ASSIST_SCREEN_PROJECTS, AssistTab.MORE),
    PROJECT_DETAIL("ProjectDetail", "nav.projects", TextKey.ASSIST_SCREEN_PROJECTS, AssistTab.MORE, needsEntity = true),
    MERCHANT("MerchantProfile", "nav.merchant", TextKey.ASSIST_SCREEN_MERCHANT, AssistTab.OPERATIONS, needsEntity = true),
    BANK_SMS("BankSms", "nav.bank_sms", TextKey.ASSIST_SCREEN_BANK_SMS, AssistTab.MORE),
    BANK_SMS_SETTINGS("BankSmsSettings", "nav.bank_sms_settings", TextKey.ASSIST_SCREEN_BANK_SMS_SETTINGS, AssistTab.MORE),
    SMS_PASTE("SmsPaste", "nav.bank_sms", TextKey.ASSIST_SCREEN_SMS_PASTE, AssistTab.MORE),
    STATEMENT_IMPORT("StatementImport", "nav.statement_import", TextKey.ASSIST_SCREEN_STATEMENT_IMPORT, AssistTab.MORE),
    IMPORT_BATCHES("ImportBatches", "nav.import_batches", TextKey.ASSIST_SCREEN_IMPORT_BATCHES, AssistTab.MORE),
    PEOPLE("People", "nav.people", TextKey.ASSIST_SCREEN_PEOPLE, AssistTab.PEOPLE),
    PERSON_PROFILE("PersonProfile", "nav.person_profile", TextKey.ASSIST_SCREEN_PERSON_PROFILE, AssistTab.PEOPLE, needsEntity = true),
    ADD_PERSON("AddPersonSheet", "nav.add_person", TextKey.ASSIST_SCREEN_ADD_PERSON, AssistTab.PEOPLE),
    EVENTS("Events", "nav.events", TextKey.ASSIST_SCREEN_EVENTS, AssistTab.PEOPLE),
    EVENT_DETAIL("EventDetail", "nav.events", TextKey.ASSIST_SCREEN_EVENTS, AssistTab.PEOPLE, needsEntity = true),
    EVENT_PREP("EventPrep", "nav.event_prep", TextKey.ASSIST_SCREEN_EVENT_PREP, AssistTab.PEOPLE),
    NUQOOT("NuqootSheet", "nav.nuqoot", TextKey.ASSIST_SCREEN_NUQOOT, AssistTab.PEOPLE),
    INVESTMENT("Investment", "nav.investment", TextKey.ASSIST_SCREEN_INVESTMENT, AssistTab.INVESTMENT),
    ASSET_DETAIL("AssetDetail", "nav.asset_detail", TextKey.ASSIST_SCREEN_ASSET_DETAIL, AssistTab.INVESTMENT, needsEntity = true),
    ZAKAT("Zakat", "nav.zakat", TextKey.ASSIST_SCREEN_ZAKAT, AssistTab.INVESTMENT),
    ZAKAT_PAY("ZakatPay", "nav.zakat_pay", TextKey.ASSIST_SCREEN_ZAKAT_PAY, AssistTab.INVESTMENT),
    SAVINGS_CALCULATOR("SavingsCalculator", "nav.savings_calculator", TextKey.ASSIST_SCREEN_SAVINGS_CALCULATOR, AssistTab.INVESTMENT),
    RETIREMENT_CALCULATOR("RetirementCalculator", "nav.retirement_calculator", TextKey.ASSIST_SCREEN_RETIREMENT, AssistTab.INVESTMENT),
    INHERITANCE_CALCULATOR("InheritanceCalculator", "nav.inheritance_calculator", TextKey.ASSIST_SCREEN_INHERITANCE, AssistTab.INVESTMENT),
    INHERITANCE_SAVED("InheritanceSaved", "nav.inheritance_saved", TextKey.ASSIST_SCREEN_INHERITANCE_SAVED, AssistTab.INVESTMENT),
    ADVISOR("Advisor", "nav.advisor", TextKey.ASSIST_SCREEN_ADVISOR, AssistTab.INVESTMENT),
    SAVINGS_GOALS("SavingsGoals", "nav.savings_goals", TextKey.ASSIST_SCREEN_SAVINGS_GOALS, AssistTab.INVESTMENT),
    GOAL_DETAIL("GoalDetail", "nav.savings_goals", TextKey.ASSIST_SCREEN_SAVINGS_GOALS, AssistTab.INVESTMENT, needsEntity = true),
    SAVINGS_GROWTH("SavingsGrowth", "nav.savings_growth", TextKey.ASSIST_SCREEN_SAVINGS_GROWTH, AssistTab.INVESTMENT),
    MORE("More", "nav.more", TextKey.ASSIST_SCREEN_MORE, AssistTab.MORE),
    ACCOUNT("Account", "nav.account", TextKey.ASSIST_SCREEN_ACCOUNT, AssistTab.MORE),
    INCOME_SOURCES("IncomeSources", "nav.income_sources", TextKey.ASSIST_SCREEN_INCOME_SOURCES, AssistTab.MORE),
    JOB_CHANGE("JobChangeSheet", "nav.job_change", TextKey.ASSIST_SCREEN_JOB_CHANGE, AssistTab.MORE),
    SPACES("Spaces", "nav.spaces", TextKey.ASSIST_SCREEN_SPACES, AssistTab.MORE),
    SPACE_SWITCHER("SpaceSwitcher", "nav.space_switch", TextKey.ASSIST_SCREEN_SPACE_SWITCHER, AssistTab.HOME),
    WALLETS("Wallets", "nav.wallets", TextKey.ASSIST_SCREEN_WALLETS, AssistTab.MORE),
    WALLET_DETAIL("WalletDetail", "nav.wallet_detail", TextKey.ASSIST_SCREEN_WALLET_DETAIL, AssistTab.MORE, needsEntity = true),
    CATEGORIES("Categories", "nav.categories", TextKey.ASSIST_SCREEN_CATEGORIES, AssistTab.MORE),
    RULES("Rules", "nav.rules", TextKey.ASSIST_SCREEN_RULES, AssistTab.MORE),
    BACKUP("Backup", "nav.backup", TextKey.ASSIST_SCREEN_BACKUP, AssistTab.MORE),
    LOCK("AppSettings", "nav.lock", TextKey.ASSIST_SCREEN_LOCK, AssistTab.MORE),
    LANGUAGE("AppSettings", "nav.language", TextKey.ASSIST_SCREEN_LANGUAGE, AssistTab.MORE),
    ISLAMIC_CONTENT("AppSettings", "nav.islamic_content", TextKey.ASSIST_SCREEN_ISLAMIC, AssistTab.MORE),
    DEBT_DETAIL("DebtDetail", "nav.debts", TextKey.ASSIST_SCREEN_DEBTS, AssistTab.PEOPLE, needsEntity = true),
    OPERATION_DETAIL("OperationDetail", "nav.operations", TextKey.ASSIST_SCREEN_OPERATIONS, AssistTab.OPERATIONS, needsEntity = true),
    ;

    /** قسم «الإعدادات» اللي بيتفتح (القفل · اللغة · المحتوى الإسلامي — صفحة واحدة `AppSettings`). */
    val section: String?
        get() = when (this) {
            LOCK -> "lock"
            LANGUAGE -> "language"
            ISLAMIC_CONTENT -> "islamic"
            else -> null
        }

    companion object {
        fun fromBoard(board: String, section: String? = null): AssistScreen? =
            entries.firstOrNull { it.board == board && it.section == section } ?: entries.firstOrNull { it.board == board }
    }
}

/**
 * رابط شاشة في رد المساعد: الشاشة + معرّفات بتتفتح بيها (`personId` · `categoryId` · `section` …). الشاشات بتتبني في مكان تاني — هنا
 * «فين» بس.
 */
data class ScreenLink(val screen: AssistScreen, val args: Map<String, String> = emptyMap()) {
    val board: String get() = screen.board
    val label: String get() = uiText(screen.label)

    companion object {
        fun of(screen: AssistScreen, vararg args: Pair<String, String>): ScreenLink =
            ScreenLink(screen, linkedMapOf(*args).apply { screen.section?.let { put("section", it) } })
    }
}
