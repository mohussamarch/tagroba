package app.masroufy.core

/**
 * تجهيزات الحدث الجاي (OVERRIDES §65): **كل حدث جاي ما عدا العزا** بيتفتح له تجهيزات — بيفضل **حدث** (§64) بس بيتجهز زي المشروع:
 * بنود، والمصروف المربوط بالحدث يقدر يشاور على بنده (`EventLink.prepItemId`) فيتشال منه.
 * البنود المقترحة **من غير مبالغ** (رد المالك: «بنود من غير مبالغ» — القاعدة 10): المستخدم هو اللي بيحط المبلغ.
 */
data class PrepItem(
    val id: Id,
    val eventId: Id,
    val name: String,
    /** null = المستخدم لسه ما حطش مبلغ — **مش صفر**. */
    val plannedMinor: Halalas?,
    val order: Int,
    val done: Boolean,
    val createdAt: String,
)

const val PREP_NAME_MAX = 60

class PrepError(message: String) : IllegalArgumentException(message)

/** العزا مالوش تجهيزات (§65). */
fun prepAllowed(event: LifeEvent): Boolean = event.kind != LifeEventKind.CONDOLENCE

/** الحدث الجاي (النهارده أو بعده) غير المؤرشف وغير العزا ⇒ بيتفتح له تجهيزات. */
fun eventNeedsPrep(event: LifeEvent, today: IsoDate): Boolean = prepAllowed(event) && !event.archived && event.date >= today

/** البنود المقترحة لكل نوع — أسماء بس (اختيار Claude — المالك يزود أو يشيل). العزا مفيش. */
fun prepSuggestionKeys(kind: LifeEventKind): List<TextKey> = when (kind) {
    LifeEventKind.WEDDING -> listOf(TextKey.PREP_HALL, TextKey.PREP_OUTFIT, TextKey.PREP_CATERING, TextKey.PREP_PHOTOGRAPHY, TextKey.PREP_INVITATIONS, TextKey.PREP_SALON, TextKey.PREP_HONEYMOON)
    LifeEventKind.ENGAGEMENT -> listOf(TextKey.PREP_ENGAGEMENT_GOLD, TextKey.PREP_VENUE, TextKey.PREP_CATERING, TextKey.PREP_OUTFIT, TextKey.PREP_PHOTOGRAPHY)
    LifeEventKind.BIRTH -> listOf(TextKey.PREP_HOSPITAL, TextKey.PREP_BABY_SUPPLIES, TextKey.PREP_SEBOU, TextKey.PREP_GUESTS)
    LifeEventKind.TRAVEL -> listOf(TextKey.PREP_TICKETS, TextKey.PREP_LODGING, TextKey.PREP_VISA, TextKey.PREP_LOCAL_TRANSPORT, TextKey.PREP_FOOD, TextKey.PREP_GIFTS_TO_BRING)
    LifeEventKind.MEDICAL -> listOf(TextKey.PREP_SURGERY, TextKey.PREP_TESTS, TextKey.PREP_MEDICINE, TextKey.PREP_FOLLOW_UP, TextKey.PREP_TRANSPORT)
    LifeEventKind.EID -> listOf(TextKey.PREP_EID_CLOTHES, TextKey.PREP_EIDIYA, TextKey.PREP_SWEETS, TextKey.PREP_OUTINGS)
    LifeEventKind.SCHOOL -> listOf(TextKey.PREP_TUITION, TextKey.PREP_BOOKS, TextKey.PREP_UNIFORM, TextKey.PREP_TRANSPORT, TextKey.PREP_CELEBRATION)
    LifeEventKind.OTHER -> listOf(TextKey.PREP_VENUE, TextKey.PREP_CATERING, TextKey.PREP_TRANSPORT)
    LifeEventKind.CONDOLENCE -> emptyList()
}

/**
 * الاقتراح بيترجع **ومش بيتحفظ** (اختيار Claude — المالك يقدر يغيّره): حدث جاي من غير ولا بند ⇒ أسامي حسب نوعه باللغة الحالية؛
 * المستخدم بيختار منها وبتتحفظ **من غير مبالغ**. عنده بنود ⇒ مفيش اقتراح (ما بنزاحمش اللي هو كتبه).
 */
fun prepSuggestions(event: LifeEvent, existing: List<PrepItem>, today: IsoDate): List<String> {
    if (!eventNeedsPrep(event, today) || existing.any { it.eventId == event.id }) return emptyList()
    return prepSuggestionKeys(event.kind).map { uiText(it) }
}

/** الاسم: تنضيف المسافات · الطول · ومفيش بندين بنفس الاسم في نفس الحدث بعد التطبيع. */
fun checkPrepName(name: String, siblings: List<PrepItem>, selfId: Id? = null): String {
    val clean = JsText.collapseWhitespace(JsText.trim(name))
    if (clean.isEmpty() || clean.length > PREP_NAME_MAX) throw PrepError(uiText(TextKey.PREP_NAME_LENGTH, PREP_NAME_MAX.toString()))
    val normalized = normalizeText(clean)
    if (siblings.any { it.id != selfId && normalizeText(it.name) == normalized }) throw PrepError(uiText(TextKey.PREP_NAME_DUPLICATE))
    return clean
}

/** المبلغ اختياري، ولو اتكتب يبقى أكبر من صفر. */
fun checkPrepPlanned(plannedMinor: Halalas?) {
    if (plannedMinor != null && (plannedMinor <= 0 || plannedMinor > MAX_SAFE_HALALAS)) throw PrepError(uiText(TextKey.PREP_PLANNED_POSITIVE))
}

/** ربط مصروف ببند: المصروف بس (النقطة مش تجهيز)، والبند من نفس الحدث. */
fun checkPrepAssignment(link: EventLink, item: PrepItem?) {
    if (link.role != EventRole.SPEND) throw PrepError(uiText(TextKey.PREP_LINK_SPEND_ONLY))
    if (item != null && item.eventId != link.eventId) throw PrepError(uiText(TextKey.PREP_ITEM_OTHER_EVENT))
}

data class PrepItemStatus(val item: PrepItem, val spentMinor: Halalas)

data class PrepSummary(
    /** بترتيب البند. */
    val items: List<PrepItemStatus>,
    /** مجموع البنود اللي ليها مبلغ — **null لو ولا بند ليه مبلغ** («غير متاح» مش صفر). */
    val plannedTotalMinor: Halalas?,
    /** بنود من غير مبلغ (المجموع فوق ناقصها). */
    val unpricedCount: Int,
    /** كل المصروف على الحدث بعملة المساحة (نصيبه بالنسبة المتخزنة) — نفس رقم «صرفت» في ملخص الحدث. */
    val spentMinor: Halalas,
    /** مصروف على الحدث مش متربط ببند. */
    val unassignedSpentMinor: Halalas,
    /** روابط مصروف بعملة تانية — ما بتتجمعش ولا بتتحوّل. */
    val otherCurrencyCount: Int,
    /** البنود اللي لسه ما خلصتش. */
    val remainingCount: Int,
)

/**
 * ملخص التجهيزات بعملة [currency]: مصروف كل بند = مجموع **نصيب الحدث** من كل عملية مربوطة بيه (`eventShareMinor` بالنسبة
 * المتخزنة على الربط — مش العملية كلها). المصروف اللي مش متربط ببند (أو بنده اتشال) بيتعد لوحده.
 */
fun summarizePrep(event: LifeEvent, currency: Currency, items: List<PrepItem>, links: List<EventLink>, transactions: List<Transaction>): PrepSummary {
    val mine = items.filter { it.eventId == event.id }.sortedWith(compareBy({ it.order }, { it.createdAt }, { it.id }))
    val byTxn = transactions.associateBy { it.id }
    val spends = links.filter { it.eventId == event.id && it.role == EventRole.SPEND }.mapNotNull { l -> byTxn[l.transactionId]?.let { l to it } }
    val local = spends.filter { it.second.currency == currency }
    fun share(pairs: List<Pair<EventLink, Transaction>>) = sumMoney(pairs.map { (l, t) -> eventShareMinor(t.amountMinor, l.sharePercent) })
    val known = mine.map { it.id }.toSet()
    val statuses = mine.map { item -> PrepItemStatus(item, share(local.filter { it.first.prepItemId == item.id })) }
    val priced = mine.mapNotNull { it.plannedMinor }
    return PrepSummary(
        items = statuses,
        plannedTotalMinor = if (priced.isEmpty()) null else sumMoney(priced),
        unpricedCount = mine.count { it.plannedMinor == null },
        spentMinor = share(local),
        unassignedSpentMinor = share(local.filter { it.first.prepItemId == null || it.first.prepItemId !in known }),
        otherCurrencyCount = spends.size - local.size,
        remainingCount = mine.count { !it.done },
    )
}

/**
 * مبلغ الحدث في التقويم من تجهيزاته: **بس لو كل البنود ليها مبلغ** (اختيار Claude) — مجموع ناقص كان هيبان كأنه الكلفة كلها.
 * مفيش بنود أو فيه بند من غير مبلغ ⇒ null (السطر «من غير مبلغ»).
 */
fun eventCalendarAmount(items: List<PrepItem>): Halalas? {
    if (items.isEmpty() || items.any { it.plannedMinor == null }) return null
    return sumMoney(items.map { it.plannedMinor!! })
}
