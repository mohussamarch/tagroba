package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * الحول من سلسلة الرصيد + اقتراح الميعاد + التقويم الهجري (OVERRIDES §62). بيانات مخترعة.
 * التواريخ الهجري المتوقعة من تقويم أم القرى المنشور: 1 رمضان 1446 = 2025-03-01 · 1 رمضان 1447 = 2026-02-18 ·
 * 1 محرم 1447 = 2025-06-26 · 1 محرم 1448 = 2026-06-16 · 1 شوال 1447 (العيد) = 2026-03-20.
 * الملف ده في `commonTest` ⇒ بيشتغل على محاكي الآيفون كمان (NSCalendar) — نفس التواريخ لازم تطلع.
 */
class ZakatHawlTest {
    private val nisab = 208_250L

    private fun txn(id: String, date: String, dir: Direction, amount: Long, wallet: String = "w-bank", to: String? = null) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        walletId = wallet, transferToWalletId = to,
    )

    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 3_000_000, "2025-01-01")

    /** 30,000 ⇒ سحب 28,500 (1,500 تحت النصاب) ⇒ إيداع 41,500 (43,000). */
    private val dipTxns = listOf(
        txn("t-1", "2025-02-10", Direction.OUT, 2_850_000),
        txn("t-2", "2025-03-01", Direction.IN, 4_150_000),
    )

    private fun series(txns: List<Transaction> = dipTxns) = zakatWealthSeries(listOf(bank), txns, emptyList())

    @Test
    fun `رصيد المحافظ يوم معيّن — التحويل الداخلي ما بيضاعفش والمحفظة قبل افتتاحها مش معروفة`() {
        val cash = Wallet("w-cash", "كاش", Currency.SAR, "cash", 100_000, "2025-03-01")
        val txns = dipTxns + txn("t-3", "2025-04-01", Direction.OUT, 50_000, to = "w-cash")
        assertEquals(mapOf("w-bank" to 150_000L, "w-cash" to null), walletBalancesOn(listOf(bank, cash), txns, "2025-02-20"))
        assertEquals(mapOf("w-bank" to 4_250_000L, "w-cash" to 150_000L), walletBalancesOn(listOf(bank, cash), txns, "2025-04-01"))
        // المجموع في السلسلة: التحويل بين محفظتين صفر
        val s = zakatWealthSeries(listOf(bank, cash), txns, emptyList())
        assertEquals(4_400_000L, balanceOn(s, "2025-04-01"))
        assertEquals(4_400_000L, balanceOn(s, "2025-03-31"))
        assertNull(balanceOn(s, "2024-12-31"), "قبل أول افتتاح = مش معروف")
    }

    @Test
    fun `السعودية — النزول تحت النصاب بيبدأ الحول من جديد من يوم الرجوع`() {
        assertEquals(HawlState.Restarted("2025-03-01"), checkHawl(ZakatCountry.SA, series(), nisab, "2025-01-01", "2025-12-21"))
        // من بعد الرجوع: كمّل
        assertEquals(HawlState.Complete(true), checkHawl(ZakatCountry.SA, series(), nisab, "2025-03-01", "2026-02-18"))
        // لسه تحت النصاب لحد آخر السنة ⇒ بدأ من جديد ومفيش يوم رجوع
        assertEquals(HawlState.Restarted(null), checkHawl(ZakatCountry.SA, series(dipTxns.take(1)), nisab, "2025-01-01", "2025-12-21"))
        // بداية قبل أول البيانات ⇒ كمّل بس مش متأكد
        assertEquals(HawlState.Complete(false), checkHawl(ZakatCountry.SA, series(emptyList()), nisab, "2024-06-01", "2025-05-20"))
    }

    @Test
    fun `مصر — اليوم الثابت ومفيش فحص للنزول`() {
        assertEquals(HawlState.Complete(true), checkHawl(ZakatCountry.EG, series(), nisab, "2025-01-01", "2025-12-21"))
    }

    @Test
    fun `الأصول غير الكاش بتدخل السلسلة من يوم ما اتملكت`() {
        val gold = ZakatItem(
            ZakatHolding.Metal("a-1", "سبيكة", ZakatMetal.GOLD, 10 * QUANTITY_SCALE, 24, null, ZakatPurpose.SAVING, null, "2025-02-01"),
            ZakatLineKind.GOLD, ZakatTopic.SAVED_METAL, ZakatItemStatus.COUNTED, 300_000, 300_000,
        )
        val worn = gold.copy(status = ZakatItemStatus.EXEMPT, zakatableMinor = 0)
        val s = zakatWealthSeries(listOf(bank), dipTxns, listOf(gold, worn))
        assertEquals(450_000L, balanceOn(s, "2025-02-10"), "1,500 كاش + 3,000 دهب")
        // الدهب شال الرصيد فوق النصاب ⇒ مفيش نزول
        assertEquals(HawlState.Complete(true), checkHawl(ZakatCountry.SA, s, 400_000, "2025-02-01", "2025-12-21"))
    }

    @Test
    fun `اقتراح الميعاد — السعودية من آخر رجوع ومصر من أول يوم`() {
        assertEquals("2025-03-01", suggestHawlStart(ZakatCountry.SA, series(), nisab))
        assertEquals("2025-01-01", suggestHawlStart(ZakatCountry.EG, series(), nisab))
        assertNull(suggestHawlStart(ZakatCountry.SA, series(dipTxns.take(1)), nisab), "تحت النصاب دلوقتي ⇒ مفيش اقتراح")
        assertEquals("2025-01-01", suggestHawlStart(ZakatCountry.SA, series(emptyList()), nisab), "عمره ما نزل ⇒ من أول البيانات")
        assertNull(suggestHawlStart(ZakatCountry.SA, emptyList(), nisab))
    }

    @Test
    fun `الهجري — أم القرى`() {
        assertEquals(HijriDate(1447, 9, 1), hijriOf("2026-02-18"))
        assertEquals(HijriDate(1447, 1, 1), hijriOf("2025-06-26"))
        assertEquals(HijriDate(1447, 10, 1), hijriOf("2026-03-20"))
        assertEquals(HijriDate(1446, 9, 1), hijriOf("2025-03-01"))
    }

    @Test
    fun `ميعاد الزكاة الجاي — نفس اليوم الهجري السنة الجاية`() {
        assertEquals("2026-02-18", nextZakatDate("2025-03-01"))
        assertEquals("2026-06-16", nextZakatDate("2025-06-26"))
        assertEquals("2027-02-08", nextZakatDate("2026-02-18"))
        // 30 صفر 1446 ⇒ صفر 1447 فيه 29 يوم ⇒ آخر الشهر
        assertEquals(HijriDate(1446, 2, 30), hijriOf("2024-09-03"))
        assertEquals("2025-08-23", nextZakatDate("2024-09-03"))
        assertEquals(HijriDate(1447, 2, 29), hijriOf("2025-08-23"))
        assertFailsWith<ZakatError> { nextZakatDate("2025-02-30") }
    }

    @Test
    fun `حالة دفع السنة — جزئي وكله وصدقة زيادة`() {
        val year = ZakatYear(
            "2026-02-18", "2025-03-01", "2026-02-18", Currency.SAR, "x", "y", 208_250,
            listOf(ZakatYearLine(ZakatLineKind.CASH, 4_000_000, 100_000), ZakatYearLine(ZakatLineKind.GOLD, 2_625_000, 65_625), ZakatYearLine(ZakatLineKind.RECEIVABLES, 300_000, 7_500)),
        )
        fun pay(id: String, amount: Long, lines: List<ZakatLineKind>, at: String = "2026-02-19") = ZakatPayment(id, year.id, "t-$id", amount, lines, at, "c-$id")
        val none = zakatYearStatus(year, emptyList())
        assertEquals(Triple(173_125L, 0L, 173_125L), Triple(none.dueMinor, none.paidMinor, none.remainingMinor))
        // دفعة على الكاش والدهب أقل من المطلوب ⇒ الكاش الأول (ترتيب السطور) والدهب فاضله
        val partial = zakatYearStatus(year, listOf(pay("1", 120_000, listOf(ZakatLineKind.GOLD, ZakatLineKind.CASH))))
        assertEquals(listOf(true, false, false), partial.lines.map { it.paid })
        assertEquals(45_625L, partial.lines[1].remainingMinor)
        assertEquals(53_125L, partial.remainingMinor)
        // دفعة أكبر من الباقي ⇒ الباقي صفر والزيادة «صدقة زيادة» (مش بتتنقل للسطر اللي ما اتختارش)
        val over = zakatYearStatus(year, listOf(pay("1", 120_000, listOf(ZakatLineKind.CASH, ZakatLineKind.GOLD)), pay("2", 50_000, listOf(ZakatLineKind.GOLD))))
        assertEquals(4_375L, over.extraCharityMinor)
        assertEquals(7_500L, over.remainingMinor, "سطر الديون ما اتختارش ⇒ فاضل")
        assertEquals(165_625L, over.paidMinor)
        // دفعة سنة تانية ما بتتحسبش هنا
        assertEquals(0L, zakatYearStatus(year, listOf(pay("3", 10_000, listOf(ZakatLineKind.CASH)).copy(yearId = "تانية"))).paidMinor)
    }

    @Test
    fun `جدول القواعد — كل نقطة ليها قاعدة واحدة لكل بلد ومصدر`() {
        for (c in ZakatCountry.entries) for (t in ZakatTopic.entries) assertEquals(1, ZAKAT_RULES.count { it.country == c && it.topic == t }, "$c $t")
        for (r in ZAKAT_RULES) {
            assertEquals(true, r.source.url.startsWith("https://"), r.toString())
            assertEquals("2026-10-04", r.source.checkedOn)
            assertEquals(true, r.ruling.isNotBlank() && r.source.document.isNotBlank())
        }
        assertEquals(ZakatAuthority.ZATCA, zakatRule(ZakatCountry.SA, ZakatTopic.NISAB).source.authority)
        assertEquals("11653", zakatRule(ZakatCountry.EG, ZakatTopic.NISAB).source.fatwaNumber)
        assertEquals(ZakatEffect.NO_RULING, zakatRule(ZakatCountry.SA, ZakatTopic.PENSION).effect)
        assertEquals(ZakatEffect.NO_RULING, zakatRule(ZakatCountry.EG, ZakatTopic.CRYPTO).effect)
        assertEquals(ZakatCountry.SA, ZakatCountry.of("sa"))
        assertNull(ZakatCountry.of("AE"))
    }

    @Test
    fun `المحتوى الإسلامي ظاهر من الأول ويتقفل`() {
        assertEquals(true, zakatVisible(null))
        assertEquals(true, zakatVisible(emptyProfile()))
        assertEquals(false, zakatVisible(emptyProfile().copy(islamicContentVisible = false)))
        assertEquals(false, parseStoredProfile(mapOf("islamicContentVisible" to false)).islamicContentVisible)
        assertNull(parseStoredProfile(mapOf("islamicContentVisible" to "لأ")).islamicContentVisible)
        checkBackupProfile(mapOf("payday" to 28L, "islamicContentVisible" to true))
        assertFailsWith<BackupError> { checkBackupProfile(mapOf("payday" to 28L, "islamicContentVisible" to "yes")) }
    }
}
