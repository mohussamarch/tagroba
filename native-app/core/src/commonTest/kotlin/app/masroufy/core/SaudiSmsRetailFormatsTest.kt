package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.SmsKind.BILL
import app.masroufy.core.SmsKind.CASH_DEPOSIT
import app.masroufy.core.SmsKind.CASH_WITHDRAWAL
import app.masroufy.core.SmsKind.FEE
import app.masroufy.core.SmsKind.OTHER
import app.masroufy.core.SmsKind.PURCHASE
import app.masroufy.core.SmsKind.REFUND
import app.masroufy.core.SmsKind.SALARY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * رسايل البنوك السعودية — شراء واسترداد وسحب وإيداع وفواتير (شكل لكل قالب في `research/banks/saudi-sms-formats.json`؛
 * التحويلات في [SaudiSmsTransferFormatsTest]). كل الرسايل مخترعة (`SmsFormatsKit.kt`).
 */
class SaudiSmsRetailFormatsTest {
    private val m = "TEST GROCER"

    private val cases = listOf(
        // ── الراجحي: الشكل القديم «بطاقة:/مبلغ:/لدى:/في:» ──
        SmsCase("rajhi pos", "شراء\nبطاقة:6604;مدى(أثير)\nمبلغ:SAR 64.25 \nلدى:$m\nفي:26-03-05 09:10", 6425, OUT, PURCHASE, m),
        SmsCase("rajhi online", "شراء انترنت\nبطاقة:6604;مدى\nمن:1188\nمبلغ:SAR 64.25\nلدى:$m\nفي:26-03-05 09:10", 6425, OUT, PURCHASE, m),
        SmsCase(
            "rajhi capture (released hold is not the amount)",
            "خصم من التفويض عبر الانترنت\nبطاقة:6604;مدى\nمن:1188\nمبلغ:SAR 64.25\nاعادة مبلغ:SAR 5.75\nلدى:$m\nفي:26-03-05 09:10", 6425, OUT, PURCHASE, m,
        ),
        // ── الراجحي 2026 «عبر:/بـ/لـ» ──
        SmsCase("rajhi 2026 pos", "شراء\nعبر:6604;مدى-ابل باي\nبـSAR 64.25\nلـ$m\n26/3/5 09:10", 6425, OUT, PURCHASE, m),
        SmsCase("rajhi 2026 online", "شراء انترنت\nعبر:6604;مدى\nمن:1188\nبـSAR 64.25\nلـ$m\n26/3/5 09:10", 6425, OUT, PURCHASE, m),
        SmsCase("rajhi english", "PoS Purchase\nBy:6604;mada(Apple Pay)\nAmount:SR 64.25\nAt:$m\n26-03-05 09:10", 6425, OUT, PURCHASE, m),
        SmsCase("rajhi e-store account debit", "عملية شراء-حساب جاري - المتجر الألكتروني\nمن:1188\nمبلغ:SR 64.25\n26-03-05", 6425, OUT, PURCHASE, ""),
        SmsCase("rajhi online refund", "استرداد شراء الانترنت\nبطاقة:6604;مدى\nمبلغ:SAR 12.40\nلدى:$m\nفي:26-03-05 09:10", 1240, IN, REFUND, m),
        SmsCase("rajhi pos refund", "استرداد شراء\nبطاقة:6604;مدى\nمبلغ:SAR 12.40\nلدى:$m\nفي:26-03-05 09:10", 1240, IN, REFUND, m),
        SmsCase("rajhi return", "مرتجع\nبطاقة:6604;مدى\nمبلغ:SAR 12.40\nالتاجر:$m\nفي:26-03-05 09:10", 1240, IN, REFUND, m),
        SmsCase("rajhi reversal", "عكس العملية\nبطاقة:6604;مدى\nمبلغ:SAR 12.40\nلدى:$m\nفي:26-03-05 09:10", 1240, IN, REFUND, m),
        SmsCase("rajhi cashback reversal", "كاش باك عكس\nبطاقة:6604;مدى\nمبلغ:SAR 12.40\nلدى:$m\nفي:26-03-05 09:10", 1240, OUT, OTHER, m),
        SmsCase("rajhi atm", "سحب:صراف آلي\nبطاقة:6604;مدى\nمبلغ:SAR 300.00\nمكان السحب:TEST ATM\n26-03-05 09:10", 30000, OUT, CASH_WITHDRAWAL),
        SmsCase("rajhi atm deposit", "إيداع:صراف آلي\nمبلغ:SAR 300.00\nالى:1188\nفي:26-03-05 09:10", 30000, IN, CASH_DEPOSIT),
        SmsCase(
            "rajhi citizen account (backslash date + hidden mark)",
            "إيداع دعم حكومي - حساب المواطن\nالمبلغ:SAR 720.00\nالى:1188\n؜26\\03\\05 09:10\nحساب المواطن", 72000, IN, OTHER,
        ),
        SmsCase("rajhi cheque", "ايداع شيك ورقي\nمبلغ:SAR 900.00\nحساب صاحب الشيك:9021\nالى:1188\nفي:26-03-05 09:10", 90000, IN),
        SmsCase("rajhi salary", "راتب\nمبلغ:SAR 7,400.00\nالى:1188\nفي:26-03-05 09:10", 740000, IN, SALARY),
        SmsCase(
            "rajhi sadad",
            "سداد فاتورة\nمن:1188\nمبلغ:SAR 150.00\nمفوتر:002\nالخدمة:فاتورة مياه\nالفاتورة:4400012345\nفي:26-03-05 09:10", 15000, OUT, BILL,
        ),
        SmsCase("rajhi telecom", "مدفوعات سوا\nمبلغ:SAR 30.00\nمن:1188\nفي:26-03-05 09:10", 3000, OUT, BILL),
        SmsCase(
            "rajhi loan installment (remaining finance is not the amount)",
            "خصم: قسط تمويل\nالقسط: 1,200.00 SAR\nمن: 1188\nالمبلغ المتبقي: SAR 30,000.00\n26/3/5 09:10", 120000, OUT,
        ),
        SmsCase("unlabelled pos", "شراء عبر نقاط البيع\nبطاقة:*6604;مدى(Apple Pay)\nمن:*1188\nلدى:$m\nمبلغ:SAR 64.25\nفي:26-03-05 09:10", 6425, OUT, PURCHASE, m),
        // ── الأهلي السعودي: «من <المحل>» من غير نقطتين، والتاريخ بعد الساعة أو مفيش تاريخ خالص ──
        SmsCase("snb pos", "شراء نقاط بيع ApplePay\nبـSAR 64.25\nمن $m\nمدى *6604\nفي 09:10 05/03/26", 6425, OUT, PURCHASE, m),
        SmsCase("snb online without date (arrival day, Riyadh time)", "شراء انترنت\nبـ64.25 SAR\nمن $m\nمدى-ابل *6604", 6425, OUT, PURCHASE, m),
        SmsCase("snb refund", "استرجاع شراء\nبـ12.40 SAR\nمن $m\nمدى *6604", 1240, IN, REFUND, m),
        SmsCase("snb cash correction", "تصحيح سحب نقدي\nمبلغ 300.00 SAR\nمدى *6604", 30000, IN, REFUND),
        // ── ساب ──
        SmsCase("sab pos", "شراء عبر نقاط البيع\nبطاقة: ***6604;mada(Apple Pay);\nمبلغ: SAR 64.25\nلدى: $m\nفي: 2026-03-05 09:10:44", 6425, OUT, PURCHASE, m),
        SmsCase("sab online", "شراء إنترنت\nبطاقة: ***6604;مدى\nمن: ***1188\nمبلغ: 64.25 SAR\nلدى: $m\nفي: 2026-03-05 09:10:44", 6425, OUT, PURCHASE, m),
        SmsCase("sab salary", "حوالة راتب\nإلى: **1188\nمبلغ: 7,400.00 SAR\nفي: 2026-03-05 09:10:44", 740000, IN, SALARY),
        // ── الإنماء ──
        SmsCase(
            "alinma pos with balance",
            "شراء محلي من نقاط البيع\nعبر Apple Pay\nبمبلغ: 64.25 SAR\nالبطاقة: **6604\nحساب: **1188\nمن: $m\nفي: 05/03/2026 09:10\nالرصيد: 4,100.00 SAR",
            6425, OUT, PURCHASE, m,
        ),
        SmsCase("alinma currency words first", "شراء عبر Apple Pay\nمبلغ: ريال سعودى 64.25\nبطاقة مدى: 6604*\nحساب: *1188\nمن: $m\nفي: 05/03/2026 09:10", 6425, OUT, PURCHASE, m),
        SmsCase("alinma credit card", "شراء عبر: POS\nالبطاقة الائتمانية: **6604\nمبلغ: SAR 64.25\nلدى: $m\nفي: 09:10 05/03/2026\nالرصيد: 4,100.00 ريال", 6425, OUT, PURCHASE, m),
        SmsCase("alinma mada pay", "شراء (مدى Pay)\nمبلغ: 64.25 SAR\nبطاقة مدى: 6604*\nمن: $m\nفي: 05/03/2026 09:10", 6425, OUT, PURCHASE, m),
        SmsCase("alinma no colons", "شراء عبر نقاط البيع\nبطاقة مدى:MADA PAY 6604*\nمبلغ 64.25 SAR\nمن $m\nفي 09:10 05/03/2026", 6425, OUT, PURCHASE, m),
        SmsCase("alinma atheer", "شراء عبر نقاط بيع\n مبلغ 64.25 SAR\nبطاقة 6604* مدى-أثير\nمن $m\nفي 09:10 05/03/2026", 6425, OUT, PURCHASE, m),
        SmsCase("alinma online", "شراء إنترنت\nمبلغ: 64.25 SAR\nببطاقة مدى: 6604*\nمن حساب: **1188\nمن: $m\nفي: 05/03/2026 09:10", 6425, OUT, PURCHASE, m),
        SmsCase("alinma online amount in title", "شراء إنترنت 64.25 SAR\nبطاقة 6604* مدى\nحساب *1188\nمن $m\nفي 09:10 05/03/2026", 6425, OUT, PURCHASE, m),
        SmsCase(
            "alinma reverse transaction = merchant refund, not a person transfer",
            "حوالة عكسية\nمبلغ: 12.40 SAR\nلبطاقة مدى: 6604*\nالحساب: **1188\nمن البائع: $m\nفي: 05/03/2026 09:10", 1240, IN, REFUND, m,
        ),
        SmsCase("alinma salary greeting", "هلا سامي\nتم إيداع الراتب\nبمبلغ: 7,400.00 SAR\nفي حساب: **1188\nفي: 05/03/2026 09:10\nمن خلال الإنماء", 740000, IN, SALARY),
        SmsCase("alinma salary", "تم إيداع الراتب\nمبلغ 7,400.00 SAR\nلـ **1188\nفي05/03/2026 09:10", 740000, IN, SALARY),
        // ── دي 360 ──
        SmsCase("d360 local online", "Local Online Purchase\nAmount: SAR 64.25\nCard: *6604 - mada (Ecommerce)\nAt: $m\nAccount number: *1188\nOn: 05/03/2026 09:10", 6425, OUT, PURCHASE, m),
        // ── بنك إس تي سي ──
        SmsCase("stc pos", "PoS Purchase\nCard:6604;VISA-mada Pay (Atheer)\nAmount:64.25SR\nAt:$m\n2026-03-05 09:10:44", 6425, OUT, PURCHASE, m),
        SmsCase("stc atheer", "mada Pay (Atheer) Purchase\nVia: *6604\nAmount: 64.25 SAR\nFrom: $m\nAt: 2026-03-05 09:10:44", 6425, OUT, PURCHASE, m),
        SmsCase("stc card", "**6604 Purchase\nVia:6604\nAmount: 64.25 SAR\nFrom: $m\nAt: 2026-03-05 09:10:44", 6425, OUT, PURCHASE, m),
        SmsCase("stc online one line", "Online Purchase Transaction Amount 64.25 SAR\nFrom: $m\nCard: *6604\nDate 2026-03-05 09:10:44", 6425, OUT, PURCHASE, m),
        SmsCase("stc arabic two من lines", "شراء mada Pay (Atheer)\nمن:*6604\nبـ:64.25 SAR\nمن:$m\nفي: 2026-03-05 09:10:44", 6425, OUT, PURCHASE, m),
        SmsCase("stc internet operation", "عملية انترنت\nب: 64.25 SAR\nمن:$m\nبطاقة:*6604\nفي:2026-03-05 09:10:44", 6425, OUT, PURCHASE, m),
        SmsCase("stc refund notice", "Notification: Refund\nTransaction: $m\nCard: ***6604\nAmount: 12.40 SAR\nDate: 2026-03-05 09:10:44", 1240, IN, REFUND, m),
        SmsCase("stc reversal (merchant in في:)", "عكس عملية\nالى: ***6604; VISA\n12.40 SAR :المبلغ\nفي: $m\nبتاريخ: 2026-03-05 09:10:44", 1240, IN, REFUND, m),
        SmsCase("stc top-up", "Adding money to account\nAmount: 300.00 SAR\nVia: *6604\nAt: 2026-03-05 09:10:44", 30000, IN, OTHER),
        SmsCase(
            "stc sadad", "Bill Payment (SADAD)\nAmount: 150.00 SAR\nBiller: TEST WATER CO\nService: Water bill\nNumber: 4400012345\nFrom Account: ***1188\nOn: 2026-03-05 09:10:44",
            15000, OUT, BILL,
        ),
        SmsCase("stc government", "Government Payments\nAmount: 150.00 SAR\nBiller: TEST AGENCY\nService: Renewal\nFrom Account: ***1188\nOn: 2026-03-05 09:10:44", 15000, OUT, BILL),
        SmsCase("stc government ar", "المدفوعات الحكومية\nمبلغ: 150.00 SAR\nالمفوتر: الجوازات\nالخدمة: تجديد\nمن حساب: ***1188\nفي: 2026-03-05 09:10:44", 15000, OUT, BILL),
        SmsCase("stc fee", "Debit fees\nReason: Card replacement fee\nAmount: SAR 15.00\nFrom: STC Bank wallet\nDate: 2026-03-05 09:10:44", 1500, OUT, FEE),
        // ── البلاد + بنك مش معروف ──
        SmsCase("albilad credit card", "شراء عبر نقاط البيع\nبطاقة: **6604;الإئتمانية\nلدى: $m\nدولة: SA\nمبلغ: 64.25 SAR\nرصيد: 4,100.00 SAR\nفي: 05/03/2026 09:10", 6425, OUT, PURCHASE, m),
        SmsCase("albilad mada", "مشتريات نقاط البيع\nبطاقة: **6604;مدى\nمن: xx188\nمبلغ: 64.25 SAR\nلدى: $m\nدولة: SA\nفي: 05/03/2026 09:10", 6425, OUT, PURCHASE, m),
        SmsCase("tidybox pos", "شراء عبر نقاط البيع\nبطاقة:6604 ;فيزا-ابل باي\nلدى:$m\nمبلغ:64.25 SAR\nرصيد:4,100.00 SAR\n؜ 5/3/26 09:10", 6425, OUT, PURCHASE, m),
    )

    @Test fun everyRetailTemplateIsReadRight() {
        for (c in cases) checkCase(c, ::parseBankSms, Currency.SAR)
    }

    /** الرسوم والضريبة مش المبلغ: «إجمالي المبلغ المستحق» هو المخصوم فعلًا. */
    @Test fun totalDueWinsOverAmountFeesAndVat() {
        val cases = listOf(
            SmsCase(
                "stc online with fees",
                "Online Purchase\nVia: *6604,Visa\nAmount: 64.25 SAR\nFrom: $m\nExchange rate: 1.0000\nVAT: 0.30 SAR\nFees: 2.00 SAR\nTotal due amount: 66.55 SAR\nRemaining balance: 4,100.00 SAR\nCountry: SA\nAt: 2026-03-05 09:10:44",
                6655, OUT, PURCHASE, m,
            ),
            SmsCase(
                "stc arabic alef maqsura",
                "شراء إنترنت\nعبر: *6604, Visa\nب: 64.25 SAR\nمن: $m\nرسوم تحويل العملات: 1.0000\nضريبة القيمة المضافة: 0.30 SAR\nرسوم العملية: 2.00 SAR\nإجمالى المبلغ المستحق: 66.55 SAR\nالرصيد المتبقى: 4,100.00 SAR\nالدولة: SA\nفى: 2026-03-05 09:10:44",
                6655, OUT, PURCHASE, m,
            ),
        )
        for (c in cases) checkCase(c, ::parseBankSms, Currency.SAR)
    }

    /** §75-12: المبلغ المحلي مكتوب ⇒ هو المبلغ والأجنبي للمعلومة؛ مش مكتوب ⇒ الرسالة تستنى ومعاها كل اللي اتقري. */
    @Test fun foreignCurrency() {
        val eur = SmsForeignAmount("EUR", 1490)
        checkCase(
            SmsCase(
                "d360 local amount in parentheses", "International Online Purchase\nAmount: EUR 14.90 (SAR 64.25)\nCard: *6604 - VISA (Ecommerce)\nFee: SAR 1.20\nAt: $m\nAccount number: *1188\nCountry: FR\nOn: 05/03/2026 09:10",
                6425, OUT, PURCHASE, m, foreign = eur,
            ),
            ::parseBankSms, Currency.SAR,
        )
        checkCase(
            SmsCase(
                "d360 international atm", "International ATM Withdrawal\nAmount: EUR 70.00 (SAR 300.00)\nCard: *6604 - VISA\nFee: 25.00\nAt: TEST ATM PARIS\nCountry: FR\nOn: 05/03/2026 09:10",
                30000, OUT, CASH_WITHDRAWAL, foreign = SmsForeignAmount("EUR", 7000),
            ),
            ::parseBankSms, Currency.SAR,
        )
        checkCase(
            SmsCase(
                "tidybox foreign + fees + total", "شراء انترنت\nبطاقة: 6604 ;فيزا\nمبلغ: 14.90 EUR (64.25 ريال)\nلدى: $m\nرسوم وضريبة: 2.10 SAR\nسعر الصرف~ 4.3121\nإجمالي المبلغ المستحق: 66.35 SAR\nدولة: FR\nرصيد: 4,100.00 SAR\n؜ 5/3/26 09:10",
                6635, OUT, PURCHASE, m, foreign = eur,
            ),
            ::parseBankSms, Currency.SAR,
        )
        val pending = assertIs<SmsParseResult.Rejected>(
            parseBankSms(smsMessage("شراء دولي\nبطاقة:6604;مدى(أثير)\nمبلغ:EUR 14.90\nدولة:FR\nلدى:$m\nفي:26-03-05 09:10"), 1),
        )
        assertEquals(uiText(TextKey.SMS_FOREIGN_CURRENCY), pending.reason)
        assertEquals(SmsForeignPending(SMS_TX_DAY, eur, OUT, m, PURCHASE, ownLast4 = "6604"), pending.foreign)
        // الجنيه في قارئ السعودية = رسالة مصرية مش عملية أجنبية ⇒ من غير «مستنية المبلغ»
        val egp = assertIs<SmsParseResult.Rejected>(parseBankSms(smsMessage("شراء\nمبلغ:EGP 50.00\nلدى:$m\nفي:26-03-05 09:10"), 1))
        assertEquals(null, egp.foreign)
    }
}
