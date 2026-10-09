package app.masroufy.core

/**
 * الجولة التامنة — رسايل المراجعة العدائية الرابعة لفرع الجولة السابعة (`sms-formats-r4-f1-f2-f3` @ 215b182). كلها **مخترعة** (ZAHRA MART ·
 * SADEEM PHARMACY · TAREQ PROBE · الكارت 4476/3318/4417 …). كل مجموعة = ملاحظة؛ الأسماء نفس أسماء المراجع (S·E·M·A·B·C·D·N·P·G·Q·R·U·Z).
 */
internal object SmsRound8Cases {
    /** وصول الرسايل: 2026-10-09 الساعة 12:00 بتوقيت الرياض/القاهرة (الصيفي). */
    const val OCT9 = "2026-10-09T09:00:00Z"
    const val OCT8 = "2026-10-08T09:00:00Z"

    /** سداد الكارت ونفس المبلغ داخل على الكارت (M01/M02) — والكارت اتقيد له من محل (S97 = استرداد). */
    val cardCredits = listOf(
        "Credit Card Credited\nAmount: SAR 1,500.00\nCard: *4476\nOn: 2026-10-09 13:05",
        "بطاقة ائتمانية تأكيد سداد\nالمبلغ: 2,200.00 ر.س\nالبطاقة: *4476\nفي: 2026-10-09 13:05",
        "بطاقة ائتمانية تاكيد سداد\nالمبلغ: 1,500.00 ر.س\nالبطاقة: *4476\nفي: 2026-10-09 10:00", // R01 من غير همزة
    )
    const val CARD_PAYMENT_DEBIT = "Credit Card Payment\nAmount: SAR 1,500.00\nCard: *4476\nFrom account: **1188\nOn: 2026-10-09 13:04"
    const val CARD_REFUND = "Credit Card Credited\nAmount: SAR 89.00\nCard: *4476\nAt: MARJAN TOYS\nOn: 2026-10-09 16:30"
    val egyptCardCredits = listOf("تم قيد مبلغ 2,500.00 جم لبطاقتك الائتمانية رقم 3318", "تم قيد مبلغ 450.00 جم لبطاقتك الائتمانية رقم 3318")

    /** تاريخ الأهلي المصري بأي فاصل = شهر-يوم (E10 · E58 · A04 · A18) — الرسالة والوصول والتاريخ الصح. */
    val nbeDates = listOf(
        Triple("تم خصم 850 جم من بطاقة الخصم المباشر رقم 3318 عند ZAHRA MART يوم 10/09 الساعة 14:05", OCT9, "2026-10-09"),
        Triple("تم خصم 850 جم من بطاقة الخصم المباشر رقم 3318 عند ZAHRA MART يوم 10-09 الساعة 14:05", OCT9, "2026-10-09"),
        Triple("تم خصم 850 جم من بطاقة الائتمان رقم 3318 عند ZAHRA MART يوم 10/09", OCT9, "2026-10-09"),
        Triple("تم خصم 185.50 جم من بطاقة الخصم المباشر رقم 4417 عند TEST BAKERY يوم 10.08 الساعة 21:40", OCT9, "2026-10-08"),
        Triple("تم تنفيذ تحويل لحظي من حسابكم رقم 4417 بمبلغ 500 جم الي هشام النموذجي رقم مرجعي 900000616 يوم 10.07", OCT8, "2026-10-07"),
    )

    /** «يوم/شهر» من غير سنة بالنقطة أو لوحده أو بفاصلة قبل الساعة (A-group) — كانت بتاخد يوم الوصول وتتسجل لوحدها. */
    val egyptPartialDates = listOf(
        Triple("08.10 23:58: Received EGP750.00 from 00201001112233 to Mobile Account Number 01009998877. Ref: 900000441122 Available Balance: 1,250.00", "2026-10-09T06:00:00Z", "2026-10-08"),
        Triple("تم خصم مبلغ EGP 320.00 من بطاقة الخصم المباشر المنتهية ب 4417 عند TEST PHARMACY في 08.10 21:15", "2026-10-10T09:00:00Z", "2026-10-08"),
        Triple("Your credit card ending with #4417 was charged for EGP 1,140.00 at TEST FURNITURE on 30.09 at 21:00.", "2026-10-02T09:00:00Z", "2026-09-30"),
        Triple("IPN transfer sent with amount of EGP 400.00 from 4417 on 05.10 at 13:00. Ref# 900000613", OCT8, "2026-10-05"),
        Triple("IPN transfer received with amount of EGP 400.00 from 4417 on 07.10 at 13:00. Ref# 900000614", OCT8, "2026-10-07"),
        Triple("Your credit card ending with #4417 was charged for EGP 640.00 at TEST MART on 07.10 at 20:00.", OCT8, "2026-10-07"),
        Triple("IPN transfer with EGP 300.00 deducted on 07.10 from your AC ending with 4417 with Ref# 900000615.", OCT8, "2026-10-07"),
        Triple("Your account ending in 4417 has been credited with EGP 3,200.00 on 07.10 from NOHA PROBE.", OCT8, "2026-10-07"),
        Triple("يرجي العلم انه تم تنفيذ تحويل لحظي بمبلغ 900 جم الي حسابك المنتهي ب 4417 برقم مرجعي 900000617 بتاريخ 07.10 23:10", OCT8, "2026-10-07"),
        Triple("You received EGP 200.00 on 07.10 at 22:00 to your card ending in 4417.", OCT8, "2026-10-07"),
        Triple("8/10, 23:58: Received EGP410.00 from 00201001112244 to Mobile Account Number 01009998877. Ref: 900000441124 Available Balance: 900.00", "2026-10-10T09:00:00Z", "2026-10-08"),
        Triple("You have received 450 EGP from 01001112266. Transaction ID: 900000553 06/10 New balance: 1,450 EGP.", OCT8, "2026-10-06"),
        Triple("You have successfully recharged 50.00 EGP to the balance of 01009998877; Your current Vodafone Cash balance is 450.00 EGP;Trx date: 07/10 Trx ID 900000556", OCT8, "2026-10-07"),
        Triple("You have successfully recharged 50.00 EGP to the balance of 01009998877; Your current Vodafone Cash balance is 450.00 EGP; Trx date: 07.10 22:10 Trx ID 900000555", OCT8, "2026-10-07"),
        // ضوابط كانت بتتقري صح
        Triple("08/10 23:58: Received EGP750.00 from 00201001112233 to Mobile Account Number 01009998877. Ref: 900000441125 Available Balance: 1,250.00", "2026-10-09T06:00:00Z", "2026-10-08"),
        Triple("08.10.26 23:58: Received EGP750.00 from 00201001112233 to Mobile Account Number 01009998877. Ref: 900000441126 Available Balance: 1,250.00", "2026-10-09T06:00:00Z", "2026-10-08"),
    )

    /** فودافون كاش بالساعة من غير تاريخ (A02 · A22 بعد نص الليل بتوقيت القاهرة · A16 بالنهار) ⇒ بتستنى. */
    val timeOnly = listOf(
        "23:58: Received EGP320.00 from 00201001112244 to Mobile Account Number 01009998877. Ref: 900000441123 Available Balance: 820.00" to "2026-10-08T21:03:00Z",
        "11:58 PM: Received EGP260.00 from 00201001112296 to Mobile Account Number 01009998877. Ref: 900000441127 Available Balance: 560.00" to "2026-10-08T21:04:00Z",
        "14:22: Received EGP150.00 from 00201001112297 to Mobile Account Number 01009998877. Ref: 900000441128 Available Balance: 650.00" to "2026-10-08T11:23:00Z",
    )

    /** حساب المالك التاني (4417) بكل كتابات سطر الحساب (P-group) — كان آخر 4 مش بيتقري فالرسالة بتتسجل في المحفظة المربوطة. */
    val ownAccount4417 = listOf(
        "Debit Transfer Local\nAmount: SAR 900.00\nAccount: **4417\nTo: TAREQ PROBE\nOn: 2026-10-08 11:40",
        "حوالة صادرة محلية\nالمبلغ: 900.00 ر.س\nالحساب: **4417\nالى: طارق الاختباري\nفي: 2026-10-08 11:40",
        "حوالة صادرة محلية\nالمبلغ: 900.00 ر.س\nمن حساب: 4417*\nالى: طارق الاختباري\nفي: 2026-10-08 11:40",
        "Credit transfer Local\nAmount: SAR 900.00\nIBAN: SA** **** 4417\nFrom: TAREQ PROBE\nOn: 2026-10-08",
        "Credit transfer Local\nAmount: SAR 900.00\nIBAN: SA****4417\nFrom: TAREQ PROBE\nOn: 2026-10-08",
        "Debit Transfer Local\nAmount: SAR 900.00\nFrom: SA****4417\nTo: TAREQ PROBE\nOn: 2026-10-08 11:40",
        "PoS Purchase\nAmount: SAR 64.25\nAccount: **4417\nAt: WOMBAT PANTRY\nOn: 2026-10-08 18:22",
        "PoS Purchase\nAmount: SAR 64.25\nCard: *9001\nAccount: **4417\nAt: WOMBAT PANTRY\nOn: 2026-10-08 18:22",
        "يرجي العلم انه تم تنفيذ تحويل لحظي بمبلغ 500 جم من حسابك المنتهي ب 4417 برقم مرجعي 7712 بتاريخ 08/10/2026",
        "تم تحويل مبلغ 500 جم لحسابكم المنتهي ب 4417 من نادر الاختباري عبر شبكة المدفوعات اللحظية مرجع رقم 7712",
        "Debit Transfer Local\nAmount: SAR 900.00\nFrom: **4417\nTo: TAREQ PROBE\nOn: 2026-10-08 11:40", // ضابط
    )

    /** محل برّه البلد (كود أو اسم بلد أو مدينة في آخره) والمبلغ بالعملة المحلية (G · D · S50) — السعودية ثم مصر. */
    val saudiAbroad = listOf(
        "PoS Purchase\nAmount: SAR 412.00\nAt: SAMPLE RESORT BAKU AZERBAIJAN\nOn: 2026-10-08 10:00",
        "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE CAFE KUALA LUMPUR MALAYSIA\nOn: 2026-10-08 10:00",
        "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE HOTEL PHUKET THAILAND\nOn: 2026-10-08 10:00",
        "شراء عبر نقاط البيع\nمبلغ: 180.00 ر.س\nلدى: فندق العينة تبليسي جورجيا\nفي: 2026-10-08 10:00",
        "شراء عبر نقاط البيع\nمبلغ: 180.00 ر.س\nلدى: متجر العينة لندن بريطانيا\nفي: 2026-10-08 10:00",
        "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE STORE ZANZIBAR TZ\nOn: 2026-10-08 10:00",
        "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE CAFE SARAJEVO BA\nOn: 2026-10-08 10:00",
        "Online Purchase\nAmount: SAR 93.75\nAt: SAMPLE BOOKS LONDON-GB\nOn: 2026-10-08",
        "شراء عبر نقاط البيع\nمبلغ: 180.00 ر.س\nلدى: SAMPLE MALL DUBAI U.A.E\nفي: 2026-10-08 10:00",
        "PoS Purchase\nAmount: SAR 75.00\nAt: Sample Store Dubai Ae\nOn: 2026-10-08 10:00",
        "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE.CO.UK\nOn: 2026-10-08 10:00",
        "PoS Purchase\nAmount: SAR 412.75\nAt: SAMPLE GALLERIA ISTANBUL\nOn: 2026-10-08 10:15",
        "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE CAFE CAIRO EG\nOn: 2026-10-08 10:00",
        "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE CAFE AMMAN JO.\nOn: 2026-10-08 10:00",
        "شراء عبر نقاط البيع\nمبلغ: 180.00 ر.س\nلدى: متجر العينة اسطنبول تركيا\nفي: 2026-10-08 10:00",
        "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE HOTEL TBILISI GEO\nOn: 2026-10-08 10:00",
    )
    val egyptAbroad = listOf(
        "تم خصم 850.00 جم من بطاقة الخصم المباشر رقم 4821 عند SAMPLE HOTEL TBILISI GEORGIA يوم 10-08 الساعة 21:10",
        "Your credit card ending with #4821 was charged for EGP 1,250.00 at SAMPLE SHOP ISTANBUL-TR on 08/10/2026 at 14:22.",
        "Your credit card ending with #4417 was charged for EGP 1,800.00 at TEST STORE DUBAI on 08/10/2026 at 13:05.",
        "تم خصم 1,250.00 جم من بطاقة الخصم المباشر رقم 4417 عند TEST OUTLET ISTANBUL يوم 10-08",
        "Your Debit Card **4417 had a Successful transaction of EGP 3,400.00 @TEST HOTEL MAKKAH,your available bal.EGP 9,000.00",
    )

    /** المبلغ من خانة مش مبلغ (Q-group) ⇒ «المبلغ مش واضح» — والضوابط بخانة مبلغ حقيقية بتتقري بيها (X05 · X06). */
    val amountFromFreeSlot = listOf(
        "PoS Purchase\nAt: محلات 5 ريال\nOn: 2026-10-08 18:22",
        "Bill Payment\nBiller: SAMPLE WATER CO\nالفاتورة: SR4471\nOn: 2026-10-08 09:00",
        "Transfer via WU\nMTCN: SR4471\nTo: TAREQ PROBE\nOn: 2026-10-08 11:40",
        "Debit Transfer Local\nTo: SR 4417\nOn: 2026-10-08 11:40",
    )

    /** علامة اتجاه **جوه** الرقم (Z — الملاحظة الأخيرة: المعروض في برنامج الرسايل غير المقروء). */
    val bidiInsideNumber = listOf(
        "PoS Purchase\nAmount: SAR 4⁧8.60⁩\nAt: QUOLL BAKERY\nOn: 2026-10-07 18:22",
        "PoS Purchase\nAmount: SAR 48.‏60\nAt: QUOLL BAKERY\nOn: 2026-10-07 18:22",
        "PoS Purchase\nAmount: SAR 1⁧,234.5⁩0\nAt: QUOLL BAKERY\nOn: 2026-10-07 18:22",
        "تم خصم 18‏5.50 جم من بطاقة الخصم المباشر رقم 4417 عند TEST BAKERY يوم 10-07 الساعة 21:40",
    )

    /** استرداد أو كاش باك في اسم المرسل (B) · كلمة حالة جوه خانة الاسم (N) — كانوا بيتسجلوا تحويل داخل لوحدهم. */
    val refundInSender = listOf(
        "Instant transfer of 120.00 EGP received from TEST STORE REFUND. Reference: 900000561",
        "Dear customer, IPN transfer of 35 EGP credited to account 4417 from TEST BANK CASHBACK. Reference: 900000562",
        "تم اضافة تحويل لحظي لحسابكم رقم 4417 بمبلغ 60 جم من كاش باك فوري رقم مرجعي 900000563 يوم 10-08",
        "You have received an IPN transfer of EGP 75.00 to account ending 4417 from TEST AIRLINE REFUNDS. Ref No: 900000564",
        "تم استلام تحويل لحظي بمبلغ 140 جم من TEST MARKET CASHBACK الي حسابكم المنتهي ب 4417",
    )
    val statusInNameSlot = listOf(
        "تم استلام مبلغ 500 جنيه من رقم 01001112255 المسجل باسم رنا التجريبية متجمد",
        "تم استلام مبلغ 500 جنيه من رقم 01001112255 المسجل باسم رنا التجريبية متعطل",
        "تم اضافة تحويل لحظي لحسابكم رقم 4417 بمبلغ 900 جم من هشام النموذجي عالق رقم مرجعي 900000615 يوم 10-08",
        "Instant transfer of 300.00 EGP received from SAMI MODEL ABORTED",
        "Instant transfer of 300.00 EGP received from SAMI MODEL REVOKED",
        "Dear customer, IPN transfer of 450 EGP credited to account 4417 from HANY MODEL UNDELIVERED. Reference: 900000614",
        "Instant transfer of 300.00 EGP received from SAMI MODEL ONHOLD",
        "Instant transfer of 300.00 EGP received from SAMI MODEL PENDINGAPPROVAL",
    )

    /** صيغ فشل (E08 · E09 · E28 في مجموعة المحافظ) ⇒ مرفوضة، مش «جاهزة». */
    val failures = listOf(
        "e& money: Transfer of EGP 600.00 to 01001112252 on 08/10/2026 couldn't be completed. Please retry",
        "WE Pay: Payment of EGP 340.00 for TEST LANDLINE on 08/10/2026 didn't go through",
        "تم تحويل 500 جنيه لرقم 01001112259 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 499. التحويل اتأخر وهيتراجع من الفرع",
    )
}
