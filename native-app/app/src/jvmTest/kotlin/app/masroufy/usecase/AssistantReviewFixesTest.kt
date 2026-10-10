package app.masroufy.usecase

import app.masroufy.core.ASSIST_UNKNOWN_TOPIC
import app.masroufy.core.ArabicVariant
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistSpeaker
import app.masroufy.core.CardState
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Language
import app.masroufy.core.MainWalletSource
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.formatMoney
import app.masroufy.core.uiText
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * المراجعة العدائية 2026-10-10 (٩ ملاحظات) من أول الكلام لحد الحفظ على عالم مخترع: المحفظة الأساسية البنك، النهارده السبت 2026-10-10،
 * قهوة ٨٥ ر.س الشهر ده في «مطاعم ومقاهي › قهوة» + بقالة ٣٠٠ كاش.
 */
class AssistantReviewFixesTest {
    @BeforeTest fun egyptianTexts() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
    }

    private suspend fun world(): AssistantWorld = AssistantWorld().also {
        MainSpendingWallets(it.stores.settings, it.wallets, DEFAULT_SPACE_ID, it.clock).set("w-bank", MainWalletSource.CHAT)
    }

    private fun ChatView.lastBot(): AssistMessage = messages.last { it.from == AssistSpeaker.BOT }
    private fun ChatView.card(): AssistMessage = messages.last { it.kind == AssistMessageKind.TXN_CARD }
    private suspend fun AssistantWorld.count() = txns.listByDateRange("2000-01-01", "2100-01-01").size

    /** ١ و٢: ولا كارت ولا عملية — رد صريح بالمكان الصح (وصفحة الشخص لو اتذكر). */
    @Test fun moneyThatIsNotSpendingIsNeverSaved() = runBlocking<Unit> {
        val cases = listOf(
            "جاني راتب 10000" to TextKey.ASSIST_NOT_EXPENSE_IN, "استلمت 500 من احمد" to TextKey.ASSIST_NOT_EXPENSE_IN,
            "رجعلي 50 من المرسى" to TextKey.ASSIST_NOT_EXPENSE_REFUND, "سلفت خالد 200" to TextKey.ASSIST_NOT_EXPENSE_DEBT,
            "حولت 1000 لسارة" to TextKey.ASSIST_NOT_EXPENSE_TRANSFER, "سحبت 500 من الصراف" to TextKey.ASSIST_NOT_EXPENSE_CASH_MOVE,
            "دفعت عن احمد 100" to TextKey.ASSIST_NOT_EXPENSE_DEBT, "ما تسجلش قهوة 15" to TextKey.ASSIST_NOT_RECORDED,
            "لا تسجل 50 بقالة" to TextKey.ASSIST_NOT_RECORDED,
        )
        for ((text, key) in cases) {
            val w = world()
            val before = w.count()
            val v = w.chat.send(text, w.ctx())
            assertTrue(v.messages.none { it.kind == AssistMessageKind.TXN_CARD || it.kind == AssistMessageKind.WALLET_PICK }, "كارت: $text")
            assertEquals(uiText(key), v.lastBot().text, text)
            assertEquals(before, w.count(), "اتسجلت عملية: $text")
        }
        val w = world()
        val links = w.chat.send("سلفت خالد 200", w.ctx()).lastBot().links
        assertEquals(listOf(AssistScreen.PERSON_PROFILE, AssistScreen.DEBTS), links.map { it.screen })
        assertEquals("p-khaled", links.first().args.values.single())
    }

    /** ٣ و٤: المبلغ بالكلام و«ونص»، ويوم المصروف («قبل يومين» · «يوم الخميس») — لحد العملية المحفوظة. */
    @Test fun wordAmountsHalvesAndDaysReachTheSavedTransaction() = runBlocking<Unit> {
        suspend fun save(text: String): Pair<Long, String> {
            val w = world()
            val card = w.chat.send(text, w.ctx()).card()
            val done = w.chat.confirm(card.id, w.ctx()).card()
            assertEquals(CardState.DONE, done.state, text)
            val tx = w.txns.findByIds(listOf(done.card!!.transactionId!!)).single()
            return tx.amountMinor to tx.occurredAt.take(10)
        }
        assertEquals(1_500L to "2026-10-10", save("قهوة خمسة عشر ريال"))
        assertEquals(1_550L to "2026-10-10", save("قهوة 15 ريال ونص"))
        assertEquals(1_550L to "2026-10-10", save("قهوة 15 ونص"))
        assertEquals(1_500L to "2026-10-08", save("قهوة 15 قبل يومين"))
        assertEquals(1_500L to "2026-10-08", save("قهوة 15 يوم الخميس"))
        assertEquals(1_500L to "2026-10-08", save("قهوة 15 أول امبارح"))
    }

    /** ٦ لحد ٩: الأرقام من حالات الاستخدام للفترة والموضوع الصح، والمجهول «مش فاهم»/«غير متاح» — مش المجموع العام. */
    @Test fun dataAnswersUseTheAskedPeriodAndSubjectOrSayTheyCannot() = runBlocking<Unit> {
        val w = world()
        val ctx = w.ctx()
        w.chat.confirm(w.chat.send("قهوة 18", ctx).card().id, ctx)
        fun money(minor: Long) = formatMoney(minor, Currency.SAR)
        suspend fun ask(text: String) = w.chat.send(text, ctx).lastBot()

        val today = ask("كم صرفت اليوم")
        assertEquals("data.spend.total", today.topic)
        assertTrue(money(1_800) in today.text, today.text)
        assertTrue(money(1_800) in ask("كم صرفت اليوم؟").text)
        // «مطاعم ومقاهي» ومعاها القهوة اللي تحتها: ٨٥ + ١٨ النهارده
        val food = ask("كم صرفت على المطاعم")
        assertEquals("data.spend.category", food.topic)
        assertTrue(money(10_300) in food.text, food.text)
        assertTrue(money(2_500) in ask("كم صرفت يوم 5").text)
        assertTrue(money(40_300) in ask("كم صرفت من ٢٨/٩ لليوم").text)
        // الشهر المالي اللي بيخلص في سبتمبر (28 أغسطس – 27 سبتمبر): قهوة 10 سبتمبر بس
        assertTrue(money(1_500) in ask("كم صرفت في شهر ٩").text)
        for (text in listOf("كم صرفت على البنزين", "كم صرفت في ستاربكس", "كم دخلي من الايجار", "كم سعر الذهب اليوم", "كم صرفت في رمضان")) {
            assertEquals(ASSIST_UNKNOWN_TOPIC, ask(text).topic, text)
        }
        for (text in listOf("كم هصرف الشهر الجاي", "كم صرفت على القهوة السنة الجاية", "لو صرفت 1000 هيكفيني؟")) {
            val r = ask(text)
            assertEquals(uiText(TextKey.ASSIST_NO_FUTURE), r.text, text)
            assertTrue(r.text.none { it.isDigit() }, "ولا رقم: $text")
        }
    }
}
