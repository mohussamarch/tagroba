package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.EGYPT_TIME_ZONE
import app.masroufy.core.SAUDI_TIME_ZONE
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** «أضف محفظة» (الحساب الجديد بيوصل هنا بعد أسئلة البداية): التأكد قبل الكتابة، وآخر 4 أرقام بس. أسماء وأرقام مخترعة. */
class ManageWalletsTest {
    private val repo = MemoryWalletRepository(listOf(Wallet("wallet-cash", "كاش", Currency.SAR, "cash", 0, "2026-10-10")))
    private val manage = ManageWallets(repo, SequentialIdGenerator(), FixedClock("2026-10-10T09:00:00.000Z"), Currency.SAR)

    @Test fun addsABankWalletWithItsOpeningAndLastFourOnly() = runBlocking<Unit> {
        val w = manage.add(AddWalletInput("bank", "  بنك   وهمي ", "٤٢١٧", 125_050, "2026-10-01"))
        assertEquals("بنك وهمي", w.name)
        assertEquals("4217", w.accountLast4, "الأرقام العربية بتتحول، وآخر 4 بس")
        assertEquals(125_050, w.openingBalanceMinor)
        assertEquals(Currency.SAR, w.currency)
        assertEquals(w, repo.findById(w.id))
    }

    @Test fun noAmountMeansZeroFromToday() = runBlocking<Unit> {
        val w = manage.add(AddWalletInput("digital_wallet", "محفظة رقمية", null, null, "2026-01-01"))
        assertEquals(0, w.openingBalanceMinor)
        assertEquals("2026-10-10", w.openingAt)
        assertNull(manage.add(AddWalletInput("cash", "كاش السفر", "1234", null, null)).accountLast4, "الكاش من غير أرقام حساب")
    }

    @Test fun refusesBeforeWriting() = runBlocking<Unit> {
        assertFailsWith<WalletError> { manage.add(AddWalletInput("bank", "  ", null, null, null)) }
        assertFailsWith<WalletError> { manage.add(AddWalletInput("cash", "كاش", null, null, null)) }
        assertFailsWith<WalletError> { manage.add(AddWalletInput("bank", "بنك", "123", null, null)) }
        assertFailsWith<WalletError> { manage.add(AddWalletInput("bank", "بنك", null, -1, "2026-10-01")) }
        assertFailsWith<WalletError> { manage.add(AddWalletInput("bank", "بنك", null, 100, "2026-10-11")) }
        assertEquals(1, repo.listAll().size, "ولا محفظة اتكتبت")
    }

    @Test fun openingChangesOnlyThatWallet() = runBlocking<Unit> {
        val updated = manage.setOpening("wallet-cash", 100_000, "2026-09-06")
        assertEquals(100_000, updated.openingBalanceMinor)
        assertEquals("2026-09-06", repo.findById("wallet-cash")!!.openingAt)
        assertFailsWith<WalletError> { manage.setOpening("wallet-cash", 5, "2027-01-01") }
    }

    @Test fun keepsTheBankPickedFromTheCatalogue() = runBlocking<Unit> {
        val w = manage.add(AddWalletInput("bank", "مصرف الراجحي", "0042", 50_000, null, "sa-al-rajhi-bank"))
        assertEquals("sa-al-rajhi-bank", repo.findById(w.id)?.bankId)
        assertEquals("2026-10-10", w.openingAt, "رصيد من غير تاريخ = النهارده")
        assertNull(manage.add(AddWalletInput("cash", "كاش الشغل", null, null, null, "sa-al-rajhi-bank")).bankId, "الكاش ما لوش بنك")
        assertNull(manage.add(AddWalletInput("bank", "بنك تاني", null, null, null, null)).bankId, "«بنك تاني» باسم بيتكتب")
        assertFailsWith<WalletError> { manage.add(AddWalletInput("bank", "بنك مش موجود", null, null, null, "sa-not-in-catalogue")) }
    }

    @Test fun prepareChecksEverythingWithoutWriting() = runBlocking<Unit> {
        val w = manage.prepare(AddWalletInput("bank", "بنك الرياض", "1234", 10_000, "2026-10-01", "sa-riyad-bank"))
        assertEquals("sa-riyad-bank", w.bankId)
        assertEquals(1, repo.listAll().size, "ما اتكتبش حاجة")
        assertFailsWith<WalletError> { manage.prepare(AddWalletInput("bank", "كاش", null, null, null)) }
    }

    /** جولة 0.3: «النهارده» = يوم البلد. 00:30 بالرياض يوم جرينتش لسه امبارح — محفظة بتاريخ النهارده كانت بتترفض «بعد النهارده». */
    @Test fun todayIsTheCountryDayNotTheUtcDay() = runBlocking<Unit> {
        val riyadh = ManageWallets(MemoryWalletRepository(), SequentialIdGenerator(), FixedClock("2026-10-10T21:30:00.000Z"), Currency.SAR, SAUDI_TIME_ZONE)
        assertEquals("2026-10-11", riyadh.today(), "00:30 الرياض")
        assertEquals("2026-10-11", riyadh.add(AddWalletInput("bank", "بنك وهمي", null, 10_000, "2026-10-11")).openingAt)
        assertEquals("2026-10-11", riyadh.add(AddWalletInput("cash", "كاش البيت", null, null, null)).openingAt, "من غير مبلغ = النهارده في البلد")
        assertEquals("2026-10-11", riyadh.setOpening(riyadh.add(AddWalletInput("cash", "كاش", null, null, null)).id, 5, "2026-10-11").openingAt)
        assertFailsWith<WalletError> { riyadh.add(AddWalletInput("bank", "بنك بكرة", null, 10_000, "2026-10-12")) }

        // 23:30 بالقاهرة في الشتا (+2) = 00:30 بالرياض: مصر لسه في يومها، فبكرة بتاع الرياض «بعد النهارده» هناك
        val cairo = ManageWallets(MemoryWalletRepository(), SequentialIdGenerator(), FixedClock("2026-01-15T21:30:00.000Z"), Currency.EGP, EGYPT_TIME_ZONE)
        assertEquals("2026-01-15", cairo.today(), "23:30 القاهرة")
        assertEquals("2026-01-15", cairo.add(AddWalletInput("cash", "كاش", null, 50_000, null)).openingAt)
        assertFailsWith<WalletError> { cairo.add(AddWalletInput("bank", "بنك وهمي", null, 10_000, "2026-01-16")) }
        // الصيف (+3): 23:30 بالقاهرة = 20:30 جرينتش — نفس اليوم
        val summer = ManageWallets(MemoryWalletRepository(), SequentialIdGenerator(), FixedClock("2026-07-15T20:30:00.000Z"), Currency.EGP, EGYPT_TIME_ZONE)
        assertEquals("2026-07-15", summer.today())
    }
}
