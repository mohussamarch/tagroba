package app.masroufy.usecase

import app.masroufy.core.CalendarItem
import app.masroufy.core.CalendarItemType
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.LeftoverProjection
import app.masroufy.core.Reservation
import app.masroufy.core.ReservationError
import app.masroufy.core.TextKey
import app.masroufy.core.countedAmount
import app.masroufy.core.nextPaydayAfter
import app.masroufy.core.projectLeftover
import app.masroufy.core.reservationId
import app.masroufy.core.reservationIdOf
import app.masroufy.core.uiText
import app.masroufy.core.walletBalancesOn
import app.masroufy.port.Clock
import app.masroufy.port.ProfileRepository
import app.masroufy.port.ReservationRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * «احسبه من فلوسي» (توضيح المالك §65): من قسم الميزانيات، **بضغطة واحدة**، الميعاد الجاي بيتحسب من الفلوس اللي معاك دلوقتي.
 * المبلغ المبدئي = مبلغ الميعاد المعروف؛ الميعاد اللي مالوش مبلغ المستخدم **لازم يكتبه**. حجز واحد لكل مرة من الميعاد،
 * والحساب التاني **بيستبدل** الأول. 🔒 علامة تخطيط بس — مفيش رصيد ولا ميزانية ولا مجموع شهر بيقراه.
 */
class ManageReservations(private val calendar: LoadCalendar, private val reservations: ReservationRepository, private val clock: Clock) {
    private suspend fun find(type: CalendarItemType, sourceId: Id, date: IsoDate, today: IsoDate): CalendarItem =
        calendar.items(date, date, today).firstOrNull { it.type == type && it.sourceId == sourceId }
            ?: throw ReservationError(uiText(TextKey.RESERVATION_ITEM_NOT_FOUND))

    /**
     * الميعاد لازم يكون في التقويم فعلًا (مش اتدفع ولا اتشال) وجاي، ومش فلوس جاية ليك. [amountMinor] null ⇒ مبلغ الميعاد.
     * العملة = عملة السطر.
     */
    suspend fun countUpcomingItem(type: CalendarItemType, sourceId: Id, date: IsoDate, today: IsoDate, amountMinor: Halalas? = null): Reservation {
        val item = find(type, sourceId, date, today)
        val amount = countedAmount(item, amountMinor, today)
        val id = reservationIdOf(item)
        val existing = reservations.listAll().firstOrNull { it.id == id }
        val saved = Reservation(id, type, sourceId, date, amount, item.currency, existing?.createdAt ?: clock.nowIso())
        reservations.save(saved)
        return saved
    }

    /** «شيله من الحساب». */
    suspend fun uncount(type: CalendarItemType, sourceId: Id, date: IsoDate) {
        val id = reservationId(type, sourceId, date)
        if (reservations.listAll().none { it.id == id }) throw ReservationError(uiText(TextKey.RESERVATION_NOT_FOUND))
        reservations.remove(id)
    }
}

data class LoadLeftoverDeps(
    val calendar: LoadCalendar,
    val wallets: WalletRepository,
    val txns: TransactionRepository,
    val reservations: ReservationRepository,
    val profile: ProfileRepository,
    /** عملة المساحة — المحافظ والمحسوب بعملة تانية ما بيدخلوش. */
    val currency: Currency,
)

/**
 * «فاضلك تقريبًا» في قسم الميزانيات: أرصدة المحافظ النهارده (نفس `walletBalancesOn` بتاع الزكاة) − المحسوب الجاي.
 * [salaried] من برا: مصادر الدخل لسه مالهاش مستودع في الفرع ده — لما تتوصل ⇒ `isSalaried(sources, today)`.
 * [unreconciledWalletIds] محافظ رصيدها مش متطابق مع الكشف (من المطابقة) ⇒ الرقم بيتعلّم تقريبي.
 */
class LoadLeftover(private val deps: LoadLeftoverDeps) {
    suspend fun load(today: IsoDate, salaried: Boolean, unreconciledWalletIds: Set<Id> = emptySet()): LeftoverProjection {
        val wallets = deps.wallets.listAll().filter { it.currency == deps.currency }
        val from = wallets.minOfOrNull { it.openingAt }
        val txns = if (from == null || from > today) emptyList() else deps.txns.listByDateRange(from, today).filter { it.currency == deps.currency }
        val balances = walletBalancesOn(wallets, txns, today).values.toList()
        val next = deps.profile.load()?.payday?.let { nextPaydayAfter(today, it) }
        val until = (listOfNotNull(today, next) + deps.reservations.listAll().map { it.occurrenceDate }).max()
        val items = deps.calendar.items(today, until, today)
        return projectLeftover(balances, items, today, deps.currency, salaried, next, wallets.any { it.id in unreconciledWalletIds })
    }
}
