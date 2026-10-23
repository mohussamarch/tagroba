package app.masroufy.core

/**
 * المرتب الشهري — **مبلغ بالظبط بيتكتب** (قرار المالك 2026-10-16، نصه: «انا محتاج اكتبه ونخلي الناس تكتبه») بدل النطاقات (§63 القديم).
 *
 * **مكان الرقم (ردود المالك 2026-10-16 18:15 — OVERRIDES §63):**
 * - فيه **جهات شغل شغالة النهارده** في البلد دي بعملتها ([salaryEmployers]) ⇒ **مبلغ لكل جهة** = «المرتب المتوقع» بتاعها
 *   (`IncomeSource.expectedMinor` — نفس الرقم في «مصادر الدخل» و«جهات الشغل»)، والكتابة بتروح عليها. «ملفك» بيعرض **المجموع**
 *   ومعاه «مجموع مرتبات N وظايف» لو أكتر من واحدة. جهة من غيرهم مالهاش مبلغ ⇒ المجموع **مش معروف** (القاعدة 10 — مش مجموع ناقص).
 * - **مفيش جهة** ⇒ المبلغ على الملف **لبلد العملة دي** ([UserProfile.salaries] — cty-4) — **ده بس** اللي بيستعمل مبلغ الملف.
 *
 * المجهول مجهول: نطاق قديم ⇒ **مش رقم** ([SalaryState.wasRange]) · عملة تانية ⇒ مش متاح · «مش عايز أقول» ⇒ [SalaryState.declined].
 */
data class SalaryState(
    /** المرتب بالهللة بعملة البلد (مجموع الجهات لو كلها معروفة، أو مبلغ الملف لو مفيش جهات)، أو `null` = مش معروف. */
    val amountMinor: Halalas?,
    val currency: Currency,
    /** اختار «مش عايز أقول» ومفيش مبلغ. */
    val declined: Boolean,
    /** كان متخزن نطاق قديم ومفيش مبلغ ⇒ «إجابتك القديمة كانت نطاق — اكتب المبلغ بالظبط». */
    val wasRange: Boolean,
    /** جهات الشغل الشغالة (المبلغ بيتحفظ على كل واحدة). فاضية = المبلغ على الملف. */
    val employers: List<IncomeSource>,
) {
    /** السؤال اتجاوب (مبلغ أو «مش عايز أقول») ⇒ كارت «كمّل ملفك» ما بيسألوش. */
    val answered: Boolean get() = amountMinor != null || declined

    /** الجهة لوحدها لو واحدة بس (سطر «هيتحفظ مرتب متوقع لـ…»). */
    val employer: IncomeSource? get() = employers.singleOrNull()
}

/** مفتاح خانة الملف في الفورم (مفيش جهات) — غير كده المفتاح معرّف الجهة. */
const val PROFILE_SALARY_KEY = ""

/** وزن المرتب في نسبة «ملفك» (§63): **12** — بتتحسب لو كتب مبلغ **أو** اختار «مش عايز أقول» (رد المالك 2026-10-16). */
const val SALARY_PROFILE_POINTS = 12

fun salaryProfilePoints(state: SalaryState?): Int = if (state?.answered == true) SALARY_PROFILE_POINTS else 0

/** جهات الشغل الشغالة النهارده (وظيفة أو بارت تايم) بعملة البلد، بترتيب ثابت (الأقدم الأول). */
fun salaryEmployers(sources: List<IncomeSource>, currency: Currency, today: IsoDate): List<IncomeSource> =
    sourcesActiveOn(sources, today).filter { isEmployer(it) && it.currency == currency }.sortedWith(compareBy<IncomeSource> { it.startedAt }.thenBy { it.id })

/** مبلغ الملف **لبلد العملة دي بس** (cty-4). المبلغ القديم من غير عملة بيتحسب للسعودية. */
fun profileSalaryIn(profile: UserProfile?, currency: Currency): Halalas? = profile?.withSalaryMigrated()?.salaries?.get(currency)

/** «أفضّل ما أقولش» في البلد دي بس. */
fun salaryDeclinedIn(profile: UserProfile?, currency: Currency): Boolean = profile?.withSalaryMigrated()?.salaryDeclinedIn?.contains(currency) == true

fun salaryState(profile: UserProfile?, sources: List<IncomeSource>, currency: Currency, today: IsoDate): SalaryState {
    val employers = salaryEmployers(sources, currency, today)
    val amount = if (employers.isEmpty()) profileSalaryIn(profile, currency)
    else employers.map { it.expectedMinor }.takeIf { all -> all.all { it != null } }?.let { known -> addMoney(*known.filterNotNull().toLongArray()) }
    val declined = amount == null && salaryDeclinedIn(profile, currency)
    val wasRange = amount == null && !declined && profile?.salaryRangeLegacy != null
    return SalaryState(amount, currency, declined, wasRange, employers)
}

/** الملف بعد كتابة المبلغ: على الملف لو مفيش جهة، وإلا مبلغ الملف بنفس العملة بيتمسح (الأرقام على الجهات). النطاق القديم و«مش عايز أقول» بيتمسحوا. */
fun profileAfterSalary(profile: UserProfile, amountMinor: Halalas, currency: Currency, onEmployer: Boolean): UserProfile {
    val p = profile.withSalaryMigrated()
    val cleared = p.copy(salaryDeclinedIn = p.salaryDeclinedIn - currency, salaryRangeLegacy = null)
    return if (!onEmployer) cleared.copy(salaries = p.salaries + (currency to amountMinor)) else cleared.copy(salaries = p.salaries - currency)
}

/** «مش عايز أقول»: المبلغ بعملة البلد بيتمسح من الملف (والجهات بيتمسح مبلغها في حالة الاستخدام) والنطاق القديم كمان. */
fun profileAfterDecline(profile: UserProfile, currency: Currency): UserProfile {
    val p = profile.withSalaryMigrated()
    return p.copy(salaryDeclinedIn = p.salaryDeclinedIn + currency, salaryRangeLegacy = null, salaries = p.salaries - currency)
}

/** المبلغ اللي بيتكتب: أكبر من صفر وفي حدود الأعداد الآمنة (أقل من كده ⇒ مرفوض بجملة جنب الخانة). */
fun validSalaryAmount(amountMinor: Halalas?): Boolean = amountMinor != null && amountMinor > 0 && amountMinor <= MAX_SAFE_HALALAS
