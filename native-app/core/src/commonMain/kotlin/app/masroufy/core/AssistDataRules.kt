package app.masroufy.core

/**
 * أسئلة البيانات — قواعد بالترتيب، **أول واحدة بتنطبق هي الإجابة**. الترتيب من الأخص للأعم: «إيه اللي عليّا قبل المرتب؟» مستحقات مش
 * «عليّا فلوس» · «باقي كم يوم على الراتب» ميعاد مرتب مش «فاضلي كام» · «فاضل كام قسط» أقساط · وهكذا. الاسم الأساسي ([AssistEntity]) بيرجع
 * مع النية — الإجابة نفسها في طبقة الاستخدامات (الرقم من حالة الاستخدام، ولا رقم بيتألف هنا).
 */
private typealias Topic = Pair<AssistIntent, AssistEntity?>

internal fun dataTopicOf(s: AssistSignals, ctx: AssistUnderstandContext): Topic? {
    val w = AssistWords
    val person = s.entities.ofType(AssistEntityType.PERSON).firstOrNull() ?: subjectPerson(ctx)
    val category = s.entity(AssistEntityType.CATEGORY)
    val merchant = s.specific(AssistEntityType.MERCHANT)
    // «فين/وين» سؤال عن مكان ⇒ تنقل مش بيانات («فين الزكاة؟» ⇒ الشاشة · «إمتى الزكاة؟» ⇒ الرقم)
    val where = s.has(w.WHERE)
    val asking = s.has(w.HOW_MUCH) || (s.question && !where)

    if (s.has(w.DUES) && (asking || s.has(w.DUE_CUES)) && !s.has(w.ROSCA)) return AssistIntent.DUES_UPCOMING to null
    if (s.has(w.SALARY) && s.has(w.WHEN)) return AssistIntent.NEXT_SALARY to null
    if (s.has(w.SALARY) && asking && !s.has(w.SPEND)) return AssistIntent.SALARY_AMOUNT to null
    s.specific(AssistEntityType.PLAN)?.takeIf { asking || s.has(w.INSTALLMENT_Q) }?.let { return AssistIntent.INSTALLMENTS to it }
    if (s.has(w.INSTALLMENT) && s.has(w.INSTALLMENT_Q) && !s.has(w.ROSCA)) return AssistIntent.INSTALLMENTS to null
    val rosca = s.specific(AssistEntityType.ROSCA)
    if ((rosca != null || s.has(w.ROSCA)) && s.has(w.ROSCA_Q)) return AssistIntent.ROSCA to rosca
    val goal = s.specific(AssistEntityType.GOAL)
    if ((goal != null || s.has(w.GOAL)) && (s.has(w.PROGRESS) || asking)) return AssistIntent.GOAL_PROGRESS to goal
    if (s.has(w.ZAKAT) && (asking || s.has(w.WHEN)) && !s.has(w.PAY_VERB)) return AssistIntent.ZAKAT to null
    if (s.has(w.OCCASIONS) && (asking || s.has(w.UPCOMING))) return AssistIntent.OCCASIONS to null
    if (s.has(w.PENDING) && asking) return AssistIntent.PENDING_REVIEW to null
    if (s.has(w.OVERDUE) && !s.has(w.SALARY)) return AssistIntent.DEBTS_OVERDUE to person
    if (person != null && s.has(w.PERSON_OWE) && (asking || s.has(w.OWED_TO_ME) || s.has(w.I_OWE))) return AssistIntent.PERSON_BALANCE to person
    if (s.has(w.OWED_TO_ME)) return AssistIntent.OWED_TO_ME to null
    if (s.has(w.I_OWE)) return AssistIntent.I_OWE to null
    if (s.has(w.CASH) && (s.has(w.HOW_MUCH) || s.has(w.SPEND) || s.has(w.ON_HAND))) return AssistIntent.CASH_ON_HAND to null
    if (s.has(w.ON_HAND) && asking) return AssistIntent.ON_HAND to s.specific(AssistEntityType.WALLET)
    if (s.has(w.COMPARE) && (s.period?.kind == AssistPeriodKind.PREVIOUS_FISCAL || s.has(vocab("compare", "قارن", "مقارنه")))) {
        return AssistIntent.SPEND_COMPARE to category
    }
    if (s.has(w.FORECAST) && (s.has(w.SPEND) || s.has(vocab("هوصل", "اوصل", "توقع")) || asking)) return AssistIntent.FORECAST to null
    if (s.has(w.PER_DAY) && (s.has(w.CAN_SPEND) || s.has(w.SPEND) || asking)) return AssistIntent.DAILY_ALLOWANCE to null
    if (category != null && s.has(w.BUDGET) && (asking || s.has(w.REMAINING) || s.has(vocab("وصل", "وصلت", fuzzy = false)))) return AssistIntent.CATEGORY_BUDGET to category
    if (s.has(w.ON_PLAN) && goal == null) return AssistIntent.BUDGET_STATUS to null
    if (s.has(w.REMAINING) && (asking || s.has(w.BUDGET))) return AssistIntent.REMAINING to null
    val recurring = s.specific(AssistEntityType.RECURRING)
    if ((recurring != null || s.has(w.BILLS)) && (asking || s.has(w.WHEN) || s.has(vocab("عاده", "عادتا", "usually", fuzzy = false)))) return AssistIntent.BILLS to recurring
    s.specific(AssistEntityType.ASSET)?.takeIf { asking || s.has(w.VALUE) }?.let { return AssistIntent.ASSETS to it }
    if (s.has(w.ASSETS) && (asking || s.has(w.VALUE))) return AssistIntent.ASSETS to null
    s.specific(AssistEntityType.EVENT)?.takeIf { asking || s.has(w.SPEND) }?.let { return AssistIntent.EVENT_SPEND to it }
    s.specific(AssistEntityType.PROJECT)?.takeIf { asking || s.has(w.SPEND) }?.let { return AssistIntent.PROJECT_SPEND to it }
    if (s.has(w.LAST)) return AssistIntent.LAST_AT_MERCHANT to merchant
    if (s.has(w.MOST) && (s.has(w.SPEND) || s.has(w.WHAT_ON)) && merchant == null) return AssistIntent.SPEND_BIGGEST to null
    if (s.has(w.INCOME) && (asking || s.period != null)) return AssistIntent.INCOME to null
    val gives = s.has(w.GIVE)
    if (person != null && s.entities.ofType(AssistEntityType.PERSON).isNotEmpty() && (s.has(w.SPEND) || gives) && (asking || gives)) {
        return AssistIntent.SPEND_PERSON to person
    }
    if (merchant != null && (s.has(w.SPEND) || asking || s.has(w.PAY_VERB))) return AssistIntent.SPEND_MERCHANT to merchant
    if (category != null && (s.has(w.SPEND) || asking || (s.tokens.size <= 2 && !s.has(w.BUDGET)))) return AssistIntent.SPEND_CATEGORY to category
    if (s.has(w.SPEND) && (asking || s.period != null)) return AssistIntent.SPEND_TOTAL to null
    return null
}

/** الشخص اللي شاشته مفتوحة («عليه كام؟» في ملف الشخص). */
private fun subjectPerson(ctx: AssistUnderstandContext): AssistEntity? {
    val id = ctx.subjectPersonId ?: return null
    val p = ctx.lexicon.people.firstOrNull { it.id == id } ?: return null
    return AssistEntity(AssistEntityType.PERSON, p.id, p.name, -1, -1)
}
