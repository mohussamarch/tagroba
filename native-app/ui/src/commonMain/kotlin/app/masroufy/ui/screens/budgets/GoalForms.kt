package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.GoalProgress
import app.masroufy.core.GoalState
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.jsTrim
import app.masroufy.core.TextKey
import app.masroufy.core.addMonthsClamped
import app.masroufy.core.parseIsoDate
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.GoalInput

/**
 * خانات «خطة جديدة» و«تعديل الخطة» والتحقق منها قبل `ManageSavingsGoals.create`/`edit` (اللي بيتحقق تاني — `checkSavingsGoal`).
 * التواريخ عدّ أيام وشهور بالتقويم (`addMonthsClamped`) — مفيش فلوس بتتحسب هنا.
 * ⚠️ «بلا موعد» (رد المالك 2026-10-09: «ينفع بلا موعد») **مش هنا**: `SavingsGoal.targetDate` لسه مطلوب في كوتلن (missingLogic).
 */
enum class GoalWhen { YEAR_END, ONE_YEAR, TWO_YEARS }

enum class GoalKind { MANUAL, LINKED }

/** تاريخ «حتى متى؟» من النهارده: آخر السنة (لو النهارده آخر يوم فيها ⇒ آخر السنة الجاية) · بعد سنة · بعد سنتين. */
fun goalDate(choice: GoalWhen, today: IsoDate): IsoDate = when (choice) {
    GoalWhen.YEAR_END -> {
        val year = parseIsoDate(today).year
        val end = "$year-12-31"
        if (today < end) end else "${year + 1}-12-31"
    }
    GoalWhen.ONE_YEAR -> addMonthsClamped(today, 12)
    GoalWhen.TWO_YEARS -> addMonthsClamped(today, 24)
}

data class GoalNewDraft(
    val name: String = "",
    val targetText: String = "",
    val whenChoice: GoalWhen = GoalWhen.YEAR_END,
    val kind: GoalKind = GoalKind.MANUAL,
    val walletId: String? = null,
)

/** نتيجة التحقق: المُدخل جاهز لحالة الاستخدام، أو رسالة الغلط (أول غلط بالترتيب زي النموذج). */
sealed interface GoalCheck {
    data class Ok(val input: GoalInput) : GoalCheck

    data class Bad(val message: String) : GoalCheck
}

private fun cleanName(raw: String): String = jsTrim(raw).replace(SPACES, " ")

private val SPACES = Regex("\\s+")

fun checkNewGoal(d: GoalNewDraft, today: IsoDate, currency: Currency, spaceId: String): GoalCheck {
    val name = cleanName(d.name)
    if (name.isEmpty()) return GoalCheck.Bad(t(TextKey.GOAL_NAME_REQUIRED))
    val target = tryParseMoney(d.targetText, currency)?.takeIf { it > 0 } ?: return GoalCheck.Bad(t(TextKey.GOAL_TARGET_POSITIVE))
    if (d.kind == GoalKind.LINKED && d.walletId == null) return GoalCheck.Bad(t(UiKey.GOAL_NEW_PICK_WALLET))
    val linked = d.kind == GoalKind.LINKED
    return GoalCheck.Ok(
        GoalInput(
            name = name, targetMinor = target, currency = currency, startDate = today, targetDate = goalDate(d.whenChoice, today),
            linkedWalletId = if (linked) d.walletId else null, linkedSpaceId = if (linked) spaceId else null,
        ),
    )
}

data class GoalEditDraft(val name: String, val targetText: String, val date: IsoDate, val archived: Boolean) {
    companion object {
        fun from(p: GoalProgress): GoalEditDraft =
            GoalEditDraft(p.goal.name, inputText(p.goal.targetMinor, p.goal.currency), p.goal.targetDate, p.goal.archived)
    }
}

/** اللي اتغيّر في «تعديل الخطة»: البيانات (اسم · مبلغ · موعد ⇒ `edit`) والأرشفة (⇒ `archive`). */
data class GoalEditChange(val input: GoalInput?, val archived: Boolean?)

sealed interface GoalEditCheck {
    data object Unchanged : GoalEditCheck

    data class Ok(val change: GoalEditChange) : GoalEditCheck

    data class Bad(val message: String) : GoalEditCheck
}

fun checkGoalEdit(d: GoalEditDraft, p: GoalProgress, today: IsoDate): GoalEditCheck {
    val g = p.goal
    val name = cleanName(d.name)
    val target = tryParseMoney(d.targetText, g.currency)
    val dataChanged = name != g.name || target != g.targetMinor || d.date != g.targetDate
    val archiveChanged = d.archived != g.archived
    if (!dataChanged && !archiveChanged) return GoalEditCheck.Unchanged
    if (name.isEmpty()) return GoalEditCheck.Bad(t(TextKey.GOAL_NAME_REQUIRED))
    if (target == null || target <= 0) return GoalEditCheck.Bad(t(TextKey.GOAL_TARGET_POSITIVE))
    if (dataChanged && d.date <= today) return GoalEditCheck.Bad(t(UiKey.GOAL_EDIT_DATE_AFTER))
    val input = if (!dataChanged) null else GoalInput(name, target, g.currency, g.startDate, d.date, g.linkedWalletId, g.linkedSpaceId)
    return GoalEditCheck.Ok(GoalEditChange(input, if (archiveChanged) d.archived else null))
}

/**
 * سطر «تحتاج X شهريًا» في «تعديل الخطة»: من `GoalProgress.requiredPerMonthMinor` **للخطة زي ما هي متخزنة** — لو المبلغ أو الموعد اتغيّروا
 * الرقم الجديد بيتحسب بعد الحفظ (مفيش حالة استخدام للمعاينة قبل الحفظ — missingLogic) ⇒ «غير متاح» بصراحة بدل رقم مخترع.
 */
fun needLines(d: GoalEditDraft, p: GoalProgress): Pair<String, String> {
    val g = p.goal
    val changed = tryParseMoney(d.targetText, g.currency) != g.targetMinor || d.date != g.targetDate
    val required: Halalas? = p.requiredPerMonthMinor
    return when {
        p.savedMinor == null -> t(UiKey.GOAL_EDIT_NEED_NA) to t(UiKey.GOAL_EDIT_NEED_NA_NOTE)
        changed -> t(UiKey.GOAL_EDIT_NEED_NA) to t(UiKey.GOAL_EDIT_NEED_AFTER_SAVE)
        p.state == GoalState.REACHED -> t(UiKey.GOAL_EDIT_COVERED) to t(UiKey.GOAL_EDIT_SUB_SAVED, amountLabel(p.savedMinor, g.currency))
        required != null -> t(UiKey.GOAL_EDIT_NEED, amountLabel(required, g.currency)) to
            t(UiKey.GOAL_EDIT_NEED_NOTE, amountLabel(p.remainingMinor, g.currency), longDate(g.targetDate))
        else -> t(UiKey.GOAL_EDIT_NEED_NA) to t(UiKey.GOAL_EDIT_NEED_NA_NOTE)
    }
}
