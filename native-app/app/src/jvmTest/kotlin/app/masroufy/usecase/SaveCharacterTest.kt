package app.masroufy.usecase

import app.masroufy.core.BackupError
import app.masroufy.core.CharacterLook
import app.masroufy.core.ProfileCheck
import app.masroufy.core.checkBackupProfile
import app.masroufy.core.parseStoredProfile
import app.masroufy.core.toStored
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryProfileRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * «شخصيتك» على ملفك (character-spec §1.2 · r041): الحفظ بيمر على `ManageProfile.saveCharacter` ⇒ `checkProfile` (النوع من الملف)،
 * والخريطة `character` بـ`v = 2` بتتقري وتتكتب ومن غير ما تمسح باقي الملف. بيانات مخترعة.
 */
class SaveCharacterTest {
    private fun manage(repo: MemoryProfileRepository) = ManageProfile(ManageProfileDeps(repo, MemoryAccount(), FixedClock("2026-10-16T09:00:00Z")))

    @Test fun savingTheCharacterKeepsTheRestOfTheProfile() = runBlocking {
        val repo = MemoryProfileRepository()
        val profile = manage(repo)
        assertNull(profile.character(), "لسه ما اتبنتش")
        assertIs<ProfileCheck.Ok>(profile.save(profile.load().copy(displayName = "سامي", gender = "male", payday = 25)))
        val look = CharacterLook.START.copy(hair = 4, beard = 3, head = 7, wrap = 1, outfit = 2)
        assertIs<ProfileCheck.Ok>(profile.saveCharacter(look))
        assertEquals(look, profile.character())
        val saved = repo.load()!!
        assertEquals("سامي", saved.displayName)
        assertEquals(25, saved.payday)
    }

    @Test fun theProfileGenderDecidesTheClothes() = runBlocking {
        val repo = MemoryProfileRepository()
        val profile = manage(repo)
        profile.save(profile.load().copy(displayName = "منى", gender = "female"))
        // شكل رجالي على ملف ست ⇒ الشعر واللبس والغطا بيتظبطوا، واللحية بتتشال
        profile.saveCharacter(CharacterLook(hair = 2, beard = 3, head = 1, outfit = 1, outfitColor = 4))
        val got = profile.character()!!
        assertEquals(1, got.gender)
        assertEquals(6, got.hair)
        assertEquals(0, got.beard)
        assertEquals(0, got.head)
        assertEquals(5, got.outfit)
        assertEquals(0, got.outfitColor)
        // تغيير النوع من «ملفك» بيظبط الشخصية المحفوظة كمان
        profile.save(profile.load().copy(gender = "male"))
        assertEquals(0, profile.character()!!.gender)
        assertEquals(0, profile.character()!!.outfit)
    }

    @Test fun outOfRangeValuesFallBackToTheDefault() = runBlocking {
        val repo = MemoryProfileRepository()
        val profile = manage(repo)
        profile.saveCharacter(CharacterLook(skin = 40, eyes = -1, wrap = 9))
        val got = profile.character()!!
        assertEquals(CharacterLook().skin, got.skin)
        assertEquals(CharacterLook().eyes, got.eyes)
        assertEquals(CharacterLook().wrap, got.wrap)
    }

    @Test fun theStoredMapRoundTripsAndOtherVersionsReadAsNotBuilt() {
        val look = CharacterLook(gender = 1, hair = 9, head = 6, wrap = 2, outfit = 3, beard = 0, glasses = 2)
        val stored = look.toStored()
        assertEquals(2L, stored["v"])
        assertEquals(look, parseStoredProfile(mapOf("gender" to "female", "character" to stored)).character)
        assertNull(parseStoredProfile(mapOf("character" to stored + ("v" to 3L))).character, "نسخة تانية ⇒ ما اتبنتش")
        assertNull(parseStoredProfile(mapOf("character" to "x")).character)
        assertNull(parseStoredProfile(emptyMap()).character)
        // حقل ناقص ⇒ الافتراضي
        assertEquals(CharacterLook(), parseStoredProfile(mapOf("character" to mapOf("v" to 2L))).character)
    }

    @Test fun backupAcceptsAMapAndRejectsAnythingElse() {
        checkBackupProfile(mapOf("payday" to 28L, "character" to CharacterLook().toStored()))
        checkBackupProfile(mapOf("payday" to 28L, "character" to mapOf("v" to 3L, "future" to "x")))
        assertFailsWith<BackupError> { checkBackupProfile(mapOf("payday" to 28L, "character" to "head=1")) }
        // `lookIndex` القديم لسه بيتفحص زي الأول
        checkBackupProfile(mapOf("payday" to 28L, "lookIndex" to 3L))
        assertFailsWith<BackupError> { checkBackupProfile(mapOf("payday" to 28L, "lookIndex" to 9L)) }
    }
}
