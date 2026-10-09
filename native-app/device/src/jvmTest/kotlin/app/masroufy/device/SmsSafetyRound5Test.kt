package app.masroufy.device

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.SmsParseResult
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الجولة الخامسة — فلتر الجهاز (قاعدة المالك §72: **الانتظار مقبول، ضياع عملية حقيقية مش مقبول**): رسايل المراجعة العدائية اللي كانت
 * **بتترمي قبل الحفظ** (ما تتسجلش وما تستناش وما تتعدش) لازم تتحفظ دلوقتي — والقارئ يسجلها أو يخليها تستنى. الرمز لسه بيترمي.
 * كل الرسايل مخترعة.
 */
class SmsSafetyRound5Test {
    /** عمليات خلصت بصيغة من غير كلمة حركة كان الفلتر يعرفها، أو بعملة مكتوبة بشكل تاني. */
    private val completed = listOf(
        "تمت إضافة مبلغ 2,000.00 جم إلى حسابك رقم 4460 يوم 05/03/2026",
        "تمت إضافة 500.00 ريال إلى محفظتك من بطاقة *5208 بتاريخ 2026-03-05",
        "تم استقطاع مبلغ 1,250.00 ر.س من حسابك 7731 قسط التمويل الشهري بتاريخ 2026-03-05",
        "تم تحصيل مبلغ 230.40 ر.س من حسابك 7731 لصالح QAMAR TELECOM بتاريخ 2026-03-05",
        "استلمت 500.00 ريال من MONA EXAMPLE بتاريخ 2026-03-05",
        "إشعار دائن\nالمبلغ: 2,000.00 ر.س\nحساب: *7731\nالتاريخ: 2026-03-05",
        "You paid SAR 87.30 to NOVA MART with card *5208 on 2026-03-05 10:42",
        "SAR 500.00 withdrawn from account *7731 on 2026-03-05",
        "EGP 2,000.00 withdrawn from your account 4460 on 05/03/2026",
        "تم إرسال مبلغ 750.00 جم إلى KARIM DEMO عبر انستاباي يوم 05/03/2026",
        "أضيف مبلغ 2,000.00 جم إلى حسابك رقم 4460 يوم 05/03/2026",
        "Your debit card 5208 was used for EGP 1,250.00 at NOVA MART on 05/03/2026",
        "Card 5208 POS EGP 1,250.00 NOVA MART 05/03/2026 09:40",
        "تم استبدال 5,000 نقطة بمبلغ 50.00 ر.س في بطاقتك *5208\n2026-03-05",
        "شراء عبر نقاط البيع\nمبلغ: 87.30 ر. س\nلدى: NOVA MART\nفي: 2026-03-05 10:42",
        // إنجليزي المحافظ واللهجة المصرية
        "InstaPay: EGP 950.00 sent to HODA TEST (hoda.t@instapay) from account ending 3344 on 08/10/2026 21:10. Ref 900000131",
        "You have sent EGP 500.00 to 01000003456 successfully. Ref 900000221",
        "You paid EGP 85.00 to TEST CAFE using your Vodafone Cash card on 08/10/2026",
        "You spent EGP 120.00 at TEST BAKERY with your wallet card. Remaining EGP 640.00",
        "استلمت 500 جنيه من 01000001113 على محفظتك. رصيدك 1,500 جنيه",
        "وصلك 500 جنيه من 01000001111 على محفظة فودافون كاش. رصيدك 760 جنيه",
        "اتحولك 500 جنيه من 01000001112 على محفظتك. رصيدك 1,260 جنيه",
        "اتحول مرتبك 9,500 جنيه على حسابك 3344 يوم 08/10/2026",
        "رصيدك الحالي 2,300 جنيه بعد تحويل 700 جنيه لرقم 01000004040",
        "Your Vodafone Cash balance is EGP 2,300.00 after receiving EGP 700.00 from 01000004041",
        // «المعلقة» اسم مطعم مش «معلقة»
        "تم خصم 185.00 جم من بطاقة الخصم المباشر رقم 4455 عند مطعم المعلقة الذهبية يوم 10-08 الساعة 14:30 المتاح 2,115.00 جم",
    )

    /** عملية أجنبية (§75-12 — لازم تستنى وتتسأل، مش تضيع): اختصار أو رمز عملة. */
    private val foreign = listOf(
        "Online Purchase\nAmount: KD 12.500\nAt: ORBIT BOOKS\n2026-03-05 10:42",
        "Online Purchase\nAmount: 950.00 TL\nAt: ORBIT BOOKS\n2026-03-05 10:42",
        "Online Purchase\nAmount: ₩45,000\nAt: ORBIT BOOKS\n2026-03-05 10:42",
        "Online Purchase\nAmount: RM 45.00\nAt: ORBIT BOOKS\n2026-03-05 10:42",
        "Online Purchase\nAmount: QR 120.00\nAt: ORBIT BOOKS\n2026-03-05 10:42",
        "Online Purchase\nAmount: 120.00 ر.ق\nAt: ORBIT BOOKS\n2026-03-05 10:42",
        "Online Purchase\nAmount: 45.00 د.إ\nAt: ORBIT BOOKS\n2026-03-05 10:42",
        "Online Purchase\nAmount: 59,90 zł\nAt: ORBIT BOOKS\n2026-03-05 10:42",
        "تم خصم 25.00 استرليني من بطاقة الائتمان رقم 5208 عند ORBIT BOOKS يوم 03-05 الساعة 09:40",
        "Your credit card ending with#5208 was charged for 45.90 Dhs at LUMEN CAFE on 05/03/26 at 09:40",
    )

    /** عملية حقيقية تحت عنوان أو قالب معروف وفيها سطر إعلان أو تحذير يمسكه الحارس ⇒ بتتحفظ وتستنى في «المرفوضة». */
    private val knownHeadWithFooter = listOf(
        "شراء\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\n2026-03-05 11:20\nلعرض تفاصيل العملية افتح التطبيق",
        "PoS Purchase\nAmount: SAR 87.40\nAt: TEST BAKERY\n2026-03-05 11:20\nSee latest offers in the app",
        "Outgoing Local Transfer\nAmount: SAR 1,250.00\nTo: TEST PERSON\nOn: 2026-03-05 11:20\nFunds will be credited to the beneficiary within 1 business day",
        "PoS Purchase\nAmount: SAR 87.40\nAt: TEST BAKERY\n2026-03-05 11:20\nRewards points will be added within 48 hours",
        "تم خصم 87.40 جم من بطاقة الخصم المباشر رقم 7731 عند TEST BAKERY يوم 03-05. لعرض عروض البنك زوروا التطبيق",
        "شراء\nمبلغ: SAR 87.40\nلدى: صالة العرض TEST\n2026-03-05 11:20",
        "شراء\nمبلغ: SAR 87.40\nلدى: TEST OFFER MART\n2026-03-05 11:20",
        "PoS Purchase\nAmount: SAR 87.40\nAt: TEST BAKERY\nAuthorization Code: 731905\n2026-03-05 11:20",
        "شراء\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\nرقم التفويض: 731905\n2026-03-05 11:20",
        "PoS Purchase\nAmount: SAR 87.40\nAt: TEST BAKERY\nTransaction code: 731905\n2026-03-05 11:20",
        "Your credit card ending with 7731 was charged for EGP 87.40 at TEST BAKERY on 05/03. Ref code: 73190",
        "حوالة واردة\nمبلغ: SAR 1,250.00\nمن: TEST PERSON\n2026-03-05 11:20\nلطلب كشف حساب أرسل 1",
        "Your credit card ending with 7731 was charged for EGP 87.40 at TEST BAKERY on 05/03. Next payment due date 25/03",
        // فودافون كاش · QNB بإعلان في الآخر
        "تم استلام مبلغ 320.00 جنيه من رقم 01000001234 المسجل باسم هاني الوهمي على رقم محفظتك 01000009900.\nرصيدك الحالي: 820.00 جنيه\n" +
            "تاريخ العملية: 14:00 26-10-08\nرقم العملية: 900000101\nادفع فواتيرك من فودافون كاش واحصل على 10% كاش باك",
        "تم تحويل 450 جنيه لرقم 01000002345 مصاريف الخدمة 1 جنيه رصيد حسابك فى فودافون كاش الحالي 500.\nتاريخ العملية: 14:00 26-10-08\n" +
            "رقم العملية: 900000102\nاستمتع بخصم 20% على مشترياتك أونلاين بكارت فودافون كاش",
        "You have successfully recharged 100.00 LE to the balance of 01000003456; your current Vodafone Cash balance is 400.00 LE;Trx date: 08/10/2026 14:00 Trx ID 900000110. Flex bundle valid until 07/11/2026",
        "تم سحب 500 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 120 جنيه. سيتم خصم مصاريف السحب 5 جنيه لاحقا",
        "Your Debit Card **4455 had a Successful transaction of EGP 90.00 @TEST JUICE,your available bal.EGP 1,240.00. Check our latest offers on the app",
        "Your Debit Card **4455 had a Successful transaction of EGP 220.00 @TEST JUICE,your available bal.EGP 1,020.00. Points will be added within 48 hours",
        "Your scheduled transfer of EGP 1,500.00 to account ending 2211 has been executed successfully on 08/10/2026. Ref 900000220",
    )

    @Test fun realTransactionsAreStoredNotSilentlyDropped() {
        val dropped = (completed + foreign + knownHeadWithFooter).filter { SmsSafety.sanitize(it) == null }
        assertTrue(dropped.isEmpty(), "dropped before storage:\n" + dropped.joinToString("\n"))
    }

    /** المحفوظ بيتقري أو بيستنى — والأجنبي بالذات **ما بيتسجلش** في أي بلد (§75-12). */
    @Test fun storedForeignPurchasesWaitInsteadOfRecording() {
        for (body in foreign) {
            val stored = SmsSafety.sanitize(body) ?: continue
            for (parse in listOf(::parseBankSms, ::parseEgyptBankSms)) {
                val r = parse(BankSmsMessage("TESTBANK", "2026-03-05T09:00:00Z", stored), 1)
                assertTrue(r !is SmsParseResult.Ok || !r.row.shape.clear, "foreign recorded: $body")
            }
        }
    }

    @Test fun oneTimeCodesAreStillDroppedEvenUnderAKnownTitle() {
        for (body in listOf(
            "شراء إنترنت\nرمز لمرة واحدة 731905\nمبلغ: 245.60 ر.س\nلدى: NOVA MART\nفي: 2026-03-05 10:42",
            "Online Purchase\nVerification No. 731905\nAmount: SAR 245.60\nAt: NOVA MART\n2026-03-05 10:42",
            "Online Purchase\nUse 731905 to confirm this payment\nAmount: SAR 245.60\nAt: NOVA MART\n2026-03-05 10:42",
            "حوالة صادرة محلية\nرقمك السري المؤقت 731905\nالمبلغ: 1,320.00 ر.س\nالى: FAHAD SAMPLE\nفي: 2026-03-05 10:42",
            "Online Purchase\n731905 is your code. Do not share it\nAmount: SAR 245.60\nAt: NOVA MART\n2026-03-05 10:42",
            "شراء إنترنت\nلإتمام العملية أدخل الرمز المرسل 731905\nمبلغ: 245.60 ر.س\nلدى: NOVA MART\nفي: 2026-03-05 10:42",
            "Online Purchase\nVerification number 731905\nAmount: SAR 87.40\nAt: TEST BAKERY\n2026-03-05 11:20",
            "شراء انترنت\nOT­P 731905\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\n2026-03-05 11:20",
            "الكود بتاعك 4829 عشان تأكد دفع 300 جنيه لـ TEST SHOP. ماتديهوش لحد",
        )) {
            assertNull(SmsSafety.sanitize(body), body)
        }
    }
}
