package app.masroufy.core

/**
 * التقويم (OVERRIDES §65) — كل اللي ليه ميعاد في فترة: المستحقات (اشتراكات وفواتير · أقساط · جمعيات · ديون) · آخر ميعاد
 * للمشاريع · الأحداث · مناسبات الأشخاص · يوم المرتب · ميعاد الزكاة · المناسبات العامة.
 * **محسوب مش متخزن** (زي `DuesAgenda`): كل مرة من الكيانات، فمفيش ميعاد يبعد عن مصدره. المتخزن الوحيد هو **المبلغ المحجوز**
 * لكل سطر (`Reservation`) — وده علامة تخطيط بس: **عمره ما بيغيّر رصيد ولا ميزانية ولا مجموع شهر**.
 */
enum class CalendarItemType(val wire: String) {
    RECURRING("recurring"), INSTALLMENT("installment"), ROSCA_CONTRIBUTION("rosca_contribution"), ROSCA_PAYOUT("rosca_payout"),
    DEBT("debt"), PROJECT("project"), EVENT("event"), OCCASION("occasion"), PAYDAY("payday"),

    /** يوم قبض مصدر دخل بيومه هو (رد المالك §64-٣): بارت تايم · معاش · إيجار · وظيفة أسبوعي. */
    INCOME_PAY("income_pay"),
    ZAKAT("zakat"), PUBLIC_OCCASION("public_occasion"),
    ;

    companion object {
        fun fromWire(wire: String): CalendarItemType = entries.first { it.wire == wire }

        fun of(source: DueSource): CalendarItemType = when (source) {
            DueSource.ROSCA_CONTRIBUTION -> ROSCA_CONTRIBUTION
            DueSource.ROSCA_PAYOUT -> ROSCA_PAYOUT
            DueSource.INSTALLMENT -> INSTALLMENT
            DueSource.DEBT -> DEBT
            DueSource.RECURRING -> RECURRING
        }
    }
}

/**
 * سطر في التقويم. [amountMinor] null = **مالوش مبلغ معروف** (ما بنخترعش رقم — القاعدة 10). [flow] null = مفيش فلوس رايحة أو جاية
 * معروفة (مناسبة · آخر ميعاد مشروع · مناسبة عامة). [daysLeft] سالب = عدّى. [reservedMinor] null = مش محجوز.
 * [approximate] = تاريخ محسوب من تقويم أم القرى والرسمي بيتعلن بالرؤية (المناسبات العامة).
 */
data class CalendarItem(
    val type: CalendarItemType,
    val sourceId: Id,
    val title: String,
    val date: IsoDate,
    val amountMinor: Halalas?,
    val currency: Currency,
    val flow: DueFlow?,
    val daysLeft: Int,
    val reservedMinor: Halalas? = null,
    val approximate: Boolean = false,
)

/**
 * المصادر بعد ما الاستخدام قراها. [dues] من بُناة `DuesAgenda` (`roscaDueItems` …) لحد آخر الفترة — مفيش حساب جدول هنا.
 * [eventPlannedMinor] مبلغ الحدث من تجهيزاته (`eventCalendarAmount`) — الحدث اللي مش فيه ⇒ من غير مبلغ.
 * [personNames] الأشخاص **غير المؤرشفين** بس (المؤرشف مناسباته ما بتظهرش — زي التنبيهات §64).
 * [payday] null = مفيش ملف حساب ⇒ مفيش سطر مرتب. [zakat] السنين مع الباقي (null = لسه ما اتحسبتش).
 */
data class CalendarSources(
    val dues: List<DueItem> = emptyList(),
    val projects: List<Project> = emptyList(),
    val events: List<LifeEvent> = emptyList(),
    val eventPlannedMinor: Map<Id, Halalas> = emptyMap(),
    val occasions: List<Occasion> = emptyList(),
    val personNames: Map<Id, String> = emptyMap(),
    val payday: Int? = null,
    val zakat: List<Pair<ZakatYear, Halalas?>> = emptyList(),
    val publicOccasions: List<PublicOccasion> = emptyList(),
    /** «المحتوى الإسلامي: ظاهر» (§46) — مقفول ⇒ لا زكاة ولا رمضان والعيدين. */
    val islamicVisible: Boolean = true,
    /** مصادر الدخل في المساحة — أيام قبض اللي ليه سطر لوحده ([hasOwnPayLine]). */
    val incomeSources: List<IncomeSource> = emptyList(),
)

/**
 * المصدر ليه سطر قبض لوحده في التقويم (رد المالك §64-٣): **بارت تايم · معاش · إيجار** (شهري أو أسبوعي) و**الوظيفة الأسبوعي**
 * (§64-٤). **الوظيفة الشهري لأ** — يومها يوم مرتب الحساب (§64) وسطر المرتب موجود، فما يتكررش. العميل والاستثمار و«غيره» لأ.
 */
fun hasOwnPayLine(s: IncomeSource): Boolean = when (s.kind) {
    IncomeSourceKind.PART_TIME, IncomeSourceKind.PENSION, IncomeSourceKind.RENT -> true
    IncomeSourceKind.JOB -> s.payFrequency == PayFrequency.WEEKLY
    IncomeSourceKind.CLIENT, IncomeSourceKind.INVESTMENT, IncomeSourceKind.OTHER -> false
}

/**
 * أيام قبض المصدر في الفترة (شاملين) **جوه مدته** — من يوم بدايته لحد يوم قفله (المصدر الشغال يوم القبض بس). الشهري بيومه في الشهر
 * (29–31 بيتقيد بآخر الشهر — زي يوم المرتب) والأسبوعي بيومه في الأسبوع. يوم مش معروف ⇒ مفيش سطور (ما بنخترعش ميعاد).
 */
fun sourcePayDatesIn(s: IncomeSource, from: IsoDate, to: IsoDate): List<IsoDate> {
    val a = maxOf(from, s.startedAt)
    val b = s.endedAt?.let { minOf(to, it) } ?: to
    if (a > b) return emptyList()
    return when (s.payFrequency) {
        PayFrequency.MONTHLY -> s.expectedDayOfMonth?.let { paydaysIn(a, b, it) }.orEmpty()
        PayFrequency.WEEKLY -> {
            val weekday = s.payWeekday ?: return emptyList()
            generateSequence(addDaysIso(a, (weekday - isoWeekday(a)).mod(7))) { addDaysIso(it, 7) }.takeWhile { it <= b }.toList()
        }
    }
}

/** أيام المرتب في الفترة: يوم [payday] من كل شهر (29–31 بيتقيد بآخر الشهر — نفس `buildPeriod`). */
fun paydaysIn(from: IsoDate, to: IsoDate, payday: Int): List<IsoDate> {
    val a = parseIsoDate(from)
    val b = parseIsoDate(to)
    val out = mutableListOf<IsoDate>()
    var index = a.year * 12 + a.month - 1
    while (index <= b.year * 12 + b.month - 1) {
        val start = buildPeriod(index.floorDiv(12), index.mod(12) + 1, payday).start
        if (start in from..to) out += start
        index++
    }
    return out
}

/** يوم المرتب الجاي **بعد** النهارده (لو النهارده يوم المرتب ⇒ الشهر الجاي). */
fun nextPaydayAfter(today: IsoDate, payday: Int): IsoDate =
    dayNumberToIso(toDayNumber(parseIsoDate(periodForDate(today, payday).end)) + 1)

/** كل مرات المناسبة في الفترة: السنوية مرة كل سنة (ومش قبل سنتها لو متسجلة)، واللي مرة واحدة يومها بس. */
fun occasionDatesIn(o: Occasion, from: IsoDate, to: IsoDate): List<IsoDate> {
    if (!o.yearly) return listOfNotNull(o.year?.let { occasionDateIn(o, it) }?.takeIf { it in from..to })
    val first = maxOf(parseIsoDate(from).year, o.year ?: Int.MIN_VALUE)
    return (first..parseIsoDate(to).year).map { occasionDateIn(o, it) }.filter { it in from..to }
}

private val TYPE_ORDER = CalendarItemType.entries

/** بالتاريخ، وفي نفس اليوم: اللي هتدفعه · اللي هتستلمه · اللي من غير فلوس — وبعدين النوع والمصدر (ثابت). */
fun sortCalendar(items: List<CalendarItem>): List<CalendarItem> =
    items.sortedWith(compareBy<CalendarItem>({ it.date }, { it.flow?.ordinal ?: DueFlow.entries.size }, { TYPE_ORDER.indexOf(it.type) }, { it.sourceId }))

/**
 * سطور التقويم من [from] لحد [to] (شاملين) بالترتيب، وعلى كل سطر حجزه لو موجود. [currency] عملة المساحة (§41) — للسطور اللي
 * مالهاش عملة من نفسها (الحدث · المناسبة · المرتب · المناسبة العامة).
 */
fun buildCalendar(
    from: IsoDate,
    to: IsoDate,
    today: IsoDate,
    currency: Currency,
    sources: CalendarSources,
    reservations: List<Reservation> = emptyList(),
): List<CalendarItem> {
    val out = mutableListOf<CalendarItem>()
    fun add(type: CalendarItemType, id: Id, title: String, date: IsoDate, amount: Halalas?, cur: Currency, flow: DueFlow?, approx: Boolean = false) {
        if (date in from..to) out += CalendarItem(type, id, title, date, amount, cur, flow, daysBetween(today, date), approximate = approx)
    }
    for (d in sources.dues) add(CalendarItemType.of(d.source), d.sourceId, d.title, d.dueAt, d.amountMinor, d.currency, d.flow)
    for (p in sources.projects) {
        val deadline = p.deadline ?: continue
        if (!p.archived) add(CalendarItemType.PROJECT, p.id, uiText(TextKey.CAL_TITLE_PROJECT_DEADLINE, p.name), deadline, null, currency, null)
    }
    for (e in sources.events) {
        if (!e.archived) add(CalendarItemType.EVENT, e.id, e.name, e.date, sources.eventPlannedMinor[e.id], currency, DueFlow.PAY)
    }
    val eventNames = sources.events.associate { it.id to it.name }
    for (o in sources.occasions) {
        val person = o.personId?.let { sources.personNames[it] }
        if (o.personId != null && person == null) continue
        val title = occasionTitle(o, person, o.sourceEventId?.let(eventNames::get))
        for (date in occasionDatesIn(o, from, to)) add(CalendarItemType.OCCASION, o.id, title, date, null, currency, null)
    }
    sources.payday?.let { day ->
        for (date in paydaysIn(from, to, day)) add(CalendarItemType.PAYDAY, PAYDAY_SOURCE_ID, uiText(TextKey.CAL_TITLE_PAYDAY), date, null, currency, DueFlow.RECEIVE)
    }
    // أيام قبض المصادر التانية — كل مصدر بيومه وبعملته، **من غير مبلغ** زي المرتب (رد المالك §64-٣)
    for (s in sources.incomeSources) {
        if (!hasOwnPayLine(s)) continue
        val title = uiText(TextKey.CAL_TITLE_SOURCE_PAY, s.name)
        for (date in sourcePayDatesIn(s, from, to)) add(CalendarItemType.INCOME_PAY, s.id, title, date, null, s.currency, DueFlow.RECEIVE)
    }
    if (sources.islamicVisible) {
        for ((year, remaining) in sources.zakat) {
            if (year.closed && remaining != null && remaining <= 0) continue
            add(CalendarItemType.ZAKAT, year.id, uiText(TextKey.CAL_TITLE_ZAKAT), year.dueAt, if (year.closed) remaining else null, year.currency, DueFlow.PAY)
        }
        for (p in sources.publicOccasions) add(CalendarItemType.PUBLIC_OCCASION, p.id, p.kind.label, p.date, null, currency, null, approx = true)
    }
    return sortCalendar(applyReservations(out, reservations))
}

/** معرّف سطر المرتب — واحد للحساب كله (§64: يوم المرتب واحد مش لكل بلد). */
const val PAYDAY_SOURCE_ID = "payday"
