package app.masroufy.usecase

import app.masroufy.core.CalendarItem
import app.masroufy.core.CalendarSources
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.SmartSummary
import app.masroufy.core.ZakatYear
import app.masroufy.core.buildCalendar
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.daysInMonth
import app.masroufy.core.eventCalendarAmount
import app.masroufy.core.nextPaydayAfter
import app.masroufy.core.parseIsoDate
import app.masroufy.core.periodForDate
import app.masroufy.core.publicOccasions
import app.masroufy.core.smartSummary
import app.masroufy.core.toDayNumber
import app.masroufy.core.zakatVisible
import app.masroufy.core.zakatYearStatus
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.LifeEventRepository
import app.masroufy.port.OccasionRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.PrepItemRepository
import app.masroufy.port.ProfileRepository
import app.masroufy.port.ProjectRepository
import app.masroufy.port.ReservationRepository
import app.masroufy.port.ZakatPaymentRepository
import app.masroufy.port.ZakatYearRepository

/**
 * صفحة التقويم (OVERRIDES §65): الشهر بمواعيده، و«الجاي بالترتيب» (فاضل كام يوم · المبلغ · محجوز ولا لأ)، والملخص الذكي.
 * **محسوب كل مرة** من الكيانات (المستحقات من `LoadDues.dueItems` — نفس بُناة `DuesAgenda`)؛ المتخزن الحجز بس.
 */
data class LoadCalendarDeps(
    val dues: LoadDues,
    val projects: ProjectRepository,
    val events: LifeEventRepository,
    val prep: PrepItemRepository,
    val occasions: OccasionRepository,
    val people: PersonRepository,
    val profile: ProfileRepository,
    val reservations: ReservationRepository,
    /** بلد المساحة (§41) — للمناسبات العامة. */
    val countryCode: String?,
    /** عملة المساحة — للسطور اللي مالهاش عملة من نفسها. */
    val currency: Currency,
    /** null = الزكاة مش متوصلة. */
    val zakatYears: ZakatYearRepository? = null,
    val zakatPayments: ZakatPaymentRepository? = null,
    /** مصادر الدخل في المساحة — أيام قبض البارت تايم والمعاش والإيجار والوظيفة الأسبوعي (رد المالك §64-٣). */
    val incomeSources: IncomeSourceRepository,
)

data class CalendarMonth(val year: Int, val month: Int, val items: List<CalendarItem>, val summary: SmartSummary)

class LoadCalendar(private val deps: LoadCalendarDeps) {
    /** كل السطور من [from] لحد [to] (شاملين) بالترتيب، وعلى كل سطر حجزه. */
    suspend fun items(from: IsoDate, to: IsoDate, today: IsoDate): List<CalendarItem> {
        val profile = deps.profile.load()
        val islamic = zakatVisible(profile)
        val prepByEvent = deps.prep.listAll().groupBy { it.eventId }
        val amounts = LinkedHashMap<Id, Halalas>()
        for ((eventId, items) in prepByEvent) eventCalendarAmount(items)?.let { amounts[eventId] = it }
        val sources = CalendarSources(
            dues = deps.dues.dueItems(today, to),
            projects = deps.projects.listAll(),
            events = deps.events.listAll(),
            eventPlannedMinor = amounts,
            occasions = deps.occasions.listAll(),
            personNames = deps.people.listAll().filter { !it.archived }.associate { it.id to it.name },
            payday = profile?.payday,
            zakat = if (islamic) zakatYears() else emptyList(),
            publicOccasions = if (islamic) publicOccasions(from, to, deps.countryCode) else emptyList(),
            islamicVisible = islamic,
            incomeSources = deps.incomeSources.listAll(),
        )
        return buildCalendar(from, to, today, deps.currency, sources, deps.reservations.listAll())
    }

    private suspend fun zakatYears(): List<Pair<ZakatYear, Halalas?>> {
        val years = deps.zakatYears ?: return emptyList()
        return years.listAll().map { y -> y to if (y.closed) zakatYearStatus(y, deps.zakatPayments?.listByYear(y.id).orEmpty()).remainingMinor else null }
    }

    /**
     * الملخص الذكي: بيقرا من أول الشهر المالي لحد الأبعد من (المرتب الجاي · آخر الشهر المالي · 30 يوم قدام).
     * مفيش ملف حساب ⇒ الشهر المالي بيوم 28 للأسبوع الأتقل بس، ومفيش «لحد المرتب».
     */
    suspend fun summary(today: IsoDate): SmartSummary {
        val payday = deps.profile.load()?.payday
        val period = periodForDate(today, payday ?: DEFAULT_PAYDAY)
        val ahead = dayNumberToIso(toDayNumber(parseIsoDate(today)) + 30)
        val until = listOfNotNull(period.end, ahead, payday?.let { nextPaydayAfter(today, it) }).max()
        val from = minOf(period.start, today)
        return smartSummary(items(from, until, today), today, payday, period, deps.currency)
    }

    /** شهر ميلادي كامل للتقويم + الملخص. */
    suspend fun month(year: Int, month: Int, today: IsoDate): CalendarMonth {
        val first = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-01"
        val last = first.substring(0, 8) + daysInMonth(year, month).toString().padStart(2, '0')
        return CalendarMonth(year, month, items(first, last, today), summary(today))
    }
}
