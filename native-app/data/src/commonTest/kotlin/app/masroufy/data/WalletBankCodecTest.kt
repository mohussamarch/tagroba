package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.Wallet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** بنك المحفظة من الدليل (`bankId` — OVERRIDES §81) بيتكتب ويتقرا، والمحفظة القديمة من غيره ما بيتكتبلهاش حقل فاضي. */
class WalletBankCodecTest {
    @Test
    fun bankIdIsStoredOnlyWhenPicked() {
        val picked = Wallet("w-1", "مصرف الراجحي", Currency.SAR, "bank", 10_000, "2026-10-10", "0042", "sa-al-rajhi-bank")
        val doc = ReferenceCodecs.wallets.toStore(picked)
        assertEquals("sa-al-rajhi-bank", doc["bankId"])
        assertEquals(picked, ReferenceCodecs.wallets.decode(doc))
        val old = picked.copy(bankId = null)
        val oldDoc = ReferenceCodecs.wallets.toStore(old)
        assertFalse(oldDoc.containsKey("bankId"))
        assertEquals(old, ReferenceCodecs.wallets.decode(oldDoc))
    }
}
