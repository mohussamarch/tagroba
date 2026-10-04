package app.masroufy.core

/**
 * الحول من **سلسلة الرصيد** (OVERRIDES §62 — «ميزة مصروفي اللي محدش عنده»): التطبيق عارف رصيدك بعد كل يوم،
 * فيعرف بالتاريخ لو نزلت تحت النصاب، ويقترح ميعاد الزكاة.
 *
 * السلسلة = رصيد المحافظ (افتتاحي + الوارد − الصادر، والتحويل الداخلي الجاي **لـ** المحفظة وارد عليها — زي `ReconcileBalance`)
 * + الأصول اللي عليها زكاة غير الكاش **من يوم ما اتملكت** بقيمتها النهارده (أسعار زمان مش متسجلة).
 * قرارات تنفيذ (Claude — المالك يقدر يغيّرها): الرصيد **آخر اليوم** (النزول والرجوع في نفس اليوم ما بيتحسبش) ·
 * النصاب بأسعار النهارده · المحفظة قبل يوم افتتاحها ما بتدخلش (التطبيق ما يعرفش رصيدها).
 */
data class DayBalance(val date: IsoDate, val balanceMinor: Halalas)

/** حركات المحافظ: (المحفظة، اليوم، التغيير). الافتتاحي حركة يوم الافتتاح. */
private fun walletEvents(wallets: List<Wallet>, transactions: List<Transaction>): List<Triple<Id, IsoDate, Halalas>> {
    val byId = wallets.associateBy { it.id }
    val events = mutableListOf<Triple<Id, IsoDate, Halalas>>()
    for (w in wallets) events += Triple(w.id, w.openingAt, w.openingBalanceMinor)
    for (t in transactions) {
        byId[t.walletId]?.let { w ->
            if (t.occurredAt >= w.openingAt) events += Triple(w.id, t.occurredAt, if (t.observedDirection == Direction.OUT) -t.amountMinor else t.amountMinor)
        }
        // الطرف الداخل للتحويل الداخلي دايمًا وارد على المحفظة دي
        byId[t.transferToWalletId]?.let { w -> if (t.occurredAt >= w.openingAt) events += Triple(w.id, t.occurredAt, t.amountMinor) }
    }
    return events
}

/** رصيد كل محفظة آخر يوم [date] — null لو المحفظة لسه ما اتفتحتش في التطبيق يومها. */
fun walletBalancesOn(wallets: List<Wallet>, transactions: List<Transaction>, date: IsoDate): Map<Id, Halalas?> {
    val totals = LinkedHashMap<Id, Halalas?>().apply { wallets.forEach { put(it.id, if (it.openingAt <= date) 0L else null) } }
    for ((id, day, delta) in walletEvents(wallets, transactions)) {
        if (day > date) continue
        totals[id] = addMoney(totals[id] ?: continue, delta)
    }
    return totals
}

/** سلسلة من حركات (اليوم، التغيير): نقطة لكل يوم فيه تغيير برصيد آخره، بالترتيب. */
fun balanceSeries(changes: List<Pair<IsoDate, Halalas>>): List<DayBalance> {
    val perDay = changes.groupBy({ it.first }, { it.second })
    var running = 0L
    return perDay.keys.sorted().map { day ->
        running = addMoney(running, sumMoney(perDay.getValue(day)))
        DayBalance(day, running)
    }
}

/**
 * سلسلة اللي عليه زكاة: المحافظ + كل سطر محسوب غير الكاش من [ZakatHolding.heldSince] (null ⇒ من أول البيانات).
 * المحفظة بالسالب بتتحسب بالسالب هنا (الإجمالي) — الدين ما بيتخصمش من الحساب نفسه، بس النزول تحت النصاب حقيقي.
 */
fun zakatWealthSeries(wallets: List<Wallet>, transactions: List<Transaction>, items: List<ZakatItem>): List<DayBalance> {
    val cash = walletEvents(wallets, transactions).map { (_, day, delta) -> day to delta }
    val first = cash.minOfOrNull { it.first }
    // الدين اللي اتحصّل وقبض الجمعية فلوسهم دخلت المحفظة فعلًا (موجودة في الكاش) ⇒ ما بيتضافوش تاني
    val others = items.filter {
        it.status == ZakatItemStatus.COUNTED && it.holding !is ZakatHolding.Cash && it.holding !is ZakatHolding.CollectedReceivable &&
            it.holding !is ZakatHolding.CollectedRosca
    }
        .mapNotNull { i -> (i.holding.heldSince ?: first)?.let { it to i.zakatableMinor!! } }
    return balanceSeries(cash + others)
}

/** الرصيد آخر يوم [date] — null لو قبل أول نقطة (مش معروف). */
fun balanceOn(series: List<DayBalance>, date: IsoDate): Halalas? = series.lastOrNull { it.date <= date }?.balanceMinor

/** السعودية: النزول جوه السنة بيقطع الحول (§3.2.1) · مصر: العبرة بأول السنة وآخرها بس (فتوى 5890). */
private fun dipRestarts(country: ZakatCountry): Boolean = when (zakatRule(country, ZakatTopic.MID_YEAR_DIP).rulingKey) {
    TextKey.ZAKAT_RULE_DIP_RESTART -> true
    TextKey.ZAKAT_RULE_DIP_START_END -> false
    else -> error("unknown MID_YEAR_DIP rule")
}

/**
 * السعودية: لو الرصيد نزل تحت النصاب في أي يوم من [from] لـ[to] ⇒ الحول بدأ من جديد من أول يوم رجع فيه فوقه.
 * مصر (فتوى 5890): **أول السنة وآخرها بس** — النزول في النص ما بيقطعش؛ لو أول السنة كان تحت النصاب ⇒ الحول بيبدأ من أول يوم
 * وصله بعدها. آخر السنة بيتفحص في الحساب نفسه (تحت النصاب يوم الميعاد ⇒ مفيش زكاة).
 * [HawlState.Complete.verified] = البيانات غطّت السنة كلها (فيه رصيد معروف يوم [from]).
 */
fun checkHawl(country: ZakatCountry, series: List<DayBalance>, nisab: Halalas, from: IsoDate, to: IsoDate): HawlState {
    if (!dipRestarts(country)) {
        val start = balanceOn(series, from) ?: return HawlState.Complete(false)
        if (start >= nisab) return HawlState.Complete(true)
        return HawlState.Restarted(series.firstOrNull { it.date > from && it.date <= to && it.balanceMinor >= nisab }?.date)
    }
    val start = balanceOn(series, from)
    val points = listOfNotNull(start?.let { DayBalance(from, it) }) + series.filter { it.date > from && it.date <= to }
    val lastBelow = points.indexOfLast { it.balanceMinor < nisab }
    if (lastBelow < 0) return HawlState.Complete(start != null)
    return HawlState.Restarted(points.drop(lastBelow + 1).firstOrNull { it.balanceMinor >= nisab }?.date)
}

/**
 * اقتراح بداية الحول (المستخدم بيأكد أو يغيّر — قرار المالك §62-ج): «أول يوم الفلوس وصلت النصاب».
 * السعودية: بداية **آخر فترة متصلة** فوق النصاب (النزول بيبدأ الحول من جديد) — null لو تحت النصاب دلوقتي.
 * مصر (5890): بداية **آخر سلسلة سنين ما اتقطعتش** — من أول يوم وصل النصاب، والسلسلة بتتقطع بس لو يوم ميعاد (نفس اليوم الهجري
 * كل سنة لحد [today]) كان تحت النصاب؛ ساعتها البداية الجديدة = أول يوم وصله بعدها. النزول في نص السنة ما بيقطعش.
 * [today] null ⇒ آخر يوم في السلسلة.
 */
fun suggestHawlStart(country: ZakatCountry, series: List<DayBalance>, nisab: Halalas, today: IsoDate? = null): IsoDate? {
    if (series.isEmpty()) return null
    if (!dipRestarts(country)) {
        val until = today ?: series.last().date
        var start = series.firstOrNull { it.balanceMinor >= nisab }?.date ?: return null
        var anniversary = nextZakatDate(start)
        while (anniversary <= until) {
            if ((balanceOn(series, anniversary) ?: 0L) < nisab) {
                start = series.firstOrNull { it.date > anniversary && it.balanceMinor >= nisab }?.date ?: return null
                anniversary = nextZakatDate(start)
            } else {
                anniversary = nextZakatDate(anniversary)
            }
        }
        return start
    }
    if (series.last().balanceMinor < nisab) return null
    val lastBelow = series.indexOfLast { it.balanceMinor < nisab }
    return series[lastBelow + 1].date
}

/** ميعاد الزكاة = نفس اليوم الهجري (أم القرى) بعد سنة — آخر يوم في الشهر لو الشهر السنة الجاية أقصر. */
fun nextZakatDate(date: IsoDate): IsoDate {
    if (!isValidIsoDate(date)) throw ZakatError(uiText(TextKey.ZAKAT_DATE_INVALID))
    return addHijriYears(date, 1)
}
