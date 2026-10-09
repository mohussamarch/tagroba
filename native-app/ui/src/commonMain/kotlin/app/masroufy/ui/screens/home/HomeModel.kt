package app.masroufy.ui.screens.home

import app.masroufy.core.AlertGroup
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.daysBetween
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.AlertInboxView
import app.masroufy.usecase.HomeScreenData
import app.masroufy.usecase.WithYouNow

/**
 * من نتايج حالات الاستخدام لحالة الرئيسية — **ترتيب وكلام بس، مفيش حساب فلوس** (CLAUDE.md #4): كل مبلغ جاي جاهز بالهللة.
 * الأيام لحد الراتب = فرق تاريخين (`daysBetween` من `core`) — مش فلوس.
 */

/** سطر الشهر تحت الخط الرفيع في البطاقة البطلة. [spentMinor] = `HomeScreenData.expenseMinor` (null ⇒ «غير متاح» مش صفر). */
data class HomeMonth(
    val spentMinor: Halalas?,
    /** فيه عمليات لسه نوعها مش محدد ⇒ الرقم «لحد دلوقتي» (`HomeScreenData.partial`). */
    val partial: Boolean,
    val transactionCount: Int,
    /** الراتب الجاي من `LoadCalendar.summary` (`untilPayday.nextPayday`) — null = مفيش ملف حساب. */
    val nextPayday: IsoDate?,
)

/** الرئيسية كلها: بيحمّل ⇒ فشل ⇒ جاهزة («معك الآن» إجباري، والشهر لوحده لو فشل بيبقى «غير متاح»). */
sealed interface HomeLoad {
    data object Loading : HomeLoad

    data object Failed : HomeLoad

    /** [inbox] = صفحة الإشعارات (null = ما اتقرتش) — كارت المساعد بيتختار منها وقت العرض عشان «×» يخفيه على طول. */
    data class Ready(val now: WithYouNow, val month: HomeMonth?, val inbox: List<AlertInboxView>?, val profileCard: Boolean) : HomeLoad
}

/** كارت المساعد (§68 — زي «القهوة هذا الشهر» في النموذج): **آخر سطر من مجموعة المساعد** في صفحة الإشعارات، مش كلام مخترع. */
data class AdvisorCard(val threadKey: String, val title: String, val body: String)

fun homeMonthOf(data: HomeScreenData, nextPayday: IsoDate?): HomeMonth =
    HomeMonth(data.expenseMinor, data.partial, data.transactionCount, nextPayday)

/** أحدث تنبيه من المساعد مش مقفول — الصفحة مرتبة الأحدث الأول (`RunAlertEngine.inbox`)، و[gone] = اللي اتمسح بـ«×». */
fun advisorCardOf(inbox: List<AlertInboxView>, gone: Set<String> = emptySet()): AdvisorCard? =
    inbox.firstOrNull { it.entry.kind.group == AlertGroup.ADVISOR && !it.muted && it.entry.threadKey !in gone }
        ?.let { AdvisorCard(it.entry.threadKey, it.entry.title, it.entry.body) }

/** «صرفت هذا الشهر 6,650.00» — المبلغ من غير عملة زي النموذج، و«غير متاح» لو مش معروف. */
fun spentLine(month: HomeMonth?, currency: Currency): String =
    t(TextKey.HOME_SPENT_MONTH, amountLabel(month?.spentMinor, currency, showCurrency = false))

/** «الراتب بعد ٢١ يومًا» (العدد بقواعد العربي: اليوم · غدًا · يومين · ٣–١٠ أيام · ١١+ يومًا). null = مفيش يوم راتب معروف. */
fun salaryLine(nextPayday: IsoDate?, today: IsoDate): String? {
    if (nextPayday == null) return null
    val days = daysBetween(today, nextPayday)
    return when {
        days <= 0 -> t(TextKey.HOME_SALARY_TODAY)
        days == 1 -> t(TextKey.HOME_SALARY_TOMORROW)
        days == 2 -> t(TextKey.HOME_SALARY_TWO_DAYS)
        days <= 10 -> t(TextKey.HOME_SALARY_FEW_DAYS, sentenceNumber(days))
        else -> t(TextKey.HOME_SALARY_MANY_DAYS, sentenceNumber(days))
    }
}

/**
 * الشاشة الفاضية (النموذج: «لا توجد عمليات هذا الشهر — أضف أول عملية أو فعّل قراءة رسائل البنك») = حساب لسه مفيهوش ولا محفظة ولا عملية.
 * لو فيه محافظ، البطاقة البطلة بتظهر (رصيدها معروف) والفاضي بيبقى سطر تحتها.
 */
fun isBrandNew(now: WithYouNow, month: HomeMonth?): Boolean = now.wallets.isEmpty() && (month?.transactionCount ?: 0) == 0

/** مفيش عمليات في الشهر ده (بس فيه محافظ) ⇒ سطر «لا توجد عمليات هذا الشهر» مكان كارت المساعد. */
fun isQuietMonth(now: WithYouNow, month: HomeMonth?): Boolean = now.wallets.isNotEmpty() && month != null && month.transactionCount == 0

/** «الكاش وحده X» جوه سطر «غير متاح» — بس لو رصيد الكاش نفسه معروف. */
fun cashOnlyLine(now: WithYouNow): String? =
    now.cashMinor?.let { t(TextKey.HOME_NA_CASH_ONLY, amountLabel(it, now.currency)) }
