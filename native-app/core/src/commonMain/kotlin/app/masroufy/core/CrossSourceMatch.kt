package app.masroufy.core

import kotlin.math.abs

/**
 * §75-10 (قرار المالك 2026-10-08): **سطر الكشف ورسالة البنك لنفس الحركة = عملية واحدة** لو الفرق بينهم يومين بالكتير — بتتدمج
 * (عملية واحدة بمصدرين) بدل ما تتسجل مرتين. الشريحة S4.
 *
 * - المقارنة بين **مصدرين مختلفين** بس (رسالة قصاد كشف): رسالتين بنفس المبلغ ممكن يبقوا عمليتين فعلًا (منع التكرار العادي بيسأل عليهم).
 * - **نفس المحفظة** (أو نفس هوية الحساب لو المحفظة مش معروفة) · نفس العملة · نفس الاتجاه · نفس المبلغ **زي ما ورد من المصدر** (قبل تعديل
 *   المستخدم — §32) · الفرق بالأيام ≤ النافذة.
 * - **احتمال واحد بس من الناحيتين** ⇒ دمج: السطر ليه عملية واحدة مرشحة، والعملية دي ليها السطر ده بس. أي احتمالين ⇒ «شبه عملية» ويسأل
 *   (ما بنختارش بالتخمين) — وكده كل عملية موجودة بتبلع سطر وارد واحد بالكتير.
 * - العملية اللي اتدمجت قبل كده (ليها المصدرين) ما بتبلعش تاني؛ والرسالة أو السطر اللي **مرجعه** متسجل عليها (بنفس المبلغ والاتجاه) أو
 *   **نصه بالحرف** ([CrossSourceExisting.hashes] — كشف من غير مرجع ولا رصيد) ⇒ «متسجلة خلاص»: إعادة استيراد نفس الكشف ما بتكررش العملية
 *   (اسم المحل في العملية المدموجة بتاع الرسالة، فمنع التكرار العادي ما كانش هيعرفها).
 *
 * [classifyCandidate] (ملف المرجع `dedupe.json`) ما اتلمسش: الدمج طبقة بعده، ومن غير نافذة (null) المعاينة هي هي بالحرف.
 */

/** بداية مرجع رسالة البنك (`smsRow` — `SMS:` + بصمة المرسل والوقت والنص). */
const val SMS_REFERENCE_PREFIX = "SMS:"

/** النافذة اللي المالك قررها (§75-10): «يومين بالكتير». */
const val CROSS_SOURCE_WINDOW_DAYS = 2

fun isSmsReference(reference: String?): Boolean = reference?.startsWith(SMS_REFERENCE_PREFIX) == true

/**
 * عملية موجودة من ناحية المطابقة: تاريخها · مبلغها زي ما ورد · اتجاهها · اتسجلت من رسالة ([fromSms]) ولا من كشف ([fromStatement]) ولا الاتنين ·
 * مراجع سجلات مصدرها (عشان الرسالة أو السطر اللي اتدمج قبل كده يتعرف).
 */
data class CrossSourceExisting(
    val transactionId: Id,
    val date: IsoDate,
    val amountMinor: Halalas,
    val direction: Direction,
    val fromSms: Boolean,
    val fromStatement: Boolean,
    val references: Set<String> = emptySet(),
    /** بصمات نص سجلات مصدرها (`SourceRecord.sourceHash`) — بالتكرار (سطرين متطابقين بالحرف = بصمتين). */
    val hashes: List<String> = emptyList(),
) {
    /** ليها المصدرين خلاص (اتدمجت قبل كده). */
    val merged: Boolean get() = fromSms && fromStatement
}

/** سطر وارد بحكم منع التكرار العادي عليه ([state] و[matchedTransactionId] من [classifyCandidate]). */
data class CrossSourceRow(
    val lineNumber: Int,
    val date: IsoDate,
    val amountMinor: Halalas,
    val direction: Direction,
    val fromSms: Boolean,
    val reference: String?,
    val state: MatchingState,
    val matchedTransactionId: Id? = null,
    /** بصمة نص السطر (`hashContent(raw)`) — null = ما بيتقارنش بالنص. */
    val hash: String? = null,
)

sealed interface CrossSourceVerdict {
    val transactionId: Id

    /** نفس الحركة ⇒ السطر ما بيعملش عملية جديدة، بيتسجل مصدر تاني للعملية دي. */
    data class Merge(override val transactionId: Id) : CrossSourceVerdict

    /** أكتر من احتمال ⇒ «شبه عملية» (الأقرب في التاريخ) ويستنى قرار المالك. */
    data class Ambiguous(override val transactionId: Id) : CrossSourceVerdict

    /** مرجع السطر متسجل خلاص على عملية اتدمجت ⇒ مكرر. */
    data class AlreadyMerged(override val transactionId: Id) : CrossSourceVerdict
}

private fun trimmedReference(reference: String?): String? = reference?.let(JsText::trim)?.takeIf { it.isNotEmpty() }

/** المرشحين لسطر: من المصدر التاني بس، ولسه ما اتدمجوش، وبنفس المبلغ والاتجاه جوه النافذة. */
private fun candidatesOf(row: CrossSourceRow, existing: List<CrossSourceExisting>, windowDays: Int): List<CrossSourceExisting> = existing.filter { e ->
    !e.merged && (if (row.fromSms) e.fromStatement else e.fromSms) && e.direction == row.direction && e.amountMinor == row.amountMinor &&
        abs(daysBetween(row.date, e.date)) <= windowDays
}

/**
 * الحكم لكل سطر اتأثر (رقم السطر ⇐ الحكم)؛ السطر اللي مش في الخريطة حكمه زي ما هو. نقية — بتشتغل على الأرقام بس.
 * المؤهل للدمج: «جديد»، أو «شبه عملية» شبهه هو نفسه المرشح من المصدر التاني (رسالة وكشف في نفس اليوم). المكرر والتعارض ما بيتدمجوش.
 */
fun matchCrossSource(rows: List<CrossSourceRow>, existing: List<CrossSourceExisting>, windowDays: Int): Map<Int, CrossSourceVerdict> {
    require(windowDays >= 0) { "window must not be negative: $windowDays" }
    val out = LinkedHashMap<Int, CrossSourceVerdict>()
    val mergedByReference = HashMap<String, CrossSourceExisting>()
    // كل بصمة بتبلع سطر واحد بس: سطرين متطابقين بالحرف وواحد بس منهم اتدمج ⇒ التاني بيكمّل في منع التكرار العادي
    val mergedByHash = HashMap<String, ArrayDeque<Id>>()
    for (e in existing) {
        if (!e.merged) continue
        for (ref in e.references) mergedByReference.getOrPut(ref) { e }
        for (h in e.hashes) mergedByHash.getOrPut(h) { ArrayDeque() }.addLast(e.transactionId)
    }
    for (row in rows) {
        if (row.state == MatchingState.DUPLICATE || row.state == MatchingState.INVALID) continue
        // نفس المرجع بمبلغ أو اتجاه تاني = تعارض حقيقي ⇒ حكم منع التكرار العادي بيفضل
        val byRef = trimmedReference(row.reference)?.let { mergedByReference[it] }?.takeIf { it.amountMinor == row.amountMinor && it.direction == row.direction }
        val id = byRef?.transactionId ?: row.hash?.let { mergedByHash[it]?.removeFirstOrNull() } ?: continue
        out[row.lineNumber] = CrossSourceVerdict.AlreadyMerged(id)
    }

    val edges = LinkedHashMap<Int, List<CrossSourceExisting>>()
    for (row in rows) {
        if (row.lineNumber in out) continue
        val candidates = candidatesOf(row, existing, windowDays)
        if (candidates.isEmpty()) continue
        val eligible = row.state == MatchingState.NEW || (row.state == MatchingState.SIMILAR && candidates.any { it.transactionId == row.matchedTransactionId })
        if (eligible) edges[row.lineNumber] = candidates
    }
    val degree = HashMap<Id, Int>()
    for (list in edges.values) for (e in list) degree[e.transactionId] = (degree[e.transactionId] ?: 0) + 1

    for (row in rows) {
        val list = edges[row.lineNumber] ?: continue
        val only = list.singleOrNull()
        out[row.lineNumber] = if (only != null && degree[only.transactionId] == 1) {
            CrossSourceVerdict.Merge(only.transactionId)
        } else {
            CrossSourceVerdict.Ambiguous(list.minWith(compareBy({ abs(daysBetween(row.date, it.date)) }, { it.date }, { it.transactionId })).transactionId)
        }
    }
    return out
}

/**
 * النسخة الشاملة (الشريحة S4): سجل الدمج ([SourceRecord.mergeUndo]) لازم يبقى خريطة بتاريخ صحيح وأعداد صحيحة وعلى سجل «مكرر» مربوط بعملية،
 * و«مش ده» ([InstallmentPlan.dismissedTxnIds] · [Rosca.dismissedTxnIds]) قايمة معرّفات نصية. بيرجّع اسم الحقل الغلط أو null.
 */
fun matchingBackupProblem(group: String, row: Map<String, Any?>): String? {
    if (group == "sourceRecords" && row["mergeUndo"] != null) {
        val m = row["mergeUndo"] as? Map<*, *> ?: return "mergeUndo"
        val date = m["occurredAt"] as? String
        val balance = m["statedBalanceMinor"]
        val ok = date != null && isValidIsoDate(date) && isSafeInteger(m["sourceOrder"]) && (balance == null || isSafeInteger(balance)) &&
            row["transactionId"] != null && row["matchingState"] == MatchingState.DUPLICATE.wire
        if (!ok) return "mergeUndo"
    }
    if ((group == "roscas" || group == "installmentPlans") && row["dismissedTxnIds"] != null) {
        val list = row["dismissedTxnIds"] as? List<*> ?: return "dismissedTxnIds"
        if (list.any { it !is String || it.isEmpty() }) return "dismissedTxnIds"
    }
    return null
}

/**
 * الاسترجاع (`mergeFullBackupDetailed`): «مش ده» قايمة معرّفات عمليات ⇒ كل معرّف بيتحوّل لمعرّف العملية **الموجودة** لو نفس العملية متخزنة
 * بمعرّف تاني على الجهاز ده (زي [BACKUP_RELATIONS] للحقل الواحد). معرّف مش في الملف بيفضل زي ما هو — ما بيتقترحش عليه أصلًا ومش علاقة مكسورة.
 */
internal fun remapDismissedTxnIds(group: String, row: MutableMap<String, Any?>, transactions: Map<String, String>?) {
    if ((group != "roscas" && group != "installmentPlans") || transactions.isNullOrEmpty()) return
    val list = row["dismissedTxnIds"] as? List<*> ?: return
    row["dismissedTxnIds"] = list.map { id -> transactions[jsString(id)] ?: id }
}
