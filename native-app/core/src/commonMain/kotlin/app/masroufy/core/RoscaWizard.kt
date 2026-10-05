package app.masroufy.core

/**
 * إنشاء الجمعية بأسئلة خطوة خطوة — طلب المالك 2026-09-30 (OVERRIDES §50): «بنك أسئلة بخطوات أثناء إنشاء
 * الجمعية: فيه كام واحد، ودورك رقم كام، وهي كل كام مدة… وبعد ما ينشئها تظهرله تحليلات هيقبض إمتى وهيدفع إيه».
 *
 * منطق نقي: الشاشة بتسأل [nextRoscaQuestion] وبتبعت الإجابة لـ[applyRoscaAnswer]، و**كل إجابة بتتفحص لوحدها**
 * فالغلط بيبان في سؤاله مش في الآخر. الرجوع لسؤال قبل كده وتغييره بيمسح بس الإجابات اللي بقت متعارضة معاه.
 */
enum class RoscaQuestion { NAME, TURNS_COUNT, SHARE_AMOUNT, FREQUENCY, FIRST_DATE, SHARE, MY_TURN, PAYOUT }

enum class RoscaFrequency(val unit: CycleUnit, val every: Int, val labelKey: TextKey) {
    WEEKLY(CycleUnit.WEEK, 1, TextKey.ROSCA_FREQ_WEEKLY),
    BIWEEKLY(CycleUnit.WEEK, 2, TextKey.ROSCA_FREQ_BIWEEKLY),
    MONTHLY(CycleUnit.MONTH, 1, TextKey.ROSCA_FREQ_MONTHLY),
    BIMONTHLY(CycleUnit.MONTH, 2, TextKey.ROSCA_FREQ_BIMONTHLY),
    QUARTERLY(CycleUnit.MONTH, 3, TextKey.ROSCA_FREQ_QUARTERLY),
}

/** نص سهم = نص القسط ودور واحد؛ سهمين = ضعف القسط ودورين. */
enum class RoscaShare(val numerator: Long, val denominator: Long, val turns: Int, val labelKey: TextKey) {
    HALF(1, 2, 1, TextKey.ROSCA_SHARE_HALF),
    ONE(1, 1, 1, TextKey.ROSCA_SHARE_ONE),
    TWO(2, 1, 2, TextKey.ROSCA_SHARE_TWO),
}

data class RoscaDraft(
    val currency: Currency,
    val name: String? = null,
    val cycleCount: Int? = null,
    /** قسط **السهم الكامل** — اللي الناس بتقوله («الجمعية بألف»). قسطك إنت محسوب من السهم. */
    val shareAmountMinor: Halalas? = null,
    val frequency: RoscaFrequency? = null,
    val firstDueAt: IsoDate? = null,
    val share: RoscaShare? = null,
    /** null = لسه ما اتسألش. فاضية = المستخدم قال «لسه ما اتعرفش». */
    val myTurns: List<Int>? = null,
    val payoutMinor: Halalas? = null,
)

sealed interface RoscaAnswer {
    data class Name(val value: String) : RoscaAnswer
    data class TurnsCount(val value: Int) : RoscaAnswer
    data class ShareAmount(val minor: Halalas) : RoscaAnswer
    data class Frequency(val value: RoscaFrequency) : RoscaAnswer
    data class FirstDate(val value: IsoDate) : RoscaAnswer
    data class Share(val value: RoscaShare) : RoscaAnswer
    /** فاضية = «لسه ما اتعرفش». */
    data class MyTurns(val value: List<Int>) : RoscaAnswer
    /** null = موافق على المحسوبة. */
    data class Payout(val minor: Halalas?) : RoscaAnswer
}

fun nextRoscaQuestion(d: RoscaDraft): RoscaQuestion? = when {
    d.name == null -> RoscaQuestion.NAME
    d.cycleCount == null -> RoscaQuestion.TURNS_COUNT
    d.shareAmountMinor == null -> RoscaQuestion.SHARE_AMOUNT
    d.frequency == null -> RoscaQuestion.FREQUENCY
    d.firstDueAt == null -> RoscaQuestion.FIRST_DATE
    d.share == null -> RoscaQuestion.SHARE
    d.myTurns == null -> RoscaQuestion.MY_TURN
    d.myTurns.isNotEmpty() && d.payoutMinor == null -> RoscaQuestion.PAYOUT
    else -> null
}

/** «سؤال 3 من 8». لو دورك لسه مش معروف، سؤال قيمة الدور بيتشال فالإجمالي 7. */
fun roscaQuestionProgress(d: RoscaDraft): Pair<Int, Int> {
    val total = if (d.myTurns?.isEmpty() == true) RoscaQuestion.entries.size - 1 else RoscaQuestion.entries.size
    val next = nextRoscaQuestion(d) ?: return total to total
    return (next.ordinal + 1) to total
}

/** قسطك إنت = قسط السهم × نصيبك. نص سهم لقسط فردي بالهللة **بيترفض** — ما بنقرّبش. */
fun roscaContributionOf(shareAmountMinor: Halalas, share: RoscaShare): Halalas {
    val scaled = multiplyMoneyByInt(shareAmountMinor, share.numerator)
    if (scaled % share.denominator != 0L) throw RoscaError(uiText(TextKey.ROSCA_HALF_UNEVEN))
    return scaled / share.denominator
}

/** قيمة الدور المقترحة لما الأسئلة اللي قبلها تتجاوب — null لو ما بتتقسمش بالظبط أو لسه ناقص. */
fun suggestedRoscaPayout(d: RoscaDraft): Halalas? {
    val count = d.cycleCount ?: return null
    val amount = d.shareAmountMinor ?: return null
    val share = d.share ?: return null
    val turns = d.myTurns?.takeIf { it.isNotEmpty() } ?: return null
    return defaultRoscaPayout(roscaContributionOf(amount, share), count, turns.size)
}

data class RoscaPrompt(
    val question: RoscaQuestion,
    val text: String,
    val hint: String?,
    /** للأسئلة اللي ليها اختيارات بس. */
    val choices: List<String>,
    /** الاختيار أو القيمة المقترحة، لو فيه. */
    val suggested: String?,
)

fun roscaPrompt(d: RoscaDraft, q: RoscaQuestion): RoscaPrompt = when (q) {
    RoscaQuestion.NAME -> RoscaPrompt(q, uiText(TextKey.ROSCA_Q_NAME), uiText(TextKey.ROSCA_Q_NAME_HINT), emptyList(), null)
    RoscaQuestion.TURNS_COUNT -> RoscaPrompt(q, uiText(TextKey.ROSCA_Q_TURNS_COUNT), uiText(TextKey.ROSCA_Q_TURNS_COUNT_HINT), emptyList(), null)
    RoscaQuestion.SHARE_AMOUNT -> RoscaPrompt(q, uiText(TextKey.ROSCA_Q_SHARE_AMOUNT), uiText(TextKey.ROSCA_Q_SHARE_AMOUNT_HINT), emptyList(), null)
    RoscaQuestion.FREQUENCY -> RoscaPrompt(
        q, uiText(TextKey.ROSCA_Q_FREQUENCY), null, RoscaFrequency.entries.map { uiText(it.labelKey) }, uiText(RoscaFrequency.MONTHLY.labelKey),
    )
    RoscaQuestion.FIRST_DATE -> RoscaPrompt(q, uiText(TextKey.ROSCA_Q_FIRST_DATE), null, emptyList(), null)
    RoscaQuestion.SHARE -> RoscaPrompt(q, uiText(TextKey.ROSCA_Q_SHARE), null, RoscaShare.entries.map { uiText(it.labelKey) }, uiText(RoscaShare.ONE.labelKey))
    RoscaQuestion.MY_TURN -> RoscaPrompt(
        q, uiText(TextKey.ROSCA_Q_MY_TURN),
        if ((d.share ?: RoscaShare.ONE).turns > 1) uiText(TextKey.ROSCA_TURNS_FOR_SHARE, (d.share ?: RoscaShare.ONE).turns.toString()) else null,
        (1..(d.cycleCount ?: 0)).map { it.toString() } + uiText(TextKey.ROSCA_TURN_UNKNOWN_CHOICE), null,
    )
    RoscaQuestion.PAYOUT -> {
        val suggested = suggestedRoscaPayout(d)
        RoscaPrompt(
            q, uiText(TextKey.ROSCA_Q_PAYOUT),
            suggested?.let { uiText(TextKey.ROSCA_Q_PAYOUT_HINT, formatMoney(it, d.currency)) } ?: uiText(TextKey.ROSCA_PAYOUT_UNEVEN),
            emptyList(), suggested?.let { formatMoney(it, d.currency) },
        )
    }
}

/** الإجابة بتتفحص وبتمسح اللي بعدها لو بقى متعارض (مثلًا عدد الأدوار قل عن رقم دورك). */
fun applyRoscaAnswer(d: RoscaDraft, a: RoscaAnswer): RoscaDraft = when (a) {
    is RoscaAnswer.Name -> {
        val clean = JsText.collapseWhitespace(JsText.trim(a.value))
        if (clean.isEmpty() || clean.length > ROSCA_NAME_MAX) throw RoscaError(uiText(TextKey.ROSCA_NAME_LENGTH, ROSCA_NAME_MAX.toString()))
        d.copy(name = clean)
    }
    is RoscaAnswer.TurnsCount -> {
        if (a.value !in 2..ROSCA_MAX_CYCLES) throw RoscaError(uiText(TextKey.ROSCA_CYCLE_COUNT, ROSCA_MAX_CYCLES.toString()))
        val turnsStillFit = d.myTurns?.all { it <= a.value } ?: true
        d.copy(cycleCount = a.value, myTurns = if (turnsStillFit) d.myTurns else null, payoutMinor = null)
    }
    is RoscaAnswer.ShareAmount -> {
        assertHalalas(a.minor)
        if (a.minor <= 0) throw RoscaError(uiText(TextKey.DUE_INSTALLMENT_POSITIVE))
        d.share?.let { roscaContributionOf(a.minor, it) }
        d.copy(shareAmountMinor = a.minor, payoutMinor = null)
    }
    is RoscaAnswer.Frequency -> d.copy(frequency = a.value)
    is RoscaAnswer.FirstDate -> {
        if (!isValidIsoDate(a.value)) throw RoscaError(uiText(TextKey.DUE_BAD_FIRST_DATE))
        d.copy(firstDueAt = a.value)
    }
    is RoscaAnswer.Share -> {
        d.shareAmountMinor?.let { roscaContributionOf(it, a.value) }
        val turnsStillFit = d.myTurns?.let { it.isEmpty() || it.size == a.value.turns } ?: true
        d.copy(share = a.value, myTurns = if (turnsStillFit) d.myTurns else null, payoutMinor = null)
    }
    is RoscaAnswer.MyTurns -> {
        val count = d.cycleCount ?: throw RoscaError(uiText(TextKey.ROSCA_DRAFT_INCOMPLETE))
        val needed = (d.share ?: RoscaShare.ONE).turns
        if (a.value.isNotEmpty()) {
            if (a.value.size != needed) throw RoscaError(uiText(TextKey.ROSCA_TURNS_FOR_SHARE, needed.toString()))
            if (a.value.toSet().size != a.value.size || a.value.any { it !in 1..count }) throw RoscaError(uiText(TextKey.ROSCA_TURNS, count.toString()))
        }
        d.copy(myTurns = a.value.sorted(), payoutMinor = if (a.value.isEmpty()) 0 else null)
    }
    is RoscaAnswer.Payout -> {
        val value = a.minor ?: suggestedRoscaPayout(d) ?: throw RoscaError(uiText(TextKey.ROSCA_PAYOUT_UNEVEN))
        assertHalalas(value)
        if (value <= 0) throw RoscaError(uiText(TextKey.ROSCA_PAYOUT_POSITIVE))
        d.copy(payoutMinor = value)
    }
}

/** الجمعية من المسودة لما كل الأسئلة تتجاوب — ونفس فحص [checkRosca] كمان. */
fun roscaFromDraft(d: RoscaDraft, id: Id, createdAt: String): Rosca {
    if (nextRoscaQuestion(d) != null) throw RoscaError(uiText(TextKey.ROSCA_DRAFT_INCOMPLETE))
    val frequency = d.frequency!!
    val r = Rosca(
        id = id, name = d.name!!, currency = d.currency,
        contributionMinor = roscaContributionOf(d.shareAmountMinor!!, d.share!!),
        every = frequency.every, firstDueAt = d.firstDueAt!!, cycleCount = d.cycleCount!!,
        myTurns = d.myTurns!!, payoutMinor = if (d.myTurns.isEmpty()) 0 else d.payoutMinor!!, createdAt = createdAt, unit = frequency.unit,
    )
    checkRosca(r)
    return r
}
