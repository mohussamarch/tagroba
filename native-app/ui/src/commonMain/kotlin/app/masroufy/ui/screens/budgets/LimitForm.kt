package app.masroufy.ui.screens.budgets

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.TextKey
import app.masroufy.core.normalizeDigits
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.text.amount
import app.masroufy.ui.text.t

/**
 * خانات لوحة السقف (`BudgetLimitSheet` — السقف · التنبيه شغال؟ · 70/80/90 أو نسبة تانية) والتحقق منها **قبل** ما تروح لـ`SetBudget`
 * (اللي بيتحقق تاني بنفسه — `BUDGET_LIMIT_POSITIVE` · `BUDGET_THRESHOLD_RANGE`). الكتابة بالأرقام العربي أو اللاتيني (`tryParseMoney`
 * من `core` — من غير أي كسر عشري). مفيش حساب فلوس هنا.
 */
data class LimitDraft(
    val limitText: String,
    val alertOn: Boolean,
    /** 70 · 80 · 90 — null لما النسبة مكتوبة في «أخرى». */
    val preset: Int?,
    val customText: String,
) {
    companion object {
        val PRESETS = listOf(70, 80, 90)
        private const val DEFAULT_PRESET = 80

        /** من السقف الحالي: سقف موجود ⇒ مبلغه ونسبته · مفيش ⇒ فاضي والتنبيه شغال على 80 (زي النموذج). */
        fun from(current: LimitCurrent, currency: Currency): LimitDraft {
            val pct = current.thresholdPercent
            val standard = pct != null && pct in PRESETS
            return LimitDraft(
                limitText = current.limitMinor?.let { inputText(it, currency) } ?: "",
                alertOn = if (current.limitMinor == null) true else current.notify && pct != null,
                preset = if (standard || pct == null) pct ?: DEFAULT_PRESET else null,
                customText = if (!standard && pct != null) pct.toString() else "",
            )
        }
    }
}

/** نتيجة التحقق: سليم بالقيم اللي هتتحفظ، أو غلط بسبب جنب كل خانة. */
sealed interface LimitCheck {
    data class Ok(val limitMinor: Halalas, val thresholdPercent: Int?, val notify: Boolean) : LimitCheck

    data class Bad(val limitError: String?, val percentError: String?) : LimitCheck
}

/** المبلغ في الخانة من غير فواصل، والكسر صفر ⇒ من غير «.00» («1200»). عرض بس. */
fun inputText(minor: Halalas, currency: Currency): String = amount(minor, currency).replace(",", "").removeSuffix(".00")

/** النسبة المكتوبة في «أخرى»: عدد صحيح من 1 لـ100 (`SetBudget.validateThreshold`). فاضية ⇒ null. */
fun customPercent(text: String): Int? {
    val s = normalizeDigits(text).trim()
    if (s.isEmpty() || s.any { it !in '0'..'9' } || s.length > 3) return null
    return s.toInt().takeIf { it in 1..100 }
}

fun checkLimit(draft: LimitDraft, currency: Currency): LimitCheck {
    val limit = tryParseMoney(draft.limitText, currency)?.takeIf { it > 0 }
    val custom = draft.customText.isNotBlank()
    val percent = if (custom) customPercent(draft.customText) else draft.preset
    val limitError = if (limit == null) t(TextKey.BUDGET_LIMIT_POSITIVE) else null
    val percentError = if (draft.alertOn && percent == null) t(TextKey.BUDGET_THRESHOLD_RANGE) else null
    if (limitError != null || percentError != null) return LimitCheck.Bad(limitError, percentError)
    // التنبيه مقفول ⇒ النسبة بتتحفظ للمرة الجاية بس ما بتنبّهش (`notifyEnabled = false`)
    return LimitCheck.Ok(limit!!, percent, notify = draft.alertOn)
}
