package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Wallet
import app.masroufy.port.smsSenderKey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * مراجعة r06 sms-1: «حوالة بين حساباتك» ورِجل منها محفظتها مش من حق المرسل ده (بنك تاني) أو مش حساب بنك (محفظة رقمية) — الرِجل دي كانت
 * بتقع على محفظة المرسل بنفس رقم السطر، فالرجلين بيكتبوا فوق بعض والمحفظة تتغير بالغلط. دلوقتي: الرِجل اللي ليها محفظة بتتسجل، والتانية
 * بتستنى «حساب تاني من حساباتك» لوحدها. بيانات مخترعة بس.
 */
class AutoRecordSmsUnroutedLegTest {
    private val b = Wallet("wB", "بنك ب", Currency.SAR, "bank", 0, "2026-01-01", "1111", null)
    private val message = "حوالة بين حساباتك\nمن:1111\nالى:2222\nمبلغ:SAR 500.00\n26/10/07 11:15"

    private suspend fun recordWith(other: Wallet, otherSender: String?): List<Pair<String?, Direction>> {
        val space = SmsSpace(wallets = listOf(CASH, b, other))
        val w = SmsWorld(listOf(space)).enable("AlRajhiBank")
        w.memory.setSenderWallet(space.spaceId, smsSenderKey("AlRajhiBank"), b.id)
        if (otherSender != null) w.memory.setSenderWallet(space.spaceId, smsSenderKey(otherSender), other.id)
        w.receive(sms("own", message, sender = "AlRajhiBank"))
        w.auto().run()
        return space.all().map { it.walletId to it.observedDirection }
    }

    @Test fun crossBankLegWaitsAndTheSenderWalletOnlyLosesTheAmount() = runBlocking<Unit> {
        val a = Wallet("wA", "بنك أ", Currency.SAR, "bank", 0, "2026-01-01", "2222", null)
        assertEquals(listOf<Pair<String?, Direction>>(b.id to Direction.OUT), recordWith(a, otherSender = "OTHERBANK"))
    }

    @Test fun digitalWalletLegWaitsAndTheSenderWalletOnlyLosesTheAmount() = runBlocking<Unit> {
        val d = Wallet("wD", "محفظة رقمية", Currency.SAR, "digital_wallet", 0, "2026-01-01", "2222", null)
        assertEquals(listOf<Pair<String?, Direction>>(b.id to Direction.OUT), recordWith(d, otherSender = null))
    }
}
