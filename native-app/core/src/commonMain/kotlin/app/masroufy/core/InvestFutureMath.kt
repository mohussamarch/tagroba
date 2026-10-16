package app.masroufy.core

/**
 * حساب «هتبقى معاك كام» (مواصفة النقل §2.3 — §2.5، نقل `project()` من النموذج `InvestFuture`): 40 سنة شهر بشهر بأعداد صحيحة بس —
 * الهللة `Long` · المعدلات نقاط أساس · المعدل الشهري جزء من 10^8 ([monthlyRateFromAnnual]) · كل ضربة بتتقرب للهللة ([mulDivWide]).
 * **مش [projectGrowth]**: الأساس والتحويش ممكن يبقوا بالسالب (ديون أكتر من الفلوس · صرف أكتر من الدخل)، وهي بترفض السالب.
 *
 * الافتراضات المكتوبة (القاعدة 10 — كل رقم مصدره بيانات المستخدم + متوسط رسمي بيتعرض مصدره):
 * - العقار بيكبر بالتضخم الرسمي، الذهب والأسهم بمتوسطهم في ملف المتوسطات، والحاجات اللي مالهاش متوسط ثابتة بالاسم (سؤال Q4).
 * - الكاش ثابت بالاسم («بتسيبهم في البنك») · القسط بينقّص الدين كل شهر لحد الصفر · دين الناس ثابت لحد ما يتسدد.
 * - «بفلوس النهارده» = القيمة ÷ (1 + التضخم)^السنين ([deflate]).
 */

const val FUTURE_MAX_YEARS = 40
const val FUTURE_DEFAULT_YEARS = 10
private const val MONTHS = FUTURE_MAX_YEARS * 12

/** خطوات بكرة السنين. */
val FUTURE_YEARS_STEPS: List<Int> = listOf(1, 2, 5, 10, 15, 20, 25, 30, 40)

fun yearsSteps(): List<Int> = FUTURE_YEARS_STEPS

/** أصغر خطوة ≥ [span] (لو العلامة بعد المدة الحالية) — مفيش ⇒ 40. */
fun stepAtLeast(span: Int): Int = FUTURE_YEARS_STEPS.firstOrNull { it >= span } ?: FUTURE_MAX_YEARS

/** ثوابت البلد (مواصفة §2.1): المحطات بعملة البلد · مبلغ البداية لو الدخل مش معروف · خطوة «+» و«−». */
data class FutureRules(val milestones: List<Pair<Halalas, InvestFutureKey>>, val fallbackStartMinor: Halalas, val stepMinor: Halalas)

fun futureRules(countryCode: String): FutureRules = if (countryCode.uppercase() == "EG") {
    FutureRules(
        listOf(100_000_000L to InvestFutureKey.FUT_MILE_FIRST_M, 500_000_000L to InvestFutureKey.FUT_MILE_5M, 1_000_000_000L to InvestFutureKey.FUT_MILE_10M),
        200_000L, 50_000L,
    )
} else {
    FutureRules(
        listOf(10_000_000L to InvestFutureKey.FUT_MILE_100K, 50_000_000L to InvestFutureKey.FUT_MILE_HALF_M, 100_000_000L to InvestFutureKey.FUT_MILE_FIRST_M),
        50_000L, 10_000L,
    )
}

/**
 * مبلغ «استثمر أكتر» أول ما يفتح (قاعدة «ادفع لنفسك الأول» 10% — §87): عُشر الدخل (بعد شيل آخر 3 أرقام زي النموذج) أو مبلغ البلد الثابت
 * لو الدخل مش معروف؛ + التحويش لو فيه تحويش؛ مقرّب لأقرب خطوة (النص لفوق) وأقله خطوة واحدة.
 */
fun startAmount(i: FutureInputs): Halalas {
    val rules = futureRules(i.countryCode)
    val step = rules.stepMinor
    val tenth = i.incomeMinor?.let { (it - it % 1000) / 10 } ?: rules.fallbackStartMinor
    val raw = if (i.low || i.saveUnknown) tenth else addMoney(i.saveMinor!!, tenth)
    val rounded = if (raw <= 0L) 0L else (raw + step / 2) / step * step
    return maxOf(step, rounded)
}

/** أقصى مبلغ لـ«+»: 4 أضعاف البداية أو الدخل كله (الأكبر). */
fun maxAmount(i: FutureInputs, start: Halalas): Halalas = maxOf(multiplyMoneyByInt(start, 4), i.incomeMinor ?: 0L)

/** متوسط «بتستثمرها في»: زي أملاكك ⇒ [holdingsMixBp] (أو الأسهم لو مفيش أملاك بتكبر) · أسهم · ذهب · عقار = التضخم. */
fun mixBpOf(mix: FutureMix, split: InvestableSplit, rates: InvestRates): Int = when (mix) {
    FutureMix.MINE -> holdingsMixBp(split, rates) ?: rates.sharesBp
    FutureMix.SHARES -> rates.sharesBp
    FutureMix.GOLD -> rates.goldBp
    FutureMix.PROPERTY -> rates.cpiBp
}

/** مصدر متوسط الاختيار (للسطر تحت الأزرار) — «زي أملاكك» مالوش مصدر واحد ⇒ null. */
fun mixSourceOf(mix: FutureMix, rates: InvestRates): AverageEntry? = when (mix) {
    FutureMix.MINE -> null
    FutureMix.SHARES -> rates.shares
    FutureMix.GOLD -> rates.gold
    FutureMix.PROPERTY -> rates.cpi
}

/** نتيجة الـ40 سنة: لكل سنة الخطوط التلاتة بالشكل المختار + الذهب والأسهم والمستثمَر بفلوس النهارده (للدخل من غير شغل). */
internal class FutureRun(val keep: List<Halalas>, val invest: List<Halalas>, val more: List<Halalas>, val goldReal: List<Halalas>,
    val stkReal: List<Halalas>, val p2Real: List<Halalas>, val p3Real: List<Halalas>)

internal fun runFuture(i: FutureInputs, r: InvestRates, amt3: Halalas, mixBp: Int, todayMoney: Boolean): FutureRun {
    val mRe = monthlyRateFromAnnual(r.cpiBp)
    val mGold = monthlyRateFromAnnual(r.goldBp)
    val mStk = monthlyRateFromAnnual(r.sharesBp)
    val mMix = monthlyRateFromAnnual(mixBp)
    var re = i.split.propertyMinor
    var gold = i.split.goldMinor
    var stk = i.split.sharesMinor
    val flat = i.split.noGrowthMinor
    var p2 = 0L
    var p3 = 0L
    val amt2 = if (i.low || i.saveUnknown) 0L else i.saveMinor!!
    val save1 = i.saveMinor ?: 0L
    val out = List(7) { ArrayList<Halalas>(FUTURE_MAX_YEARS + 1) }
    for (t in 0..MONTHS) {
        if (t % 12 == 0) {
            val y = t / 12
            val debt = sumMoney(i.debts.map { maxOf(0L, subtractMoney(it.balanceMinor, multiplyMoneyByInt(it.monthlyMinor, t.toLong()))) })
            val base = subtractMoney(addMoney(addMoney(re, gold), addMoney(addMoney(stk, flat), i.cashMinor)), debt)
            fun real(v: Halalas) = deflate(v, 12 * y, r.cpiBp)
            fun shown(v: Halalas) = if (todayMoney) real(v) else v
            out[0].add(shown(addMoney(base, multiplyMoneyByInt(save1, t.toLong()))))
            out[1].add(shown(addMoney(base, p2)))
            out[2].add(shown(addMoney(base, p3)))
            out[3].add(real(gold)); out[4].add(real(stk)); out[5].add(real(p2)); out[6].add(real(p3))
        }
        if (t == MONTHS) break
        re = addMoney(re, mulDivWide(re, mRe, MONTHLY_RATE_SCALE))
        gold = addMoney(gold, mulDivWide(gold, mGold, MONTHLY_RATE_SCALE))
        stk = addMoney(stk, mulDivWide(stk, mStk, MONTHLY_RATE_SCALE))
        p2 = addMoney(p2, mulDivWide(p2, mMix, MONTHLY_RATE_SCALE), amt2)
        p3 = addMoney(p3, mulDivWide(p3, mMix, MONTHLY_RATE_SCALE), amt3)
    }
    return FutureRun(out[0], out[1], out[2], out[3], out[4], out[5], out[6])
}

/** المحطات على خط [values] (0..40): عدّيتها النهارده · أول سنة بتوصلها · مش خلال 40 سنة. */
fun milestones(values: List<Halalas>, rules: FutureRules): List<MilestoneRow> = rules.milestones.map { (th, key) ->
    when {
        values[0] >= th -> MilestoneRow(th, key, MileState.PASSED, null)
        else -> {
            val i = (1..FUTURE_MAX_YEARS).firstOrNull { values[it] >= th }
            if (i == null) MilestoneRow(th, key, MileState.NEVER, null) else MilestoneRow(th, key, MileState.REACHED, i)
        }
    }
}

/**
 * الدخل في الشهر من غير شغل (بفلوس النهارده): الربح **فوق التضخم بس** من الذهب والأسهم واللي استثمرته (من غير ما تلمس الأصل) ÷ 12،
 * + الإيجار والعائد المعروفين. معدل تحت التضخم ⇒ صفر من النوع ده.
 */
fun passiveAt(goldReal: Halalas, stkReal: Halalas, potReal: Halalas, r: InvestRates, mixBp: Int, knownMinor: Halalas): Halalas {
    fun above(bp: Int) = maxOf(0, bp - r.cpiBp).toLong()
    return addMoney(
        addMoney(mulDivWide(goldReal, above(r.goldBp), 120_000), mulDivWide(stkReal, above(r.sharesBp), 120_000)),
        addMoney(mulDivWide(potReal, above(mixBp), 120_000), knownMinor),
    )
}

/** التوقع كله لاختيار [c] — null لو المتوسطات مش متاحة (البطاقة بتقول «غير متاح» وسببه). */
fun projectFuture(i: FutureInputs, c: FutureChoice): FutureProjection? {
    val r = (i.rates as? InvestRatesState.Ready)?.rates ?: return null
    val years = c.years.coerceIn(1, FUTURE_MAX_YEARS)
    val paths = i.paths
    val selected = c.path?.takeIf { it in paths } ?: if (FuturePath.INVEST in paths) FuturePath.INVEST else FuturePath.MORE
    val rules = futureRules(i.countryCode)
    val start = startAmount(i)
    val max = maxAmount(i, start)
    val amount = (c.amountMinor ?: start).coerceIn(rules.stepMinor, maxOf(rules.stepMinor, max))
    val mixBp = mixBpOf(c.mix, i.split, r)
    val run = runFuture(i, r, amount, mixBp, c.todayMoney)
    val lines = paths.map { PathLine(it, if (it == FuturePath.KEEP) run.keep else if (it == FuturePath.INVEST) run.invest else run.more) }
    val fv = lines.first { it.path == selected }.values
    val end = fv[years]
    val goals = i.goals.map { g -> GoalRow(g.name, g.targetDate, g.targetDate?.let { d -> if (d > i.today) fullMonthsBetween(i.today, d) else 0 }) }
    val pot = when (selected) {
        FuturePath.KEEP -> 0L
        FuturePath.INVEST -> run.p2Real[years]
        FuturePath.MORE -> run.p3Real[years]
    }
    val now = passiveAt(run.goldReal[0], run.stkReal[0], 0L, r, mixBp, i.passiveKnownMinor)
    val atYearReal = passiveAt(run.goldReal[years], run.stkReal[years], pot, r, mixBp, i.passiveKnownMinor)
    val atYear = if (c.todayMoney) atYearReal else compoundYears(atYearReal, years, r.cpiBp)
    val pins = c.marks.filter { it - i.thisYear in 1..FUTURE_MAX_YEARS }.sorted().map { it to fv[it - i.thisYear] }
    return FutureProjection(
        i, c.copy(years = years, path = selected, amountMinor = amount), r, selected, lines, end, subtractMoney(end, i.netNowMinor),
        start, max, rules.stepMinor, mixBp, mixSourceOf(c.mix, r), milestones(fv, rules), goals,
        PassiveIncome(now, atYear, i.passiveKnownMinor), pins,
    )
}

/**
 * «في البلدين»: كل بلد بطريق «زي ما إنت» بعملته بعد نفس المدة، والمجموع بعملة البلد الشغال بسعر صرف النهارده (مواصفة §2.5):
 * جنيه ÷ ريال = [FxRate.egpPerSar4] ÷ 10,000. أي قيمة مش معروفة أو سعر الصرف ناقص ⇒ المجموع null («غير متاح»).
 */
fun countriesTotal(mine: CountryRow, others: List<CountryRow>, fx: FxRate?): Halalas? {
    val own = mine.valueMinor ?: return null
    var total = own
    for (o in others) {
        val v = o.valueMinor ?: return null
        total = addMoney(total, convertCountry(v, o.currency, mine.currency, fx) ?: return null)
    }
    return total
}

/** ريال ⇄ جنيه بسعر [fx] — نفس العملة ⇒ زي ما هو، أي عملة تانية أو سعر ناقص ⇒ null. */
fun convertCountry(v: Halalas, from: Currency, to: Currency, fx: FxRate?): Halalas? = when {
    from == to -> v
    fx == null || fx.egpPerSar4 <= 0L -> null
    from == Currency.EGP && to == Currency.SAR -> mulDivWide(v, 10_000, fx.egpPerSar4)
    from == Currency.SAR && to == Currency.EGP -> mulDivWide(v, fx.egpPerSar4, 10_000)
    else -> null
}

/** «جنيه لكل ريال × 10,000» من سعر الدولار بالريال (هللة) وبالجنيه (قرش): تقريب النص لفوق. */
fun egpPerSar4(usdSarMinor: Long, usdEgpMinor: Long): Long {
    require(usdSarMinor > 0 && usdEgpMinor > 0)
    return mulDivWide(usdEgpMinor, 10_000, usdSarMinor)
}
