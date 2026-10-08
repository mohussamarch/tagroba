package app.masroufy.core

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * أدوات اختبارات أشكال رسايل البنوك (جلسة 32). ⚠️ **كل الرسايل مخترعة** بنفس **شكل** قوالب البحث (`research/banks`) —
 * ولا اسم ولا رقم ولا محل من المصادر (المستودع عام، وبعض المصادر فيها بيانات ناس حقيقية).
 * القيم المخترعة الثابتة: المحل TEST GROCER · الطرف «سامي التجريبي» / SAMI TESTER · حسابك 1188 · حساب الطرف 9021 ·
 * حسابك التاني 2277 · الكارت 6604 · اليوم 2026-03-05 الساعة 09:10.
 */
internal const val SMS_TX_DAY = "2026-03-05"

/** وصول الرسالة 09:10:30 بتوقيت الرياض والقاهرة تقريبًا (06:10 UTC). */
internal const val SMS_RECEIVED_AT = "2026-03-05T06:10:30Z"

/**
 * حالة واحدة: الرسالة + المتوقع. [merchant] null = ما بيتفحصش؛ [party] = اسم الطرف المتوقع (أو «••••1234» لو أرقام بس)،
 * و[last4] آخر 4 أرقام الطرف المتوقعة؛ الاتنين null = لازم **ما يطلعش** طرف.
 */
internal data class SmsCase(
    val name: String,
    val body: String,
    val amount: Long,
    val direction: Direction,
    val kind: SmsKind? = null,
    val merchant: String? = null,
    val party: String? = null,
    val last4: String? = null,
    val date: String = SMS_TX_DAY,
    val foreign: SmsForeignAmount? = null,
)

internal fun smsMessage(body: String, sender: String = "TESTBANK", receivedAt: String = SMS_RECEIVED_AT) = BankSmsMessage(sender, receivedAt, body)

internal fun smsTransaction(row: SmsRow, currency: Currency = Currency.SAR) = Transaction(
    id = "t-1", occurredAt = row.date, datePrecision = "day", sourceOrder = row.lineNumber, economicKind = EconomicKind.UNCLASSIFIED,
    economicKindConfirmed = false, observedDirection = row.direction, amountMinor = row.amountMinor, currency = currency,
    categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x",
    updatedAt = "x", rawDescription = row.description, rawMerchantName = row.merchantName,
)

/** الحالة بتتقري زي ما هي متوقعة — المبلغ والاتجاه والتاريخ والنوع والمحل والطرف (من الوصف المقصوص زي التخزين بالظبط). */
internal fun checkCase(c: SmsCase, parse: (BankSmsMessage, Int) -> SmsParseResult, currency: Currency) {
    val row = when (val r = parse(smsMessage(c.body), 1)) {
        is SmsParseResult.Ok -> r.row
        is SmsParseResult.Rejected -> fail("${c.name}: rejected «${r.reason}»")
    }
    assertEquals(c.amount, row.amountMinor, "${c.name}: amount")
    assertEquals(c.direction, row.direction, "${c.name}: direction")
    assertEquals(c.date, row.date, "${c.name}: date")
    c.kind?.let { assertEquals(it, row.kind, "${c.name}: kind") }
    c.merchant?.let { assertEquals(it, row.merchantName, "${c.name}: merchant") }
    assertEquals(c.foreign, row.foreign, "${c.name}: foreign")
    val party = transferPartyOf(smsTransaction(row, currency))
    if (c.party == null && c.last4 == null) {
        assertNull(party, "${c.name}: unexpected party $party")
    } else {
        assertNotNull(party, "${c.name}: party missing")
        c.party?.let { assertEquals(it, party.label, "${c.name}: party name") }
        assertEquals(c.last4, party.last4, "${c.name}: party last 4")
    }
    // ولا رقم حساب كامل في الوصف أو المفتاح (قاعدة 11)
    assertNull(Regex("\\d{9,}").find(row.description + (party?.key ?: "")), "${c.name}: full number stored")
}
