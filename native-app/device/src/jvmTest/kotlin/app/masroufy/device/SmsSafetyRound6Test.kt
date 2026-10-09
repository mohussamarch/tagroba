package app.masroufy.device

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.SmsParseResult
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الجولة السادسة — فلتر الجهاز (§72: **ضياع عملية حقيقية مش مقبول**): الحارس بقى بيتفحص على **أول الرسالة** بس قبل الحفظ (سطر إعلان
 * أو تنبيه في آخر عملية حقيقية ما بيرميهاش)، والرمز بيترمي **لو فيه رمز فعلًا** (سطر «لا تشارك الرمز/Never share your OTP» من غير
 * رقم مش رمز). كل الرسايل مخترعة (NAJM BOOKS · ZEPHYR KIOSK · 0100000xxxx …).
 */
class SmsSafetyRound6Test {
    private val received = "2026-03-05T09:00:00Z"

    /** عمليات خلصت شكلها مش في القايمة وآخرها إعلان أو تنبيه — كانت بتترمي في صمت. */
    private val promoOrNoticeTail = listOf(
        "Purchase of SAR 230.00 at NAJM BOOKS with mada card **8063 on 2026-03-05 11:14. You will earn 23 points.",
        "تم إيداع راتب شهر مارس بمبلغ 9,800.00 ر.س في حسابك رقم **2951 بتاريخ 2026-03-05. سيتم خصم قسط التمويل بتاريخ 2026-03-27",
        "Dear Customer, SAR 1,500.00 has been transferred from your account **2951 to SAHAR SAMPLE on 2026-03-05 10:20. The amount will be credited to the beneficiary within 2 hours.",
        "سحب نقدي بمبلغ 500.00 ر.س من الصراف OLAYA ATM 12 بطاقة *8063 بتاريخ 2026-03-05 11:02. احصل على 5% كاش باك عند الدفع ببطاقتك",
        "Salary credited: SAR 12,400.00 to account **2951 on 2026-03-05. Enjoy 0% fees on international transfers this month",
        "Refund of SAR 189.00 from NAJM BOOKS credited to card **5528 on 2026-03-05. Shop now with NAJM BOOKS",
        "تم شراء بمبلغ 120.00 ر.س من WADI CAFE ببطاقة مدى *8063 في 2026-03-05 09:10. اكسب نقاط مضاعفة عند الدفع بآبل باي",
        "تم تحويل 900.00 ر.س إلى SAHAR SAMPLE بنجاح بتاريخ 2026-03-05 10:20. خدمة التحويل الفوري متاحة على مدار الساعة ابتداءً من اليوم",
        "تم إيداع مرتبك بمبلغ 14,500.00 جم في حسابك رقم 7392 يوم 05/03/2026. استمتع بخصم 15% على مشترياتك ببطاقتك",
        "EGP 640.00 was spent on your card 7392 at SAMPLE PHARMACY on 05/03/2026. Points will be added to your account",
        "تم تحويل 1,000 جنيه من حسابك رقم 7392 إلى KARIM SAMPLE عبر انستاباي يوم 05/03/2026. اربح حتى 500 جنيه مع كل تحويل",
        "تم سحب 2,000 جنيه من ماكينة الصراف الآلي SAMPLE ATM ببطاقتك 7392 يوم 05/03/2026. سيتم احتساب رسوم 5 جنيه",
        "Your account **2951 has been debited SAR 312.50 for TAWAL ELECTRICITY bill on 2026-03-05. Next bill due on 2026-04-05",
        "تم خصم مبلغ 1,850.00 ر.س من حسابك **2951 قسط تمويل شخصي بتاريخ 2026-03-05. القسط القادم بتاريخ 2026-04-05",
        "Your account 7392 was debited with EGP 750.00 for SAMPLE INTERNET bill on 05/03/2026. Next payment due date 05/04/2026",
        "تم تحويل 180 جنيه إلى 01200007461 بنجاح من أورانج كاش. رصيدك 520 جنيه. اشحن كارت أورانج كاش واحصل على 10% كاش باك",
        "WE Pay: تم دفع فاتورة الإنترنت الأرضي بمبلغ 450 جنيه بنجاح يوم 08/10/2026. استمتع بخصم 20% على الباقات",
        "e& money: You received EGP 900.00 from 01100007462. Enjoy 5% cashback on bill payments this month",
        "InstaPay: EGP 1,750.00 sent to NADA TEST from account ending 6612 on 08/10/2026. A confirmation will be sent to the beneficiary",
        "تم دفع فاتورة كهرباء جنوب القاهرة بمبلغ 412.50 جنيه عن طريق فوري. رقم العملية 900000331. سيتم إرسال الإيصال على رقمك",
        "Fawry: Payment of EGP 199.00 for TEST INTERNET was successful. Reference 900000332. A receipt will be sent to your email",
        "Purchase of SAR 73.15 at ZEPHYR KIOSK with card *4417 on 2026-10-08. Points will be credited within 48 hours",
        "تمت عملية شراء بمبلغ 73.15 ر.س لدى كشك زفير بتاريخ 2026-10-08، سيتم إضافة النقاط خلال 48 ساعة",
        "Your account 5519 was debited SAR 73.15 for ZEPHYR KIOSK on 2026-10-08. Check your e-statement in the app",
        "Purchase SAR 73.15 at ZEPHYR KIOSK 2026-10-08. Pay it over 3 months - reply YES",
        "تم الدفع بمبلغ 412.60 جم لدى كشك زفير يوم 08/10/2026. لعرض كشف حسابك ادخل التطبيق",
    )

    /** سطر تحذير أمني من غير رمز — عملية حقيقية، مش رسالة رمز. */
    private val securityWarningTail = listOf(
        "تم تحويل 250 جنيه لرقم 01000007451 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 349. فودافون كاش عمرها ما هتطلب منك الرقم السري",
        "Your Debit Card **6620 had a Successful transaction of EGP 145.00 @TEST BAKERY,your available bal.EGP 2,310.00. Never share your OTP or PIN with anyone",
        "تم استلام 1,000 جنيه من رقم 01100007452 رصيدك الحالي 1,240 جنيه. اوعى تدي كود التحقق لأي حد",
        "e& money: تم دفع 85 جنيه لـ TEST KIOSK بكارت المحفظة يوم 08/10/2026. لا تشارك الـ OTP مع أي حد",
        "تم تحويل 3,000 جنيه بنجاح إلى حساب ينتهي بـ 9921 عبر انستاباي يوم 08/10/2026. لا تشارك الرقم السري الخاص بالتطبيق",
        "You have sent EGP 220.00 to 01000007615. Ref 900000615. Do not share your one-time password with anyone",
        "PoS Purchase\nAmount: SAR 73.15\nAt: ZEPHYR KIOSK\n2026-10-08 09:15\nNever share your OTP with anyone",
        "شراء عبر نقاط البيع\nمبلغ: 73.15 ر.س\nلدى: كشك زفير\nفي: 2026-10-08 09:15\nلا تشارك رمز التحقق مع أي شخص",
        "تم خصم 412.60 جم من بطاقة الخصم المباشر رقم 4417 عند كشك زفير يوم 10-08 الساعة 09:15. لا تشارك الرقم السري مع أحد",
        "Your account ending in 5519 has been credited with EGP 1,875.00 on 08/10/2026 from SAMIR DEMO. Ref: 552190 (IPN Inward Transfer). Never disclose your OTP",
    )

    /** عملة أجنبية اسمها أو اختصارها ما كانش في القايمة (§75-12: تستنى وتتسأل، مش تضيع). */
    private val foreignNames = listOf(
        "شراء دولي\nبطاقة: 8063;مدى\nمبلغ: 1,500.00 بات\nدولة: TH\nلدى: SAMPLE BANGKOK\nفي: 26-03-05 10:00",
        "شراء إنترنت\nمبلغ: 45,000 وون\nلدى: SAMPLE SEOUL\nفي: 2026-03-05 10:00",
        "شراء إنترنت\nمبلغ: 850.00 بيزو مكسيكي\nلدى: SAMPLE CANCUN\nفي: 2026-03-05 10:00",
        "شراء إنترنت\nمبلغ: 320.00 كرونة\nلدى: SAMPLE OSLO\nفي: 2026-03-05 10:00",
        "Online Purchase\nAmount: 12,500 Ft\nAt: SAMPLE BUDAPEST\nOn: 2026-03-05 10:00",
        "تم خصم 1,500.00 بات من بطاقة الائتمان رقم 7392 عند SAMPLE BANGKOK يوم 03-05",
        "Your credit card ending with #7392 was charged for 150,000 L.L at SAMPLE BEIRUT on 05/03/26",
    )

    @Test fun realTransactionsWithATailAreStoredNotDropped() {
        val dropped = (promoOrNoticeTail + securityWarningTail + foreignNames).filter { SmsSafety.sanitize(it) == null }
        assertTrue(dropped.isEmpty(), "dropped before storage:\n" + dropped.joinToString("\n"))
    }

    @Test fun storedMessagesAreNeverRecordedWrongly() {
        // المحفوظ بيتقري أو بيستنى — والأجنبي ما بيتسجلش في أي بلد (§75-12)
        for (body in foreignNames) {
            val stored = assertNotNull(SmsSafety.sanitize(body))
            for (parse in listOf(::parseBankSms, ::parseEgyptBankSms)) {
                val r = parse(BankSmsMessage("TESTBANK", received, stored), 1)
                assertTrue(r !is SmsParseResult.Ok || !r.row.shape.clear, "foreign recorded: $body")
            }
        }
    }

    @Test fun codesInNewWordingsAreStillDropped() {
        for (body in listOf(
            "رقم التعريف المؤقت 662190 لعملية شراء بمبلغ 230.00 ر.س لدى NAJM BOOKS. لا تشاركه",
            "Online Purchase\nAmount: SAR 649.00\nAt: NAJM ELECTRONICS\nApproval PIN: 553901\nOn: 2026-03-05 11:05",
            "شراء إنترنت\nمبلغ: 649.00 ر.س\nلدى: NAJM ELECTRONICS\nرقم التعريف المؤقت: 553901\nفي: 2026-03-05 11:05",
            "mada Pay activation number 662190 for card *8063. A SAR 1.00 test charge applies",
            "Secure code for EGP 1,180.00 at SAMPLE MALL: 662190. Valid 5 minutes",
            "رقم التعريف المؤقت 662190 لإتمام دفع 1,180.00 جم لـ SAMPLE MALL",
        )) {
            assertNull(SmsSafety.sanitize(body), body)
        }
    }

    /**
     * سطر الاعتراض بالتليفون **بعد الحجب** («اتصل ••••0000»): كان بيبقى سطر مش معروف فالرسالة الحقيقية تستنى، وأي جملة من غير رقم
     * كانت بتعدّي — العكس. دلوقتي السطر المعروف بيفضل معروف بعد الحفظ.
     */
    @Test fun aKnownDisputeFooterStaysKnownAfterRedaction() {
        val body = "شراء عبر نقاط البيع\nبطاقة: 8063*;مدى\nمبلغ: 64.00 ر.س\nلدى: WADI CAFE\nفي: 2026-03-05 09:10\nللاعتراض على العملية اتصل 8001240000"
        val stored = assertNotNull(SmsSafety.sanitize(body))
        assertTrue("••••0000" in stored, stored)
        val row = assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", received, stored), 1)).row
        assertTrue(row.shape.clear, "${row.shape.wire}: $stored")
    }
}
