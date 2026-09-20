package app.masroufy.core

/**
 * منع التكرار — نقل `src/domain/dedupe.ts` (الدرجات الخمس في spec/05).
 * التشابه **ما بيمسحش أبدًا**، بيتعرض للقرار بس. ومن غير مرجع، سطر الكشف بيتعرف بالحساب + اليوم + المبلغ
 * + الاتجاه + **الرصيد المعلن بعد الحركة** (مطابقة واحد لواحد).
 */
enum class MatchingState(val wire: String) {
    NEW("new"), DUPLICATE("duplicate"), SIMILAR("similar"), CONFLICT("conflict"), INVALID("invalid");

    companion object {
        fun fromWire(wire: String): MatchingState = entries.first { it.wire == wire }
    }
}

data class DedupeCandidate(
    val accountIdentity: String,
    val sourceReference: String?,
    val date: IsoDate,
    val amountMinor: Halalas,
    val direction: Direction,
    val merchantName: String,
    val rowIndex: Int,
    /** الرصيد المعلن بعد الحركة في الكشف، لو موجود. */
    val statedBalanceMinor: Halalas? = null,
    /** المصدر رسالة بنك (مفيش رصيد سطر يتطابق عليه). */
    val smsSource: Boolean = false,
)

data class ExistingRecord(val candidate: DedupeCandidate, val transactionId: String)

data class DedupeVerdict(
    val state: MatchingState,
    /** السبب بلغة المستخدم — مفيش حالة من غير تفسير. */
    val reason: String,
    val matchedTransactionId: String? = null,
    val conflictFields: List<String>? = null,
    /** مفتاح الرصيد اللي اتطابق — المستدعي بيزوّد عدّاده (كل سجل موجود بيبلع صف وارد واحد بس). */
    val matchedBalanceKey: String? = null,
)

/** تاريخ + مبلغ + اتجاه + اسم تاجر مطبعن — بيفرّق «مكرر» عن «تعارض» عند تطابق المرجع. */
fun detailKey(c: DedupeCandidate): String = listOf(c.date, c.amountMinor.toString(), c.direction.wire, normalizeText(c.merchantName)).joinToString("|")

/** المرجع مقيد بهوية الحساب (spec/03). */
fun referenceKey(c: DedupeCandidate): String? {
    val ref = c.sourceReference ?: return null
    val trimmed = JsText.trim(ref)
    return if (trimmed.isEmpty()) null else "${c.accountIdentity}|$trimmed"
}

/** مفتاح سطر الكشف بالرصيد المعلن — null لو مفيش رصيد (رسالة، أو ملف من غير عمود رصيد). */
fun balanceKey(c: DedupeCandidate): String? {
    if (c.smsSource || c.statedBalanceMinor == null) return null
    return listOf(c.accountIdentity, c.date, c.amountMinor, c.direction.wire, c.statedBalanceMinor).joinToString("|")
}

private fun amountDayKey(c: DedupeCandidate) = listOf(c.accountIdentity, c.date, c.amountMinor, c.direction.wire).joinToString("|")

private fun diffFields(a: DedupeCandidate, b: DedupeCandidate): List<String> = buildList {
    if (a.date != b.date) add(uiText(TextKey.DIFF_DATE))
    if (a.amountMinor != b.amountMinor) add(uiText(TextKey.DIFF_AMOUNT))
    if (a.direction != b.direction) add(uiText(TextKey.DIFF_DIRECTION))
    if (normalizeText(a.merchantName) != normalizeText(b.merchantName)) add(uiText(TextKey.DIFF_MERCHANT))
}

class DedupeIndex internal constructor(
    internal val byAmountDay: Map<String, List<ExistingRecord>>,
    internal val byReference: Map<String, ExistingRecord>,
    internal val byDetail: Map<String, List<ExistingRecord>>,
    /** **قايمة** لكل مفتاح: الراجحي بيطبع رصيد نهاية اليوم على كل سطور اليوم. */
    internal val byBalance: Map<String, List<ExistingRecord>>,
)

fun buildDedupeIndex(existing: List<ExistingRecord>): DedupeIndex {
    val byAmountDay = mutableMapOf<String, MutableList<ExistingRecord>>()
    val byReference = mutableMapOf<String, ExistingRecord>()
    val byDetail = mutableMapOf<String, MutableList<ExistingRecord>>()
    val byBalance = mutableMapOf<String, MutableList<ExistingRecord>>()
    for (record in existing) {
        val c = record.candidate
        byAmountDay.getOrPut(amountDayKey(c)) { mutableListOf() }.add(record)
        referenceKey(c)?.let { byReference[it] = record }
        balanceKey(c)?.let { byBalance.getOrPut(it) { mutableListOf() }.add(record) }
        byDetail.getOrPut(detailKey(c)) { mutableListOf() }.add(record)
    }
    return DedupeIndex(byAmountDay, byReference, byDetail, byBalance)
}

private fun smsSimilarity(c: DedupeCandidate, index: DedupeIndex): DedupeVerdict? {
    val match = index.byAmountDay[amountDayKey(c)].orEmpty().firstOrNull { c.smsSource || it.candidate.smsSource } ?: return null
    return DedupeVerdict(
        MatchingState.SIMILAR,
        uiText(TextKey.DEDUPE_SMS_SIMILAR),
        matchedTransactionId = match.transactionId,
    )
}

/** بيصنّف سطر واحد قصاد الموجود. نقية — ما بتعدّلش الفهرس. `consumedBalance` قراية بس. */
fun classifyCandidate(candidate: DedupeCandidate, index: DedupeIndex, consumedBalance: Map<String, Int>? = null): DedupeVerdict {
    val ref = referenceKey(candidate)
    if (ref != null) {
        val existing = index.byReference[ref]
        if (existing != null) {
            val differences = diffFields(candidate, existing.candidate)
            if (differences.isEmpty()) {
                return DedupeVerdict(
                    MatchingState.DUPLICATE,
                    uiText(TextKey.DEDUPE_SAME_REFERENCE, candidate.sourceReference ?: ""),
                    matchedTransactionId = existing.transactionId,
                )
            }
            return DedupeVerdict(
                MatchingState.CONFLICT,
                uiText(TextKey.DEDUPE_REFERENCE_CONFLICT, candidate.sourceReference ?: "", differences.joinToString(uiText(TextKey.JOIN_AND))),
                matchedTransactionId = existing.transactionId,
                conflictFields = differences,
            )
        }
    }

    val sameLine = balanceKey(candidate)
    if (sameLine != null) {
        val alreadyUsed = consumedBalance?.get(sameLine) ?: 0
        val lineMatch = index.byBalance[sameLine].orEmpty().getOrNull(alreadyUsed)
        if (lineMatch != null) {
            return DedupeVerdict(
                MatchingState.DUPLICATE,
                uiText(TextKey.DEDUPE_SAME_STATEMENT_LINE),
                matchedTransactionId = lineMatch.transactionId,
                matchedBalanceKey = sameLine,
            )
        }
    }

    smsSimilarity(candidate, index)?.let { return it }
    if (ref != null) return DedupeVerdict(MatchingState.NEW, uiText(TextKey.DEDUPE_NEW_REFERENCE))

    val similar = index.byDetail[detailKey(candidate)]
    if (!similar.isNullOrEmpty()) {
        return DedupeVerdict(
            MatchingState.SIMILAR,
            uiText(TextKey.DEDUPE_SIMILAR_NO_REFERENCE),
            matchedTransactionId = similar[0].transactionId,
        )
    }
    return DedupeVerdict(MatchingState.NEW, uiText(TextKey.DEDUPE_NEW))
}

/**
 * بصمة الملف (الدرجة ١) — نفس FNV-1a بتاع التطبيق الحالي بالظبط، على وحدات UTF-16.
 * ضرب الـInt في كوتلن بيلف زي `Math.imul`، و`1099511628211 % 2^32` = 435.
 */
fun hashContent(content: String): String {
    var hi = 0xcbf29ce4.toInt()
    var lo = 0x84222325.toInt()
    for (ch in content) {
        val code = ch.code
        lo = lo xor (code and 0xff)
        hi = hi xor ((code ushr 8) and 0xff)
        val l = lo * 16777619
        val h = hi * 16777619 + lo * 435
        lo = l
        hi = h
    }
    return (hi.toUInt().toString(16).padStart(8, '0') + lo.toUInt().toString(16).padStart(8, '0')).uppercase()
}

fun importFingerprint(content: String, accountIdentity: String): String =
    hashContent("[${JsText.jsonString(accountIdentity)},${JsText.jsonString(content)}]")
