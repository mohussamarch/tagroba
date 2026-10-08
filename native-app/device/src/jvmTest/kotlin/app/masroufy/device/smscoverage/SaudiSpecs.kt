package app.masroufy.device.smscoverage

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
        1 to out(merchant = true, foreign = true, values = FOREIGN_TRIP + ("currency" to "USD")),
        2 to out(merchant = true),
        // «مبلغ» = المخصوم، و«اعادة مبلغ» = الباقي من الحجز اللي رجع — مبلغين مختلفين بنفس الخانة في القالب
        3 to out(merchant = true, fix = { it.replace("اعادة مبلغ:SAR {amount}", "اعادة مبلغ:SAR {released}") }),
        4 to ignore("authorization hold (final debit comes later)"),
        5 to out(merchant = true),
        6 to out(merchant = true),
        7 to out(merchant = true),
        8 to out(),
        9 to inn(merchant = true),
        10 to inn(merchant = true),
        11 to custom(
            CustomBody("استرجاع", "استرجاع\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nلدى:{merchant}\nفي:{date} {time}", Expect(true, Dir.IN, merchant = true)),
            CustomBody("إرجاع", "إرجاع\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nالتاجر:{merchant}\nفي:{date} {time}", Expect(true, Dir.IN, merchant = true)),
            CustomBody("مرتجع", "مرتجع\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nلدى:{merchant}\nفي:{date} {time}", Expect(true, Dir.IN, merchant = true)),
            CustomBody("عكس العملية", "عكس العملية\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nلدى:{merchant}\nفي:{date} {time}", Expect(true, Dir.IN, merchant = true)),
            CustomBody("كاش باك", "كاش باك\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nلدى:{merchant}\nفي:{date} {time}", Expect(true, Dir.IN, merchant = true)),
            CustomBody("كاش باك عكس", "كاش باك عكس\nبطاقة:{last4};مدى\nمبلغ:SAR {amount}\nلدى:{merchant}\nفي:{date} {time}", Expect(true, Dir.OUT, merchant = true)),
            date = rajhiDate,
        ),
        12 to out(),
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
        23 to out(party = listOf("acct")),
        24 to ignore("declined outgoing transfer (مرفوضة)"),
        25 to inn(),
        26 to inn(),
        27 to inn(),
        28 to inn(),
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
        42 to inn(merchant = true),
        43 to inn(),
        44 to ignore("declined purchase"),
        45 to ignore("OTP with amount"),
        46 to ignore("refund of a previously rejected operation"),
        47 to custom(
            CustomBody("حوالة صادرة", "حوالة صادرة\nبـ{amount} SAR\nالى: {name}\nالرصيد المتاح: SAR {balance}\nفي {time} {date}", Expect(true, Dir.OUT, partyKeys = listOf(NAME))),
            CustomBody("حوالة بين حساباتك", "حوالة بين حساباتك\nبـ{amount} SAR\nالى {name}\nالرصيد: SAR {balance}\nفي {time} {date}", Expect(true, Dir.OUT, partyKeys = listOf(NAME))),
            CustomBody("سداد", "سداد\nبـ{amount} SAR\nالى: {name}\nالرصيد المتاح: SAR {balance}\nفي {time} {date}", Expect(true, Dir.OUT)),
        ),
        48 to out(merchant = true),
        // ── ساب ──
        49 to out(merchant = true),
        50 to out(merchant = true),
        51 to out(party = NAME_DIGITS),
        52 to inn(party = NAME_DIGITS),
        53 to inn(),
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
        63 to inn(merchant = true),
        64 to inn(party = listOf(NAME)),
        65 to inn(party = NAME_DIGITS),
        66 to inn(party = listOf(NAME)),
        67 to inn(),
        68 to out(party = NAME_DIGITS),
        69 to out(party = NAME_DIGITS),
        70 to out(party = listOf("acct2")),
        71 to inn(),
        72 to inn(),
        73 to inn(),
        74 to ignore("declined: card balance not enough (has amount + merchant)"),
        75 to ignore("purchase-like OTP"),
        76 to ignore("activation OTP"),
        // ── الفرنسي ──
        77 to out(party = NAME_DIGITS),
        78 to inn(party = NAME_DIGITS),
        // ── دي 360: المبلغ بالريال بين قوسين جنب الأجنبي ⇒ ده المبلغ ──
        79 to out(merchant = true, values = FOREIGN_TRIP),
        80 to out(merchant = true),
        81 to out(values = FOREIGN_TRIP),
        82 to inn(party = listOf("cpAcct")),
        83 to inn(party = listOf(NAME)),
        84 to out(party = NAME_DIGITS),
        85 to out(party = NAME_DIGITS),
        // ── بنك إس تي سي ──
        86 to out(merchant = true),
        87 to out(merchant = true),
        88 to out(merchant = true),
        89 to out(merchant = true),
        90 to out(merchant = true, amountKey = "total", values = FOREIGN_TRIP),
        91 to out(merchant = true),
        92 to out(merchant = true),
        93 to out(merchant = true),
        94 to out(merchant = true, amountKey = "total", values = FOREIGN_TRIP),
        95 to inn(merchant = true),
        96 to inn(merchant = true),
        97 to inn(merchant = true),
        98 to out(party = listOf("acct2")),
        99 to out(party = listOf("acct2")),
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
        // مبلغ أجنبي و(مقابله بالريال) + رسوم وضريبة ⇒ المخصوم الحقيقي «إجمالي المبلغ المستحق»
        119 to out(merchant = true, amountKey = "total", values = FOREIGN_TRIP + ("total" to "93.25")),
        // تراكم كاش باك في «محفظة الاسترجاع النقدي» — اتحسب داخل (نوع السطر cashback)، وده محل نقاش
        120 to inn(),
        121 to inn(),
    )
}
