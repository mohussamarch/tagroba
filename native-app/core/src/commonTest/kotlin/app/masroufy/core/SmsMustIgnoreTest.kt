package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * رسايل **لازم ما تتسجلش** (شكل لكل نوع في البحث) — ولازم تترفض **بحارس مقصود** (عرض · رمز · مرفوضة · مش عملية)، مش بالصدفة
 * عشان التاريخ أو المبلغ مش واضح: الرمز اللي فيه مبلغ ومحل كان ممكن يعدّي لو الرسالة فيها تاريخ. بتتجرب على قارئ البلدين،
 * وعلى فلتر الجهاز (`SmsVocabulary.ignoreBeforeStorage` — نفسه اللي `SmsSafety` بيرمي بيه قبل الحفظ). كل الرسايل مخترعة.
 */
class SmsMustIgnoreTest {
    private val m = "TEST GROCER"
    private val guards = setOf(TextKey.SMS_OFFER, TextKey.SMS_SENSITIVE, TextKey.SMS_DECLINED, TextKey.SMS_NOT_TRANSACTION)

    private val ignored = listOf(
        // ── رمز تحقق — حتى لو فيه مبلغ ومحل وتاريخ ──
        "otp shaped like a purchase" to "ننصح بعدم مشاركة الرمز لحمايتك من الاحتيال\nالرمز:482913\nبطاقة:*6604\nمبلغ:SAR 64.25\nلدى:$m\nفي:26-03-05 09:10",
        "one-time password with amount" to "كلمة مرور صالحة لمرة واحدة\nرمز: 482913\nلـ : شراء انترنت\nالمبلغ: SAR 64.25",
        "temporary code with amount" to "رمز مؤقت:482913\nلـ:شراء\nالمبلغ:SAR 64.25",
        "online purchase code with merchant + date" to "رمز شراء أونلاين 482913\nللبطاقة *6604\nبـ 64.25 SAR\nمن $m\nفي 09:10 05/03/2026",
        "snb secret number" to "الرقم السري لتأكيد شراء عبر الانترنت: 482913\nمبلغ 64.25 SAR\nبطاقة *6604",
        "cib one-time number with merchant" to "الرقم السري المتغير الخاص بعملية الشراء بمبلغ 64.25 جم لدى $m هو 482913 لا تشاركه مع أحد.",
        "english otp with amount" to "482913 is your OTP\nFor: Online purchase\nAmount: 64.25 SAR\n*Do not share the code",
        "activation code" to "استخدم رمز التفعيل: 482913\nالمبلغ: 64.25 SAR\nلخدمة: محفظة",
        // ── تفويض / حجز (الخصم الحقيقي بييجي بعدين) ──
        "online authorization hold" to "تفويض عبر الانترنت\nبطاقة:6604;مدى\nمن:1188\nمبلغ:SAR 64.25\nلدى:$m\nفي:26-03-05 09:10",
        "credit card hold" to "بطاقة ائتمانية حجز مبلغ\nمبلغ: SAR 64.25\nفي: 2026-03-05 09:10",
        "credit card hold release" to "Credit Card Cash Release\nAmount: SAR 64.25\nOn: 2026-03-05 09:10",
        // ── رصيد مش كفاية / مرفوضة ──
        "rajhi insufficient balance" to "نظرا لعدم وجود رصيد كافي في حسابكم، لم يتم تنفيذ العملية على بطاقة الصراف رقم 6604",
        "stc insufficient balance with amount + merchant" to "Insufficient balance-Online Purchase\nCard:6604;VISA-VISA\nAmount:64.25SR\nAt:$m\n2026-03-05 09:10:44",
        "stc insufficient (pos line)" to "Insufficient balance\nTransaction: POS Purchase\nAmount:64.25SR\nAt:$m\n2026-03-05 09:10:44",
        "alinma card balance not enough" to "رصيد البطاقة *6604 لا يكفي لإتمام مشترياتك\nمبلغ: SAR 64.25\nمن: $m\nفي: 05/03/2026\nالرصيد المتاح : 4,100.00",
        "snb rejected purchase" to "عملية مرفوضة\nشراء-POS\nبـ64.25 SAR\nرصيد غير كافي",
        "rajhi rejected transfer" to "حوالة مالية صادرة مرفوضة\nمن: *1188\nالى: سامي التجريبي\nالمبلغ: 900.00 SAR\nفي: 26-03-05 09:10\nالسبب: تجاوز الحد اليومي",
        "snb refund of a rejected operation" to "استرجاع عملية شراء سابقة مرفوضة\nبـ64.25 SAR\nمن $m\nمدى *6604",
        "cib declined" to "لقد تم رفض المعاملة من $m على بطاقتكم الائتمانية المنتهية بـ6604 بقيمة 64.25 جم لعدم كفاية رصيد البطاقة",
        "vf not enough balance" to "لا يوجد رصيد كاف في حسابك. يمكنك تحويل 40.00 جنية فقط بعد خصم 1.00 ج رسوم تحويل; رقم العملية 123456789012",
        "vf wrong pin" to "الرقم السري غير صحيح برجاء اعادة المحاولة,رقم العملية 123456789012",
        // ── عروض وكاش باك لازم يتطلب ──
        "vf cashback promo" to "مبروك كسبت 20.00 جنيه كاش باك فلوس على محفظتك تستخدمها في خدمات فودافون كاش.\nللاستمتاع بالعرض إطلب\n*365*777#\nقبل 30/03/2026",
        "scheduled debit" to "سيتم خصم 64.25 SAR من حسابك غدًا",
        // ── تقسيط شراء قديم (مش صرف جديد) ──
        "cib installment plan" to "لقد تم تقسيط مبلغ 1,200.00 جم من $m على بطاقتكم الائتمانية المنتهية بـ 6604 بقسط شهري 100.00 جم على 12 شهر / أشهر على أن يتم سداد أول قسط يوم 01/04/2026",
        // ── طلبات لسه ما اتنفذتش ──
        "cib refund request" to "تم استلام الطلب لرد مبلغ من $m بقيمة EGP -12.40 وسيتم اضافته للبطاقة الائتمانية # 6604 بمجرد استلام المبلغ من التاجر",
        "vf cash-out request" to "تم طلب سحب مبلغ 300.00 جنيه من حساب فودافون كاش. للتأكيد اطلب #1*9* وادخل الرقم السري. رقم العملية 123456789012.",
        // ── معلومة بس ──
        "cib statement" to "كشف حساب شهر مارس 2026 لبطاقتك الائتمانية المنتهية بـ 6604 هو 4,100.00 جم ، الحد الأدنى للسداد قبل 25/03 هو 350.00 جم .",
        "cib card activated" to "تم تفعيل بطاقتك الائتمانية المُنتهية بـ #6604 بنجاح. يرجى إنشاء رقم سري من خلال ماكينة الصراف الآلي التابعة لـCIB",
        "ipn pin set" to "05/03/2026 09:10 Your account IPN PIN has been SET successfully.",
        "login" to "Dear Customer, You have logged in to KFH - Egypt Mobile Banking service at 05/03/2026, 09:10",
        "vf balance only" to "رصيد حسابك فى فودافون كاش الحالي4,100.00 جنيه؛ تاريخ العملية 09:10 26-03-05 رقم العملية123456789012.تابع مصروفاتك",
        "vf balance only english" to "Your current Vodafone Cash balance is 4,100.00 LE  Trx date: 05/03/2026 09:10 Trx ID 123456789012. Enjoy",
    )

    @Test fun bothReadersRejectEveryMustIgnoreMessageByAGuard() {
        for ((name, body) in ignored) {
            for ((country, parse) in listOf("SA" to ::parseBankSms, "EG" to ::parseEgyptBankSms)) {
                val r = assertIs<SmsParseResult.Rejected>(parse(smsMessage(body), 1), "$country $name")
                assertTrue(guards.any { uiText(it) == r.reason }, "$country $name: rejected by accident («${r.reason}»), not by a guard")
            }
        }
    }

    /**
     * فلتر الجهاز بيرميها — إلا اللي أولها شكل بنك معروف أو الحارس مسك آخرها بس (بتتحفظ وتستنى في «المرفوضة» بسبب الحارس — الجولة
     * الخامسة والسادسة: الضياع مش مقبول). الرمز بيترمي دايمًا، واللي اتحفظ القارئين بيرفضوه (الاختبار اللي فوق).
     */
    @Test fun theDeviceFilterDropsThemBeforeStorage() {
        for ((name, body) in ignored) {
            val stored = !SmsVocabulary.ignoreBeforeStorage(body)
            val reason = SmsVocabulary.ignoreReason(body)
            assertTrue(!stored || (reason != null && reason != TextKey.SMS_SENSITIVE), name)
            if (reason == TextKey.SMS_SENSITIVE) assertTrue(!stored, "$name: a code was kept")
        }
    }

    /** الحراس ما بيمسكوش عمليات حقيقية شبههم: الخصم بعد التفويض · قسط التمويل · «دفعة» · رمز في كلمة تانية. */
    @Test fun guardsDoNotSwallowRealTransactions() {
        val real = listOf(
            "خصم من التفويض عبر الانترنت\nبطاقة:6604;مدى\nمن:1188\nمبلغ:SAR 64.25\nلدى:$m\nفي:26-03-05 09:10",
            "خصم: قسط تمويل\nالقسط: 1,200.00 SAR\nمن: 1188\nالمبلغ المتبقي: SAR 30,000.00\n26/3/5 09:10",
            "تم سداد مبلغ 1,500.00 جم فى بطاقتكم الائتمانية المنتهية بـ 6604 بتاريخ 05/03/26",
        )
        for (body in real) assertTrue(!SmsVocabulary.ignoreBeforeStorage(body), body)
        assertEquals(6425L, (parseBankSms(smsMessage(real[0]), 1) as SmsParseResult.Ok).row.amountMinor)
    }
}
