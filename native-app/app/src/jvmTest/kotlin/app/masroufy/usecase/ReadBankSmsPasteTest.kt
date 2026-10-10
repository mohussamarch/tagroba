package app.masroufy.usecase

import app.masroufy.core.SmsShape
import app.masroufy.core.UiKey
import app.masroufy.core.parseBankSms
import app.masroufy.core.uiText
import app.masroufy.port.BankSmsPort
import app.masroufy.port.BankSmsRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * §82-14 (رد المالك 2026-10-10 ✱ — غير المقترح «تاريخ اللصق = تاريخ الوصول»): **الرسالة الملصوقة لازم تاريخ** يكتبه المالك (أو «النهاردة؟»).
 * التاريخ ده هو يوم وصولها: الرسالة اللي مكتوب فيها تاريخها بتاخد تاريخها، واللي مفيهاش بتاخد التاريخ اللي اتكتب. كل الرسايل مخترعة.
 */
class ReadBankSmsPasteTest {
    private val reader = ReadBankSms(
        object : BankSmsPort {
            override val available = false
            override suspend fun read(from: String, to: String, senders: List<String>) = BankSmsRead(emptyList(), false)
        },
        ::parseBankSms,
    )

    @Test fun aPastedMessageNeedsAValidDate() {
        for (bad in listOf("", "2026-13-01", "10/10/2026", "النهاردة")) {
            val e = assertFailsWith<IllegalArgumentException>(bad) { reader.paste("PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE", bad) }
            assertEquals(uiText(UiKey.SMS_PASTE_DATE_NEEDED), e.message)
        }
    }

    @Test fun aDatelessPastedMessageTakesTheDateTheOwnerWrote() {
        val known = reader.paste("PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE", "2026-10-09").rows.single()
        assertEquals("2026-10-09" to 2_500L, known.date to known.amountMinor)
        assertTrue(known.shape.clear, "known layout ⇒ same reading as from the phone")
        // §82-7: من غير شكل معروف ولا «تم» ⇒ نفس التاريخ وبيستنى (ما بيتسجلش إلا لما المالك يضغط «سجّل»)
        val keyword = reader.paste("Purchase\nبـSR 30\nلدى:TEST SHOP", "2026-10-09").rows.single()
        assertEquals("2026-10-09" to SmsShape.KeywordFallback, keyword.date to keyword.shape)
    }

    @Test fun aMessageThatCarriesItsOwnDateKeepsIt() {
        val row = reader.paste("شراء\nبـSR 40\nلدى:TEST MART\n26/10/07", "2026-10-10").rows.single()
        assertEquals("2026-10-07", row.date, "the date written in the message wins over the paste date")
    }

    @Test fun withoutADateTheOldAppBehaviourStaysForTheGoldenFileOnly() {
        // ملف المرجع (`SmsFlowGoldenTest`) بيقرا من غير وقت وصول زي التطبيق القديم ⇒ الرسالة اللي مفيهاش تاريخ بتتعدّى
        val batch = reader.pasteWithoutDate("PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE")
        assertEquals(0 to 1, batch.rows.size to batch.skipped.size)
    }
}
