package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * الجمعية في مصر = زي الدين ليك (رد المالك §62، فتوى 4399) — القبض جوه السنة سطر «اتحصّل» مرة واحدة على **فلوسك** بس.
 * كل الأسامي والمبالغ مخترعة.
 */
class ZakatRoscaTest {
    private fun e(id: String, kind: RoscaEntryKind, amount: Long, date: String) = RoscaEntry(id, "r-1", "t-$id", kind, amount) to date

    private val prices = ZakatPrices(goldPureGramMinor = 30_000, silverPureGramMinor = 350)

    private fun assess(country: ZakatCountry, holdings: List<ZakatHolding>) =
        assessZakat(country, Currency.SAR, "2026-02-18", zakatItems(country, holdings, prices), nisabMinor(country, prices), HawlState.Complete(true))

    @Test
    fun ownMoneyIsWhatYouPaidBeforeThePayoutAndNotYetReceived() {
        val entries = listOf(
            e("c1", RoscaEntryKind.CONTRIBUTION, 100_000, "2025-04-01"), e("c2", RoscaEntryKind.CONTRIBUTION, 100_000, "2025-05-01"),
            e("p1", RoscaEntryKind.PAYOUT, 1_000_000, "2025-06-01"),
            // نفس يوم القبض التاني: القسط اتدفع الأول ⇒ من فلوسك
            e("c3", RoscaEntryKind.CONTRIBUTION, 100_000, "2025-07-01"), e("p2", RoscaEntryKind.PAYOUT, 50_000, "2025-07-01"),
            e("c4", RoscaEntryKind.CONTRIBUTION, 100_000, "2025-08-01"),
        )
        assertEquals(
            listOf(RoscaOwnReceipt("p1", "2025-06-01", 200_000), RoscaOwnReceipt("p2", "2025-07-01", 50_000)),
            roscaOwnMoneyReceipts(entries.shuffled()),
        )
    }

    @Test
    fun aPayoutBeforeAnyPaymentHasNoOwnMoney() {
        val entries = listOf(e("p1", RoscaEntryKind.PAYOUT, 1_000_000, "2025-04-01"), e("c1", RoscaEntryKind.CONTRIBUTION, 100_000, "2025-05-01"))
        assertTrue(roscaOwnMoneyReceipts(entries).isEmpty(), "اللي قبضته الأول كله فلوس الناس")
        // القبض الأكبر من اللي دفعته: فلوسك بس — والقبض التاني ما بيعدّش نفس الفلوس تاني
        val twice = listOf(
            e("c1", RoscaEntryKind.CONTRIBUTION, 300_000, "2025-04-01"),
            e("p1", RoscaEntryKind.PAYOUT, 500_000, "2025-05-01"), e("p2", RoscaEntryKind.PAYOUT, 500_000, "2025-06-01"),
        )
        assertEquals(listOf(300_000L), roscaOwnMoneyReceipts(twice).map { it.ownMinor })
    }

    @Test
    fun egyptCollectedRoscaIsAOneTimeLineAndSaudiIgnoresIt() {
        val cash = ZakatHolding.Cash("w-1", "بنك", 4_000_000)
        val payout = ZakatHolding.CollectedRosca("e-p", "r-1", "جمعية وهمية", 300_000, "2025-09-15")
        val eg = assess(ZakatCountry.EG, listOf(cash, payout))
        assertEquals(ZakatLine(ZakatLineKind.COLLECTED_RECEIVABLES, 300_000, 7_500), eg.lines.single { it.kind == ZakatLineKind.COLLECTED_RECEIVABLES })
        assertEquals(107_500L, eg.dueMinor)
        assertEquals(ZakatTopic.RECEIVABLE_COLLECTED, eg.items.single { it.holding.id == "e-p" }.topic)
        assertEquals("4399", zakatRule(ZakatCountry.EG, ZakatTopic.RECEIVABLE_COLLECTED).source.fatwaNumber)
        val sa = assess(ZakatCountry.SA, listOf(cash, payout))
        assertTrue(sa.items.none { it.holding.id == "e-p" }, "السعودية: الجمعية بتتحسب كل سنة ⇒ القبض ما بيطلّعش حاجة")
        assertEquals(100_000L, sa.dueMinor)
        // فلوس القبض في المحفظة أصلًا ⇒ ما بتتضافش على سلسلة الحول
        val bank = Wallet("w-1", "بنك", Currency.SAR, "bank", 4_000_000, "2025-01-01")
        assertEquals(4_000_000L, balanceOn(zakatWealthSeries(listOf(bank), emptyList(), eg.items), "2025-12-01"))
    }
}
