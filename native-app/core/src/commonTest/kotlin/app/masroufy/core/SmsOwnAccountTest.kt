package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * آخر 4 أرقام **حسابك أو كارتك** في الرسالة (§75-11: بنك واحد بحسابين ⇒ التفرقة بآخر 4). حسابك = 1188 · كارتك = 6604 ·
 * الطرف التاني = 9021 (لازم **ما** يطلعش هنا) · حسابك التاني في «بين حساباتك» = 2277 (هو الطرف، مش الحساب اللي الرسالة عنه).
 * كل الرسايل مخترعة.
 */
class SmsOwnAccountTest {
    private val m = "TEST GROCER"
    private val ar = "سامي التجريبي"

    private val saudi = listOf(
        "card only" to ("شراء\nبطاقة:6604;مدى(أثير)\nمبلغ:SAR 64.25\nلدى:$m\nفي:26-03-05 09:10" to "6604"),
        "account wins over card" to ("شراء انترنت\nبطاقة:6604;مدى\nمن:1188\nمبلغ:SAR 64.25\nلدى:$m\nفي:26-03-05 09:10" to "1188"),
        "rajhi 2026 card" to ("شراء\nعبر:6604;مدى-ابل باي\nبـSAR 64.25\nلـ$m\n26/3/5 09:10" to "6604"),
        "snb card line" to ("شراء نقاط بيع ApplePay\nبـSAR 64.25\nمن $m\nمدى *6604\nفي 09:10 05/03/26" to "6604"),
        "alinma account line" to ("شراء عبر Apple Pay\nمبلغ: ريال سعودى 64.25\nبطاقة مدى: 6604*\nحساب: *1188\nمن: $m\nفي: 05/03/2026 09:10" to "1188"),
        "out: من = you, الى = them" to ("حوالة داخلية صادرة\nمن:1188\nمبلغ:SAR 900.00\nالى:$ar\nالى:9021\nفي:26-03-05 09:10" to "1188"),
        "in: الى = you, من = them" to ("حوالة داخلية واردة\nمبلغ:SAR 900.00\nالى:1188\nمن:$ar\nمن:9021\nفي:26-03-05 09:10" to "1188"),
        "rajhi 2026 in «لـ1188»" to ("حوالة داخلية واردة بـSR 900\nلـ1188\nمن9021;$ar\n26/3/5 09:10" to "1188"),
        "bsf out «خصمت من حساب»" to ("عملية حوالة مالية صادرة مقبولة\nخصمت من حساب ****1188\nإلى $ar\nآيبان ****9021\nالقيمة SAR 900.00\nفي05-03-2026 09:10:44" to "1188"),
        "d360 in: IBAN is yours" to ("Incoming Transfer: Riyad Bank\nAmount: SAR 900.00\nFrom: *9021\nIBAN: *1188\nat: 05/03/2026 09:10" to "1188"),
        "d360 out: From is yours" to ("Outgoing Local Transfer\nFrom : ****1188\nAmount: SAR 900.00\nTo: SAMI TESTER\nIBAN: ****9021 - Riyad Bank\nOn: 05/03/2026 09:10" to "1188"),
        "stc account" to ("Bill Payment (SADAD)\nAmount: 150.00 SAR\nBiller: TEST WATER CO\nFrom Account: ***1188\nOn: 2026-03-05 09:10:44" to "1188"),
        "stc reversal «الى: ***6604; VISA»" to ("عكس عملية\nالى: ***6604; VISA\n12.40 SAR :المبلغ\nفي: $m\nبتاريخ: 2026-03-05 09:10:44" to "6604"),
        "own transfer: source not printed" to ("حوالة بين حساباتك\nمبلغ: SAR 900.00\nالى: 2277\n26-03-05" to null),
    )

    private val egypt = listOf(
        "nbe card" to ("تم خصم 64.25 جم من بطاقة الائتمان رقم 6604 عند $m يوم 03-05 الساعة 09:10 المتاح 4,100.00 جم" to "6604"),
        "nbe glued card" to ("تم خصم 64.25 EGP من بطاقة الخصم المباشر رقم6604 عند$m يوم05/03/26 الساعة09:10 المتاح4,100.00EGP" to "6604"),
        "nbe account" to ("تم إضافة تحويل لحظي لحسابكم رقم 1188 بمبلغ 900.00 جم من $ar رقم مرجعي 123456789012 يوم 03-05 الساعة 09:10" to "1188"),
        "cib masked account" to ("يرجى العلم انه تم تنفيذ تحويل لحظي بمبلغ 900.00 جم من حسابك المنتهي بـ ********1188 برقم مرجعي 123456789012 بتاريخ 05/03/26 09:10" to "1188"),
        "cib card #" to ("Your credit card ending with#6604 was charged for EGP 64.25 at $m on 05/03/26 at 09:10." to "6604"),
        "arab bank card" to ("A Trx using Card XXXX6604 from $m for EGP 64.25 on 05/03/2026 at 09:10 GMT+2." to "6604"),
        "hsbc account" to ("Your account ending in 1188 has been credited with EGP 900.00 on 05/03/2026 from SAMI TESTER. Ref: 123456789012" to "1188"),
        "vf own wallet number (not the sender's)" to ("تم استلام مبلغ 900.00 جنيه من رقم 01000000777 المسجل بإسم $ar على رقم محفظتك  01000000888.\nتاريخ العملية: 09:10 26-03-05" to "0888"),
        "vf english account" to ("Mar 5, 2026 9:10:44 AM: Received EGP900.00 from 002010000009999 to Mobile Account Number 1188. Ref: 123456789012" to "1188"),
        "kfh 3-digit tail is not enough" to ("IPN Transfer with EGP 900.00 deducted on 05/03 09:10 from your AC ending with 188 with Ref# 123456789012." to null),
    )

    @Test fun saudiMessagesNameYourAccountOrCard() {
        for ((name, case) in saudi) {
            val row = assertIs<SmsParseResult.Ok>(parseBankSms(smsMessage(case.first), 1), name).row
            assertEquals(case.second, row.ownLast4, name)
        }
    }

    @Test fun egyptianMessagesNameYourAccountCardOrWallet() {
        for ((name, case) in egypt) {
            val row = assertIs<SmsParseResult.Ok>(parseEgyptBankSms(smsMessage(case.first), 1), name).row
            assertEquals(case.second, row.ownLast4, name)
        }
    }

    /** الرقم هو هو بعد فلتر الجهاز (الأرقام الطويلة بتتقص لـ«••••» + آخر 4 قبل الحفظ). */
    @Test fun sameDigitsAfterTheDeviceRedaction() {
        val body = redactSms("تم استلام مبلغ 900.00 جنيه من رقم 01000000777 المسجل بإسم $ar على رقم محفظتك  01000000888.\nتاريخ العملية: 09:10 26-03-05")
        assertEquals("0888", assertIs<SmsParseResult.Ok>(parseEgyptBankSms(smsMessage(body), 1)).row.ownLast4)
    }

    /** قوالب زيادة: كلمات استرداد الراجحي · وزارة الداخلية · كارت فيزا إس تي سي · أورانج كاش. */
    @Test fun moreTemplateInstances() {
        val more = listOf(
            SmsCase("rajhi استرجاع", "استرجاع\nبطاقة:6604;مدى\nمبلغ:SAR 12.40\nلدى:$m\nفي:26-03-05 09:10", 1240, Direction.IN, SmsKind.REFUND, m),
            SmsCase("rajhi إرجاع", "إرجاع\nبطاقة:6604;مدى\nمبلغ:SAR 12.40\nالتاجر:$m\nفي:26-03-05 09:10", 1240, Direction.IN, SmsKind.REFUND, m),
            SmsCase("rajhi كاش باك", "كاش باك\nبطاقة:6604;مدى\nمبلغ:SAR 12.40\nلدى:$m\nفي:26-03-05 09:10", 1240, Direction.IN, SmsKind.REFUND, m),
            SmsCase("rajhi moi", "مدفوعات وزارة الداخلية\nمن:1188\nمبلغ:SAR 150.00\nالجهة:الجوازات\nالخدمة:تجديد\nفي:26-03-05 09:10", 15000, Direction.OUT, SmsKind.BILL),
            SmsCase("stc visa", "VISA Purchase\nVia: *6604\nAmount: 64.25 SAR\nFrom: $m\nAt: 2026-03-05 09:10:44", 6425, Direction.OUT, SmsKind.PURCHASE, m),
        )
        for (c in more) checkCase(c, ::parseBankSms, Currency.SAR)
        checkCase(
            SmsCase(
                "orange receive", "تم استلام مبلغ 900.00 جنيه من رقم 01200000777 رصيدك الحالي 4,100.00 جنيه كود العملية 123456789012",
                90000, Direction.IN, SmsKind.TRANSFER_IN, party = "••••0777", last4 = "0777",
            ),
            ::parseEgyptBankSms, Currency.EGP,
        )
    }
}
