package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.SmsKind.BILL
import app.masroufy.core.SmsKind.CARD_PAYMENT
import app.masroufy.core.SmsKind.CASH_WITHDRAWAL
import app.masroufy.core.SmsKind.OTHER
import app.masroufy.core.SmsKind.PURCHASE
import app.masroufy.core.SmsKind.REFUND
import app.masroufy.core.SmsKind.SALARY
import app.masroufy.core.SmsKind.TRANSFER_IN
import app.masroufy.core.SmsKind.TRANSFER_OUT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * رسايل البنوك والمحافظ المصرية (شكل لكل قالب في `research/banks/egypt-sms-formats.json`). كل الرسايل مخترعة (`SmsFormatsKit.kt`).
 * الفخاخ اللي بتتمسك هنا: «جم/ج/LE/ج.م» · الأهلي من غير مسافات وتاريخ من غير سنة · سحب الصرّاف بنفس نص الشراء · التجاري الدولي
 * الاتجاه من «من حسابك»/«إلى حسابك» بس · فودافون كاش الاسم + الموبايل (آخر 4 بس) · مصاريف الخدمة مش المبلغ.
 */
class EgyptSmsFormatsTest {
    private val m = "TEST GROCER"
    private val ar = "سامي التجريبي"
    private val en = "SAMI TESTER"
    private val ref = "123456789012"

    private val cases = listOf(
        // ── الأهلي المصري ──
        SmsCase(
            "nbe credit card (MM-DD, no year)", "تم خصم 64.25 جم من بطاقة الائتمان رقم 6604 عند $m يوم 03-05 الساعة 09:10 المتاح 4,100.00 جم للمزيد اتصل ب 19623.",
            6425, OUT, PURCHASE, m,
        ),
        SmsCase(
            "nbe debit card, missing spaces", "تم خصم 64.25 EGP من بطاقة الخصم المباشر رقم6604 عند$m يوم05/03/26 الساعة09:10 المتاح4,100.00EGP للمزيد اتصل ب 19623",
            6425, OUT, PURCHASE, m,
        ),
        SmsCase(
            "nbe atm = same text, merchant «NBE ATM»", "تم خصم 300.00 EGP من بطاقة الخصم المباشر رقم6604 عندNBE ATM0912 يوم05/03/26 الساعة09:10 المتاح4,100.00EGP للمزيد اتصل ب ١٩٦٢٣",
            30000, OUT, CASH_WITHDRAWAL,
        ),
        SmsCase(
            "nbe instapay in", "تم إضافة تحويل لحظي لحسابكم رقم 1188 بمبلغ 900.00 جم من $ar رقم مرجعي $ref يوم 03-05 الساعة 09:10 للمزيد اتصل بـ 19623",
            90000, IN, TRANSFER_IN, party = ar,
        ),
        SmsCase(
            "nbe instapay out (masked name)", "تم تنفيذ تحويل لحظي من حسابكم رقم 1188 بمبلغ 900.00 جم إلى سام* الت***** رقم مرجعي $ref يوم 03-05 الساعة 09:10 للمزيد اتصل بـ 19623",
            90000, OUT, TRANSFER_OUT, party = "سام* الت*****",
        ),
        SmsCase("nbe prepaid card in", "تم إضافة تحويل لحظي لبطاقتكم مسبقة الدفع بمبلغ 900.00 جم من $ar رقم مرجعي $ref يوم 03-05 الساعة 09:10", 90000, IN, TRANSFER_IN, party = ar),
        SmsCase("nbe-like in, no date (arrival day)", "تم تحويل مبلغ 900.00 جم لحسابكم المنتهي بـ 1188 من $ar عبر شبكة المدفوعات اللحظية مرجع رقم $ref", 90000, IN, TRANSFER_IN, party = ar),
        SmsCase("nbe english in", "Instant transfer of 900.00 EGP received from $en. Reference: $ref.", 90000, IN, TRANSFER_IN, party = en),
        SmsCase("instapay short in", "تم إضافة تحويل لحظي لحسابكم بمبلغ 900.00 جنيه رقم مرجعي $ref يوم 05-03-2026", 90000, IN, TRANSFER_IN),
        SmsCase("instapay short out", "تم تنفيذ تحويل لحظي من حسابكم بمبلغ 900.00 جنيه رقم مرجعي $ref يوم 05-03-2026", 90000, OUT, TRANSFER_OUT),
        // ── التجاري الدولي ──
        SmsCase(
            "cib credit card", "Your credit card ending with#6604 was charged for EGP 64.25 at $m on 05/03/26 at 09:10. Card available limit is EGP 4,100.00.",
            6425, OUT, PURCHASE, m,
        ),
        SmsCase("cib refunded", "The transaction on your credit card#6604 from $m with EGP 12.40 on 05/03/26 at 09:10 has been refunded.", 1240, IN, SmsKind.RETURNED, m),
        SmsCase("cib refund ar (no date)", "لقد تم رد EGP12.40 على بطاقتكم الائتمانية المنتهية بـ# 6604 من $m", 1240, IN, SmsKind.RETURNED, m),
        SmsCase(
            "cib debit card", "تم خصم مبلغ EGP 64.25 من بطاقة الخصم المباشر المنتهية بـ **6604 عند $m في 05/03/26 09:10 ، الرصيد المتاح EGP 4,100.00",
            6425, OUT, PURCHASE, m,
        ),
        SmsCase("cib card payment", "تم سداد مبلغ 1,500.00 جم فى بطاقتكم الائتمانية المنتهية بـ 6604 بتاريخ 05/03/26", 150000, OUT, CARD_PAYMENT),
        SmsCase(
            "cib instapay out = «من حسابك»", "يرجى العلم انه تم تنفيذ تحويل لحظي بمبلغ 900.00 جم من حسابك المنتهي بـ ********1188 برقم مرجعي $ref بتاريخ 05/03/26 09:10 للمزيد، برجاء الاتصال بـ 19666",
            90000, OUT, TRANSFER_OUT,
        ),
        SmsCase(
            "cib instapay in = «إلى حسابك»", "يرجى العلم انه تم تنفيذ تحويل لحظي بمبلغ 900.00 جم إلى حسابك المنتهي بـ ********1188 من $ar برقم مرجعي $ref بتاريخ 05/03/26 09:10 للمزيد، برجاء الاتصال بـ 19666",
            90000, IN, TRANSFER_IN, party = ar,
        ),
        SmsCase("cib salary", "عميلنا العزيز لقد تم تحويل مبلغ EGP7,400.00 على حسابكم لدينا من جهة العمل", 740000, IN, SALARY),
        SmsCase("cib english in", "You have received an IPN transfer of EGP 900.00 to account ending 1188 from $en. Ref No: $ref.", 90000, IN, TRANSFER_IN, party = en),
        SmsCase("cib-like in ج.م", "تم استلام تحويل لحظي بمبلغ 900.00 ج.م من $ar إلى حسابكم المنتهي بـ 1188. رقم المرجع $ref", 90000, IN, TRANSFER_IN, party = ar),
        // ── بنوك تانية ──
        SmsCase("banque misr in", "إيداع تحويل لحظي IPN بمبلغ 900.00 جم بحسابك رقم ...1188 من $ar مرجع $ref", 90000, IN, TRANSFER_IN, party = ar),
        SmsCase(
            "hsbc in", "Your account ending in 1188 has been credited with EGP 900.00 on 05/03/2026 from $en. Ref: $ref (IPN Inward Transfer)",
            90000, IN, TRANSFER_IN, party = en,
        ),
        SmsCase("kfh out (no party)", "IPN Transfer with EGP 900.00 deducted on 05/03 09:10 from your AC ending with 188 with Ref# $ref. For info call 19533", 90000, OUT, TRANSFER_OUT),
        SmsCase("kfh returned", "IPN Transfer dated 05/03 09:10 with EGP 900.00 returned with Ref# $ref. For info call 19533", 90000, IN, SmsKind.RETURNED),
        SmsCase("arab bank card", "A Trx using Card XXXX6604 from $m for EGP 64.25 on 05/03/2026 at 09:10 GMT+2. Available balance is EGP 4,100.00.", 6425, OUT, PURCHASE, m),
        // الجولة التامنة: فلوس داخلة على كارت ائتمان = سداد البطاقة (CARD_PAYMENT) — بتستنى في المحفظة البنكية (مش دخل)
        SmsCase("arab bank card credit", "تم قيد مبلغ 12.40 جنيه لبطاقتك الائتمانية رقم #6604", 1240, IN, CARD_PAYMENT),
        SmsCase("breadfast top-up", "You received EGP 300.00 on 05.03.26 at 09:10 to your card ending in ***6604.For details, please  contact Breadfast customer support via the app.", 30000, IN),
        // ── فودافون كاش ومحافظ تانية: الطرف = الاسم المسجل + آخر 4 من الموبايل ──
        SmsCase(
            "vf receive", "تم استلام مبلغ 900.00 جنيه من رقم 01000000777 المسجل بإسم $ar على رقم محفظتك  01000000888.\nرصيدك الحالي: 4,100.00 جنيه\nتاريخ العملية: 09:10 26-03-05\nرقم العملية: $ref\nتابع كل مصروفاتك من تاريخ المعاملات",
            90000, IN, TRANSFER_IN, party = ar, last4 = "0777",
        ),
        SmsCase(
            "vf receive english", "Mar 5, 2026 9:10:44 AM: Received EGP900.00 from 002010000009999 to Mobile Account Number 1188. Ref: $ref Available Balance: 4,100.00",
            90000, IN, TRANSFER_IN, party = "••••9999", last4 = "9999",
        ),
        SmsCase(
            "vf send (service fee is not the amount)", "تم تحويل 900.00 جنيه لرقم 01000000777 مصاريف الخدمة 1.00 جنيه رصيد حسابك فى فودافون كاش الحالي 4,100.00.\nتاريخ العملية: 09:10 26-03-05\nرقم العملية: $ref",
            90000, OUT, TRANSFER_OUT, party = "••••0777", last4 = "0777",
        ),
        SmsCase(
            "vf cash out «ج»", "تم سحب 300.00 ج بنجاح. رصيد حسابك في فودافون  كاش الحالي 4,100.00 جنيه. تاريخ العملية ‎26-03-05 09:10 رقم العملية $ref.",
            30000, OUT, CASH_WITHDRAWAL,
        ),
        SmsCase(
            "vf recharge LE", "You have successfully recharged 50.00 LE to the balance of 01000000777; your current Vodafone Cash balance is 4,100.00 LE;Trx date: 05/03/2026 09:10 Trx ID $ref.",
            5000, OUT, BILL,
        ),
        SmsCase("vf recharge incl. tax", "تم شحن رصيد موبايلك ب 50.00 بنجاح وخصم 57.00 من محفظتك شاملة الضريبة جنيه. رصيد حسابك في فودافون كاش الحالي 4,100.00", 5700, OUT, BILL),
        SmsCase("wallet receive", "تم استلام 900.00 جنيه من رقم 01100000777 رصيدك الحالي 4,100.00 جنيه رقم المرجع $ref", 90000, IN, TRANSFER_IN, party = "••••0777", last4 = "0777"),
        SmsCase("wallet send", "تم تحويل 900.00 جنيه لرقم 01100000777 رصيدك الحالي 4,100.00 جنيه Ref: $ref", 90000, OUT, TRANSFER_OUT, party = "••••0777", last4 = "0777"),
        SmsCase("generic english in", "Dear customer, IPN transfer of 900.00 EGP credited to account 1188 from $en. Reference: $ref", 90000, IN, TRANSFER_IN, party = en),
        SmsCase("generic arabic in", "تم إضافة مبلغ 900.00 جم إلى حسابك المنتهي بـ 1188 من $ar تحويل لحظي مرجع: $ref", 90000, IN, TRANSFER_IN, party = ar),
    )

    @Test fun everyEgyptianTemplateIsReadRight() {
        for (c in cases) checkCase(c, ::parseEgyptBankSms, Currency.EGP)
    }

    /**
     * §75-12 (قرار المالك: يتسجل ويسأل عن المبلغ المحلي): دولار أو يورو ⇒ ترفض «مش جنيه» ومعاها كل اللي اتقري (ما بتتسجلش لوحدها).
     * الريال من غير جنيه في الرسالة = رسالة سعودية (من غير «مستنية»).
     */
    @Test fun foreignCurrencyWaitsForTheLocalAmount() {
        val usd = assertIs<SmsParseResult.Rejected>(
            parseEgyptBankSms(smsMessage("A Trx using Card XXXX6604 from $m for USD 14.90 on 05/03/2026 at 09:10 GMT+2. Available balance is EGP 4,100.00."), 1),
        )
        assertEquals(uiText(TextKey.SMS_NOT_EGP), usd.reason)
        assertEquals(SmsForeignPending(SMS_TX_DAY, SmsForeignAmount("USD", 1490), OUT, m, PURCHASE, ownLast4 = "6604"), usd.foreign)
        val eur = assertIs<SmsParseResult.Rejected>(
            parseEgyptBankSms(smsMessage("Your credit card ending with#6604 was charged for EUR 14.90 at $m on 05/03/26 at 09:10. Card available limit is EGP 4,100.00."), 1),
        )
        assertEquals(SmsForeignAmount("EUR", 1490), eur.foreign?.foreign)
        val saudi = assertIs<SmsParseResult.Rejected>(parseEgyptBankSms(smsMessage("شراء\nبطاقة:6604;مدى\nمبلغ:SAR 64.25\nلدى:$m\nفي:26-03-05 09:10"), 1))
        assertEquals(null, saudi.foreign)
    }

    /** كل رسالة مصرية بتترفض من قارئ السعودية (ما تتسجلش في البلدين). */
    @Test fun egyptianMessagesAreNotReadAsSaudi() {
        for (c in cases) assertIs<SmsParseResult.Rejected>(parseBankSms(smsMessage(c.body), 1), c.name)
    }
}
