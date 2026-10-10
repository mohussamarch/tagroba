package app.masroufy.ui.screens.people

import app.masroufy.core.BalanceDirection
import app.masroufy.core.Currency
import app.masroufy.core.DueItem
import app.masroufy.core.DueSource
import app.masroufy.core.DueStatus
import app.masroufy.core.Direction
import app.masroufy.core.GiftBadge
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.ObligationKind
import app.masroufy.core.PersonOverviewRow
import app.masroufy.core.PersonProfile
import app.masroufy.core.TextKey
import app.masroufy.core.circleOf
import app.masroufy.core.daysBetween
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.PersonRow
import app.masroufy.usecase.UpcomingOccasion

/**
 * ملف الشخص (لوحة `PersonProfile`) من نتايج حالات الاستخدام: `ManagePeople.listWithBalances` (الالتزامات المفتوحة بالمتبقي) ·
 * `LoadPeopleOverview.forSpace` (أرصدته لكل عملة) · `ManagePersonCircles.profileOf` · `ManageOccasions.forPerson` · `ManageEvents.personBadges`.
 * ترتيب وكلام بس — المبالغ جاية جاهزة.
 */
internal data class HistoryRow(
    val obligationId: Id,
    val title: String,
    val sub: String,
    val amountMinor: Halalas,
    val currency: Currency,
    val tone: AmountTone,
)

internal data class PersonPageUi(
    val id: Id,
    val name: String,
    val circleLine: String,
    val relLine: String?,
    val archived: Boolean,
    /** null = الأرصدة ما اتحمّلتش (غير متاح) · فاضية = لا شيء. */
    val owed: List<MoneyLine>?,
    val owe: List<MoneyLine>?,
    val overdue: String?,
    val occasions: List<UpcomingOccasion>,
    val history: List<HistoryRow>,
    val badges: List<String>,
)

internal fun personPageUi(
    row: PersonRow,
    overview: PersonOverviewRow?,
    overviewLoaded: Boolean,
    profile: PersonProfile?,
    occasions: List<UpcomingOccasion>,
    badges: List<GiftBadge>,
    dues: List<DueItem>,
    today: IsoDate,
): PersonPageUi {
    val p = row.person
    fun lines(d: BalanceDirection) = if (!overviewLoaded) null else overview?.balances.orEmpty().filter { it.direction == d }.map { MoneyLine(it.amountMinor, it.currency) }
    val receivables = row.obligations.filter { it.obligation.kind == ObligationKind.RECEIVABLE }.map { it.obligation.id }.toSet()
    val late = dues.filter { it.source == DueSource.DEBT && it.sourceId in receivables && it.status == DueStatus.OVERDUE }.minByOrNull { it.dueAt }
    val history = row.obligations.map { o ->
        val ob = o.obligation
        val title = when (ob.kind) {
            ObligationKind.RECEIVABLE -> t(TextKey.PERSON_PAGE_DEBT_RECEIVABLE)
            ObligationKind.LOAN_PAYABLE -> t(TextKey.PERSON_PAGE_DEBT_LOAN)
            ObligationKind.CUSTODY_PAYABLE -> t(TextKey.PERSON_PAGE_DEBT_CUSTODY)
        }
        val remaining = t(TextKey.PERSON_PAGE_REMAINING, amountLabel(o.remainingMinor, ob.currency), amountLabel(ob.originalMinor, ob.currency))
        val sub = if (ob.originTransactionId == null) joinLine(t(TextKey.OWED_REASON_OPENING), remaining) else remaining
        // سلفة منك = فلوس طلعت منك (−) · سلفة أو أمانة منه = فلوس دخلتلك (+)
        val tone = if (ob.kind == ObligationKind.RECEIVABLE) AmountTone.EXPENSE else AmountTone.INCOME
        HistoryRow(ob.id, title, sub, ob.originalMinor, ob.currency, tone)
    }
    val badgeLines = badges.map { b ->
        val key = if (b.direction == Direction.IN) TextKey.EVENT_BADGE_IN else TextKey.EVENT_BADGE_OUT
        t(key, amountLabel(b.amountMinor, b.currency), b.eventName)
    }
    return PersonPageUi(
        id = p.id,
        name = p.name,
        circleLine = circleOf(profile).label,
        relLine = profile?.relationLabel,
        archived = p.archived,
        owed = lines(BalanceDirection.OWED_TO_YOU),
        owe = lines(BalanceDirection.YOU_OWE),
        overdue = late?.let { t(TextKey.OWED_SIGNAL_OVERDUE, countOf(-daysBetween(today, it.dueAt), Noun.DAYS)) },
        occasions = occasions,
        history = history,
        badges = badgeLines,
    )
}
