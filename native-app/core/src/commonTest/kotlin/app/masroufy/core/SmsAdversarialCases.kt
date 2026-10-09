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
        // ══ الجولة التالتة ══
        // ── رمز بـ«ال» أو «ك» أو تطويل أو صيغة تانية (كانت كلها بتتسجل شراء) ──
        "SA" to "الرمز المؤقت 482913\nشراء عبر الإنترنت\nمبلغ: 64.25 ر.س\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
        "SA" to "رمزك هو 482913 لإتمام شراء بمبلغ 64.25 ر.س لدى TEST STORE بتاريخ 2026-03-05. لا تشاركه مع أحد",
        "SA" to "رمز الشراء 482913\nمبلغ: SAR 64.25\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
        "SA" to "Your code for the purchase of SAR 64.25 at TEST STORE on 2026-03-05 is 482913. Do not share it.",
        "SA" to "Your 3D Secure password for purchase SAR 64.25 at TEST STORE is 482913 2026-03-05",
        "SA" to "Token 482913 for purchase of SAR 64.25 at TEST STORE on 2026-03-05",
        "SA" to "رمـز التحقق 482913\nشراء\nمبلغ: SAR 64.25\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
        "SA" to "رقم التحقق 482913 لعملية شراء بمبلغ 64.25 ريال لدى TEST STORE بتاريخ 2026-03-05",
        "SA" to "Verify your purchase of SAR 64.25 at TEST STORE on 2026-03-05 by entering 482913",
        "EG" to "استخدم الكود 482913 لإتمام عملية purchase بمبلغ 500 جم عند TEST STORE يوم 05/03/2026",
        "EG" to "Use 482913 to authenticate your purchase of EGP 500.00 at TEST STORE on 05/03/2026",
        // ── حجز (الخصم الحقيقي بييجي بعدين) ──
        "SA" to "شراء\nمبلغ محجوز: 500.00 ر.س\nلدى: TEST HOTEL\nفي: 2026-03-05 09:10",
        "SA" to "Purchase (Pre-auth)\nCard: *6604\nAmount: SAR 500.00\nAt: TEST HOTEL\n2026-03-05 09:10",
        "SA" to "Temporary hold: SAR 500.00\nPurchase at TEST HOTEL\n2026-03-05 09:10",
        "SA" to "تم تجميد مبلغ 500.00 ر.س من بطاقتك *6604 لعملية شراء لدى TEST HOTEL بتاريخ 2026-03-05",
        "EG" to "Purchase amount EGP 1,500.00 blocked on your debit card 6604 at TEST HOTEL on 05/03/2026",
        "EG" to "تم خصم مبلغ 1,500 جم بشكل مؤقت من بطاقتك 6604 لحين إتمام الحجز لدى TEST HOTEL يوم 05/03/2026",
        // ── ما اكتملتش · محاولة · رقم سري غلط · الكارت موقوف ──
        "SA" to "لم تكتمل عملية الشراء\nمبلغ: SAR 64.25\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
        "SA" to "Purchase not completed\nAmount: SAR 64.25\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "Purchase attempt\nAmount: SAR 64.25\nAt: TEST STORE\nReason: Not enough balance\n2026-03-05 09:10",
        "SA" to "محاولة شراء\nمبلغ: SAR 64.25\nلدى: TEST STORE\nالسبب: تجاوز الحد اليومي\nفي: 2026-03-05 09:10",
        "SA" to "Incorrect PIN entered\nPurchase SAR 64.25\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "Purchase blocked\nCard *6604 is stopped\nAmount: SAR 64.25\nAt: TEST STORE\n2026-03-05 09:10",
        "EG" to "Your purchase of EGP 500.00 at TEST STORE on 05/03/2026 was not completed",
        "EG" to "Unable to process your purchase of EGP 500.00 at TEST STORE on 05/03/2026",
        // ── خصم جاي أو منفي («يتم» جواها «تم») · لسه بيتنفذ ──
        "SA" to "سوف يتم خصم مبلغ 230.00 ريال من حسابك 1188 لسداد فاتورة TEST POWER بتاريخ 2026-03-05",
        "SA" to "سيخصم مبلغ 230.00 ر.س من حسابك 1188 لسداد فاتورة TEST POWER في 2026-03-06",
        "SA" to "Bill Payment Reminder\nAmount: SAR 230.00\nBiller: TEST POWER\nDate: 2026-03-05",
        "SA" to "جاري تنفيذ حوالة صادرة\nالمبلغ: 900.00 ر.س\nإلى: SAMI TESTER\nفي: 2026-03-05 09:10",
        "EG" to "هيتم خصم 500 جم من حسابك 1188 يوم 05/03/2026 لسداد القسط",
        "EG" to "سوف يتم خصم 500.00 جم من بطاقتك 6604 لدى TEST STORE يوم 05/03/2026",
        "EG" to "لن يتم خصم 500 جم من بطاقتك 6604 لعدم اكتمال العملية يوم 05/03/2026",
        // ── رصيد بس (الرقم كان بيتسجل صرف 4,100) ──
        "SA" to "الرصيد المتاح بعد عملية الشراء\n4,100.00 ر.س\nحساب: *1188\n2026-03-05 09:10",
        "SA" to "Available Balance after Purchase\nSAR 4,100.00\nAccount *1188\n2026-03-05 09:10",
        // ── عروض من غير «%» ولا «احصل» ──
        "SA" to "خصم 50 ريال على مشترياتك من TEST STORE ابتداءً من 2026-03-01 عند الدفع ببطاقة مدى",
        "SA" to "اربح 500 ريال عند الشراء ببطاقتك الائتمانية خلال الفترة من 2026-03-01",
        "SA" to "Get SAR 50 back on your purchase at TEST STORE. Campaign started 2026-03-01",
        "SA" to "Spend SAR 1,000 on purchases from 2026-03-01 and win double points",
        "EG" to "Win EGP 1,000 with every purchase using your credit card from 01/03/2026",
    )

    /**
     * **عمليات حقيقية لازم تتسجل** (الجولة التالتة): الحراس ما يمسكوش سطر تحذير أو إعلان في آخر رسالة حقيقية («للاعتراض … اتصل» ·
     * «تذكير: لا تشارك» · «If you have not authorized …» · «الغاء الخدمة» · «استمتع بخدماتنا» · «To dispute …» · «For info call») ولا
     * كلمة شبه الحارس («معرض» · «حساب جاري» · «Approval code» · «Spend alert»). (البلد · الرسالة · المبلغ المتوقع بالوحدة الصغرى).
     */
    val mustBook: List<Triple<String, String, Long>> = listOf(
        Triple("SA", "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10\nللاعتراض على العملية اتصل 8001110000", 6425),
        Triple("SA", "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10\nتذكير: لا تشارك بيانات بطاقتك مع أحد", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nIf you have not authorized this transaction call 8001110000", 6425),
        Triple("SA", "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10\nلإيقاف البطاقة أو الغاء الخدمة اتصل 8001110000", 6425),
        Triple("SA", "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10\nاستمتع بخدماتنا", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nTo dispute this transaction call 8001110000", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nIf you did not attempt this transaction call 8001110000", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nNever share your card details or password with anyone", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\nApproval code: 482913\n2026-03-05 09:10", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nAvailable balance: SAR 4,100.00", 6425),
        Triple("SA", "شراء\nمبلغ: SAR 64.25\nلدى: معرض TEST للسيارات\nفي: 2026-03-05 09:10", 6425),
        Triple("SA", "عملية شراء-حساب جاري - المتجر الألكتروني\nمن:1188\nمبلغ:SR 64.25\n2026-03-05", 6425),
        Triple("SA", "Spend alert\nPurchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10", 6425),
        Triple("SA", "استرداد مبلغ عملية ملغاة\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10", 6425),
        Triple("SA", "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10\nإذا لم تتم العملية بواسطتك اتصل 8001110000", 6425),
        Triple("SA", "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10\nفي حال لم تقم بالعملية سوف يتم التواصل معك", 6425),
        Triple("SA", "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10\nيمكنك تجميد بطاقتك من التطبيق", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nLost card? Call 8001110000 to get it blocked", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nShop safely with 3D Secure", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nIf you are unable to recognise it, never share the code with anyone", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nNot attempted purchase? Call 8001110000", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05 09:10\nNo authorization from you? Call 8001110000", 6425),
        Triple("SA", "Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\nPoints earned: 64 (SAR 6.40)\n2026-03-05 09:10", 6425),
        Triple("SA", "Purchase\nCard 6604 (SAR 64.25)\nAt TEST GROCER\n2026-03-05 09:10", 6425),
        Triple("EG", "تم خصم 500.00 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026. If you have not authorized this transaction call 19000", 50000),
        Triple("EG", "Your debit card 6604 was charged EGP 500.00 at TEST GROCER on 05/03/2026. Not authorised by you? Call 19000", 50000),
        Triple("EG", "تم خصم 500.00 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026. للمزيد اتصل ب 19623", 50000),
        Triple("EG", "Your debit card 6604 was charged EGP 500.00 at TEST GROCER on 05/03/2026. For info call 19700", 50000),
        Triple("EG", "تم خصم 500.00 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026. تذكير: لا تشارك بيانات بطاقتك مع أحد", 50000),
        Triple("EG", "تم خصم 500.00 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026. لإلغاء الخدمة اتصل 19000", 50000),
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
        // ══ الجولة التالتة ══
        // عملة أجنبية برمز أو اسم أو كود من غير كسور، والمقابل المحلي مكتوب (§75-12 — كانت بتتسجل بالمحلي)
        "SA" to "Online Purchase\nAmount: \$23.40 (SAR 87.75)\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "Purchase\nJPY 4500 (SAR 112.50)\nAt TEST STORE\n2026-03-05 09:10",
        "SA" to "International Purchase\nCard *6604\nINR 2500 = SAR 112.50\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "شراء دولي\nبطاقة: *6604\nمبلغ: 4500 ين ياباني\nما يعادل: 112.50 ر.س\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
        "SA" to "شراء دولي\nبطاقة: *6604\nمبلغ: 300 يوان\n(156.20 ريال)\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
        "SA" to "Purchase\n€19.99 (SAR 81.40)\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "Purchase\nAmount: 12.50 Swiss Francs (SAR 52.10)\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "شراء عبر الإنترنت\nمبلغ: 20 فرنك سويسري\nالمبلغ بالريال: 84.10 ر.س\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
        "SA" to "VISA Purchase\nAmount: \$25.00 (SAR 93.75)\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "International Purchase\nAmount: 25.00 Euro (SAR 101.00)\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "Purchase\nAmount: 25.00 US Dollars (SAR 93.75)\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "International Purchase TRY 450 at TEST STORE (SAR 93.75) 2026-03-05",
        "SA" to "شراء دولي\nمبلغ: 300 يوان (155.00 ريال)\nلدى: TEST STORE\nفي: 2026-03-05 09:10",
        "SA" to "شراء دولي\nبطاقة: *6604\nمبلغ: 500.00 جم\n(38.50 ر.س)\nلدى: TEST STORE\n2026-03-05 09:10",
        "SA" to "International Online Purchase\nAmount: EGP 500.00 (SAR 37.50)\nCard: *6604 - VISA (Ecommerce)\nAt: TEST STORE\nOn: 05/03/2026 09:10",
        "SA" to "Purchase of EGP 500.00 (SAR 37.50) at TEST STORE on 05/03/2026 with card 6604",
        "EG" to "Your credit card 6604 was charged \$15.00 (EGP 720.00) at TEST STORE on 05/03/2026",
        "EG" to "Your credit card 6604 was charged £12.00 at TEST STORE on 05/03/2026. Equivalent EGP 750.00",
        "EG" to "A Trx using card 6604 for 15.00 US Dollars (EGP 720.00) at TEST STORE on 05/03/2026",
        "EG" to "تم خصم 4,500 ين من بطاقتك 6604 عند TEST STORE يوم 05/03/2026 بما يعادل 1,450 جم",
        "EG" to "تم خصم \$25.00 (EGP 1,250.00) من بطاقتك المنتهية بـ 6604 عند TEST STORE يوم 05/03/2026",
        "EG" to "Your credit card ending with#6604 was charged for SAR 75.00 (EGP 980.00) at TEST STORE on 05/03/26 at 09:10",
        "EG" to "Purchase\nAmount: SAR 75.00 (EGP 980.00)\nAt: TEST STORE\n2026-03-05 09:10",
        // عكس أو استقطاع لحاجة ليها اتجاه (كانت بتتسجل عكس اتجاهها — وعكس سحب الصرّاف كان هيبقى كاش وهمي §75-4)
        "SA" to "Refund Reversal\nAmount: SAR 64.25\nMerchant: TEST STORE\n2026-03-05 09:10",
        "SA" to "عكس استرداد\nمبلغ: 64.25 ر.س\nلدى: TEST STORE\nفي: 2026-03-05",
        "SA" to "ATM Withdrawal Reversal\nAmount: SAR 500.00\nATM: TEST ATM 01\n2026-03-05 09:10",
        "SA" to "عكس سحب صراف آلي\nمبلغ: 500.00 ر.س\nفي: 2026-03-05 09:10",
        "SA" to "Cashback Reversal\nAmount: SAR 15.00\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "Debit Reversal\nAmount: SAR 64.25\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "عكس حوالة واردة\nالمبلغ: 900.00 ر.س\nمن: SAMI TESTER\nفي: 2026-03-05 09:10",
        "SA" to "Salary Deduction\nAmount: SAR 500.00\nAccount: *1188\n2026-03-05",
        "SA" to "Salary Reversal\nAmount: SAR 12,000.00\nAccount: *1188\n2026-03-05",
        // مصر: شيك رجع · من حسابك لحسابك · رصيد بعد آخر عملية
        "EG" to "Cheque no. 4417 for EGP 5,000.00 deposited on 05/03/2026 was returned unpaid",
        "EG" to "تم تحويل مبلغ 500 جم من حسابك رقم 1188 إلى حسابك رقم 2277 يوم 05/03/2026",
        "EG" to "Account 1188: EGP 4,100.00 after last purchase on 05/03/2026",
        // تاريخ هجري من غير ميلادي
        "SA" to "Purchase\nAmount: SAR 64.25\nAt: TEST STORE\nOn: 1447-09-16",
        "EG" to "تم خصم 500 جم من بطاقتك 6604 عند TEST STORE يوم 1447/09/16",
        // رسوم أو ضريبة أو مرجع قصير لازق — والمبلغ الحقيقي من غير عملة
        "SA" to "Outgoing Transfer\nAmount: 900.00\nSAR 5.75 fee\nTo: SAMI TESTER\n2026-03-05 09:10",
        "SA" to "Online Purchase\nAmount: 64.25\nTax: SAR 8.38\nAt: TEST STORE\n2026-03-05 09:10",
        "SA" to "Purchase\nAmount 64.25\nRef SR4821\nAt TEST STORE\n2026-03-05 09:10",
        "SA" to "Ref SR1234\nPurchase\nAmount 64.25\nAt TEST GROCER\n2026-03-05",
        // فواصل غريبة · كسور أكتر من منزلتين قبل العملة
        "SA" to "شراء\nمبلغ: 1'234.50 ر.س\nلدى: TEST STORE\n2026-03-05",
        "SA" to "شراء\nمبلغ: 12.345 SAR\nلدى: TEST GROCER\n2026-03-05",
        "SA" to "شراء\nمبلغ: 1,234.567 SAR\nلدى: TEST GROCER\n2026-03-05",
        "SA" to "شراء\nمبلغ: 64.255 ريال\nلدى: TEST GROCER\n2026-03-05",
        "SA" to "شراء\nمبلغ: 1.234.50 SAR\nلدى: TEST GROCER\n2026-03-05",
        // اتلغت ومعاها استرداد بس العنوان صرف
        "SA" to "Purchase Cancelled\nAmount: SAR 64.25\nAt: TEST GROCER\nRefund\n2026-03-05 09:10",
    )
}
