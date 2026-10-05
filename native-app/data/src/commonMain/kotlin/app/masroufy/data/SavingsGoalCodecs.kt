package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.GOAL_CONTRIBUTIONS_GROUP
import app.masroufy.core.GoalContribution
import app.masroufy.core.SAVINGS_GOALS_GROUP
import app.masroufy.core.SavingsGoal

/**
 * خطط الادخار وإيداعاتها (المساعد المالي §68) — مجموعتين جداد على مستوى الحساب، في كوتلن بس (التطبيق الحالي ما يعرفهمش).
 * تاريخ البداية والهدف بيتكتبوا `startedAt` و`deadline` (النسخة الشاملة بتفحصهم كتواريخ أصلًا). الاختياري ما بيتكتبش،
 * وقواعد فايربيز (`users/{uid}/{document=**}`) بتغطيهم ⇒ مفيش نشر.
 */
object SavingsGoalCodecs {
    val savingsGoals: DocCodec<SavingsGoal> = codec(
        SAVINGS_GOALS_GROUP, { it.id },
        { g ->
            doc {
                req("id", g.id); req("name", g.name); req("targetMinor", g.targetMinor); req("currency", g.currency.name)
                req("startedAt", g.startDate); req("deadline", g.targetDate); opt("linkedWalletId", g.linkedWalletId); opt("linkedSpaceId", g.linkedSpaceId)
                req("archived", g.archived); req("createdAt", g.createdAt); req("updatedAt", g.updatedAt)
            }
        },
        { d ->
            SavingsGoal(
                d.str("id"), d.str("name"), d.long("targetMinor"), d.wire("currency", Currency::valueOf), d.str("startedAt"), d.str("deadline"),
                d.strOrNull("linkedWalletId"), d.strOrNull("linkedSpaceId"), d.bool("archived"), d.str("createdAt"), d.str("updatedAt"),
            )
        },
    )

    val goalContributions: DocCodec<GoalContribution> = codec(
        GOAL_CONTRIBUTIONS_GROUP, { it.id },
        { c ->
            doc {
                req("id", c.id); req("goalId", c.goalId); req("date", c.date); req("amountMinor", c.amountMinor); req("createdAt", c.createdAt)
                opt("note", c.note)
            }
        },
        { d -> GoalContribution(d.str("id"), d.str("goalId"), d.str("date"), d.long("amountMinor"), d.str("createdAt"), d.strOrNull("note")) },
    )
}
