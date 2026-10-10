package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import app.masroufy.core.IsoDate
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionKind
import app.masroufy.core.TextKey
import app.masroufy.core.checkOccasion
import app.masroufy.core.daysBetween
import app.masroufy.core.nextOccurrence
import app.masroufy.core.normalizeDigits
import app.masroufy.core.occasionTitle
import app.masroufy.ui.text.t
import app.masroufy.usecase.OccasionInput
import app.masroufy.usecase.UpcomingOccasion

/**
 * لوحة «مناسبة» (`OccasionSheet`) من غير رسم: الخانات · سطر «المرة القادمة» لايف · التذكير · الفحص قبل الحفظ.
 * التواريخ من `core` (`checkOccasion` · `nextOccurrence`) — ميلادي بس، ٢٩ فبراير ⇒ ٢٨ في السنين العادية، والتذكير ١–٦٠ يوم (المبدئي أسبوع).
 */
internal data class OccasionDraft(
    val kind: OccasionKind = OccasionKind.BIRTHDAY,
    val label: String = "",
    val month: Int? = null,
    val day: String = "",
    val year: String = "",
    val yearly: Boolean = true,
    val lead: Int = DEFAULT_LEAD,
) {
    companion object {
        const val DEFAULT_LEAD = 7
        const val LEAD_MAX = 60

        fun of(o: Occasion) = OccasionDraft(
            o.kind, o.label.orEmpty(), o.month, o.day.toString(), o.year?.toString().orEmpty(), o.yearly, o.leadDays ?: DEFAULT_LEAD,
        )
    }

    fun withLead(v: Int) = copy(lead = v.coerceIn(1, LEAD_MAX))

    /** الفرح مرة واحدة دايمًا؛ الباقي بيرجع سنوي لما تغيّر النوع. */
    fun withKind(k: OccasionKind) = copy(kind = k, yearly = if (k == OccasionKind.WEDDING) false else if (k == kind) yearly else true)
}

/** رقم صحيح من خانة (الأرقام العربي مقبولة) أو null. */
internal fun intOf(text: String): Int? = normalizeDigits(text).filter { it in '0'..'9' }.takeIf { it.isNotEmpty() && it.length <= 6 }?.toInt()

private fun draftOccasion(d: OccasionDraft): Occasion? {
    val month = d.month ?: return null
    val day = intOf(d.day) ?: return null
    return runCatching {
        checkOccasion(Occasion("draft", null, d.kind, d.label.takeIf { d.kind == OccasionKind.OTHER }, month, day, intOf(d.year), d.yearly, d.lead, null, ""))
    }.getOrNull()
}

/** «قبلها بأسبوع» · «قبلها بيومين» · «قبلها بـ١٠ أيام». */
internal fun leadName(days: Int): String = when (days) {
    1 -> t(UiKey.OCC_LEAD_ONE)
    2 -> t(UiKey.OCC_LEAD_TWO)
    7 -> t(UiKey.OCC_LEAD_WEEK)
    14 -> t(UiKey.OCC_LEAD_TWO_WEEKS)
    30 -> t(UiKey.OCC_LEAD_MONTH)
    else -> t(UiKey.OCC_LEAD_DAYS, countOf(days, Noun.DAYS))
}

/** سطرين لايف تحت التاريخ: «المرة القادمة: …، بعد N» و«يصلك التذكير …». */
internal data class OccasionPreview(val next: String, val remind: String)

internal fun occasionPreview(d: OccasionDraft, today: IsoDate): OccasionPreview {
    val o = draftOccasion(d)
    val next = o?.let { nextOccurrence(it, today) }
    val nextText = when {
        next != null -> t(UiKey.OCC_NEXT, joinLine(dayMonthYear(next), relativeDays(daysBetween(today, next))))
        o != null && !o.yearly -> t(UiKey.OCC_PAST)
        else -> t(UiKey.OCC_PICK)
    }
    val remind = when {
        next == null -> t(UiKey.OCC_REMIND_PLAIN)
        daysBetween(today, next) <= d.lead -> t(UiKey.OCC_REMIND_NOW)
        else -> t(UiKey.OCC_REMIND_USUAL, leadName(d.lead))
    }
    return OccasionPreview(nextText, remind)
}

/**
 * الفحص قبل الحفظ بنفس ترتيب النموذج — رسالة واحدة واضحة أو [OccasionInput] جاهز لـ`ManageOccasions.add/update`
 * (اللي بيفحص تاني بـ`checkOccasion` — الحد الحقيقي هناك).
 */
internal sealed interface OccasionCheck {
    data class Ok(val input: OccasionInput) : OccasionCheck
    data class Bad(val message: String) : OccasionCheck
}

internal fun checkDraft(d: OccasionDraft, personId: String?): OccasionCheck {
    val label = d.label.trim()
    if (d.kind == OccasionKind.OTHER && label.isEmpty()) return OccasionCheck.Bad(t(UiKey.OCC_NEED_LABEL))
    val month = d.month ?: return OccasionCheck.Bad(t(UiKey.OCC_NEED_MONTH))
    val year = intOf(d.year)
    if (d.year.isNotBlank() && year == null) return OccasionCheck.Bad(t(UiKey.PPL_DATE_INVALID))
    val day = intOf(d.day) ?: return OccasionCheck.Bad(t(UiKey.OCC_NEED_DAY))
    if (!d.yearly && year == null) return OccasionCheck.Bad(t(UiKey.PPL_ONCE_NEEDS_YEAR))
    val input = OccasionInput(personId, d.kind, label.takeIf { d.kind == OccasionKind.OTHER }, month, day, year, d.yearly, d.lead)
    return runCatching {
        checkOccasion(Occasion("draft", personId, input.kind, input.label, month, day, year, input.yearly, input.leadDays, null, ""))
        OccasionCheck.Ok(input) as OccasionCheck
    }.getOrElse { OccasionCheck.Bad(it.message ?: t(UiKey.PPL_DATE_INVALID)) }
}

/** كارت المناسبة: العنوان («زواج خالد») وتحته «١٨ أكتوبر ٢٠٢٦، بعد ١١ يومًا، التذكير قبلها بأسبوع». */
internal fun occasionCardTitle(u: UpcomingOccasion): String = occasionTitle(u.occasion, u.personName, null)

internal fun occasionCardSub(u: UpcomingOccasion, today: IsoDate): String =
    joinLine(dayMonthYear(u.date), relativeDays(daysBetween(today, u.date)), t(UiKey.OCC_REMIND_PREFIX, leadName(u.occasion.leadDays ?: OccasionDraft.DEFAULT_LEAD)))
