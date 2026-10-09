package app.masroufy.core

import app.masroufy.core.SmsRound5Cases.MARCH

/** تكملة [SmsRound5Cases] (حد الـ300 سطر): اتجاه متناقض · عملة أجنبية · حالة بعد العنوان · عنوان بلاحقة · حروف مخفية. */
internal object SmsRound5Cases2 {
    val mustNotAutoRecord: List<Triple<String, String, String>> = listOf(
        // ── اتجاه العنوان عكس باقي الرسالة ──
        Triple("B01", MARCH, "شراء\nتم استرداد مبلغ SAR 87.40 إلى بطاقتك\nمن: TEST BAKERY\n2026-03-05 11:20"),
        Triple("B02", MARCH, "شراء انترنت\nعكس العملية\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\n2026-03-05 11:20"),
        Triple("B03", MARCH, "سحب صراف آلي\nتم إيداع مبلغ SAR 500.00\nالصراف: TEST ATM 07\n2026-03-05 11:20"),
        Triple("B04", MARCH, "حوالة صادرة داخلية\nمرتجعة\nمبلغ: SAR 1,250.00\nإلى: TEST PERSON\n2026-03-05 11:20"),
        Triple("B05", MARCH, "PoS Purchase\nAmount: SAR 87.40\nAt: TEST BAKERY\nStatus: Reversed\n2026-03-05 11:20"),
        Triple("B06", MARCH, "Online Purchase\nAmount: SAR 87.40 credited back to your card\nAt: TEST BAKERY\n2026-03-05 11:20"),
        Triple("A12", MARCH, "شراء\nمبلغ: SAR 87.40 CR\nلدى: TEST BAKERY\n2026-03-05 11:20"),
        Triple("B13", MARCH, "Credit transfer Local\nAmount: SAR 1,250.00\nDebit from your account\nFrom: TEST PERSON\n2026-03-05 11:20"),
        Triple("A09", MARCH, "حوالة واردة\nمبلغ: -1,250.00 SAR\nمن: TEST SENDER\n2026-03-05 11:20"),
        Triple("B08", MARCH, "تم خصم 87.40 جم من بطاقة الخصم المباشر رقم 7731 عند TEST BAKERY يوم 03-05 وتم عكس العملية"),
        Triple("B09", MARCH, "Your credit card ending with 7731 was charged for EGP 87.40 at TEST BAKERY on 05/03 and the charge was reversed"),
        Triple("B10", MARCH, "تم خصم 87.40 جم من بطاقة الخصم المباشر رقم 7731 عند TEST BAKERY يوم 03-05 - تم استرداد المبلغ"),
        Triple("B14", MARCH, "تم اضافة تحويل لحظي لحسابكم بمبلغ 1,250.00 جم يوم 03-05 وتم سحب 1,250.00 جم"),
        // ── عملة أجنبية مكتوبة باختصار أو رمز، أو المحلي الأول (§75-12: ما بتتسجلش لوحدها أبدًا) ──
        Triple("C01", MARCH, "شراء دولي\nمبلغ: 450.00 LE (SAR 34.20)\nلدى: TEST BAZAAR\n2026-03-05 11:20"),
        Triple("C02", MARCH, "PoS International Purchase\nAmount: 61.50 QR (SAR 63.35)\nAt: TEST SOUK\n2026-03-05 11:20"),
        Triple("C03", MARCH, "شراء انترنت\nمبلغ: 150.00 د.إ (153.60 ر.س)\nلدى: TEST MALL\n2026-03-05 11:20"),
        Triple("C04", MARCH, "شراء\nمبلغ: 12.500 KD\n(SAR 152.40)\nلدى: TEST SOUK\n2026-03-05 11:20"),
        Triple("C05", MARCH, "شراء دولي\nالمبلغ: 45.00 QR\nالمبلغ المخصوم: 46.35 SAR\nلدى: TEST SOUK\n2026-03-05 11:20"),
        Triple("C06", MARCH, "Online Purchase\nAmount: 2,600 Rs (SAR 117.00)\nAt: TEST BAZAAR\n2026-03-05 11:20"),
        Triple("C07", MARCH, "شراء عبر نقاط البيع دولية\nمبلغ: 9.800 ر.ع (95.45 ر.س)\nلدى: TEST SOUK\n2026-03-05 11:20"),
        Triple("C14", MARCH, "Online Purchase\nAmount: 350.00 ฿ (SAR 36.40)\nAt: TEST MARKET\n2026-03-05 11:20"),
        Triple("C10", MARCH, "شراء انترنت\nمبلغ: SAR 153.60 (150.00 د.إ)\nلدى: TEST MALL\n2026-03-05 11:20"),
        Triple("C18", MARCH, "شراء دولي\nبطاقة:7731;مدى\nمبلغ:QR 61.50\nدولة:QAT\nلدى:TEST SOUK\nالمبلغ بالريال:SAR 63.35\nفي:2026-03-05 11:20"),
        Triple("C08", MARCH, "Your credit card ending with#7731 was charged for 61.50 QR (EGP 820.00) at TEST SOUK on 05/03/26 at 11:20"),
        Triple("C09", MARCH, "تم خصم 1,950.00 جم من بطاقة الائتمان رقم 7731 عند TEST MALL (150.00 د.إ) يوم 03-05"),
        Triple("C11", MARCH, "شراء دولي\nمبلغ: SAR 63.35\nلدى: TEST SOUK\n2026-03-05 11:20"),
        // ── حالة مكتوبة بعد أول سطر ──
        Triple("D01", MARCH, "سداد فاتورة\nتذكير بسداد فاتورة TEST POWER\nمبلغ: SAR 230.40\n2026-03-05"),
        Triple("D02", MARCH, "Bill Payment\nReminder: SAR 230.40 for TEST WATER\n2026-03-05"),
        Triple("D04", MARCH, "شراء\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\nالحالة: غير مكتملة\n2026-03-05 11:20"),
        Triple("D05", MARCH, "PoS Purchase\nAmount: SAR 87.40\nAt: TEST BAKERY\nStatus: Incomplete\n2026-03-05 11:20"),
        Triple("D06", MARCH, "Online Purchase\nTransaction not authorized\nAmount: SAR 87.40\nAt: TEST BAKERY\n2026-03-05 11:20"),
        Triple("D11", MARCH, "شراء\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\nالبطاقة منتهية الصلاحية\n2026-03-05 11:20"),
        Triple("D17", MARCH, "شراء\nالعملية قيد المعالجة\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\n2026-03-05 11:20"),
        Triple("D18", MARCH, "PoS Purchase\nStatus: Processing\nAmount: SAR 87.40\nAt: TEST BAKERY\n2026-03-05 11:20"),
        Triple("D19", MARCH, "حوالة صادرة محلية\nبانتظار الموافقة\nمبلغ: SAR 1,250.00\nإلى: TEST PERSON\n2026-03-05 11:20"),
        Triple("D20", MARCH, "Debit Transfer Local\nAwaiting approval\nAmount: SAR 1,250.00\nTo: TEST PERSON\n2026-03-05 11:20"),
        // ── عنوان بنك معروف بكلمة زيادة ──
        Triple("G04", MARCH, "Incoming Transfer Request\nAmount: SAR 1,250.00\nFrom: TEST PERSON\n2026-03-05 11:20"),
        Triple("G05", MARCH, "Incoming Transfer Returned\nAmount: SAR 1,250.00\nFrom: TEST PERSON\n2026-03-05 11:20"),
        Triple("G06", MARCH, "حوالة داخلية واردة بانتظار التأكيد\nمبلغ: SAR 1,250.00\nمن: TEST PERSON\n2026-03-05 11:20"),
        Triple("G07", MARCH, "عميلنا العزيز،\nتم استلام حوالة واردة قيد التحقق\nمبلغ: SAR 1,250.00\n2026-03-05 11:20"),
        Triple("G08", MARCH, "شراء نقاط بيع مؤجل\nبـ87.40 SAR\nمن TEST BAKERY\n2026-03-05 11:20"),
        Triple("G09", MARCH, "Outgoing Internal Transfer Initiated\nAmount: SAR 1,250.00\nTo: TEST PERSON\n2026-03-05 11:20"),
        Triple("G10", MARCH, "شراء عبر Apple Pay - بانتظار التأكيد\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\n2026-03-05 11:20"),
        // ── حروف تنسيق مخفية جوه كلمة الحارس ──
        Triple("D12", MARCH, "شراء\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\n2026-03-05 11:20\nالعملية مرف​وضة"),
        Triple("D13", MARCH, "PoS Purchase\nAmount: SAR 87.40\nAt: TEST BAKERY\n2026-03-05 11:20\nDecl‌ined"),
        Triple("D14", MARCH, "شراء انترنت\nOT­P 731905\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\n2026-03-05 11:20"),
        // ── طلب إيداع · فلوس رجعت لمحفظتك · طلب دفع · هدية · ما تمش ──
        Triple("S37", MARCH, "عزيزي العميل، قسط التمويل 1,250.00 ر.س بتاريخ 2026-03-06، يرجى إيداع المبلغ في حسابك 7731"),
        Triple("E11", MARCH, "تم استرجاع مبلغ 300.00 جنيه إلى محفظتك من عملية تحويل لرقم 01000002020 رصيدك الحالي 1,300.00 جنيه"),
        Triple("vf08b", SmsRound5Cases.OCTOBER, "التحويل لرقم 01000008899 بمبلغ 600 جنيه ماتمش عشان رصيدك مش كفاية يوم 08/10/2026"),
        Triple("ip06", SmsRound5Cases.OCTOBER, "You received a payment request of EGP 300.00 from 01000004321 via InstaPay. Tap to pay now"),
        Triple("ee11", SmsRound5Cases.OCTOBER, "تم إضافة 20 جنيه هدية لمحفظتك صالحة لمدة 7 أيام للاستخدام في شراء الباقات"),
    )

    /**
     * المسموح لها **تستنى «جاهزة»** (القارئ قراها والشكل بس مش واضح) — والسبب. **أي رسالة تانية في الجدولين لازم تترفض** (حارس ·
     * تناقض · عملة · مبلغ · تاريخ): «سجّل الكل» كان هيسجلها زي ما هي بمبلغ أو اتجاه غلط.
     */
    val mayWaitReady: Map<String, String> = mapOf(
        "ip15" to "IPN received then «returned to the sender»: read IN, waits (doubt word)",
        "vf21" to "Vodafone send 300 with the fee written as «وخصم 1.50 جنيه»: read OUT 300 (fee skipped), waits (not the template)",
        "B11" to "receive 750 + «وخصم 7.50 من محفظتك» (no currency on the fee): read IN 750, waits",
        "E29" to "two dates: read with the first full date (golden rule), waits",
        "S35" to "two dates: read with the first (golden rule), waits",
        "S59" to "two dates: read with the first (golden rule), waits",
        "F01" to "two dates: read with the first (golden rule), waits",
        "F02" to "two dates: read with the first (golden rule), waits",
        "F09" to "two dates: read with the full date (golden rule), waits",
        "C11" to "international purchase with only a SAR amount: waits (§75-12, choice (م))",
        "G04" to "«Incoming Transfer Request» title: read IN by the keyword rule, waits (not a known title)",
        "G05" to "«Incoming Transfer Returned» title: read IN by the keyword rule, waits (not a known title)",
    )
}
