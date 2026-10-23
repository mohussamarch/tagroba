package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** فحص 0.5.3: رسالة مصرية من غير سنة («يوم 23/10») — السنة من وقت الوصول: نفس السنة، أو اللي قبلها لو التاريخ كان هيبقى في المستقبل. نص مخترع. */
class EgyptYearlessDateTest {
    private val nbe = "تم خصم 75.00 جم من بطاقة الخصم المباشر رقم 4321 عند QA CAFE يوم 23/10 الساعة 16:05"

    @Test fun nbeDayMonthWithoutYearIsRead() {
        assertTrue(isNbeSentence(nbe), "جملة الأهلي")
        assertEquals("2026-10-23", egyptTransactionDate(nbe, "2026-10-23T14:10:00.000Z"))
    }

    @Test fun nbeDateInJanuaryForLateDecemberIsLastYear() {
        val body = nbe.replace("23/10", "30/12")
        assertEquals("2026-12-30", egyptTransactionDate(body, "2027-01-02T08:00:00.000Z"))
    }

    @Test fun nbeMonthDayStaysMonthDayWhenBothReadingsArePossible() {
        // البحث: الأهلي بيكتب شهر-يوم («يوم 10-09» = 9 أكتوبر) — ما اتغيرش
        assertEquals("2026-10-09", egyptTransactionDate(nbe.replace("23/10", "10-09"), "2026-10-10T08:00:00.000Z"))
    }

    @Test fun futureDayWithoutYearIsNotGuessed() {
        assertNull(egyptTransactionDate(nbe.replace("23/10", "25/10"), "2026-10-20T08:00:00.000Z"))
    }

    @Test fun wholeMessageParsesWithTheInferredDate() {
        when (val r = parseEgyptBankSms(BankSmsMessage(sender = "NBE", receivedAt = "2026-10-23T14:10:00.000Z", body = nbe), 1)) {
            is SmsParseResult.Ok -> assertEquals("2026-10-23", r.row.date)
            is SmsParseResult.Rejected -> assertTrue(r.reason != uiText(TextKey.SMS_DATE_UNCLEAR), "التاريخ اتقري: ${r.reason}")
        }
    }
}
