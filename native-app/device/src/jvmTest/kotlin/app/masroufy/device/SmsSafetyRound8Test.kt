package app.masroufy.device

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.SmsParseResult
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * الجولة التامنة — فلتر الجهاز (OVERRIDES §72.5 · §72: **ضياع عملية حقيقية مش مقبول**، والانتظار مقبول). رسايل المراجعة العدائية الرابعة
 * اللي كانت **بتترمي قبل الحفظ** وهي عملية خلصت: رقم تاني غير الرمز اتقري رمز (سنة بعد اسم شهر · رقم الكارت · رقم خدمة العملاء · كود
 * الدفع/المشترك/الطلب/التاجر) · الرصيد في الأول وبعده حركة بفعل ناقص · حارس مسك كلمة عادية («رقم التفويض» · «مجدولة» · «تم طلب» ·
 * «تذكير» · «statement») والفعل الخلصان ناقص · نقاط ولاء · اختصار عملة أجنبية ما نعرفهوش. كل الرسايل مخترعة.
 */
class SmsSafetyRound8Test {
    private val mustStore = listOf(
        // ── رسالة فيها كلمة رمز ورقم تاني مش رمز ──
        "Online Purchase\nAmount: SAR 299.00\nAt: QUBBA STREAMING\nDate: 09 Oct 2026 14:05\nAuthenticated with OTP",
        "تمت عملية شراء عبر الإنترنت بمبلغ 230.00 ر.س لدى LAYAN OPTICS بتاريخ 9 أكتوبر 2026 باستخدام رمز التحقق",
        "PoS Purchase\nAmount: SAR 48.50\nAt: SADEEM PHARMACY\nVia: mada Pay 5093\nOn: 2026-10-09 10:41\nVerified with OTP",
        "Your credit card ending with #3318 was charged for EGP 1,250.00 at ZAHRA MART on 09/10/26 at 14:05. Never share your OTP or card details, call 19990",
        "تم خصم 850 جم من بطاقة الخصم المباشر رقم 3318 عند ZAHRA MART يوم 10-09 الساعة 14:05. لا تشارك الرقم السري أو رمز التحقق مع أي شخص واتصل على 19990",
        "تم دفع 230.00 جنيه لفاتورة SAMPLE NET بنجاح. كود الدفع 7781234. شكرا لاستخدامك فوري",
        "Purchase of EGP 850.00 at ZAHRA MART on 09 Oct 2026 was verified by OTP",
        // ── «كود/code <اسم> <رقم>» = رقم عملية مش رمز (القاعدة اتقلبت) ──
        "تم سداد فاتورة الكهرباء بمبلغ 410 جنيه عن طريق فوري. كود المشترك 44712",
        "Payment of EGP 199.00 to TEST INTERNET via Fawry was successful. Customer code: 553128",
        "تم دفع 230 جنيه من محفظتك لطلبات TEST FOOD. كود الطلب 77120",
        "Fawry: EGP 85.00 paid for TEST WATER. Bill code 4471209",
        "تم حجز تذكرة قطار بمبلغ 145 جنيه ودفعها من محفظة فودافون كاش. كود الحجز 663201",
        "Fawry Pay: 175.00 EGP paid to TEST TELECOM, service code 1234",
        "Vodafone Cash: You paid EGP 95.00 to TEST KIOSK. Order code 55821",
        "انستاباي: تم تحويل 1,100 جنيه لمازن الافتراضي. كود الدفع 5521007",
        "تم إيداع 1,000 جنيه في محفظتك من خلال الوكيل، كود الوكيل 22341",
        "تم دفع 140 جنيه لـ TEST GROCERY بكارت ميزة، كود التاجر 778120",
        // ── جمل تحذير كانت بتتقري رمز (أوعى بالهمزة · متشاركش · احذر · خلي بالك · Beware · Report any OTP request · is never needed) ──
        "e& money: You received EGP 450.00 from 01001112277, receipt 4471230. Beware of fraud calls asking for your OTP",
        "تم استلام 300 جنيه من رقم 01001112288 رصيدك الحالي 800 جنيه. رقم الإيصال 5502231. خلي بالك من اللي بيطلب منك كود التحقق",
        "WE Pay: تم دفع فاتورة التليفون الأرضي 340 جنيه بنجاح لخط 24455667. احذر مشاركة الرقم السري",
        "Orange Cash: Transfer of EGP 250.00 to 01201112200 done at store 33120. Report any OTP request to 7115",
        "InstaPay: Payment of EGP 640.00 to TEST CLINIC completed successfully. Invoice 2026114. Your OTP is never needed for payments",
        "جالك 400 جنيه من 01001112234 على محفظة أورانج كاش - تذكير متشاركش الرقم السري",
        "قبضت 2,500 جنيه من 01001112235 عن طريق انستاباي، أوعى تدي حد الـ OTP",
        "جالك 400 جنيه من 01001112237 على محفظة أورانج كاش - متشاركش الرقم السري مع حد",
        // ── الرصيد في الأول وبعده حركة بمبلغها ──
        "Your account balance is SAR 3,212.00 after a debit of SAR 48.50 at SADEEM PHARMACY on 09/10/2026",
        "Your new balance is SAR 3,163.50 following a purchase of SAR 48.50 at SADEEM PHARMACY on 09/10/2026",
        "رصيدك الحالي 3,163.50 ر.س بعد عملية شراء بمبلغ 48.50 ر.س لدى صيدلية سديم في 2026-10-09",
        "الرصيد المتاح 2,851.10 ر.س بعد سداد فاتورة بمبلغ 312.40 ر.س في 2026-10-09",
        "رصيد محفظتك الحالي 650.00 جنيه بعد خصم 350.00 جنيه قيمة فاتورة SAMPLE NET",
        "رصيدك الحالي 1,240 جنيه بعد دفع 95 جنيه لـ SAMPLE FOOD",
        "رصيدك الحالي 1,240 جنيه بعد شراء بمبلغ 95 جنيه من SAMPLE FOOD",
        "Your Vodafone Cash balance is EGP 1,550.00 after paying EGP 450.00 to SAMPLE FOOD on 09/10/2026",
        "رصيد محفظتك 1,550 جنيه بعد سداد فاتورة الكهرباء بمبلغ 450 جنيه",
        // ── حارس مسك كلمة عادية والفعل الخلصان كان ناقص ──
        "Approved purchase SAR 48.50 at SADEEM PHARMACY, card *5093, 09/10/2026 14:05, authorization code 553120",
        "عملية شراء ناجحة بمبلغ 48.50 ر.س لدى صيدلية سديم، رقم التفويض 553120، 2026-10-09 14:05",
        "عملية شراء ناجحة بمبلغ 850 جنيه لدى ZAHRA MART، رقم التفويض 553120، 09/10/2026 14:05",
        "تنفيذ دفعة مجدولة\nالمبلغ: 312.40 ر.س\nالمفوتر: SAMPLE POWER CO\nفي: 2026-10-09",
        "تم طلب دفتر شيكات وتحصيل رسوم 25.00 ر.س من حسابك **1188 بتاريخ 2026-10-09",
        "تم تفعيل بطاقتك الائتمانية *4476 واستيفاء رسوم الإصدار 150.00 ر.س بتاريخ 2026-10-09",
        "تم تفعيل باقة SAMPLE NET الشهرية وخصم 120 جنيه من رصيد محفظتك. رصيدك الحالي 530 جنيه",
        "مبروك كسبت 50 جنيه كاش باك وتمت إضافتها لمحفظتك. رصيدك الحالي 580 جنيه",
        "Dear customer, a purchase transaction with amount EGP 250.00 has been made on your Meeza card ending 4417 at TEST MART, Authorization code 482211",
        "سحبت 1,000 جنيه من ماكينة TEST ATM بكارت ميزة المنتهي ب 4417 رقم التفويض 552310",
        "حولت 600 جنيه لرنا التجريبية على انستاباي، رقم المرجع 900000571، تذكير: الخدمة متاحة 24 ساعة",
        "Payment successful: EGP 320.00 for TEST GYM membership via valU Pay, statement available in app",
        "You've received EGP 700.00 from 01001112233 - reminder: keep your PIN safe",
        "بعتّ 300 جنيه لرقم 01001112295 على فودافون كاش - تذكير: الرسوم 1 جنيه",
        "Transfer done: EGP 600.00 to HODA MODEL via InstaPay, reminder to rate your experience",
        // ── عرض/نقاط/مجدولة في نفس الجملة ──
        "Your mada card **5093 was used for SAR 48.50 at SADEEM PHARMACY on 09/10/2026, you will earn 48 points.",
        "Dear customer, your card 3318 was used for EGP 850.00 at ZAHRA MART on 09/10/2026 and you will earn 85 points",
        "شراء بمبلغ 48.50 ر.س لدى صيدلية سديم ببطاقة مدى *5093 في 2026-10-09، سيتم إضافة 48 نقطة إلى رصيد نقاطك",
        "Scheduled Transfer Executed\nAmount: SAR 1,500.00\nTo: MAJED SAMPLE\nOn: 2026-10-09 06:00",
        // ── اختصارات عملة أجنبية (§75-12: تستنى ومعاها المبلغ الأجنبي) ──
        "Purchase\nAmount: DHS 120.00\nAt: SAMPLE DUBAI MALL\nOn: 2026-10-09 19:20",
        "Online Purchase\nAmount: RS 2,500.00\nAt: SAMPLE KARACHI STORE",
        "PoS International Purchase\nAmount: SFr 45.00\nAt: SAMPLE ZURICH",
        "PoS Purchase\nAmount: JD 25.000\nAt: SAMPLE AMMAN CAFE\nOn: 2026-10-09",
    )

    private val mustDrop = listOf(
        // رقم موافقة من غير كلمة «رمز» (S02 · S03 — كانوا بيتحفظوا ويتقروا شراء «جاهز»)
        "أدخل الرقم 604218 لتأكيد عملية الشراء بمبلغ 85.50 ر.س من NOOR BOOKS بتاريخ 2026-10-09",
        "To approve your purchase of SAR 85.50 at NOOR BOOKS on 2026-10-09, reply with 604218 within 5 minutes.",
        // الرمز جنب كلمته — حتى تحت عنوان معروف أو في رسالة فيها حركة خلصت
        "Online Purchase\nAmount: SAR 349.00\nAt: SAFA OPTICS\nVerification: 551204\nOn: 2026-10-08 10:12",
        "رمز التحقق 482913 لعملية شراء بمبلغ 230.00 ر.س لدى NAJM BOOKS",
        "Use code 482913 to confirm your purchase of SAR 64.25 at TEST GROCER on 2026-03-05. Do not share it.",
        "تم خصم 850 جم من بطاقة الخصم المباشر رقم 3318 عند ZAHRA MART. لا تشارك الرمز 482913 مع أي شخص",
        "الكود بتاعك 4829 لتحويل 300 جنيه لرقم 01001112233",
        // فشل
        "e& money: Transfer of EGP 600.00 to 01001112252 on 08/10/2026 couldn't be completed. Please retry",
        "WE Pay: Payment of EGP 340.00 for TEST LANDLINE on 08/10/2026 didn't go through",
    )

    @Test fun realTransactionsAreStoredNotDropped() {
        val dropped = mustStore.filter { SmsSafety.sanitize(it) == null }
        assertTrue(dropped.isEmpty(), "dropped before storage:\n" + dropped.joinToString("\n"))
    }

    @Test fun codesApprovalNumbersAndFailuresAreStillDropped() {
        val kept = mustDrop.filter { SmsSafety.sanitize(it) != null }
        assertTrue(kept.isEmpty(), "kept for storage:\n" + kept.joinToString("\n"))
    }

    /** اللي اتحفظ عمره ما بيتسجل لوحده هنا (كلها مش على شكل معروف كامل أو مرفوضة) — بتستنى المالك. */
    @Test fun storedMessagesWaitInsteadOfRecordingThemselves() {
        val auto = mutableListOf<String>()
        for (body in mustStore) {
            val stored = SmsSafety.sanitize(body) ?: continue
            for (parse in listOf(::parseBankSms, ::parseEgyptBankSms)) {
                val r = parse(BankSmsMessage("TESTBANK", "2026-10-09T09:00:00Z", stored), 1)
                if (r is SmsParseResult.Ok && r.row.shape.clear) auto += "${r.row.shape.wire}: $body"
            }
        }
        assertTrue(auto.isEmpty(), auto.joinToString("\n"))
    }
}
