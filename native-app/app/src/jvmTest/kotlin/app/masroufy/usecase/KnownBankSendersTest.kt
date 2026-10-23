package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Wallet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * البنك اللي اختاره في أول تشغيل بيتسجّل لوحده بمرسلينه المعروفين (قرار المالك 2026-10-15 مساءً — OVERRIDES §91): ما حدش يكتب اسم مرسل
 * لبنك البرنامج عارفه. محافظ مخترعة (الأسماء والأرصدة) — مفاتيح البنوك من الدليل.
 */
class KnownBankSendersTest {
    private fun wallet(id: String, kind: String = "bank", name: String = "حساب تجربة", bankId: String? = null) =
        Wallet(id = id, name = name, currency = Currency.SAR, kind = kind, openingBalanceMinor = 12_345, openingAt = "2026-10-15", bankId = bankId)

    @Test fun theOnboardingBankGetsItsKnownSendersOnItsWallet() {
        val known = knownBankSenders(listOf(wallet("cash", kind = "cash"), wallet("w1", bankId = "sa-al-rajhi-bank")), "SA")
        assertEquals(listOf(KnownSender("AlRajhiBank", "w1")), known)
    }

    @Test fun anOldWalletNamedLikeTheBankIsKnownToo() {
        val known = knownBankSenders(listOf(wallet("w2", name = "مصرف الراجحي")), "SA")
        assertEquals(listOf("AlRajhiBank"), known.map { it.sender })
    }

    @Test fun twoWalletsOfOneBankLeaveTheChoiceToTheMessage() {
        val known = knownBankSenders(listOf(wallet("w1", bankId = "sa-al-rajhi-bank"), wallet("w2", bankId = "sa-al-rajhi-bank")), "SA")
        assertEquals(listOf(KnownSender("AlRajhiBank", null)), known, "مرسل واحد من غير محفظة — مفيش تخمين")
    }

    @Test fun cashAndUnknownBanksAreNotGuessed() {
        assertTrue(knownBankSenders(listOf(wallet("c", kind = "cash"), wallet("x", name = "بنك مخترع")), "SA").isEmpty())
        assertTrue(knownBankSenders(emptyList(), "SA").isEmpty())
    }

    @Test fun addingKnownSendersKeepsTheOldOnesAndTheLimit() {
        val known = listOf(KnownSender("AlRajhiBank", "w1"), KnownSender("NEWBANK", null))
        assertEquals(listOf("alrajhibank", "NEWBANK"), sendersWithKnown(listOf("alrajhibank"), known), "بدون تكرار بحالة الحروف")
        val full = (1..app.masroufy.core.MAX_SMS_SENDERS).map { "B$it" }
        assertEquals(full, sendersWithKnown(full, known), "الصندوق مليان ⇒ ما بيتشالش حد")
        assertEquals(listOf("AlRajhiBank", "NEWBANK"), sendersWithKnown(emptyList(), known))
    }

    /** OVERRIDES §112-7: حد المرسلين 25 لكل بلد في مكان واحد — كان 10 للجهاز كله. */
    @Test fun theLimitIsTwentyFivePerCountry() {
        assertEquals(25, app.masroufy.core.MAX_SMS_SENDERS_PER_COUNTRY)
        assertEquals(50, app.masroufy.core.MAX_SMS_SENDERS, "بلدين ليهم قارئ رسايل")
        val many = (1..29).map { "S$it" }
        assertEquals(30, sendersWithKnown(many, listOf(KnownSender("S30", null))).size, "أكتر من 10 بقى مسموح")
    }
}
