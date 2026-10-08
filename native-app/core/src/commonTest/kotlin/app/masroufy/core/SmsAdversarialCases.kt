package app.masroufy.core

/**
 * رسايل مخترعة **عدائية** (الجولة التانية من مراجعة قارئ الرسايل — مراجع «الإيجابيات الكاذبة» جرّب 102 رسالة، و43 منها كانت بتعدّي).
 * ⚠️ كل الرسايل مخترعة: المحل TEST GROCER/TEST HOTEL/TEST STORE · الكارت 6604 · الحساب 1188 · المبالغ والأرقام مخترعة (المستودع عام).
 * «SA» = قارئ السعودية، «EG» = قارئ مصر — الرسالة بتتجرب على قارئ بلدها **وعلى قارئ البلد التانية** كمان.
 */
internal object SmsAdversarialCases {
    /** لازم **ما تتسجلش** — ولازم يوقفها **حارس مقصود** (عرض · رمز · مرفوضة · مش عملية) في القارئين وفلتر الجهاز. */
    val mustIgnore: List<Pair<String, String>> = listOf(
        // ── رمز تحقق بكل الصيغ (فيه مبلغ ومحل وتاريخ) ──
        "SA" to "رمز التأكيد 482913 لعملية شراء بمبلغ 64.25 SAR لدى TEST GROCER في 2026-03-05",
        "SA" to "Your one-time PIN for purchase of SAR 64.25 at TEST GROCER is 482913. Date 2026-03-05",
        "SA" to "Use code 482913 to confirm your purchase of SAR 64.25 at TEST GROCER on 2026-03-05. Do not share it.",
        "SA" to "Purchase confirmation code: 482913\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10",
        "SA" to "رمز الأمان 482913\nشراء عبر الانترنت\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 26-03-05 09:10",
        "SA" to "الرمز السري للعملية هو 482913\nشراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\n2026-03-05",
        "SA" to "OTP482913 for Purchase SAR 64.25 at TEST GROCER 2026-03-05",
        "SA" to "Passcode 482913\nPurchase\nAmount SAR 64.25\nAt TEST GROCER\n2026-03-05",
        "SA" to "كود التحقق 482913 لعملية شراء بقيمة 64.25 ريال من TEST GROCER بتاريخ 2026-03-05",
        "SA" to "رمز التحقق: ٤٨٢٩١٣\nشراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\n2026-03-05",
        "SA" to "رمز الأمان 4829\nشراء-POS\nبـ64.25 SAR\nمن TEST GROCER\nمدى *6604",
        "SA" to "Authentication code 482913 for your online purchase of SAR 64.25 at TEST GROCER 2026-03-05",
        "EG" to "Your one-time PIN for purchase of EGP 500.00 at TEST GROCER is 482913",
        "EG" to "كود التحقق 482913 لتأكيد عملية purchase بمبلغ 500 جم عند TEST GROCER",
        // ── تفويض وحجز (الخصم الحقيقي بييجي بعدين) ──
        "SA" to "Purchase Authorization\nCard: *6604\nAmount: SAR 500.00\nAt: TEST HOTEL\n2026-03-05 09:10",
        "SA" to "Pre-Authorization\nPurchase\nCard: *6604\nAmount: SAR 500.00\nAt: TEST HOTEL\n2026-03-05 09:10",
        "SA" to "عملية تفويض\nشراء عبر الانترنت\nبطاقة: 6604\nمبلغ: SAR 500.00\nلدى: TEST HOTEL\nفي: 26-03-05 09:10",
        "SA" to "تم حجز 500.00 ر.س من بطاقتك *6604 لعملية شراء لدى TEST HOTEL بتاريخ 2026-03-05",
        "SA" to "Amount SAR 500.00 is on hold for your purchase at TEST HOTEL 2026-03-05 card *6604",
        "EG" to "An amount of EGP 500.00 has been authorized on your credit card 6604 at TEST HOTEL on 05/03/26",
        // ── مرفوضة · فشلت · اتلغت ──
        "SA" to "Purchase Rejected\nCard: *6604\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10",
        "SA" to "تعذر إتمام عملية الشراء\nبطاقة: *6604\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10",
        "SA" to "فشلت عملية الشراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10",
        "SA" to "رفضت عملية الشراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\n2026-03-05",
        "SA" to "Purchase Cancelled\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10",
        "SA" to "إلغاء عملية شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10",
        "SA" to "Transaction denied: Purchase SAR 64.25 at TEST GROCER 2026-03-05",
        "SA" to "رصيدك لا يسمح بإتمام العملية\nشراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\n2026-03-05",
        "EG" to "Your purchase of EGP 500.00 at TEST GROCER using debit card 6604 was rejected",
        "EG" to "Transaction could not be completed: debit card 6604 EGP 500.00 at TEST GROCER",
        "EG" to "تعذر تنفيذ عملية شراء بمبلغ 500.00 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026",
        "EG" to "عملية ملغاة: تم خصم 500.00 جم من بطاقتك 6604 عند TEST GROCER يوم 05/03/2026",
        // ── لسه معلقة ──
        "SA" to "Purchase (Pending)\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05",
        "SA" to "شراء معلق\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05",
        "SA" to "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05\nThis transaction is pending",
        "EG" to "Transaction of EGP 500.00 on your credit card ending 6604 at TEST HOTEL is pending",
        "EG" to "تم خصم 500 جم من حسابك يوم 05/03/2026 - عملية معلقة لحين التسوية",
        // ── طلبات واعتراضات (الفلوس ما اتحركتش) ──
        "SA" to "Refund Request\nAmount: SAR 64.25\nMerchant: TEST GROCER\nDate: 2026-03-05\nStatus: Under review",
        "SA" to "طلب استرداد مبلغ 64.25 ريال من TEST GROCER بتاريخ 2026-03-05 قيد المراجعة",
        "SA" to "اعتراض على عملية شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05\nتم تسجيل اعتراضك",
        "SA" to "Your dispute for the purchase of SAR 64.25 at TEST GROCER on 2026-03-05 has been received",
        "EG" to "Your refund request of EGP 64.25 from TEST GROCER is under review",
        "EG" to "تم استلام طلبك لاسترداد 64.25 جم من TEST GROCER",
        "EG" to "تم استلام طلب سحب 300.00 جنيه من محفظتك",
        // ── عرض تقسيط شراء قديم (مش صرف جديد) ──
        "SA" to "Convert your purchase of SAR 3,000.00 at TEST STORE on 05/03/2026 into 12 easy installments. Reply YES",
        "SA" to "قسّط مشترياتك بقيمة 3,000.00 ريال من TEST STORE على 12 شهر بتاريخ 2026-03-05",
        "SA" to "حوّل مشترياتك بقيمة 3,000.00 ريال إلى أقساط شهرية بتاريخ 2026-03-05",
        "EG" to "Shop now with your credit card and pay over 12 months with 0% interest on purchases above EGP 5,000",
        // ── عروض وكاش باك (التاريخ = آخر موعد العرض) ──
        "SA" to "احصل على خصم 50 ريال على مشترياتك من TEST GROCER حتى 31/03/2026",
        "SA" to "كاش باك يصل إلى 100 ريال عند الدفع ببطاقتك حتى 31/03/2026",
        "SA" to "Enjoy 20% off on purchases above SAR 200 at TEST GROCER until 2026-03-31",
        "SA" to "استمتع بخصم 50 ريال على مشترياتك\nمن TEST GROCER\nمدى *6604",
        "SA" to "Earn SAR 25 cashback on your next purchase at TEST GROCER. Valid till 2026-03-31",
        "SA" to "خصم 15% على مشتريات تتجاوز 300 ريال لدى TEST GROCER حتى 2026-03-31",
        "EG" to "Get EGP 50 cashback when you pay with your debit card at TEST GROCER",
        "EG" to "رصيدك 4,100.00 جم. اشحن الآن واحصل على 20 جنيه هدية",
        // ── تذكير سداد · كشف · رصيد بس ──
        "SA" to "تذكير: المبلغ المستحق للسداد على بطاقتك *6604 هو 4,100.00 ريال قبل 2026-03-25",
        "SA" to "فاتورتك الجديدة بمبلغ 230.00 ريال جاهزة للسداد حتى 2026-03-25",
        "SA" to "Your credit card payment of SAR 1,500.00 is due on 2026-03-25",
        "SA" to "رصيدك المتاح بعد آخر عملية شراء: 4,100.00 ريال في 2026-03-05",
        "SA" to "الرصيد\nSAR 4,100.00\nآخر عملية: شراء\n2026-03-05",
        "EG" to "Dear customer, your credit card 6604 statement balance is EGP 4,100.00, due 25/03/2026",
        "EG" to "Dear Customer, a payment of EGP 1,500.00 is due on your credit card 6604 by 25/03/2026",
        "EG" to "Your available balance on account ending 1188 is EGP 4,100.00 after salary deposit",
        "EG" to "Your Vodafone Cash balance is 4,100.00 LE. Trx date: 05/03/2026",
        "EG" to "Dear Customer, your salary transfer of EGP 12,000.00 is scheduled for 25/03/2026",
    )

    /**
     * حركة فلوس ممكن تكون حقيقية بس **ملتبسة** — لازم ما تتسجلش لوحدها (أي رفض مقبول: بتستنى المالك)، ولا في البلد التانية.
     * كانت كلها بتتسجل بمبلغ أو اتجاه أو تاريخ غلط.
     */
    val mustNotBook: List<Pair<String, String>> = listOf(
        // اتجاه ملتبس: شراء اتعكس · «تصحيح» (داخل) وفيه «تم خصم» · شيك رجع
        "SA" to "Purchase SAR 64.25 at TEST GROCER 2026-03-05 was reversed due to a technical error",
        "SA" to "تصحيح\nتم خصم مبلغ 64.25 SAR من حسابك 1188\nفي: 2026-03-05",
        "SA" to "شيك مرتجع\nالمبلغ: SAR 5,000.00\nحساب: 1188\nفي: 2026-03-05",
        // الرقم اللي جنب العملة مش المبلغ (رصيد بعده · رقم مرجع · آخر 4 من الكارت) أو رسوم في السطر اللي بعد اسمها
        "SA" to "Deposit\nSAR 4,100.00 is your available balance\n2026-03-05",
        "SA" to "Ref SR2026030512\nPurchase\nAmount 64.25\nAt TEST GROCER\n2026-03-05",
        "SA" to "Purchase\nCard 6604 SAR 64.25\nAt TEST GROCER\n2026-03-05",
        "SA" to "حوالة صادرة\nالمبلغ: 900.00\nرسوم:\nSAR 5.75\nالى: SAMI TESTER\n2026-03-05 09:10",
        "SA" to "Purchase 64.25 at TEST GROCER 2026-03-05 Bal SAR 4,100.00",
        "EG" to "Purchase with debit card 6604 EGP 500.00 at TEST GROCER on 05/03/2026",
        // فواصل غلط (كانت بتطلع مبلغ أكبر 100 مرة أو جزء من الرقم)
        "SA" to "شراء\nمبلغ: SAR 64,25\nلدى: TEST GROCER\n2026-03-05",
        "SA" to "شراء\nمبلغ: SAR 1 234.50\nلدى: TEST GROCER\n2026-03-05",
        "SA" to "شراء\nمبلغ: 1،234.50 ر.س\nلدى: TEST GROCER\n2026-03-05",
        "SA" to "شراء\nمبلغ: SAR 1.234,50\nلدى: TEST GROCER\n2026-03-05",
        "SA" to "شراء\nمبلغ: SAR 12.345\nلدى: TEST GROCER\n2026-03-05",
        "EG" to "تم خصم 64,25 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026",
        "EG" to "تم خصم 1 234.50 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026",
        "EG" to "تم خصم 1.234 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026",
        // تاريخ بسنة في المستقبل (بعد الوصول بأكتر من يوم) — مش عملية خلصت
        "SA" to "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2027-03-05",
        "SA" to "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-31",
        "EG" to "Your debit card 6604 was charged EGP 500.00 at TEST GROCER on 25/03/2026",
        "EG" to "Your debit card 6604 was charged EGP 500.00 at TEST GROCER on Mar 25, 2026",
        // عملة أجنبية (§75-12 — قرار المالك): تستنى المبلغ المحلي حتى لو مكتوب
        "SA" to "International Online Purchase\nAmount: USD 23.40 (SAR 87.50)\nCard: *6604 - VISA (Ecommerce)\nAt: TEST GROCER\nOn: 05/03/2026 09:10",
        "SA" to "شراء دولي\nبطاقة:6604;مدى\nمبلغ:120 ريال قطري\nدولة:QA\nلدى:TEST GROCER\nفي:2026-03-05 09:10",
        "SA" to "شراء دولي\nبطاقة:6604;مدى\nمبلغ:KWD 12.345\nدولة:KW\nلدى:TEST GROCER\nفي:2026-03-05 09:10",
        "EG" to "Your credit card ending with#6604 was charged for SAR 75.00 at TEST GROCER on 05/03/26 at 09:10. Card available limit is EGP 4,100.00.",
    )
}
