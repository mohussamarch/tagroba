package app.masroufy.usecase

import app.masroufy.core.AssistEntityType
import app.masroufy.core.AssistScreen
import app.masroufy.core.BudgetLevel
import app.masroufy.core.DueFlow
import app.masroufy.core.DueSource
import app.masroufy.core.Id
import app.masroufy.core.PeopleSection
import app.masroufy.core.ScreenLink
import app.masroufy.core.TextKey
import app.masroufy.core.assistPeriodLabel
import app.masroufy.core.uiText
import app.masroufy.core.shiftDays
import app.masroufy.core.daysBetween
import app.masroufy.core.walletBalancesOn

/** «فاضلي كام؟» (سؤال المالك ٧): سقف إجمالي ⇒ المتبقي منه + المتاح في اليوم؛ من غيره ⇒ «فاضلك تقريبًا» من الرئيسية؛ الاتنين مجهولين ⇒ «غير متاح». */
internal suspend fun AnswerKit.remaining(): AssistReply {
    val b = rb()
    val bd = budget(current)
    val st = bd?.totalStatus
    if (st != null) {
        if (st.remainingMinor >= 0) b.line(TextKey.ASSIST_REMAINING_CAP, b.money(st.remainingMinor), b.money(st.limitMinor))
        else b.line(TextKey.ASSIST_OVER_CAP, b.money(st.limitMinor), b.money(-st.remainingMinor))
        val allowance = home(current)?.allowance
        allowance?.amountMinor?.takeIf { st.remainingMinor > 0 }?.let { b.line(TextKey.ASSIST_PER_DAY, b.money(it), assistDays(allowance.remainingDays)) }
        if (!bd.spentReliable) b.approx(null)
    } else {
        val lo = s.leftover?.load(ctx.today, s.unreconciledWalletIds)
        val left = lo?.leftoverMinor
        if (lo == null || left == null) return na(TextKey.ASSIST_REMAINING_NA, ScreenLink.of(AssistScreen.BUDGETS))
        b.line(TextKey.ASSIST_LEFTOVER, b.money(left), lo.until?.let { assistDate(it, ctx.today) } ?: uiText(TextKey.ASSIST_PERIOD_END))
        if (lo.approximate) b.approx(null)
    }
    b.link(ScreenLink.of(AssistScreen.BUDGETS))
    return b.build()
}

internal suspend fun AnswerKit.dailyAllowance(): AssistReply {
    val a = home(current)?.allowance ?: return na()
    val b = rb()
    val amount = a.amountMinor
    if (amount == null) b.line(TextKey.ASSIST_ALLOWANCE_NA, a.reason) else b.line(TextKey.ASSIST_ALLOWANCE, b.money(amount), assistDays(a.remainingDays))
    if (amount != null && a.approximate) b.approx(null)
    b.link(ScreenLink.of(AssistScreen.BUDGETS))
    return b.build()
}

/** التوقع من الرئيسية زي ما هو، وقصوره مكتوب دايمًا؛ بدري ⇒ نص «بدري على التوقع» بالحرف. */
internal suspend fun AnswerKit.forecast(): AssistReply {
    val f = home(current)?.forecast ?: return na()
    val b = rb()
    f.projectedMinor?.let { b.line(TextKey.ASSIST_FORECAST, b.money(it)) }
    b.lines += f.caveat
    b.link(ScreenLink.of(AssistScreen.HOME))
    return b.build()
}

internal suspend fun AnswerKit.budgetStatus(): AssistReply {
    val bd = budget(current) ?: return na()
    val st = bd.totalStatus ?: return na(TextKey.ASSIST_NO_BUDGET, ScreenLink.of(AssistScreen.BUDGETS))
    val b = rb()
    val key = when (st.level) {
        BudgetLevel.UNDER -> TextKey.ASSIST_BUDGET_UNDER
        BudgetLevel.NEAR -> TextKey.ASSIST_BUDGET_NEAR
        BudgetLevel.OVER -> TextKey.ASSIST_BUDGET_OVER
    }
    b.line(key, b.money(st.spentMinor), b.money(st.limitMinor))
    val names = bd.categories.associate { it.id to it.name }
    val hot = bd.lines.filter { it.status?.thresholdCrossed == true }.mapNotNull { names[it.categoryId] }
    if (hot.isNotEmpty()) b.line(TextKey.ASSIST_AT_THRESHOLD, assistList(hot.take(3)))
    if (!bd.spentReliable) b.approx(null)
    b.link(ScreenLink.of(AssistScreen.BUDGETS))
    return b.build()
}

internal suspend fun AnswerKit.categoryBudget(): AssistReply {
    val cat = subject(AssistEntityType.CATEGORY) ?: return budgetStatus()
    val link = ScreenLink.of(AssistScreen.CATEGORY_BUDGET, "categoryId" to cat.id)
    val bd = budget(current) ?: return na()
    val name = quoted(categoryName(cat.id))
    val st = bd.lines.firstOrNull { it.categoryId == cat.id }?.status
    val b = rb()
    when {
        st == null -> b.line(TextKey.ASSIST_NO_CATEGORY_CAP, name)
        st.remainingMinor >= 0 -> b.line(TextKey.ASSIST_CATEGORY_CAP, name, b.money(st.spentMinor), b.money(st.limitMinor), b.money(st.remainingMinor))
        else -> b.line(TextKey.ASSIST_CATEGORY_CAP_OVER, name, b.money(st.spentMinor), b.money(st.limitMinor), b.money(-st.remainingMinor))
    }
    b.link(link)
    return b.build()
}

/** محفظة الكاش: مفيش ⇒ «لا توجد محفظة كاش» (عمره ما بيقول صفر). */
internal suspend fun AnswerKit.cashOnHand(): AssistReply {
    val src = s.cash ?: return na()
    val c = src.load(current, ctx.today) ?: return na(TextKey.ASSIST_NO_CASH, ScreenLink.of(AssistScreen.WALLETS))
    val b = rb()
    b.line(TextKey.ASSIST_CASH, b.money(c.balanceMinor, c.wallet.currency), b.money(c.spentInPeriodMinor, c.wallet.currency), assistPeriodLabel(null))
    b.link(ScreenLink.of(AssistScreen.CASH_DETAILS))
    return b.build()
}

/** «معك الآن» من الرئيسية؛ محفظة بالاسم ⇒ نفس دالة المحافظ. أي رصيد مجهول ⇒ الإجمالي «غير متاح». */
internal suspend fun AnswerKit.onHand(): AssistReply {
    val b = rb()
    val named = subject(AssistEntityType.WALLET)?.let { e -> lexicon.wallets.firstOrNull { it.id == e.id } }
    if (named != null) {
        val rows = s.txns.listByDateRange(named.openingAt, ctx.today)
        val bal = walletBalancesOn(listOf(named), rows, ctx.today)[named.id]
        if (bal == null) b.line(TextKey.ASSIST_WALLET_NA, quoted(named.name)) else b.line(TextKey.ASSIST_WALLET_BALANCE, quoted(named.name), b.money(bal, named.currency))
        if (bal != null && named.id in s.unreconciledWalletIds) b.approx(null)
        b.link(ScreenLink.of(AssistScreen.WALLET_DETAIL, "walletId" to named.id))
        return b.build()
    }
    val lo = s.leftover?.load(ctx.today, s.unreconciledWalletIds) ?: return na()
    val on = lo.onHandMinor ?: return na(TextKey.ASSIST_ON_HAND_NA, ScreenLink.of(AssistScreen.WALLETS))
    b.line(TextKey.ASSIST_ON_HAND, b.money(on))
    if (lo.approximate) b.approx(null)
    b.link(ScreenLink.of(AssistScreen.WALLETS))
    return b.build()
}

/** «ليك/عليك» عند الناس: إجمالي لكل (بلد · عملة) جنب بعض — مفيش صافي ولا جمع بين عملتين (§64 · §67) + أول ٣ أسامي. */
private suspend fun AnswerKit.peopleSide(section: PeopleSection, has: TextKey, none: TextKey, screen: AssistScreen): AssistReply {
    val ov = s.people?.acrossSpaces(ctx.today) ?: return na()
    val b = rb()
    val totals = ov.totals.mapNotNull { t ->
        val v = if (section == PeopleSection.OWED_TO_YOU) t.owedToYouMinor else t.youOweMinor
        if (v > 0) b.money(v, t.currency) else null
    }
    val ids = ov.sections.firstOrNull { it.section == section }?.personIds.orEmpty()
    val names = ids.mapNotNull { id -> ov.rows.firstOrNull { it.person.id == id }?.person?.name }.take(3)
    if (totals.isEmpty()) b.line(none) else b.line(has, assistList(totals), assistList(names))
    b.link(ScreenLink.of(screen))
    return b.build()
}

internal suspend fun AnswerKit.owedToMe() = peopleSide(PeopleSection.OWED_TO_YOU, TextKey.ASSIST_OWED_TO_ME, TextKey.ASSIST_OWED_NONE, AssistScreen.OWED_TO_YOU)

internal suspend fun AnswerKit.iOwe() = peopleSide(PeopleSection.YOU_OWE, TextKey.ASSIST_I_OWE, TextKey.ASSIST_I_OWE_NONE, AssistScreen.YOU_OWE)

/** سطور الدين مع شخص من [PersonAcrossSpaces] — ليك وعليك منفصلين. بيرجع true لو فيه أي دين. */
internal suspend fun AnswerKit.personLines(b: ReplyBuilder, personId: Id, name: String): Boolean {
    val debts = s.personAcross?.debts(personId) ?: return false
    var any = false
    debts.forEach { d ->
        if (d.receivableMinor > 0) { b.line(TextKey.ASSIST_PERSON_OWES_ME, name, b.money(d.receivableMinor, d.currency)); any = true }
        if (d.payableLoanMinor > 0) { b.line(TextKey.ASSIST_I_OWE_PERSON, name, b.money(d.payableLoanMinor, d.currency)); any = true }
        if (d.payableCustodyMinor > 0) { b.line(TextKey.ASSIST_CUSTODY, name, b.money(d.payableCustodyMinor, d.currency)); any = true }
    }
    return any
}

internal suspend fun AnswerKit.personBalance(): AssistReply {
    val pid = subject(AssistEntityType.PERSON)?.id ?: ctx.subjectPersonId ?: return na(null, ScreenLink.of(AssistScreen.PEOPLE))
    val name = lexicon.people.firstOrNull { it.id == pid }?.name ?: subject(AssistEntityType.PERSON)?.name.orEmpty()
    if (s.personAcross == null) return na()
    val b = rb()
    if (!personLines(b, pid, name)) b.line(TextKey.ASSIST_PERSON_NONE, name)
    b.link(ScreenLink.of(AssistScreen.PERSON_PROFILE, "personId" to pid))
    return b.build()
}

/** الديون اللي ليك وميعادها فات — **معلومة بس** (رد المالك ٣: مفيش «ذكّره» ولا رسالة تذكير). */
internal suspend fun AnswerKit.debtsOverdue(): AssistReply {
    val dues = s.dues ?: return na()
    val late = dues.dueItems(shiftDays(ctx.today, -3650), ctx.today)
        .filter { it.flow == DueFlow.RECEIVE && it.source == DueSource.DEBT && it.dueAt < ctx.today }.sortedBy { it.dueAt }
    val b = rb()
    if (late.isEmpty()) {
        b.line(TextKey.ASSIST_OVERDUE_NONE)
    } else {
        val items = late.take(3).map { uiText(TextKey.ASSIST_OVERDUE_ITEM, it.title, b.money(it.amountMinor, it.currency), assistDays(daysBetween(it.dueAt, ctx.today))) }
        b.line(TextKey.ASSIST_OVERDUE, assistList(items))
        b.link(ScreenLink.of(AssistScreen.DEBT_DETAIL, "obligationId" to late.first().sourceId))
    }
    b.link(ScreenLink.of(AssistScreen.DEBTS))
    return b.build()
}
