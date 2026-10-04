package app.masroufy.core

/**
 * «فاضلك تقريبًا» (توضيح المالك §65): الفلوس اللي معاك دلوقتي − المواعيد الجاية اللي إنت حسبتها منها (الحجز).
 * - **بمرتب شهري** ⇒ «فاضلك تقريبًا آخر الشهر»: بيتطرح بس المحسوب اللي ميعاده **قبل المرتب الجاي**. «بمرتب» = وظيفة · بارت تايم ·
 *   معاش · إيجار شهري (رد المالك §65)؛ ولو أقرب قبض من مصدر تاني قبل يوم مرتب الحساب ⇒ «لحد القبض الجاي».
 * - **من غير مرتب ثابت** ⇒ «فاضلك تصرف من اللي معاك»: بيتطرح كل المحسوب الجاي، **ومن غير كلمة «آخر الشهر»**.
 * أعداد صحيحة بس. **رصيد محفظة مش معروف ⇒ الرقم «غير متاح»** (مش رقم أكيد على بيانات مجهولة — القاعدة 10)، ومحفظة رصيدها
 * مش متطابق مع الكشف ⇒ الرقم بيتعلّم **تقريبي**. ده رقم توقّع لوحده — **ما بيغيّرش** أي رصيد أو ميزانية أو مجموع.
 */
enum class LeftoverMode {
    /** القبض الجاي هو يوم المرتب بتاع الحساب (الشهري) ⇒ «فاضلك تقريبًا آخر الشهر». */
    UNTIL_MONTH_END,
    /** القبض الجاي من مصدر تاني قبل يوم المرتب (بارت تايم أسبوعي · معاش · إيجار) ⇒ «فاضلك تقريبًا لحد القبض الجاي». */
    UNTIL_NEXT_PAY,
    FROM_WHAT_YOU_HAVE,
}

data class LeftoverProjection(
    val mode: LeftoverMode,
    /** مجموع أرصدة المحافظ بعملة المساحة النهارده — null لو أي واحدة مش معروفة. */
    val onHandMinor: Halalas?,
    /** مجموع المحسوب اللي اتطرح. */
    val countedMinor: Halalas,
    val countedCount: Int,
    /** الفلوس اللي معاك − المحسوب (ممكن تطلع سالب — ما بنخبيش). null = «غير متاح». */
    val leftoverMinor: Halalas?,
    /** فيه محفظة رصيدها مش متطابق ⇒ الرقم تقريبي. */
    val approximate: Boolean,
    /** المرتب الجاي (بمرتب بس) — المحسوب لحد اليوم اللي قبله. */
    val until: IsoDate?,
    /** محسوب بعملة تانية — ما بيتطرحش ولا بيتحوّل. */
    val otherCurrencyCount: Int,
)

/**
 * المصدر ده بيخلّي صاحبه «بمرتب» (رد المالك §65): **وظيفة · بارت تايم · معاش · إيجار شهري** — الإيجار الأسبوعي لأ.
 * العميل والاستثمار و«غيره» لأ.
 */
fun countsAsSalaried(s: IncomeSource): Boolean = when (s.kind) {
    IncomeSourceKind.JOB, IncomeSourceKind.PART_TIME, IncomeSourceKind.PENSION -> true
    IncomeSourceKind.RENT -> s.payFrequency == PayFrequency.MONTHLY
    IncomeSourceKind.CLIENT, IncomeSourceKind.INVESTMENT, IncomeSourceKind.OTHER -> false
}

/** بمرتب = عنده مصدر واحد على الأقل من [countsAsSalaried] شغال النهارده. */
fun isSalaried(sources: List<IncomeSource>, today: IsoDate): Boolean = sourcesActiveOn(sources, today).any(::countsAsSalaried)

/** يوم في الأسبوع بنظام ISO: 1 = الاتنين … 7 = الحد (1970-01-01 كان خميس). */
fun isoWeekday(date: IsoDate): Int = (toDayNumber(parseIsoDate(date)) + 3).mod(7) + 1

/**
 * القبض الجاي من المصدر ده **بعد** النهارده (null = ميعاده مش معروف). **الوظيفة** يومها = يوم المرتب في الملف ([accountPayday]،
 * واحد للحساب كله §64) · الباقي يومه هو: الشهري يوم في الشهر (29–31 بيتقيد بآخر الشهر) والأسبوعي يوم في الأسبوع.
 * القبض بعد ما المصدر يتقفل ما بيتعدش.
 */
fun nextPayDate(s: IncomeSource, today: IsoDate, accountPayday: Int?): IsoDate? {
    val next = when {
        s.kind == IncomeSourceKind.JOB -> accountPayday?.let { nextPaydayAfter(today, it) }
        s.payFrequency == PayFrequency.WEEKLY -> s.payWeekday?.let { w -> addDaysIso(today, (w - isoWeekday(today) - 1).mod(7) + 1) }
        else -> s.expectedDayOfMonth?.let { nextPaydayAfter(today, it) }
    } ?: return null
    return next.takeIf { s.endedAt == null || it <= s.endedAt }
}

/** لحد إمتى «فاضلك» بيتحسب: [until] أقرب قبض جاي، و[monthEnd] = ده يوم المرتب الشهري بتاع الحساب. */
data class LeftoverHorizon(val until: IsoDate, val monthEnd: Boolean)

/**
 * أقرب قبض جاي من المصادر اللي بتخلّيك «بمرتب» والشغالة النهارده **بعملة المساحة**. null = مش «بمرتب» أو مفيش ميعاد معروف.
 * «آخر الشهر» بس لو أقرب قبض هو يوم مرتب الحساب؛ غير كده «لحد القبض الجاي» (اختيار Claude — المالك يقدر يغيّره).
 */
fun leftoverHorizon(sources: List<IncomeSource>, today: IsoDate, accountPayday: Int?, currency: Currency): LeftoverHorizon? {
    val dates = sourcesActiveOn(sources, today).filter { it.currency == currency && countsAsSalaried(it) }.mapNotNull { nextPayDate(it, today, accountPayday) }
    val until = dates.minOrNull() ?: return null
    return LeftoverHorizon(until, accountPayday != null && until == nextPaydayAfter(today, accountPayday))
}

/**
 * [balances] رصيد كل محفظة بعملة المساحة النهارده (null = مش معروف). [items] سطور التقويم من النهارده وطالع وعليها الحجز —
 * الميعاد اللي اتدفع أو اتشال **مش هيبقى فيها** فحجزه ما بيتطرحش تاني (الفلوس خرجت خلاص).
 * [nextPayday] null مع [salaried] ⇒ مفيش «آخر الشهر» نعرفه ⇒ بيتحسب زي اللي من غير مرتب. [monthEnd] = [nextPayday] هو يوم مرتب
 * الحساب (⇒ «آخر الشهر»)، ولا قبض تاني أقرب (⇒ «لحد القبض الجاي»).
 */
fun projectLeftover(
    balances: List<Halalas?>,
    items: List<CalendarItem>,
    today: IsoDate,
    currency: Currency,
    salaried: Boolean,
    nextPayday: IsoDate?,
    unreconciled: Boolean,
    monthEnd: Boolean = true,
): LeftoverProjection {
    val mode = when {
        !salaried || nextPayday == null -> LeftoverMode.FROM_WHAT_YOU_HAVE
        monthEnd -> LeftoverMode.UNTIL_MONTH_END
        else -> LeftoverMode.UNTIL_NEXT_PAY
    }
    val until = if (mode == LeftoverMode.FROM_WHAT_YOU_HAVE) null else nextPayday
    val counted = items.filter { it.reservedMinor != null && it.date >= today && (until == null || it.date < until) }
    val local = counted.filter { it.currency == currency }
    val countedMinor = sumMoney(local.map { it.reservedMinor!! })
    // مفيش ولا محفظة ⇒ مش «صفر معاك» — مش معروف
    val onHand = if (balances.isEmpty() || balances.any { it == null }) null else sumMoney(balances.map { it!! })
    return LeftoverProjection(
        mode = mode,
        onHandMinor = onHand,
        countedMinor = countedMinor,
        countedCount = local.size,
        leftoverMinor = onHand?.let { subtractMoney(it, countedMinor) },
        approximate = unreconciled,
        until = until,
        otherCurrencyCount = counted.size - local.size,
    )
}

/**
 * العنوان: «فاضلك تقريبًا آخر الشهر» لما القبض الجاي يوم مرتب الحساب · «فاضلك تقريبًا لحد القبض الجاي» لما قبض تاني أقرب ·
 * «فاضلك تصرف من اللي معاك» للي من غير مرتب — من غير «آخر الشهر» خالص.
 */
fun leftoverLabel(p: LeftoverProjection): String = uiText(
    when (p.mode) {
        LeftoverMode.UNTIL_MONTH_END -> TextKey.LEFTOVER_MONTH_END
        LeftoverMode.UNTIL_NEXT_PAY -> TextKey.LEFTOVER_NEXT_PAY
        LeftoverMode.FROM_WHAT_YOU_HAVE -> TextKey.LEFTOVER_FROM_WHAT_YOU_HAVE
    },
)

/** الرقم للعرض — «غير متاح» لو رصيد مش معروف. */
fun leftoverAmountText(p: LeftoverProjection, currency: Currency): String =
    p.leftoverMinor?.let { formatMoney(it, currency) } ?: uiText(TextKey.NOT_AVAILABLE)
