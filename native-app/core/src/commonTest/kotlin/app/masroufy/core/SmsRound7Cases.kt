package app.masroufy.core

/**
 * الجولة السابعة — رسايل المراجعة العدائية التالتة لفرع الجولة السادسة (`sms-formats-r4-f1-f2` @ 392a86b)، **السعودية**. كلها **مخترعة**
 * (HISHAM SAMPLE · YAZEED SAMPLE · SAFA OPTICS · QUOLL BAKERY · الكارت 7739 …). كل واحدة كانت **بتتسجل لوحدها** وهي مش عملية خلصت
 * أو بمبلغ أو تاريخ غلط. المطلوب: ما تتسجلش لوحدها في أي بلد (ترفض أو تستنى — `SmsShape.clear` = false).
 */
internal object SmsRound7Cases {
    /** وصول الرسايل: 2026-10-08 الساعة 12:30 بتوقيت الرياض. */
    const val OCT8 = "2026-10-08T09:30:00Z"

    /** بعد نص الليل بتوقيت الرياض بخمس دقايق (2026-10-08 00:05). */
    const val AFTER_MIDNIGHT = "2026-10-07T21:05:00Z"

    val mustNotAutoRecord: List<Triple<String, String, String>> = listOf(
        // ── 1. سطر من غير «:» بيبدأ بكلمة لابل («To be credited …» · «من المتوقع …» · «عند تحصيل …» · «الى حين …») ──
        Triple("B01", OCT8, "Credit transfer International\nAmount: SAR 3,740.00\nFrom: HISHAM SAMPLE\nOn: 2026-10-08\nTo be credited within 2 working days"),
        Triple("B02", OCT8, "حوالة واردة دولية\nالمبلغ: 3,740.00 ر.س\nمن: هشام التجريبي\nفي: 2026-10-08\nمن المتوقع إيداع المبلغ في حسابك خلال يومي عمل"),
        Triple("B11", OCT8, "إيداع شيك ورقي\nالمبلغ: 5,000.00 ر.س\nالحساب: **6618\nفي: 2026-10-08\nعند تحصيل الشيك سيضاف المبلغ لحسابك"),
        Triple("B10", OCT8, "Deposit Paper Cheque\nAmount: SAR 5,000.00\nAccount: **6618\nOn: 2026-10-08\nTo be cleared within 2 working days"),
        Triple("B04", OCT8, "Outgoing Local Transfer\nAmount: SAR 3,250.00\nTo: YAZEED SAMPLE\nOn: 2026-10-08 22:40\nTo be executed on the next business day"),
        Triple("B05", OCT8, "حوالة صادرة محلية\nالمبلغ: 3,250.00 ر.س\nالى: يزيد التجريبي\nفي: 2026-10-08 09:40\nمن المقرر تنفيذ الحوالة يوم العمل التالي"),
        Triple("SN57", OCT8, "Credit Card Refund\nCard: *4476\nAmount: SAR 89.00\nAt: SAFA OPTICS\nOn: 2026-10-08\nTo be posted in 5-7 working days"),
        Triple("B03", OCT8, "Credit Card Refund\nCard: *4476\nAmount: SAR 89.00\nAt: SAFA OPTICS\nOn: 2026-10-08\nTo be credited to your card within 14 days"),
        Triple("B09", OCT8, "استرداد شراء\nبطاقة: 7739*;مدى\nمبلغ: 89.00 ر.س\nمن: SAFA OPTICS\nفي: 2026-10-08\nمن المتوقع ظهور المبلغ في حسابك خلال 14 يوم"),
        Triple("B06", OCT8, "Received transfer\nAmount: SAR 980.00\nFrom: YAZEED SAMPLE\nOn: 2026-10-08\nTo be reflected in your account after compliance check"),
        Triple("B07", OCT8, "حوالة واردة محلية\nالمبلغ: 980.00 ر.س\nمن: يزيد التجريبي\nفي: 2026-10-08\nالى حين استكمال التحقق من بيانات المستفيد"),
        Triple("B12", OCT8, "Debit Transfer International\nAmount: SAR 3,740.00\nTo: HISHAM SAMPLE\nOn: 2026-10-08\nTo be sent after the compliance review"),
        Triple("C06", OCT8, "Received transfer\nAmount: SAR 1,200.00\nFrom: YAZEED SAMPLE\nOn: 2026-10-08\nTo be released after KYC update"),
        Triple("C07", OCT8, "حوالة واردة\nالمبلغ: 1,200.00 ر.س\nمن: يزيد التجريبي\nفي: 2026-10-08\nمن فضلك حدّث بياناتك لإضافة المبلغ لحسابك"),
        Triple("C08", OCT8, "Received transfer\nAmount: SAR 1,200.00\nFrom: YAZEED SAMPLE\nOn: 2026-10-08\nTo receive funds update your ID"),
        Triple("C09", OCT8, "إيداع حوالة واردة\nالمبلغ: 1,200.00 ر.س\nمن: يزيد التجريبي\nفي: 2026-10-08\nعند اكتمال التحقق يضاف المبلغ"),
        // ── 2. الخانة الحرة فيها كلام حالة مش في قايمة الكلمات (الخانة بقت «كلمات اسم بس») ──
        Triple("SN24", OCT8, "Bill Payment\nAmount: SAR 412.60\nBiller: SAMPLE POWER CO\nTransaction: did not go through\nOn: 2026-10-08 10:01"),
        Triple("SN25", OCT8, "Online Purchase\nAmount: SAR 349.00\nAt: SAFA OPTICS\nTransaction: stopped by bank\nOn: 2026-10-08 10:12"),
        Triple("SN26", OCT8, "Received transfer\nAmount: SAR 3,250.00\nFrom: YAZEED SAMPLE wasn't completed\nOn: 2026-10-08"),
        Triple("SN63", OCT8, "Outgoing Local Transfer\nAmount: SAR 3,250.00\nTo: YAZEED SAMPLE\nTransaction: on queue\nOn: 2026-10-08 09:40"),
        Triple("B14", OCT8, "Online Purchase\nAmount: SAR 349.00\nAt: SAFA OPTICS\nTransaction: sent for approval\nOn: 2026-10-08 10:12"),
        Triple("SN09", OCT8, "Online Purchase\nAmount: SAR 349.00\nAt: SAFA OPTICS\nTransaction: confirm 551204\nOn: 2026-10-08 10:12"),
        Triple("SN65", OCT8, "Online Purchase\nAmount: SAR 1.00\nAt: CLOUDNEST APPS\nTransaction: card check\nOn: 2026-10-08 10:12"),
        Triple("SN68", OCT8, "Bill Payment\nAmount: SAR 412.60\nBiller: SAMPLE POWER CO\nService: new bill issued\nOn: 2026-10-08 10:01"),
        Triple("SN66", OCT8, "سداد فاتورة\nالمبلغ: 412.60 ر.س\nالجهة: شركة الكهرباء التجريبية\nالخدمة: فاتورة جديدة صادرة\nفي: 2026-10-08 10:01"),
        Triple("SN14", OCT8, "شراء عبر نقاط البيع\nمبلغ: 1,500.00 ر.س\nلدى: فندق الرمال ضمان\nفي: 2026-10-08 14:05"),
        Triple("K01", OCT8, "PoS Purchase\nAmount: SAR 150.00\nAt: OKAPI FUEL 14 ESTIMATED\nOn: 2026-10-07 18:22"),
        Triple("K04", OCT8, "شراء عبر نقاط البيع\nمبلغ: 150.00 ر.س\nلدى: محطة الغزال مبلغ تقديري\nفي: 2026-10-07 18:22"),
        Triple("K15", OCT8, "PoS Purchase\nAmount: SAR 150.00\nAt: QUOLL HOTEL INCREMENTAL AUTH\nOn: 2026-10-07 18:22"),
        Triple("K02", OCT8, "Debit Transfer Local\nAmount: SAR 900.00\nTo: RAYAN EXAMPLE UNCONFIRMED\nOn: 2026-10-07 11:40"),
        // ── 3. سطور حرة بقت مقفولة: ملاحظة دعم حكومي · الخدمة · الفاتورة · السبب ──
        Triple("K05", OCT8, "ايداع دعم حكومي - حساب المواطن\nمبلغ: 1,200.00 ر.س\nيتم الايداع خلال يومين\nفي: 2026-10-07"),
        Triple("K06", OCT8, "ايداع دعم حكومي - حساب المواطن\nمبلغ: 1,200.00 ر.س\nستودع الدفعة خلال يومين\nفي: 2026-10-07"),
        Triple("K07", OCT8, "Bill Payment\nAmount: SAR 230.00\nBiller: SAMPLE POWER CO\nService: payable next week\nOn: 2026-10-07 09:00"),
        Triple("K08", OCT8, "سداد فاتورة\nمبلغ: 230.00 ر.س\nالجهة: شركة الكهرباء التجريبية\nالفاتورة: مستحقة بعد اسبوع\nفي: 2026-10-07"),
        Triple("K09", OCT8, "Debit Fees\nAmount: SAR 5.75\nReason: Card replacement fee waived\nOn: 2026-10-07 09:00"),
        // ── 4. عنوان دي 360 بالنقطتين: بعدها اسم بنك من القايمة بس ──
        Triple("T01", OCT8, "Incoming Transfer: Expected\nAmount: SAR 500.00\nFrom: RAYAN EXAMPLE\nOn: 2026-10-07 11:40"),
        Triple("T03", OCT8, "Incoming Internal Transfer: Unverified\nAmount: SAR 500.00\nOn: 2026-10-07 11:40"),
        Triple("T04", OCT8, "Outgoing Internal Transfer: Draft\nAmount: SAR 500.00\nTo: RAYAN EXAMPLE\nOn: 2026-10-07 11:40"),
        Triple("T06", OCT8, "Incoming Transfer: Awaited\nAmount: SAR 500.00\nFrom: RAYAN EXAMPLE\nOn: 2026-10-07 11:40"),
        // ── 5. علامات قلب الاتجاه (المعروض غير المقروء) ──
        Triple("J01", OCT8, "PoS Purchase\nAmount: SAR ‮06.84‬\nAt: QUOLL BAKERY\nOn: 2026-10-07 18:22"),
        Triple("J04", OCT8, "PoS Purchase\nAmount: SAR 48.60\nAt: QUOLL BAKERY ‮DENILCED‬\nOn: 2026-10-07 18:22"),
        Triple("J05", OCT8, "شراء عبر نقاط البيع\nمبلغ: 48.60 ر.س\nلدى: صيدلية النورس ‭ةضوفرم‬\nفي: 2026-10-07 18:22"),
        Triple("J06", OCT8, "PoS Purchase\nAmount: SAR 48.60\nAt: ‫QUOLL BAKERY‬\nOn: 2026-10-07 18:22"),
        // ── 6. «Total due amount» على عملية داخلة ──
        Triple("A08", OCT8, "Received transfer\nAmount: SAR 1,000.00\nFees: SAR 5.00\nTotal due amount: SAR 1,005.00\nFrom: RAYAN EXAMPLE\nOn: 2026-10-07 11:40"),
        Triple("A10", OCT8, "Credit transfer Salary\nAmount: SAR 9,800.00\nFees: SAR 0.50\nTotal due amount: SAR 9,800.50\nOn: 2026-10-07 11:40"),
        Triple("A09", OCT8, "Credit Card Refund\nAmount: SAR 300.00\nVAT: SAR 45.00\nTotal due amount: SAR 345.00\nOn: 2026-10-07 11:40"),
        // ── 7. الأهلي من غير تاريخ وفيه ساعة (وصلت بعد نص الليل) ──
        Triple("H07", AFTER_MIDNIGHT, "شراء نقاط بيع\nبـ48.60 SAR\nمن QUOLL BAKERY\n23:58\nمدى *3906"),
        Triple("H13", AFTER_MIDNIGHT, "شراء نقاط بيع\nبـ48.60 SAR\nمن QUOLL BAKERY\n09:10 23:58\nمدى *3906"),
        // ── 8. تاريخ أقدم من 60 يوم (أو قرايته التانية هي اللي في النافذة) ──
        Triple("D09", OCT8, "PoS Purchase\nAmount: SAR 48.60\nAt: QUOLL BAKERY\nOn: 10/07/2026 18:22"),
        Triple("D10", OCT8, "PoS Purchase\nAmount: SAR 48.60\nAt: QUOLL BAKERY\nOn: 2025-11-02 18:22"),
        // ── 9. نفس اللابل مرتين بقيمتين (عمليتين في رسالة) ──
        Triple("A01", OCT8, "PoS Purchase\nAmount: SAR 48.60\nAt: QUOLL BAKERY\nOn: 2026-10-07 18:22\nAmount: SAR 48.60\nAt: MARLIN TOYS\nOn: 2026-10-07 18:25"),
        Triple("A02", OCT8, "شراء عبر نقاط البيع\nمبلغ: 48.60 ر.س\nلدى: مخبز الوعل\nلدى: متجر المرلين\nفي: 2026-10-07 18:22"),
        Triple("A03", OCT8, "Outgoing Internal Transfer: Al Rajhi Bank\nFrom: **7741\nFrom: **2290\nAmount: SAR 500.00\nTo: RAYAN EXAMPLE\nOn: 2026-10-07 11:40"),
        Triple("A04", OCT8, "حوالة صادرة محلية\nمبلغ: 900.00 ر.س\nإلى: ريان التجريبي\nإلى: سلمان التجريبي\nفي: 2026-10-07 11:40"),
        // ── 10. محل برّه البلد (كود البلد في آخر اسمه) والمبلغ بالريال (§75-12) ──
        Triple("SF09", OCT8, "شراء عبر نقاط البيع\nمبلغ: 120.00 ر.س\nلدى: SAMPLE MALL DUBAI AE\nفي: 2026-10-08 10:00"),
        Triple("SF24", OCT8, "Online Purchase\nAmount: SAR 93.75\nAt: SAMPLE SEATTLE US\nOn: 2026-10-08"),
        Triple("SF25", OCT8, "شراء إنترنت\nمبلغ: 93.75 ر.س\nلدى: SAMPLE.COM LONDON GBR\nفي: 2026-10-08"),
        // ── 11. رقم الموافقة (مش رمز) — الرسالة بتتحفظ وبتستنى (السطر مش خانة معروفة) ──
        Triple("SR14", OCT8, "شراء عبر نقاط البيع\nمبلغ: 212.00 ر.س\nلدى: سفا للبصريات\nرمز الموافقة: 553120\nفي: 2026-10-08 10:12"),
        Triple("SR27", OCT8, "PoS Purchase\nAmount: SAR 48.50\nAt: BUSTAN CAFE\nAuth. Code: 553120\nOn: 2026-10-08 11:42"),
        Triple("M02", OCT8, "PoS Purchase\nAmount: SAR 48.50\nAt: BUSTAN CAFE\nRef. Code: 553120\nOn: 2026-10-08 11:42"),
        Triple("M03", OCT8, "PoS Purchase\nAmount: SAR 48.50\nAt: BUSTAN CAFE\nTxn Code: 553120\nOn: 2026-10-08 11:42"),
        Triple("M04", OCT8, "شراء عبر نقاط البيع\nمبلغ: 48.50 ر.س\nلدى: بستان كافيه\nكود الموافقة: 553120\nفي: 2026-10-08 11:42"),
        // ── 12. رمز تحقق بصيغ جديدة جوه شكل شراء ──
        Triple("SN08", OCT8, "Online Purchase\nAmount: SAR 349.00\nAt: SAFA OPTICS\nVerification: 551204\nOn: 2026-10-08 10:12"),
        Triple("SN07", OCT8, "Online Purchase\nAmount: SAR 349.00\nAt: SAFA OPTICS\nPassword: 551204\nValid for 3 minutes"),
    )

    /** لازم **تترفض** في القارئ السعودي (مش «جاهزة تستنى» — ضغطة «سجّل الكل» كانت هتسجلها بمبلغ أو اتجاه غلط). */
    val saudiMustReject: List<String> = listOf(
        "B01", "B02", "B11", "B10", "B04", "B05", "SN57", "B03", "B09", "B06", "B07", "B12", "C06", "C07", "C08", "C09",
        "J01", "J04", "J05", "A08", "A10", "A09", "A01", "SN08", "SN07",
    )
}
