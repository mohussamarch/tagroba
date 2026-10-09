package app.masroufy.core

/**
 * كلمات كل شاشة (فصحى · مصري · خليجي · إنجليزي) — بتتوحّد مرة واحدة ([assistNormalize]). الكلمة الواحدة جذر (بأي أداة قبلها وأي لاحقة
 * بعدها، وغلطة إملائية واحدة لو طويلة)، والعبارة كلمات بالترتيب. **شاشات التفاصيل** ([AssistScreen.needsEntity]) مالهاش كلمات هنا —
 * بتتفتح من اسم الشخص/المحفظة/التصنيف نفسه.
 */
internal val SCREEN_WORDS: Map<AssistScreen, List<String>> = mapOf(
    AssistScreen.HOME to listOf("الرئيسيه", "الصفحه الرئيسيه", "الهوم", "home", "main page", "home page"),
    AssistScreen.CASH_DETAILS to listOf("تفاصيل الكاش", "صفحه الكاش", "الكاش", "كاش", "cash"),
    AssistScreen.NOTIFICATIONS to listOf("الاشعارات", "اشعارات", "التنبيهات", "تنبيهات", "الجرس", "نوتيفيكيشن", "notifications", "notification", "alerts"),
    AssistScreen.NOTIFICATION_SETTINGS to listOf(
        "اعدادات الاشعارات", "اعدادات التنبيهات", "اطفي تنبيهات", "اطفي الاشعارات", "اقفل الاشعارات", "اقفل التنبيهات", "سؤال الكاش", "notification settings",
    ),
    AssistScreen.CALENDAR to listOf("التقويم", "تقويم", "الكالندر", "كالندر", "مواعيدي", "الاجنده", "اجنده", "calendar", "agenda"),
    AssistScreen.PROFILE_QUESTION to listOf("كمل ملفي", "اكمل ملفي", "ملفي ناقص", "اكتمال ملفي", "نسبه اكتمال", "complete my profile"),
    AssistScreen.OPERATIONS to listOf("العمليات", "عملياتي", "الحركات", "حركاتي", "سجل مصاريفي", "transactions", "operations"),
    AssistScreen.OPERATION_FILTERS to listOf("فلتر", "فلتره", "صفي العمليات", "تصفيه", "ابحث عن عمليه", "ادور على عمليه", "filter", "search transactions"),
    AssistScreen.REVIEW_QUEUE to listOf("المراجعه", "محتاجه تاكيد", "تبي تاكيد", "يبي تاكيد", "اكد العمليات", "review queue"),
    AssistScreen.ADD_OPERATION to listOf(
        "ضيف عمليه", "اضف عمليه", "عمليه جديده", "اسجل مصروف", "اسجل مصروفا", "سجل مصروف", "سجل دخل", "ضيف مصروف", "add a transaction", "add transaction",
        "new transaction",
    ),
    AssistScreen.BUDGETS to listOf("الميزانيات", "الميزانيه", "ميزانيتي", "السقف", "حدود الصرف", "البادجت", "budgets", "budget"),
    AssistScreen.DUES to listOf("المستحقات", "الالتزامات", "dues", "commitments"),
    AssistScreen.DEBTS to listOf("الديون", "ديون", "السلف", "صفحه الديون", "debts", "loans"),
    AssistScreen.OWED_TO_YOU to listOf("صفحه لك", "صفحه ليك", "المطلوب لي", "اللي ليا عند الناس", "اللي لي عند الناس", "owed to me"),
    AssistScreen.YOU_OWE to listOf("صفحه عليك", "اللي عليا للناس", "اللي علي للناس", "what i owe"),
    AssistScreen.ROSCAS to listOf("الجمعيات", "الجمعيه", "جمعيتي", "roscas", "rosca"),
    AssistScreen.INSTALLMENTS to listOf("الاقساط", "اقساطي", "التقسيط", "التمويل", "installments", "financing"),
    AssistScreen.SUBSCRIPTIONS to listOf("الاشتراكات", "اشتراكاتي", "فواتير البيت", "الفواتير", "subscriptions"),
    AssistScreen.TRANSFERS to listOf("التحويلات", "الحوالات", "زون التحويلات", "حولوا لي", "transfers"),
    AssistScreen.SPACE_TRANSFER to listOf("التحويل بين البلدين", "تحويل لنفسي", "حولت فلوس لمصر", "حولت لمصر", "transfer between my countries"),
    AssistScreen.PROJECTS to listOf("المشاريع", "مشاريعي", "مشروع", "projects"),
    AssistScreen.BANK_SMS to listOf("رسائل البنك", "رسايل البنك", "الرسايل", "الرسائل", "مسجات البنك", "مسجات", "bank messages", "sms"),
    AssistScreen.BANK_SMS_SETTINGS to listOf(
        "اعدادات رسائل البنك", "اعدادات رسايل البنك", "اعدادات الرسايل", "وقف قراءه الرسايل", "وقف قراءه الرسائل", "مرسلين البنك", "sms settings",
    ),
    AssistScreen.SMS_PASTE to listOf("الصق رساله", "الصق الرساله", "paste sms"),
    AssistScreen.STATEMENT_IMPORT to listOf("كشف الحساب", "كشف البنك", "ارفع كشف", "استيراد كشف", "استورد كشف", "كشف", "import a statement", "statement"),
    AssistScreen.IMPORT_BATCHES to listOf("دفعات الاستيراد", "الاستيرادات", "تراجع عن استيراد", "import history"),
    AssistScreen.PEOPLE to listOf("الاشخاص", "الناس", "صحابي", "اصحابي", "معارفي", "people", "contacts"),
    AssistScreen.ADD_PERSON to listOf("ضيف شخص", "اضف شخص", "شخص جديد", "اضف صديق", "ضيف صاحبي", "add a person", "add person"),
    AssistScreen.EVENTS to listOf("الاحداث", "المناسبات", "الفرح", "العزاء", "العزا", "events"),
    AssistScreen.EVENT_PREP to listOf("تجهيزات", "قائمه التجهيز", "التجهيزات", "event prep"),
    AssistScreen.NUQOOT to listOf("النقوط", "نقوط", "نقطه الفرح", "nuqoot"),
    AssistScreen.INVESTMENT to listOf("الاستثمار", "استثمار", "اصولي", "الاصول", "الذهب", "الاسهم", "investment", "invest"),
    AssistScreen.ZAKAT to listOf("الزكاه", "زكاه", "زكاتي", "الحول والنصاب", "الحول", "zakat"),
    AssistScreen.ZAKAT_PAY to listOf("ادفع الزكاه", "دفع الزكاه", "سجل دفع الزكاه", "دفعت زكاتي", "دفعت الزكاه", "pay zakat"),
    AssistScreen.SAVINGS_CALCULATOR to listOf("حاسبه الادخار", "احوش كام في الشهر", "ادخر عشان اوصل", "كم ادخر عشان", "savings calculator"),
    AssistScreen.RETIREMENT_CALCULATOR to listOf("حاسبه التقاعد", "التقاعد", "المعاش", "التامينات", "retirement", "pension"),
    AssistScreen.INHERITANCE_CALCULATOR to listOf("حاسبه الورث", "الورث", "الميراث", "المواريث", "التركه", "inheritance"),
    AssistScreen.INHERITANCE_SAVED to listOf("حسبات الورث المحفوظه", "الحسبات اللي حفظتها", "الحسابات المحفوظه", "saved inheritance"),
    AssistScreen.ADVISOR to listOf("التحليلات الذكيه", "التحليلات", "النصائح", "المستشار", "insights", "advisor"),
    AssistScreen.SAVINGS_GOALS to listOf("الاهداف", "خطط الادخار", "الحصاله", "التحويش", "goals"),
    AssistScreen.SAVINGS_GROWTH to listOf("لو حوشت هوصل لكام", "مقارنه الادخار", "savings growth"),
    AssistScreen.MORE to listOf("المزيد", "الترس", "القائمه", "more"),
    AssistScreen.ACCOUNT to listOf("ملفي", "بروفايلي", "البروفايل", "بياناتي الشخصيه", "حسابي", "profile", "my account"),
    AssistScreen.INCOME_SOURCES to listOf("مصادر الدخل", "فين دخلي", "وين دخلي", "شغلي", "وظيفتي", "income sources"),
    AssistScreen.JOB_CHANGE to listOf("غيرت شغلي", "شغل جديد", "وظيفه جديده", "استقلت", "تركت الدوام", "سبت الشغل", "changed jobs", "changed my job"),
    AssistScreen.SPACES to listOf("البلاد", "الدول", "اضف بلد", "حساب مصر", "countries"),
    AssistScreen.SPACE_SWITCHER to listOf("حول لمصر", "حول للسعوديه", "بدل البلد", "روح للسعوديه", "روح لمصر", "switch to egypt", "switch to saudi", "switch country"),
    AssistScreen.WALLETS to listOf("المحافظ", "محافظي", "حساباتي البنكيه", "البنوك", "حساباتي", "wallets"),
    AssistScreen.CATEGORIES to listOf("التصنيفات", "الفئات", "عدل التصنيفات", "categories"),
    AssistScreen.RULES to listOf("القواعد", "التجار", "قواعد التصنيف", "المحلات", "rules", "merchants"),
    AssistScreen.BACKUP to listOf("النسخه الاحتياطيه", "نسخه احتياطيه", "باك اب", "باكاب", "صدر بياناتي", "تصدير", "اكسل", "backup", "export"),
    AssistScreen.LOCK to listOf("القفل", "البصمه", "قفل التطبيق", "قفل البرنامج", "lock"),
    AssistScreen.LANGUAGE to listOf("اللغه", "خليه انجليزي", "غير اللغه", "language"),
    AssistScreen.ISLAMIC_CONTENT to listOf("المحتوى الاسلامي", "اخف الزكاه", "اخفي الزكاه", "اظهر الزكاه", "islamic content"),
)

/** الكلمات بعد التوحيد (مرة واحدة). */
internal val SCREEN_KEYWORDS: Map<AssistScreen, List<List<String>>> =
    SCREEN_WORDS.mapValues { (_, words) -> words.map { assistTokens(assistNormalize(it)) }.filter { it.isNotEmpty() } }

/**
 * لو الكلام ما قربش من أي شاشة: أقرب الشاشات للتبويب اللي هو فيه (نفس اختيار النموذج — `NEAR` في `AssistantChat.dc.html`).
 */
internal val TAB_NEAREST: Map<AssistTab, List<AssistScreen>> = mapOf(
    AssistTab.HOME to listOf(AssistScreen.BUDGETS, AssistScreen.WALLETS, AssistScreen.NOTIFICATIONS),
    AssistTab.OPERATIONS to listOf(AssistScreen.CATEGORIES, AssistScreen.BANK_SMS, AssistScreen.RULES),
    AssistTab.PEOPLE to listOf(AssistScreen.DEBTS, AssistScreen.ROSCAS, AssistScreen.EVENTS),
    AssistTab.INVESTMENT to listOf(AssistScreen.ZAKAT, AssistScreen.INVESTMENT, AssistScreen.RETIREMENT_CALCULATOR),
    AssistTab.MORE to listOf(AssistScreen.WALLETS, AssistScreen.BACKUP, AssistScreen.ACCOUNT),
)
