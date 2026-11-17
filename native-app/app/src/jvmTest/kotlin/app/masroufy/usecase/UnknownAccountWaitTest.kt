package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsRow
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.memory.MemoryWalletRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** OVERRIDES §120-9 — بيانات مخترعة: A آخره 2222 · B آخره 1111 · 3333 مش لأي محفظة. */
class UnknownAccountWaitTest {
    private val a = Wallet("wA", "بنك أ", Currency.SAR, "bank", 0, "2026-01-01", "2222", null)
    private val b = Wallet("wB", "بنك ب", Currency.SAR, "bank", 0, "2026-01-01", "1111", null)

    private fun purchase(account: String?, card: String? = null) =
        SmsRow(1, "2026-03-03", 5_000, Direction.OUT, "متجر", "SMS:r", "", "", "شراء", kind = SmsKind.PURCHASE, ownLast4 = card ?: account, accountLast4 = account)

    @Test
    fun accountDigitsMatchingNoWalletWaitForTheOwner() {
        val target = targetOf(a, listOf(a, b), false)
        assertEquals(TextKey.SMS_WAIT_UNKNOWN_ACCOUNT, waitReasonOf(purchase("3333"), target))
    }

    @Test
    fun ownOrKnownOrNotMineDigitsOrCardOnlyDigitsDoNotWaitForThisReason() {
        val target = targetOf(a, listOf(a, b), false)
        assertNull(waitReasonOf(purchase("2222"), target))
        assertNull(waitReasonOf(purchase(null), target))
        // أرقام كارت بس (مش سطر حساب) ⇒ زي الأول
        assertNull(waitReasonOf(purchase(null, card = "9999"), target))
        val notMine = targetOf(a.copy(notMineLast4 = listOf("3333")), listOf(a.copy(notMineLast4 = listOf("3333")), b), false)
        assertNull(waitReasonOf(purchase("3333"), notMine))
        // أرقام محفظة تانية من محافظك ⇒ سببها القديم («حساب تاني»)
        assertEquals(TextKey.SMS_WAIT_OTHER_ACCOUNT, waitReasonOf(purchase("1111"), target))
    }

    @Test
    fun walletWithoutDigitsAsksThenYesSavesThemAndNoKeepsTheOldWay() = runBlocking<Unit> {
        // §121-11: محفظة المرسل من غير أرقام + رقم حساب في الرسالة ⇒ «الحساب ده بتاعك؟»
        val bare = a.copy(accountLast4 = null)
        assertEquals(TextKey.SMS_WAIT_UNKNOWN_ACCOUNT, waitReasonOf(purchase("3333"), targetOf(bare, listOf(bare, b), false)))
        assertNull(waitReasonOf(purchase(null), targetOf(bare, listOf(bare, b), false)), "مفيش رقم حساب ⇒ زي الأول")
        assertNull(waitReasonOf(purchase(null, card = "9999"), targetOf(bare, listOf(bare, b), false)), "رقم كارت بس ⇒ زي الأول")
        // أيوه ⇒ الأرقام اتحفظت على المحفظة وما بيسألش تاني
        val repo = MemoryWalletRepository(listOf(bare, b))
        val saved = AnswerSmsAccount(repo).mine(bare.id, "3333")
        assertEquals("3333", saved.accountLast4)
        assertNull(waitReasonOf(purchase("3333"), targetOf(saved, repo.listAll(), false)))
        // لأ ⇒ «مش بتاعي» على المحفظة ⇒ بيتسجل فيها زي الأول من غير سؤال
        val repo2 = MemoryWalletRepository(listOf(bare, b))
        val declined = AnswerSmsAccount(repo2).notMine(bare.id, "3333")
        assertNull(declined.accountLast4)
        assertNull(waitReasonOf(purchase("3333"), targetOf(declined, repo2.listAll(), false)))
    }
}
