package app.masroufy.usecase

import app.masroufy.core.ProfileCheck
import app.masroufy.core.parseStoredProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryProfileRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * «اختر شكلك» بقى بيتحفظ (قرار المالك 2026-10-15 مساءً — اتنقل من أول تشغيل لـ«كمّل بروفايلك»): الشكل على ملفك (`lookIndex`)
 * والدواير بتقراه من هناك. بيانات مخترعة.
 */
class ChooseLookTest {
    private fun manage(repo: MemoryProfileRepository) = ManageProfile(ManageProfileDeps(repo, MemoryAccount(), FixedClock("2026-10-15T18:00:00Z")))

    @Test fun choosingALookSavesItOnTheProfileAndKeepsTheRest() = runBlocking {
        val repo = MemoryProfileRepository()
        val profile = manage(repo)
        assertEquals(1, profile.look(), "ما اختارش ⇒ الأول")
        profile.save(profile.load().copy(displayName = "سعيد", payday = 25))
        assertIs<ProfileCheck.Ok>(profile.chooseLook(4))
        assertEquals(4, repo.load()?.lookIndex)
        assertEquals(4, profile.look())
        assertEquals("سعيد", repo.load()?.displayName, "باقي الملف زي ما هو")
        assertEquals(25, repo.load()?.payday)
        assertIs<ProfileCheck.Ok>(profile.chooseLook(6))
        assertEquals(6, profile.look(), "التغيير تاني بيتحفظ")
    }

    @Test fun aLookOutsideTheSixIsNotWritten() = runBlocking {
        val repo = MemoryProfileRepository()
        val profile = manage(repo)
        profile.chooseLook(2)
        assertIs<ProfileCheck.Invalid>(profile.chooseLook(0))
        assertIs<ProfileCheck.Invalid>(profile.chooseLook(7))
        assertEquals(2, profile.look(), "الغلط ما بيمسحش المحفوظ")
    }

    @Test fun storedLookIsReadSafely() {
        assertEquals(3, parseStoredProfile(mapOf("lookIndex" to 3L)).lookIndex)
        assertNull(parseStoredProfile(mapOf("lookIndex" to 9L)).lookIndex, "برّه الستة ⇒ ما اختارش")
        assertNull(parseStoredProfile(mapOf("lookIndex" to "2")).lookIndex)
        assertNull(parseStoredProfile(emptyMap()).lookIndex)
    }
}
