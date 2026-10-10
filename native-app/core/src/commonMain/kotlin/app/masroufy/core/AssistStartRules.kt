package app.masroufy.core

/**
 * «أمور لم تُنجزها بعد» أول المحادثة (آخر §76 على فرع التصميم + ردود المالك 2026-10-09) — **كارت واحد لكل نوع** (الأهم منه)، أقصى ٤،
 * بترتيب ثابت: رسايل بنك مستنية تأكيد · فاتورة دورية عدّى يومها الشهر ده ومتسجلتش · تصنيف وصل نسبة التنبيه ولسه ما عدّاش السقف ·
 * دين ليك ميعاده فات.
 * - **الدين المتأخر معلومة بس من غير زرار** (رد المالك ٣ — مفيش «ذكّره»).
 * - **الإشعار الممسوح من الجرس بيشيل كارته** (رد المالك ٣): الكارت اللي ليه موضوع تنبيه ([StartItem.alertThreadKey]) ممسوح ⇒ ما بيظهرش.
 *   (الكروت التلاتة التانية بتعمل نفس الحكاية — المقترح؛ سؤال المالك ٢ لسه مفتوح.)
 * - **«×» على الكارت بيقفله** حتى لو الحاجة ما اتعملتش (النافذة التانية ٢) — علامة على الحساب بمفتاح الكارت **بالحاجة نفسها** (الفاتورة
 *   بميعادها · الدين بقسطه) ⇒ كارت الشهر الجاي بيظهر عادي. كله اتقفل ⇒ «مفيش حاجة مستنياك دلوقتي» (مش «خلّصت كل حاجة»).
 * - الكروت بتختفي لوحدها لما الحاجة تتعمل — مفيش «اتعمل» متخزن.
 */
const val MAX_START_ITEMS = 4

enum class StartItemKind { SMS_WAITING, BILL_UNRECORDED, CATEGORY_AT_THRESHOLD, DEBT_OVERDUE }

/** فعل الكارت: يفتح شاشة («راجعها» · «افتحها») · يسأل جوه المحادثة («سجّلها») · ولا حاجة (الدين المتأخر — معلومة بس). */
sealed interface StartAction {
    data class OpenScreen(val link: ScreenLink, val label: String) : StartAction

    data class AskInChat(val chip: ChipRef, val label: String) : StartAction
}

data class StartItem(
    /** مفتاح الكارت بالحاجة نفسها — «×» عليه ما بيقفلش الحاجة الجاية بعده. */
    val key: String,
    val kind: StartItemKind,
    val text: String,
    val action: StartAction?,
    /** موضوع التنبيه في الجرس اللي بيقول نفس الحاجة (لو موجود). */
    val alertThreadKey: String?,
)

/**
 * الكروت اللي هتظهر: واحد لكل نوع (أول مرشح من النوع — المستدعي بيرتّب جوه النوع بالأهم)، بالترتيب الثابت، من غير المقفول بـ«×»
 * ولا اللي إشعاره اتمسح من الجرس، وأقصى ٤. [anyHidden] = فيه كارت كان هيظهر واتقفل (للنص «مفيش حاجة مستنياك دلوقتي»).
 */
data class StartPick(val items: List<StartItem>, val anyHidden: Boolean)

fun pickStartItems(candidates: List<StartItem>, closedCards: Set<String>, dismissedThreads: Set<String>): StartPick {
    val byKind = StartItemKind.entries.mapNotNull { kind ->
        val ofKind = candidates.filter { it.kind == kind }
        val visible = ofKind.firstOrNull { it.key !in closedCards && (it.alertThreadKey == null || it.alertThreadKey !in dismissedThreads) }
        Triple(kind, visible, ofKind.isNotEmpty()).takeIf { it.third }
    }
    val shown = byKind.mapNotNull { it.second }.take(MAX_START_ITEMS)
    return StartPick(shown, anyHidden = byKind.any { it.second == null })
}

/**
 * الفاتورة الدورية اللي يومها عدّى **الشهر ده** ولسه ما اتسجلتش (التصميم + اختيار Claude في «اتسجلت» — المالك يقدر يغيّره): نوعها
 * فاتورة وشغالة · ميعادها الجاي قبل النهارده وجوه الشهر المالي الحالي · ومفيش عملية خارجة لنفس المحل (`recurringKey`) من أسبوع قبل
 * الميعاد لحد النهارده (نفس سماحية ±٧ أيام في اكتشاف الاشتراكات). ⚠️ «الميعاد الجاي» ما بيتحركش لوحده لما الخصم يوصل (§75-٧ لسه
 * ما اتبناش) ⇒ الدفعة نفسها هي اللي بتقول «اتسجلت».
 */
fun billUnrecorded(item: RecurringItem, rows: List<Transaction>, today: IsoDate, period: Period): Boolean {
    if (!item.active || item.kind != "bill" || item.nextDueAt >= today || item.nextDueAt < period.start) return false
    val from = addDaysIso(item.nextDueAt, -7)
    return rows.none {
        it.observedDirection == Direction.OUT && it.currency == item.currency && recurringKey(it) == item.merchantKey &&
            it.occurredAt.take(10) >= from && it.occurredAt.take(10) <= today
    }
}

/** آخر دفعة للفاتورة الدورية (خارجة، نفس المحل والعملة). */
fun lastPaymentOf(item: RecurringItem, rows: List<Transaction>): Transaction? = rows
    .filter { it.observedDirection == Direction.OUT && it.currency == item.currency && recurringKey(it) == item.merchantKey }
    .maxWithOrNull(compareBy<Transaction> { it.occurredAt }.thenBy { it.sourceOrder })

/**
 * محفظة الفاتورة الدورية = محفظة آخر دفعة ليها (رد المالك في النافذة التانية ١: «الفاتورة الدورية على محفظتها المعتادة»). مفيش حقل
 * محفظة في `recurringItems` (التطبيق القديم بيكتبها كمان) ⇒ بتتحسب ومش بتتخزن. عمرها ما اتدفعت ⇒ null ⇒ المحفظة الأساسية.
 */
fun usualWalletOf(item: RecurringItem, rows: List<Transaction>): Id? = rows
    .filter { it.observedDirection == Direction.OUT && it.currency == item.currency && recurringKey(it) == item.merchantKey && it.walletId != null }
    .maxWithOrNull(compareBy<Transaction> { it.occurredAt }.thenBy { it.sourceOrder })?.walletId

/** تصنيف آخر دفعة للفاتورة (للكارت) — null لو مالهاش. */
fun usualCategoryOf(item: RecurringItem, rows: List<Transaction>): Id? = rows
    .filter { it.observedDirection == Direction.OUT && it.currency == item.currency && recurringKey(it) == item.merchantKey && it.categoryId != null }
    .maxWithOrNull(compareBy<Transaction> { it.occurredAt }.thenBy { it.sourceOrder })?.categoryId

private fun spaced(base: String, spaceId: String) = if (spaceId == DEFAULT_SPACE_ID) base else "$spaceId:$base"

/** موضوع تنبيه المستحق (`dueAlertCandidates` + `inSpace`) بالظبط — عشان مسحه من الجرس يشيل كارته. */
fun dueAlertThread(item: DueItem, spaceId: String): String =
    spaced("due|${item.source.wire}|${item.sourceId}|${item.dueAt}|${item.flow.wire}", spaceId)

/** موضوع تنبيه الفاتورة الدورية في ميعادها. */
fun billAlertThread(recurringId: Id, dueAt: IsoDate, spaceId: String): String =
    spaced("due|${DueSource.RECURRING.wire}|$recurringId|$dueAt|${DueFlow.PAY.wire}", spaceId)

/** موضوع تنبيه العتبة/السقف للتصنيف (`budgetAlertCandidates`). */
fun budgetAlertThread(periodStart: IsoDate, categoryId: Id, spaceId: String): String = spaced("budget|$periodStart|cat:$categoryId", spaceId)

/** موضوع تنبيه رسايل البنك المستنية (`smsConfirmCandidate` — على مستوى الحساب، أحدث رسالة مستنية). */
fun smsAlertThread(newestWaitingId: String): String = "sms-confirm|${hashContent(newestWaitingId)}"
