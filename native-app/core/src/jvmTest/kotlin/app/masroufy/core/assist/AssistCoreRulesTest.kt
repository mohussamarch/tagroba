package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * قواعد المساعد النقية (§78): التوحيد · المبالغ (وحدة صغرى، ولا `Double`) · الفترات (الأسبوع من السبت · اسم الشهر = اللي بيخلص فيه) ·
 * التقسيم · المحفظة · كلمات البذور على الشجرة الحقيقية. كله مخترع.
 */
class AssistCoreRulesTest {
    private fun money(text: String) = scanMoney(assistTokens(assistNormalize(text)))

    @Test
    fun normalizationFollowsTheDesignAndMore() {
        assertEquals("ادفع الزكاه", assistNormalize("أَدْفَعُ الزَّكَاة"))
        assertEquals("اسال عن الاياب", assistNormalize("إسأل عن الإيّاب"))
        assertEquals("مستشفي", assistNormalize("مستشفى"))
        assertEquals("مسوول بيي", assistNormalize("مسؤول بيئ"))
        assertEquals("كام", assistNormalize("كـــاااام"))
        assertEquals("قهوه 15.5", assistNormalize("قهوة ١٥٫٥"))
        assertEquals("1,250", assistNormalize("١٬٢٥٠"))
        assertEquals("123", assistNormalize("۱۲۳"))
        assertEquals("15 ريال", assistNormalize("15 ﷼"))
        assertEquals("coffee", assistNormalize("COFFEE"))
        assertEquals("الكهربا", assistNormalize("الكهرباء"))
    }

    @Test
    fun amountsAreIntegerMinorUnits() {
        val cases = mapOf(
            "١٥" to 1_500L, "15.5" to 1_550L, "١٥٫٥" to 1_550L, "١٥٫٠٥" to 1_505L, "1,250" to 125_000L, "١٬٢٥٠" to 125_000L, "1.500" to 150_000L,
            "ب15" to 1_500L, "٢ ألف" to 200_000L, "ألفين" to 200_000L, "خمسه وعشرين" to 2_500L, "ميتين" to 20_000L, "الف وخمسميه" to 150_000L,
            "50 هللة" to 50L, "١٢٫٧٥ ر.س" to 1_275L, "15ريال" to 1_500L,
        )
        for ((text, minor) in cases) assertEquals(listOf(minor), money(text).amounts.map { it.minor }, text)
    }

    @Test
    fun numbersThatAreNotAmountsAreSkipped() {
        for (text in listOf("آخر 7 أيام", "يوم ٢٨", "٨٠٪", "الساعة 5", "بعد 3 شهور", "الاتنين", "يوم التلات", "قسط 3 اقساط")) {
            assertTrue(money(text).amounts.isEmpty(), text)
        }
        assertEquals(3, money("نقسم على ٣").headCount)
        assertEquals(4, money("قسمها 4 اشخاص").headCount)
        assertTrue(money("قهوة 15.5555").invalid)
        assertTrue(money("قهوة 15.5555").amounts.isEmpty())
        assertEquals(listOf(1_500L, 2_000L), money("قهوة 15 و 20").distinct)
    }

    @Test
    fun currencyIsReadAndForeignIsHonest() {
        assertEquals(Currency.USD, money("15 دولار").currency)
        assertTrue(money("15 دولار").isForeign(Currency.SAR))
        assertTrue(money("١٥ دينار").isForeign(Currency.SAR))
        assertFalse(money("15 ريال").isForeign(Currency.SAR))
        assertTrue(money("15 ريال").isForeign(Currency.EGP))
        assertFalse(money("15 جنيه").isForeign(Currency.EGP))
        assertFalse(money("قهوة 15").isForeign(Currency.EGP))
        assertEquals(Currency.EGP, money("50 قرش").currency)
    }

    private fun period(text: String) = detectAssistPeriod(assistNormalize(text), assistTokens(assistNormalize(text)))

    @Test
    fun periodsResolveToTheSameFiscalMonthAsHome() {
        val today = "2026-10-09" // جمعة
        fun range(text: String) = resolveAssistRange(period(text), today, 28).let { it.from to it.to }
        assertEquals("2026-09-28" to "2026-10-27", range("صرفت كام الشهر ده"))
        assertEquals("2026-09-28" to "2026-10-27", range("صرفت كام"))
        assertEquals("2026-08-28" to "2026-09-27", range("الشهر اللي فات"))
        assertEquals("2026-10-03" to "2026-10-09", range("هذا الأسبوع"), "الأسبوع من السبت")
        assertEquals("2026-09-26" to "2026-10-02", range("الأسبوع الماضي"))
        assertEquals("2026-10-09" to "2026-10-09", range("النهارده"))
        assertEquals("2026-10-08" to "2026-10-08", range("امبارح"))
        assertEquals("2026-10-07" to "2026-10-07", range("أول امبارح"))
        assertEquals("2026-01-01" to "2026-10-09", range("من أول السنة"))
        assertEquals("2025-01-01" to "2025-12-31", range("السنة اللي فاتت"))
        assertEquals("2026-10-03" to "2026-10-09", range("آخر ٧ أيام"))
        assertEquals("2026-07-28" to "2026-10-09", range("آخر 3 شهور"))
        assertEquals("2026-09-28" to "2026-10-27", range("في أكتوبر"), "اسم الشهر = الشهر المالي اللي بيخلص فيه")
        assertEquals("2026-08-28" to "2026-09-27", range("سبتمبر"))
        assertEquals(AssistPeriodKind.NAMED_MONTH, period("كم صرفت في تشرين الاول")?.kind)
        assertNull(period("كم صرفت على القهوة"))
    }

    @Test
    fun spendDayFromTheText() {
        fun day(text: String) = detectSpendDayOffset(text, assistTokens(assistNormalize(text)))
        assertEquals(1, day("قهوة 15 امبارح"))
        assertEquals(2, day("قهوة 15 أول امبارح"))
        assertEquals(0, day("قهوة 15 اليوم"))
        assertEquals(-1, day("قهوة 15 بكرة"))
        assertNull(day("قهوة 15"))
        assertNull(day("أقدر أصرف كام في اليوم"), "«في اليوم» معدل مش يوم")
    }

    @Test
    fun splitSharesAreExactWithTheRemainderOnMe() {
        assertEquals(listOf(12_000L, 12_000L, 12_000L), splitShares(36_000, 3))
        assertEquals(listOf(3_334L, 3_333L, 3_333L), splitShares(10_000, 3))
        assertEquals(10_000L, splitShares(10_000, 3).sum())
        assertEquals(listOf(5_001L, 5_000L), splitShares(10_001, 2))
    }

    @Test
    fun walletOrderNamedThenBillThenMainThenOnly() {
        assertEquals("w-cash" to WalletSource.NAMED_IN_TEXT, resolveSpendWallet("w-cash", "w-bank", "w-bank"))
        assertEquals("w-bank" to WalletSource.RECURRING_BILL, resolveSpendWallet(null, "w-bank", "w-cash"))
        assertEquals("w-cash" to WalletSource.MAIN_WALLET, resolveSpendWallet(null, null, "w-cash"))
        assertEquals("w-only" to WalletSource.ONLY_WALLET, resolveSpendWallet(null, null, null, "w-only"))
        assertEquals(null to WalletSource.NONE, resolveSpendWallet(null, null, null))
    }

    @Test
    fun variationNeverRepeatsTheSameOpenerTwiceInARow() {
        val v = AssistVariants.RECORDED
        var prev: String? = null
        repeat(20) { n ->
            val picked = pickVariant(v, prev, 20_000, n)!!
            assertTrue(picked.key != prev, "نفس الصيغة ورا بعض")
            prev = picked.key
        }
        assertEquals(pickVariant(v, "REC_1", 5, 2), pickVariant(v, "REC_1", 5, 2), "من غير عشوائية")
        assertEquals("U1", pickVariant(listOf(Variant("U1", TextKey.ASSIST_UNKNOWN_1)), "U1", 1, 1)?.key, "صيغة واحدة بتتكرر")
        assertNull(pickVariant(emptyList(), null, 1, 1))
        assertTrue(isMorning(4) && isMorning(11) && !isMorning(12) && !isMorning(3))
        assertTrue(isLateNight(0) && isLateNight(3) && !isLateNight(4))
    }

    /** كل كلمة عامة في `AssistSeedWords` بتوصل لتصنيف موجود فعلًا في شجرة السعودية أو مصر الحقيقية (المعرّف ما بيتغيرش §66). */
    @Test
    fun everySeedWordPointsToARealCategory() {
        val ids = (buildCountryCategoryTree(RealSeeds.tree, SAUDI_PACK).categories + buildCountryCategoryTree(RealSeeds.tree, EGYPT_PACK).categories +
            GiftCategories.defaults()).map { it.id }.toSet()
        val broken = SEED_WORD_INDEX.filterValues { targets -> targets.none { it in ids } }
        assertTrue(broken.isEmpty(), "كلمات بتشاور على تصنيف مش موجود: $broken")
        assertTrue(SEED_WORD_INDEX.size >= 150, "كلمات البذور: ${SEED_WORD_INDEX.size}")
    }
}
