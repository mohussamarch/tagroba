package app.masroufy.core

/**
 * ملخص شاشة الأشخاص (دالة نقية — الشاشة ما بتحسبش رقم): لكل شخص دايرته وصلته بيك، وأرصدته **سطر لكل (بلد · عملة · اتجاه)**
 * — **مفيش جمع بين عملتين ولا بين بلدين** (spec/02 «لا تقاص» · §64 «كل بلد لوحدها بعملتها») — ولون إطار الصورة، وأقرب مناسبة،
 * وبادجات النقوط. والقايمة متقسمة أقسام بعدد، والإجماليات فوق لكل (بلد · عملة).
 */

/** رصيد الشخص في بلد بعملة — نفس أرقام `PersonAcrossSpaces` (لك عنده · عليك قرض · عليك أمانة). */
data class PersonSpaceBalance(
    val personId: Id,
    val spaceId: String,
    val spaceLabel: String,
    val currency: Currency,
    val receivableMinor: Halalas,
    val payableLoanMinor: Halalas,
    val payableCustodyMinor: Halalas,
)

enum class BalanceDirection { OWED_TO_YOU, YOU_OWE }

/** سطر رصيد واحد. «عليك» = القرض + الأمانة **في نفس البلد ونفس العملة** ([custodyMinor] = جزء الأمانة منه). */
data class PersonBalanceLine(
    val spaceId: String,
    val spaceLabel: String,
    val currency: Currency,
    val direction: BalanceDirection,
    val amountMinor: Halalas,
    val custodyMinor: Halalas = 0,
)

/** لون إطار الصورة — بأولوية ثابتة: عليك > لك > مناسبة خلال [PEOPLE_OCCASION_SOON_DAYS] يوم > ولا حاجة. */
enum class PersonOutlineState { YOU_OWE, OWED_TO_YOU, OCCASION_SOON, NONE }

/** «مناسبة قريبة» في شاشة الأشخاص = خلال 30 يوم (النهارده محسوب) — من طلب الشاشة. */
const val PEOPLE_OCCASION_SOON_DAYS = 30

data class PersonNextOccasion(val occasion: Occasion, val date: IsoDate, val daysAway: Int)

/** بادج نقوط من بلد معينة (الأحداث جوه كل بلد). */
data class SpaceGiftBadge(val spaceId: String, val spaceLabel: String, val badge: GiftBadge)

data class PersonOverviewRow(
    val person: Person,
    val circle: PersonCircle,
    val relationLabel: String?,
    val balances: List<PersonBalanceLine>,
    val state: PersonOutlineState,
    val nextOccasion: PersonNextOccasion?,
    val giftBadges: List<SpaceGiftBadge>,
)

enum class PeopleSection(val labelKey: TextKey) {
    OWED_TO_YOU(TextKey.PEOPLE_SECTION_OWED_TO_YOU),
    YOU_OWE(TextKey.PEOPLE_SECTION_YOU_OWE),
    OCCASIONS_SOON(TextKey.PEOPLE_SECTION_OCCASIONS_SOON),
    NO_BALANCE(TextKey.PEOPLE_SECTION_NO_BALANCE),
    ;

    val label: String get() = uiText(labelKey)
}

data class PeopleSectionRows(val section: PeopleSection, val personIds: List<Id>) {
    val count: Int get() = personIds.size
}

/** إجمالي بلد بعملة — مجموع الأشخاص في نفس البلد ونفس العملة بس. */
data class PeopleTotal(val spaceId: String, val spaceLabel: String, val currency: Currency, val owedToYouMinor: Halalas, val youOweMinor: Halalas)

data class PeopleOverview(
    val rows: List<PersonOverviewRow>,
    /** الحلقات: عيلة · صحاب · شغل · غيرهم — الأشخاص المؤرشفين برا الحلقات. */
    val rings: Map<PersonCircle, List<Id>>,
    val relations: List<PersonRelation>,
    val sections: List<PeopleSectionRows>,
    val totals: List<PeopleTotal>,
)

data class PeopleOverviewInput(
    val today: IsoDate,
    val people: List<Person>,
    val profiles: List<PersonProfile>,
    val relations: List<PersonRelation>,
    val occasions: List<Occasion>,
    val balances: List<PersonSpaceBalance>,
    val badges: List<Pair<Id, SpaceGiftBadge>>,
)

/** سطور رصيد الشخص — «لك» و«عليك» منفصلين، ومن غير سطر صفر. */
fun balanceLines(balances: List<PersonSpaceBalance>): List<PersonBalanceLine> = balances.flatMap { b ->
    val owe = addMoney(b.payableLoanMinor, b.payableCustodyMinor)
    listOfNotNull(
        if (b.receivableMinor > 0) PersonBalanceLine(b.spaceId, b.spaceLabel, b.currency, BalanceDirection.OWED_TO_YOU, b.receivableMinor) else null,
        if (owe > 0) PersonBalanceLine(b.spaceId, b.spaceLabel, b.currency, BalanceDirection.YOU_OWE, owe, b.payableCustodyMinor) else null,
    )
}

/** اختيار Claude (المالك يقدر يغيّره): «عليك» الأول لأنه التزام عليك، بعده «لك»، بعده المناسبة القريبة. */
fun outlineState(lines: List<PersonBalanceLine>, next: PersonNextOccasion?): PersonOutlineState = when {
    lines.any { it.direction == BalanceDirection.YOU_OWE } -> PersonOutlineState.YOU_OWE
    lines.any { it.direction == BalanceDirection.OWED_TO_YOU } -> PersonOutlineState.OWED_TO_YOU
    next != null && next.daysAway <= PEOPLE_OCCASION_SOON_DAYS -> PersonOutlineState.OCCASION_SOON
    else -> PersonOutlineState.NONE
}

/** أقرب مناسبة جاية للشخص (النهارده محسوب). */
fun nextPersonOccasion(personId: Id, occasions: List<Occasion>, today: IsoDate): PersonNextOccasion? = occasions
    .filter { it.personId == personId }
    .mapNotNull { o -> nextOccurrence(o, today)?.let { PersonNextOccasion(o, it, daysBetween(today, it)) } }
    .minWithOrNull(compareBy<PersonNextOccasion> { it.date }.thenBy { it.occasion.id })

fun peopleOverview(input: PeopleOverviewInput): PeopleOverview {
    val profiles = input.profiles.associateBy { it.personId }
    val balancesByPerson = input.balances.groupBy { it.personId }
    val badgesByPerson = input.badges.groupBy({ it.first }, { it.second })
    val rows = input.people.mapNotNull { p ->
        val lines = balanceLines(balancesByPerson[p.id].orEmpty())
        // المؤرشف: بيفضل ظاهر بس لو عليه أو ليه فلوس (الرقم موجود — القاعدة 10)، ومناسباته ما بتتحسبش (زي التنبيهات §64)
        if (p.archived && lines.isEmpty()) return@mapNotNull null
        val next = if (p.archived) null else nextPersonOccasion(p.id, input.occasions, input.today)
        val profile = profiles[p.id]
        PersonOverviewRow(p, circleOf(profile), profile?.relationLabel, lines, outlineState(lines, next), next, badgesByPerson[p.id].orEmpty())
    }.sortedWith { a, b -> compareArabic(a.person.name, b.person.name).takeIf { it != 0 } ?: a.person.id.compareTo(b.person.id) }

    val active = rows.filter { !it.person.archived }
    // رد المالك (§67): اللي مالوش دايرة («آخرون») في القايمة بس، مش في الدواير
    val rings = PersonCircle.entries.filter { it != PersonCircle.OTHER }.associateWith { c -> active.filter { it.circle == c }.map { it.person.id } }
    return PeopleOverview(rows, rings, visibleRelations(input.relations, input.people), sections(rows), totals(rows))
}

/**
 * الأقسام (اختيار Claude — المالك يقدر يغيّره): «لك عندهم» و«عليك لهم» كل اللي عنده سطر بالاتجاه ده — **الشخص اللي ليه وعليه بيظهر في
 * الاتنين** (من غير تقاص)؛ «مناسبات قريبة» اللي مالهمش أرصدة ومناسبتهم خلال 30 يوم (بالأقرب)؛ «بلا أرصدة» الباقي.
 */
private fun sections(rows: List<PersonOverviewRow>): List<PeopleSectionRows> {
    fun has(r: PersonOverviewRow, d: BalanceDirection) = r.balances.any { it.direction == d }
    val noBalance = rows.filter { it.balances.isEmpty() }
    val soon = noBalance.filter { it.state == PersonOutlineState.OCCASION_SOON }.sortedBy { it.nextOccasion!!.date }
    return listOf(
        PeopleSectionRows(PeopleSection.OWED_TO_YOU, rows.filter { has(it, BalanceDirection.OWED_TO_YOU) }.map { it.person.id }),
        PeopleSectionRows(PeopleSection.YOU_OWE, rows.filter { has(it, BalanceDirection.YOU_OWE) }.map { it.person.id }),
        PeopleSectionRows(PeopleSection.OCCASIONS_SOON, soon.map { it.person.id }),
        PeopleSectionRows(PeopleSection.NO_BALANCE, noBalance.filter { it.state != PersonOutlineState.OCCASION_SOON }.map { it.person.id }),
    )
}

/** الإجماليات: لكل (بلد · عملة) لوحدها — حتى نفس العملة في بلدين **ما بتتجمعش** (§64 قرار ١١). */
private fun totals(rows: List<PersonOverviewRow>): List<PeopleTotal> {
    val out = LinkedHashMap<Pair<String, Currency>, PeopleTotal>()
    for (line in rows.flatMap { it.balances }) {
        val key = line.spaceId to line.currency
        val t = out[key] ?: PeopleTotal(line.spaceId, line.spaceLabel, line.currency, 0, 0)
        out[key] = when (line.direction) {
            BalanceDirection.OWED_TO_YOU -> t.copy(owedToYouMinor = addMoney(t.owedToYouMinor, line.amountMinor))
            BalanceDirection.YOU_OWE -> t.copy(youOweMinor = addMoney(t.youOweMinor, line.amountMinor))
        }
    }
    return out.values.sortedWith(compareBy<PeopleTotal> { it.spaceId != DEFAULT_SPACE_ID }.thenBy { it.spaceId }.thenBy { it.currency.name })
}
