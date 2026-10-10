package app.masroufy.device.smscoverage

import app.masroufy.core.SmsKind.CASH_WITHDRAWAL
import app.masroufy.core.SmsKind.REFUND
import app.masroufy.core.SmsKind.RETURNED
import app.masroufy.core.SmsKind.SALARY

/**
 * المتوقع من كل سطر في `research/banks/egypt-sms-formats.json` (0…57). نفس القواعد زي [SaudiSpecs]؛ العملة المحلية الجنيه
 * والمبلغ بالقروش. الطرف التاني في المحافظ = رقم الموبايل (آخر 4) أو الاسم المسجل.
 */
internal object EgyptSpecs {
    private const val NAME = "name"

    val rows: Map<Int, RowSpec> = mapOf(
        // ── الأهلي المصري ──
        0 to out(merchant = true),
        1 to out(merchant = true),
        2 to out(kind = CASH_WITHDRAWAL), // سحب صرّاف — نفس نص الشراء والمحل «NBE ATM…»
        3 to inn(party = listOf(NAME)),
        4 to out(party = listOf("masked_name")),
        5 to inn(party = listOf(NAME)),
        6 to inn(party = listOf(NAME)),
        7 to inn(party = listOf(NAME)),
        8 to inn(),
        9 to out(),
        // ── التجاري الدولي ──
        10 to out(merchant = true),
        // «has been refunded» · «لقد تم رد» ⇒ عملية رجعت (§77-D — بتدوّر على الأصلية، وإلا «استرداد» مقترح)
        11 to inn(merchant = true, kind = RETURNED),
        12 to inn(merchant = true, kind = RETURNED),
        13 to ignore("refund REQUEST (money not moved yet)", values = mapOf("amount" to "-42.25")),
        14 to out(merchant = true),
        15 to anyDir(), // سداد البطاقة الائتمانية = تحويل داخلي؛ الاتجاه حسب المحفظة
        16 to out(),
        17 to inn(party = listOf(NAME)),
        18 to inn(kind = SALARY),
        19 to ignore("existing purchase converted to installments (not a new spend)", values = mapOf("currency" to "جم")),
        20 to ignore("declined", values = mapOf("currency" to "جم", "reason" to "عدم كفاية رصيد البطاقة")),
        21 to ignore("purchase OTP with amount + merchant"),
        22 to ignore("OTP"),
        23 to ignore("info: IPN PIN set"),
        24 to ignore("info: card activated"),
        25 to ignore("info: credit-card statement", values = mapOf("amount" to "350.00", "balance" to "4,200.00")),
        26 to inn(party = listOf(NAME)),
        27 to inn(party = listOf(NAME)),
        // ── بنوك تانية ──
        28 to inn(party = listOf(NAME)),
        29 to inn(party = listOf(NAME)),
        30 to out(),
        // IPN رجع ⇒ الفلوس رجعت. الجولة السادسة: «dated {date}» = يوم **التحويل الأصلي** (البحث) ⇒ العملية بيوم وصول الرسالة، مش {date}
        // (القالب هنا بتاريخ أصلي قبلها بأربع أيام — المتوقع لسه يوم الوصول [Fill.TX_DATE])
        31 to inn(kind = RETURNED, values = mapOf("date" to "10/09")),
        32 to custom(
            CustomBody("ar", "عزيزي العميل لقد قمت بتسجيل الدخول في بنك بيت التمويل الكويتي – مصر في {date}, {time}", Expect(false, what = "info: login")),
            CustomBody("en", "Dear Customer, You have logged in to KFH - Egypt Mobile Banking service at {date}, {time}", Expect(false, what = "info: login")),
            date = DateStyle.DD_MM_YYYY_SLASH,
        ),
        33 to out(merchant = true),
        34 to out(merchant = true, foreign = true),
        35 to inn(),
        36 to inn(),
        // ── فودافون كاش ──
        // رقمين في القالب بنفس الخانة: المرسل ورقم محفظتك — مختلفين
        37 to RowSpec(
            Expect(true, Dir.IN, partyKeys = listOf(NAME, "phone")),
            values = mapOf("ownPhone" to "01000000456"),
            fix = { t -> t.replace("على رقم محفظتك  {phone}", "على رقم محفظتك  {ownPhone}") },
        ),
        38 to inn(party = listOf("counterparty_id")),
        39 to out(party = listOf("phone")),
        40 to ignore("cash-out REQUEST waiting for PIN"),
        // سحب كاش من المحفظة (§75-4: نقل للكاش) — قبل التاريخ علامة LRM مخفية زي الرسالة الحقيقية
        41 to out(kind = CASH_WITHDRAWAL, fix = { it.replace("تاريخ العملية {date}", "تاريخ العملية ‎{date}") }),
        42 to ignore("declined: insufficient balance"),
        43 to ignore("declined: wrong PIN", values = mapOf("rest" to ",رقم العملية 260914000427")),
        44 to ignore("info: balance only"),
        45 to out(),
        46 to out(amountKey = "total", values = mapOf("amount" to "50.00", "total" to "57.00", "rest" to "جنيه.")),
        47 to ignore("info: balance only"),
        48 to ignore("promo cashback to be claimed"),
        49 to inn(party = listOf("phone")),
        // ── محافظ تانية ──
        50 to inn(party = listOf("phone")),
        51 to out(party = listOf("phone")),
        52 to inn(party = listOf("phone")),
        53 to out(party = listOf("phone")),
        54 to inn(party = listOf("phone")),
        55 to out(party = listOf("phone")),
        // ── أشكال عامة ──
        56 to inn(party = listOf(NAME)),
        57 to inn(party = listOf(NAME)),
    )
}
