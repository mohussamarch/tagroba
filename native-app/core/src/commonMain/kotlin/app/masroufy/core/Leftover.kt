package app.masroufy.core

/**
 * «فاضلك تقريبًا» (توضيح المالك §65): الفلوس اللي معاك دلوقتي − المواعيد الجاية اللي إنت حسبتها منها (الحجز).
 * - **بمرتب شهري** ⇒ «فاضلك تقريبًا آخر الشهر»: بيتطرح بس المحسوب اللي ميعاده **قبل المرتب الجاي**.
 * - **من غير مرتب ثابت** ⇒ «فاضلك تصرف من اللي معاك»: بيتطرح كل المحسوب الجاي، **ومن غير كلمة «آخر الشهر»**.
 * أعداد صحيحة بس. **رصيد محفظة مش معروف ⇒ الرقم «غير متاح»** (مش رقم أكيد على بيانات مجهولة — القاعدة 10)، ومحفظة رصيدها
 * مش متطابق مع الكشف ⇒ الرقم بيتعلّم **تقريبي**. ده رقم توقّع لوحده — **ما بيغيّرش** أي رصيد أو ميزانية أو مجموع.
 */
enum class LeftoverMode { UNTIL_MONTH_END, FROM_WHAT_YOU_HAVE }

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
 * بمرتب = عنده مصدر دخل **وظيفة** (`JOB`) شغال النهارده (اختيار Claude — المالك يقدر يغيّره). مصادر الدخل لسه مالهاش مستودع في
 * الفرع ده ⇒ الاستخدام بياخد النتيجة كـ`salaried` من برا لحد ما تتوصل.
 */
fun isSalaried(sources: List<IncomeSource>, today: IsoDate): Boolean = sourcesActiveOn(sources, today).any { it.kind == IncomeSourceKind.JOB }

/**
 * [balances] رصيد كل محفظة بعملة المساحة النهارده (null = مش معروف). [items] سطور التقويم من النهارده وطالع وعليها الحجز —
 * الميعاد اللي اتدفع أو اتشال **مش هيبقى فيها** فحجزه ما بيتطرحش تاني (الفلوس خرجت خلاص).
 * [nextPayday] null مع [salaried] ⇒ مفيش «آخر الشهر» نعرفه ⇒ بيتحسب زي اللي من غير مرتب.
 */
fun projectLeftover(
    balances: List<Halalas?>,
    items: List<CalendarItem>,
    today: IsoDate,
    currency: Currency,
    salaried: Boolean,
    nextPayday: IsoDate?,
    unreconciled: Boolean,
): LeftoverProjection {
    val mode = if (salaried && nextPayday != null) LeftoverMode.UNTIL_MONTH_END else LeftoverMode.FROM_WHAT_YOU_HAVE
    val until = if (mode == LeftoverMode.UNTIL_MONTH_END) nextPayday else null
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

/** العنوان: «فاضلك تقريبًا آخر الشهر» للي بمرتب، و«فاضلك تصرف من اللي معاك» للي من غير — من غير «آخر الشهر» خالص. */
fun leftoverLabel(p: LeftoverProjection): String =
    uiText(if (p.mode == LeftoverMode.UNTIL_MONTH_END) TextKey.LEFTOVER_MONTH_END else TextKey.LEFTOVER_FROM_WHAT_YOU_HAVE)

/** الرقم للعرض — «غير متاح» لو رصيد مش معروف. */
fun leftoverAmountText(p: LeftoverProjection, currency: Currency): String =
    p.leftoverMinor?.let { formatMoney(it, currency) } ?: uiText(TextKey.NOT_AVAILABLE)
