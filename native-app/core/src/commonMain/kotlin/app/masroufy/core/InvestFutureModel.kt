package app.masroufy.core

/**
 * نموذج «هتبقى معاك كام» في «الاستثمار» (حزمة FUTURE — مواصفة النقل §2): المدخلات من بيانات المستخدم نفسه + المتوسطات الرسمية بمصادرها،
 * والناتج أرقام بالهللة بس (CLAUDE.md #1). الحساب في `InvestFutureMath.kt`؛ الشاشة بتعرض بس.
 */

/** الطرق التلاتة («اختار طريقك»): زي ما إنت · استثمر تحويشك · استثمر أكتر (أو «ابدأ تحوّش» لو مفيش تحويش). */
enum class FuturePath(val wire: String) {
    KEEP("keep"), INVEST("invest"), MORE("more");

    companion object {
        fun fromWire(wire: String?): FuturePath? = entries.firstOrNull { it.wire == wire }
    }
}

/** «بتستثمرها في»: زي أملاكك (متوسط موزون) · أسهم · دهب · عقار (بالتضخم). */
enum class FutureMix(val wire: String) {
    MINE("mine"), SHARES("stk"), GOLD("gold"), PROPERTY("re");

    companion object {
        fun fromWire(wire: String?): FutureMix? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * اختيارات المستخدم: [years] من خطوات البكرة · [path] null = الافتراضي · [amountMinor] null = مبلغ البداية (قاعدة الـ10%) ·
 * [todayMoney] «بفلوس النهارده» (الافتراضي §87-5) · [marks] سنين العلامات (سنة تقويمية).
 */
data class FutureChoice(
    val years: Int = FUTURE_DEFAULT_YEARS,
    val path: FuturePath? = null,
    val amountMinor: Halalas? = null,
    val mix: FutureMix = FutureMix.MINE,
    val todayMoney: Boolean = true,
    val marks: List<Int> = emptyList(),
)

/** دين بيخلص: المتبقي والقسط الشهري (0 = ثابت لحد ما يتسدد — ديون الناس). */
data class DebtLine(val balanceMinor: Halalas, val monthlyMinor: Halalas)

/** هدف خطة ادخار شغالة — [targetDate] null = «من غير ميعاد». */
data class FutureGoal(val name: String, val targetDate: IsoDate?)

/**
 * مدخلات بلد واحد (مواصفة §2.1). [cashMinor] = مجموع المحافظ المعروفة · [unknownCount] = محافظ وحاجات من غير قيمة معروفة (برّه الحساب) ·
 * [saveMinor] null = التحويش مش معروف (أقل من 3 شهور كاملة) — **مش صفر** · [incomeMinor] null = الدخل مش معروف ⇒ مبلغ بداية ثابت ·
 * [passiveKnownMinor] = الإيجار والعائد المتوقع من مصادر الدخل · [ageNow] null = العمر مش مكتوب.
 */
data class FutureInputs(
    val spaceId: String,
    val countryCode: String,
    val currency: Currency,
    val today: IsoDate,
    val cashMinor: Halalas,
    val unknownCount: Int,
    val split: InvestableSplit,
    val debts: List<DebtLine>,
    val saveMinor: Halalas?,
    val incomeMinor: Halalas?,
    val passiveKnownMinor: Halalas,
    val rates: InvestRatesState,
    val ageNow: Int?,
    val goals: List<FutureGoal>,
) {
    val thisYear: Int get() = today.substring(0, 4).toInt()
    val saveUnknown: Boolean get() = saveMinor == null
    val low: Boolean get() = saveMinor != null && saveMinor <= 0L

    /** عادي ⇒ التلاتة؛ تحويش صفر أو سالب أو مش معروف ⇒ «زي ما إنت» و«ابدأ تحوّش» بس (§2.2). */
    val paths: List<FuturePath> get() = if (low || saveUnknown) listOf(FuturePath.KEEP, FuturePath.MORE) else FuturePath.entries

    /** «النهارده ≈»: الكاش + الحاجات (من غير الشخصي) − كل الديون — بالاسم النهارده. */
    val netNowMinor: Halalas get() = subtractMoney(addMoney(cashMinor, split.totalMinor), sumMoney(debts.map { it.balanceMinor }))
}

enum class MileState { PASSED, REACHED, NEVER }

/** محطة (100 ألف · نص مليون · أول مليون …): [yearsFromNow] لما [state] = REACHED. */
data class MilestoneRow(val thresholdMinor: Halalas, val label: InvestFutureKey, val state: MileState, val yearsFromNow: Int?)

/** هدف ادخار على محور الزمن — [monthsFromToday] للرسم بس (null = من غير ميعاد). */
data class GoalRow(val name: String, val targetDate: IsoDate?, val monthsFromToday: Int?)

/** «هتجيبلك كام في الشهر من غير شغل»: النهارده وسنة المدة على الطريق المختار (بشكل الأرقام المختار). [knownMinor] الإيجار والعائد المعروف. */
data class PassiveIncome(val nowMinor: Halalas, val atYearMinor: Halalas, val knownMinor: Halalas)

/** خط طريق واحد: قيمة آخر كل سنة من 0 لـ[FUTURE_MAX_YEARS] بالشكل المختار (فلوس النهارده أو زي ما هيكون). */
data class PathLine(val path: FuturePath, val values: List<Halalas>)

/**
 * ناتج التوقع (مواصفة §2.3 — §2.5). [choice] بعد التحديد (الطريق والمبلغ مش null). [mixSource] مصدر المتوسط لو مش «زي أملاكك» (أو «زي أملاكك» من غير أملاك بتكبر ⇒ مصدر الأسهم).
 * [pins] = (سنة العلامة، قيمتها على الطريق المختار).
 */
data class FutureProjection(
    val inputs: FutureInputs,
    val choice: FutureChoice,
    val rates: InvestRates,
    val selected: FuturePath,
    val lines: List<PathLine>,
    val endMinor: Halalas,
    val deltaMinor: Halalas,
    val startAmountMinor: Halalas,
    val maxAmountMinor: Halalas,
    val stepMinor: Halalas,
    val mixBp: Int,
    val mixSource: AverageEntry?,
    val milestones: List<MilestoneRow>,
    val goals: List<GoalRow>,
    val passive: PassiveIncome,
    val pins: List<Pair<Int, Halalas>>,
) {
    val years: Int get() = choice.years
    val amountMinor: Halalas get() = choice.amountMinor ?: startAmountMinor
    val netNowMinor: Halalas get() = inputs.netNowMinor
    fun line(path: FuturePath): List<Halalas> = lines.first { it.path == path }.values
    fun valueAt(path: FuturePath, year: Int): Halalas = line(path)[year]
}

/** سعر الصرف: جنيه لكل ريال × 10,000 (من سعر الدولار بالريال وبالجنيه في ملف الأسعار) + مصدره وتاريخه. */
data class FxRate(val egpPerSar4: Long, val source: String, val asOf: IsoDate)

/** صف بلد في «في البلدين»: قيمة «زي ما إنت» بعد المدة بعملته — null = «غير متاح». */
data class CountryRow(val spaceId: String, val countryCode: String, val currency: Currency, val valueMinor: Halalas?)

/** «في البلدين سنة …»: [totalMinor] بعملة البلد الشغال — null لو سعر الصرف مش موجود أو بلد قيمته مش معروفة. */
data class CountriesView(val year: Int, val rows: List<CountryRow>, val totalMinor: Halalas?, val currency: Currency, val fx: FxRate?)
