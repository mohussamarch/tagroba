package app.masroufy.ui.screens.people

import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionKind
import app.masroufy.core.R06Key
import app.masroufy.core.TextRef
import app.masroufy.core.dayMonth
import app.masroufy.core.daysBetween
import app.masroufy.core.occasionDateIn
import app.masroufy.core.occasionSoonDays
import app.masroufy.core.parseIsoDate
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.shell.CalendarMark
import app.masroufy.ui.shell.MarkKind
import app.masroufy.usecase.UpcomingOccasion

/**
 * صفحة الشخص الجديدة (r06-person — OVERRIDES §116-ب · `PersonHero.dc.html` و`PersonProfile.dc.html`، فرع التصميم 59eac9b8) من غير رسم:
 * - المناسبات **السنوية** = أيقونات صغيرة فوق في الأخضر · كل المناسبات (سنوية ومرة واحدة) = كروت الستارة · **مرة واحدة** = الشريط تحت الكروت.
 * - التنبيه = أقرب مناسبة **مرة واحدة** جوه مدة تذكيرها (`occasionSoonDays`).
 * - ترتيب أقسام الصفحة ثابت: كروت «لك/عليك» دايمًا (حتى من غير ديون — §115-1) والتنبيه **بعدها** فما بيزقهاش.
 * ترتيب وكلام بس — مفيش أي حساب فلوس.
 */
internal enum class OccGlyph(val icon: Lucide) {
    CAKE(PeopleIcons.CAKE), HEART(PeopleIcons.HEART), GIFT(Lucide.GIFT), FRIENDS(PeopleIcons.FRIENDS), CALENDAR(Lucide.CALENDAR),
}

/** عيد ميلاد ⇒ كيكة · عيد جواز ⇒ قلب · فرح ⇒ هدية · غيره ⇒ صحاب (سنوية) أو تقويم (مرة واحدة) — اختيار Claude للأنواع اللي النموذج ما رسمهاش. */
internal fun glyphOf(o: Occasion): OccGlyph = when (o.kind) {
    OccasionKind.BIRTHDAY -> OccGlyph.CAKE
    OccasionKind.WEDDING_ANNIVERSARY -> OccGlyph.HEART
    OccasionKind.WEDDING -> OccGlyph.GIFT
    OccasionKind.OTHER -> if (o.yearly) OccGlyph.FRIENDS else OccGlyph.CALENDAR
}

internal data class OccCard(val id: Id, val glyph: OccGlyph, val title: String, val date: String, val left: String, val yearly: Boolean, val wedding: Boolean)

internal data class OccNotice(val id: Id, val glyph: OccGlyph, val title: String, val sub: String, val wedding: Boolean)

internal data class PersonOccasionsUi(
    /** أيقونات فوق في الأخضر (سنوية بس) — من غير كلام، والاسم لقارئ الشاشة في [OccCard.title]. */
    val yearly: List<OccCard>,
    /** كروت الستارة: السنوية الأول وبعدين المرة الواحدة (زي النموذج). */
    val cards: List<OccCard>,
    /** شريط «مناسبات مرة واحدة». */
    val once: List<OccCard>,
    val notice: OccNotice?,
)

internal fun personOccasionsUi(items: List<UpcomingOccasion>, today: IsoDate): PersonOccasionsUi {
    fun card(u: UpcomingOccasion) = OccCard(
        u.occasion.id, glyphOf(u.occasion), occasionCardTitle(u),
        if (u.occasion.yearly) dayMonth(u.date) else dayMonthYear(u.date),
        relativeDays(daysBetween(today, u.date)), u.occasion.yearly, u.occasion.kind == OccasionKind.WEDDING,
    )
    val yearly = items.filter { it.occasion.yearly }.map(::card)
    val onceItems = items.filter { !it.occasion.yearly }
    val soon = onceItems.firstOrNull { daysBetween(today, it.date) in 0..occasionSoonDays(it.occasion) }
    return PersonOccasionsUi(
        yearly = yearly,
        cards = yearly + onceItems.map(::card),
        once = onceItems.map(::card),
        notice = soon?.let {
            OccNotice(it.occasion.id, glyphOf(it.occasion), joinLine(occasionCardTitle(it), relativeDays(daysBetween(today, it.date))), occasionCardSub(it, today), it.occasion.kind == OccasionKind.WEDDING)
        },
    )
}

/** علامات شهر في تقويم الشخص: كل مناسباته (السنوية كل سنة، والمرة الواحدة في سنتها بس). */
internal fun occasionMarks(items: List<UpcomingOccasion>, year: Int, month: Int): List<CalendarMark> = items.mapNotNull { u ->
    val o = u.occasion
    val date = when {
        o.month != month -> null
        o.yearly -> occasionDateIn(o, year)
        o.year == year -> occasionDateIn(o, year)
        else -> null
    } ?: return@mapNotNull null
    CalendarMark(parseIsoDate(date).day, MarkKind.EVENT, occasionCardTitle(u))
}.sortedBy { it.day }

/** «معاه» بيفرد 4 أيقونات على قوس (مش قايمة — تعديل المالك 2026-10-23). «طلب اتصال» مش هنا — ليه علامته لوحده شمال الصورة. */
internal enum class FanAction(val label: TextRef, val icon: Lucide) {
    DEBT(R06Key.PH_FAN_DEBT, PeopleIcons.HAND_COINS),
    SPLIT(R06Key.PH_FAN_SPLIT, PeopleIcons.SPLIT_BILL),
    ROSCA(R06Key.PH_FAN_ROSCA, PeopleIcons.ROSCA),
    PROJECT(R06Key.PH_FAN_PROJECT, PeopleIcons.PROJECT),
}

internal fun fanActions(): List<FanAction> = FanAction.entries

/** أقسام صفحة الشخص بالترتيب. [NOTICE] بس لو فيه تنبيه — وبعد [BALANCES] دايمًا. */
internal enum class PersonSection { HERO, NAME, BALANCES, NOTICE, ONCE, HISTORY, ACCOUNTS, NEEDS_PERSON, LINK_OPS, DUES_RULES, ARCHIVE, BADGES }

internal fun personSections(ui: PersonPageUi, hasNotice: Boolean, hasLedger: Boolean): List<PersonSection> = buildList {
    add(PersonSection.HERO)
    add(PersonSection.NAME)
    add(PersonSection.BALANCES)
    if (hasNotice) add(PersonSection.NOTICE)
    add(PersonSection.ONCE)
    add(PersonSection.HISTORY)
    if (hasLedger) {
        add(PersonSection.ACCOUNTS)
        add(PersonSection.NEEDS_PERSON)
        add(PersonSection.LINK_OPS)
    }
    add(PersonSection.DUES_RULES)
    if (!ui.archived) add(PersonSection.ARCHIVE)
    if (ui.badges.isNotEmpty()) add(PersonSection.BADGES)
}
