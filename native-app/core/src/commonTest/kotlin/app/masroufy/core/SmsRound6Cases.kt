package app.masroufy.core

import app.masroufy.core.SmsRound5Cases.MARCH
import app.masroufy.core.SmsRound5Cases.OCTOBER

/**
 * الجولة السادسة — رسايل المراجعة العدائية التانية لفرع الجولة الخامسة (`sms-formats-r4-f1` @ 4faaf07)، **السعودية**. كلها **مخترعة**
 * (FAHAD SAMPLE · NAJM BOOKS · ZEPHYR KIOSK · الكارت 8063 …). كل واحدة كانت **بتتسجل لوحدها** وهي مش عملية خلصت، أو بمبلغ غلط.
 * المطلوب: ما تتسجلش لوحدها في أي بلد (ترفض أو تستنى تأكيد المالك — `SmsShape.clear` = false).
 */
internal object SmsRound6Cases {
    val mustNotAutoRecord: List<Triple<String, String, String>> = listOf(
        // ── 1. سطر تحذير في الآخر بكلام حالة (الفوتر بقى قايمة جمل معروفة مقفولة) ──
        Triple("SA24", MARCH, "حوالة صادرة محلية\nمبلغ: 2,300.00 ر.س\nالى: FAHAD SAMPLE\nمن: **2951\nفي: 2026-03-05 10:20\nللاستفسار: الحوالة معادة من بنك المستفيد"),
        Triple("SA25", MARCH, "Outgoing Local Transfer\nAmount: SAR 2,300.00\nTo: FAHAD SAMPLE\nOn: 2026-03-05 10:20\nfor details: transfer bounced back by beneficiary bank"),
        Triple("SA28", MARCH, "امر مستديم حوالة صادرة محلية\nمبلغ: 2,000.00 ر.س\nالى: FAHAD SAMPLE\nفي: 2026-03-05\nللمزيد: أول تنفيذ للأمر يوم 27 من كل شهر"),
        Triple("SA29", MARCH, "خصم قسط تمويل\nالقسط: 1,850.00 ر.س\nالحساب: **2951\nفي: 2026-03-05\nفي حال عدم توفر الرصيد الكافي اليوم لن يخصم القسط"),
        Triple("SA30", MARCH, "خصم شيك ورقي\nمبلغ: 5,000.00 ر.س\nحساب: **2951\nفي: 2026-03-05\nللاستفسار: الشيك بدون رصيد كاف"),
        Triple("SA31", MARCH, "حوالة واردة دولية\nمبلغ: 3,740.00 ر.س\nمن: OMAR SAMPLE\nفي: 2026-03-05\nللمزيد: يرجى تزويدنا بمصدر الأموال لإضافة المبلغ لحسابك"),
        Triple("SA32", MARCH, "Credit Card Refund\nCard: *5528\nAmount: SAR 189.00\nAt: NAJM BOOKS\nOn: 2026-03-05\nfor details: refund will reflect on your card within 14 days"),
        Triple("SA37", MARCH, "شراء عبر نقاط البيع\nمبلغ: 900.00 ر.س\nلدى: QASR HOTEL\nفي: 2026-03-05 11:00\nللمزيد: المبلغ مجمد لحين المغادرة"),
        Triple("SA39", MARCH, "Credit Card Cashback\nCard: *5528\nAmount: SAR 50.00\nOn: 2026-03-05\nfor details: cashback is credited once your monthly purchases reach 2,000"),
        Triple("SA60", MARCH, "شراء عبر نقاط البيع\nمبلغ: 87.00 ر.س\nلدى: WADI CAFE\nفي: 2026-03-05 08:10\nللاستفسار عن سبب رد العملية تواصل معنا عبر التطبيق"),
        Triple("SA61", MARCH, "Outgoing Local Transfer\nAmount: SAR 3,000.00\nTo: FAHAD SAMPLE\nOn: 2026-03-05 10:20\nIf you did not initiate this transfer or wish to stop it before execution, contact us through the app"),
        Triple("S38b", OCTOBER, "Debit Transfer Local\nAmount: SAR 1,875.00\nTo: SAMIR DEMO\n2026-10-08 09:15\nFor details: transfer suspended by compliance team"),
        Triple("S39b", OCTOBER, "شراء عبر نقاط البيع\nمبلغ: 73.15 ر.س\nلدى: كشك زفير\nفي: 2026-10-08 09:15\nللاستفسار: العملية موقوفة لدى البنك"),
        Triple("S40", OCTOBER, "Received transfer\nAmount: SAR 1,875.00\nFrom: SAMIR DEMO\n2026-10-08 09:15\nFor more details: amount withheld for verification"),
        // ── 2. «إجمالي المبلغ المستحق» مش مبلغ + رسوم + ضريبة ⇒ مش مبلغ العملية ──
        Triple("SA16", MARCH, "بطاقة ائتمانية تسديد\nبطاقة: **5528\nمبلغ: 1,000.00 ر.س\nإجمالي المبلغ المستحق: 3,215.40 ر.س\nفي: 2026-03-05 10:15"),
        Triple("SA17", MARCH, "Credit Card Payment\nCard: *5528\nAmount: SAR 750.00\nTotal due amount: SAR 2,480.10\nOn: 2026-03-05 10:15"),
        Triple("SA18", MARCH, "Online Purchase\nCard: *8063\nAmount: SAR 250.00\nAt: NAJM ELECTRONICS\nTotal due amount: SAR 1,000.00\nOn: 2026-03-05 11:05"),
        Triple("S01", OCTOBER, "Online Purchase\nAmount: SAR 73.15\nAt: ZEPHYR KIOSK\nTotal due amount: SAR 4,310.00\n2026-10-08 09:15"),
        Triple("S02", OCTOBER, "شراء إنترنت\nمبلغ: 73.15 ر.س\nلدى: كشك زفير\nإجمالي المبلغ المستحق: 2,915.40 ر.س\nفي: 2026-10-08 09:15"),
        // ── 3. «1.250 SAR» (قراية ملف المرجع 250.00 — سؤال (ز)) ما بتتسجلش لوحدها ──
        Triple("SA14", MARCH, "شراء عبر نقاط البيع\nبطاقة: 8063*;مدى\nمبلغ: 1.250 SAR\nلدى: WADI FURNITURE\nفي: 2026-03-05 11:42"),
        Triple("SA15", MARCH, "PoS Purchase\nCard: *8063\nAmount: 2.400 SAR\nAt: WADI FURNITURE\nOn: 2026-03-05 11:42"),
        Triple("S03", OCTOBER, "شراء عبر نقاط البيع\nمبلغ: 1.875 ر.س\nلدى: كشك زفير\nفي: 2026-10-08 09:15"),
        Triple("S04", OCTOBER, "PoS Purchase\nAmount: 4.120 SAR\nAt: PIXEL GARAGE\n2026-10-08 09:15"),
        // ── 4. خانة حرة (المحل/الطرف) أو ذيل الكارت/الحساب فيها حالة ──
        Triple("SA20", MARCH, "شراء عبر نقاط البيع\nمبلغ: 250.00 ر.س\nلدى: WADI FUEL (مبلغ مؤقت)\nفي: 2026-03-05 07:40"),
        Triple("SA21", MARCH, "PoS Purchase\nAmount: SAR 400.00\nAt: QASR HOTEL - AUTH ONLY\nOn: 2026-03-05 11:00"),
        Triple("SA22", MARCH, "شراء عبر نقاط البيع\nمبلغ: 1,200.00 ر.س\nلدى: QASR HOTEL\nبطاقة: 8063* - حجز ضمان\nفي: 2026-03-05 11:00"),
        Triple("SA33", MARCH, "ATM Withdrawal\nAmount: SAR 500.00\nCard: *8063\nAt: OLAYA ATM 12 - CASH RETAINED\nOn: 2026-03-05 11:02"),
        Triple("SA38", MARCH, "Online Purchase\nAmount: SAR 1.00\nAt: NAJM APPS - CARD VERIFICATION\nOn: 2026-03-05 11:05"),
        Triple("S31", OCTOBER, "حوالة واردة\nالمبلغ: 1,875.00 ر.س\nمن: سامر التجريبي - تم استرجاعها\nفي: 2026-10-08 09:15"),
        Triple("S32", OCTOBER, "حوالة صادرة محلية\nالمبلغ: 1,875.00 ر.س\nالى: سامر التجريبي - تم الغاؤها\nفي: 2026-10-08 09:15"),
        Triple("S33", OCTOBER, "حوالة صادرة محلية\nالمبلغ: 1,875.00 ر.س\nالى: سامر التجريبي (تم تعليقها)\nفي: 2026-10-08 09:15"),
        Triple("S34", OCTOBER, "شراء عبر نقاط البيع\nمبلغ: 73.15 ر.س\nلدى: كشك زفير - أُلغيت\nفي: 2026-10-08 09:15"),
        Triple("S35", OCTOBER, "Received transfer\nAmount: SAR 1,875.00\nFrom: SAMIR DEMO (recalled by sender)\n2026-10-08 09:15"),
        Triple("S36", OCTOBER, "Debit Transfer Local\nAmount: SAR 1,875.00\nTo: SAMIR DEMO - reverted\n2026-10-08 09:15"),
        Triple("S37", OCTOBER, "PoS Purchase\nAmount: SAR 73.15\nAt: ZEPHYR KIOSK - txn timed out\n2026-10-08 09:15"),
        Triple("S49", OCTOBER, "Incoming Transfer: Riyad Bank - recalled\nAmount: SAR 1,875.00\nFrom: *7719\nat: 2026-10-08 09:15"),
        Triple("S50", OCTOBER, "Bill Payment\nAmount: SAR 412.60\nBiller: LUMA TELECOM - due tomorrow\n2026-10-08 09:15"),
        Triple("S51", OCTOBER, "سداد فاتورة\nالمبلغ: 412.60 ر.س\nالمفوتر: لوما للاتصالات - تستحق غدا\n2026-10-08 09:15"),
        Triple("S52", OCTOBER, "Received transfer\nAmount: SAR 1,875.00\nFrom: SAMIR DEMO (to be credited tomorrow)\n2026-10-08 09:15"),
        Triple("S53", OCTOBER, "حوالة واردة\nالمبلغ: 1,875.00 ر.س\nمن: سامر التجريبي - تودع غدا\nفي: 2026-10-08 09:15"),
        Triple("S74", OCTOBER, "حوالة داخلية\nمن: 5519\nالى: رنا المثال - مسترجعة\nبـSR 1,875\n26/10/08 09:35"),
        Triple("S77", OCTOBER, "PoS Purchase\nAmount: SAR 73.15\nAt: ZEPHYR KIOSK\nTransaction: not yet settled\n2026-10-08 09:15"),
        Triple("S78", OCTOBER, "شراء عبر نقاط البيع\nمبلغ: 73.15 ر.س\nلدى: كشك زفير - عملية غير مسواة\nفي: 2026-10-08 09:15"),
        Triple("S83", OCTOBER, "ATM Withdrawal\nAmount: SAR 500.00\nAt: ORBIT ATM 07 - cash retained\n2026-10-08 09:15"),
        Triple("S84", OCTOBER, "سحب صراف آلي\nمبلغ: 500.00 ر.س\nالصراف: صراف أوربت - النقد محتجز\nفي: 2026-10-08 09:15"),
        Triple("S86", OCTOBER, "Deposit ATM\nAmount: SAR 2,000.00\nAt: ORBIT ATM 07 - counting\n2026-10-08 09:15"),
        // ── 5. سطر بنك · ذيل حساب · ذيل كارت · تحية — كلام حر من غير فحص ──
        Triple("S41", OCTOBER, "Credit transfer Local\nAmount: SAR 1,875.00\nFrom: SAMIR DEMO\nBank transfer recalled\n2026-10-08 09:15"),
        Triple("S42", OCTOBER, "حوالة واردة محلية\nالمبلغ: 1,875.00 ر.س\nمن: سامر التجريبي\nبنك المرسل استرد الحوالة\nفي: 2026-10-08 09:15"),
        Triple("S43", OCTOBER, "حوالة صادرة محلية\nالمبلغ: 1,875.00 ر.س\nالى: سامر التجريبي\nمصرف المستفيد اوقف الحوالة\nفي: 2026-10-08 09:15"),
        Triple("S44", OCTOBER, "حوالة صادرة محلية\nالمبلغ: 1,875.00 ر.س\nمن حساب: 5519 - مجمد\nالى: سامر التجريبي\nفي: 2026-10-08 09:15"),
        Triple("S45", OCTOBER, "Debit Transfer Local\nAmount: SAR 1,875.00\nFrom account: 5519 - suspended\nTo: SAMIR DEMO\n2026-10-08 09:15"),
        Triple("S46", OCTOBER, "PoS Purchase\nAmount: SAR 73.15\nCard: *4417 - blocked\nAt: ZEPHYR KIOSK\n2026-10-08 09:15"),
        Triple("S47", OCTOBER, "PoS Purchase\nAmount: SAR 73.15\nCard: *4417 (expired)\nAt: ZEPHYR KIOSK\n2026-10-08 09:15"),
        Triple("S48", OCTOBER, "هلا سامر - الحوالة موقوفة\nتم ايداع الراتب\nالمبلغ: 9,500.00 ر.س\nفي: 2026-10-08"),
        // ── 6. حروف مخفية أو أشكال عرض عربية جوه كلمة حالة ──
        Triple("S28", OCTOBER, "Received transfer\nAmount: SAR 1,875.00\nFrom: SAMIR DEMO - decl⁣ined\n2026-10-08 09:15"),
        Triple("S29", OCTOBER, "حوالة واردة\nالمبلغ: 1,875.00 ر.س\nمن: سامر التجريبي (مرف͏وضة)\nفي: 2026-10-08 09:15"),
        Triple("S30", OCTOBER, "حوالة واردة\nالمبلغ: 1,875.00 ر.س\nمن: سامر التجريبي (ﻣﺮﻓﻮﺿﺔ)\nفي: 2026-10-08 09:15"),
        // ── 7. شراء برّه البلد بالريال (§75-12 · اختيار (م)): سعر صرف ≠ 1 أو دولة تانية ──
        Triple("SF09", MARCH, "Online Purchase\nVia: *8063,Visa\nAmount: 187.50 SAR\nFrom: SAMPLE LONDON SHOP\nExchange rate: 4.6875\nCountry: GB\nAt: 2026-03-05 10:00"),
        Triple("SF15", MARCH, "شراء عبر نقاط البيع\nبطاقة: 8063*;مدى\nمبلغ: 120.00 ر.س\nلدى: SAMPLE MALL DUBAI\nالدولة: الإمارات\nفي: 2026-03-05 10:00"),
        Triple("S60", OCTOBER, "Online Purchase\nAmount: SAR 73.15\nAt: TBILISI MARKET 52.00 ₾\n2026-10-08 09:15"),
    )

    /**
     * لازم القارئ يرفضها (مش «جاهزة» بمبلغ أو اتجاه غلط): إجمالي مش متسق مع المبلغ والرسوم · حالة مستخبية بحروف مخفية · عملية مصرية
     * معكوسة أو مستردة («معكوسة» · «(مستردة)» · «تأمين مسترد» — الاتجاه مش واضح).
     */
    val mustReject: List<String> = listOf("SA16", "SA17", "SA18", "S01", "S02", "S28", "S29", "S30", "EG13", "EG21", "EG14")
}
