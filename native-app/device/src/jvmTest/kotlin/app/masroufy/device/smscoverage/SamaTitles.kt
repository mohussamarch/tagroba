package app.masroufy.device.smscoverage

import app.masroufy.core.SmsKind

/**
 * عناوين العمليات الموحّدة من البنك المركزي السعودي (تعميم 42023876 — السطور 122…182 في ملف السعودية).
 * دي **عناوين بس**: كل عنوان بيتجرب كأول سطر في رسالة عامة (مبلغ + تاريخ)، مرة بالعربي ومرة بالإنجليزي (العنوان الإنجليزي من
 * أول جملة في `notes`). المطلوب من القارئ هنا: يفهم الاتجاه من العنوان، ويرفض الحجز وفكّه.
 */
internal object SamaTitles {
    const val AMOUNT = "245.75"

    fun arabicBody(title: String) = "$title\nمبلغ: SAR $AMOUNT\nفي: ${DateStyle.YYYY_MM_DD.date} ${DateStyle.YYYY_MM_DD.time}"

    fun englishBody(title: String) = "$title\nAmount: SAR $AMOUNT\nOn: ${DateStyle.YYYY_MM_DD.date} ${DateStyle.YYYY_MM_DD.time}"

    /** «Bill Payment» من «Bill Payment» أو «Credit transfer … Withdrawal. Direction …». */
    fun englishTitle(notes: String): String = notes.substringBefore(". ").substringBefore(" | ").trim()

    private val IN = Expect(true, Dir.IN)
    private val OUT = Expect(true, Dir.OUT)
    private val EITHER = Expect(true, Dir.ANY)
    private val CASH_OUT = Expect(true, Dir.OUT, kind = SmsKind.CASH_WITHDRAWAL)
    private val CASH_IN = Expect(true, Dir.IN, kind = SmsKind.CASH_DEPOSIT)
    private val REFUND_IN = Expect(true, Dir.IN, kind = SmsKind.REFUND)
    private val OWN_IN = Expect(true, Dir.IN, kind = SmsKind.OWN_TRANSFER)
    private val OWN_OUT = Expect(true, Dir.OUT, kind = SmsKind.OWN_TRANSFER)

    val rows: Map<Int, Expect> = mapOf(
        122 to OUT, // سداد فاتورة
        123 to OUT, // سداد فاتورة لمرة واحدة
        124 to OUT, // إصدار شيك مصدّق
        125 to Expect(false, what = "credit-card hold released (no money moved)"),
        126 to Expect(false, what = "credit-card amount reserved (hold)"),
        127 to REFUND_IN, // بطاقة ائتمانية استرجاع نقدي
        128 to EITHER, // بطاقة ائتمانية تأكيد سداد — سداد البطاقة: داخل للبطاقة وطالع من الحساب
        129 to EITHER, // بطاقة ائتمانية تسديد
        130 to REFUND_IN, // بطاقة ائتمانية استرداد مبلغ
        131 to IN, // إيداع رسوم (Credit Transaction Fees)
        132 to IN, 133 to OWN_IN, 134 to IN,
        135 to EITHER, // سحب نقدي طارئ — الاتجاه ملتبس في المصدر نفسه
        136 to OWN_IN, 137 to OWN_IN, 138 to IN, 139 to IN, 140 to IN, 141 to IN, 142 to IN, 143 to IN, 144 to IN, 145 to IN,
        146 to OUT, // خصم رسوم
        147 to OUT, 148 to OWN_OUT, 149 to OUT, 150 to OUT,
        151 to OUT, // خصم قسط تمويل
        152 to OUT, 153 to OUT, 154 to OUT, 155 to OWN_OUT, 156 to OWN_OUT,
        157 to OUT, 158 to OUT, // خصم شيك
        159 to CASH_IN, 160 to CASH_IN, 161 to IN, 162 to IN, // إيداع
        163 to OUT, // شراء عملة أجنبية (بيتخصم بالريال)
        164 to CASH_OUT, // سحب صراف آلي دولي
        165 to OUT, // مدفوعات وزارة الداخلية
        166 to OUT, // شراء إنترنت
        167 to OUT, 168 to OUT, 169 to OWN_OUT, 170 to OUT, 171 to OUT, 172 to OUT, 173 to OUT, // امر مستديم
        174 to OUT, 175 to OUT, 176 to OUT, // شراء عبر نقاط البيع
        177 to EITHER, // تسوية نقطة البيع (للتاجر)
        178 to IN, // حوالة واردة
        179 to REFUND_IN, // استرجاع مدفوعات وزارة الداخلية
        180 to Expect(true, Dir.IN, kind = SmsKind.RETURNED), // حوالة عكسية = عملية رجعت (§77-D)
        181 to CASH_OUT, 182 to CASH_OUT, // سحب صراف آلي · سحب فرع
    )
}
