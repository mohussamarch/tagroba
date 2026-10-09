package app.masroufy.device.smscoverage

import app.masroufy.core.SmsKind.CASH_WITHDRAWAL
import app.masroufy.core.SmsKind.OWN_TRANSFER
import app.masroufy.core.SmsKind.REFUND
import app.masroufy.core.SmsKind.RETURNED
import app.masroufy.core.SmsKind.SALARY

/**
 * المتوقع من كل سطر في `research/banks/saudi-sms-formats.json` (0…121؛ عناوين البنك المركزي 122…182 في [SamaTitles]).
 * المعنى من نوع السطر وملاحظاته في ملف البحث + قرارات OVERRIDES §75: السحب من الصرّاف حركة طالعة (بتتنقل للكاش بعدين — ٤)،
 * الاسترداد داخل (ويتقترح «استرداد» — ٦)، العملة الأجنبية تتسجل وتسأل عن المحلي (١٢).
 */
internal object SaudiSpecs {
    private const val NAME = "name"
    private val NAME_DIGITS = listOf("name", "cpAcct")
    private val FOREIGN_TRIP = mapOf("country" to "GB")

    private val rajhiDate = DateStyle.YY_MM_DD_DASH

    val rows: Map<Int, RowSpec> = mapOf(
        // ── الراجحي ──
        0 to out(merchant = true),
        // البحث: «{currency} is the foreign code» = أي كود — الليرة التركية مش في قايمة العشر عملات القديمة (مراجعة جلسة 33)
        1 to out(merchant = true, foreign = true, values = FOREIGN_TRIP + ("currency" to "TRY")),
        2 to out(merchant = true),
        // «مبلغ» = المخصوم، و«اعادة مبلغ» = الباقي من الحجز اللي رجع — مبلغين مختلفين بنفس الخانة في القالب
        3 to out(merchant = true, fix = { it.replace("اعادة مبلغ:SAR {amount}", "اعادة مبلغ:SAR {released}") }),
        4 to ignore("authorization hold (final debit comes later)"),
        5 to out(merchant = true),
        6 to out(merchant = true),
        7 to out(merchant = true),
        8 to out(),
        9 to inn(merchant = true, kind = REFUND),
        10 to inn(merchant = true, kind = REFUND),
        11 to custom(
            CustomBody("استرجاع", "استرجاع\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nلدى:{merchant}\nفي:{date} {time}", Expect(true, Dir.IN, merchant = true, kind = REFUND)),
            CustomBody("إرجاع", "إرجاع\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nالتاجر:{merchant}\nفي:{date} {time}", Expect(true, Dir.IN, merchant = true, kind = REFUND)),
            CustomBody("مرتجع", "مرتجع\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nلدى:{merchant}\nفي:{date} {time}", Expect(true, Dir.IN, merchant = true, kind = REFUND)),
            // §77-D (الشريحة S3): عكس العملية = عملية رجعت (بتدوّر على الأصلية، وإلا «استرداد» مقترح)
            CustomBody("عكس العملية", "عكس العملية\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nلدى:{merchant}\nفي:{date} {time}", Expect(true, Dir.IN, merchant = true, kind = RETURNED)),
            CustomBody("كاش باك", "كاش باك\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nلدى:{merchant}\nفي:{date} {time}", Expect(true, Dir.IN, merchant = true, kind = REFUND)),
            CustomBody("كاش باك عكس", "كاش باك عكس\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nلدى:{merchant}\nفي:{date} {time}", Expect(true, Dir.OUT, merchant = true)),
            date = rajhiDate,
        ),
        12 to out(kind = CASH_WITHDRAWAL),
        13 to inn(party = listOf(NAME)),
        14 to inn(party = NAME_DIGITS),
        15 to inn(party = NAME_DIGITS),
        16 to inn(party = listOf(NAME)),
        17 to inn(party = NAME_DIGITS),
        18 to out(party = listOf(NAME)),
        19 to out(party = NAME_DIGITS),
        20 to out(party = NAME_DIGITS),
        21 to out(party = NAME_DIGITS),
        22 to out(party = listOf(NAME)),
        // بين حساباتك: «الى: {acct}» = حسابك التاني ⇒ الطرف = آخر 4 منه (§75-11)
        23 to out(party = listOf("acct"), kind = OWN_TRANSFER),
        24 to ignore("declined outgoing transfer (مرفوضة)"),
        25 to inn(),
        26 to inn(),
        27 to inn(),
        28 to inn(kind = SALARY),
        29 to out(),
        30 to out(),
        31 to out(),
        32 to out(),
        33 to ignore("OTP (may carry an amount)"),
        34 to ignore("OTP (may carry an amount)"),
        35 to ignore("OTP shaped exactly like a purchase"),
        36 to ignore("declined: insufficient balance"),
        37 to inn(party = NAME_DIGITS),
        38 to out(merchant = true),
        39 to out(merchant = true),
        // ── الأهلي السعودي ──
        40 to out(merchant = true, values = mapOf("wallet" to "ApplePay")),
        41 to out(merchant = true),
        42 to inn(merchant = true, kind = REFUND),
        43 to inn(kind = REFUND),
        44 to ignore("declined purchase"),
        45 to ignore("OTP with amount"),
        46 to ignore("refund of a previously rejected operation"),
        47 to custom(
            CustomBody("حوالة صادرة", "حوالة صادرة\nبـ{amount} SAR\nالى: {name}\nالرصيد المتاح: SAR {balance}\nفي {time} {date}", Expect(true, Dir.OUT, partyKeys = listOf(NAME))),
            CustomBody("حوالة بين حساباتك", "حوالة بين حساباتك\nبـ{amount} SAR\nالى {name}\nالرصيد: SAR {balance}\nفي {time} {date}", Expect(true, Dir.OUT, partyKeys = listOf(NAME), kind = OWN_TRANSFER)),
            CustomBody("سداد", "سداد\nبـ{amount} SAR\nالى: {name}\nالرصيد المتاح: SAR {balance}\nفي {time} {date}", Expect(true, Dir.OUT)),
        ),
        48 to out(merchant = true),
        // ── ساب ──
        49 to out(merchant = true),
        50 to out(merchant = true),
        51 to out(party = NAME_DIGITS),
        52 to inn(party = NAME_DIGITS),
        53 to inn(kind = SALARY),
        // سطر كلمات بس («الرصيد[ المتاح]: SAR …») ⇒ متجرب جوه شكل شراء ساب (السطر 49): الرصيد ما يتحسبش مبلغ
        54 to custom(
            CustomBody("الرصيد المتاح", "شراء عبر نقاط البيع\nبطاقة: ***{last4};mada({wallet});\nمبلغ: SAR {amount}\nلدى: {merchant}\nالرصيد المتاح: SAR {balance}\nفي: {date} {time}", Expect(true, Dir.OUT, merchant = true)),
            CustomBody("الرصيد", "شراء عبر نقاط البيع\nبطاقة: ***{last4};mada({wallet});\nمبلغ: SAR {amount}\nلدى: {merchant}\nالرصيد: SAR {balance}\nفي: {date} {time}", Expect(true, Dir.OUT, merchant = true)),
            values = mapOf("wallet" to "Apple Pay"),
        ),
        // ── الإنماء ──
        55 to out(merchant = true),
        56 to out(merchant = true),
        57 to out(merchant = true),
        58 to out(merchant = true),
        59 to out(merchant = true),
        60 to out(merchant = true),
        61 to out(merchant = true),
        62 to out(merchant = true),
        63 to inn(merchant = true, kind = RETURNED), // حوالة عكسية (§77-D)
        64 to inn(party = listOf(NAME)),
        65 to inn(party = NAME_DIGITS),
        66 to inn(party = listOf(NAME)),
        // شكل تاريخ الإنماء مفترض: بشَرطة كمان (قص الأرقام الطويلة في فلتر الجهاز كان بيلزق «**3355 2026-09-14 14» — مراجعة جلسة 33)
        67 to inn().copy(alsoDates = listOf(DateStyle.YYYY_MM_DD, DateStyle.DD_MM_YYYY_DASH)),
        68 to out(party = NAME_DIGITS),
        69 to out(party = NAME_DIGITS),
        70 to out(party = listOf("acct2"), kind = OWN_TRANSFER),
        71 to inn(kind = SALARY),
        72 to inn(kind = SALARY),
        73 to inn(),
        74 to ignore("declined: card balance not enough (has amount + merchant)"),
        75 to ignore("purchase-like OTP"),
        76 to ignore("activation OTP"),
        // ── الفرنسي ──
        77 to out(party = NAME_DIGITS),
        78 to inn(party = NAME_DIGITS),
        // ── دي 360: المبلغ بالريال بين قوسين جنب الأجنبي ⇒ اقتراح بس، الرسالة تستنى (قرار المالك §75-12 ✗ على «يتسجل لو المحلي مكتوب») ──
        79 to out(merchant = true, foreign = true, amountKey = "foreignAmount", localKey = "amount", values = FOREIGN_TRIP),
        80 to out(merchant = true),
        81 to out(foreign = true, amountKey = "foreignAmount", localKey = "amount", values = FOREIGN_TRIP, kind = CASH_WITHDRAWAL),
        82 to inn(party = listOf("cpAcct")),
        83 to inn(party = listOf(NAME)),
        84 to out(party = NAME_DIGITS),
        85 to out(party = NAME_DIGITS),
        // ── بنك إس تي سي ──
        86 to out(merchant = true),
        87 to out(merchant = true),
        88 to out(merchant = true),
        89 to out(merchant = true),
        // الجولة السادسة (§75-12 · اختيار (م)): المبلغ بالريال صح (الإجمالي = المبلغ + الضريبة + الرسوم)، بس سعر الصرف ≠ 1 والدولة GB
        // = شراء برّه البلد ⇒ بيتقري صح و**بيستنى** تأكيد المالك (`SmsTemplateCoverageTest.WAIT_BY_DESIGN`) — مش بيتسجل لوحده
        90 to out(merchant = true, amountKey = "total", values = FOREIGN_TRIP),
        91 to out(merchant = true),
        92 to out(merchant = true),
        93 to out(merchant = true),
        94 to out(merchant = true, amountKey = "total", values = FOREIGN_TRIP), // زي #90: بيتقري صح وبيستنى (الدولة GB)
        95 to inn(merchant = true, kind = REFUND),
        96 to inn(merchant = true, kind = RETURNED), // عكس عملية (§77-D)
        97 to inn(merchant = true, kind = RETURNED), // Purchase Reversal (§77-D)
        98 to out(party = listOf("acct2"), kind = OWN_TRANSFER),
        99 to out(party = listOf("acct2"), kind = OWN_TRANSFER),
        100 to inn(party = listOf(NAME)),
        101 to inn(party = listOf(NAME)),
        102 to inn(party = listOf(NAME)),
        103 to inn(party = listOf(NAME)),
        104 to out(party = listOf(NAME)),
        105 to out(party = listOf(NAME)),
        106 to out(party = listOf(NAME)),
        107 to out(party = listOf(NAME), values = mapOf("country" to "EG")),
        108 to inn(),
        109 to out(),
        110 to out(),
        111 to out(),
        112 to out(),
        113 to custom(
            CustomBody("Insufficient balance-Online Purchase", "Insufficient balance-Online Purchase\nCard:{last4};VISA-VISA\nAmount:{amount}SR\nAt:{merchant}\n{date} {time}", Expect(false, what = "declined: insufficient balance")),
            CustomBody("Transaction: POS Purchase", "Insufficient balance\nTransaction: POS Purchase\nAmount:{amount}SR\nAt:{merchant}\n{date} {time}", Expect(false, what = "declined: insufficient balance")),
            CustomBody("Declined transaction due to", "Declined transaction due to insufficient balance\nAmount: {amount} SAR\nAt: {merchant}\n{date} {time}", Expect(false, what = "declined")),
        ),
        114 to ignore("OTP with amount"),
        115 to ignore("OTP"),
        // ── البلاد + بنوك مش معروفة ──
        116 to out(merchant = true),
        117 to out(merchant = true),
        118 to out(merchant = true),
        // مبلغ أجنبي و(مقابله بالريال) + رسوم وضريبة ⇒ تستنى (§75-12)، واقتراح المبلغ المحلي = المخصوم الحقيقي «إجمالي المبلغ المستحق»
        119 to out(merchant = true, foreign = true, amountKey = "foreignAmount", localKey = "total", values = FOREIGN_TRIP + ("total" to "93.25")),
        // تراكم كاش باك في «محفظة الاسترجاع النقدي» — اتحسب داخل (نوع السطر cashback)، وده محل نقاش
        120 to inn(),
        121 to inn(),
    )
}
