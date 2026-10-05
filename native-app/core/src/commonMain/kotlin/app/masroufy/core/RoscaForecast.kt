package app.masroufy.core

/**
 * تحليل الجمعية بعد إنشائها — طلب المالك 2026-09-30 (OVERRIDES §50): «تظهرله تحليلات هيقبض إمتى وهيدفع إيه».
 * **من الجمعية نفسها بس** (مش من الكشف)، فبيشتغل قبل أول قسط. اللي اتدفع فعلًا بيبان في [roscaStatus].
 */
data class RoscaCycleRow(
    val number: Int,
    val dueAt: IsoDate,
    val payMinor: Halalas,
    val receiveMinor: Halalas,
    /** موقفك بعد الدور ده: موجب = الجمعية شايلالك، سالب = عليك. */
    val positionAfterMinor: Halalas,
)

data class RoscaForecast(
    val rows: List<RoscaCycleRow>,
    val totalPayMinor: Halalas,
    /** null = دورك لسه ما اتحددش. */
    val totalReceiveMinor: Halalas?,
    val gainMinor: Halalas?,
    val payoutDates: List<IsoDate>,
    val lastDueAt: IsoDate,
    /** أقساط هتدفعها **قبل** أول قبض — دي الفترة اللي الجمعية بتحوّشلك فيها. */
    val paymentsBeforePayout: Int?,
    /** أقساط هتفضل تدفعها **بعد** آخر قبض — دي الفترة اللي إنت فيها مديون للجمعية. */
    val paymentsAfterPayout: Int?,
    /** أكبر مبلغ الجمعية بتبقى شايلاهولك. */
    val peakSavedMinor: Halalas,
    /** أكبر مبلغ بتبقى مديون بيه للجمعية. */
    val peakOwedMinor: Halalas,
)

fun roscaForecast(r: Rosca): RoscaForecast {
    checkRosca(r)
    val schedule = contributionSchedule(r)
    val turns = r.myTurns.toSet()
    var paid = 0L
    var received = 0L
    var peakSaved = 0L
    var peakOwed = 0L
    val rows = (1..r.cycleCount).map { n ->
        val pay = installmentAmountOf(schedule, n)
        val receive = if (n in turns) r.payoutMinor else 0L
        paid = addMoney(paid, pay)
        received = addMoney(received, receive)
        val position = subtractMoney(paid, received)
        peakSaved = maxOf(peakSaved, position)
        peakOwed = maxOf(peakOwed, -position)
        RoscaCycleRow(n, dueDateOf(schedule, n), pay, receive, position)
    }
    val known = r.myTurns.isNotEmpty()
    return RoscaForecast(
        rows = rows,
        totalPayMinor = paid,
        totalReceiveMinor = if (known) received else null,
        gainMinor = if (known) subtractMoney(received, paid) else null,
        payoutDates = r.myTurns.sorted().map { dueDateOf(schedule, it) },
        lastDueAt = dueDateOf(schedule, r.cycleCount),
        paymentsBeforePayout = if (known) r.myTurns.min() - 1 else null,
        paymentsAfterPayout = if (known) r.cycleCount - r.myTurns.max() else null,
        peakSavedMinor = peakSaved,
        peakOwedMinor = peakOwed,
    )
}
