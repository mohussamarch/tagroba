package app.masroufy.core

import app.masroufy.core.SmsRound5Cases.MARCH
import app.masroufy.core.SmsRound5Cases.OCTOBER

/**
 * الجولة السادسة — تكملة [SmsRound6Cases]: **مصر** (قوالب البنوك والمحافظ). كلها مخترعة (SAMPLE GROCER · ماجد الوهمي · 0100000xxxx …).
 * الخانة الحرة (الاسم · المحل · ذيل فودافون كاش) كانت بتقبل أي جملة حالة، فاتسجلت عمليات ما خلصتش.
 */
internal object SmsRound6Cases2 {
    private const val VF_TO = "على رقم محفظتك 01000009900."

    val mustNotAutoRecord: List<Triple<String, String, String>> = listOf(
        // ── 1. كلمة حالة بصيغة تانية (معكوسة · مستردة · تأمين · موقوف) ──
        Triple("EG13", MARCH, "تم خصم 450.00 جم من بطاقة الخصم المباشر رقم 7392 عند SAMPLE GROCER - عملية معكوسة يوم 03-05 الساعة 10:15"),
        Triple("EG21", MARCH, "تم خصم 450.00 جم من بطاقة الخصم المباشر رقم 7392 عند SAMPLE GROCER (مستردة) يوم 03-05 الساعة 10:15"),
        Triple("EG14", MARCH, "تم خصم 2,500.00 جم من بطاقة الائتمان رقم 7392 عند SAMPLE RESORT تأمين مسترد يوم 03-05 الساعة 10:15"),
        Triple(
            "EG20", MARCH,
            "تم استلام مبلغ 320.00 جنيه من رقم 01012340000 المسجل باسم هاني عينة $VF_TO رصيدك الحالي: 500.00 جنيه تاريخ العملية: 26-03-05 11:00 " +
                "رقم العملية: 912340001 المبلغ موقوف لحين تأكيد بيانات محفظتك",
        ),
        // ── 2. ذيل فودافون كاش الحر (كان يقبل 120 حرف أي كلام) ──
        Triple("v02", OCTOBER, "تم استلام مبلغ 400 جنيه من رقم 01000007431 المسجل باسم ماجد الوهمي $VF_TO التحويل مستني موافقتك، افتح تطبيق أنا فودافون عشان تقبله"),
        Triple("v03", OCTOBER, "تم استلام مبلغ 1,200 جنيه من رقم 01000007432 المسجل باسم ماجد الوهمي $VF_TO المبلغ هيتضاف لرصيدك بعد مراجعة البنك"),
        Triple("v04", OCTOBER, "تم تحويل 300 جنيه لرقم 01000007433 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 699. العملية متمتش وهيرجعلك المبلغ"),
        Triple("v05", OCTOBER, "تم تحويل 650 جنيه لرقم 01000007434 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 349. للأسف التحويل اترفض من البنك"),
        Triple("v06", OCTOBER, "تم تحويل 400 جنيه لرقم 01000007435 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 599. التحويل هيتنفذ بكره الصبح"),
        Triple("v21", OCTOBER, "تم تحويل 120 جنيه لرقم 01000007436 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 879. الرقم مش مشترك في فودافون كاش والفلوس رجعتلك"),
        Triple("v22", OCTOBER, "تم سحب 1,000 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 400 جنيه. الماكينة مطلعتش الفلوس وهيترجع المبلغ لمحفظتك"),
        Triple("b19", OCTOBER, "تم سحب 800 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 1,200 جنيه. العملية اتعكست والفلوس رجعت لرصيدك"),
        Triple("b20", OCTOBER, "تم تحويل 500 جنيه لرقم 01000007437 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 499. التحويل اتوقف لحد ما المستلم يأكد هويته"),
        Triple("b25", OCTOBER, "تم استلام مبلغ 600 جنيه من رقم 01000007438 المسجل باسم ماجد الوهمي $VF_TO التحويل ده اتعمل بالغلط وهيتسحب من رصيدك تاني"),
        Triple("E01", OCTOBER, "تم استلام مبلغ 500.00 جنيه من رقم 01000007439 المسجل باسم سامر التجريبي $VF_TO رصيدك الحالي: 900.00 جنيه وتم ارجاعه للمرسل"),
        Triple("E02", OCTOBER, "تم استلام مبلغ 500.00 جنيه من رقم 01000007439 المسجل باسم سامر التجريبي $VF_TO رصيدك الحالي: 900.00 جنيه وتم إلغاؤها"),
        Triple("E15", OCTOBER, "تم تحويل 500 جنيه لرقم 01000007440 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 400. والمبلغ رجعلك تاني"),
        Triple("E26", OCTOBER, "تم تحويل 500 جنيه لرقم 01000007441 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 400. التحويل متعلق لحد ما المستلم يأكد"),
        Triple(
            "E23", OCTOBER,
            "تم سحب 500 جنيه بنجاح. رصيد حسابك في فودافون كاش الحالي 1,400.00 جنيه. تاريخ العملية 09:15 26-10-08 رقم العملية 900000778 الماكينة ما طلعتش الفلوس",
        ),
        Triple("v18", OCTOBER, "تم سحب 500 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 300 جنيه. العملية دي اتعملت امبارح بالليل من ماكينة فوري"),
        // ── 3. خانة الاسم أو المحل أو «.+» في قوالب البنوك وانستاباي ──
        Triple("i03", OCTOBER, "تم اضافة تحويل لحظي لحسابكم بمبلغ 1,500 جم من كريم التجريبي والتحويل مستني تأكيد البنك رقم مرجعي 900000371"),
        Triple("b17", OCTOBER, "تم اضافة تحويل لحظي لبطاقتكم مسبقة الدفع بمبلغ 700 جم من مها التجريبية في الطريق لحسابكم رقم مرجعي 900000617"),
        Triple("b06", OCTOBER, "تم تحويل مبلغ 3,500 جم لحسابكم المنتهي ب 3344 من حازم التجريبي والمبلغ متعلق لحد مراجعة الهوية"),
        Triple("b22", OCTOBER, "يرجى العلم انه تم تنفيذ تحويل لحظي بمبلغ 2,000 جم إلى حسابك المنتهي ب 4410 من سلمى التجريبية مستنية مراجعة البنك برقم مرجعي 900000622"),
        Triple("b23", OCTOBER, "إيداع تحويل لحظي IPN بمبلغ 850 جم بحسابك رقم ...5521 من سيف التجريبي المبلغ متعلق مرجع 900000623"),
        Triple("i08", OCTOBER, "You have received an IPN transfer of EGP 1,250.00 to account ending 4410 from HANY SAMPLE, not yet credited"),
        Triple("b02", OCTOBER, "Instant transfer of 640.00 EGP received from RANA SAMPLE could not be credited to your account"),
        Triple("b03", OCTOBER, "Instant transfer of 980.00 EGP received from OMAR SAMPLE, subject to bank verification. Reference: 900000603"),
        Triple("b04", OCTOBER, "Instant transfer of 980.00 EGP received from OMAR SAMPLE - on its way to your account"),
        Triple("b05", OCTOBER, "Instant transfer of 1,100.00 EGP received from SAMIR SAMPLE was not deposited due to a name mismatch"),
        Triple("b16", OCTOBER, "Your account ending in 2211 has been credited with EGP 3,000.00 on 08/10/2026 from LAILA DEMO, credit to be confirmed by the sender bank"),
        Triple("c04", OCTOBER, "Your credit card ending with #5512 was charged for EGP 760.00 at TEST HOTEL (pre-approval only, final amount not yet charged) on 08/10/26"),
        Triple("b10", OCTOBER, "Your credit card ending with #5512 was charged for EGP 1.00 at TEST APP STORE (temporary charge for card verification) on 08/10/26"),
        Triple("b07", OCTOBER, "تم خصم 1.00 جم من بطاقة الائتمان رقم 5512 عند TEST APP خصم مؤقت للتحقق من البطاقة يوم 10-08 الساعة 09:00"),
        Triple("b08", OCTOBER, "تم خصم 2,000 جم من بطاقة الائتمان رقم 5512 عند TEST HOTEL (تأمين هيتفك عند الخروج) يوم 10-08 الساعة 14:00"),
        Triple("b09", OCTOBER, "A Trx using Card XXXX9087 from TEST STORE (to be confirmed) for EGP 300.00 on 08/10/2026 at 10:00 GMT+2."),
        Triple("c10", OCTOBER, "You received EGP 300.00 on 08/10/2026 as a provisional credit to your card ending in 4410"),
        Triple("E03", OCTOBER, "تم خصم 412.60 جم من بطاقة الخصم المباشر رقم 4417 عند كشك زفير (تم الغاءها) يوم 10-08 الساعة 09:15"),
        Triple("E04", OCTOBER, "Your credit card ending with#4417 was charged for EGP 412.60 at ZEPHYR KIOSK (reverted) on 08/10/26 at 09:15"),
        Triple("E05", OCTOBER, "Your credit card ending with#4417 was charged for EGP 412.60 at ZEPHYR KIOSK - timed out on 08/10/26 at 09:15"),
        Triple("E13", OCTOBER, "Your Debit Card **4417 had a Successful transaction of EGP 412.60 @ZEPHYR KIOSK (rolled back),your available bal.EGP 2,000.00"),
        Triple("E31", OCTOBER, "Your credit card ending with#4417 was charged for EGP 412.60 at ZEPHYR KIOSK, txn not posted on 08/10/26 at 09:15"),
        Triple("E32", OCTOBER, "تم اضافة تحويل لحظي لحسابكم بمبلغ 1,875.00 جم من سامر التجريبي المرتد رقم مرجعي 900000781"),
        // ── 4. تاريخ في خانة «تاريخ العملية» من غير سنة (القارئ بيقراه دلوقتي — والشكل بيستنى) ──
        Triple(
            "v17", OCTOBER,
            "تم استلام مبلغ 500 جنيه من رقم 01000007441 المسجل باسم عادل التجريبي $VF_TO رصيدك الحالي: 900 جنيه تاريخ العملية: 23:50 7/10 رقم العملية: 900000317",
        ),
        Triple("b11", OCTOBER, "تم سحب 300 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 700 جنيه. تاريخ العملية: 21:05 07.10 رقم العملية: 900000611"),
        Triple("b12", OCTOBER, "تم تحويل 260 جنيه لرقم 01000007612 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 239. تاريخ العملية: 6/10"),
    )

    /** القارئ لازم يرفضها (مش تستنى «جاهزة» تسجلها ضغطة «سجّل الكل»): عملية ما وصلتش أو ما اتحسبتش. */
    val mustReject: List<Triple<String, String, String>> = listOf(
        Triple("i05", OCTOBER, "Instant transfer of EGP 640.00 received from RANA SAMPLE could not be credited to your account"),
        Triple("i06", OCTOBER, "Instant transfer of EGP 980.00 received from OMAR SAMPLE, subject to bank verification. Reference: 900000373"),
        Triple("i07", OCTOBER, "Instant transfer of EGP 980.00 received from OMAR SAMPLE - on its way to your account"),
        Triple("i20", OCTOBER, "Instant transfer of EGP 1,100.00 received from SAMIR SAMPLE was not deposited due to a name mismatch"),
    )

    /** تاريخ «تاريخ العملية» من غير سنة: القراية الصح (اليوم/الشهر — اللي في نافذة الـ60 يوم). */
    val partialDates: Map<String, String> = mapOf("v17" to "2026-10-07", "b11" to "2026-10-07", "b12" to "2026-10-06")
}
