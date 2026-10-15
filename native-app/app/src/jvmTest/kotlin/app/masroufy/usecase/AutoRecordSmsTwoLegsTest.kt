package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Wallet
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * مراجعة r04: «حوالة بين حساباتك» بين محفظتين من محافظ المالك في التسجيل التلقائي — الرِجل الأولى كانت بتشيل الرسالة من الصندوق، فالتانية
 * ما بتتسجلش أبدًا والمحفظة التانية تنقص بالمبلغ كله. بيانات مخترعة بس.
 */
class AutoRecordSmsTwoLegsTest {
    private val a = Wallet("wA", "بنك أ", Currency.SAR, "bank", 0, "2026-01-01", "2222", null)
    private val b = Wallet("wB", "بنك ب", Currency.SAR, "bank", 0, "2026-01-01", "1111", null)

    @Test fun aTransferBetweenTwoOwnWalletsRecordsBothLegs() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, b))
        val w = SmsWorld(listOf(space)).enable("AlRajhiBank")
        w.receive(sms("own", "حوالة بين حساباتك\nمن:1111\nالى:2222\nمبلغ:SAR 500.00\n26/10/07 11:15", sender = "AlRajhiBank"))

        val r = w.auto().run()
        assertEquals(2, r.recorded, "رجلين: خارج من ب وداخل في أ")
        assertEquals<Map<*, *>>(mapOf(b.id to Direction.OUT, a.id to Direction.IN), space.all().associate { it.walletId to it.observedDirection })
        assertEquals(listOf(50_000L, 50_000L), space.all().map { it.amountMinor })
        assertTrue(w.queued().isEmpty(), "الرسالة اتشالت بعد ما الرجلين اتسجلوا")
        assertEquals(0, w.auto().run().recorded, "تشغيلة تانية ما بتكررش")
    }
}
