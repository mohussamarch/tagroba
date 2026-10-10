package app.masroufy.ui.screens.people

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.Language
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Texts
import app.masroufy.core.Transaction
import app.masroufy.core.eventLinkId
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.ManageEvents
import app.masroufy.usecase.ManageEventsDeps

/** بيانات مخترعة لاختبارات منطقة «الأشخاص» (المستودع عام — مفيش بيانات حقيقية). النهارده ثابت. */
internal const val TODAY = "2026-10-09"
internal val testClock = FixedClock("2026-10-09T10:00:00.000Z")

/** كل اختبار بيبدأ بالفصحى (السعودية) وبيرجّعها — زي `ArabicVariantsTest`. */
internal fun resetTexts() {
    Texts.language = Language.AR
    Texts.arabicVariant = ArabicVariant.MSA
}

internal fun egyptian() {
    Texts.language = Language.AR
    Texts.arabicVariant = ArabicVariant.EGYPTIAN
}

internal fun english() {
    Texts.language = Language.EN
}

internal fun txn(
    id: String,
    amount: Long,
    direction: Direction = Direction.OUT,
    date: String = "2026-10-01",
    merchant: String? = "متجر وهمي",
    currency: Currency = Currency.SAR,
    kind: EconomicKind = if (direction == Direction.OUT) EconomicKind.PURCHASE else EconomicKind.SALARY,
    review: ReviewState = ReviewState.CONFIRMED,
    wallet: String? = "w-1",
    order: Int = 1,
    kindConfirmed: Boolean = true,
) = Transaction(
    id = id, occurredAt = date, datePrecision = "day", sourceOrder = order, economicKind = kind, economicKindConfirmed = kindConfirmed,
    observedDirection = direction, amountMinor = amount, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
    reviewState = review, isCashTagged = false, createdAt = "c", updatedAt = "c", walletId = wallet, rawMerchantName = merchant,
)

internal fun event(
    id: String,
    name: String,
    date: String,
    kind: LifeEventKind = LifeEventKind.WEDDING,
    mine: Boolean = true,
    host: String? = null,
    archived: Boolean = false,
) = LifeEvent(id, name, name, kind, date, mine, host, archived, "c")

internal fun link(eventId: String, txnId: String, role: EventRole = EventRole.SPEND, person: String? = null, share: Int = 100) =
    EventLink(eventLinkId(txnId), eventId, txnId, role, person, "c", share)

/** «الأحداث» بمستودعات الذاكرة — نفس اعتمادات `PeopleGraph`. */
internal class EventsWorld(
    events: List<LifeEvent>,
    links: List<EventLink> = emptyList(),
    txns: List<Transaction> = emptyList(),
    people: List<Person> = emptyList(),
) {
    val eventsRepo = MemoryLifeEventRepository(events)
    val linksRepo = MemoryEventLinkRepository(links)
    val txnsRepo = MemoryTransactionRepository(txns)
    val peopleRepo = MemoryPersonRepository(people)
    val events = ManageEvents(ManageEventsDeps(eventsRepo, linksRepo, txnsRepo, peopleRepo, SequentialIdGenerator(), testClock))
}
