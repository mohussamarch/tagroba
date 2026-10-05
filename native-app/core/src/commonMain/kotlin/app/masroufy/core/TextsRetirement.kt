package app.masroufy.core

/**
 * نصوص حاسبة التقاعد بعد ردود المالك (OVERRIDES §69.3 · §69.5): الأساسي والسكن لوحدهم في السعودية · معاش مصر (قانون 148/2019).
 * فصحى مختصرة للسعودية (§66) · مصري لمصر · إنجليزي (كتابة Claude ومستني مراجعة المالك §40). كل «غير متاح» بيقول إيه الناقص وليه.
 */
internal val MSA_RETIRE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.CALC_HOUSING_NOT_NEGATIVE to "بدل السكن لا يكون سالبًا (اكتب صفرًا إن لم يكن لك بدل سكن)",
    TextKey.CALC_MONTHS_2020 to "شهور ما قبل 2020 جزء من مدة اشتراكك، فلا تزيد عليها",
    TextKey.CALC_EGYPT_LEGAL_AGE_RANGE to "سن الشيخوخة من 60 إلى 65",
    TextKey.CALC_REASON_NEED_BASIC to "غير متاح: اكتب الأجر الأساسي الشهري",
    TextKey.CALC_REASON_NEED_HOUSING to "غير متاح: اكتب بدل السكن الشهري — اكتب صفرًا إن لم يكن لك بدل سكن",
    TextKey.CALC_REASON_EGYPT_EARLY_BELOW_HALF to "المعاش المبكر غير مستحق: المحسوب أقل من نصف أجر التسوية (المادة 21 من القانون 148 لسنة 2019)",
    TextKey.CALC_REASON_EGYPT_EARLY_BELOW_MINIMUM to "المعاش المبكر غير مستحق: المحسوب أقل من الحد الأدنى للمعاش (65% من الحد الأدنى لأجر الاشتراك)",
    TextKey.CALC_REASON_NEED_SETTLEMENT_WAGE to "غير متاح: اكتب متوسط أجرك الشهري في التأمينات (أجر التسوية)",
    TextKey.CALC_REASON_NEED_EGYPT_LEGAL_AGE to "غير متاح: اكتب سن الشيخوخة الخاص بك (من 60 إلى 65) — القانون يرفعه تدريجيًا إلى 65 في يوليو 2040 بقرار من رئيس الوزراء، والجدول ليس في نص القانون",
    TextKey.CALC_REASON_EGYPT_PRE_2020 to "غير متاح: لك مدة اشتراك قبل 2020، وحسابها يحتاج أجورك بالقانون القديم ونسب التضخم الرسمية",
    TextKey.CALC_REASON_EGYPT_AFTER_LEGAL_AGE to "غير متاح: جدول حساب المعاش في القانون لا يشمل التقاعد بعد سن الشيخوخة",
    TextKey.CALC_EGYPT_FLOOR_UNKNOWN to "الحد الأدنى للمعاش (65% من الحد الأدنى لأجر الاشتراك) غير متاح: الحد الأدنى لأجر الاشتراك تحدده اللائحة التنفيذية، فاكتبه إن كنت تعرفه",
    TextKey.CALC_EGYPT_SETTLEMENT_WAGE_HELP to "أجر التسوية = متوسط أجرك الشهري في التأمينات منذ 2020، بعد زيادته بمتوسط التضخم عن كل سنة. تجده في كشف التأمينات، أو اكتب تقديرك",
)

internal val EGYPTIAN_RETIRE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.CALC_HOUSING_NOT_NEGATIVE to "بدل السكن ما ينفعش يبقى بالسالب (اكتب صفر لو مالكش بدل سكن)",
    TextKey.CALC_MONTHS_2020 to "شهور ما قبل 2020 جزء من مدة اشتراكك، فما تزيدش عليها",
    TextKey.CALC_EGYPT_LEGAL_AGE_RANGE to "سن المعاش من 60 لـ65",
    TextKey.CALC_REASON_NEED_BASIC to "غير متاح: اكتب المرتب الأساسي في الشهر",
    TextKey.CALC_REASON_NEED_HOUSING to "غير متاح: اكتب بدل السكن في الشهر — اكتب صفر لو مالكش بدل سكن",
    TextKey.CALC_REASON_EGYPT_EARLY_BELOW_HALF to "المعاش المبكر مش مستحق: المحسوب أقل من نص أجر التسوية (مادة 21 من قانون 148 لسنة 2019)",
    TextKey.CALC_REASON_EGYPT_EARLY_BELOW_MINIMUM to "المعاش المبكر مش مستحق: المحسوب أقل من الحد الأدنى للمعاش (65% من الحد الأدنى لأجر الاشتراك)",
    TextKey.CALC_REASON_NEED_SETTLEMENT_WAGE to "غير متاح: اكتب متوسط مرتبك في الشهر في التأمينات (أجر التسوية)",
    TextKey.CALC_REASON_NEED_EGYPT_LEGAL_AGE to "غير متاح: اكتب سن المعاش بتاعك (من 60 لـ65) — القانون بيرفعه بالتدريج لـ65 في يوليو 2040 بقرار من رئيس الوزراء، والجدول مش في نص القانون",
    TextKey.CALC_REASON_EGYPT_PRE_2020 to "غير متاح: ليك مدة اشتراك قبل 2020، وحسابها محتاج مرتباتك بالقانون القديم ونسب التضخم الرسمية",
    TextKey.CALC_REASON_EGYPT_AFTER_LEGAL_AGE to "غير متاح: جدول حساب المعاش في القانون ما فيهوش التقاعد بعد سن المعاش",
    TextKey.CALC_EGYPT_FLOOR_UNKNOWN to "الحد الأدنى للمعاش (65% من الحد الأدنى لأجر الاشتراك) غير متاح: الحد الأدنى لأجر الاشتراك بتحدده اللائحة التنفيذية، فاكتبه لو تعرفه",
    TextKey.CALC_EGYPT_SETTLEMENT_WAGE_HELP to "أجر التسوية = متوسط مرتبك في الشهر في التأمينات من 2020، بعد ما يزيد بمتوسط التضخم عن كل سنة. هتلاقيه في كشف التأمينات، أو اكتب تقديرك",
)

internal val ENGLISH_RETIRE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.CALC_HOUSING_NOT_NEGATIVE to "The housing allowance can't be negative (enter zero if you have none)",
    TextKey.CALC_MONTHS_2020 to "Months before 2020 are part of your contribution period, so they can't exceed it",
    TextKey.CALC_EGYPT_LEGAL_AGE_RANGE to "The pension age is from 60 to 65",
    TextKey.CALC_REASON_NEED_BASIC to "Not available: enter your monthly basic wage",
    TextKey.CALC_REASON_NEED_HOUSING to "Not available: enter your monthly housing allowance — enter zero if you have none",
    TextKey.CALC_REASON_EGYPT_EARLY_BELOW_HALF to "No early pension: the calculated pension is below half of the settlement wage (Article 21 of Law 148 of 2019)",
    TextKey.CALC_REASON_EGYPT_EARLY_BELOW_MINIMUM to "No early pension: the calculated pension is below the minimum pension (65% of the minimum contribution wage)",
    TextKey.CALC_REASON_NEED_SETTLEMENT_WAGE to "Not available: enter your average monthly insured wage (the settlement wage)",
    TextKey.CALC_REASON_NEED_EGYPT_LEGAL_AGE to "Not available: enter your pension age (60 to 65) — the law raises it step by step to 65 by July 2040 through a Prime Minister's decree, and the schedule isn't in the law itself",
    TextKey.CALC_REASON_EGYPT_PRE_2020 to "Not available: you have contribution months before 2020, and those need your wages under the old law and the official inflation rates",
    TextKey.CALC_REASON_EGYPT_AFTER_LEGAL_AGE to "Not available: the law's pension table doesn't cover retiring after the pension age",
    TextKey.CALC_EGYPT_FLOOR_UNKNOWN to "The minimum pension (65% of the minimum contribution wage) isn't available: the executive regulations set the minimum contribution wage, so enter it if you know it",
    TextKey.CALC_EGYPT_SETTLEMENT_WAGE_HELP to "Settlement wage = your average monthly insured wage since 2020, raised by the average inflation for each year. Find it on your insurance statement, or enter your estimate",
)
