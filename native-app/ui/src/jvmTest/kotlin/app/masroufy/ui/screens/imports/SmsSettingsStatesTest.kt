package app.masroufy.ui.screens.imports

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.usecase.AddOperationResult
import app.masroufy.usecase.AutoRecordResult
import app.masroufy.usecase.InboxView
import app.masroufy.usecase.UnmappedSender
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * r041c — «إعداد القراءة» كانت بتفضل بيضا: بتستنى `overview` كامل على خيط الواجهة. دلوقتي [smsSettingsStates] بيدّي «بيحمّل» **الأول على
 * طول** (الإطار بيترسم) ثم آخر صورة محفوظة لو فيه ثم الجديدة — من `SmsDeps.settings` الخفيفة. **كل حاجة مخترعة.**
 */
class SmsSettingsStatesTest {
    @AfterTest fun reset() = Fx.resetTexts()

    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")

    private fun picture(waiting: Int) = SmsOverview(
        Fx.inbox(senders = listOf("TESTBANK", "OTHERBANK")), listOf(UnmappedSender("sa", "OTHERBANK", waiting)), emptyList(),
        mapOf("TESTBANK" to bank.id, "OTHERBANK" to null),
    )

    /** الصفحة بتطلب `settings` و`cachedSettings` بس — أي نداء تاني (زي `overview` التقيل) بيوقع الاختبار. */
    private class Fake(private val fresh: SmsOverview?, private val cached: SmsOverview? = null) : SmsDeps {
        var settingsCalls = 0
        override suspend fun settings(): SmsOverview {
            settingsCalls++
            return fresh ?: error("القراية وقعت")
        }
        override fun cachedSettings(): SmsOverview? = cached
        override suspend fun overview(record: Boolean): SmsOverview = error("الصفحة ما بقتش بتطلب overview")
        override suspend fun record(categories: Map<String, Id>, includeSimilar: Set<String>): Int = error("x")
        override suspend fun dismiss(messageIds: List<String>): Unit = error("x")
        override suspend fun chooseWallet(sender: String, walletId: Id?): AutoRecordResult = error("x")
        override suspend fun enable(senders: List<String>): InboxView = error("x")
        override suspend fun disable(): InboxView = error("x")
        override suspend fun remember(merchant: String, categoryId: Id, direction: Direction): Boolean = error("x")
        override suspend fun recordedToday(): List<Transaction> = error("x")
        override suspend fun recordByHand(messageId: String, sender: String, amountText: String): AddOperationResult = error("x")
    }

    @Test fun loadingIsEmittedBeforeTheData() = runBlocking<Unit> {
        val sms = Fake(picture(3))
        val states = smsSettingsStates(sms, "SA", emptyList()) { listOf(bank) }.toList()
        assertEquals(SmsSettingsState.Loading, states.first(), "الإطار بيترسم على طول")
        assertEquals(2, states.size)
        val ready = assertIs<SmsSettingsState.Ready>(states.last())
        assertEquals(listOf("بنك وهمي", null), ready.ui.senders.map { it.walletName })
        assertEquals(listOf(0, 3), ready.ui.senders.map { it.waiting })
        assertEquals(1, sms.settingsCalls)
    }

    @Test fun theLastPictureShowsRightAwayThenTheNewOne() = runBlocking<Unit> {
        val states = smsSettingsStates(Fake(picture(5), cached = picture(2)), "SA", emptyList()) { listOf(bank) }.toList()
        assertEquals(SmsSettingsState.Loading, states[0])
        assertEquals(listOf(2, 5), states.drop(1).map { assertIs<SmsSettingsState.Ready>(it).ui.senders.last().waiting })
    }

    @Test fun noReaderOrAFailedReadIsUnavailableAndWalletsFallBack() = runBlocking<Unit> {
        val none = smsSettingsStates(null, "SA", emptyList()) { listOf(bank) }.toList()
        assertFalse(assertIs<SmsSettingsState.Ready>(none.last()).ui.available)
        val failed = smsSettingsStates(Fake(null), "SA", listOf(bank)) { error("المحافظ وقعت") }.toList()
        val ready = assertIs<SmsSettingsState.Ready>(failed.last())
        assertFalse(ready.ui.available)
        assertEquals(listOf(bank), ready.wallets, "المحافظ اللي كانت عند الشاشة")
    }
}
