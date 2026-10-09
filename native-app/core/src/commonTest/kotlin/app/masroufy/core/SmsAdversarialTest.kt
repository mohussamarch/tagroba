package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * جدول الرسايل العدائية (`SmsAdversarialCases`) — اختبار دايم (الجولة التانية من المراجعة): المراجع لقى 43 رسالة مش عملية أو ملتبسة
 * بتعدّي القارئ. كل رسالة بتتجرب على **قارئ البلدين** وعلى فلتر الجهاز (`SmsVocabulary.ignoreBeforeStorage` — نفسه اللي `SmsSafety`
 * بيرمي بيه قبل الحفظ).
 */
class SmsAdversarialTest {
    private val guards = setOf(TextKey.SMS_OFFER, TextKey.SMS_SENSITIVE, TextKey.SMS_DECLINED, TextKey.SMS_NOT_TRANSACTION).map { uiText(it) }.toSet()
    private val readers = listOf("SA" to ::parseBankSms, "EG" to ::parseEgyptBankSms)

    @Test fun theTableIsBigEnoughAndCoversBothCountries() {
        val ignore = SmsAdversarialCases.mustIgnore
        assertTrue(ignore.size >= 50, "at least 50 must-ignore messages (now ${ignore.size})")
        assertTrue(ignore.count { it.first == "EG" } >= 15, "Egyptian must-ignore messages")
        assertEquals(ignore.size, ignore.map { it.second }.toSet().size, "duplicate message in the table")
    }

    /** رمز · تفويض · مرفوضة · معلقة · طلب · عرض · تقسيط · تذكير · رصيد بس ⇒ **حارس مقصود** في القارئين، مش رفض بالصدفة. */
    @Test fun everyMustIgnoreMessageIsStoppedByADeliberateGuardInBothReaders() {
        val leaks = mutableListOf<String>()
        for ((country, body) in SmsAdversarialCases.mustIgnore) {
            for ((reader, parse) in readers) {
                when (val r = parse(smsMessage(body), 1)) {
                    is SmsParseResult.Ok -> leaks += "[$country/$reader] BOOKED ${r.row.amountMinor} ${r.row.direction}: $body"
                    is SmsParseResult.Rejected -> if (r.reason !in guards) leaks += "[$country/$reader] by accident «${r.reason}»: $body"
                }
            }
        }
        assertTrue(leaks.isEmpty(), leaks.joinToString("\n"))
    }

    /**
     * الجولة الخامسة (§72: الانتظار مقبول، الضياع لا): فلتر الجهاز بيرمي الرسالة **إلا** لو أولها عنوان أو قالب بنك معروف — ساعتها بتتحفظ
     * وتستنى في «المرفوضة» بسبب الحارس (القارئين بيرفضوها — الاختبار اللي فوق). رمز التحقق بيترمي دايمًا.
     */
    @Test fun theDeviceFilterDropsEveryMustIgnoreMessageUnlessItHasAKnownHead() {
        val kept = SmsAdversarialCases.mustIgnore.filterNot { (_, body) ->
            SmsVocabulary.ignoreBeforeStorage(body) ||
                (SmsVocabulary.hasKnownHead(body) && SmsVocabulary.ignoreReason(body) != TextKey.SMS_SENSITIVE)
        }
        assertTrue(kept.isEmpty(), "kept for storage: ${kept.joinToString("\n")}")
        val otp = SmsAdversarialCases.mustIgnore.filter { SmsVocabulary.ignoreReason(it.second) == TextKey.SMS_SENSITIVE }
        assertTrue(otp.isNotEmpty() && otp.all { SmsVocabulary.ignoreBeforeStorage(it.second) }, "an OTP was kept for storage")
    }

    /**
     * الجولة التالتة: عمليات حقيقية فيها سطر تحذير أو إعلان أو كلمة شبه الحارس ⇒ **بتتسجل** بقارئ بلدها بمبلغها، وفلتر الجهاز
     * ما بيرميهاش (كانت بتترمي قبل الحفظ وما تظهرش حتى في «مستنية تأكيدك»).
     */
    @Test fun realTransactionsWithFootersAreBookedAndKept() {
        val wrong = mutableListOf<String>()
        for ((country, body, amount) in SmsAdversarialCases.mustBook) {
            val parse = if (country == "SA") ::parseBankSms else ::parseEgyptBankSms
            when (val r = parse(smsMessage(body), 1)) {
                is SmsParseResult.Ok -> if (r.row.amountMinor != amount || r.row.date != SMS_TX_DAY) wrong += "[$country] ${r.row.amountMinor} ${r.row.date}: $body"
                is SmsParseResult.Rejected -> wrong += "[$country] rejected «${r.reason}»: $body"
            }
            if (SmsVocabulary.ignoreBeforeStorage(body)) wrong += "[$country] dropped before storage: $body"
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    /** ملتبسة: أي رفض مقبول (بتستنى المالك) — بس **ما تتسجلش** في أي بلد، ولا تستنى «المبلغ المحلي» في البلدين. */
    @Test fun ambiguousMessagesAreNeverBookedAutomatically() {
        val booked = mutableListOf<String>()
        for ((country, body) in SmsAdversarialCases.mustNotBook) {
            val results = readers.associate { (reader, parse) -> reader to parse(smsMessage(body), 1) }
            for ((reader, r) in results) if (r is SmsParseResult.Ok) booked += "[$country/$reader] ${r.row.amountMinor} ${r.row.direction} ${r.row.date}: $body"
            if (results.values.count { (it as? SmsParseResult.Rejected)?.foreign != null } > 1) booked += "[$country] waiting in both countries: $body"
        }
        assertTrue(booked.isEmpty(), booked.joinToString("\n"))
    }
}
