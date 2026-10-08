package app.masroufy.device.smscoverage

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsForeignPending
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.SmsVocabulary
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.device.SmsSafety

/**
 * الرسالة بتعدّي على **نفس خط التطبيق** (التسجيل التلقائي §72): `SmsSafety.sanitize` قبل الحفظ ⇒ قارئ البلد على النص المحفوظ
 * ⇒ العملية بتتعمل زي `ImportStatement` (الوصف = نص الرسالة بعد القص، من غير نوع عملية) ⇒ `transferPartyOf`.
 * وجنبها: القراية بطلب المستخدم (`ReadBankSms` — النص الأصلي من غير فلتر الأمان)، وقارئ البلد التانية على نفس النص
 * (`AutoRecordSms` بيقرا الصندوق بقارئ كل بلد).
 */
internal object Evaluate {
    private val REASONS = listOf(
        TextKey.SMS_OFFER to "offer", TextKey.SMS_SENSITIVE to "otp/sensitive", TextKey.SMS_DECLINED to "declined",
        TextKey.SMS_DIRECTION_UNCLEAR to "direction unclear", TextKey.SMS_FOREIGN_CURRENCY to "foreign currency",
        TextKey.SMS_NOT_EGP to "not EGP", TextKey.SMS_AMOUNT_INVALID to "amount invalid",
        TextKey.SMS_MULTIPLE_AMOUNTS to "multiple amounts", TextKey.SMS_CURRENCY_UNCLEAR to "currency unclear",
        TextKey.SMS_AMOUNT_UNCLEAR to "amount unclear", TextKey.SMS_DATE_UNCLEAR to "date unclear",
        TextKey.SMS_NOT_TRANSACTION to "not a transaction",
    )
    private val DELIBERATE = setOf("offer", "otp/sensitive", "declined", "not a transaction")
    private val GARBAGE_KEYS = listOf("acct", "last4", "ownIbanMasked", "acct_tail", "date", "amount", "balance")

    /** المبلغ الأجنبي في قوالب البحث كله دولار (الراجحي «شراء دولي» والبنك العربي). */
    private const val FOREIGN_CURRENCY = "USD"

    private fun reasonCode(reason: String): String = REASONS.firstOrNull { uiText(it.first) == reason }?.second ?: reason

    /** ليه `SmsSafety` رمى الرسالة — من نفس مفردات الفلتر (`SmsVocabulary`)، للتشخيص بس. */
    private fun safetyWhy(body: String): String {
        SmsVocabulary.ignoreReason(body)?.let { return "guard '${reasonCode(uiText(it))}'" }
        if (!SmsVocabulary.hasMovement(body)) return "no movement word"
        return "no currency token SmsSafety knows"
    }

    private fun reader(country: String): (BankSmsMessage, Int) -> SmsParseResult = if (country == "SA") ::parseBankSms else ::parseEgyptBankSms

    private fun norm(s: String) = s.trim().trimEnd('.', ',', '،', ':', ';').trim().replace(Regex("\\s+"), " ").uppercase()

    private fun digitsOnly(v: String) = v.isNotEmpty() && v.all { it.isDigit() }

    private fun transaction(country: String, row: SmsRow) = Transaction(
        id = "t-1", occurredAt = row.date, datePrecision = "day", sourceOrder = row.lineNumber,
        economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, observedDirection = row.direction,
        amountMinor = row.amountMinor, currency = if (country == "SA") Currency.SAR else Currency.EGP,
        categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false,
        createdAt = "x", updatedAt = "x", rawDescription = row.description, rawMerchantName = row.merchantName,
    )

    private fun summary(country: String, row: SmsRow): String {
        val party = transferPartyOf(transaction(country, row))
        return "amount=${row.amountMinor} dir=${row.direction.wire} date=${row.date} merchant='${row.merchantName}'" +
            (party?.let { " party='${it.label}'/${it.last4 ?: "-"}" } ?: "")
    }

    /** مقارنة اللي اتقري باللي القالب معناه. فاضية = صح. */
    private fun compare(country: String, v: Variant, row: SmsRow): List<String> {
        val e = v.expect
        val problems = mutableListOf<String>()
        if (e.foreign) problems += "foreign-currency amount booked as local ${if (country == "SA") "SAR" else "EGP"} (§75-12: record + ask for the local amount)"
        val want = Fill.minor(v.values.getValue(e.amountKey))
        if (row.amountMinor != want) problems += "amount ${row.amountMinor} != expected $want (${e.amountKey})"
        val dir = if (row.direction == Direction.IN) Dir.IN else Dir.OUT
        if (e.dir != Dir.ANY && dir != e.dir) problems += "direction ${dir.name} != expected ${e.dir.name}"
        if (row.date != Fill.TX_DATE) problems += "date ${row.date} != expected ${Fill.TX_DATE}"
        if (e.kind != null && row.kind != e.kind) problems += "kind ${row.kind.wire} != expected ${e.kind.wire}"
        if (e.merchant) {
            val m = v.values.getValue("merchant")
            if (norm(row.merchantName) != norm(m)) problems += "merchant '${row.merchantName}' != expected '$m'"
        } else {
            // مفيش محل في القالب: اسم العملية لازم ما يبقاش حسابك ولا كارتك ولا التاريخ ولا المبلغ
            val junk = GARBAGE_KEYS.mapNotNull { v.values[it] }.filter { it.isNotEmpty() && it in row.merchantName }
            if (junk.isNotEmpty()) problems += "merchant name shows non-merchant text '${row.merchantName}'"
        }
        val party = transferPartyOf(transaction(country, row))
        val names = e.partyKeys.mapNotNull { k -> v.values[k]?.takeIf { !digitsOnly(it) && it in v.body } }
        val digits = e.partyKeys.mapNotNull { k ->
            v.values[k]?.takeIf { digitsOnly(it) && it in v.body }?.takeLast(4)?.takeIf { it.length == 4 }
        }
        if (names.isEmpty() && digits.isEmpty()) {
            if (party != null) problems += "unexpected transfer party '${party.label}'/${party.last4 ?: "-"}"
        } else {
            val wanted = (names.map { "name '$it'" } + digits.map { "last4 $it" }).joinToString(" or ")
            val ok = party != null && (names.any { norm(it) == norm(party.label) } || (party.last4 != null && party.last4 in digits))
            if (!ok) problems += "counterparty ${party?.let { "'${it.label}'/${it.last4 ?: "-"}" } ?: "missing"} (expected $wanted)"
        }
        return problems
    }

    /** عملية أجنبية من غير مبلغ محلي (§75-12): الصح = رفض بسبب العملة **ومعاه** المبلغ الأجنبي والاتجاه والتاريخ (والمحل لو متوقع). */
    private fun comparePending(v: Variant, pending: SmsForeignPending): List<String> {
        val e = v.expect
        val problems = mutableListOf<String>()
        val want = Fill.minor(v.values.getValue(e.amountKey))
        if (pending.foreign.currency != FOREIGN_CURRENCY || pending.foreign.amountMinor != want) {
            problems += "foreign ${pending.foreign.currency} ${pending.foreign.amountMinor} != expected $FOREIGN_CURRENCY $want"
        }
        val dir = if (pending.direction == Direction.IN) Dir.IN else Dir.OUT
        if (e.dir != Dir.ANY && dir != e.dir) problems += "direction ${dir.name} != expected ${e.dir.name}"
        if (pending.date != Fill.TX_DATE) problems += "date ${pending.date} != expected ${Fill.TX_DATE}"
        if (e.merchant && norm(pending.merchantName) != norm(v.values.getValue("merchant"))) problems += "merchant '${pending.merchantName}'"
        return problems
    }

    private fun judge(country: String, v: Variant, parsed: SmsParseResult): Triple<Outcome, List<String>, String> = when (parsed) {
        is SmsParseResult.Rejected -> {
            val code = reasonCode(parsed.reason)
            val pending = parsed.foreign
            when {
                v.expect.tx && v.expect.foreign && pending != null -> {
                    val problems = comparePending(v, pending)
                    val s = "foreign pending §75-12: ${pending.foreign.currency} ${pending.foreign.amountMinor} dir=${pending.direction.wire} date=${pending.date} merchant='${pending.merchantName}'"
                    Triple(if (problems.isEmpty()) Outcome.CORRECT else Outcome.WRONG, problems, s)
                }
                v.expect.tx -> Triple(Outcome.REJECTED, listOf("parser rejected: $code"), code)
                else -> Triple(Outcome.CORRECTLY_IGNORED, emptyList(), code)
            }
        }
        is SmsParseResult.Ok -> {
            val s = summary(country, parsed.row)
            if (!v.expect.tx) Triple(Outcome.WRONGLY_ACCEPTED, listOf("booked a ${v.expect.what}: $s"), s)
            else {
                val problems = compare(country, v, parsed.row)
                Triple(if (problems.isEmpty()) Outcome.CORRECT else Outcome.WRONG, problems, s)
            }
        }
    }

    fun run(country: String, sender: String, v: Variant): VariantResult {
        val manual = judge(country, v, reader(country)(BankSmsMessage(sender, Fill.RECEIVED_AT, v.body), 1)).first
        val stored = SmsSafety.sanitize(v.body)
        if (stored == null) {
            val why = safetyWhy(v.body)
            return if (v.expect.tx) {
                VariantResult(v, Outcome.REJECTED, listOf("dropped by SmsSafety before the parser ($why)"), "SmsSafety: $why", manual, null, false)
            } else {
                VariantResult(v, Outcome.CORRECTLY_IGNORED, emptyList(), "SmsSafety: $why", manual, null, !why.startsWith("guard"))
            }
        }
        val parsed = reader(country)(BankSmsMessage(sender, Fill.RECEIVED_AT, stored), 1)
        val (outcome, problems, detail) = judge(country, v, parsed)
        val otherCountry = if (country == "SA") "EG" else "SA"
        val cross = (reader(otherCountry)(BankSmsMessage(sender, Fill.RECEIVED_AT, stored), 1) as? SmsParseResult.Ok)
            ?.let { "$otherCountry reader also accepted: ${summary(otherCountry, it.row)}" }
        val fragile = outcome == Outcome.CORRECTLY_IGNORED && detail !in DELIBERATE
        val date = v.values["date"]
        val latent = if (outcome == Outcome.REJECTED && detail == "date unclear" && date != null && date !in v.body) {
            val dated = SmsSafety.sanitize(v.body + "\n" + date + " " + v.values["time"].orEmpty())
            dated?.let { judge(country, v, reader(country)(BankSmsMessage(sender, Fill.RECEIVED_AT, it), 1)) }
                ?.let { (o, p, d) -> "with a date line: ${o.wire}" + if (p.isEmpty()) "" else " — ${p.joinToString("; ")}" + " ($d)" }
        } else null
        return VariantResult(v, outcome, problems, detail, manual, cross, fragile, latent)
    }
}
