package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Person
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySharedMerchantCatalog
import app.masroufy.memory.MemorySyncCursor
import app.masroufy.memory.MemoryTransferPartyRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OVERRIDES §80 · CLAUDE.md «الأمان» البند 6: `contribute` عمره ما يرفع اسم شخص من «الأشخاص» ولا اسم طرف تحويل (بأي قرار —
 * حتى «مش ده») للقايمة المشتركة، حتى لو العملية «شراء» والتصنيف من الشجرة. كل الأسامي مخترعة.
 */
class SharedMerchantsPersonGuardTest {
    private val catalog = MemorySharedMerchantCatalog()
    private val shared = SharedMerchants(
        SharedMerchantsDeps(
            catalog = catalog, merchants = MemoryMerchantRepository(), baseline = emptyList(), treeCategoryIds = setOf("cat-food"),
            cursor = MemorySyncCursor(), confirmedCursor = MemorySyncCursor(), clock = FixedClock("2026-10-01T10:00:00.000Z"),
            people = MemoryPersonRepository(listOf(Person("p-1", "سامي التجريبي"))),
            transferParties = MemoryTransferPartyRepository(
                listOf(
                    TransferParty("k-1", "SAMPLE OTHER", "4321", TransferVerdict.DISMISSED),
                    TransferParty("k-2", "TEST OWNER", "1111", TransferVerdict.OWN_ACCOUNT),
                ),
            ),
        ),
    )

    private suspend fun give(name: String) = shared.contribute(EconomicKind.PURCHASE, Direction.OUT, name, "cat-food")

    @Test fun peopleAndTransferPartiesNeverReachTheSharedList() = runBlocking<Unit> {
        assertEquals(ContributionResult.SKIPPED, give("سامي التجريبي"))
        assertEquals(ContributionResult.SKIPPED, give("STC PAY سامي التجريبي"))
        assertEquals(ContributionResult.SKIPPED, give("SAMPLE OTHER"))
        assertEquals(ContributionResult.SKIPPED, give("TEST OWNER"))
        assertEquals(emptyList(), catalog.listChangedSince(null).map { it.normalizedName })
    }

    @Test fun aRealShopIsStillShared() = runBlocking<Unit> {
        assertEquals(ContributionResult.SHARED, give("Test Coffee House"))
        assertEquals(listOf("TEST COFFEE HOUSE"), catalog.listChangedSince(null).map { it.normalizedName })
    }
}
