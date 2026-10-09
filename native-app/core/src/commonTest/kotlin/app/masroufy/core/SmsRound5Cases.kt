package app.masroufy.core

/**
 * الجولة الخامسة — رسايل المراجعة العدائية للجولة الرابعة (كلها **مخترعة**: NOVA MART · TEST BAKERY · KARIM DEMO · الكارت 5208 …).
 * كل واحدة منهم كانت **بتتسجل لوحدها** (شكل معروف) وهي مش عملية خلصت، أو اتجاهها/مبلغها/تاريخها غلط. المطلوب: **ما تتسجلش لوحدها**
 * في أي بلد — ترفض أو تستنى تأكيد المالك (`SmsShape.clear` = false).
 */
internal object SmsRound5Cases {
    /** وصول رسايل مارس (السعودية 12:00 · القاهرة 11:00). */
    const val MARCH = "2026-03-05T09:00:00Z"

    /** وصول رسايل أكتوبر (المحافظ المصرية). */
    const val OCTOBER = "2026-10-08T12:00:00Z"

    /** (اسم المراجع · وقت الوصول · الرسالة). */
    val mustNotAutoRecord: List<Triple<String, String, String>> = listOf(
        // ── 1. سطر حالة بعد العنوان (القايمة البيضا) ──
        Triple("S17", MARCH, "Bill Payment\nAmount: SAR 230.40\nBiller: QAMAR TELECOM\nStatus: Processing\n2026-03-05 10:42"),
        Triple("S18", MARCH, "سداد فاتورة\nالمبلغ: 230.40 ر.س\nالمفوتر: QAMAR TELECOM\nالحالة: قيد المعالجة\n2026-03-05 10:42"),
        Triple("S19", MARCH, "حوالة صادرة دولية\nالمبلغ: 3,000.00 ر.س\nالى: KARIM DEMO\nالحالة: تحت الإجراء\n2026-03-05 10:42"),
        Triple("S20", MARCH, "Bill Payment\nAmount: SAR 230.40\nBiller: QAMAR TELECOM\nStatus: Submitted\n2026-03-05 10:42"),
        Triple("X02", MARCH, "Bill Payment\nAmount: SAR 230.40\nBiller: QAMAR TELECOM\nStatus: In Process\n2026-03-05 10:42"),
        Triple("X03", MARCH, "Bill Payment\nAmount: SAR 230.40\nBiller: QAMAR TELECOM\nStatus: Awaiting approval\n2026-03-05 10:42"),
        Triple("X06", MARCH, "Bill Payment\nAmount: SAR 230.40\nBiller: QAMAR TELECOM\nStatus: Initiated\n2026-03-05 10:42"),
        Triple("X01", MARCH, "حوالة صادرة دولية\nالمبلغ: 3,000.00 ر.س\nالى: KARIM DEMO\nالحالة: تحت المراجعة\n2026-03-05 10:42"),
        Triple("X04", MARCH, "حوالة صادرة دولية\nالمبلغ: 3,000.00 ر.س\nالى: KARIM DEMO\nالحالة: بانتظار الموافقة\n2026-03-05 10:42"),
        Triple("E01", MARCH, "تم خصم 1,250.00 جم من بطاقة الخصم المباشر رقم 5208 عند NOVA MART يوم 03-05 الساعة 09:40 - العملية قيد المعالجة"),
        Triple("E02", MARCH, "تم خصم 1,250.00 جم من بطاقة الخصم المباشر رقم 5208 عند NOVA MART يوم 03-05 الساعة 09:40 (قيد التسوية)"),
        Triple("E07", MARCH, "تم تنفيذ تحويل لحظي من حسابكم رقم 3344 بمبلغ 750.00 جم إلى KARIM DEMO رقم مرجعي 900000131 يوم 03-05 الساعة 09:40 وجاري التأكيد من البنك المستفيد"),
        // ── 2. مرفوضة بصيغة تانية ──
        Triple("S07", MARCH, "شراء عبر نقاط البيع\nالعملية لم تُقبل\nمبلغ: 87.30 ر.س\nلدى: NOVA MART\nفي: 2026-03-05 10:42"),
        Triple("S08", MARCH, "PoS Purchase\nResult: Not Accepted\nAmount: SAR 87.30\nAt: NOVA MART\n2026-03-05 10:42"),
        Triple("S09", MARCH, "شراء إنترنت\nعملية غير مقبولة\nمبلغ: 245.60 ر.س\nلدى: NOVA MART\nفي: 2026-03-05 10:42"),
        Triple("S10", MARCH, "Online Purchase\nAmount: SAR 245.60\nAt: NOVA MART\nReason: Expired card\n2026-03-05 10:42"),
        Triple("S11", MARCH, "VISA Purchase\nVia: *5208\nAmount: 87.30 SAR\nFrom: NOVA MART\nStatus: Refused\nAt: 2026-03-05 10:42"),
        Triple("S12", MARCH, "شراء نقاط بيع\nبـ87.30 SAR\nمن NOVA MART\nعملية غير مكتملة\nمدى *3917"),
        Triple("S13", MARCH, "Online Purchase\nIncomplete transaction\nAmount: SAR 245.60\nAt: NOVA MART\n2026-03-05 10:42"),
        Triple("S14", MARCH, "Debit Transfer Local\nAmount: SAR 1,320.00\nTo: FAHAD SAMPLE\nTransfer not sent - beneficiary account closed\n2026-03-05 10:42"),
        Triple("S15", MARCH, "سحب صراف آلي\nمبلغ: 500.00 ر.س\nالصراف: ORBIT ATM\nلم يُصرف المبلغ\nفي: 2026-03-05 10:42"),
        Triple("S16", MARCH, "ATM Withdrawal\nAmount: SAR 500.00\nAt: ORBIT ATM\nCash not dispensed\n2026-03-05 10:42"),
        Triple("X11", MARCH, "Bill Payment\nAmount: SAR 230.40\nBiller: QAMAR TELECOM\nStatus: Unpaid\n2026-03-05 10:42"),
        Triple("X14", MARCH, "سداد فاتورة\nالمبلغ: 230.40 ر.س\nالمفوتر: QAMAR TELECOM\nالحالة: لم تسدد\n2026-03-05 10:42"),
        Triple("E17", MARCH, "A Trx using card 5208 from NOVA MART for EGP 1,250.00 on 05/03/2026 09:40 was not accepted"),
        // ── 3. مجدولة · أمر مستديم · القسط الجاي ──
        Triple("S25", MARCH, "حوالة صادرة محلية\nحوالة مجدولة\nالمبلغ: 1,320.00 ر.س\nالى: FAHAD SAMPLE\nتاريخ التنفيذ: 2026-03-06"),
        Triple("S26", MARCH, "Debit Transfer Local\nFuture dated transfer\nAmount: SAR 1,320.00\nTo: FAHAD SAMPLE\nExecution date: 2026-03-06"),
        Triple("S27", MARCH, "سداد فاتورة\nتمت جدولة السداد\nالمبلغ: 230.40 ر.س\nالمفوتر: QAMAR TELECOM\nموعد السداد: 2026-03-06"),
        Triple("S28", MARCH, "امر مستديم حوالة صادرة محلية\nتم إنشاء الأمر بنجاح\nالمبلغ: 1,500.00 ر.س\nالى: MONA EXAMPLE\nتاريخ الإنشاء: 2026-03-05"),
        Triple("S29", MARCH, "Permanent transfer Bill Payment\nStanding order created\nAmount: SAR 300.00\nBiller: QAMAR TELECOM\n2026-03-05"),
        Triple("S30", MARCH, "امر مستديم سداد فواتير\nتم إيقاف الأمر المستديم\nالمبلغ: 300.00 ر.س\n2026-03-05"),
        Triple("S32", MARCH, "خصم قسط تمويل\nالقسط القادم\nالمبلغ: 1,250.00 ر.س\nتاريخ الخصم: 2026-03-06"),
        Triple("S33", MARCH, "Debit Transfer Loan Instalment\nNext instalment\nAmount: SAR 1,250.00\nDebit date: 2026-03-06"),
        Triple("D03", MARCH, "حوالة صادرة داخلية\nحوالة مجدولة\nمبلغ: SAR 1,250.00\nإلى: TEST PERSON\nتاريخ التنفيذ: 2026-03-06"),
        // ── 4. رمز تحقق بصيغة تانية تحت عنوان شراء أو حوالة ──
        Triple("S01", MARCH, "شراء إنترنت\nرمز لمرة واحدة 731905\nمبلغ: 245.60 ر.س\nلدى: NOVA MART\nفي: 2026-03-05 10:42"),
        Triple("S02", MARCH, "Online Purchase\nVerification No. 731905\nAmount: SAR 245.60\nAt: NOVA MART\n2026-03-05 10:42"),
        Triple("S03", MARCH, "Online Purchase\nUse 731905 to confirm this payment\nAmount: SAR 245.60\nAt: NOVA MART\n2026-03-05 10:42"),
        Triple("S04", MARCH, "حوالة صادرة محلية\nرقمك السري المؤقت 731905\nالمبلغ: 1,320.00 ر.س\nالى: FAHAD SAMPLE\nفي: 2026-03-05 10:42"),
        Triple("S05", MARCH, "Online Purchase\n731905 is your code. Do not share it\nAmount: SAR 245.60\nAt: NOVA MART\n2026-03-05 10:42"),
        Triple("S06", MARCH, "شراء إنترنت\nلإتمام العملية أدخل الرمز المرسل 731905\nمبلغ: 245.60 ر.س\nلدى: NOVA MART\nفي: 2026-03-05 10:42"),
        Triple("D07", MARCH, "شراء انترنت\nرمز لمرة واحدة 731905\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\n2026-03-05 11:20"),
        Triple("D08", MARCH, "Online Purchase\nVerification number 731905\nAmount: SAR 87.40\nAt: TEST BAKERY\n2026-03-05 11:20"),
        // ── 5. حجز وتفويض ──
        Triple("S22", MARCH, "PoS Purchase\nAuth hold: SAR 600.00\nAt: LUMEN HOTEL\n2026-03-05 10:42"),
        Triple("S23", MARCH, "Online Purchase\nAmount: SAR 245.60\nAt: NOVA MART\nAwaiting settlement\n2026-03-05 10:42"),
        Triple("S24", MARCH, "شراء عبر نقاط البيع\nمبلغ مبدئي: 300.00 ر.س\nلدى: ORBIT FUEL\nفي: 2026-03-05 10:42"),
        Triple("D09", MARCH, "شراء\nAmount reserved: SAR 500.00\nAt: TEST HOTEL\n2026-03-05 11:20"),
        Triple("D10", MARCH, "شراء\nتم تعليق مبلغ SAR 500.00\nلدى: TEST HOTEL\n2026-03-05 11:20"),
        // ── 6. كشف البطاقة · بطاقة موقوفة ──
        Triple("S41", MARCH, "بطاقة ائتمانية تسديد\nكشف البطاقة جاهز\nإجمالي المبلغ المستحق: 4,310.00 ر.س\nتاريخ الكشف: 2026-03-05\nآخر موعد: 2026-03-25"),
        Triple("S42", MARCH, "Credit Card Payment\nE-statement is ready\nTotal amount: SAR 4,310.00\nStatement date: 2026-03-05"),
        Triple("S39", MARCH, "Credit Card Payment\nYour card *5208 has been blocked due to overdue amount\nOverdue: SAR 820.00\n2026-03-05"),
        // ── 7. اترجعت أو اتعكست أو طلب استرداد ──
        Triple("S57", MARCH, "حوالة صادرة محلية\nتم إرجاع الحوالة من بنك المستفيد\nالمبلغ: 1,320.00 ر.س\nالى: FAHAD SAMPLE\nفي: 2026-03-05 10:42"),
        Triple("S58", MARCH, "Outgoing Local Transfer\nStatus: Returned by beneficiary bank\nAmount: SAR 1,320.00\nTo: FAHAD SAMPLE\nOn: 2026-03-05 10:42"),
        Triple("X09", MARCH, "شراء إنترنت\nمبلغ: 245.60 ر.س\nلدى: NOVA MART\nفي: 2026-03-05 10:42\nتم عكس العملية"),
        Triple("X10", MARCH, "Online Purchase\nAmount: SAR 245.60\nAt: NOVA MART\n2026-03-05 10:42\nTransaction reversed"),
        Triple("X07", MARCH, "تم خصم 1,250.00 جم من بطاقة الخصم المباشر رقم 5208 عند NOVA MART يوم 03-05 الساعة 09:40 - عملية مرتجعة"),
        Triple("X08", MARCH, "Your credit card ending with#5208 was charged for EGP 1,250.00 at NOVA MART on 05/03/26 at 09:40 - transaction reversed"),
        Triple("X12", MARCH, "استرداد شراء\nتم تسجيل طلب الاسترداد\nمبلغ: 245.60 ر.س\nلدى: NOVA MART\nفي: 2026-03-05 10:42"),
        Triple("X13", MARCH, "Credit Card Refund\nRefund initiated by merchant\nAmount: SAR 245.60\nOn: 2026-03-05 10:42"),
        // ── 8. شيك رجع أو لسه تحت التحصيل ──
        Triple("S62", MARCH, "إيداع شيك ورقي\nالمبلغ: 5,600.00 ر.س\nشيك رقم: 0412\nشيك معاد\n2026-03-05"),
        Triple("X05", MARCH, "إيداع شيك ورقي\nالمبلغ: 5,600.00 ر.س\nشيك رقم: 0412\nتمت إعادة الشيك\n2026-03-05"),
        Triple("S63", MARCH, "Deposit Paper Cheque\nAmount: SAR 5,600.00\nCheque No. 0412\nUnder clearing\n2026-03-05"),
        Triple("E13", MARCH, "Your account ending in 4460 has been credited with EGP 2,000.00 on 05/03/2026 - cheque under collection"),
        // ── 9. أكتر من تاريخ (ميعاد استحقاق · تاريخ طلب · تاريخ كشف) ──
        Triple("S35", MARCH, "خصم قسط تمويل\nالمبلغ: 1,250.00 ر.س\nتاريخ الاستحقاق: 2026-02-25\nتاريخ الخصم: 2026-03-05"),
        Triple("S59", MARCH, "حوالة واردة محلية\nالمبلغ: 2,000.00 ر.س\nمن: MONA EXAMPLE\nتاريخ الطلب: 2026-02-26\nتاريخ الإيداع: 2026-03-05"),
        Triple("E29", MARCH, "تم خصم 1,250.00 جم من بطاقة الائتمان رقم 5208 عند NOVA MART يوم 03-05 الساعة 09:40 - تاريخ آخر كشف 28/02/2026"),
        Triple("F01", MARCH, "عكس عملية\nمبلغ: SAR 87.40\nالعملية الأصلية: 2026-02-20\nفي: TEST BAKERY\nبتاريخ: 2026-03-05"),
        Triple("F02", MARCH, "حوالة واردة\nمبلغ: SAR 1,250.00\nتاريخ الطلب: 2026-03-01\nتاريخ الإيداع: 2026-03-05\nمن: TEST PERSON"),
        Triple("F09", MARCH, "Your credit card ending with 7731 was charged for EGP 87.40 at TEST BAKERY on 05/03. Statement date 28/02/2026"),
        // ── 10. قالب مصري معروف وبعده كلام زيادة (الجملة كلها لازم على القالب) ──
        Triple("vf17", OCTOBER, "Money Request: Received EGP 400.00 from 01000005577 to Mobile Account Number 01000009900. Dial *9*15# to approve"),
        Triple("vf17b", OCTOBER, "Reversal: Received EGP 400.00 from 01000005578 to Mobile Account Number 01000009900 has been reversed by the sender bank"),
        Triple("vf18", OCTOBER, "تم استلام 250 جنيه من رقم 01000006688 رصيدك الحالي 250 جنيه - العملية في انتظار موافقتك من التطبيق"),
        Triple("vf20", OCTOBER, "تم استلام مبلغ 400 جنيه من رقم 01000005579 المسجل باسم هاني الوهمي على رقم محفظتك 01000009900 وتم عكس العملية لطلب البنك المرسل"),
        Triple("vf22", OCTOBER, "تم تحويل 600 جنيه لرقم 01000002020 مصاريف الخدمة 1 جنيه رصيد حسابك فى فودافون كاش الحالي 1,399. الرقم غير مسجل في فودافون كاش وتم استرجاع المبلغ لرصيدك"),
        Triple("ee05", OCTOBER, "تم تحويل 220 جنيه لرقم 01100009913 رصيدك الحالي 75 جنيه - العملية تحت المراجعة"),
        Triple("ip12", OCTOBER, "IPN transfer received with amount of EGP 1,100.00 from TAREK TEST awaiting confirmation"),
        Triple("ip15", OCTOBER, "IPN transfer received with amount of EGP 600.00 from NOUR TEST has been returned to the sender due to account mismatch"),
        Triple("bk01", OCTOBER, "تم خصم 300.00 جم من بطاقة الخصم المباشر رقم 4455 عند TEST SHOP يوم 10-08 الساعة 12:40 - العملية غير مكتملة وهيترد المبلغ خلال 14 يوم"),
        Triple("bk02b", OCTOBER, "تم خصم 150.00 جم من بطاقة الائتمان رقم 9911 عند TEST THEATRE يوم 10-08 الساعة 21:00. العملية اتلغت"),
        Triple("bk03", OCTOBER, "Your credit card ending with#7788 was charged for EGP 520.00 at TEST HOTEL on 08/10/26 at 08:05. This charge has been reversed."),
        Triple("D16", MARCH, "تم خصم 87.40 جم من بطاقة الخصم المباشر رقم 7731 عند TEST BAKERY يوم 03-05 الحالة: غير مكتملة"),
        Triple("G11b", MARCH, "تم خصم 87.40 جم من بطاقة الخصم المباشر رقم 7731 عند TEST BAKERY يوم 03-05 (عملية مؤجلة)"),
        // ── 11. مبلغ من خانة غلط (رسوم · ضريبة · حد) ──
        Triple("vf21", OCTOBER, "تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة وخصم 1.50 جنيه من محفظتك. رصيد حسابك في فودافون كاش الحالي 98.50"),
        Triple("or03", OCTOBER, "تم تحويل 500.00 جنيه لرقم 01200005566 رصيدك الحالي 380.00 جنيه وخصم 5.00 جنيه من محفظتك مصاريف خدمة رقم العملية 900000122"),
        Triple("we02", OCTOBER, "تم استلام مبلغ 2,000 جنيه من رقم 01500006677 رصيدك الحالي 2,390 جنيه وخصم 10 جنيه من محفظتك رسوم استلام رقم العملية 900000124"),
        Triple("B11", MARCH, "تم استلام مبلغ 750.00 جنيه من رقم 01000000431 المسجل باسم TEST PERSON وخصم 7.50 من محفظتك"),
        Triple("or04", OCTOBER, "تم تحويل 500 لرقم 01200009876 رصيدك الحالي 1,200 جنيه تكلفة الخدمة 1.50 جنيه رقم العملية 900000210"),
        Triple("ab01", OCTOBER, "A Trx using Card XXXX6604 from TEST LIMIT FITNESS for EGP 300.00 on 08/10/2026 at 10:00 GMT+2 incl. VAT EGP 42.00. Available balance is EGP 2,000.00."),
        Triple("A01", MARCH, "شراء\nمبلغ: 87.40\nحد الائتمان: 6,000.00 SAR\nلدى: TEST BAKERY\n2026-03-05 11:20"),
        Triple("A02", MARCH, "شراء عبر نقاط البيع\nمبلغ: 87.40\nالمستحق: 2,315.60 SAR\nلدى: TEST BAKERY\n2026-03-05 11:20"),
        Triple("A03", MARCH, "PoS Purchase\nAmount 87.40\nOutstanding: SAR 2,315.60\nAt: TEST BAKERY\n2026-03-05 11:20"),
        Triple("A04", MARCH, "شراء\nمبلغ: 87.40\nسقف البطاقة 10,000 ر.س\nلدى: TEST BAKERY\n2026-03-05 11:20"),
        Triple("A05", MARCH, "شراء\nمبلغ: 87.40\nقيمة النقاط المكتسبة SAR 4.37\nلدى: TEST BAKERY\n2026-03-05 11:20"),
        Triple("A06", MARCH, "PoS Purchase\nAmount 87.40\nTrace SR 5317\nAt: TEST BAKERY\n2026-03-05 11:20"),
        Triple("A07", MARCH, "شراء\nمبلغ: 87.40\nلدى: SR 9 MART\n2026-03-05 11:20"),
        // ── 12. التاريخ يوم-شهر من مرسل مش الأهلي ──
        Triple("dt01", "2026-10-08T09:30:00Z", "تم استلام 300 جنيه من رقم 01000001358 رصيدك الحالي 500 جنيه يوم 08-10 الساعة 14:00"),
    )
}
