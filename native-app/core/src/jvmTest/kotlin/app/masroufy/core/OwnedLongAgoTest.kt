package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** §121-6 (الحاجات القديمة «من زمان») و§121-7 (سؤال الحول) — بيانات مخترعة. */
class OwnedLongAgoTest {
    private val g = QUANTITY_SCALE
    private fun asset(id: String, holdingType: String? = "gold", asked: Boolean? = null) =
        Asset(id, "حاجة وهمية", "gold", "جرام", Currency.SAR, false, holdingType = holdingType, ownedSinceAsked = asked)

    @Test fun legacyHoldingOpeningLotReadsLongAgo() {
        val lots = listOf(AssetLot("l1", "a", "2026-01-10", g, 100, 0), AssetLot("l2", "a", "2026-02-01", g, 100, 0))
        val out = withLegacyOwnedLongAgo(listOf(asset("a")), lots)
        assertEquals(listOf(OWNED_LONG_AGO, "2026-02-01"), out.map { it.purchasedAt })
    }

    @Test fun newHoldingsPlainAssetsAndLinkedLotsStayAsTheyAre() {
        val lots = listOf(
            AssetLot("l1", "asked", "2024-05-01", g, 100, 0),
            AssetLot("l2", "plain", "2024-05-01", g, 100, 0),
            AssetLot("l3", "linked", "2024-05-01", g, 100, 0, transactionId = "t1"),
        )
        val assets = listOf(asset("asked", asked = true), asset("plain", holdingType = null), asset("linked"))
        assertEquals(lots, withLegacyOwnedLongAgo(assets, lots))
    }

    private fun metal(longAgo: Boolean, hawl: Boolean?, purpose: ZakatPurpose = ZakatPurpose.SAVING) =
        ZakatHolding.Metal("a", "دهب وهمي", ZakatMetal.GOLD, 100 * g, 21, null, purpose, null, OWNED_LONG_AGO, longAgo, hawl)

    private val prices = ZakatPrices(goldPureGramMinor = 30_000, silverPureGramMinor = 350)

    @Test fun longAgoMetalWaitsForTheHawlAnswer() {
        val item = zakatItems(ZakatCountry.SA, listOf(metal(true, null)), prices).single()
        assertEquals(ZakatItemStatus.NEEDS_FACT, item.status)
        assertEquals(ZAKAT_FACT_HAWL, item.missingFact)
        assertNull(item.zakatableMinor, "مش رقم مؤكد لحد الإجابة")
    }

    @Test fun answeredOrRecentOrExemptIsNotAsked() {
        assertEquals(ZakatItemStatus.COUNTED, zakatItems(ZakatCountry.SA, listOf(metal(true, true)), prices).single().status)
        assertEquals(ZakatItemStatus.COUNTED, zakatItems(ZakatCountry.SA, listOf(metal(true, false)), prices).single().status)
        assertEquals(ZakatItemStatus.COUNTED, zakatItems(ZakatCountry.SA, listOf(metal(false, null)), prices).single().status)
        val worn = zakatItems(ZakatCountry.SA, listOf(metal(true, null, ZakatPurpose.WEAR)), prices).single()
        assertEquals(null, worn.missingFact, "اللبس معفي ⇒ السؤال ملوش لازمة")
    }

    @Test fun heldSinceFollowsTheAnswer() {
        assertEquals(OWNED_LONG_AGO, longAgoHeldSince(OWNED_LONG_AGO, true, "2026-02-18"))
        assertEquals(OWNED_LONG_AGO, longAgoHeldSince(OWNED_LONG_AGO, null, "2026-02-18"))
        assertEquals("2026-02-18", longAgoHeldSince(OWNED_LONG_AGO, false, "2026-02-18"))
        assertEquals("2025-05-01", longAgoHeldSince("2025-05-01", false, "2026-02-18"))
    }
}
