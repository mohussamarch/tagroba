package app.masroufy.core

/**
 * «زون التحويلات» (OVERRIDES §39 · §39.1 · §42 · §60): مين الطرف التاني في كل تحويل، والطرف اللي التحويلات معاه كترت
 * بيتسأل عنه: «ده حسابك التاني؟» (⇒ تحويل داخلي، والقديم كله بيتصلح) ولا «شخص» (⇒ الصادر ليه دعم، والوارد منه يتسأل).
 *
 * الطرف بيتعرف **بالاسم + آخر 4 أرقام بس** (قاعدة 11). الوصف المتخزن في فايربيز متقص (`****` + آخر 4) والوصف قبل التخزين
 * كامل — والمفتاح بياخد **آخر 4 أرقام بس** فبيطلع هو هو في الحالتين.
 * ⚠️ الأشكال اتاخدت من كشفين المالك (الراجحي CSV + QNB PDF) — مفيش ولا اسم ولا رقم منهم هنا؛ الأمثلة في الاختبارات مخترعة.
 */
enum class TransferVerdict(val wire: String) {
    /** حسابك التاني ⇒ تحويل داخلي (مش دخل ولا مصروف). */
    OWN_ACCOUNT("own_account"),

    /** شخص ⇒ الصادر ليه «دعم» (مصروف)، والوارد منه بيتسأل عن نوعه (§39.1). */
    PERSON("person"),

    /** «مش ده» ⇒ ما يتسألش عنه تاني. */
    DISMISSED("dismissed");

    companion object {
        fun fromWire(wire: String): TransferVerdict = entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("unknown transfer verdict: $wire")
    }
}

/** قرار صاحب الحساب على طرف — المعرّف = [key]. */
data class TransferParty(
    val key: String,
    val label: String,
    val last4: String?,
    val verdict: TransferVerdict,
    val personId: Id? = null,
    val decidedAt: String = "",
)

/** الطرف زي ما اتقري من وصف العملية. */
data class TransferPartyRef(val key: String, val label: String, val last4: String?)

/** قرار المالك §39 (ب): الشك بعد 5 تحويلات مع نفس الطرف في شهر واحد. */
const val SUSPICIOUS_TRANSFERS_PER_MONTH = 5

/** الأنواع اللي بتتعرض لما فلوس تيجي من شخص مربوط (§42 — بيقفل سؤال §39.1). */
val INCOMING_FROM_PERSON_KINDS: List<EconomicKind> = listOf(
    EconomicKind.GIFT_RECEIVED, EconomicKind.SUPPORT_RECEIVED, EconomicKind.DEBT_COLLECTED,
    EconomicKind.LOAN_RECEIVED, EconomicKind.CUSTODY_RECEIVED, EconomicKind.ROSCA_PAYOUT,
)

private const val ARABIC = "؀-ۿﭐ-﷿ﹰ-﻿"
private val PRESENTATION_FORM = Regex("[ﭐ-﷿ﹰ-﻿]")
// المقلوب هو حروف العرض بس — العربي العادي جوه نفس الوصف (زي «[رقم]» اللي التطبيق القديم بيحطه) مكتوب صح ومايتقلبش
private val PRESENTATION_RUN = Regex("[ﭐ-﷿ﹰ-﻿]+(?: [ﭐ-﷿ﹰ-﻿]+)*")

/**
 * كشف الراجحي القديم (CSV) فيه العربي **بترتيب العرض** (حروف «presentation forms» ومقلوبة): «ﺪﻤﺤﻣ» = «محمد» بالعكس.
 * لو النص فيه الحروف دي ⇒ كل جملة عربي بتتقلب لترتيبها الحقيقي. النص العادي بيرجع زي ما هو.
 */
fun logicalArabic(text: String): String =
    if (!PRESENTATION_FORM.containsMatchIn(text)) text else PRESENTATION_RUN.replace(text) { it.value.reversed() }

private val DIGITS_OR_MASK = "[0-9*•]"

/**
 * الراجحي: «<اسم مختصر>W-/FRACCT/<الحساب>FR<اسم لاتيني اختياري>:» — والصادر TOACCT/…TO. ومحفظة دراهم: «B1B/FRACCT/SA<الحساب>Drahim/».
 */
private val ACCOUNT = Regex("([$ARABIC A-Za-z]{0,40}?)[A-Z]?-?/(?:FR|TO)ACCT/(?:[A-Z]{2})?($DIGITS_OR_MASK{4,})([A-Za-z]{2,30})?")
private val DIRECTION_MARK = Regex("^(?:FR|TO)(?=[A-Z]|$)")

/**
 * QNB: «IPN<الاسم — لحد 12 حرف و`_` بدل المسافة>‎<بصمة hex>ACC|CAR|WAL» (ACC حساب · CAR كارت · WAL محفظة).
 * الاسم العربي بيضيع من ملف البنك («????») ⇒ مالوش طرف. والصادر قبله اسم صاحب الحساب نفسه — مش هو الطرف.
 */
private val QNB_IPN = Regex("IPN(?!\\s*TRANSFER)([A-Za-z?_.*][A-Za-z?_.*]{0,11})[0-9a-f*•]*(?:ACC|CAR|WAL)")
private val HEX_TAIL = Regex("[a-f]+$")

/** الاسم بحروف كبيرة لازق فيه أول حرف من البصمة («MOHAMED_EIDc») ⇒ يتشال. الاسم اللي فيه حروف صغيرة أصلًا بيفضل زي ما هو. */
private fun qnbName(raw: String): String {
    val body = raw.replace(HEX_TAIL, "")
    return if (body.isNotEmpty() && body.none { it in 'a'..'z' }) body else raw
}

/** سريع وارد (مرتب): «…-INMAIN<كود>-<الشركة>». */
private val SARIE = Regex("-INMAIN[A-Z0-9*]*-([^|]{2,80}?)\\s*(?:\\||$)")

/** حوالة محلية واردة: «… | <المستفيد>-<المرسل><كود البنك>…». */
private val LOCAL_IN = Regex("\\|\\s*[^|-]{2,80}-([^|]{2,80}?)-?[A-Z]{4}PE[A-Z][0-9A-Z*]*")

/** فورية واردة: «<الاسم>/<كود>SA<بنك>…» أو «<كود>SA<بنك>…/<الاسم>». */
private val INSTANT_IN_BEFORE = Regex("([^|/:]{2,60}?)\\s*/\\s*$DIGITS_OR_MASK{2,}SA[A-Z]{4}")
private val INSTANT_IN_AFTER = Regex("$DIGITS_OR_MASK{2,}SA[A-Z]{4}[0-9A-Z*]*/\\s*([^|/:]{2,60})")

/** فيزا دايركت: «PAYPAL*<الاسم> /SG». */
private val VISA_DIRECT = Regex("PAYPAL\\*([^/|]{2,60}?)\\s*/")

/** صادر (فورية · أجنبية): «<الاسم>/[رقم]» أو «<الاسم>/<أرقام>» في أول الوصف. */
private val OUTGOING = Regex("^\\s*([^/|:]{2,60}?)\\s*/\\s*(?:\\[رقم]|$DIGITS_OR_MASK{2,})")

private val FEE_PREFIX = Regex("^\\s*رسوم")
private val TRANSFER_WORD = Regex("تحويل|حوال|حواال|TRANSFER|\\bIPN\\b", RegexOption.IGNORE_CASE)
private val WALLET_TOPUP = Regex("محفظة|DRAHIM", RegexOption.IGNORE_CASE)

/** العملية تحويل أو حوالة (مش رسومها) — من نوع العملية في الكشف، وإلا من أول الوصف. */
fun isTransferLike(t: Transaction): Boolean {
    val op = t.sourceOperationType?.let { nfkc(logicalArabic(it)) }
    val text = op ?: t.rawDescription?.let { nfkc(logicalArabic(it)) }?.take(60) ?: return false
    return !FEE_PREFIX.containsMatchIn(text) && TRANSFER_WORD.containsMatchIn(text)
}

private fun lastFour(run: String): String? = run.filter { it in '0'..'9' }.takeLast(4).takeIf { it.length == 4 }

private fun ref(label: String, last4: String?): TransferPartyRef? {
    val clean = JsText.trim(JsText.collapseWhitespace(label.replace('_', ' ').trim('-', '/', ' '))).take(60)
    val compact = normalizeCompact(clean)
    if (compact.isEmpty() && last4 == null) return null
    // اسم كله «؟» (عربي ضاع من ملف البنك) ⇒ مش اسم
    if (compact.isEmpty() && clean.isNotEmpty()) return last4?.let { TransferPartyRef("#$it", "••••$it", it) }
    return TransferPartyRef(compact + (last4?.let { "#$it" } ?: ""), clean.ifEmpty { "••••$last4" }, last4)
}

/*
 * رسايل البنك (§72): الوصف المتخزن = نص الرسالة بعد قص الأرقام. **الأشكال من الرسايل المخترعة اللي في المستودع بس** (`golden/sms.json`
 * · تعليق `EgyptBankSms.kt`) — ما اخترعناش شكل بنك:
 *   - سطر «إلى:»/«من:» (أو to:/from:) — الصادر: الطرف في «إلى» (و«من» = حسابك إنت)، والوارد: الطرف في «من». الاسم ⇒ اسم؛ أرقام بس ⇒ آخر 4.
 *   - سطر «من حساب»/«إلى حساب» + رقم (الآيبان بيتقص لـ`••••`+آخر 4 قبل الحفظ) ⇒ آخر 4.
 *   - QNB مصر «IPN transfer sent/received with amount of …» **مفيهاش الطرف أصلًا** («from 1234» = حسابك) ⇒ مالهاش طرف — كانت بتعدّي
 *     لنمط الكشف الصادر وتطلع «طرف» من أول الرسالة لحد التاريخ (اتكشف في الجلسة دي).
 * الرسالة بتتعرف إنها رسالة من سطورها (أكتر من سطر — وصف الكشف سطر واحد) أو من شكل QNB — وساعتها أشكال الكشف ما بتتجربش (تاريخ
 * «26/09/16» كان ممكن يتقري «اسم/أرقام»)، ورسالة من غير سطر طرف ⇒ مالهاش طرف. شكل رسالة من سطر واحد بـ«to:» جوه الكلام ما بيتقريش (مفيش عينة طرف حقيقية — سؤال مفتوح للمالك).
 */
private val SMS_PARTY_LINE = Regex("^[ \\t]*(من|إلى|الى|from|to)[ \\t]*[:：][ \\t]*([^\\n]+?)[ \\t]*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
private val SMS_ACCOUNT_LINE = Regex("^[ \\t]*(من|إلى|الى)[ \\t]+حساب[ \\t]+([^\\n]+?)[ \\t]*$", RegexOption.MULTILINE)
private val QNB_SMS_TRANSFER = Regex("^IPN transfer (?:sent|received) with amount of")
private val SMS_OUT_SIDE = setOf("إلى", "الى", "to")
private val SMS_IN_SIDE = setOf("من", "from")

/** وصف الكشف سطر واحد دايمًا (CSV/PDF)؛ الرسالة سطور. */
private fun looksLikeSms(text: String): Boolean = QNB_SMS_TRANSFER.containsMatchIn(text) || '\n' in text

private fun smsPartyOf(text: String, direction: Direction): TransferPartyRef? {
    val side = if (direction == Direction.OUT) SMS_OUT_SIDE else SMS_IN_SIDE
    SMS_ACCOUNT_LINE.findAll(text).firstOrNull { it.groupValues[1] in side }?.let { m ->
        // قص الآيبان بياكل السطر الجديد اللي بعده («••••7519بـSR 100») ⇒ الرقم من أول السطر بس
        return SMS_LEADING_NUMBER.find(m.groupValues[2])?.value?.let(::lastFour)?.let { ref("", it) }
    }
    val match = SMS_PARTY_LINE.findAll(text).firstOrNull { it.groupValues[1].lowercase() in side } ?: return null
    val value = match.groupValues[2]
    return if (value.all { it in '0'..'9' || it in "*•xX -" }) lastFour(value)?.let { ref("", it) } else ref(value, null)
}

private val SMS_LEADING_NUMBER = Regex("^[0-9*•xX -]+")

/** الطرف التاني في التحويل، أو `null` لو مش تحويل أو مفيش اسم ولا رقم يتعرف بيه (ما بنخمّنش). */
fun transferPartyOf(t: Transaction): TransferPartyRef? {
    if (!isTransferLike(t)) return null
    val text = nfkc(logicalArabic(latinizeDigits(t.rawDescription.orEmpty())))
    if (looksLikeSms(text)) return smsPartyOf(text, t.observedDirection)
    val op = t.sourceOperationType?.let { nfkc(logicalArabic(it)) }.orEmpty()
    if (WALLET_TOPUP.containsMatchIn(op)) return ref("محفظة دراهم", null)
    ACCOUNT.find(text)?.let { m ->
        val latin = m.groupValues[3].replace(DIRECTION_MARK, "")
        if (normalizeCompact(latin) == "DRAHIM") return ref("محفظة دراهم", null)
        return ref(m.groupValues[1].ifBlank { latin }, lastFour(m.groupValues[2]))
    }
    QNB_IPN.find(text)?.let { return ref(qnbName(it.groupValues[1]), null) }
    SARIE.find(text)?.let { return ref(it.groupValues[1], null) }
    LOCAL_IN.find(text)?.let { return ref(it.groupValues[1], null) }
    VISA_DIRECT.find(text)?.let { return ref(it.groupValues[1], null) }
    if (t.observedDirection == Direction.IN) {
        (INSTANT_IN_BEFORE.find(text)?.groupValues?.get(1) ?: INSTANT_IN_AFTER.find(text)?.groupValues?.get(1))?.let { return ref(it, null) }
    } else {
        OUTGOING.find(text)?.let { return ref(it.groupValues[1], null) }
    }
    return null
}

/** طرف التحويلات معاه كترت في شهر واحد ولسه ما اتسألش عنه. */
data class SuspiciousParty(val party: TransferPartyRef, val month: String, val count: Int, val incoming: Int, val outgoing: Int)

/**
 * الأطراف اللي ليها [SUSPICIOUS_TRANSFERS_PER_MONTH] تحويلات أو أكتر في **شهر ميلادي واحد** ومالهاش قرار في [decided].
 * كل طرف بيطلع مرة واحدة (بأكتر شهر)، والأكتر الأول.
 */
fun suspiciousTransferParties(transactions: List<Transaction>, decided: Set<String>): List<SuspiciousParty> {
    val byParty = LinkedHashMap<String, MutableList<Pair<TransferPartyRef, Transaction>>>()
    for (t in transactions) {
        val party = transferPartyOf(t) ?: continue
        if (party.key in decided) continue
        byParty.getOrPut(party.key) { mutableListOf() } += party to t
    }
    return byParty.values.mapNotNull { list ->
        val (month, inMonth) = list.groupBy { it.second.occurredAt.take(7) }.maxWithOrNull(compareBy({ it.value.size }, { it.key })) ?: return@mapNotNull null
        if (inMonth.size < SUSPICIOUS_TRANSFERS_PER_MONTH) return@mapNotNull null
        SuspiciousParty(
            list.first().first, month, inMonth.size,
            inMonth.count { it.second.observedDirection == Direction.IN }, inMonth.count { it.second.observedDirection == Direction.OUT },
        )
    }.sortedWith(compareByDescending<SuspiciousParty> { it.count }.thenBy { it.party.key })
}

/**
 * القرار على الطرف ⇒ العملية (§39 · §39.1): «حسابك التاني» ⇒ تحويل داخلي مؤكد (حتى لو كان متأكد قبل كده — قرار المالك (ج):
 * القديم كله يتصلح) · «شخص» ⇒ الصادر «دعم» مؤكد لو نوعه لسه ما اتأكدش، والوارد **يتسأل** (ما بيتصنفش لوحده) · «مش ده» ⇒ ولا حاجة.
 * بترجع نفس العملية لو مفيش تغيير.
 */
fun applyTransferVerdict(t: Transaction, party: TransferParty?, nowIso: String): Transaction {
    if (party == null) return t
    return when (party.verdict) {
        TransferVerdict.OWN_ACCOUNT ->
            if (t.economicKind == EconomicKind.INTERNAL_TRANSFER && t.economicKindConfirmed && t.reviewState == ReviewState.CONFIRMED) t
            else t.copy(economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = nowIso)
        TransferVerdict.PERSON -> when {
            t.economicKindConfirmed -> t
            t.observedDirection == Direction.OUT ->
                t.copy(economicKind = EconomicKind.SUPPORT_GIFT, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = nowIso)
            t.reviewState == ReviewState.NEEDS_REVIEW -> t
            else -> t.copy(reviewState = ReviewState.NEEDS_REVIEW, updatedAt = nowIso)
        }
        TransferVerdict.DISMISSED -> t
    }
}
