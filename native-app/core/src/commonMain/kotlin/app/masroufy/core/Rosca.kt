package app.masroufy.core

/**
 * الجمعية — قرارات المالك 2026-09-30 (OVERRIDES §50).
 *
 * **القسط مش مصروف والدور مش دخل.** قبل دورك القسط فلوس محوّشة عند الجمعية، وبعد دورك دين بترده.
 * فبدل ما التطبيق يسأل «ده ادخار ولا سداد؟» بيمسك **رقم واحد**: موقفك = اللي دفعته − اللي قبضته.
 * موجب ⇒ الجمعية شايلالك فلوس. سالب ⇒ عليك للجمعية. وفي الآخر بيرجع صفر.
 *
 * المالك **عضو** بس دلوقتي؛ الجمعية المشتركة (حد يبدأها ويبعت دعوات ولوحة للكل) **بعدين** —
 * عشان كده الأعضاء وأدوارهم ليهم مكان في الكيان من دلوقتي، بس **اختياريين**.
 */
data class RoscaMember(
    /** رقم دوره. */
    val turn: Int,
    val name: String,
    /** لو الشخص متسجل في التطبيق. */
    val personId: Id? = null,
)

data class Rosca(
    val id: Id,
    val name: String,
    val currency: Currency,
    /** قسطك إنت كل دور — لو ماسك نص سهم يبقى نص القسط، ولو سهمين يبقى الضعف. */
    val contributionMinor: Halalas,
    /** كل كام [unit] (1 = كل شهر أو كل أسبوع). */
    val every: Int,
    /** ميعاد أول قسط، وهو نفسه ميعاد قبض الدور الأول. */
    val firstDueAt: IsoDate,
    /** عدد الأدوار = مدة الجمعية. */
    val cycleCount: Int,
    /** أدوارك (من 1). غالبًا دور واحد. **فاضية = لسه ما اتحددش** (قرعة مثلًا) — القبض بيبان «غير متاح» مش صفر. */
    val myTurns: List<Int>,
    /** اللي هتقبضه في كل دور من أدوارك. صفر لو دورك لسه ما اتحددش. */
    val payoutMinor: Halalas,
    val members: List<RoscaMember> = emptyList(),
    /** المسؤول اللي بيلم الفلوس — لو متسجل. */
    val organizerPersonId: Id? = null,
    val createdAt: String = "",
    val unit: CycleUnit = CycleUnit.MONTH,
    /** §75-8 (الشريحة S4): عمليات المالك قال عنها «مش من الجمعية دي» ⇒ ما تتقترحش عليها تاني (`SuggestDueLinks`). */
    val dismissedTxnIds: List<Id> = emptyList(),
)

enum class RoscaEntryKind(val wire: String) {
    CONTRIBUTION("contribution"), PAYOUT("payout");

    companion object {
        fun fromWire(wire: String): RoscaEntryKind = entries.first { it.wire == wire }
    }
}

/** ربط عملية من الكشف بالجمعية — زي التسوية مع الدين (spec/03). */
data class RoscaEntry(val id: Id, val roscaId: Id, val transactionId: Id, val kind: RoscaEntryKind, val amountMinor: Halalas)

const val ROSCA_NAME_MAX = 80
const val ROSCA_MAX_CYCLES = 60

class RoscaError(message: String) : IllegalArgumentException(message)

/**
 * قيمة الدور لما الأعضاء متساويين: (قسطك × عدد الأدوار) ÷ عدد أدوارك.
 * لو ما بتتقسمش بالظبط بيرجع null — **المستخدم يكتبها**، ما بنقرّبش فلوس حد.
 */
fun defaultRoscaPayout(contributionMinor: Halalas, cycleCount: Int, turns: Int): Halalas? {
    if (turns <= 0) return null
    val total = multiplyMoneyByInt(contributionMinor, cycleCount.toLong())
    return if (total % turns == 0L) total / turns else null
}

fun checkRosca(r: Rosca): CheckedName {
    val clean = JsText.collapseWhitespace(JsText.trim(r.name))
    if (clean.isEmpty() || clean.length > ROSCA_NAME_MAX) throw RoscaError(uiText(TextKey.ROSCA_NAME_LENGTH, ROSCA_NAME_MAX.toString()))
    if (r.cycleCount !in 2..ROSCA_MAX_CYCLES) throw RoscaError(uiText(TextKey.ROSCA_CYCLE_COUNT, ROSCA_MAX_CYCLES.toString()))
    if (r.myTurns.toSet().size != r.myTurns.size || r.myTurns.any { it !in 1..r.cycleCount }) {
        throw RoscaError(uiText(TextKey.ROSCA_TURNS, r.cycleCount.toString()))
    }
    assertHalalas(r.payoutMinor)
    if (if (r.myTurns.isEmpty()) r.payoutMinor < 0 else r.payoutMinor <= 0) throw RoscaError(uiText(TextKey.ROSCA_PAYOUT_POSITIVE))
    for (m in r.members) {
        if (m.turn !in 1..r.cycleCount) throw RoscaError(uiText(TextKey.ROSCA_MEMBER_TURN, m.name, r.cycleCount.toString()))
    }
    checkDueSchedule(contributionSchedule(r))
    return CheckedName(clean, normalizeText(clean))
}

/** أقساطك: قسط كل دور لحد آخر الجمعية. */
fun contributionSchedule(r: Rosca): DueSchedule =
    DueSchedule(r.firstDueAt, r.every, r.contributionMinor, multiplyMoneyByInt(r.contributionMinor, r.cycleCount.toLong()), r.currency, r.unit)

fun totalPayoutOf(r: Rosca): Halalas = multiplyMoneyByInt(r.payoutMinor, r.myTurns.size.toLong())

enum class RoscaPhase(val wire: String) {
    /** لسه ما قبضتش — القسط ادخار. */
    SAVING("saving"),

    /** قبضت وبتكمّل — القسط سداد. */
    REPAYING("repaying"),

    /** دفعت كل الأقساط وقبضت كل أدوارك. */
    DONE("done"),
}

enum class PayoutState(val wire: String) { RECEIVED("received"), PARTIAL("partial"), LATE("late"), EXPECTED("expected") }

data class RoscaPayoutView(val turn: Int, val dueAt: IsoDate, val amountMinor: Halalas, val receivedMinor: Halalas, val state: PayoutState)

data class RoscaStatus(
    val contributions: DueProgress,
    val payouts: List<RoscaPayoutView>,
    val paidMinor: Halalas,
    val receivedMinor: Halalas,
    /** اللي دفعته − اللي قبضته. موجب = الجمعية شايلالك، سالب = عليك للجمعية. */
    val positionMinor: Halalas,
    /** الفرق بين كل اللي هتقبضه وكل اللي هتدفعه — صفر في الجمعية العادية؛ سالب = رسوم عليك. null = دورك لسه ما اتحددش. */
    val gainMinor: Halalas?,
    val phase: RoscaPhase,
)

fun roscaStatus(r: Rosca, entries: List<RoscaEntry>, today: IsoDate): RoscaStatus {
    checkRosca(r)
    val mine = entries.filter { it.roscaId == r.id }
    val paid = sumMoney(mine.filter { it.kind == RoscaEntryKind.CONTRIBUTION }.map { it.amountMinor })
    val received = sumMoney(mine.filter { it.kind == RoscaEntryKind.PAYOUT }.map { it.amountMinor })
    val totalPayout = totalPayoutOf(r)
    if (received > totalPayout) {
        throw RoscaError(uiText(TextKey.ROSCA_PAYOUT_OVER, formatMoney(totalPayout, r.currency), formatMoney(received - totalPayout, r.currency)))
    }
    val schedule = contributionSchedule(r)
    // المقبوض بيتوزع على أدوارك بالترتيب — زي الأقساط
    var left = received
    val payouts = r.myTurns.sorted().map { turn ->
        val dueAt = dueDateOf(schedule, turn)
        val got = minOf(left, r.payoutMinor)
        left -= got
        val state = when {
            got == r.payoutMinor -> PayoutState.RECEIVED
            got > 0 -> PayoutState.PARTIAL
            dueAt < today -> PayoutState.LATE
            else -> PayoutState.EXPECTED
        }
        RoscaPayoutView(turn, dueAt, r.payoutMinor, got, state)
    }
    val contributions = dueProgress(schedule, paid, today)
    val phase = when {
        contributions.done && received == totalPayout -> RoscaPhase.DONE
        received > 0 -> RoscaPhase.REPAYING
        else -> RoscaPhase.SAVING
    }
    return RoscaStatus(contributions, payouts, paid, received, subtractMoney(paid, received),
        if (r.myTurns.isEmpty()) null else subtractMoney(totalPayout, schedule.totalMinor), phase,
    )
}

/** السبب لو مرفوض، أو null لو المبلغ ينفع يتربط. الزيادة **ما بتتبلعش** — نفس قاعدة التسوية. */
fun checkRoscaEntry(r: Rosca, entries: List<RoscaEntry>, kind: RoscaEntryKind, amountMinor: Halalas): String? {
    assertHalalas(amountMinor)
    if (amountMinor <= 0) return uiText(TextKey.DUE_AMOUNT_POSITIVE)
    if (kind == RoscaEntryKind.PAYOUT && r.myTurns.isEmpty()) return uiText(TextKey.ROSCA_TURN_UNKNOWN)
    val mine = entries.filter { it.roscaId == r.id && it.kind == kind }
    val done = sumMoney(mine.map { it.amountMinor })
    val cap = if (kind == RoscaEntryKind.CONTRIBUTION) contributionSchedule(r).totalMinor else totalPayoutOf(r)
    val room = subtractMoney(cap, done)
    if (amountMinor <= room) return null
    val key = if (kind == RoscaEntryKind.CONTRIBUTION) TextKey.ROSCA_CONTRIBUTION_OVER else TextKey.ROSCA_PAYOUT_OVER
    return uiText(key, formatMoney(room, r.currency), formatMoney(amountMinor - room, r.currency))
}

/** قبض من الجمعية: [ownMinor] الجزء اللي كان **من فلوسك** (اللي دفعته قبله وما رجعلكش لسه). */
data class RoscaOwnReceipt(val entryId: Id, val date: IsoDate, val ownMinor: Halalas)

/**
 * الجمعية في مصر = زي الدين ليك (رد المالك §62، فتوى 4399): اللي دفعته وما قبضتوش ما بيدخلش الحساب السنوي، ولما تقبض بيتزكّى
 * **مرة واحدة على اللي كان من فلوسك** بس — الباقي من القبض فلوس الناس (سلفة عليك). [entries] كل حركة بتاريخها (عمليتها).
 * الترتيب بالتاريخ، وفي نفس اليوم الدفع قبل القبض (القسط اللي دفعته يوم القبض من فلوسك) — اختيار Claude. كل قبض بياخد من
 * اللي دفعته ولسه ما رجعلكش، فما يتعدش مبلغ مرتين. القبض اللي مفيش قبله فلوس ليك ⇒ مش في القايمة.
 */
fun roscaOwnMoneyReceipts(entries: List<Pair<RoscaEntry, IsoDate>>): List<RoscaOwnReceipt> {
    var outstanding = 0L
    val out = mutableListOf<RoscaOwnReceipt>()
    for ((e, date) in entries.sortedWith(compareBy({ it.second }, { it.first.kind != RoscaEntryKind.CONTRIBUTION }, { it.first.id }))) {
        if (e.kind == RoscaEntryKind.CONTRIBUTION) {
            outstanding = addMoney(outstanding, e.amountMinor)
            continue
        }
        val own = minOf(e.amountMinor, outstanding)
        if (own <= 0) continue
        outstanding = subtractMoney(outstanding, own)
        out += RoscaOwnReceipt(e.id, date, own)
    }
    return out
}
