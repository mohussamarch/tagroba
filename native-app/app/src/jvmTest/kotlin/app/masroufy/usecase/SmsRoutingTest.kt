package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.core.ParsedRow
import app.masroufy.core.ReviewState
import app.masroufy.core.Wallet
import app.masroufy.core.addMoney
import app.masroufy.core.subtractMoney
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §75-11 (قرار المالك 2026-10-08): «بنك واحد بحسابين ⇒ التفرقة بآخر ٤ أرقام (وكمان معرفة «حسابي التاني» منها)». كل رسالة بتروح المحفظة
 * اللي أرقامها في الرسالة، والتحويل لحساب تاني من حسابات المالك بيبقى تحويل داخلي من غير سؤال. كل الأسامي والأرقام مخترعة.
 */
class SmsRoutingTest {
    private val a = Wallet("w-a", "بنك وهمي أ", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "1111")
    private val b = Wallet("w-b", "بنك وهمي ب", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "2222")

    private fun buy(amount: String, merchant: String, extra: String) = "PoS Purchase\nAmount: SAR $amount\n${extra}At: $merchant\nOn: 2026-10-07 10:00"

    @Test fun eachMessageGoesToTheWalletOfItsLastFourDigits() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, b))
        val w = SmsWorld(listOf(space)).enable()
        w.auto().chooseWallet("sa", "TESTBANK", a.id)
        w.receive(
            sms("toB", buy("200.00", "TEST GROCER", "Account: **2222\n")),
            sms("toA", buy("30.00", "TEST CAFE", "Account: **1111\n")),
            sms("none", buy("12.50", "TEST BAKERY", "")),
            sms("card", buy("7.25", "TEST KIOSK", "Card: *9999\n")),
        )
        val r = w.auto().run()
        assertEquals(4, r.recorded)
        assertTrue(r.waiting.isEmpty(), "ولا «حساب تاني من حساباتك» لرسالة ليها محفظة: ${r.waiting}")
        val where = space.all().associate { it.amountMinor to it.walletId }
        assertEquals(mapOf(20_000L to b.id, 3_000L to a.id, 1_250L to a.id, 725L to a.id), where)
        // رصيد كل محفظة من عملياتها هي بس (بالوحدة الصغرى)
        suspend fun net(walletId: String) = space.all().filter { it.walletId == walletId }
            .fold(0L) { sum, t -> if (t.observedDirection == Direction.IN) addMoney(sum, t.amountMinor) else subtractMoney(sum, t.amountMinor) }
        assertEquals(-20_000L, net(b.id))
        assertEquals(-4_975L, net(a.id))
    }

    @Test fun withoutAMappedWalletTheDigitsStillChooseAndTheRestWaitForTheOwner() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, b))
        val w = SmsWorld(listOf(space)).enable()
        w.receive(sms("toB", buy("200.00", "TEST GROCER", "Account: **2222\n")), sms("none", buy("12.50", "TEST BAKERY", "")))
        val r = w.auto().run()
        assertEquals(1, r.recorded)
        assertEquals(listOf("none"), r.waiting)
        assertEquals(listOf(UnmappedSender("sa", "testbank", 1)), r.unmappedSenders, "رسالة واحدة بس محتاجة اختيار المحفظة")
        assertEquals(b.id, space.all().single().walletId)
    }

    @Test fun aTransferToTheOwnersOtherAccountIsAnInternalTransferWithoutAQuestion() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, b))
        val w = SmsWorld(listOf(space)).enable()
        w.auto().chooseWallet("sa", "TESTBANK", a.id)
        w.receive(sms("own", "Debit Transfer Internal\nAmount: SAR 300.00\nTo: **2222\nFrom: **1111\nOn: 2026-10-07 11:15"))
        assertEquals(1, w.auto().run().recorded)
        val t = space.all().single()
        assertEquals(a.id, t.walletId, "الصادر من حساب أ")
        assertEquals(Triple(EconomicKind.INTERNAL_TRANSFER, true, ReviewState.CONFIRMED), Triple(t.economicKind, t.economicKindConfirmed, t.reviewState))
    }

    @Test fun aStatementLineWithTheSameCounterpartyIsUnchanged() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, b))
        val importer = ImportStatement(space.importDeps(listOf(OwnAccountByLast4Effect(space.wallets))))
        val row = ParsedRow(1, "2026-10-07", 30_000, Direction.OUT, "", null, "Statement", "حوالة صادرة TESTW-/TOACCT/****2222TO:", "line-1")
        val request = ImportRequest(
            fileName = "statement.csv", content = "line-1", accountIdentity = a.name, sourceType = ImportSourceType.CSV_PREVIEW, walletId = a.id,
            parsedRows = listOf(row),
        )
        importer.commit(request, importer.preview(request))
        val t = space.all().single()
        assertEquals("2222", app.masroufy.core.transferPartyOf(t)?.last4, "نفس الطرف (آخر 4 = حساب ب)")
        assertEquals(EconomicKind.UNCLASSIFIED to false, t.economicKind to t.economicKindConfirmed, "رسايل البنك بس — الكشف ليه زون التحويلات")
    }
}
