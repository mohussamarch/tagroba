package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** دليل البنوك (OVERRIDES §81) واقتراح البنك من المرسل (§74): المؤكد بس، والمش معروف بيفضل خام. */
class BankCatalogTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
    }

    @Test
    fun catalogueHasBothCountriesWithUniqueStableKeys() {
        assertEquals(48, BANKS.count { it.country == "SA" })
        assertEquals(51, BANKS.count { it.country == "EG" })
        assertEquals(BANKS.size, BANKS.map { it.key }.toSet().size)
        assertTrue(BANKS.all { it.key.matches(Regex("(sa|eg)-[a-z0-9-]+")) })
        assertEquals("مصرف الراجحي", bankByKey("sa-al-rajhi-bank")?.nameAr)
        assertNull(bankByKey("sa-not-a-bank"))
        assertNull(bankByKey(null))
    }

    @Test
    fun namesUseWesternDigitsOnlyAndNeverEmpty() {
        val eastern = Regex("[٠-٩۰-۹٪٫٬]")
        for (b in BANKS) {
            assertTrue(b.nameAr.isNotBlank() && b.nameEn.isNotBlank(), b.key)
            assertFalse(eastern.containsMatchIn(b.nameAr + b.words.joinToString()), b.key)
        }
        assertEquals("بنك دي 360", bankByKey("sa-d360-bank")?.nameAr)
    }

    @Test
    fun defaultListIsOperatingRetailOnly() {
        val saudi = banksIn("SA")
        assertTrue(saudi.all { it.retail })
        assertTrue(saudi.none { it.key == "sa-bank-of-china" || it.key == "sa-ez-bank" }, "شركات بس / لسه ما بدأش")
        assertTrue(banksIn("SA", all = true).any { it.key == "sa-bank-of-china" })
        assertTrue(banksIn("EG").none { it.key == "eg-instapay" || it.key == "eg-citibank-egypt" })
        assertTrue(banksIn("EG").any { it.key == "eg-hsbc-egypt" }, "أعلن البيع بس لسه شغال")
    }

    @Test
    fun filtersByWalletKind() {
        val wallets = banksIn("EG", walletKind = "digital_wallet")
        assertTrue(wallets.any { it.key == "eg-vodafone-cash" })
        assertTrue(wallets.none { it.walletKind == "bank" })
        assertTrue(banksIn("SA", walletKind = "bank").any { it.key == "sa-stc-bank" }, "البنك الرقمي حساب بنكي")
    }

    @Test
    fun searchMatchesArabicWithoutHamzaEnglishAndOldNames() {
        assertEquals("sa-alinma-bank", banksIn("SA", query = "انماء").first().key)
        assertEquals("sa-al-rajhi-bank", banksIn("SA", query = "rajhi").single().key)
        assertTrue(banksIn("SA", query = "ساب").any { it.key == "sa-saudi-awwal-bank" })
        assertTrue(banksIn("EG", query = "التجاري الدولي").any { it.key == "eg-cib-egypt" })
        assertTrue(banksIn("SA", query = "zzzz").isEmpty())
        assertEquals(banksIn("SA"), banksIn("SA", query = "  "))
    }

    @Test
    fun confirmedSendersMapToTheirBankAndUnknownStayRaw() {
        assertEquals("sa-al-rajhi-bank", bankForSender("AlRajhiBank", "SA")?.key)
        assertEquals("sa-al-rajhi-bank", bankForSender(" alrajhibank ", "SA")?.key)
        assertEquals("eg-national-bank-of-egypt", bankForSender("BanK-AlAhly", "EG")?.key)
        assertEquals("eg-national-bank-of-egypt", bankForSender("bank alahly", "EG")?.key)
        assertEquals("eg-vodafone-cash", bankForSender("VF-Cash", "EG")?.key)
        assertEquals("sa-stc-bank", bankForSender("STCBank")?.key, "من غير بلد: بنك واحد بس بالاسم ده")
        assertNull(bankForSender("CIB", "SA"), "اسم مرسل مصري في السعودية ما يتخمنش")
        assertNull(bankForSender("SNB-AlAhli", "SA"), "مش مؤكد في البحث ⇒ يفضل خام")
        assertNull(bankForSender("", "SA"))
        assertNull(bankForSender("RandomShop", "SA"))
    }

    @Test
    fun walletBankFromSavedKeyOrFromItsExactName() {
        fun w(name: String, kind: String = "bank", bankId: String? = null) = Wallet("w", name, Currency.SAR, kind, 0, "2026-01-01", null, bankId)
        assertEquals("sa-riyad-bank", bankForWallet(w("حسابي", bankId = "sa-riyad-bank"), "SA")?.key)
        assertEquals("sa-al-rajhi-bank", bankForWallet(w("الراجحي"), "SA")?.key)
        assertEquals("sa-al-rajhi-bank", bankForWallet(w("مصرف الراجحي - الراتب"), "SA")?.key)
        assertEquals("sa-saudi-national-bank", bankForWallet(w("الأهلي"), "SA")?.key)
        assertEquals("eg-national-bank-of-egypt", bankForWallet(w("البنك الأهلي", "bank"), "EG")?.key)
        assertNull(bankForWallet(w("البنك"), "SA"))
        assertNull(bankForWallet(w("الراجحي", kind = "cash"), "SA"))
        assertNull(bankForWallet(w("الراجحي"), null), "من غير بلد الاسم ما يتخمنش")
    }

    @Test
    fun labelFollowsLanguage() {
        val rajhi = bankByKey("sa-al-rajhi-bank")!!
        assertEquals("مصرف الراجحي", bankLabel(rajhi))
        Texts.language = Language.EN
        assertEquals("Al Rajhi Bank", bankLabel(rajhi))
    }

    @Test
    fun logosPointToBundledKeysAndCibHasNone() {
        assertNotNull(bankByKey("sa-al-rajhi-bank")?.logo)
        assertNull(bankByKey("eg-cib-egypt")?.logo, "موقع التجاري الدولي طلب تحقق «مش روبوت» — مفيش لوجو (§76)")
        assertTrue(BANKS.mapNotNull { it.logo }.all { it.matches(Regex("[a-z0-9_]+")) })
    }
}
