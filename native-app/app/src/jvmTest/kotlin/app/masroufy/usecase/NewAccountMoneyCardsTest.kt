package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.SAUDI_PACK
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryReferenceSeed
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.seed.BundledSeeds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * كارتين البداية للحساب الجديد (OVERRIDES §81): «معاك كام كاش؟» ⇒ رصيد افتتاح محفظة الكاش · «عندك حساب بنكي؟» ⇒ محفظة بنك من الدليل
 * (آخر 4 + رصيده) عن طريق `ManageWallets` — **كل حاجة بتتأكد قبل أي كتابة**، والإعادة بعد انقطاع ما بتكررش البنك. أرقام مخترعة.
 */
class NewAccountMoneyCardsTest {
    private val clock = FixedClock("2026-10-10T09:00:00.000Z")

    private class World(val onboard: OnboardAccount, val wallets: MemoryWalletRepository, val profiles: ManageProfile)

    private fun world(withBankQuestion: Boolean = true): World {
        val w = MemoryWalletRepository(emptyList())
        val categories = MemoryCategoryRepository()
        val rules = MemoryRuleRepository()
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val txns = MemoryTransactionRepository()
        val people = ManagePeople(
            ManagePeopleDeps(
                MemoryPersonRepository(), obligations, settlements, MemorySettlementWriter(obligations, settlements), MemoryAllocationRepository(),
                txns, MemoryUnitOfWork(listOf(txns)), SequentialIdGenerator(), clock,
            ),
        )
        val seeding = NewAccountSeeding(categories, rules, MemoryReferenceSeed(categories, rules, MemoryMerchantRepository()), BundledSeeds, SAUDI_PACK)
        val profiles = ManageProfile(ManageProfileDeps(MemoryProfileRepository(null), MemoryAccount(), clock))
        val manage = if (withBankQuestion) ManageWallets(w, SequentialIdGenerator(), clock, Currency.SAR) else null
        return World(OnboardAccount(OnboardAccountDeps(profiles, people, w, clock, seeding, manage)), w, profiles)
    }

    private val rajhi = AddWalletInput("bank", "مصرف الراجحي", "0042", 250_000, null, "sa-al-rajhi-bank")

    @Test fun cashAndBankFromTheTwoCardsAreSavedTogether() = runBlocking<Unit> {
        val w = world()
        val start = w.onboard.start()
        assertTrue(start.freshAccount)
        assertEquals(OnboardingResult.Ok, w.onboard.finish(OnboardingInput(start.profile, 75_050, emptyList(), rajhi)))
        val cash = w.wallets.listAll().single { it.kind == "cash" }
        assertEquals(75_050, cash.openingBalanceMinor)
        assertEquals("2026-10-10", cash.openingAt)
        val bank = w.wallets.listAll().single { it.kind == "bank" }
        assertEquals("sa-al-rajhi-bank", bank.bankId)
        assertEquals("0042", bank.accountLast4)
        assertEquals(250_000, bank.openingBalanceMinor)
        assertEquals("مصرف الراجحي", bank.name)
        assertTrue(!w.profiles.needsOnboarding(w.profiles.load()), "الانتهاء اتسجّل")
    }

    @Test fun skippingBothLeavesOnlyTheCashWalletAtZero() = runBlocking<Unit> {
        val w = world()
        assertEquals(OnboardingResult.Ok, w.onboard.finish(OnboardingInput(w.onboard.start().profile, null, emptyList(), null)))
        assertEquals(listOf(Wallet(CASH_WALLET_ID, SAUDI_PACK.cashWalletName, Currency.SAR, "cash", 0, "2026-10-10")), w.wallets.listAll())
    }

    @Test fun aBadBankStopsEverythingBeforeAnyWrite() = runBlocking<Unit> {
        val w = world()
        val bad = rajhi.copy(last4 = "12")
        val r = w.onboard.finish(OnboardingInput(w.onboard.start().profile, 10_000, emptyList(), bad))
        assertIs<OnboardingResult.Failed>(r)
        assertEquals("bank", r.step)
        assertTrue(w.wallets.listAll().isEmpty(), "ولا الكاش ولا التجهيز اتكتبوا")
        assertTrue(w.profiles.needsOnboarding(w.profiles.load()), "الأسئلة بترجع")
    }

    @Test fun retryingAfterAnInterruptionDoesNotAddTheBankTwice() = runBlocking<Unit> {
        val w = world()
        val profile = w.onboard.start().profile
        // انقطاع بعد ما البنك اتكتب وقبل تسجيل الانتهاء: البنك موجود والأسئلة لسه ما خلصتش
        w.wallets.save(Wallet("wallet-old", "مصرف الراجحي", Currency.SAR, "bank", 250_000, "2026-10-10", "0042", "sa-al-rajhi-bank"))
        assertEquals(OnboardingResult.Ok, w.onboard.finish(OnboardingInput(profile, null, emptyList(), rajhi)))
        assertEquals(1, w.wallets.listAll().count { it.bankId == "sa-al-rajhi-bank" })
    }

    @Test fun withoutTheBankQuestionWiredTheBankStepSaysSo() = runBlocking<Unit> {
        val w = world(withBankQuestion = false)
        val r = w.onboard.finish(OnboardingInput(w.onboard.start().profile, null, emptyList(), rajhi))
        assertIs<OnboardingResult.Failed>(r)
        assertEquals("bank", r.step)
        assertNull(w.wallets.listAll().firstOrNull { it.kind == "bank" })
    }
}
