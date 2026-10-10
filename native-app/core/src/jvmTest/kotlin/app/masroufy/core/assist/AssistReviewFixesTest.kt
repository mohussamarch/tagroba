package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * المراجعة العدائية 2026-10-10 على فرع `assistant-engine` (٩ ملاحظات): كل صيغة اتبلّغ عنها بالحرف + صيغ زيادة. كل الأسامي مخترعة.
 * النهارده في الاختبار = السبت 2026-10-10.
 */
class AssistReviewFixesTest {
    private val lex = AssistTestLexicon.lexicon()
    private val today = "2026-10-10"

    private fun u(text: String, l: AssistLexicon = lex) = understandAssist(text, AssistUnderstandContext(l))
    private fun amount(text: String) = u(text).signals.money.amounts.firstOrNull()?.minor

    /** ١ و٢: فلوس داخلة · سلفة · تحويل · سحب ⇒ مش مصروف، من غير كارت (والشخص بيطلع للرابط). */
    @Test fun moneyThatIsNotSpendingNeverBecomesAPurchaseCard() {
        val cases = listOf(
            "جاني راتب 10000" to AssistNote.NOT_EXPENSE_IN,
            "استلمت 500 من احمد" to AssistNote.NOT_EXPENSE_IN,
            "قبضت 3000" to AssistNote.NOT_EXPENSE_IN,
            "received 500 from ahmed" to AssistNote.NOT_EXPENSE_IN,
            "رجعلي 50 من المرسى" to AssistNote.NOT_EXPENSE_REFUND,
            "refund 40 from al marsa" to AssistNote.NOT_EXPENSE_REFUND,
            "سلفت خالد 200" to AssistNote.NOT_EXPENSE_DEBT,
            "دفعت عن احمد 100" to AssistNote.NOT_EXPENSE_DEBT,
            "استلفت من سارة 300" to AssistNote.NOT_EXPENSE_DEBT,
            "سددت لخالد 150" to AssistNote.NOT_EXPENSE_DEBT,
            "حولت 1000 لسارة" to AssistNote.NOT_EXPENSE_TRANSFER,
            "سحبت 500 من الصراف" to AssistNote.NOT_EXPENSE_CASH_MOVE,
            "ايداع 700 في الراجحي" to AssistNote.NOT_EXPENSE_CASH_MOVE,
        )
        val wrong = cases.mapNotNull { (text, note) -> u(text).let { if (it.intent == AssistIntent.QUICK_ADD && it.note == note) null else "«$text» ⇒ ${it.wire} ${it.note}" } }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
        assertEquals("p-khaled", u("سلفت خالد 200").subject?.id)
        assertEquals("p-sara", u("حولت 1000 لسارة").subject?.id)
        assertEquals("p-ahmed", u("دفعت عن احمد 100").subject?.id)
        // مصروف عادي زي ما هو: رسوم التحويل/السحب · «دفعت عن طريق» · سداد فاتورة من غير شخص
        for (text in listOf("قهوة 15", "رسوم تحويل 5", "رسوم سحب 3", "دفعت عن طريق البطاقة 50 بقالة", "سددت فاتورة الكهرباء 380", "بقالة 120 كاش")) {
            val r = u(text)
            assertEquals(AssistIntent.QUICK_ADD, r.intent, text)
            assertNull(r.note, text)
        }
    }

    /** ٣: الأرقام بالكلام بعشراتها، و«ونص». */
    @Test fun wordNumbersKeepTheirTensAndAHalfIsAdded() {
        assertEquals(1_500L, amount("قهوة خمسة عشر ريال"))
        assertEquals(1_200L, amount("قهوة اثنا عشر ريال"))
        assertEquals(1_700L, amount("قهوة سبعتاشر"))
        assertEquals(1_550L, amount("قهوة 15 ريال ونص"))
        assertEquals(1_550L, amount("قهوة 15 ونص"))
        assertEquals(1_550L, amount("قهوة 15 و نص"))
        assertEquals(550L, amount("قهوة خمسة ونص"))
        assertEquals(150_000L, amount("ايجار الف ونص"))
        assertEquals(15_000L, amount("بقالة مية ونص"))
        assertEquals(250_000L, amount("اثاث 2 الف ونص"))
        assertEquals(1_550L, amount("coffee 15 and a half"))
        // «150 ونص» ملتبس (١٥٠٫٥٠ ولا ٢٠٠؟) ⇒ سؤال بصراحة، مش تخمين
        assertEquals(AssistNote.INVALID_AMOUNT, u("بقالة 150 ونص").note)
    }

    /** ٤: «قبل يومين» · «يوم الخميس» · «يوم 5» بيتحسبوا يوم المصروف (السبت 10 أكتوبر). */
    @Test fun relativeDaysAndWeekdaysSetTheSpendDate() {
        fun date(text: String) = u(text).signals.let { assistSpendDate(it.dayOffset, it.period, today) }
        assertEquals("2026-10-08", date("قهوة 15 قبل يومين"))
        assertEquals("2026-10-08", date("قهوة 15 يوم الخميس"))
        assertEquals("2026-10-08", date("قهوة 15 أول امبارح"))
        assertEquals("2026-10-07", date("قهوة 15 من 3 أيام"))
        assertEquals("2026-10-05", date("قهوة 15 يوم 5"))
        assertEquals("2026-10-10", date("قهوة 15 يوم السبت"))
        assertEquals("2026-10-04", date("قهوة 15 يوم الاحد"))
        assertNull(date("قهوة 15"))
        assertEquals(1_500L, amount("قهوة 15 يوم 5"))
        assertEquals(1_500L, amount("قهوة 15 قبل يومين"))
        assertEquals(AssistIntent.EDIT_PENDING, understandAssist("يوم الخميس", AssistUnderstandContext(lex, pendingCard = true)).intent)
    }

    /** ٥: «ما تسجلش» ⇒ ولا كارت. */
    @Test fun dontRecordMakesNoCard() {
        for (text in listOf("ما تسجلش قهوة 15", "لا تسجل 50 بقالة", "متسجلش بقالة 30", "don't record coffee 15")) {
            assertEquals(AssistNote.DONT_RECORD, u(text).note, text)
        }
    }

    /** ٦: «كم صرفت اليوم» صرف النهارده مش المتاح يوميًا · سؤال السعر «مش فاهم» · «من ٢٨/٩ لليوم» من التاريخ لحد النهارده. */
    @Test fun spentTodayIsTodaysSpendingNotTheDailyAllowance() {
        for (text in listOf("كم صرفت اليوم", "كم صرفت اليوم؟", "ماذا صرفت اليوم", "صرفت كام النهارده")) {
            val r = u(text)
            assertEquals(AssistIntent.SPEND_TOTAL, r.intent, text)
            assertEquals(AssistPeriodKind.TODAY, r.signals.period?.kind, text)
        }
        assertEquals(AssistIntent.DAILY_ALLOWANCE, u("كم اقدر اصرف في اليوم").intent)
        assertEquals(AssistIntent.DAILY_ALLOWANCE, u("كم أصرف يوميا").intent)
        assertEquals(AssistIntent.UNKNOWN, u("كم سعر الذهب اليوم").intent)
        val since = u("كم صرفت من ٢٨/٩ لليوم")
        assertEquals(AssistIntent.SPEND_TOTAL, since.intent)
        assertTrue(since.signals.money.amounts.isEmpty(), "التاريخ مش مبلغ")
        assertEquals(AssistRange("2026-09-28", today, null), resolveAssistRange(since.signals.period, today, 28))
    }

    /** ٧: اسم مش معروف بعد «على/في/عند» ⇒ «مش فاهم» مش المجموع العام · «المطاعم» = «مطاعم ومقاهي». */
    @Test fun anUnknownSubjectIsNotAnsweredWithTheMonthlyTotal() {
        // نفس عالم المراجعة: «مطاعم ومقاهي» وتحته «قهوة» بس
        val food = Category("c-food", null, "مطاعم ومقاهي", "food", "#000", "#fff", true, 1)
        val coffee = Category("c-coffee", "c-food", "قهوة", "coffee", "#000", "#fff", true, 2)
        val small = AssistLexicon(categories = listOf(food, coffee))
        val texts = listOf(
            "كم صرفت على البنزين", "كم صرفت على الوقود", "كم صرفت على الملابس", "كم صرفت في ستاربكس", "how much did I spend at starbucks", "كم دخلي من الايجار",
        )
        for (text in texts) assertEquals(AssistIntent.UNKNOWN, u(text, small).intent, text)
        // في شجرة التصنيفات الحقيقية «البنزين» تصنيف ⇒ صرفه هو
        assertEquals(AssistIntent.SPEND_CATEGORY, u("كم صرفت على البنزين").intent)
        // الكلمات اللي مش اسم موضوع بتعدّي
        for (text in listOf("كم صرفت في الشهر ده", "كم صرفت هذا الشهر", "كم صرفت في المجمل", "how much did I spend in total", "كم دخلي هذا الشهر")) {
            assertTrue(u(text).intent != AssistIntent.UNKNOWN, text)
        }
        val r = u("كم صرفت على المطاعم", small)
        assertEquals(AssistIntent.SPEND_CATEGORY, r.intent)
        assertEquals("c-food", r.subject?.id)
        assertEquals(AssistIntent.UNKNOWN, u("كم صرفت على coffee", small).intent)
        assertEquals(AssistIntent.UNKNOWN, u("كام صرفت على الكوفي", small).intent)
    }

    /** ٨: الفترات اللي كانت بتتحول للشهر الحالي في صمت. */
    @Test fun periodsAreReadOrHonestlyNotUnderstood() {
        val cases = listOf(
            "كم صرفت آخر شهرين" to AssistPeriod(AssistPeriodKind.LAST_MONTHS, 2),
            "كم صرفت في آخر شهرين" to AssistPeriod(AssistPeriodKind.LAST_MONTHS, 2),
            "كم صرفت هذي السنة" to AssistPeriod(AssistPeriodKind.THIS_YEAR),
            "كم صرفت في شهر ٩" to AssistPeriod(AssistPeriodKind.NAMED_MONTH, 9),
            "كم صرفت في الشهر 8" to AssistPeriod(AssistPeriodKind.NAMED_MONTH, 8),
            "كم صرفت الشهر قبل الماضي" to AssistPeriod(AssistPeriodKind.FISCAL_AGO, 2),
            "كم صرفت اخر اسبوعين" to AssistPeriod(AssistPeriodKind.LAST_DAYS, 14),
            "كم صرفت يوم 5" to AssistPeriod(AssistPeriodKind.ON_DATE, 5),
            "كم صرفت من 3 أيام" to AssistPeriod(AssistPeriodKind.DAYS_AGO, 3),
            "كم صرفت قبل 3 أيام" to AssistPeriod(AssistPeriodKind.DAYS_AGO, 3),
            "how much did I spend in the last 2 weeks" to AssistPeriod(AssistPeriodKind.LAST_DAYS, 14),
            "كم صرفت يوم الخميس" to AssistPeriod(AssistPeriodKind.WEEKDAY, 4),
        )
        val wrong = cases.mapNotNull { (text, p) -> u(text).let { if (it.intent == AssistIntent.SPEND_TOTAL && it.signals.period == p) null else "«$text» ⇒ ${it.wire} ${it.signals.period}" } }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
        assertTrue(u("كم صرفت في شهر ٩").signals.money.amounts.isEmpty(), "٩ رقم شهر مش مبلغ")
        assertEquals(AssistIntent.UNKNOWN, u("كم صرفت في رمضان").intent)
        assertEquals(AssistIntent.UNKNOWN, u("كم صرفت آخر سنتين").intent)
        // المدى: الشهر قبل الماضي = الشهر المالي اللي بيخلص في أغسطس (الراتب يوم 28) · «يوم 5» = 5 أكتوبر · «يوم 25» = 25 سبتمبر
        assertEquals("2026-07-28", resolveAssistRange(AssistPeriod(AssistPeriodKind.FISCAL_AGO, 2), today, 28).from)
        assertEquals("2026-10-05", resolveAssistRange(AssistPeriod(AssistPeriodKind.ON_DATE, 5), today, 28).from)
        assertEquals("2026-09-25", resolveAssistRange(AssistPeriod(AssistPeriodKind.ON_DATE, 25), today, 28).from)
        assertEquals("2025-12-05", resolveAssistRange(AssistPeriod(AssistPeriodKind.ON_DATE, 5, 12), today, 28).from)
        // «ايجار الشهر 3000» مبلغ (مش رقم شهر)
        assertEquals(300_000L, amount("ايجار الشهر 3000"))
    }

    /** ٩: المستقبل والافتراض ⇒ «غير متاح»، مش صرف اللي فات. */
    @Test fun futureAndWhatIfQuestionsAreNotAnsweredWithPastSpending() {
        for (text in listOf("كم هصرف الشهر الجاي", "كم صرفت على القهوة السنة الجاية", "لو صرفت 1000 هيكفيني؟", "how much will I spend next month")) {
            val r = u(text)
            assertEquals(AssistIntent.UNKNOWN, r.intent, text)
            assertEquals(AssistNote.NO_FUTURE, r.note, text)
        }
        assertEquals(AssistIntent.FORECAST, u("كم هصرف لآخر الشهر").intent)
        assertEquals(AssistIntent.SPEND_CATEGORY, u("كم صرفت على القهوة لو سمحت").intent)
    }
}
