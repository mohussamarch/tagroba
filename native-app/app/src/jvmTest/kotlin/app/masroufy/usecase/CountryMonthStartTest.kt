package app.masroufy.usecase

import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.ProfileCheck
import app.masroufy.core.checkBackupProfile
import app.masroufy.core.emptyProfile
import app.masroufy.core.parseStoredProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** §111-3: بداية الشهر المالي لكل بلد — «غيّر بداية الشهر» من مصدر دخل مصري (`IncomeSourceEditSheet`) ما بيحرّكش شهر السعودية. */
class CountryMonthStartTest {
    private val stored = MemoryProfileRepository(emptyProfile().copy(payday = 28))
    private val clock = FixedClock("2026-10-23T10:00:00.000Z")

    private fun incomesIn(spaceId: String): ManageIncomeSources {
        val repo = MemoryIncomeSourceRepository()
        val profile = ManageProfile(ManageProfileDeps(SpaceProfileRepository(stored, spaceId), MemoryAccount(), clock, repo))
        return ManageIncomeSources(ManageIncomeSourcesDeps(repo, profile, MemoryUnitOfWork(listOf(repo)), SequentialIdGenerator(), clock))
    }

    @Test fun anEgyptianPaydayMovesOnlyTheEgyptianMonth() = runBlocking<Unit> {
        val egypt = SpaceProfileRepository(stored, "eg")
        val saudi = SpaceProfileRepository(stored, DEFAULT_SPACE_ID)
        assertEquals(28, egypt.load()!!.payday, "مصر من غير يوم ⇒ بتاخد يوم السعودية")
        assertTrue(incomesIn("eg").applyMonthStart(5) is ProfileCheck.Ok)
        assertEquals(5, egypt.load()!!.payday)
        assertEquals(28, saudi.load()!!.payday, "شهر السعودية ما اتحرّكش")
        assertEquals(28, stored.load()!!.payday)
        assertTrue(incomesIn(DEFAULT_SPACE_ID).applyMonthStart(27) is ProfileCheck.Ok)
        assertEquals(27, saudi.load()!!.payday)
        assertEquals(5, egypt.load()!!.payday, "وتغيير السعودية ما بيحرّكش مصر بعد ما اتحدد ليها يوم")
    }

    @Test fun savingTheProfileFromEgyptDoesNotPinTheSaudiDayOnEgypt() = runBlocking<Unit> {
        val egypt = SpaceProfileRepository(stored, "eg")
        egypt.save(egypt.load()!!.copy(displayName = "اسم وهمي"))
        assertTrue(stored.load()!!.paydays.isEmpty(), "الحفظ من غير تغيير اليوم ما بيكتبش يوم لمصر")
        SpaceProfileRepository(stored, DEFAULT_SPACE_ID).save(stored.load()!!.copy(payday = 1))
        assertEquals(1, egypt.load()!!.payday, "لسه بتتبع السعودية")
    }

    @Test fun thePerCountryDaysSurviveReadAndBackup() {
        val p = parseStoredProfile(mapOf("payday" to 28L, "paydays" to mapOf("eg" to 5L, "bad key" to 3L, "xx" to 40L)))
        assertEquals(mapOf("eg" to 5), p.paydays)
        checkBackupProfile(mapOf("paydays" to mapOf("eg" to 5L)))
    }
}
