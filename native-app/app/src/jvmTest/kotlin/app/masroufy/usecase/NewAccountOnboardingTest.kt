package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.EGYPT_PACK
import app.masroufy.core.RealSeeds
import app.masroufy.core.SAUDI_PACK
import app.masroufy.core.UserProfile
import app.masroufy.core.Wallet
import app.masroufy.core.buildCountryCategoryTree
import app.masroufy.core.loadReferences
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * حساب جديد خالص (المحاكي 2026-10-10: كان بيفتح على رئيسية فاضية): الهيكل بيفتح أسئلة البداية، والانتهاء بيجهّز التصنيفات والقواعد
 * ومحفظة الكاش — و**حساب فيه بيانات ما بيتفتحلوش** حتى لو `onboardedAt` فاضي (حساب المالك المشترك مع التطبيق القديم).
 */
class NewAccountOnboardingTest {
    private val clock = FixedClock("2026-10-10T09:00:00.000Z")

    private class World(val onboard: OnboardAccount, val wallets: MemoryWalletRepository, val categories: MemoryCategoryRepository, val rules: MemoryRuleRepository)

    private fun world(wallets: List<Wallet> = emptyList(), profile: UserProfile? = null, seeded: Boolean = true, pack: app.masroufy.core.CountryPack = SAUDI_PACK): World {
        val w = MemoryWalletRepository(wallets)
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
        val seeding = if (seeded) NewAccountSeeding(categories, rules, MemoryReferenceSeed(categories, rules, MemoryMerchantRepository()), BundledSeeds, pack) else null
        val profiles = ManageProfile(ManageProfileDeps(MemoryProfileRepository(profile), MemoryAccount(), clock))
        return World(OnboardAccount(OnboardAccountDeps(profiles, people, w, clock, seeding)), w, categories, rules)
    }

    @Test fun aBrandNewAccountOpensOnboardingAndFinishingSeedsCategoriesAndCash() = runBlocking<Unit> {
        val w = world()
        assertTrue(w.onboard.shouldStart())
        val start = w.onboard.start()
        assertTrue(start.freshAccount)
        assertEquals(OnboardingResult.Ok, w.onboard.finish(OnboardingInput(start.profile, null, emptyList())))
        val expected = buildCountryCategoryTree(RealSeeds.tree, SAUDI_PACK).categories
        assertEquals(expected.map { it.id }.sorted(), w.categories.listAll().map { it.id }.sorted(), "شجرة السعودية كاملة")
        assertTrue(w.rules.listAll().isNotEmpty(), "قواعد التصنيف العامة اتزرعت")
        val cash = w.wallets.listAll().single()
        assertEquals(Wallet(CASH_WALLET_ID, SAUDI_PACK.cashWalletName, Currency.SAR, "cash", 0, "2026-10-10"), cash)
        assertFalse(w.onboard.shouldStart(), "بعد الانتهاء ما بيتفتحش تاني")
    }

    @Test fun anAccountWithDataNeverOpensOnboardingEvenWithoutOnboardedAt() = runBlocking<Unit> {
        // زي حساب المالك: محافظ موجودة من التطبيق القديم (أرقام مخترعة) و`onboardedAt` فاضي
        val old = world(listOf(Wallet("wallet-bank", "البنك", Currency.SAR, "bank", 123_456, "2025-01-01")))
        assertFalse(old.onboard.shouldStart())
        assertFalse(old.onboard.start().freshAccount)
    }

    @Test fun anotherCountrySpaceNeverOpensItAutomatically() = runBlocking<Unit> {
        assertFalse(world(seeded = false).onboard.shouldStart(), "من غير تجهيز (بلد غير الأساسية) ⇒ مفيش فتح تلقائي")
    }

    @Test fun egyptSeedsItsOwnTreeAndCashName() = runBlocking<Unit> {
        val w = world(pack = EGYPT_PACK)
        val start = w.onboard.start()
        assertEquals(OnboardingResult.Ok, w.onboard.finish(OnboardingInput(start.profile, null, emptyList())))
        assertEquals(buildCountryCategoryTree(RealSeeds.tree, EGYPT_PACK).categories.size, w.categories.listAll().size)
        assertEquals(Currency.EGP, w.wallets.listAll().single().currency)
    }

    /** النسخة المضمّنة في التطبيق = ملفات المستودع بالظبط (لو حد عدّل الشجرة أو القواعد ولد `BundledSeedJson.kt` تاني). */
    @Test fun bundledSeedsMatchTheRepositoryFiles() = runBlocking<Unit> {
        val source = BundledSeeds.load(SAUDI_PACK)
        val tree = buildCountryCategoryTree(RealSeeds.tree, SAUDI_PACK)
        assertEquals(tree.categories, source.categories)
        assertEquals(loadReferences(RealSeeds.rules, emptyList(), tree.categories, tree).rules, source.rules)
        assertTrue(source.merchants.isEmpty(), "التجار ما بيتزرعوش في البلد")
    }
}
