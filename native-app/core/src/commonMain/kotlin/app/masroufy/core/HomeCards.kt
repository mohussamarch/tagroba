package app.masroufy.core

/**
 * كروت الرئيسية (اختيار المالك 2026-10-15 من نافذة اختيار — HANDOVER جلسة 31 الخطوة 29 · لوحات `HomeCards`/`HomeDaily`/`HomeBars`/`HomeQuick`):
 * «خلي الكروت دي موجودة وهو يقدر يعدلها او يغير ترتيبها في الصفحة الرئيسية».
 * - الترتيب الأول (رد المالك 2026-10-16 — اختار التاني مش المقترح): القادم ⇐ محتاجة مراجعة ⇐ تقدر تصرف النهارده ⇐ شخصيتك بتكلمك ⇐
 *   أكتر حاجة صرفت فيها ⇐ أهدافك ⇐ آخر العمليات.
 * - مستخبيين أول مرة (ما اتختاروش بس موجودين): ليك وعليك · استثماراتك.
 * - الترتيب والمخفي بيتحفظوا في الحساب (`userSettings/homeCards` — [UserSetting.HomeCards]) زي صف «أهم 5».
 * دوال نقية بس — التخزين في حالة الاستخدام `ManageHomeCards`.
 */
enum class HomeCardKind(val wire: String) {
    DAILY("daily"),
    BUDDY("buddy"),
    UPCOMING("upcoming"),
    TOP("top"),
    GOALS("goals"),
    REVIEW("review"),
    RECENT("recent"),
    PEOPLE("people"),
    INVEST("invest"),
    ;

    companion object {
        fun fromWire(wire: String): HomeCardKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** معرّف مستند الكروت في `userSettings`. */
const val HOME_CARDS_KEY = "homeCards"

/** الترتيب الأول (رد المالك 2026-10-16). */
val HOME_CARDS_DEFAULT_ORDER: List<HomeCardKind> = listOf(
    HomeCardKind.UPCOMING, HomeCardKind.REVIEW, HomeCardKind.DAILY, HomeCardKind.BUDDY, HomeCardKind.TOP, HomeCardKind.GOALS, HomeCardKind.RECENT,
    HomeCardKind.PEOPLE, HomeCardKind.INVEST,
)

/** المستخبيين أول مرة: «ليك وعليك» و«استثماراتك» (ما اتختاروش في النافذة). */
val HOME_CARDS_DEFAULT_HIDDEN: Set<HomeCardKind> = setOf(HomeCardKind.PEOPLE, HomeCardKind.INVEST)

/** ترتيب الكروت كلها (الظاهر والمخفي) و[hidden] المخفيين. [custom] = اتعمل ترتيب بإيدك (مش الأول). */
data class HomeCardsLayout(val order: List<HomeCardKind>, val hidden: Set<HomeCardKind>, val custom: Boolean) {
    /** الكروت اللي بتتعرض في الرئيسية بالترتيب. */
    val visible: List<HomeCardKind> get() = order.filter { it !in hidden }
}

fun defaultHomeCards(): HomeCardsLayout = HomeCardsLayout(HOME_CARDS_DEFAULT_ORDER, HOME_CARDS_DEFAULT_HIDDEN, custom = false)

/**
 * المحفوظ ⇒ الترتيب: اسم مش معروف (نسخة أحدث) بيتشال، والتكرار بيتشال، وأي كارت مش في المحفوظ (كارت جديد) بيتحط في الآخر بترتيبه الأول
 * وبحالته الأولى (ظاهر أو مخفي). مفيش محفوظ (null) ⇒ الأول.
 */
fun homeCardsLayout(storedOrder: List<String>?, storedHidden: List<String>?): HomeCardsLayout {
    if (storedOrder == null) return defaultHomeCards()
    val known = storedOrder.mapNotNull { HomeCardKind.fromWire(it) }.distinct()
    val added = HOME_CARDS_DEFAULT_ORDER.filter { it !in known }
    val hidden = storedHidden.orEmpty().mapNotNull { HomeCardKind.fromWire(it) }.toSet() + added.filter { it in HOME_CARDS_DEFAULT_HIDDEN }
    return HomeCardsLayout(known + added, hidden, custom = true)
}

/** سهم لفوق ([delta] = -1) أو لتحت (+1). أول واحد لفوق أو آخر واحد لتحت ⇒ زي ما هو. */
fun moveHomeCard(layout: HomeCardsLayout, kind: HomeCardKind, delta: Int): HomeCardsLayout {
    val i = layout.order.indexOf(kind)
    val j = i + delta
    if (i < 0 || j < 0 || j >= layout.order.size) return layout
    val order = layout.order.toMutableList()
    order[i] = order[j]
    order[j] = kind
    return layout.copy(order = order, custom = true)
}

/** مفتاح ظاهر/مخفي. */
fun toggleHomeCard(layout: HomeCardsLayout, kind: HomeCardKind): HomeCardsLayout =
    layout.copy(hidden = if (kind in layout.hidden) layout.hidden - kind else layout.hidden + kind, custom = true)

// ─── «تقدر تصرف النهارده» (رد المالك 2026-10-16) ───

/**
 * «تقدر تصرف النهارده» = اللي معاك دلوقتي − الالتزامات اللي ميعادها قبل يوم المرتب، ÷ الأيام الفاضلة، **مقرّب لتحت** لأقرب وحدة كاملة.
 * مثال المالك: معاك 3,000 وعليك قسط واشتراكات بـ1,000 قبل المرتب وفاضل 10 أيام ⇒ 200 في اليوم.
 * [perDayMinor] null = «غير متاح» (رصيد مش معروف · يوم المرتب مش معروف) — مش صفر. الالتزامات أكبر من اللي معاك ⇒ صفر و[short] = true.
 */
data class DailySpend(
    val perDayMinor: Halalas?,
    val onHandMinor: Halalas?,
    val duesMinor: Halalas,
    /** الأيام لحد المرتب (شامل النهارده) — null = مش معروف. */
    val daysLeft: Int?,
    val short: Boolean,
)

/** [onHandMinor] اللي معاك · [duesMinor] الالتزامات لحد المرتب · [daysLeft] الأيام شامل النهارده (≤ 0 أو null ⇒ «غير متاح»). */
fun dailySpend(onHandMinor: Halalas?, duesMinor: Halalas, daysLeft: Int?): DailySpend {
    val days = daysLeft?.takeIf { it > 0 }
    if (onHandMinor == null || days == null) return DailySpend(null, onHandMinor, duesMinor, days, short = false)
    val base = subtractMoney(onHandMinor, duesMinor)
    if (base <= 0L) return DailySpend(0L, onHandMinor, duesMinor, days, short = base < 0L)
    val raw = base / days
    return DailySpend(raw - raw % 100, onHandMinor, duesMinor, days, short = false)
}

/**
 * الالتزامات اللي بتتطرح: سطور التقويم اللي **بتدفعها** (`DueFlow.PAY`: أقساط · اشتراكات · فواتير · ديون · جمعيات · أحداث بمبلغ مخطط · زكاة)
 * من النهارده لحد [lastDay] (اليوم اللي قبل المرتب) بعملة المساحة. اختيار Claude: السطر اللي مالوش مبلغ (مناسبة من غير مبلغ) ما بيدخلش،
 * والسطر بعملة تانية ما بيتحوّلش.
 */
fun duesUntil(items: List<CalendarItem>, today: IsoDate, lastDay: IsoDate, currency: Currency): Halalas =
    sumMoney(duesItemsUntil(items, today, lastDay, currency).map { it.amountMinor!! })

/** سطور [duesUntil] نفسها — فاضية ⇒ «مفيش التزامات متسجلة قبل المرتب» بدل «0.00» (r041b P4 — CLAUDE.md #10). */
fun duesItemsUntil(items: List<CalendarItem>, today: IsoDate, lastDay: IsoDate, currency: Currency): List<CalendarItem> =
    items.filter { it.flow == DueFlow.PAY && it.amountMinor != null && it.currency == currency && it.date >= today && it.date <= lastDay }

/** عرض الشريط لكل صف من أكبر واحد (0–100، صحيح) — «أكتر حاجة صرفت فيها». الأكبر = 100، والصفر أو السالب = 0. */
fun sharesOfLargest(amounts: List<Halalas>): List<Int> {
    val max = amounts.maxOrNull() ?: return emptyList()
    if (max <= 0L) return amounts.map { 0 }
    return amounts.map { a -> if (a <= 0L) 0 else ((a * 100) / max).toInt().coerceIn(1, 100) }
}

/** نسبة المدّخر من الهدف (0–100، لتحت) — null لو المدّخر مش معروف أو الهدف صفر. */
fun goalPercent(savedMinor: Halalas?, targetMinor: Halalas): Int? {
    if (savedMinor == null || targetMinor <= 0L) return null
    if (savedMinor <= 0L) return 0
    return ((savedMinor * 100) / targetMinor).toInt().coerceIn(0, 100)
}
