package app.masroufy.core

/**
 * مناسبات الشخص (OVERRIDES §44.1 و§64) — عيد ميلاد · عيد جواز · فرح · غيره، **ميلادي بس** (اختيار المالك).
 * المناسبة بتاعة شخص ([Occasion.personId]) أو بتاعتك إنت (`null`) — ومنها تذكير حدثك السنوي (فرحك) اللي إنت
 * بتحدد مدته ([Occasion.sourceEventId] + [Occasion.leadDays]).
 * إمتى التذكير يتبعت وبأنهي قناة = محرك التنبيهات (§61)؛ هنا «إمتى المناسبة الجاية» بس.
 */
enum class OccasionKind(val wire: String, val labelKey: TextKey) {
    BIRTHDAY("birthday", TextKey.OCCASION_KIND_BIRTHDAY),
    WEDDING_ANNIVERSARY("wedding_anniversary", TextKey.OCCASION_KIND_WEDDING_ANNIVERSARY),
    WEDDING("wedding", TextKey.OCCASION_KIND_WEDDING),
    OTHER("other", TextKey.OCCASION_KIND_OTHER),
    ;

    val label: String get() = uiText(labelKey)

    companion object {
        fun fromWire(wire: String): OccasionKind = entries.first { it.wire == wire }
    }
}

/**
 * مناسبة. [year] اختياري للسنوية (خصوصية — سنة الميلاد مش لازم) وإجباري للي مرة واحدة.
 * [leadDays] = قبلها بكام يوم ينبّه (1–[OCCASION_LEAD_MAX])؛ null ⇒ [OCCASION_DEFAULT_SOON_DAYS].
 */
data class Occasion(
    val id: Id,
    val personId: Id?,
    val kind: OccasionKind,
    val label: String? = null,
    val month: Int,
    val day: Int,
    val year: Int? = null,
    val yearly: Boolean,
    val leadDays: Int? = null,
    val sourceEventId: Id? = null,
    val createdAt: String,
)

const val OCCASION_LEAD_MAX = 60
const val OCCASION_LABEL_MAX = 60
const val OCCASION_YEAR_MIN = 1900
const val OCCASION_YEAR_MAX = 2200

/** «قرّب» من غير مدة محددة = أسبوع (اختيار Claude — §64، المالك يقدر يغيّره). */
const val OCCASION_DEFAULT_SOON_DAYS = 7

class OccasionError(message: String) : IllegalArgumentException(message)

/**
 * فحص المناسبة، وبترجع نسخة نضيفة (الاسم من غير مسافات زيادة، والفاضي null).
 * 29 فبراير مسموح للسنوية أو من غير سنة؛ مع سنة لازم السنة كبيسة.
 */
fun checkOccasion(o: Occasion): Occasion {
    if (o.month !in 1..12) throw OccasionError(uiText(TextKey.OCCASION_DATE_INVALID))
    if (o.year != null && o.year !in OCCASION_YEAR_MIN..OCCASION_YEAR_MAX) throw OccasionError(uiText(TextKey.OCCASION_DATE_INVALID))
    // من غير سنة: أطول شهر ممكن (فبراير 29)
    val maxDay = daysInMonth(o.year ?: 2000, o.month)
    if (o.day !in 1..maxDay) throw OccasionError(uiText(TextKey.OCCASION_DATE_INVALID))
    if (!o.yearly && o.year == null) throw OccasionError(uiText(TextKey.OCCASION_YEAR_REQUIRED))
    if (o.leadDays != null && o.leadDays !in 1..OCCASION_LEAD_MAX) throw OccasionError(uiText(TextKey.OCCASION_LEAD_RANGE, OCCASION_LEAD_MAX.toString()))
    val label = o.label?.let { JsText.collapseWhitespace(JsText.trim(it)) }?.takeIf { it.isNotEmpty() }
    if (label != null && label.length > OCCASION_LABEL_MAX) throw OccasionError(uiText(TextKey.OCCASION_LABEL_LENGTH, OCCASION_LABEL_MAX.toString()))
    return o.copy(label = label)
}

private fun isoOf(year: Int, month: Int, day: Int): IsoDate =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

/** يوم المناسبة في سنة معيّنة — 29 فبراير ⇒ 28 في السنين العادية. */
fun occasionDateIn(o: Occasion, year: Int): IsoDate = isoOf(year, o.month, minOf(o.day, daysInMonth(year, o.month)))

/**
 * المرة الجاية (النهارده محسوب). السنوية: السنة دي لو لسه ما عدّتش، وإلا السنة الجاية — ومش قبل سنتها لو متسجلة
 * (فرح لسه جاي ما يتعدّش له ذكرى قبله). اللي مرة واحدة: يومها لو النهارده أو بعده، وإلا null.
 */
fun nextOccurrence(o: Occasion, today: IsoDate): IsoDate? {
    if (!o.yearly) {
        val date = isoOf(o.year ?: return null, o.month, o.day)
        return if (date >= today) date else null
    }
    val thisYear = parseIsoDate(today).year
    val start = maxOf(thisYear, o.year ?: thisYear)
    val candidate = occasionDateIn(o, start)
    return if (candidate >= today) candidate else occasionDateIn(o, start + 1)
}

fun occasionSoonDays(o: Occasion): Int = o.leadDays ?: OCCASION_DEFAULT_SOON_DAYS

/** معرّف ثابت لتذكير حدثك ⇒ طلب التذكير تاني بيعدّل نفس المناسبة (مش مناسبة جديدة). */
fun ownEventOccasionId(eventId: Id): Id = "occ-event-$eventId"

/**
 * تذكير سنوي بحدثك (§64: «لو فرحه ممكن يختار ان يتعمله تنبيه قبل مدة معينة انه يجيب هدية لمراته او احتفال»).
 * الفرح ⇒ «عيد جواز»، والباقي «غيره» باسم الحدث. المدة إجبارية هنا (المستخدم هو اللي بيحددها).
 */
fun ownEventOccasion(event: LifeEvent, leadDays: Int, now: String, existing: Occasion? = null): Occasion {
    if (!event.mine) throw OccasionError(uiText(TextKey.OCCASION_EVENT_NOT_MINE))
    val d = parseIsoDate(event.date)
    return checkOccasion(
        Occasion(
            id = ownEventOccasionId(event.id),
            personId = null,
            kind = if (event.kind == LifeEventKind.WEDDING) OccasionKind.WEDDING_ANNIVERSARY else OccasionKind.OTHER,
            month = d.month, day = d.day, year = d.year, yearly = true, leadDays = leadDays,
            sourceEventId = event.id, createdAt = existing?.createdAt ?: now,
        ),
    )
}

/**
 * «إيه المناسبة» للعرض جوه التطبيق: الاسم اللي المستخدم كتبه · أو اسم الحدث لتذكير حدثك · أو النوع + اسم الشخص.
 * [personName] null = مناسبتك إنت.
 */
fun occasionTitle(o: Occasion, personName: String?, eventName: String?): String {
    if (o.label != null) return if (personName != null) uiText(TextKey.OCCASION_TITLE_LABEL_OF, o.label, personName) else o.label
    if (eventName != null && personName == null) return uiText(TextKey.OCCASION_TITLE_FROM_EVENT, eventName)
    val (of, own) = when (o.kind) {
        OccasionKind.BIRTHDAY -> TextKey.OCCASION_TITLE_BIRTHDAY_OF to TextKey.OCCASION_TITLE_BIRTHDAY_OWN
        OccasionKind.WEDDING_ANNIVERSARY -> TextKey.OCCASION_TITLE_ANNIVERSARY_OF to TextKey.OCCASION_TITLE_ANNIVERSARY_OWN
        OccasionKind.WEDDING -> TextKey.OCCASION_TITLE_WEDDING_OF to TextKey.OCCASION_TITLE_WEDDING_OWN
        OccasionKind.OTHER -> TextKey.OCCASION_TITLE_OTHER_OF to TextKey.OCCASION_TITLE_OTHER_OWN
    }
    return if (personName != null) uiText(of, personName) else uiText(own)
}
