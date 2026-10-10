package app.masroufy.usecase

import app.masroufy.core.AssistEntityType
import app.masroufy.core.AssistScreen
import app.masroufy.core.DueFlow
import app.masroufy.core.ScreenLink
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.core.shiftDays
import app.masroufy.core.countsAsSalaried
import app.masroufy.core.daysBetween
import app.masroufy.core.nextPayDate
import app.masroufy.core.sourcesActiveOn

/**
 * «إمتى الراتب؟»: مصدر راتب شغال في البلد دي ⇒ ميعاده الجاي (من نفس دالة مصادر الدخل). مفيش راتب (مصر مثلًا) ⇒ بيقول كده بصراحة ويعرض
 * دخل البلد دي بمواعيدها (اليوم المجهول «غير متاح»).
 */
internal suspend fun AnswerKit.nextSalary(): AssistReply {
    val all = s.incomeSources?.list() ?: return na()
    val active = sourcesActiveOn(all, ctx.today)
    val b = rb()
    val salaried = active.firstOrNull(::countsAsSalaried)
    if (salaried != null) {
        val next = nextPayDate(salaried, ctx.today, payday)
        when {
            next == null -> b.line(TextKey.ASSIST_SALARY_DAY_NA)
            next == ctx.today -> b.line(TextKey.ASSIST_SALARY_TODAY)
            else -> b.line(TextKey.ASSIST_NEXT_SALARY, assistDate(next, ctx.today), assistDays(daysBetween(ctx.today, next)))
        }
    } else {
        b.line(TextKey.ASSIST_NO_SALARY, ctx.space.name)
        if (active.isNotEmpty()) {
            val items = active.take(3).map { src -> "${src.name} ${nextPayDate(src, ctx.today, payday)?.let { assistDate(it, ctx.today) } ?: uiText(TextKey.ASSIST_NA)}" }
            b.line(TextKey.ASSIST_SPACE_INCOME, ctx.space.name, assistList(items))
        }
    }
    b.link(ScreenLink.of(AssistScreen.INCOME_SOURCES))
    return b.build()
}

/** المبلغ المتوقع المتخزن (مصدر الراتب أو الملف) — **عمره ما بيتحسب متوسط من الإيداعات**. */
internal suspend fun AnswerKit.salaryAmount(): AssistReply {
    val sources = s.incomeSources?.list().orEmpty()
    val stored = sourcesActiveOn(sources, ctx.today).firstOrNull(::countsAsSalaried)?.expectedMinor ?: profile?.salaryMinor
    val b = rb()
    if (stored == null) b.line(TextKey.ASSIST_SALARY_NA) else b.line(TextKey.ASSIST_SALARY_AMOUNT, b.money(stored))
    b.link(ScreenLink.of(AssistScreen.INCOME_SOURCES))
    return b.build()
}

internal suspend fun AnswerKit.lastAtMerchant(): AssistReply {
    val ent = subject(AssistEntityType.MERCHANT) ?: return na()
    val merchant = lexicon.merchants.firstOrNull { it.id == ent.id } ?: return na()
    val finder = s.lastAt ?: return na()
    val b = rb()
    val last = finder.find(merchant, ctx.today)
    if (last == null) {
        b.line(TextKey.ASSIST_LAST_NONE, quoted(merchant.displayName))
        b.link(ScreenLink.of(AssistScreen.MERCHANT, "merchantId" to merchant.id))
    } else {
        val t = last.transaction
        val wallet = last.wallet?.let { uiText(TextKey.ASSIST_FROM_WALLET, it.name) }.orEmpty()
        b.line(TextKey.ASSIST_LAST_AT, quoted(merchant.displayName), b.money(t.amountMinor, t.currency), assistDate(t.occurredAt.take(10), ctx.today), wallet)
        b.link(ScreenLink.of(AssistScreen.OPERATION_DETAIL, "transactionId" to t.id))
    }
    return b.build()
}

/** الخطة بالاسم ⇒ اللي عليها النجمة (§68) ⇒ الأولى. رصيد مجهول ⇒ «غير متاح». خطة من غير تاريخ ما ليهاش «في الشهر». */
internal suspend fun AnswerKit.goalProgress(): AssistReply {
    val list = s.goals?.load(ctx.today) ?: return na()
    val named = subject(AssistEntityType.GOAL)?.id
    val g = list.firstOrNull { it.goal.id == named } ?: list.firstOrNull { it.goal.starred } ?: list.firstOrNull()
        ?: return na(TextKey.ASSIST_NO_GOALS, ScreenLink.of(AssistScreen.SAVINGS_GOALS))
    val b = rb()
    val c = g.goal.currency
    val saved = g.savedMinor
    if (saved == null) {
        b.line(TextKey.ASSIST_GOAL_NA, quoted(g.goal.name))
    } else {
        b.line(TextKey.ASSIST_GOAL, quoted(g.goal.name), b.money(saved, c), b.money(g.goal.targetMinor, c))
        g.aheadMinor?.let { if (it > 0) b.line(TextKey.ASSIST_GOAL_AHEAD, b.money(it, c)) else if (it < 0) b.line(TextKey.ASSIST_GOAL_BEHIND, b.money(-it, c)) }
        g.requiredPerMonthMinor?.takeIf { it > 0 }?.let { b.line(TextKey.ASSIST_GOAL_PER_MONTH, b.money(it, c)) }
    }
    b.link(ScreenLink.of(AssistScreen.GOAL_DETAIL, "goalId" to g.goal.id))
    return b.build()
}

/** الزكاة مخفية ⇒ مكانها؛ مفيش حول ⇒ اقتراح بدايته؛ الأسعار ناقصة ⇒ «غير متاح». المبلغ والمدفوع من حالتي الاستخدام زي ما هم. */
internal suspend fun AnswerKit.zakat(): AssistReply {
    val z = s.zakat ?: return na()
    if (!z.visible()) return na(TextKey.ASSIST_ZAKAT_HIDDEN, ScreenLink.of(AssistScreen.ISLAMIC_CONTENT))
    val b = rb()
    val prices = s.zakatPrices
    val year = z.openYear()
    if (year == null) {
        val sug = prices?.let { z.suggestDate(ctx.today, it) }
        if (sug == null) b.line(TextKey.ASSIST_ZAKAT_NO_YEAR) else b.line(TextKey.ASSIST_ZAKAT_SUGGEST, assistDate(sug.hawlStart, ctx.today), assistDate(sug.dueAt, ctx.today))
        b.link(ScreenLink.of(AssistScreen.ZAKAT))
        return b.build()
    }
    val due = prices?.let { z.assess(year.id, ctx.today, it) }?.dueMinor
    if (due == null) b.line(TextKey.ASSIST_ZAKAT_NA, assistDate(year.dueAt, ctx.today)) else b.line(TextKey.ASSIST_ZAKAT_DUE, b.money(due, year.currency), assistDate(year.dueAt, ctx.today))
    s.payZakat?.status(year.id)?.takeIf { it.paidMinor > 0 }?.let { b.line(TextKey.ASSIST_ZAKAT_PAID, b.money(it.paidMinor, year.currency), b.money(it.remainingMinor, year.currency)) }
    b.link(ScreenLink.of(AssistScreen.ZAKAT))
    return b.build()
}

/** قبل الراتب (من ملخص التقويم) + أقرب ٣ مستحقات عليك في ٣٠ يوم بالترتيب. */
internal suspend fun AnswerKit.duesUpcoming(): AssistReply {
    val b = rb()
    val outlook = s.calendar?.summary(ctx.today)?.untilPayday
    outlook?.let { o ->
        b.line(TextKey.ASSIST_DUES_UNTIL_PAYDAY, assistDate(o.nextPayday, ctx.today), b.money(o.knownTotalMinor), o.knownCount.toString())
        if (o.unknownAmountCount > 0) b.line(TextKey.ASSIST_DUES_UNKNOWN_AMOUNT, o.unknownAmountCount.toString())
    }
    val items = s.dues?.dueItems(ctx.today, shiftDays(ctx.today, 30))?.filter { it.flow == DueFlow.PAY && it.dueAt >= ctx.today }?.sortedBy { it.dueAt }
    if (outlook == null && items == null) return na()
    if (items.isNullOrEmpty()) {
        if (outlook == null) b.line(TextKey.ASSIST_DUES_NONE)
    } else {
        b.line(TextKey.ASSIST_DUES_LIST, assistList(items.take(3).map { "${it.title} ${b.money(it.amountMinor, it.currency)} ${assistDate(it.dueAt, ctx.today)}" }))
    }
    b.link(ScreenLink.of(AssistScreen.CALENDAR))
    b.link(ScreenLink.of(AssistScreen.DUES))
    return b.build()
}

/** الفواتير والاشتراكات بمبلغها المتوقع وميعادها **زي ما هم متخزنين** (مفيش إعادة حساب). */
internal fun AnswerKit.bills(): AssistReply {
    val b = rb()
    val named = subject(AssistEntityType.RECURRING)?.let { e -> lexicon.recurring.firstOrNull { it.id == e.id } }
    if (named != null) {
        b.line(TextKey.ASSIST_BILL, quoted(named.name), b.money(named.expectedMinor, named.currency), assistDate(named.nextDueAt, ctx.today))
        b.link(ScreenLink.of(AssistScreen.SUBSCRIPTION_DETAIL, "recurringId" to named.id))
        return b.build()
    }
    val active = lexicon.recurring.filter { it.active }.sortedBy { it.nextDueAt }
    if (active.isEmpty()) b.line(TextKey.ASSIST_NO_BILLS)
    else b.line(TextKey.ASSIST_BILLS_LIST, assistList(active.take(4).map { "${it.name} ${b.money(it.expectedMinor, it.currency)} ${assistDate(it.nextDueAt, ctx.today)}" }))
    b.link(ScreenLink.of(AssistScreen.SUBSCRIPTIONS))
    return b.build()
}

internal suspend fun AnswerKit.pendingReview(): AssistReply {
    val review = home(current)?.needsReviewCount
    val sms = s.sms?.takeIf { it.available }?.waiting()?.messageIds?.size
    if (review == null && sms == null) return na()
    val b = rb()
    when {
        (review ?: 0) == 0 && (sms ?: 0) == 0 -> b.line(TextKey.ASSIST_PENDING_NONE)
        sms != null -> b.line(TextKey.ASSIST_PENDING, sms.toString(), (review ?: 0).toString())
        else -> b.line(TextKey.ASSIST_PENDING_REVIEW, review.toString())
    }
    if ((sms ?: 0) > 0) b.link(ScreenLink.of(AssistScreen.BANK_SMS))
    b.link(ScreenLink.of(AssistScreen.REVIEW_QUEUE))
    return b.build()
}

internal suspend fun AnswerKit.assets(): AssistReply {
    val pv = s.assets?.listPortfolio(ctx.today) ?: return na()
    val b = rb()
    val value = pv.totals.marketValueMinor
    when {
        pv.rows.isEmpty() -> b.line(TextKey.ASSIST_NO_ASSETS)
        value == null -> b.line(TextKey.ASSIST_ASSETS_NA)
        else -> {
            b.line(TextKey.ASSIST_ASSETS, b.money(value))
            if (pv.totals.assetsWithoutPrice > 0) b.line(TextKey.ASSIST_ASSETS_PARTIAL, pv.totals.assetsWithoutPrice.toString())
        }
    }
    val asset = subject(AssistEntityType.ASSET)
    b.link(if (asset != null) ScreenLink.of(AssistScreen.ASSET_DETAIL, "assetId" to asset.id) else ScreenLink.of(AssistScreen.INVESTMENT))
    return b.build()
}

/** صرف الحدث بعملته؛ النقوط معلومة بس ومش بتتطرح (§64). */
internal suspend fun AnswerKit.eventSpend(): AssistReply {
    val ent = subject(AssistEntityType.EVENT) ?: return na(TextKey.ASSIST_WHICH_EVENT, ScreenLink.of(AssistScreen.EVENTS))
    val d = s.events?.detail(ent.id) ?: return na()
    val b = rb()
    val spent = d.summary.totals.filter { it.spentMinor > 0 }.map { b.money(it.spentMinor, it.currency) }
    if (spent.isEmpty()) b.line(TextKey.ASSIST_EVENT_NONE, quoted(d.event.name))
    else b.line(TextKey.ASSIST_EVENT, quoted(d.event.name), assistList(spent), assistTimes(d.summary.spendCount))
    b.link(ScreenLink.of(AssistScreen.EVENT_DETAIL, "eventId" to ent.id))
    return b.build()
}

internal suspend fun AnswerKit.projectSpend(): AssistReply {
    val ent = subject(AssistEntityType.PROJECT) ?: return na(TextKey.ASSIST_WHICH_PROJECT, ScreenLink.of(AssistScreen.PROJECTS))
    val d = s.projects?.detail(ent.id) ?: return na()
    val b = rb()
    b.line(TextKey.ASSIST_PROJECT, quoted(d.project.name), b.money(d.summary.spentMinor), assistTimes(d.summary.count))
    if (d.summary.needsReviewCount > 0) b.approx(TextKey.ASSIST_PARTIAL, d.summary.needsReviewCount.toString())
    b.link(ScreenLink.of(AssistScreen.PROJECT_DETAIL, "projectId" to ent.id))
    return b.build()
}

internal suspend fun AnswerKit.rosca(): AssistReply {
    val src = s.roscas ?: return na()
    val list = src.list(ctx.today)
    val named = subject(AssistEntityType.ROSCA)?.id
    val r = list.firstOrNull { it.rosca.id == named } ?: list.firstOrNull() ?: return na(TextKey.ASSIST_NO_ROSCAS, ScreenLink.of(AssistScreen.ROSCAS))
    val b = rb()
    val c = r.status.contributions
    b.line(TextKey.ASSIST_ROSCA, quoted(r.rosca.name), c.paidCount.toString(), c.count.toString())
    c.nextDueAt?.let { b.line(TextKey.ASSIST_NEXT_PAYMENT, b.money(c.nextAmountMinor), assistDate(it, ctx.today)) }
    src.forecast(r.rosca.id).payoutDates.firstOrNull { it >= ctx.today }?.let { b.line(TextKey.ASSIST_ROSCA_PAYOUT, assistDate(it, ctx.today)) }
    b.link(ScreenLink.of(AssistScreen.ROSCA_DETAIL, "roscaId" to r.rosca.id))
    return b.build()
}

internal suspend fun AnswerKit.installments(): AssistReply {
    val list = s.installments?.list(ctx.today) ?: return na()
    val named = subject(AssistEntityType.PLAN)?.id
    val picked = list.filter { named == null || it.plan.id == named }.filter { it.progress.remainingMinor > 0 }
    val b = rb()
    if (picked.isEmpty()) return na(TextKey.ASSIST_NO_INSTALLMENTS, ScreenLink.of(AssistScreen.INSTALLMENTS))
    picked.take(3).forEach { v ->
        val p = v.progress
        b.line(TextKey.ASSIST_INSTALLMENT, quoted(v.plan.name), b.money(p.remainingMinor), (p.count - p.paidCount).toString(), p.count.toString())
        p.nextDueAt?.let { b.line(TextKey.ASSIST_NEXT_PAYMENT, b.money(p.nextAmountMinor), assistDate(it, ctx.today)) }
    }
    b.link(if (picked.size == 1) ScreenLink.of(AssistScreen.INSTALLMENT_DETAIL, "planId" to picked[0].plan.id) else ScreenLink.of(AssistScreen.INSTALLMENTS))
    return b.build()
}

/** المناسبات خلال ٣٠ يوم (§67). */
internal suspend fun AnswerKit.occasions(): AssistReply {
    val list = s.occasions?.upcoming(ctx.today) ?: return na()
    val soon = list.filter { it.date <= shiftDays(ctx.today, 30) }
    val b = rb()
    if (soon.isEmpty()) b.line(TextKey.ASSIST_NO_OCCASIONS)
    else b.line(TextKey.ASSIST_OCCASIONS, assistList(soon.take(4).map { "${it.personName ?: it.occasion.label.orEmpty()} ${assistDate(it.date, ctx.today)}" }))
    b.link(ScreenLink.of(AssistScreen.EVENTS))
    return b.build()
}
