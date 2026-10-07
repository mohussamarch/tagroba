package app.masroufy.core

/**
 * «الأحداث» (OVERRIDES §44 و§44.1 و§64) — فرح · خطوبة · عزا · ولادة · سفر · عملية · عيد · دخول مدرسة · غيره.
 * **الحدث كيان لوحده مش نوع مشروع** (نص المالك §64)، وبيتربط بيه عمليات بإيد المستخدم بدور واحد من تلاتة:
 * مصروف عليه · نقطة جاتلك · نقطة إنت اديتها.
 *
 * 🔒 **قاعدة المالك:** «متحسبش النقوط كأنها هتشيل جزء من المصروف … دي حجات مختلفة». ⇒ مفيش هنا أي «صافي» ولا
 * «كلّفك بعد النقوط» — المصروف رقم، والنقوط رقم تاني **للمعلومية بس** ومش بيتطرح من حاجة (§47 ما بيتطبقش على الحدث).
 * ومجموع النقوط اللي جاتلك بيظهر **بس لو الحدث بتاعك** (فرحك)؛ حدث حد تاني ⇒ اللي بيظهر اللي **إنت** نقّطته.
 */
enum class LifeEventKind(val wire: String, val labelKey: TextKey) {
    WEDDING("wedding", TextKey.EVENT_KIND_WEDDING),
    ENGAGEMENT("engagement", TextKey.EVENT_KIND_ENGAGEMENT),
    CONDOLENCE("condolence", TextKey.EVENT_KIND_CONDOLENCE),
    BIRTH("birth", TextKey.EVENT_KIND_BIRTH),
    TRAVEL("travel", TextKey.EVENT_KIND_TRAVEL),
    MEDICAL("medical", TextKey.EVENT_KIND_MEDICAL),
    EID("eid", TextKey.EVENT_KIND_EID),
    SCHOOL("school", TextKey.EVENT_KIND_SCHOOL),
    OTHER("other", TextKey.EVENT_KIND_OTHER),
    ;

    val label: String get() = uiText(labelKey)

    companion object {
        fun fromWire(wire: String): LifeEventKind = entries.first { it.wire == wire }
    }
}

/**
 * حدث. [mine] = الحدث بتاعك إنت (فرحك) ⇒ مجموع النقوط اللي جاتلك بيظهر، وتقدر تطلب تذكير سنوي بيه.
 * [hostPersonId] = صاحب الحدث لما يكون لحد تاني (فرح أخوك) — ولازم يبقى فاضي لو الحدث بتاعك.
 * مفيش مسح — أرشفة، زي المشروع.
 */
data class LifeEvent(
    val id: Id,
    val name: String,
    val normalizedName: String,
    val kind: LifeEventKind,
    val date: IsoDate,
    val mine: Boolean,
    val hostPersonId: Id? = null,
    val archived: Boolean = false,
    val createdAt: String,
)

enum class EventRole(val wire: String) {
    /** مصروف على الحدث (عملية طالعة). */
    SPEND("spend"),
    /** نقطة جاتلك من شخص (عملية داخلة نوعها «نقوط» `event_gift`). */
    GIFT_IN("gift_in"),
    /** نقطة إنت اديتها لشخص في حدثه (عملية طالعة نوعها «هدية أو مساعدة» `support_gift`). */
    GIFT_OUT("gift_out"),
    ;

    val isGift: Boolean get() = this != SPEND

    companion object {
        fun fromWire(wire: String): EventRole = entries.first { it.wire == wire }
    }
}

/**
 * ربط عملية بحدث. [personId] إجباري للنقوط (مين نقّط/مين نقّطته)، واختياري للمصروف.
 * [sharePercent] نصيب الحدث من العملية (1..100) — **للمصروف بس**؛ النقطة دايمًا العملية كلها (100).
 * رد المالك §64: «نحددها بالنسبة المئوية و يفضل التصنيفين دول ملازمين العملية دايما» ⇒ النسبة **بتتخزن على الربط
 * ومش بتتحسب من جديد أبدًا**، والربط القديم اللي مافيهوش الحقل = 100 (العملية كلها، زي ما كان).
 */
data class EventLink(
    val id: Id,
    val eventId: Id,
    val transactionId: Id,
    val role: EventRole,
    val personId: Id? = null,
    val createdAt: String,
    val sharePercent: Int = EVENT_SHARE_WHOLE,
    /**
     * بند التجهيز اللي المصروف ده اتصرف عليه (§65) — للمصروف بس، واختياري: الربط القديم من غيره = «مش على بند».
     * ما بيتكتبش لو null ⇒ مستند الربط القديم هو هو.
     */
    val prepItemId: Id? = null,
)

const val EVENT_NAME_MAX = 60

/** النسبة الكاملة = العملية كلها للحدث (الافتراضي، والقديم من غير الحقل). */
const val EVENT_SHARE_WHOLE = 100

/**
 * نصيب الحدث من العملية بالهللة: المبلغ × النسبة ÷ 100 **بأعداد صحيحة** وتقريب النص لفوق على الهللة (`rateOfMoney`) —
 * اختيار Claude (§64): 33% من 1.01 ⇒ 0.3333 ⇒ 0.33 · 50% من 0.01 ⇒ 0.005 ⇒ 0.01. 100 ⇒ المبلغ نفسه بالظبط.
 */
fun eventShareMinor(amountMinor: Halalas, sharePercent: Int): Halalas =
    if (sharePercent == EVENT_SHARE_WHOLE) amountMinor else rateOfMoney(amountMinor, sharePercent.toLong(), EVENT_SHARE_WHOLE.toLong())

/** النسبة عدد صحيح من 1 لـ100، والنقطة (جاتلك أو اديتها) العملية كلها بس. */
fun checkEventShare(role: EventRole, sharePercent: Int) {
    if (sharePercent !in 1..EVENT_SHARE_WHOLE) throw EventError(uiText(TextKey.EVENT_SHARE_RANGE))
    if (role.isGift && sharePercent != EVENT_SHARE_WHOLE) throw EventError(uiText(TextKey.EVENT_GIFT_SHARE_WHOLE))
}

class EventError(message: String) : IllegalArgumentException(message)

/**
 * معرّف ثابت **من العملية بس** ⇒ العملية الواحدة ليها ربط واحد بحدث واحد على مستوى التخزين نفسه
 * (والفحص [checkEventLink] بيرفض قبل الكتابة برسالة واضحة).
 */
fun eventLinkId(transactionId: Id): Id = "elink-$transactionId"

/** نفس فحص اسم المشروع: تنضيف المسافات · الطول · ومفيش حدثين بنفس الاسم بعد التطبيع. */
fun checkEventName(name: String, events: List<LifeEvent>, selfId: Id? = null): CheckedName {
    val clean = JsText.collapseWhitespace(JsText.trim(name))
    if (clean.isEmpty() || clean.length > EVENT_NAME_MAX) throw EventError(uiText(TextKey.EVENT_NAME_LENGTH, EVENT_NAME_MAX.toString()))
    val normalized = normalizeText(clean)
    if (events.any { it.id != selfId && it.normalizedName == normalized }) throw EventError(uiText(TextKey.EVENT_NAME_DUPLICATE))
    return CheckedName(clean, normalized)
}

/** التاريخ صالح، وصاحب الحدث بيتحدد للأحداث اللي لغيرك بس. */
fun checkEventFields(date: IsoDate, mine: Boolean, hostPersonId: Id?) {
    if (!isValidIsoDate(date)) throw EventError(uiText(TextKey.EVENT_DATE_INVALID))
    if (mine && hostPersonId != null) throw EventError(uiText(TextKey.EVENT_HOST_ONLY_OTHERS))
}

/**
 * قبل ربط عملية بحدث. [existing] = أي ربط موجود لنفس العملية (بأي حدث).
 * - العملية **ما تتربطش بحدثين** (ولا مرتين بنفس الحدث) — زي قاعدة `DueLinks`.
 * - المصروف والنقطة اللي اديتها ⇒ عملية طالعة · النقطة اللي جاتلك ⇒ عملية داخلة.
 * - النقطة (رايحة أو جاية) لازم ليها شخص.
 * - النقطة اللي جاتلك **في حدثك إنت بس** (اختيار Claude — §64: مجموعها بيظهر في حدثك بس، فتسجيلها في حدث حد تاني رقم مستخبي).
 */
fun checkEventLink(event: LifeEvent, role: EventRole, personId: Id?, txn: Transaction, existing: List<EventLink>, sharePercent: Int = EVENT_SHARE_WHOLE) {
    if (existing.any { it.transactionId == txn.id }) throw EventError(uiText(TextKey.EVENT_TXN_ALREADY_LINKED))
    checkEventShare(role, sharePercent)
    val needed = if (role == EventRole.GIFT_IN) Direction.IN else Direction.OUT
    if (txn.observedDirection != needed) throw EventError(uiText(if (needed == Direction.IN) TextKey.EVENT_TXN_NEEDS_IN else TextKey.EVENT_TXN_NEEDS_OUT))
    if (role.isGift && personId == null) throw EventError(uiText(TextKey.EVENT_GIFT_NEEDS_PERSON))
    if (role == EventRole.GIFT_IN && !event.mine) throw EventError(uiText(TextKey.EVENT_GIFT_IN_NOT_MINE))
}

/** مجاميع حدث بعملة واحدة — **مفيش جمع بين عملتين** (زي الدين في بلدين §64). */
data class EventCurrencyTotals(
    val currency: Currency,
    /** المصروف على الحدث (نصيبه من كل عملية حسب النسبة المتخزنة) — رقم لوحده، **عمره ما بيتنقّص منه النقوط**. */
    val spentMinor: Halalas,
    /** النقوط اللي جاتلك — **null لو الحدث مش بتاعك** (مش صفر — مش بيظهر خالص). */
    val giftsInMinor: Halalas?,
    /** النقوط اللي إنت اديتها في الحدث ده. */
    val giftsOutMinor: Halalas,
)

data class EventSummary(
    /** بترتيب العملة (اسمها). فاضية = مفيش عمليات. */
    val totals: List<EventCurrencyTotals>,
    val spendCount: Int,
    /** null لو الحدث مش بتاعك. */
    val giftInCount: Int?,
    val giftOutCount: Int,
)

/**
 * ملخص الحدث من روابطه وعملياتها. المصروف = نصيب الحدث من كل عملية بالنسبة **المتخزنة على الربط** ([eventShareMinor]) ·
 * النقطة = العملية كلها.
 * ⚠️ **مفيش دالة «صافي» ولا «كلفة بعد النقوط» — عن قصد** (قرار المالك §64).
 */
fun summarizeEvent(event: LifeEvent, links: List<EventLink>, transactions: List<Transaction>): EventSummary {
    val byId = transactions.associateBy { it.id }
    val mine = links.filter { it.eventId == event.id }.mapNotNull { l -> byId[l.transactionId]?.let { l to it } }
    val currencies = mine.map { it.second.currency }.distinct().sortedBy { it.name }
    val totals = currencies.map { c ->
        fun sum(role: EventRole) = sumMoney(mine.filter { it.first.role == role && it.second.currency == c }.map { (l, t) -> eventShareMinor(t.amountMinor, l.sharePercent) })
        EventCurrencyTotals(c, sum(EventRole.SPEND), if (event.mine) sum(EventRole.GIFT_IN) else null, sum(EventRole.GIFT_OUT))
    }
    fun count(role: EventRole) = mine.count { it.first.role == role }
    return EventSummary(totals, count(EventRole.SPEND), if (event.mine) count(EventRole.GIFT_IN) else null, count(EventRole.GIFT_OUT))
}

/**
 * «بادج» في بروفايل الشخص (§44.1): «نقّطك 2,000 في فرحك» / «نقّطته 1,000 في فرح أخوه».
 * **معلومة بس** — مفيش التزام في «المستحقات» ولا تذكير إجباري. [direction] IN = هو نقّطك · OUT = إنت نقّطته.
 */
data class GiftBadge(
    val eventId: Id,
    val eventName: String,
    val eventKind: LifeEventKind,
    val eventDate: IsoDate,
    val direction: Direction,
    val amountMinor: Halalas,
    val currency: Currency,
)

/** كل نقوط الشخص — بتتجمع لكل (حدث · اتجاه · عملة)، والأحدث الأول. */
fun personGiftBadges(personId: Id, events: List<LifeEvent>, links: List<EventLink>, transactions: List<Transaction>): List<GiftBadge> {
    val eventById = events.associateBy { it.id }
    val txnById = transactions.associateBy { it.id }
    val groups = LinkedHashMap<String, GiftBadge>()
    for (l in links) {
        if (l.personId != personId || !l.role.isGift) continue
        val event = eventById[l.eventId] ?: continue
        val txn = txnById[l.transactionId] ?: continue
        val direction = if (l.role == EventRole.GIFT_IN) Direction.IN else Direction.OUT
        val key = "${event.id}|${direction.wire}|${txn.currency.name}"
        val prev = groups[key]
        groups[key] = prev?.copy(amountMinor = addMoney(prev.amountMinor, txn.amountMinor))
            ?: GiftBadge(event.id, event.name, event.kind, event.date, direction, txn.amountMinor, txn.currency)
    }
    return groups.values.sortedWith(compareByDescending<GiftBadge> { it.eventDate }.thenBy { it.eventName }.thenBy { it.direction.wire })
}

fun giftBadgeText(badge: GiftBadge): String = uiText(
    if (badge.direction == Direction.IN) TextKey.EVENT_BADGE_IN else TextKey.EVENT_BADGE_OUT,
    formatMoney(badge.amountMinor, badge.currency),
    badge.eventName,
)

/**
 * تصنيف «نقوط» جوه أساسي «هدايا» — رد المالك §64: «ما تتصنف ك نقوط عادي ايه المشكلة ؟ وتكون جوا قسم هدايا».
 * نفس آلية `ZakatCategory` و`DuesCategories`: المعرّفات ثابتة عشان الجهازين يلاقوا نفس التصنيف، وبيتعملوا **لو مش موجودين بس**
 * (اللي المستخدم غيّره ما يتكتبش فوقه). الاسم بلغة الواجهة وقت الإنشاء — زي بذور الحساب (§40: بتتخزن في بيانات المستخدم).
 * «هدايا» أساسي في مجموعة «الحياة الشخصية» (§28.1) — المجموعة للعرض بس، والمصروف/الدخل من نوع العملية.
 * ⚠️ الرمز والألوان مؤقتة لحد تصميم المالك (§55).
 */
object GiftCategories {
    const val ROOT = "cat-gifts"
    const val EVENT_GIFTS = "cat-gifts-nuqoot"

    /** بالترتيب: الأب الأول (عشان الفرع ما يتحفظش قبل أبوه). */
    fun defaults(): List<Category> = listOf(
        Category(ROOT, null, uiText(TextKey.CATEGORY_GIFTS), "gift", "#9b3d6b", "#e8a3c4", true, 920, groupKey = "personal"),
        Category(EVENT_GIFTS, ROOT, uiText(TextKey.CATEGORY_EVENT_GIFTS), "hand-coins", "#9b3d6b", "#e8a3c4", true, 921),
    )
}
