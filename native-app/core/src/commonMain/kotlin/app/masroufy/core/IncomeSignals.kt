package app.masroufy.core

/**
 * مين بيحوّل المرتب، والمرتب المتأخر (OVERRIDES §48 · §64) — دوال نقية.
 *
 * **اللي البرنامج بيتعلمه: مين اللي حوّل بس** — عشان يبطّل يسأل. **عمره ما بيستنتج زيادة أو خصم من المبلغ** (قرار المالك §48):
 * المبلغ ما بيدخلش في أي قرار هنا — لا في السؤال، ولا في النسبة للمصدر، ولا في «وصل ولا لأ».
 */

/** إيداع شكله مرتب: داخل، ونوعه «مرتب» أو لسه ما اتحددش (المستخدم ما قالش إنه حاجة تانية). */
fun isSalaryLike(t: Transaction): Boolean =
    t.observedDirection == Direction.IN &&
        (t.economicKind == EconomicKind.SALARY || (!t.economicKindConfirmed && t.economicKind == EconomicKind.UNCLASSIFIED))

/** المصادر اللي ممكن نسأل عنها في يوم [day]: وظيفة أو بارت تايم شغالة بنفس العملة. */
private fun salariedActiveOn(sources: List<IncomeSource>, day: IsoDate, currency: Currency): List<IncomeSource> =
    sourcesActiveOn(sources, day).filter { it.kind in SALARIED_KINDS && it.currency == currency }

/**
 * المصدر اللي الإيداع ده **منسوب** ليه: الطرف اتأكد إنه بيحوّل مرتبه، والمصدر كان شغال يوم الإيداع وبنفس العملة.
 * الإيداع اللي المستخدم أكد إنه حاجة مش دخل (سلفة · تحويل داخلي …) ما بيتنسبش. والنقوط ومكافأة نهاية الخدمة عمرهم ما بيتنسبوا
 * (مش مرتب الشهر ده — المكافأة ما بتقفلش تنبيه «المرتب اتأخر»).
 */
fun attributedSourceId(t: Transaction, sources: List<IncomeSource>): Id? {
    if (t.observedDirection != Direction.IN || t.economicKind in NOT_IN_INCOME_AVERAGES) return null
    if (t.economicKindConfirmed && !countsAsIncome(t.economicKind)) return null
    val key = transferPartyOf(t)?.key ?: return null
    return sourcesActiveOn(sources, t.occurredAt).firstOrNull { key in it.payerKeys && it.currency == t.currency }?.id
}

/** سؤال «ده مرتب من «…»؟» على أول إيداع من طرف لسه مش معروف. */
data class PayerQuestion(val party: TransferPartyRef, val sourceId: Id, val sourceName: String, val transactionId: Id, val date: IsoDate)

/**
 * سؤال واحد لكل طرف: أول إيداع شكله مرتب من طرف **مش متأكد لأي مصدر** وقت ما فيه وظيفة أو بارت تايم شغالة.
 * لو كذا مصدر شغال: الأول اللي لسه ما اتعرفش مين بيحوّله، وبعدين الأحدث (اختيار Claude). «لأ» على مصدر ⇒ ما يتسألش عنه
 * تاني للمصدر ده (ممكن يتسأل عن مصدر تاني شغال). [skipParties] = أطراف اتقال عليها في زون التحويلات «حسابي التاني» أو «شخص».
 */
fun payerQuestions(transactions: List<Transaction>, sources: List<IncomeSource>, skipParties: Set<String> = emptySet()): List<PayerQuestion> {
    val known = sources.flatMap { it.payerKeys }.toSet()
    val asked = LinkedHashMap<String, PayerQuestion>()
    for (t in transactions.sortedWith(compareBy({ it.occurredAt }, { it.sourceOrder }))) {
        if (!isSalaryLike(t)) continue
        val party = transferPartyOf(t) ?: continue
        if (party.key in known || party.key in skipParties || party.key in asked) continue
        val source = salariedActiveOn(sources, t.occurredAt, t.currency)
            .filter { party.key !in it.declinedPayerKeys }
            .sortedWith(compareBy<IncomeSource> { it.payerKeys.isNotEmpty() }.thenByDescending { it.startedAt })
            .firstOrNull() ?: continue
        asked[party.key] = PayerQuestion(party, source.id, source.name, t.id, t.occurredAt)
    }
    return asked.values.toList()
}

fun payerQuestionText(q: PayerQuestion): String = uiText(TextKey.INCOME_Q_PAYER, q.sourceName)

/**
 * رد المالك (§64، اختياره — مش المقترح): بعد ما يأكد **مرة** إن الطرف ده بيحوّل مرتبه ⇒ **أي** إيداع منه بيتسجل «مرتب» لوحده
 * (المكافأة واسترداد مصاريف الشغل كمان). **اللي المستخدم غيّره بإيده عمره ما يتكتب فوقه**: العملية اللي نوعها متأكد ما بتتلمسش.
 * اختيارات Claude: أي مصدر عنده الطرف ده (حتى لو اتقفل — المكافأة بتيجي بعد ما تسيب) ومن غير شرط العملة. بترجع نفس العملية لو مفيش تغيير.
 */
fun applyKnownPayerSalary(t: Transaction, sources: List<IncomeSource>, nowIso: String): Transaction {
    if (t.observedDirection != Direction.IN || t.economicKindConfirmed) return t
    val key = transferPartyOf(t)?.key ?: return t
    if (sources.none { key in it.payerKeys }) return t
    return t.copy(economicKind = EconomicKind.SALARY, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = nowIso)
}

/** الرد على «ده مرتب من …؟» — المصدر بعد الرد. الرد مش بيلمس المبلغ المتوقع ولا أي رقم تاني. */
fun answerPayerQuestion(source: IncomeSource, partyKey: String, yes: Boolean): IncomeSource =
    if (yes) source.copy(payerKeys = (source.payerKeys + partyKey).distinct(), declinedPayerKeys = source.declinedPayerKeys - partyKey)
    else source.copy(declinedPayerKeys = (source.declinedPayerKeys + partyKey).distinct())

/** مهلة المرتب المتأخر: لو ما وصلش لحد اليوم المتوقع + 3 أيام ⇒ تنبيه هادي (اختيار Claude — المالك يقدر يغيّره). */
const val LATE_INCOME_GRACE_DAYS = 3

/** الإيداع اللي بيوصل لحد 15 يوم **قبل** اليوم المتوقع بيتحسب لنفس الشهر (المرتب ساعات بينزل بدري قبل إجازة) — اختيار Claude. */
const val INCOME_EARLY_WINDOW_DAYS = 15

/** آخر يوم متوقع عدّت مهلته لحد [today] (اليوم 29–31 بيتقيد بآخر يوم في الشهر). */
fun lastDueWithGracePassed(expectedDay: Int, today: IsoDate): IsoDate {
    val cutoff = addDaysIso(today, -LATE_INCOME_GRACE_DAYS)
    val (y, m, _) = parseIsoDate(cutoff)
    val thisMonth = formatIsoDate(DateParts(y, m, minOf(expectedDay, daysInMonth(y, m))))
    if (thisMonth <= cutoff) return thisMonth
    val (py, pm) = if (m == 1) (y - 1) to 12 else y to (m - 1)
    return formatIsoDate(DateParts(py, pm, minOf(expectedDay, daysInMonth(py, pm))))
}

/** مهلة القبض الأسبوعي: يومين (رد المالك 2026-10-05، §64) — الأسبوع قصير على 3 أيام. */
const val LATE_WEEKLY_GRACE_DAYS = 2

/** الإيداع اللي بيوصل لحد 3 أيام قبل يوم القبض الأسبوعي بيتحسب لنفس الأسبوع — اختيار Claude. */
const val WEEKLY_EARLY_WINDOW_DAYS = 3

/** آخر يوم قبض أسبوعي ([weekday] 1=الاتنين … 7=الحد) عدّت مهلته لحد [today]. */
fun lastWeeklyDueWithGracePassed(weekday: Int, today: IsoDate): IsoDate {
    val cutoff = addDaysIso(today, -LATE_WEEKLY_GRACE_DAYS)
    return addDaysIso(cutoff, -((isoWeekday(cutoff) - weekday).mod(7)))
}

/**
 * المرتب المتأخر: مصدر شغال النهارده، ليه يوم متوقع، **ومعروف مين بيحوّله** (من غير ده مانقدرش نقول «ما وصلش» — قاعدة 10)،
 * وما فيش إيداع منسوب ليه من [INCOME_EARLY_WINDOW_DAYS] يوم قبل اليوم المتوقع لحد النهارده. **أي مبلغ** بيحل التنبيه
 * (المبلغ مش علامة على حاجة). الموضوع = المصدر + اليوم المتوقع ⇒ الشهر الجاي موضوع جديد، واللي وصل بيختفي لوحده.
 */
fun lateIncomeCandidates(sources: List<IncomeSource>, transactions: List<Transaction>, today: IsoDate): List<AlertCandidate> =
    sourcesActiveOn(sources, today).mapNotNull { s ->
        if (s.payerKeys.isEmpty()) return@mapNotNull null
        // الأسبوعي (رد المالك §64): يومين مهلة، والإيداع اللي قبل اليوم بـ3 أيام بيتحسب لنفس الأسبوع
        val (due, early) = when (s.payFrequency) {
            PayFrequency.MONTHLY -> lastDueWithGracePassed(s.expectedDayOfMonth ?: return@mapNotNull null, today) to INCOME_EARLY_WINDOW_DAYS
            PayFrequency.WEEKLY -> lastWeeklyDueWithGracePassed(s.payWeekday ?: return@mapNotNull null, today) to WEEKLY_EARLY_WINDOW_DAYS
        }
        if (due < s.startedAt) return@mapNotNull null
        val from = addDaysIso(due, -early)
        val arrived = transactions.any { it.occurredAt in from..today && attributedSourceId(it, sources) == s.id }
        if (arrived) return@mapNotNull null
        AlertCandidate(
            kind = AlertKind.INCOME_LATE,
            threadKey = "income_late|${s.id}|$due",
            title = uiText(TextKey.ALERT_INCOME_LATE_TITLE, s.name),
            body = uiText(TextKey.ALERT_INCOME_LATE_BODY, due),
        )
    }
