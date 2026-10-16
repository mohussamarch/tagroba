package app.masroufy.core

/**
 * المرتب الشهري — **مبلغ بالظبط بيتكتب** (قرار المالك 2026-10-16، نصه: «انا محتاج اكتبه ونخلي الناس تكتبه») بدل النطاقات (§63 القديم).
 *
 * **مصدر واحد للرقم:** لو فيه **جهة شغل رئيسية** شغالة النهارده في البلد دي ([salaryEmployer]) ⇒ المرتب هو «المرتب المتوقع» بتاعها
 * (`IncomeSource.expectedMinor` — نفس الرقم اللي في «مصادر الدخل» و«جهات الشغل»)، والكتابة بتروح عليها. مفيش جهة ⇒ المبلغ على الملف
 * ([UserProfile.salaryMinor] + [UserProfile.salaryCurrency]). القراية والكتابة بنفس القاعدة دي بالظبط.
 * الجهة لسه مالهاش مبلغ والملف عليه مبلغ بنفس العملة (اتكتب قبل ما الجهة تتضاف) ⇒ بيتعرض مبلغ الملف لحد ما يتكتب من جديد (بيروح للجهة
 * وبيتمسح من الملف — ما يفضلش رقمين).
 *
 * المجهول مجهول (القاعدة 10): نطاق قديم ⇒ **مش رقم** ([SalaryState.wasRange]) · عملة تانية ⇒ مش متاح · «أفضّل ما أقولش» ⇒ [SalaryState.declined].
 */
data class SalaryState(
    /** المبلغ بالهللة بعملة البلد، أو `null` = مش معروف. */
    val amountMinor: Halalas?,
    val currency: Currency,
    /** اختار «أفضّل ما أقولش» ومفيش مبلغ. */
    val declined: Boolean,
    /** كان متخزن نطاق قديم ومفيش مبلغ ⇒ «كنت مختار نطاق — اكتب المبلغ بالظبط». */
    val wasRange: Boolean,
    /** الجهة اللي المبلغ بيتحفظ عليها، أو `null` = على الملف. */
    val employer: IncomeSource?,
) {
    /** السؤال اتجاوب (مبلغ أو «أفضّل ما أقولش») ⇒ كارت «كمّل ملفك» ما بيسألوش. */
    val answered: Boolean get() = amountMinor != null || declined
}

/**
 * جهة الشغل الرئيسية في البلد دي النهارده: الجهات الشغالة (وظيفة أو بارت تايم) بعملة البلد ⇒ «وظيفة» واحدة بس، وإلا جهة واحدة بس.
 * أكتر من واحدة من غير ما تبان الرئيسية ⇒ `null` (ما بنختارش من عندنا — زي حاسبة المعاش) والمبلغ بيفضل على الملف.
 */
fun salaryEmployer(sources: List<IncomeSource>, currency: Currency, today: IsoDate): IncomeSource? {
    val current = sourcesActiveOn(sources, today).filter { isEmployer(it) && it.currency == currency }
    return current.filter { it.kind == IncomeSourceKind.JOB }.singleOrNull() ?: current.singleOrNull()
}

/** مبلغ الملف لو بعملة البلد (أو مبلغ قديم من غير عملة — بيتعرض زي ما كان). */
fun profileSalaryIn(profile: UserProfile?, currency: Currency): Halalas? =
    profile?.salaryMinor?.takeIf { profile.salaryCurrency == null || profile.salaryCurrency == currency }

fun salaryState(profile: UserProfile?, sources: List<IncomeSource>, currency: Currency, today: IsoDate): SalaryState {
    val employer = salaryEmployer(sources, currency, today)
    val amount = employer?.expectedMinor ?: profileSalaryIn(profile, currency)
    val declined = amount == null && profile?.salaryDeclined == true
    val wasRange = amount == null && !declined && profile?.salaryRangeLegacy != null
    return SalaryState(amount, currency, declined, wasRange, employer)
}

/** الملف بعد كتابة المبلغ: على الملف لو مفيش جهة، وإلا مبلغ الملف بنفس العملة بيتمسح (الرقم على الجهة). النطاق القديم و«أفضّل ما أقولش» بيتمسحوا. */
fun profileAfterSalary(profile: UserProfile, amountMinor: Halalas, currency: Currency, onEmployer: Boolean): UserProfile {
    val cleared = profile.copy(salaryDeclined = null, salaryRangeLegacy = null)
    return when {
        !onEmployer -> cleared.copy(salaryMinor = amountMinor, salaryCurrency = currency)
        profileSalaryIn(profile, currency) != null -> cleared.copy(salaryMinor = null, salaryCurrency = null)
        else -> cleared
    }
}

/** «أفضّل ما أقولش»: المبلغ بعملة البلد بيتمسح من الملف (والجهة بيتمسح مبلغها في حالة الاستخدام) والنطاق القديم كمان. */
fun profileAfterDecline(profile: UserProfile, currency: Currency): UserProfile {
    val base = profile.copy(salaryDeclined = true, salaryRangeLegacy = null)
    return if (profileSalaryIn(profile, currency) != null) base.copy(salaryMinor = null, salaryCurrency = null) else base
}

/** المبلغ اللي بيتكتب: أكبر من صفر وفي حدود الأعداد الآمنة (أقل من كده ⇒ مرفوض بجملة جنب الخانة). */
fun validSalaryAmount(amountMinor: Halalas?): Boolean = amountMinor != null && amountMinor > 0 && amountMinor <= MAX_SAFE_HALALAS
