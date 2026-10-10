package app.masroufy.core

/**
 * أسامي الشاشات في ردود المساعد «مصروفي» (§78-١ «يوديك للشاشة») — نفس أسامي النموذج التفاعلي (`KOTLIN-MAP.md`). فصحى · مصري ·
 * إنجليزي (كتابة Claude ومستني مراجعة المالك زي باقي الجدول §40).
 */
internal val MSA_ASSIST_SCREEN_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ASSIST_SCREEN_HOME to "الرئيسية", TextKey.ASSIST_SCREEN_CASH to "تفاصيل الكاش", TextKey.ASSIST_SCREEN_NOTIFICATIONS to "الإشعارات",
    TextKey.ASSIST_SCREEN_NOTIFICATION_SETTINGS to "إعدادات الإشعارات", TextKey.ASSIST_SCREEN_CALENDAR to "التقويم",
    TextKey.ASSIST_SCREEN_PROFILE_QUESTION to "أكمل ملفك", TextKey.ASSIST_SCREEN_OPERATIONS to "العمليات", TextKey.ASSIST_SCREEN_OPERATION_FILTERS to "تصفية العمليات",
    TextKey.ASSIST_SCREEN_REVIEW_QUEUE to "المراجعة", TextKey.ASSIST_SCREEN_ADD_OPERATION to "إضافة عملية", TextKey.ASSIST_SCREEN_BUDGETS to "الميزانيات",
    TextKey.ASSIST_SCREEN_CATEGORY_BUDGET to "ميزانية التصنيف", TextKey.ASSIST_SCREEN_DUES to "المستحقات", TextKey.ASSIST_SCREEN_DEBTS to "الديون",
    TextKey.ASSIST_SCREEN_OWED_TO_YOU to "لك", TextKey.ASSIST_SCREEN_YOU_OWE to "عليك", TextKey.ASSIST_SCREEN_ROSCAS to "الجمعيات",
    TextKey.ASSIST_SCREEN_INSTALLMENTS to "الأقساط", TextKey.ASSIST_SCREEN_SUBSCRIPTIONS to "الاشتراكات", TextKey.ASSIST_SCREEN_TRANSFERS to "التحويلات",
    TextKey.ASSIST_SCREEN_SPACE_TRANSFER to "التحويل بين البلدين", TextKey.ASSIST_SCREEN_PROJECTS to "المشاريع", TextKey.ASSIST_SCREEN_MERCHANT to "صفحة التاجر",
    TextKey.ASSIST_SCREEN_BANK_SMS to "رسائل البنك", TextKey.ASSIST_SCREEN_BANK_SMS_SETTINGS to "إعدادات رسائل البنك", TextKey.ASSIST_SCREEN_SMS_PASTE to "الصق رسالة",
    TextKey.ASSIST_SCREEN_STATEMENT_IMPORT to "كشف الحساب", TextKey.ASSIST_SCREEN_IMPORT_BATCHES to "دفعات الاستيراد", TextKey.ASSIST_SCREEN_PEOPLE to "الأشخاص",
    TextKey.ASSIST_SCREEN_PERSON_PROFILE to "ملف الشخص", TextKey.ASSIST_SCREEN_ADD_PERSON to "شخص جديد", TextKey.ASSIST_SCREEN_EVENTS to "الأحداث",
    TextKey.ASSIST_SCREEN_EVENT_PREP to "التجهيزات", TextKey.ASSIST_SCREEN_NUQOOT to "النقوط", TextKey.ASSIST_SCREEN_INVESTMENT to "الاستثمار",
    TextKey.ASSIST_SCREEN_ASSET_DETAIL to "تفاصيل الأصل", TextKey.ASSIST_SCREEN_ZAKAT to "الزكاة", TextKey.ASSIST_SCREEN_ZAKAT_PAY to "دفع الزكاة",
    TextKey.ASSIST_SCREEN_SAVINGS_CALCULATOR to "حاسبة الادخار", TextKey.ASSIST_SCREEN_RETIREMENT to "حاسبة التقاعد", TextKey.ASSIST_SCREEN_INHERITANCE to "حاسبة الورث",
    TextKey.ASSIST_SCREEN_INHERITANCE_SAVED to "حسابات الورث المحفوظة", TextKey.ASSIST_SCREEN_ADVISOR to "التحليلات الذكية",
    TextKey.ASSIST_SCREEN_SAVINGS_GOALS to "خطط الادخار", TextKey.ASSIST_SCREEN_SAVINGS_GROWTH to "كم ستصل", TextKey.ASSIST_SCREEN_MORE to "المزيد",
    TextKey.ASSIST_SCREEN_ACCOUNT to "ملفك", TextKey.ASSIST_SCREEN_INCOME_SOURCES to "مصادر الدخل", TextKey.ASSIST_SCREEN_JOB_CHANGE to "تغيير العمل",
    TextKey.ASSIST_SCREEN_SPACES to "البلدان", TextKey.ASSIST_SCREEN_SPACE_SWITCHER to "تبديل البلد", TextKey.ASSIST_SCREEN_WALLETS to "المحافظ",
    TextKey.ASSIST_SCREEN_WALLET_DETAIL to "تفاصيل المحفظة", TextKey.ASSIST_SCREEN_CATEGORIES to "التصنيفات", TextKey.ASSIST_SCREEN_RULES to "القواعد والتجار",
    TextKey.ASSIST_SCREEN_BACKUP to "النسخة الاحتياطية", TextKey.ASSIST_SCREEN_LOCK to "القفل", TextKey.ASSIST_SCREEN_LANGUAGE to "اللغة",
    TextKey.ASSIST_SCREEN_ISLAMIC to "المحتوى الإسلامي",
)

internal val EGYPTIAN_ASSIST_SCREEN_TEXTS: Map<TextKey, String> = MSA_ASSIST_SCREEN_TEXTS + mapOf(
    TextKey.ASSIST_SCREEN_PROFILE_QUESTION to "كمّل ملفك", TextKey.ASSIST_SCREEN_OPERATION_FILTERS to "فلترة العمليات", TextKey.ASSIST_SCREEN_ADD_OPERATION to "ضيف عملية",
    TextKey.ASSIST_SCREEN_OWED_TO_YOU to "ليك", TextKey.ASSIST_SCREEN_BANK_SMS to "رسايل البنك", TextKey.ASSIST_SCREEN_BANK_SMS_SETTINGS to "إعدادات رسايل البنك",
    TextKey.ASSIST_SCREEN_SMS_PASTE to "الصق رسالة", TextKey.ASSIST_SCREEN_SAVINGS_GROWTH to "هتوصل لكام", TextKey.ASSIST_SCREEN_JOB_CHANGE to "غيّرت شغلي",
    TextKey.ASSIST_SCREEN_SPACE_SWITCHER to "بدّل البلد",
)

internal val ENGLISH_ASSIST_SCREEN_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ASSIST_SCREEN_HOME to "Home", TextKey.ASSIST_SCREEN_CASH to "Cash details", TextKey.ASSIST_SCREEN_NOTIFICATIONS to "Notifications",
    TextKey.ASSIST_SCREEN_NOTIFICATION_SETTINGS to "Notification settings", TextKey.ASSIST_SCREEN_CALENDAR to "Calendar",
    TextKey.ASSIST_SCREEN_PROFILE_QUESTION to "Complete your profile", TextKey.ASSIST_SCREEN_OPERATIONS to "Transactions",
    TextKey.ASSIST_SCREEN_OPERATION_FILTERS to "Filter transactions", TextKey.ASSIST_SCREEN_REVIEW_QUEUE to "Review", TextKey.ASSIST_SCREEN_ADD_OPERATION to "Add a transaction",
    TextKey.ASSIST_SCREEN_BUDGETS to "Budgets", TextKey.ASSIST_SCREEN_CATEGORY_BUDGET to "Category budget", TextKey.ASSIST_SCREEN_DUES to "Dues",
    TextKey.ASSIST_SCREEN_DEBTS to "Debts", TextKey.ASSIST_SCREEN_OWED_TO_YOU to "Owed to you", TextKey.ASSIST_SCREEN_YOU_OWE to "You owe",
    TextKey.ASSIST_SCREEN_ROSCAS to "Roscas", TextKey.ASSIST_SCREEN_INSTALLMENTS to "Installments", TextKey.ASSIST_SCREEN_SUBSCRIPTIONS to "Subscriptions",
    TextKey.ASSIST_SCREEN_TRANSFERS to "Transfers", TextKey.ASSIST_SCREEN_SPACE_TRANSFER to "Transfer between countries", TextKey.ASSIST_SCREEN_PROJECTS to "Projects",
    TextKey.ASSIST_SCREEN_MERCHANT to "Merchant page", TextKey.ASSIST_SCREEN_BANK_SMS to "Bank messages", TextKey.ASSIST_SCREEN_BANK_SMS_SETTINGS to "Bank message settings",
    TextKey.ASSIST_SCREEN_SMS_PASTE to "Paste a message", TextKey.ASSIST_SCREEN_STATEMENT_IMPORT to "Statement import", TextKey.ASSIST_SCREEN_IMPORT_BATCHES to "Import batches",
    TextKey.ASSIST_SCREEN_PEOPLE to "People", TextKey.ASSIST_SCREEN_PERSON_PROFILE to "Person profile", TextKey.ASSIST_SCREEN_ADD_PERSON to "New person",
    TextKey.ASSIST_SCREEN_EVENTS to "Events", TextKey.ASSIST_SCREEN_EVENT_PREP to "Event prep", TextKey.ASSIST_SCREEN_NUQOOT to "Nuqoot",
    TextKey.ASSIST_SCREEN_INVESTMENT to "Investment", TextKey.ASSIST_SCREEN_ASSET_DETAIL to "Asset details", TextKey.ASSIST_SCREEN_ZAKAT to "Zakat",
    TextKey.ASSIST_SCREEN_ZAKAT_PAY to "Pay zakat", TextKey.ASSIST_SCREEN_SAVINGS_CALCULATOR to "Savings calculator", TextKey.ASSIST_SCREEN_RETIREMENT to "Retirement calculator",
    TextKey.ASSIST_SCREEN_INHERITANCE to "Inheritance calculator", TextKey.ASSIST_SCREEN_INHERITANCE_SAVED to "Saved inheritance cases",
    TextKey.ASSIST_SCREEN_ADVISOR to "Smart insights", TextKey.ASSIST_SCREEN_SAVINGS_GOALS to "Savings goals", TextKey.ASSIST_SCREEN_SAVINGS_GROWTH to "Savings growth",
    TextKey.ASSIST_SCREEN_MORE to "More", TextKey.ASSIST_SCREEN_ACCOUNT to "Your profile", TextKey.ASSIST_SCREEN_INCOME_SOURCES to "Income sources",
    TextKey.ASSIST_SCREEN_JOB_CHANGE to "I changed jobs", TextKey.ASSIST_SCREEN_SPACES to "Countries", TextKey.ASSIST_SCREEN_SPACE_SWITCHER to "Switch country",
    TextKey.ASSIST_SCREEN_WALLETS to "Wallets", TextKey.ASSIST_SCREEN_WALLET_DETAIL to "Wallet details", TextKey.ASSIST_SCREEN_CATEGORIES to "Categories",
    TextKey.ASSIST_SCREEN_RULES to "Rules and merchants", TextKey.ASSIST_SCREEN_BACKUP to "Backup", TextKey.ASSIST_SCREEN_LOCK to "Lock",
    TextKey.ASSIST_SCREEN_LANGUAGE to "Language", TextKey.ASSIST_SCREEN_ISLAMIC to "Islamic content",
)
