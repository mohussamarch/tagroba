package app.masroufy.core

/**
 * نصوص حاسبة التقاعد بعد ردود المالك (OVERRIDES §69.3 · §69.5 · §69.8 اللائحة التنفيذية): الأساسي والسكن لوحدهم في السعودية · معاش مصر (قانون 148/2019).
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
    TextKey.CALC_REASON_NEED_EGYPT_LEGAL_AGE to "غير متاح: اكتب سن الشيخوخة الخاص بك (من 60 إلى 65) — القانون يرفعه تدريجيًا إلى 65 في يوليو 2040 بقرار من رئيس الوزراء، ولم يُنشر هذا القرار بعد",
    TextKey.CALC_REASON_EGYPT_PRE_2020 to "غير متاح: لك مدة اشتراك قبل 2020، وحسابها يحتاج أجورك بالقانون القديم ونسب التضخم الرسمية",
    TextKey.CALC_REASON_EGYPT_AFTER_LEGAL_AGE to "غير متاح: جدول حساب المعاش في القانون لا يشمل التقاعد بعد سن الشيخوخة",
    TextKey.CALC_EGYPT_FLOOR_UNKNOWN to "الحد الأدنى للمعاش (65% من الحد الأدنى لأجر الاشتراك يوم التقاعد، ولا يقل عن 900 جنيه) غير متاح: رقم سنة تقاعدك لم يُعلن بعد. آخر رقم رسمي {0} في {1}، ويزيد كل يناير — اكتب الرقم الذي تتوقعه",
    TextKey.CALC_EGYPT_SETTLEMENT_WAGE_HELP to "أجر التسوية = متوسط أجرك الشهري في التأمينات منذ 2020، بعد زيادته بمتوسط التضخم عن كل سنة. تجده في كشف التأمينات، أو اكتب تقديرك",
    TextKey.CALC_EGYPT_MAX_BELOW_MIN to "الحد الأقصى لأجر الاشتراك لا يكون أقل من الحد الأدنى",
    TextKey.CALC_EGYPT_MAX_CAP_UNKNOWN to "سقف المعاش (80% من الحد الأقصى لأجر الاشتراك يوم التقاعد) غير مطبّق: رقم سنة تقاعدك لم يُعلن بعد. آخر رقم رسمي {0} في {1} — اكتب الرقم الذي تتوقعه",
    TextKey.CALC_EGYPT_WAGE_CAPPED to "أجر التسوية لا يزيد على الحد الأقصى لأجر الاشتراك ({0})، فحُسب المعاش عليه (المادة 22)",
    TextKey.CALC_EGYPT_ART163_NOTE to "لا يشمل هذا الرقم زيادة المادة 163 (الفرق بين 450 جنيهًا و33% من المعاش)، لأن طريقة حسابها غير واضحة في النص",
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
    TextKey.CALC_REASON_NEED_EGYPT_LEGAL_AGE to "غير متاح: اكتب سن المعاش بتاعك (من 60 لـ65) — القانون بيرفعه بالتدريج لـ65 في يوليو 2040 بقرار من رئيس الوزراء، والقرار ده لسه ما اتنشرش",
    TextKey.CALC_REASON_EGYPT_PRE_2020 to "غير متاح: ليك مدة اشتراك قبل 2020، وحسابها محتاج مرتباتك بالقانون القديم ونسب التضخم الرسمية",
    TextKey.CALC_REASON_EGYPT_AFTER_LEGAL_AGE to "غير متاح: جدول حساب المعاش في القانون ما فيهوش التقاعد بعد سن المعاش",
    TextKey.CALC_EGYPT_FLOOR_UNKNOWN to "الحد الأدنى للمعاش (65% من الحد الأدنى لأجر الاشتراك يوم التقاعد، ومش أقل من 900 جنيه) غير متاح: رقم سنة تقاعدك لسه ما اتعلنش. آخر رقم رسمي {0} في {1}، وبيزيد كل يناير — اكتب الرقم اللي تتوقعه",
    TextKey.CALC_EGYPT_SETTLEMENT_WAGE_HELP to "أجر التسوية = متوسط مرتبك في الشهر في التأمينات من 2020، بعد ما يزيد بمتوسط التضخم عن كل سنة. هتلاقيه في كشف التأمينات، أو اكتب تقديرك",
    TextKey.CALC_EGYPT_MAX_BELOW_MIN to "الحد الأقصى لأجر الاشتراك ما ينفعش يبقى أقل من الحد الأدنى",
    TextKey.CALC_EGYPT_MAX_CAP_UNKNOWN to "سقف المعاش (80% من الحد الأقصى لأجر الاشتراك يوم التقاعد) مش متطبّق: رقم سنة تقاعدك لسه ما اتعلنش. آخر رقم رسمي {0} في {1} — اكتب الرقم اللي تتوقعه",
    TextKey.CALC_EGYPT_WAGE_CAPPED to "أجر التسوية ما يزيدش عن الحد الأقصى لأجر الاشتراك ({0})، فالمعاش اتحسب عليه (مادة 22)",
    TextKey.CALC_EGYPT_ART163_NOTE to "الرقم ده من غير زيادة مادة 163 (الفرق بين 450 جنيه و33% من المعاش)، لأن طريقة حسابها مش واضحة في النص",
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
    TextKey.CALC_REASON_NEED_EGYPT_LEGAL_AGE to "Not available: enter your pension age (60 to 65) — the law raises it step by step to 65 by July 2040 through a Prime Minister's decree, and that decree hasn't been published yet",
    TextKey.CALC_REASON_EGYPT_PRE_2020 to "Not available: you have contribution months before 2020, and those need your wages under the old law and the official inflation rates",
    TextKey.CALC_REASON_EGYPT_AFTER_LEGAL_AGE to "Not available: the law's pension table doesn't cover retiring after the pension age",
    TextKey.CALC_EGYPT_FLOOR_UNKNOWN to "The minimum pension (65% of the minimum contribution wage on your retirement date, and at least EGP 900) isn't available: your retirement year's figure hasn't been announced. The latest official figure is {0} for {1}, and it rises every January — enter the figure you expect",
    TextKey.CALC_EGYPT_SETTLEMENT_WAGE_HELP to "Settlement wage = your average monthly insured wage since 2020, raised by the average inflation for each year. Find it on your insurance statement, or enter your estimate",
    TextKey.CALC_EGYPT_MAX_BELOW_MIN to "The maximum contribution wage can't be lower than the minimum",
    TextKey.CALC_EGYPT_MAX_CAP_UNKNOWN to "The pension ceiling (80% of the maximum contribution wage on your retirement date) isn't applied: your retirement year's figure hasn't been announced. The latest official figure is {0} for {1} — enter the figure you expect",
    TextKey.CALC_EGYPT_WAGE_CAPPED to "The settlement wage can't exceed the maximum contribution wage ({0}), so the pension uses that (Article 22)",
    TextKey.CALC_EGYPT_ART163_NOTE to "This figure doesn't include the Article 163 increase (the difference between EGP 450 and 33% of the pension), because the text doesn't make clear how to calculate it",
)
