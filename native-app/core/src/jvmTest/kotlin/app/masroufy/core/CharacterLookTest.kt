package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «شخصيتك» v2 — القيم والظبط على النوع والتخزين، مطابقة لـ`CharacterArt`/`CharacterBuilder` @2df8134d. */
class CharacterLookTest {
    // DEFAULT في النموذج = SAMPLES[0] بغطا راس 0
    private val protoDefault = mapOf(
        "gender" to 0, "build" to 1, "face" to 0, "skin" to 2, "hair" to 1, "hairColor" to 0, "eyes" to 0, "brows" to 0, "nose" to 0,
        "mouth" to 1, "beard" to 2, "glasses" to 0, "head" to 0, "wrap" to 0, "outfit" to 0, "outfitColor" to 0, "hat" to 0, "bling" to 0,
    )

    @Test
    fun defaultsAreThePrototypeDefaultAndStartHasNoBeard() {
        val stored = CharacterLook().toStored()
        protoDefault.forEach { (k, v) -> assertEquals(v.toLong(), stored[k], k) }
        assertEquals(2L, stored["v"])
        assertEquals(19, stored.size)
        assertEquals(CharacterLook(beard = 0), CharacterLook.START)
    }

    @Test
    fun fitGenderMovesItemsOutsideTheGenderListsToTheFirstChoice() {
        val man = CharacterLook(hair = 3, head = 1, outfit = 2, outfitColor = 4, beard = 3)
        val woman = man.fitGender(1)
        assertEquals(CharacterLook(gender = 1, hair = 6, head = 0, outfit = 5, outfitColor = 0, beard = 0), woman)
        val back = CharacterLook(gender = 1, hair = 8, head = 6, outfit = 3, outfitColor = 2, beard = 0).fitGender(0)
        assertEquals(CharacterLook(gender = 0, hair = 1, head = 0, outfit = 0, outfitColor = 0, beard = 0), back)
        // اختيارات مشتركة بتفضل، ومكافأة اللبس (4) بتفضل مع لونها
        val shared = CharacterLook(head = 0, outfit = 4, outfitColor = 3, glasses = 4, hat = 1).fitGender(1)
        assertEquals(4, shared.outfit); assertEquals(3, shared.outfitColor); assertEquals(4, shared.glasses); assertEquals(1, shared.hat)
        // الراجل بيحتفظ بالدقن
        assertEquals(3, CharacterLook(beard = 3).fitGender(0).beard)
        // اختيار جوه القايمة ما بيتغيرش
        val inList = CharacterLook(gender = 1, hair = 10, head = 5, outfit = 6, outfitColor = 2, beard = 0)
        assertEquals(inList, inList.fitGender(1))
    }

    @Test
    fun choosingAPartFollowsThePrototypeSetRules() {
        val l = CharacterLook(wrap = 4, outfitColor = 3)
        assertEquals(2, l.with(CharacterPart.HEAD, 6).wrap)
        assertEquals(6, l.with(CharacterPart.HEAD, 6).head)
        assertEquals(4, l.with(CharacterPart.HEAD, 5).wrap)
        assertEquals(0, l.with(CharacterPart.OUTFIT, 2).outfitColor)
        assertEquals(2, l.with(CharacterPart.OUTFIT, 2).outfit)
        CharacterPart.entries.forEach { part -> part.options(false).forEach { o -> assertEquals(o.id, l.with(part, o.id).valueOf(part), "$part ${o.id}") } }
        CharacterPart.entries.forEach { part -> part.options(true).forEach { o -> assertEquals(o.id, l.with(part, o.id).valueOf(part), "$part ${o.id}") } }
        CharacterColorKey.entries.forEach { k -> assertEquals(5, l.withColor(k, 5).colorOf(k)) }
    }

    @Test
    fun normalizedPutsEveryFieldOutsideItsRangeBackToItsDefault() {
        val bad = CharacterLook(gender = 2, build = -1, face = 4, skin = 6, hair = 11, hairColor = 9, eyes = 4, brows = 4, nose = 3, mouth = 4,
            beard = 6, glasses = 5, head = 8, wrap = 6, outfit = 7, outfitColor = 6, hat = 2, bling = -3)
        assertEquals(CharacterLook(), bad.normalized())
        val edge = CharacterLook(gender = 1, build = 2, face = 3, skin = 5, hair = 10, hairColor = 5, eyes = 3, brows = 3, nose = 2, mouth = 3,
            beard = 5, glasses = 4, head = 7, wrap = 5, outfit = 6, outfitColor = 5, hat = 1, bling = 1)
        assertEquals(edge, edge.normalized())
    }

    @Test
    fun storedRoundTripAndLenientParse() {
        val look = CharacterLook(gender = 1, build = 0, face = 2, skin = 4, hair = 7, hairColor = 3, eyes = 1, brows = 1, nose = 2, mouth = 2,
            beard = 0, glasses = 2, head = 5, wrap = 4, outfit = 3, outfitColor = 1)
        assertEquals(look, parseStoredCharacter(look.toStored()))
        // حقل ناقص أو مش رقم صحيح أو برّه المدى ⇒ الافتراضي؛ Int وDouble الصحيح مقبولين
        val messy = mapOf("v" to 2.0, "face" to 3, "skin" to "4", "hair" to 1.5, "head" to 99L, "build" to 2.0)
        assertEquals(CharacterLook(face = 3, build = 2), parseStoredCharacter(messy))
        // من غير خريطة أو بنسخة تانية ⇒ لسه ما اتبنتش
        assertNull(parseStoredCharacter(null))
        assertNull(parseStoredCharacter("x"))
        assertNull(parseStoredCharacter(mapOf("face" to 1L)))
        assertNull(parseStoredCharacter(mapOf("v" to 3L, "face" to 1L)))
        assertNull(parseStoredCharacter(mapOf("v" to 1L)))
    }

    @Test
    fun profileCarriesTheCharacterAndFitsItToTheProfileGender() {
        val stored = mapOf("gender" to "female", "character" to CharacterLook(hair = 2, beard = 3, head = 1).toStored())
        val profile = parseStoredProfile(stored)
        assertEquals(CharacterLook(hair = 2, beard = 3, head = 1), profile.character)
        val ok = checkProfile(profile) as ProfileCheck.Ok
        assertEquals(CharacterLook(gender = 1, hair = 6, beard = 0, head = 0, outfit = 5), ok.profile.character)
        assertNull(parseStoredProfile(mapOf("gender" to "male")).character)
        assertNull((checkProfile(emptyProfile()) as ProfileCheck.Ok).profile.character)
        assertEquals(1, genderIndex("female")); assertEquals(0, genderIndex("male")); assertEquals(0, genderIndex(null))
    }

    @Test
    fun backupAcceptsAnyCharacterMapButNotOtherShapes() {
        checkBackupProfile(mapOf("character" to mapOf("v" to 3L, "anything" to "new")))
        checkBackupProfile(mapOf("character" to null))
        assertFailsWith<BackupError> { checkBackupProfile(mapOf("character" to "wrong")) }
    }

    @Test
    fun optionListsMatchTheBuilderWithNoRewardIds() {
        fun ids(p: CharacterPart, w: Boolean) = p.options(w).map { it.id }
        assertEquals(listOf(0, 1, 2), ids(CharacterPart.OUTFIT, false)); assertEquals(listOf(5, 3, 6), ids(CharacterPart.OUTFIT, true))
        assertEquals(listOf(0, 1, 2, 3, 7), ids(CharacterPart.HEAD, false)); assertEquals(listOf(0, 4, 5, 6), ids(CharacterPart.HEAD, true))
        assertEquals((0..5).toList(), ids(CharacterPart.HAIR, false)); assertEquals((6..10).toList(), ids(CharacterPart.HAIR, true))
        assertEquals((0..5).toList(), ids(CharacterPart.BEARD, false)); assertEquals(emptyList(), ids(CharacterPart.BEARD, true))
        assertEquals((0..3).toList(), ids(CharacterPart.GLASSES, false))
        assertTrue(CharacterPart.entries.none { p -> listOf(false, true).any { w -> p == CharacterPart.OUTFIT && 4 in ids(p, w) } })
        assertEquals(listOf(CharacterSection.FACE, CharacterSection.BODY, CharacterSection.WEAR), CharacterSection.entries)
        assertEquals(listOf(CharacterPart.BUILD, CharacterPart.SKIN, CharacterPart.HAIR), CharacterSection.BODY.partsFor(true))
        assertEquals(CharacterSection.entries.flatMap { it.parts }.toSet(), CharacterPart.entries.toSet())
        // كل اسم موجود في التلات جداول
        val keys = CharacterSection.entries.map { it.title } + CharacterPart.entries.map { it.title } +
            CharacterPart.entries.flatMap { it.options(false) + it.options(true) }.map { it.label }
        keys.forEach { k -> assertTrue(k in MSA_TEXTS && k in EGYPTIAN_TEXTS && k in ENGLISH_TEXTS, "$k") }
    }
}
