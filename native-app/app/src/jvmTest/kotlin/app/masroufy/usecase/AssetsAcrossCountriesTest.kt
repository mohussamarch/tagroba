package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.defaultSpace
import app.masroufy.core.emptyProfile
import app.masroufy.core.newSpaceFor
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * §111-6: أسئلة الملف (عربية · إيجار · عمالة) واحدة للحساب، والحاجة اللي بتضيفها بتحدد بلدها — عربية في مصر وعربية في السعودية مع بعض،
 * والاتنين بيظهروا في «الاستثمار» كل واحدة ببلدها وعملتها. أسامي مخترعة.
 */
class AssetsAcrossCountriesTest {
    private val clock = FixedClock("2026-10-23T09:00:00.000Z")
    private val egypt = newSpaceFor("EG", emptyList(), "2026-01-01T00:00:00Z")

    private fun country(space: app.masroufy.core.Space): CountryAssets {
        val repo = MemoryAssetRepository()
        return CountryAssets(space, ManageAssets(ManageAssetsDeps(repo, MemoryAssetLotRepository(), MemoryAssetSaleRepository(), MemoryAssetPriceRepository(), SequentialIdGenerator(), clock)), repo)
    }

    @Test fun aCarInEachCountryBothShowWithTheirOwnCurrency() = runBlocking<Unit> {
        val across = AssetsAcrossCountries(listOf(country(defaultSpace()), country(egypt)))
        across.add(DEFAULT_SPACE_ID, NewAsset("عربية وهمية", "other"))
        across.add(egypt.id, NewAsset("عربية وهمية", "other", currency = Currency.SAR))
        val all = across.list()
        assertEquals(listOf(DEFAULT_SPACE_ID to Currency.SAR, egypt.id to Currency.EGP), all.map { it.space.id to it.asset.currency }, "عملة البلد اللي اختارها، مش الشغالة")
    }

    @Test fun theProfileQuestionsStayOneForTheAccount() = runBlocking<Unit> {
        val account = MemoryProfileRepository(emptyProfile().copy(hasCar = true, renter = false, domesticWorker = true))
        val fromEgypt = SpaceProfileRepository(account, egypt.id).load()!!
        val fromSaudi = SpaceProfileRepository(account, DEFAULT_SPACE_ID).load()!!
        assertEquals(Triple(true, false, true), Triple(fromEgypt.hasCar, fromEgypt.renter, fromEgypt.domesticWorker))
        assertEquals(Triple(fromSaudi.hasCar, fromSaudi.renter, fromSaudi.domesticWorker), Triple(fromEgypt.hasCar, fromEgypt.renter, fromEgypt.domesticWorker))
    }
}
