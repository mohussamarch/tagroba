package app.masroufy.firestore

import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Space
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * اللي الجلسة بتفتحه وتقفله بعد تغيير في سجل البلاد (`AccountSession.refreshSpaces` — «البلدان» و«أول تشغيل»): بلد جديدة ⇒ تتفتح ·
 * اتأرشفت ⇒ تتقفل · رجعت ⇒ تتفتح تاني · السعودية دايمًا مفتوحة. من غير فايربيز (الجلسة نفسها على المحاكي في `SpacesOnFirestoreTest`).
 */
class SpaceChangesTest {
    private val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-10-11T08:00:00.000Z")

    @Test fun aNewCountryOpensAndNothingCloses() {
        val c = spaceChanges(setOf(DEFAULT_SPACE_ID), listOf(egypt))
        assertEquals(listOf("eg"), c.open.map { it.id })
        assertTrue(c.close.isEmpty())
    }

    @Test fun anOpenCountryIsNotOpenedTwice() {
        val c = spaceChanges(setOf(DEFAULT_SPACE_ID, "eg"), listOf(egypt))
        assertTrue(c.open.isEmpty())
        assertTrue(c.close.isEmpty())
    }

    @Test fun archivingClosesItAndBringingItBackReopensIt() {
        val archived = spaceChanges(setOf(DEFAULT_SPACE_ID, "eg"), listOf(egypt.copy(archived = true)))
        assertTrue(archived.open.isEmpty())
        assertEquals(setOf("eg"), archived.close)
        val back = spaceChanges(setOf(DEFAULT_SPACE_ID), listOf(egypt))
        assertEquals(listOf("eg"), back.open.map { it.id })
    }

    @Test fun saudiStaysOpenWhateverTheRegistrySays() {
        // السعودية مش في السجل خالص — ولو حد كتبها فيه (حتى مؤرشفة) ما بتتقفلش ولا بتتفتح مرتين
        val c = spaceChanges(setOf(DEFAULT_SPACE_ID), listOf(Space(DEFAULT_SPACE_ID, "السعودية", "SA", Currency.SAR, "", archived = true)))
        assertTrue(c.open.isEmpty())
        assertTrue(c.close.isEmpty())
    }

    /** مراجعة 0.3: قراية ناقصة للسجل (البلد مش فيها) ما تقفلش البلد — الأرشفة الصريحة بس. */
    @Test fun aCountryMissingFromAPartialListingStaysOpen() {
        assertEquals(emptySet(), spaceChanges(setOf(DEFAULT_SPACE_ID, "eg"), emptyList()).close)
        assertEquals(setOf("eg"), spaceChanges(setOf(DEFAULT_SPACE_ID, "eg"), listOf(egypt.copy(archived = true))).close)
    }

    /** مراجعة 0.3: البلاد اللي اتفتحت دلوقتي مستمعينها بيقفوا مع **أي** خطأ (مش المهلة بس) ولو التغيير ما اتسجلش. */
    @Test fun newlyOpenedCountriesStopOnAnyError() = runBlocking<Unit> {
        val stopped = mutableListOf<String>()
        val uae = egypt.copy(id = "ae")
        assertFailsWith<IllegalStateException> {
            openThenCommit(listOf(egypt, uae), { it.id }, { if (it == "ae") throw IllegalStateException("network") }, { stopped += it }) { true }
        }
        assertEquals(listOf("eg", "ae"), stopped)
        stopped.clear()
        assertEquals(false, openThenCommit(listOf(egypt), { it.id }, {}, { stopped += it }) { false })
        assertEquals(listOf("eg"), stopped, "a lost race stops them too")
        stopped.clear()
        assertEquals(true, openThenCommit(listOf(egypt), { it.id }, {}, { stopped += it }) { true })
        assertEquals(emptyList(), stopped)
    }
}
