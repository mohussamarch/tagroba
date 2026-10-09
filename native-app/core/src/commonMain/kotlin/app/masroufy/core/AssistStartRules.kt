package app.masroufy.core

/**
 * «أمور لم تُنجزها بعد» أول المحادثة (آخر §76 على فرع التصميم) — أقصى ٤ بالترتيب: رسائل بنك مستنية تأكيد · فاتورة متكررة عدّى يومها
 * ومتسجلتش · تصنيف وصل نسبة التنبيه ولسه ما عدّاش السقف · دين ليك ميعاده فات.
 * - **الدين المتأخر معلومة بس من غير زرار** (رد المالك ٣ — مفيش «ذكّره»).
 * - **«×» على كل كارت بيقفله** حتى لو الحاجة ما اتعملتش (الرد التاني ٢) — بيتخزن على الحساب ([ASSISTANT_DISMISSALS_GROUP]).
 * - **الإشعار الممسوح من الجرس بيشيل كارته** (رد المالك ٣) — الكارت اللي ليه موضوع تنبيه ([StartItem.alertThread]) ممسوح ⇒ ما بيظهرش.
 * - كله اتقفل ⇒ «مفيش حاجة مستنياك دلوقتي» (مش «خلّصت كل حاجة») · مفيش حاجة أصلًا ⇒ «خلّصت كل حاجة».
 */
const val MAX_START_ITEMS = 4

enum class StartItemKind { SMS_WAITING, BILL_DUE, CATEGORY_THRESHOLD, DEBT_OVERDUE }

/** فعل الكارت: راجعها (رسائل البنك) · سجّلها (جوه المحادثة) · افتحها (سقف التصنيف) · ولا حاجة (الدين المتأخر). */
enum class StartAction { REVIEW, RECORD, OPEN, NONE }

data class StartItem(
    /** مفتاح الكارت **بالحاجة نفسها** (الفاتورة بميعادها · الدين بقسطه) — «×» على كارت ما بيقفلش الحاجة الجاية بعده. */
    val key: String,
    val kind: StartItemKind,
    val text: String,
    val action: StartAction,
    val link: ScreenLink? = null,
    /** «سجّلها»: الكلام اللي بيتبعت للمساعد («سجّل فاتورة الكهرباء»). */
    val ask: String? = null,
    /** موضوع التنبيه في الجرس اللي بيقول نفس الحاجة (لو موجود). */
    val alertThread: String? = null,
)

data class StartItems(val items: List<StartItem>, val anyDismissed: Boolean) {
    val empty: Boolean get() = items.isEmpty()
}

/** الترتيب ثابت بالنوع (زي ما اتعرض على المالك) وجوه النوع زي ما جه، والممسوح (من الكارت أو من الجرس) برّه، وأقصى ٤. */
fun pickStartItems(candidates: List<StartItem>, dismissedCards: Set<String>, dismissedAlerts: Set<String>): StartItems {
    val ordered = candidates.sortedBy { it.kind.ordinal }
    val visible = ordered.filter { it.key !in dismissedCards && (it.alertThread == null || it.alertThread !in dismissedAlerts) }
    return StartItems(visible.take(MAX_START_ITEMS), anyDismissed = visible.size < ordered.size)
}

/**
 * الفاتورة المتكررة اللي يومها عدّى ولسه ما اتسجلتش (اختيار Claude — المالك يقدر يغيّره): شغالة · ميعادها الجاي قبل النهارده · ومفيش
 * عملية خارجة لنفس المحل (`recurringKey`) من أسبوع قبل الميعاد ولحد النهارده (نفس سماحية ±٧ أيام في اكتشاف الاشتراكات).
 */
fun billPendingSince(item: RecurringItem, rows: List<Transaction>, today: IsoDate): Boolean {
    if (!item.active || item.nextDueAt >= today) return false
    val from = addDaysIso(item.nextDueAt, -7)
    return rows.none {
        it.observedDirection == Direction.OUT && it.currency == item.currency && recurringKey(it) == item.merchantKey &&
            it.occurredAt.take(10) >= from && it.occurredAt.take(10) <= today
    }
}

/** المحفظة المعتادة للفاتورة = محفظة آخر دفعة ليها (الرد التاني ١ على فرع التصميم: «الفاتورة الدورية على محفظتها المعتادة»). */
fun usualWalletOf(item: RecurringItem, rows: List<Transaction>): Id? = rows
    .filter { it.observedDirection == Direction.OUT && it.currency == item.currency && recurringKey(it) == item.merchantKey && it.walletId != null }
    .maxWithOrNull(compareBy<Transaction> { it.occurredAt }.thenBy { it.sourceOrder })?.walletId

/** تصنيف آخر دفعة للفاتورة (للكارت) — null لو مالهاش. */
fun usualCategoryOf(item: RecurringItem, rows: List<Transaction>): Id? = rows
    .filter { it.observedDirection == Direction.OUT && it.currency == item.currency && recurringKey(it) == item.merchantKey && it.categoryId != null }
    .maxWithOrNull(compareBy<Transaction> { it.occurredAt }.thenBy { it.sourceOrder })?.categoryId

/** موضوع تنبيه الدين المتأخر نفسه (`dueAlertCandidates` + `inSpace`) — عشان مسحه من الجرس يشيل كارته. */
fun debtAlertThread(obligationId: Id, dueAt: IsoDate, spaceId: String): String {
    val base = "due|${DueSource.DEBT.wire}|$obligationId|$dueAt|${DueFlow.RECEIVE.wire}"
    return if (spaceId == DEFAULT_SPACE_ID) base else "$spaceId:$base"
}

/** موضوع تنبيه الفاتورة اللي عدّى ميعادها (`dueAlertCandidates` + `inSpace`). */
fun billAlertThread(recurringId: Id, dueAt: IsoDate, spaceId: String): String {
    val base = "due|${DueSource.RECURRING.wire}|$recurringId|$dueAt|${DueFlow.PAY.wire}"
    return if (spaceId == DEFAULT_SPACE_ID) base else "$spaceId:$base"
}

/** موضوع تنبيه العتبة/السقف للتصنيف (`budgetAlertCandidates`). */
fun budgetAlertThread(periodStart: IsoDate, categoryId: Id, spaceId: String): String {
    val base = "budget|$periodStart|cat:$categoryId"
    return if (spaceId == DEFAULT_SPACE_ID) base else "$spaceId:$base"
}

/** موضوع تنبيه رسايل البنك المستنية (`smsConfirmCandidate` — على مستوى الحساب). */
fun smsAlertThread(newestWaitingId: String): String = "sms-confirm|${hashContent(newestWaitingId)}"

// ─── المحفظة الأساسية والمسح من الجرس ───

/**
 * المحفظة الأساسية لكل بلد (رد المالك ٢ — 2026-10-09): بتتسأل مرة واحدة («بتصرف عادةً منين؟»)، وبعدها أي إضافة بالكتابة أو الصوت أو
 * لوحة «+» بتبدأ عليها، وتتغير من «المحافظ ← تفاصيل المحفظة ← اجعلها الأساسية». **على الحساب ويتزامن وفي النسخة الشاملة** — مجموعة
 * لوحدها (مش في ملف الحساب `profile/main`: التطبيق القديم بيكتب الملف كله فوق بعضه، فأي حقل جديد كان هيتمسح).
 */
const val MAIN_SPENDING_WALLETS_GROUP = "mainSpendingWallets"

enum class MainWalletSource(val wire: String) { CHAT("chat"), ADD_SHEET("add_sheet"), WALLET_DETAIL("wallet_detail");

    companion object {
        fun fromWire(wire: String): MainWalletSource? = entries.firstOrNull { it.wire == wire }
    }
}

data class MainSpendingWallet(val spaceId: String, val walletId: Id, val setAt: String, val source: MainWalletSource)

/**
 * الإشعار الممسوح من الجرس («×» — رد المالك ٣) بموضوعه: ما بيرجعش تاني لحد ما الموضوع يتحل، وبيشيل كارته من «أمور لم تُنجزها بعد».
 * والكارت المقفول من أول المحادثة. **على الحساب ويتزامنوا وفي النسخة الشاملة.**
 */
const val ALERT_DISMISSALS_GROUP = "alertDismissals"
const val ASSISTANT_DISMISSALS_GROUP = "assistantDismissals"

data class Dismissal(val key: String, val at: String)
