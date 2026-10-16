package app.masroufy.core

/**
 * كروت الرئيسية (اختيار المالك 2026-10-15 من نافذة اختيار — HANDOVER جلسة 31 الخطوة 29 · لوحات `HomeCards`/`HomeDaily`/`HomeBars`/`HomeQuick`):
 * «خلي الكروت دي موجودة وهو يقدر يعدلها او يغير ترتيبها في الصفحة الرئيسية».
 * - ظاهرين أول مرة: تقدر تصرف النهارده · شخصيتك بتكلمك · القادم · أكتر حاجة صرفت فيها · أهدافك · محتاجة مراجعة · آخر العمليات.
 * - مستخبيين أول مرة (ما اتختاروش بس موجودين): ليك وعليك · استثماراتك.
 * - الترتيب والمخفي بيتحفظوا في الحساب (`userSettings/homeCards` — [UserSetting.HomeCards]) زي صف «أهم 5».
 * ❓ الترتيب الأول اختيار Claude مستني المالك (OVERRIDES — كروت الرئيسية). دوال نقية بس — التخزين في حالة الاستخدام `ManageHomeCards`.
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

/** الترتيب الأول (❓ اختيار Claude مستني المالك). */
val HOME_CARDS_DEFAULT_ORDER: List<HomeCardKind> = HomeCardKind.entries.toList()

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

// ─── «تقدر تصرف النهارده» ───

/**
 * طريقة حساب «تقدر تصرف النهارده» — ❓ مستنية المالك (OVERRIDES — كروت الرئيسية). **التغيير بسطر واحد:** [DAILY_SPEND_BASIS].
 * - [LEFTOVER_UNTIL_PAYDAY]: اللي معاك ناقص المحسوب الجاي لحد المرتب (`LoadLeftover` — نفس «فاضلك تقريبًا» في الميزانيات) ÷ الأيام لحد المرتب.
 * - [ON_HAND_UNTIL_PAYDAY]: اللي معاك بس ÷ الأيام لحد المرتب (من غير ما نطرح الجاي).
 */
enum class DailySpendBasis { LEFTOVER_UNTIL_PAYDAY, ON_HAND_UNTIL_PAYDAY }

/** ❓ اختيار Claude لحد رد المالك (زي النموذج: اللي معاك ناقص الالتزامات الجاية لحد المرتب، على الأيام الفاضلة). */
val DAILY_SPEND_BASIS = DailySpendBasis.LEFTOVER_UNTIL_PAYDAY

/**
 * «تقدر تصرف النهارده»: [perDayMinor] بالهللة ومقرّب **لتحت** لأقرب وحدة كاملة (ريال/جنيه) — ما نقولش رقم أكبر من الحقيقة.
 * null = «غير متاح» (رصيد مش معروف · مفيش ميعاد مرتب) — مش صفر. اللي جاي أكبر من اللي معاك ⇒ صفر في اليوم و[short] = true.
 */
data class DailySpend(
    val perDayMinor: Halalas?,
    /** اللي اتقسم: اللي معاك (أو اللي معاك ناقص الجاي) — null = مش معروف. */
    val baseMinor: Halalas?,
    val onHandMinor: Halalas?,
    val upcomingMinor: Halalas,
    /** الأيام لحد المرتب (شامل النهارده) — null = مفيش ميعاد مرتب. */
    val daysLeft: Int?,
    val short: Boolean,
    val approximate: Boolean,
)

/** [onHandMinor] اللي معاك · [upcomingMinor] المحسوب الجاي · [daysLeft] الأيام لحد المرتب شامل النهارده (≤ 0 أو null ⇒ «غير متاح»). */
fun dailySpend(
    onHandMinor: Halalas?,
    upcomingMinor: Halalas,
    daysLeft: Int?,
    approximate: Boolean,
    basis: DailySpendBasis = DAILY_SPEND_BASIS,
): DailySpend {
    val base = when (basis) {
        DailySpendBasis.LEFTOVER_UNTIL_PAYDAY -> onHandMinor?.let { subtractMoney(it, upcomingMinor) }
        DailySpendBasis.ON_HAND_UNTIL_PAYDAY -> onHandMinor
    }
    val days = daysLeft?.takeIf { it > 0 }
    if (base == null || days == null) return DailySpend(null, base, onHandMinor, upcomingMinor, days, short = false, approximate = approximate)
    if (base <= 0L) return DailySpend(0L, base, onHandMinor, upcomingMinor, days, short = base < 0L, approximate = approximate)
    val raw = base / days
    return DailySpend(raw - raw % 100, base, onHandMinor, upcomingMinor, days, short = false, approximate = approximate)
}

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
