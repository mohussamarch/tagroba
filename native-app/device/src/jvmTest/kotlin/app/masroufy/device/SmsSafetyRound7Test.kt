package app.masroufy.device

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.SmsParseResult
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الجولة السابعة — فلتر الجهاز (§72: **ضياع عملية حقيقية مش مقبول**، والانتظار مقبول). رسايل المراجعة العدائية التالتة اللي كانت
 * **بتترمي قبل الحفظ** وهي عملية خلصت: رقم الموافقة اتقري رمز · جملة تحذير من غير رقم · حارس مسك كلمة ليها معنى عادي في جملة الحركة
 * (كشف حساب · تم طلب · تم تفعيل · dispute · due on · statement balance · تفويض · الرصيد في الأول · scheduled) · إلغاء ومعاه فلوس راجعة ·
 * رمز الريال الجديد · شحن رصيد من غير كلمة عملة. وكمان الحجب: المبلغ اللازق والرصيد من غير فواصل والموبايل بفواصل. كل الرسايل مخترعة.
 */
class SmsSafetyRound7Test {
    private val received = "2026-10-08T09:30:00Z"

    /** إلغاء ومعاه فلوس راجعة (الاسترداد §75-6) — كان بيترمي «مرفوضة». */
    private val cancelWithRefund = listOf(
        "تم إلغاء عملية الشراء لدى SAFA OPTICS وإعادة مبلغ 212.00 ر.س إلى بطاقتك *7739 بتاريخ 2026-10-08",
        "تم إلغاء حجز الفندق RIMAL SUITES وإرجاع 450.00 ر.س إلى بطاقتك *7739 بتاريخ 2026-10-08",
    )

    private val mustStore = listOf(
        // ── رقم الموافقة/التفويض مش رمز · «Verified with OTP» من غير رقم ──
        "شراء عبر نقاط البيع\nمبلغ: 212.00 ر.س\nلدى: سفا للبصريات\nرمز الموافقة: 553120\nفي: 2026-10-08 10:12",
        "PoS Purchase\nAmount: SAR 48.50\nAt: BUSTAN CAFE\nAuth. Code: 553120\nOn: 2026-10-08 11:42",
        "تمت عملية شراء بمبلغ 212.00 ر.س لدى SAFA OPTICS ببطاقة مدى *7739 في 2026-10-08 10:12، رمز الموافقة 553120",
        "Purchase SAR 48.50 at BUSTAN CAFE card *7739 2026-10-08 11:42 Appr Code 553120",
        "Purchase\nAmount: SAR 48.50\nAt: BUSTAN CAFE\nOn: 2026-10-08 11:42\nVerified with OTP",
        "PoS Purchase\nAmount: SAR 48.50\nAt: BUSTAN CAFE\nRef. Code: 553120\nOn: 2026-10-08 11:42",
        "PoS Purchase\nAmount: SAR 48.50\nAt: BUSTAN CAFE\nTxn Code: 553120\nOn: 2026-10-08 11:42",
        "شراء عبر نقاط البيع\nمبلغ: 48.50 ر.س\nلدى: بستان كافيه\nكود الموافقة: 553120\nفي: 2026-10-08 11:42",
        // ── جملة حركة خلصت فيها كلمة حارس بمعنى عادي ──
        "تم خصم رسوم إصدار كشف حساب بمبلغ 25.00 ر.س من حسابك **6618 بتاريخ 2026-10-08",
        "تم طلب بطاقة بديلة وخصم رسوم الإصدار 30.00 ر.س من حسابك **6618 بتاريخ 2026-10-08",
        "تم تفعيل بطاقتك الجديدة رقم 4482 وخصم رسوم إصدار 150 جنيه من حسابك يوم 08/10/2026",
        "Your dispute for SAFA OPTICS has been resolved. SAR 212.00 has been credited to your card *7739 on 2026-10-08",
        "Your chargeback claim for CLOUDNEST APPS is approved. SAR 349.00 credited to card *7739 on 2026-10-08",
        "Your refund request has been processed: SAR 89.00 credited to card *7739 on 2026-10-08",
        "Bill payment of SAR 318.40 to SAMPLE POWER CO for the bill due on 2026-10-20 was successful",
        "Payment of SAR 1,500.00 received towards your credit card *4476 statement balance on 2026-10-08. Thank you",
        "Your credit card *4476 payment of SAR 170.64 (minimum payment) has been received on 2026-10-08",
        "تمت تسوية التفويض لدى RIMAL SUITES وخصم مبلغ 1,140.00 ر.س من بطاقتك *7739 بتاريخ 2026-10-08",
        "تمت عملية شراء بمبلغ 48.50 ر.س لدى BUSTAN CAFE بتاريخ 2026-10-08، رقم التفويض 553120",
        "Available balance SAR 3,912.40 after PoS purchase of SAR 48.50 at BUSTAN CAFE on 2026-10-08",
        "رصيدك الحالي 1,240 جنيه. تم استلام 500 جنيه من 01000007306 يوم 08/10/2026",
        "Your scheduled transfer of SAR 1,500.00 to HISHAM SAMPLE was executed on 2026-10-08",
        "Your InstaPay transfer of EGP 1,000.00 to MARIO SAMPLE that failed earlier today has now been completed successfully on 08/10/2026",
        // ── إلغاء ومعاه فلوس راجعة ──
    ) + cancelWithRefund + listOf(
        // ── محافظ مصر: جملة تحذير من غير رمز ──
        "InstaPay: EGP 500.00 sent to ZIAD PROBE on 08/10/2026. Keep your IPN PIN and OTP private.",
        "تم تحويل 350 جنيه لرقم 01000007307 من محفظتك. رصيدك 650 جنيه. متشاركش الرقم السري مع حد",
        "تم استلام 600 جنيه من 01000007302 على محفظتك. محدش من فودافون هيطلب منك الرقم السري",
        "تم تحويل 2,300 جنيه لرقم 01000007308 بنجاح. خلي بالك: الرقم السري بتاعك ماحدش يعرفه",
        "e& money: You received EGP 300.00 from 01000007301. Beware of fraud calls asking for your OTP",
        "e& money: You received EGP 450.00 from 01000007308. Your OTP is never needed to receive money",
        "تم استلام 700 جنيه من رقم 01000007305 على محفظتك. لو حد طلب منك كود التحقق اقفل السكة",
        // ── محافظ مصر: حارس مسك كلمة عادية في جملة الحركة ──
        "تم حجز تذكرة القطار بمبلغ 150 جنيه من محفظتك بنجاح",
        "تم استلام طلبك ودفع 230 جنيه من محفظتك لـ PROBE FOOD",
        "تم رد مبلغ 89 جنيه لمحفظتك من PROBE FOOD بسبب إلغاء الطلب",
        "Order cancelled: EGP 89.00 returned to your Vodafone Cash wallet from PROBE FOOD",
        "You received EGP 300.00 from 01000007301, available EGP 900.00, pending EGP 0.00",
        "رصيد محفظتك 900 جنيه. تم استلام 300 جنيه من 01000007301",
        "Withdrawal request processed: EGP 1,500.00 has been withdrawn from your wallet at PROBE AGENT",
        "Your cash-out request of EGP 2,000.00 at PROBE AGENT was completed successfully",
        "انستاباي: تم تنفيذ التحويل المجدول بمبلغ 1,000 جنيه إلى نهى التجريبية بنجاح",
        "تم خصم 200 جنيه من محفظتك بتفويض منك لـ PROBE GYM اشتراك شهري",
        "تم تحويل 640 جنيه لرقم 01000007305 بنجاح وتعذر إرسال الإيصال على بريدك",
        "اتحولك 300 جنيه من مازن التجريبي، افتح الابلكيشن لعرض الإيصال",
        "اتحولك 200 جنيه من مازن التجريبي على انستاباي واحصل على 50 جنيه هدية لما تدفع فاتورتك",
        // ── عملات وكتابات جديدة ──
        "PoS Purchase\nAmount: ⃁ 48.60\nAt: QUOLL BAKERY\nOn: 2026-10-07 18:22",
        "Purchase of ⃁ 48.60 at QUOLL BAKERY with mada card *3906 on 2026-10-07 18:22",
        "Your mada card *3906 was used for 48.60 ⃁ at QUOLL BAKERY on 2026-10-07 18:22",
        "تمت عملية شراء بمبلغ ﷼ 48.60 لدى مخبز الوعل بتاريخ 2026-10-07",
        "Purchase of S.R 48.60 at QUOLL BAKERY on 2026-10-07 18:22",
        "Purchase 1.250,00 ₺ at SAMPLE ISTANBUL with card *7739 on 2026-10-08",
        // ── شحن رصيد فودافون من غير كلمة عملة ──
        "تم شحن رصيد موبايلك ب 30 بنجاح وخصم 34.20 من محفظتك شاملة الضريبة. رصيد حسابك في فودافون كاش الحالي 465.80",
    )

    private val mustDrop = listOf(
        "Online Purchase\nAmount: SAR 349.00\nAt: SAFA OPTICS\nVerification: 551204\nOn: 2026-10-08 10:12",
        "شراء إنترنت\nمبلغ: 349.00 ر.س\nلدى: SAFA OPTICS\nلإتمام العملية أدخل: 551204",
        "Your mada Pay registration code for card *7739 is 551204. A SAR 1.00 verification charge may apply",
        "Online Purchase\nAmount: SAR 349.00\nAt: SAFA OPTICS\nPassword: 551204\nValid for 3 minutes",
        "Your transaction PIN for InstaPay transfer of EGP 3,000.00 to MARIO SAMPLE is 553320",
        // ولسه: رمز فعلًا جنب كلمة الرمز
        "رمز التحقق 482913 لعملية شراء بمبلغ 230.00 ر.س لدى NAJM BOOKS",
        "Your OTP for card ending 4417 is 482913. Do not share it",
        // والعروض والمرفوض والجاي من غير فعل حركة خلصت لسه بيترموا (الاستثناء مش باب للإعلانات)
        "Cashback of SAR 50.00 will be credited to your card when you spend SAR 500 this month",
        "احصل على 50 ريال كاش باك عند الدفع ببطاقتك مدى",
        "عملية مرفوضة: لم يتم خصم 50.00 ر.س من حسابك",
        "Your purchase of SAR 50.00 at TEST SHOP was declined",
        "تم استلام طلب سحب 300.00 جنيه من محفظتك",
    )

    @Test fun realTransactionsAreStoredNotDropped() {
        val dropped = mustStore.filter { SmsSafety.sanitize(it) == null }
        assertTrue(dropped.isEmpty(), "dropped before storage:\n" + dropped.joinToString("\n"))
    }

    @Test fun realCodesAreStillDropped() {
        val kept = mustDrop.filter { SmsSafety.sanitize(it) != null }
        assertTrue(kept.isEmpty(), "kept for storage:\n" + kept.joinToString("\n"))
    }

    @Test fun storedMessagesAreNeverRecordedAutomaticallyWrongly() {
        // المحفوظ: يا القارئ يقراه، يا يرفضه فيستنى — والإلغاء اللي معاه فلوس راجعة ما بيتسجلش صرف
        for (body in cancelWithRefund) {
            val stored = assertNotNull(SmsSafety.sanitize(body))
            val r = parseBankSms(BankSmsMessage("TESTBANK", received, stored), 1)
            assertTrue(r !is SmsParseResult.Ok || r.row.direction.wire != "out" || !r.row.shape.clear, "refund booked as spending: $body")
        }
        // رمز الريال الجديد و«﷼» و«S.R»: بتتحفظ وبتستنى (القارئ لسه ما بيقراهاش ريال — سؤال للمالك)، عمرها ما بتتسجل لوحدها
        for (body in mustStore.filter { '⃁' in it || '﷼' in it || "S.R" in it }) {
            val stored = assertNotNull(SmsSafety.sanitize(body))
            val r = parseBankSms(BankSmsMessage("TESTBANK", received, stored), 1)
            assertTrue(r !is SmsParseResult.Ok || !r.row.shape.clear, "recorded: $body")
        }
        val recharge = assertNotNull(SmsSafety.sanitize(mustStore.last()))
        val row = assertIs<SmsParseResult.Ok>(parseEgyptBankSms(BankSmsMessage("VF-CASH", received, recharge), 1)).row
        assertEquals(3420L, row.amountMinor)
        assertTrue(row.shape.clear, row.shape.wire)
    }

    @Test fun amountsAndBalancesAreKeptAndPhonesAreHidden() {
        val v09 = assertNotNull(
            SmsSafety.sanitize(
                "2026-10-08 13:15: Received EGP12500 from 01000007309 to Mobile Account Number 01000009900. Ref: 004417095 Available Balance: 13,540.00",
            ),
        )
        assertTrue("EGP12500" in v09, v09)
        val r = assertIs<SmsParseResult.Ok>(parseEgyptBankSms(BankSmsMessage("VF-CASH", received, v09), 1)).row
        assertEquals(1_250_000L, r.amountMinor)
        val v10 = assertNotNull(
            SmsSafety.sanitize(
                "تم تحويل 300 جنيه لرقم 01000007305 مصاريف الخدمة 1 جنيه رصيد حسابك فى فودافون كاش الحالي 15230.50. تاريخ العملية: 2026-10-08 13:15 رقم العملية: 004417097",
            ),
        )
        assertTrue("15230.50" in v10, v10)
        val send = assertIs<SmsParseResult.Ok>(parseEgyptBankSms(BankSmsMessage("VF-CASH", received, v10), 1)).row
        assertEquals(30_000L, send.amountMinor)
        assertTrue(send.shape.clear, send.shape.wire)
        val m07 = assertNotNull(SmsSafety.sanitize("تم استلام 300 جنيه من رقم 010-0000-7310 رصيدك الحالي 800 جنيه"))
        assertFalse("010-0000-7310" in m07, m07)
        assertTrue("••••7310" in m07, m07)
        assertNull(Regex("0\\d{2}[ .-]\\d{3,4}[ .-]\\d{4}").find(app.masroufy.core.redactSms("اتصل 055 123 4567")))
    }
}
