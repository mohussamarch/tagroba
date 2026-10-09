package app.masroufy.core

import app.masroufy.core.SmsRound7Cases.OCT8

/**
 * الجولة السابعة — رسايل المراجعة العدائية التالتة، **مصر** (بنوك ومحافظ). كلها **مخترعة** (مازن التجريبي · ZIAD PROBE · PROBE STORE ·
 * التليفونات 0100000xxxx …). كل واحدة كانت **بتتسجل لوحدها** وهي مش عملية خلصت. المطلوب: ما تتسجلش لوحدها في أي بلد.
 */
internal object SmsRound7Cases2 {
    val mustNotAutoRecord: List<Triple<String, String, String>> = listOf(
        // ── 1. خانة الاسم (NM) فيها موافقة/تحقق/لسه/هيوصل … ──
        Triple("a01", OCT8, "تم استلام مبلغ 900 جنيه من رقم 01000007301 المسجل باسم مازن التجريبي محتاج موافقتك"),
        Triple("a02", OCT8, "تم استلام مبلغ 900 جنيه من رقم 01000007301 المسجل باسم مازن التجريبي هيوصلك خلال ساعة"),
        Triple("a03", OCT8, "تم استلام مبلغ 650 جنيه من رقم 01000007302 المسجل باسم شادي الوهمي لسه ماتأكدش"),
        Triple("a17", OCT8, "تم استلام مبلغ 300 جنيه من رقم 01000007301 المسجل باسم هاني التجريبي اقبله او ارفضه"),
        Triple("b05", OCT8, "تم استلام مبلغ 450 جنيه من رقم 01000007301 المسجل باسم مازن وهيتفعل لما تحدث بياناتك"),
        Triple("a04", OCT8, "You have received an IPN transfer of EGP 2,400.00 to account ending 4417 from ZIAD PROBE AWAITS APPROVAL"),
        Triple("b09", OCT8, "You have received an IPN transfer of EGP 800.00 to account ending 4417 from KARIM PROBE TILL KYC."),
        Triple("a14", OCT8, "Dear customer, IPN transfer of 1,000 EGP credited to account 4417 from KARIM PROBE NEEDS ACCEPTANCE"),
        Triple("a05", OCT8, "تم اضافة تحويل لحظي لحسابكم بمبلغ 1,800 جم من ريم الافتراضية محتاجة موافقة رقم مرجعي 7712093 يوم 10-08"),
        Triple("b06", OCT8, "تم اضافة تحويل لحظي لبطاقتكم مسبقة الدفع رقم 4417 بمبلغ 900 جم من شادي الوهمي غير متاح للصرف رقم مرجعي 66120479 يوم 10-08"),
        Triple("b08", OCT8, "تم تنفيذ تحويل لحظي من حسابكم رقم 4417 بمبلغ 600 جم الي ريم الافتراضية محتاج تفعيل رقم مرجعي 66120480 يوم 10-08"),
        Triple(
            "a10", OCT8,
            "يرجي العلم انه تم تنفيذ تحويل لحظي بمبلغ 700 جنيه الي حسابك المنتهي ب 4417 من نهى التجريبية برجاء القبول برقم مرجعي 66120 بتاريخ 08/10/2026",
        ),
        Triple("a06", OCT8, "Your account ending in 4417 has been credited with EGP 3,200.00 on 08/10/2026 from NOHA PROBE UNCLEARED FUNDS."),
        Triple("b07", OCT8, "Your account ending in 4417 has been credited with EGP 5,000.00 on 08/10/2026 from NOHA PROBE UNDER AML CHECK."),
        Triple("EG20", OCT8, "تم اضافة تحويل لحظي لحسابكم بمبلغ 1,500 جم من ليان unconfirmed رقم مرجعي 778812 يوم 10-07"),
        Triple("EG29", OCT8, "Your account ending in 5172 has been credited with EGP 1,500.00 on 07/10/2026 from LAYAN SHALL CONFIRM"),
        Triple("EG09", OCT8, "تم استلام مبلغ 1,500 جنيه من رقم 01000007303 المسجل باسم ليان سترجع"),
        Triple("EG03", OCT8, "تم اضافة تحويل لحظي لحسابكم بمبلغ 1,500 جم من ليان التجريبية رقم مرجعي 778812REVERSED يوم 10-07"),
        Triple("B15", OCT8, "تم اضافة تحويل لحظي لحسابكم بمبلغ 2,750 جم من المتوقع اضافته خلال ساعتين رقم مرجعي 771204"),
        // ── 2. خانة المحل (M) فيها حجز/تحقق/تقديري/طلب استرداد ──
        Triple("b11", OCT8, "لقد تم رد EGP260.00 على بطاقتكم الائتمانية المنتهية بـ# 4417 من PROBE STORE بمجرد استلام المبلغ من التاجر"),
        Triple("b01", OCT8, "لقد تم رد EGP120.00 على بطاقتكم الائتمانية المنتهية بـ# 4417 من PROBE STORE خلال 14 يوم عمل"),
        Triple("b02", OCT8, "لقد تم رد EGP120.00 على بطاقتكم الائتمانية المنتهية بـ# 4417 من PROBE STORE وهيظهر في الكشف الجاي"),
        Triple("a07", OCT8, "تم خصم 1.00 جم من بطاقة الخصم المباشر رقم 4417 عند PROBE APP CARD VALIDATION يوم 10-08 الساعة 13:20"),
        Triple("a08", OCT8, "تم خصم 2,000 جم من بطاقة الائتمان رقم 4417 عند PROBE HOTEL HOLD يوم 10-07 الساعة 22:10"),
        Triple("b04", OCT8, "تم خصم 3,000 جم من بطاقة الائتمان رقم 4417 عند PROBE HOTEL تأمين يوم 10-07 الساعة 22:10"),
        Triple("a09", OCT8, "Your credit card ending with #4417 was charged for EGP 640.00 at PROBE MART UNCONFIRMED on 08/10/2026"),
        Triple("b03", OCT8, "Your credit card ending with #4417 was charged for EGP 1.00 at PROBE STORE CARD AUTH on 08/10/2026"),
        Triple("EN24", OCT8, "تم خصم 1.00 جم من بطاقة الائتمان رقم 3390 عند SAMPLE APP CARDCHECK يوم 10-08 الساعة 14:05"),
        Triple("EN27", OCT8, "Your credit card ending with #3390 was charged for EGP 760.00 at SAMPLE HOTEL INCREMENTAL AUTH on 08/10/26 at 14:05"),
        Triple("EG19", OCT8, "تم خصم 350.00 جم من بطاقة الائتمان رقم 5172 عند QUOLL HOTEL ESTIMATED يوم 10-07 الساعة 18:22"),
        Triple("EG35", OCT8, "تم خصم 350.00 جم من بطاقة الائتمان رقم 5172 عند محطة الغزال مبلغ تقديري يوم 10-07 الساعة 18:22"),
        // ── 3. محل برّه مصر والمبلغ بالجنيه (§75-12) ──
        Triple("EF10", OCT8, "Your credit card ending with #3390 was charged for EGP 2,450.00 at SAMPLE CLOUD USA on 08/10/26 at 10:05"),
        Triple("EF11", OCT8, "تم خصم 2,450.00 جم من بطاقة الائتمان رقم 3390 عند SAMPLESHOP.COM US يوم 10-08 الساعة 10:05"),
        // ── 4. علامات قلب الاتجاه ──
        Triple("EG07", OCT8, "تم خصم ‮06.84‬ جم من بطاقة الخصم المباشر رقم 5172 عند QUOLL BAKERY يوم 10-07 الساعة 18:22"),
        Triple("EG24", OCT8, "Your debit card 5172 had a successful transaction of EGP 350.00 @QUOLL BAKERY ‮DENILCED‬,your available bal.EGP 1,200.00"),
        // ── 5. تاريخ أقدم من 60 يوم ──
        Triple("EG05", OCT8, "Your credit card ending with #5172 was charged for EGP 350.00 at QUOLL BAKERY on 10/07/2026 at 18:22"),
        Triple("EG06", OCT8, "Your credit card ending with #5172 was charged for EGP 350.00 at QUOLL BAKERY on 02/11/2025"),
        Triple("EG33", OCT8, "Your account ending in 5172 has been credited with EGP 350.00 on Jul 10, 2026 from LAYAN SAMPLE"),
        // ── 6. مش عملية خلصت برّه الأشكال المعروفة (كانت «جاهزة» بمبلغ واتجاه) ──
        Triple("a11", OCT8, "تم استلام مبلغ 500 جنيه من رقم 01000007304 المسجل باسم كريم الوهمي هيتأكد بكره الصبح"),
        Triple("a12", OCT8, "تم استلام مبلغ 500 جنيه من رقم 01000007304 المسجل باسم كريم الوهمي هيتسجل في رصيدك قريب"),
        Triple("a16", OCT8, "تم استلام تحويل لحظي بمبلغ 700 جنيه من نهى التجريبية لحين القبول الي حسابكم المنتهي ب 4417"),
        Triple("b10", OCT8, "تم استلام مبلغ 350 جنيه من رقم 01000007302 المسجل باسم شادي الوهمي واتحجزت لحد التوثيق"),
        Triple("t06", OCT8, "You have received 77 EGP from 01000007305. Transaction ID: 7730194603 09/10/2026 New balance: 177 EGP."),
        // ── 7. كارت مصري اتخصم بالريال: بيستنى في مصر بس (في السعودية بيترفض) ──
        Triple("EF08", OCT8, "تم خصم 300.00 ريال سعودي من بطاقة الائتمان رقم 3390 عند SAMPLE JEDDAH يوم 08/10/2026"),
    )

    /** لازم **تترفض** في قارئ مصر (مش «جاهزة» — «سجّل الكل» كانت هتسجلها). */
    val egyptMustReject: List<String> = listOf("a11", "a12", "a16", "b10", "t06", "EG07", "EG24", "B15")

    /** لازم تترفض في القارئ السعودي (جملة واحدة على قالب بنك مصري ⇒ بتتقري في مصر). */
    val saudiMustReject: List<String> = listOf("EF08")
}
