package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * قارئ كشف QNB مصر على صفحة **مخترعة بنفس الشكل** (الأعمدة والإحداثيات زي الكشف الحقيقي — الحقيقي بيتجرب على جهاز المالك بس).
 * كل أرقام الحسابات والأسماء هنا مخترعة.
 */
class QnbPdfTest {
    private fun w(x: Double, y: Double, text: String) = PositionedWord(x, y, text)

    private val header = listOf(
        w(21.0, 769.0, "Date"), w(111.0, 769.0, "Description"), w(439.0, 769.0, "Debit"), w(553.0, 769.0, "Credit"), w(667.0, 769.0, "Balance"),
        w(21.0, 797.0, "1234567890123"),
    )

    private val page = PdfPage(
        1,
        header + listOf(
            w(21.0, 749.0, "2026-01-01"), w(111.0, 749.0, "BALANCE BROUGHT FORWARD----2026-01-01"), w(542.0, 749.0, "-"), w(621.0, 749.0, "1000.00"), w(735.0, 749.0, "1000.00"),
            // تحويل وارد: الوصف فوق وتحت السطر، وفيه رقم مرجع طويل لازم يتقص
            w(111.0, 731.0, "IPN TRANSFER-VC123456789-IPNTEST_PERSON1a2b3cACC -2026-01-"),
            w(21.0, 727.0, "2026-01-02"), w(542.0, 727.0, "-"), w(634.0, 727.0, "250"), w(735.0, 727.0, "1250.00"),
            w(111.0, 722.0, "02"),
            // شراء بالكارت: المدين بالسالب
            w(111.0, 706.0, "CARD PURCHASE-DB987654321-CARD PURCHASE 03/01/26 10:15-SAMPLE"),
            w(21.0, 702.0, "2026-01-03"), w(519.0, 702.0, "-41.25"), w(656.0, 702.0, "-"), w(735.0, 702.0, "1208.75"),
            w(111.0, 697.0, "STORE FUEL12345 CAIRO 1234-2026-01-03"),
            w(21.0, 680.0, "2026-01-03"), w(111.0, 680.0, "BALANCE CARRIED FORWARD----2026-01-03"), w(542.0, 680.0, "-"), w(626.0, 680.0, "1208.75"), w(735.0, 680.0, "1208.75"),
        ),
    )

    @Test fun readsRowsBalancesAndMerchants() {
        val out = parseQnbPdf(listOf(page))
        assertTrue(out.errors.isEmpty(), out.errors.toString())
        assertEquals(100_000, out.openingBalanceMinor)
        assertEquals(120_875, out.closingBalanceMinor)
        assertEquals(2, out.rows.size, "سطرين «BALANCE … FORWARD» مش عمليات")
        val (incoming, card) = out.rows
        assertEquals(Direction.IN to 25_000L, incoming.direction to incoming.amountMinor)
        assertEquals(125_000, incoming.statedBalanceMinor)
        assertEquals("IPN TRANSFER", incoming.sourceOperationType)
        assertEquals(Direction.OUT to 4_125L, card.direction to card.amountMinor)
        assertEquals("SAMPLE STORE FUEL", card.merchantName, "كود الفرع والمدينة مش من اسم التاجر")
        assertEquals("2026-01-03", card.date)
    }

    @Test fun neverReturnsAFullNumber() {
        val longDigits = Regex("\\d{5,}")
        for (r in parseQnbPdf(listOf(page)).rows) {
            for (text in listOf(r.description, r.raw, r.merchantName)) assertTrue(!longDigits.containsMatchIn(text), text)
        }
        // آخر 4 أرقام بس بيفضلوا عشان المستخدم يتعرف على المرجع
        assertTrue("••••6789" in parseQnbPdf(listOf(page)).rows.first().description)
    }

    @Test fun eachReaderRefusesTheOtherBank() {
        assertTrue(parseAlrajhiPdf(listOf(page)).rows.isEmpty())
    }
}
