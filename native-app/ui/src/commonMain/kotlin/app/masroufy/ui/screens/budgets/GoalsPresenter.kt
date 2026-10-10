package app.masroufy.ui.screens.budgets

import app.masroufy.core.Currency
import app.masroufy.core.GOAL_PACE_MIN_DAYS
import app.masroufy.core.GoalProgress
import app.masroufy.core.GoalState
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.absMoney
import app.masroufy.core.dayMonth
import app.masroufy.core.daysBetween
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.amount
import app.masroufy.ui.text.t

/**
 * مقدِّم «خطط الادخار» (`SavingsGoals` + تفاصيل الخطة): كل رقم من `LoadGoalsOverview` ([GoalProgress] — المدّخر · المفروض اليوم · قدام/ورا ·
 * المطلوب شهريًا · «ستصل إلى») وأي `null` بيبان «غير متاح». الحاجة الوحيدة اللي بتتحسب هنا **نسبة الشريط** (المدّخر ÷ الهدف ×١٠٠ لتحت —
 * `GoalProgress` مالهاش نسبة: missingLogic)، و«جديدة» = عدد الأيام من البداية أقل من ٣٠ (`GOAL_PACE_MIN_DAYS`).
 */
enum class StatTone { PLAIN, GOOD, BAD, WARN, MUTED }

data class GoalStat(val label: String, val value: String, val tone: StatTone)

data class GoalCardUi(
    val id: String,
    val name: String,
    val sub: String,
    val untilText: String,
    val starred: Boolean,
    val chip: String?,
    val chipTone: Tone,
    val savedMinor: Halalas?,
    val targetMinor: Halalas,
    val currency: Currency,
    val percent: Int?,
    val reached: Boolean,
    val linked: Boolean,
    /** اسم الحساب المربوط (برقمه الأخير لو موجود) — null للخطة اليدوية. */
    val walletLabel: String?,
    val stats: List<GoalStat>,
    val ofText: String,
    val progress: GoalProgress,
)

data class ArchivedUi(val id: String, val name: String, val text: String)

data class GoalsUi(val active: List<GoalCardUi>, val archived: List<ArchivedUi>)

suspend fun loadGoals(deps: BudgetsDeps, today: IsoDate, wallets: List<Wallet>): GoalsUi =
    mapGoals(deps.goalsOverview.load(today, includeArchived = true), today, wallets)

fun mapGoals(list: List<GoalProgress>, today: IsoDate, wallets: List<Wallet>): GoalsUi {
    val byId = wallets.associateBy { it.id }
    val (archived, active) = list.partition { it.goal.archived }
    return GoalsUi(
        active = active.map { goalCard(it, today, byId) },
        archived = archived.map { p ->
            val saved = p.savedMinor?.let { amount(it, p.goal.currency) } ?: t(TextKey.NOT_AVAILABLE)
            ArchivedUi(p.goal.id, p.goal.name, t(TextKey.GOALS_ARCHIVED_ROW, saved, amount(p.goal.targetMinor, p.goal.currency)))
        },
    )
}

/** «٣١ ديسمبر ٢٠٢٦». */
fun longDate(date: IsoDate): String = t(TextKey.GOAL_EDIT_DATE_VALUE, dayMonth(date), sentenceNumber(parseIsoDate(date).year))

/** نسبة الشريط: المدّخر ÷ الهدف ×١٠٠ لتحت، بين ٠ و١٠٠ — null لو المدّخر مش معروف. عرض بس (أعداد صحيحة). */
fun goalPercent(saved: Halalas?, target: Halalas): Int? {
    if (saved == null || target <= 0) return null
    return (saved.coerceAtLeast(0) * 100 / target).coerceIn(0, 100).toInt()
}

/** اسم الحساب المربوط: «الاسم ••••١٢٣٤» (آخر أربعة بس — CLAUDE.md #11) أو «حساب مربوط» لو في بلد تانية. */
fun walletLabel(wallet: Wallet?): String = when {
    wallet == null -> t(TextKey.GOALS_LINKED_OTHER)
    wallet.accountLast4 != null -> t(TextKey.GOALS_WALLET_LAST4, wallet.name, wallet.accountLast4!!)
    else -> wallet.name
}

private fun goalCard(p: GoalProgress, today: IsoDate, wallets: Map<String, Wallet>): GoalCardUi {
    val g = p.goal
    val linked = !g.manual
    val wallet = if (linked) walletLabel(wallets[g.linkedWalletId]) else null
    val until = longDate(g.targetDate)
    val young = today >= g.startDate && daysBetween(g.startDate, today) < GOAL_PACE_MIN_DAYS
    val (chip, tone) = when (p.state) {
        GoalState.UNKNOWN -> t(TextKey.NOT_AVAILABLE) to Tone.MUTED
        GoalState.REACHED -> t(TextKey.GOALS_CHIP_REACHED) to Tone.OK
        GoalState.OVERDUE -> t(TextKey.GOALS_CHIP_OVERDUE) to Tone.OVER
        GoalState.NOT_STARTED -> t(TextKey.GOALS_CHIP_NEW) to Tone.NEW
        GoalState.PACE_UNKNOWN -> null to Tone.MUTED
        GoalState.ON_TRACK -> (if (young) t(TextKey.GOALS_CHIP_NEW) to Tone.NEW else t(TextKey.GOALS_CHIP_AHEAD) to Tone.OK)
        GoalState.BEHIND -> (if (young) t(TextKey.GOALS_CHIP_NEW) to Tone.NEW else t(TextKey.GOALS_CHIP_BEHIND) to Tone.OVER)
    }
    val percent = goalPercent(p.savedMinor, g.targetMinor)
    val target = amount(g.targetMinor, g.currency)
    return GoalCardUi(
        id = g.id,
        name = g.name,
        sub = t(TextKey.GOALS_SUB_UNTIL, until, wallet ?: t(TextKey.GOALS_MANUAL)),
        untilText = until,
        starred = g.starred,
        chip = chip,
        chipTone = tone,
        savedMinor = p.savedMinor,
        targetMinor = g.targetMinor,
        currency = g.currency,
        percent = percent,
        reached = p.state == GoalState.REACHED,
        linked = linked,
        walletLabel = wallet,
        stats = stats(p, young),
        ofText = if (percent == null) t(TextKey.GOALS_OF_ONLY, target) else t(TextKey.GOALS_OF, target, sentenceNumber(percent)),
        progress = p,
    )
}

private fun money(minor: Halalas?, currency: Currency): String = minor?.let { amount(absMoney(it), currency) } ?: t(TextKey.NOT_AVAILABLE)

/** الأربع أرقام (٢×٢): المفروض اليوم · قدام/ورا · المطلوب شهريًا · «ستصل إلى» (بعد ٣٠ يوم بس). */
private fun stats(p: GoalProgress, young: Boolean): List<GoalStat> {
    val c = p.goal.currency
    val na = p.savedMinor == null
    val ahead = p.aheadMinor
    val aheadStat = when {
        na || ahead == null -> GoalStat(t(TextKey.GOALS_STAT_AHEAD), t(TextKey.NOT_AVAILABLE), StatTone.MUTED)
        ahead > 0 -> GoalStat(t(TextKey.GOALS_STAT_AHEAD), money(ahead, c), StatTone.GOOD)
        ahead < 0 -> GoalStat(t(TextKey.GOALS_STAT_BEHIND), money(ahead, c), StatTone.BAD)
        else -> GoalStat(t(TextKey.GOALS_STAT_ON_TIME), money(ahead, c), StatTone.PLAIN)
    }
    val projected = p.projectedAtTargetMinor
    val reach = when {
        na -> GoalStat(t(TextKey.GOALS_STAT_REACH), t(TextKey.NOT_AVAILABLE), StatTone.MUTED)
        projected != null -> GoalStat(t(TextKey.GOALS_STAT_REACH), money(projected, c), if (projected < p.goal.targetMinor) StatTone.WARN else StatTone.PLAIN)
        young && (p.state == GoalState.ON_TRACK || p.state == GoalState.BEHIND) -> GoalStat(t(TextKey.GOALS_STAT_REACH), t(TextKey.GOALS_STAT_AFTER_30), StatTone.PLAIN)
        else -> GoalStat(t(TextKey.GOALS_STAT_REACH), t(TextKey.NOT_AVAILABLE), StatTone.MUTED)
    }
    return listOf(
        GoalStat(t(TextKey.GOALS_STAT_EXPECTED), money(p.expectedMinor, c), if (p.expectedMinor == null) StatTone.MUTED else StatTone.PLAIN),
        aheadStat,
        GoalStat(t(TextKey.GOALS_STAT_REQUIRED), money(p.requiredPerMonthMinor, c), if (p.requiredPerMonthMinor == null) StatTone.MUTED else StatTone.PLAIN),
        reach,
    )
}

/** سطر «المدّخر» تحت الرقم في تفاصيل الخطة: «٦١٪، بقي X» · «اكتملت!» · «رصيد الحساب غير معروف». */
fun detailProgressLine(card: GoalCardUi): String = when {
    card.savedMinor == null || card.percent == null -> t(TextKey.GOAL_DETAIL_UNKNOWN)
    card.reached -> t(TextKey.GOAL_DETAIL_REACHED, sentenceNumber(card.percent))
    else -> t(TextKey.GOAL_DETAIL_PROGRESS, sentenceNumber(card.percent), amountLabel(card.progress.remainingMinor, card.currency))
}
