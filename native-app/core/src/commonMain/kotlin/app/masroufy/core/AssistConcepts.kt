package app.masroufy.core

/**
 * المعاني اللي القواعد بتدوّر عليها — كل معنى بصيغ كتير: فصحى · مصري · خليجي · إنجليزي (§78-١ «بصيغ كتير سعودي ومصري ومع الأخطاء
 * الإملائية»). الكلمة الواحدة جذر (أي لاحقة بعدها) والعبارة كلمات بالترتيب. لغة **بيانات** مش واجهة (زي كلمات رسايل البنك) ⇒ مش في
 * جدول النصوص.
 */
internal object AssistWords {
    val SPEND = vocab(
        "صرفت", "صرف", "بصرف", "هصرف", "صرفي", "صرفنا", "مصروف", "مصاريف", "مصروفات", "صرفيات", "انفقت", "انفق", "انفاق", "كلفتني", "كلفت", "كلفه", "تكلفه",
        "اخد مني", "اخذ مني", "spent", "spend", "spending", "cost", "expenses", "دفعت كام", "دفعت كم", "كم دفعت",
    )
    val GIVE = vocab("اعطيت", "اديت", "عطيت", "هدايا", "هديه", "اهديت", "give", "gave", "gifts")
    val INCOME = vocab("دخلي", "دخل", "الدخل", "ايراد", "ايرادات", "دخلنا", "earn", "earned", "income", "حركه الفلوس", "cash flow", "money in")
    val REMAINING = vocab("فاضل", "فاضلي", "فاضلك", "فضل لي", "فضلي", "باقي", "باقيلي", "بقي", "بقالي", "متبقي", "يتبقي", "ضل لي", "left", "remaining", "remain")
    val PER_DAY = vocab("في اليوم", "باليوم", "يوميا", "لليوم", "كل يوم", "per day", "daily", "a day")
    val CAN_SPEND = vocab("اصرف", "اقدر", "المتاح", "متاح", "allowance", "can i spend", "afford")
    val FORECAST = vocab("اخر الشهر", "نهايه الشهر", "بنهايه الشهر", "اخر الفتره", "توقع", "توقعات", "forecast", "end of the month", "end of month", "هوصل لكام", "ساصرف", "سأصرف")
    val BUDGET = vocab("ميزانيه", "ميزانيتي", "ميزانيات", "سقف", "البادجت", "budget")
    val ON_PLAN = vocab(
        "على الخطه", "علي الخطه", "ماشي على", "ماشي علي", "وضع ميزانيتي", "الميزانيه عامله", "الميزانيه ماشيه", "on budget", "on track", "within budget",
        "ماشي صح",
    )
    val CASH = vocab("كاش", "نقد", "نقدا", "نقدي", "cash", fuzzy = false, exact = true)
    val ON_HAND = vocab("رصيدي", "رصيد", "balance", "معايا كام", "معاي كم", "كم معي", "معايا", "معي كم", "عندي كام", "عندي كم", "فلوسي كام", "how much do i have")
    val OWED_TO_ME = vocab(
        "عليه فلوس", "عليهم فلوس", "يدين لي", "يدينني", "مطلوب لي", "فلوسي عند", "ليا عند الناس", "لي عند الناس", "ليا فلوس", "owes me", "owe me",
        "مديون لي", "مديونين",
    )
    val I_OWE = vocab("عليا فلوس", "علي فلوس", "ديوني", "وش علي", "اللي عليا", "عليا لمين", "لمن علي", "i owe", "do i owe", "اللي علي للناس", "مديون")
    val PERSON_OWE = vocab("عند", "عليه", "عليها", "عليا", "يدين", "ليا", "owes", "owe", "مديون", "مديونه", fuzzy = false, exact = true)
    val OVERDUE = vocab("اتاخر", "تاخر", "تأخر", "متاخر", "متاخرين", "المتاخره", "فات ميعاده", "فات موعده", "فات ميعادها", "overdue", "late")
    val SALARY = vocab("راتب", "راتبي", "الراتب", "مرتب", "مرتبي", "المرتب", "معاش", "salary", "payday", "paycheck", "pay day")
    val WHEN = vocab("امتي", "متي", "ايمتي", "امته", "when", "كم يوم", "باقي كم يوم", "how many days", "موعد", "ميعاد", fuzzy = false, exact = true)
    val HOW_MUCH = vocab("كم", "كام", "بكم", "بكام", "قديش", "how much", "what is", "whats", "قيمه", fuzzy = false, exact = true)
    val LAST = vocab("اخر مره", "اخر مرة", "اخر عمليه", "اخر عملية", "last time", "last purchase", "متى اخر", "امتى اخر", "اخر دفعه")
    val GOAL = vocab("هدف", "الهدف", "اهدافي", "خطه الادخار", "خطط الادخار", "خطتي اللي عليها النجمه", "النجمه", "التحويش", "ادخار", "goal", "savings plan", "savings goal", fuzzy = false)
    val PROGRESS = vocab("وصلت", "وصل", "فاضل", "باقي", "بقي", "ماشي", "كم", "كام", "progress", "how far", "left")
    val ZAKAT = vocab("زكاه", "زكاتي", "الزكاه", "الحول", "النصاب", "zakat")
    val PAY_VERB = vocab("ادفع", "دفع", "دفعت", "اسدد", "سدد", "pay", "paid", fuzzy = false, exact = true)
    val WHERE = vocab("فين", "وين", "اين", "where", fuzzy = false, exact = true)
    val DUE_CUES = vocab("قبل المرتب", "قبل الراتب", "عليا", "علي", "الجايه", "القادمه", "الاسبوع", "due", fuzzy = false, exact = true)
    val DUES = vocab(
        "مستحق", "مستحقات", "قبل المرتب", "قبل الراتب", "مواعيد", "مواعيدي", "اقساطي الجايه", "عليا قبل", "علي قبل", "due", "what's due", "whats due",
        "الالتزامات الجايه", "اللي عليا الشهر",
    )
    val BILLS = vocab("فاتوره", "فواتير", "كهربا", "الكهربا", "مياه", "المياه", "مويه", "المويه", "النت", "انترنت", "اشتراك", "اشتراكات", "bill", "bills", "subscription", "subscriptions", "electricity")
    val PENDING = vocab("محتاجه تاكيد", "محتاج تاكيد", "تاكيد", "تأكيد", "مستنيه", "منتظره", "بانتظار", "تنتظر", "waiting", "pending", "review", "مستنيني")
    val ASSETS = vocab("اصول", "اصولي", "ذهبي", "ذهب", "الذهب", "اسهم", "الاسهم", "اسهمي", "محفظه الاستثمار", "portfolio", "gold", "stocks", "assets", "فضتي")
    val VALUE = vocab("بكام", "بكم", "قيمه", "كم", "كام", "عامله ايه", "عامل ايه", "وضع", "worth", "value", "how much", "how are")
    val ROSCA = vocab("جمعيه", "جمعيتي", "الجمعيه", "جمعيات", "rosca")
    val ROSCA_Q = vocab("دوري", "اقبض", "اقبضها", "قسط", "فاضل", "امتي", "متي", "turn", "when", "payout")
    val INSTALLMENT = vocab("قسط", "اقساط", "تقسيط", "التمويل", "تمويل", "installment", "installments", "financing")
    val INSTALLMENT_Q = vocab("فاضل", "باقي", "بقي", "الجاي", "الجاي امتي", "امتي", "متي", "next", "left", "how many", "remaining", "كم")
    val OCCASIONS = vocab("عيد ميلاد", "اعياد ميلاد", "مناسبات", "المناسبات", "مناسبه", "birthday", "birthdays", "occasions", "anniversary")
    val UPCOMING = vocab("الجايه", "الجاي", "القادمه", "القادم", "قريب", "upcoming", "coming", "next")
    val MOST = vocab("اكتر", "اكثر", "اكبر", "most", "biggest", "top", fuzzy = false, exact = true)
    val WHAT_ON = vocab("على ايه", "علي ايه", "على وش", "علي وش", "وش اكثر", "ايش اكثر", "ما اكبر", "اكبر تصنيف", "where did", "what did", "فلوسي راحت", "شيء صرفت")
    val COMPARE = vocab("قارن", "مقارنه", "compare", "comparison", "اعلى من", "اعلي من", "اكتر من", "اكثر من", "more than", "higher than", "less than", "اقل من")
    val RECORD = vocab("سجل", "سجلي", "سجلها", "دفعت", "record", "log", "paid", fuzzy = false)
    val MAIN = vocab("الاساسيه", "اساسيه", "الاساسي", "محفظتي الاساسيه", "main", "default", "primary")
    val USUALLY_FROM = vocab("بصرف عاده من", "اصرف عاده من", "عاده بصرف من", "عاده اصرف من", "usually pay from", "usually spend from", "بدفع عاده من")
    val FEE = vocab("رسوم", "رسم", "عموله", "fee", "fees", "commission", fuzzy = false, exact = true)
    val SPLIT = vocab("قسم", "قسمها", "تقسيم", "نقسم", "اقسم", "يتقسم", "قسمت", "نتقاسم", "بالنص", "split", "بيني وبين", fuzzy = false)
    val OPEN = vocab("افتح", "افتحلي", "وريني", "ورني", "ودني", "وديني", "روح", "صفحه", "ملف", "تفاصيل", "open", "show me", "go to", "فين", "وين", fuzzy = false, exact = true)
    val CATEGORY_WORD = vocab("التصنيف", "تصنيف", "تصنيفها", "category", fuzzy = false)
}
